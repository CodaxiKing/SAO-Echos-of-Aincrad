package com.aincrad.skills;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * As skills únicas: cada uma tem um só dono na rede inteira (a chave primária do banco garante).
 * A primeira é Duas Espadas, dada à mão pelo admin ou ao vencedor do teste de reação.
 */
public final class UniqueService implements Listener {

    public static final String DUAL = "duas_espadas";

    /** O dono de uma skill única. */
    public record Holder(UUID uuid, String name, long at) {
    }

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private volatile Map<String, Holder> holders = Map.of();
    private Map<String, com.aincrad.skills.model.UniqueRules.Def> defs = Map.of();
    private SkillStore store;
    private String testId = DUAL;
    private final Set<String> granting = new HashSet<>();

    /** Lê o unique-skills.yml. */
    public void reloadDefs(org.bukkit.configuration.ConfigurationSection skills, SkillStore skillStore) {
        store = skillStore;
        List<String> errors = new java.util.ArrayList<>();
        defs = com.aincrad.skills.model.UniqueRules.parse(skills, errors);
        errors.forEach(e -> plugin.getLogger().warning("unique-skills.yml: " + e));
    }

    public Map<String, com.aincrad.skills.model.UniqueRules.Def> defs() {
        return defs;
    }

    /**
     * O jogador avançou (aparou, acertou, usou uma skill, subiu a proficiência): se cumpriu o
     * critério de uma skill única ainda sem dono, ela é dele.
     */
    public void check(Player p) {
        SkillStore.Data d = store == null ? null : store.data(p);
        if (d == null) {
            return;
        }
        for (var def : defs.values()) {
            if (holders.containsKey(def.id()) || granting.contains(def.id()) || !def.criterion().met(d::counter, d::prof)) {
                continue;
            }
            granting.add(def.id());
            grant(def.id(), p.getUniqueId(), p.getName()).thenAccept(ok -> {
                granting.remove(def.id());
                if (ok) {
                    announce(def.id(), p.getName());
                    msg.send(p, "unica.e_sua", Messages.vars("skill", def.name()));
                    // O critério fica só no arquivo: nem o console diz qual foi.
                    plugin.getLogger().info(p.getName() + " cumpriu o critério da skill única " + def.id() + ".");
                }
            });
        }
    }

    // Teste de reação
    private boolean testWaiting;
    private boolean testOpen;
    private long testOpenAt;
    private final Set<UUID> testOut = new HashSet<>();

    public UniqueService(Plugin plugin, Database db, Messages msg) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
    }

    /** Relê os donos do banco (outro servidor da rede pode ter mudado). */
    public void refresh() {
        db.query("SELECT skill, uuid, name, at_time FROM skill_unique",
                        rs -> Map.entry(rs.getString(1), new Holder(UUID.fromString(rs.getString(2)), rs.getString(3), rs.getLong(4))))
                .thenAccept(list -> {
                    Map<String, Holder> m = new ConcurrentHashMap<>();
                    list.forEach(e -> m.put(e.getKey(), e.getValue()));
                    holders = m;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível ler as skills únicas", ex);
                    return null;
                });
    }

    public Holder holder(String skill) {
        return holders.get(skill);
    }

    public boolean has(Player p, String skill) {
        Holder h = holders.get(skill);
        return h != null && h.uuid().equals(p.getUniqueId());
    }

    /** Dá a skill ao jogador se ela ainda não tem dono. O resultado chega na thread do servidor. */
    public CompletableFuture<Boolean> grant(String skill, UUID id, String name) {
        long now = System.currentTimeMillis();
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        db.async(c -> {
            if (!Database.query(c, "SELECT uuid FROM skill_unique WHERE skill = ?", rs -> rs.getString(1), skill).isEmpty()) {
                return false;
            }
            try {
                Database.update(c, "INSERT INTO skill_unique (skill, uuid, name, at_time) VALUES (?, ?, ?, ?)", skill, id.toString(), name, now);
                return true;
            } catch (java.sql.SQLException dup) {
                // Outro servidor gravou no mesmo instante: a chave primária recusou este.
                return false;
            }
        }).whenComplete((ok, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível dar a skill única " + skill, ex);
                out.complete(false);
                return;
            }
            if (ok) {
                Map<String, Holder> m = new ConcurrentHashMap<>(holders);
                m.put(skill, new Holder(id, name, now));
                holders = m;
            }
            out.complete(ok);
        }));
        return out;
    }

    public CompletableFuture<Boolean> clear(String skill) {
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        db.update("DELETE FROM skill_unique WHERE skill = ?", skill).whenComplete((n, ex) -> AincradCore.get().runSync(() -> {
            if (ex == null) {
                Map<String, Holder> m = new ConcurrentHashMap<>(holders);
                m.remove(skill);
                holders = m;
            }
            out.complete(ex == null && n > 0);
        }));
        return out;
    }

    // ------------------------------------------------------------------ teste de reação

    /** Começa o teste: depois de uma espera sorteada, o primeiro a se agachar ganha Duas Espadas. */
    public boolean startTest(String id) {
        if (testWaiting || testOpen || holder(id) != null || (!id.equals(DUAL) && !defs.containsKey(id))) {
            return false;
        }
        testId = id;
        testWaiting = true;
        testOut.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, "unica.teste_prepare");
        }
        long delay = ThreadLocalRandom.current().nextLong(60, 200);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            testWaiting = false;
            testOpen = true;
            testOpenAt = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                msg.title(p, "unica.teste_agora", Duration.ofSeconds(2));
            }
            // Ninguém reagiu em 10 segundos: o teste acaba sem dono.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (testOpen) {
                    testOpen = false;
                    Bukkit.getOnlinePlayers().forEach(p -> msg.send(p, "unica.teste_ninguem"));
                }
            }, 200L);
        }, delay);
        return true;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player p = event.getPlayer();
        if (testWaiting) {
            // Agachou antes da hora: está fora desta rodada.
            if (testOut.add(p.getUniqueId())) {
                msg.send(p, "unica.teste_queimou");
            }
            return;
        }
        if (!testOpen || testOut.contains(p.getUniqueId())) {
            return;
        }
        testOpen = false;
        long ms = System.currentTimeMillis() - testOpenAt;
        String id = testId;
        grant(id, p.getUniqueId(), p.getName()).thenAccept(ok -> {
            if (ok) {
                announce(id, p.getName());
                plugin.getLogger().info(p.getName() + " venceu o teste de reação (" + ms + " ms) e recebeu a skill única " + id + ".");
            }
        });
    }

    /** O servidor só fica sabendo que a skill encontrou um dono. */
    public void announce(String skill, String player) {
        var def = defs.get(skill);
        String name = def != null ? def.name() : msg.has("unica.nomes." + skill) ? msg.raw("unica.nomes." + skill) : skill;
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, "unica.anuncio", Messages.vars("skill", name, "jogador", player));
        }
    }

    public List<String> known() {
        return List.copyOf(holders.keySet());
    }
}
