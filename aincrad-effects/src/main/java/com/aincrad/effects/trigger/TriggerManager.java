package com.aincrad.effects.trigger;

import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.engine.Audience;
import com.aincrad.effects.engine.EffectParseException;
import com.aincrad.effects.engine.Spec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Gatilhos sem código (triggers.yml): cada evento do jogo toca um efeito. */
public final class TriggerManager implements Listener {

    public enum Kind {
        ENTRAR_SERVIDOR, ENTRAR_REGIAO, SAIR_REGIAO, MATAR_MOB, SUBIR_NIVEL, PEGAR_ITEM, MORRER;

        static Kind parse(String s) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Um gatilho já lido. Filtros vazios valem para qualquer valor. */
    public record Trigger(String id, Kind kind, String effect, int delay, String permission, double chance,
                          Audience audience, Set<String> filter, int level, Region region, boolean waitPack) {
    }

    /** Quanto o gatilho de entrada espera pelo pack antes de tocar mesmo assim. */
    private static final int PACK_TIMEOUT_TICKS = 200;

    private final AincradEffectsPlugin plugin;
    private final Map<Kind, List<Trigger>> byKind = new EnumMap<>(Kind.class);
    private final Map<UUID, Set<String>> insideRegions = new HashMap<>();
    private final Map<UUID, List<Trigger>> waitingPack = new HashMap<>();

    public TriggerManager(AincradEffectsPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return erros de configuração */
    public List<String> load(YamlConfiguration cfg) {
        byKind.clear();
        insideRegions.clear();
        List<String> errors = new ArrayList<>();
        ConfigurationSection root = cfg.getConfigurationSection("gatilhos");
        if (root == null) {
            return errors;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            try {
                Trigger t = parse(id, sec);
                byKind.computeIfAbsent(t.kind(), k -> new ArrayList<>()).add(t);
            } catch (EffectParseException e) {
                errors.add(e.getMessage());
            }
        }
        return errors;
    }

    private Trigger parse(String id, ConfigurationSection sec) {
        String where = "triggers.yml → " + id;
        Spec s = new Spec(sec, where);
        Kind kind = Kind.parse(s.required("evento"));
        if (kind == null) {
            throw new EffectParseException(where + ": evento desconhecido (entrar_servidor, entrar_regiao, sair_regiao, matar_mob, subir_nivel, pegar_item, morrer)");
        }
        String effect = s.required("efeito");
        if (plugin.engine().get(effect) == null) {
            throw new EffectParseException(where + ": efeito \"" + effect + "\" não existe");
        }
        Audience aud = s.has("publico") ? Audience.parse(s.str("publico", null), null) : null;
        Set<String> filter = new HashSet<>();
        String filterKey = kind == Kind.MATAR_MOB ? "mobs" : kind == Kind.PEGAR_ITEM ? "itens" : null;
        if (filterKey != null) {
            for (String f : s.strings(filterKey)) {
                filter.add(f.trim().toUpperCase(Locale.ROOT));
            }
        }
        Region region = null;
        if (kind == Kind.ENTRAR_REGIAO || kind == Kind.SAIR_REGIAO) {
            ConfigurationSection r = sec.getConfigurationSection("regiao");
            if (r == null) {
                throw new EffectParseException(where + ": falta \"regiao\" com mundo, canto1 e canto2");
            }
            Spec rs = new Spec(r, where + " regiao");
            region = Region.of(rs.required("mundo"), rs.vector("canto1", new Vector()), rs.vector("canto2", new Vector()));
        }
        return new Trigger(id, kind, effect, s.ticks("atraso", kind == Kind.ENTRAR_SERVIDOR ? 1 : 0),
                s.str("permissao", null), Math.max(0, Math.min(1, s.dbl("chance", 1))), aud, filter,
                s.integer("nivel", 0), region, s.bool("esperar_pack", true));
    }

    public int count() {
        return byKind.values().stream().mapToInt(List::size).sum();
    }

    private void fire(Trigger t, Player p, Map<String, String> vars) {
        if (t.permission() != null && !p.hasPermission(t.permission())) {
            return;
        }
        if (t.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= t.chance()) {
            return;
        }
        Runnable run = () -> {
            if (!p.isOnline()) {
                return;
            }
            var effect = plugin.engine().get(t.effect());
            if (effect != null) {
                plugin.engine().play(effect, p.getLocation(), p, t.audience(), null, vars);
            }
        };
        if (t.delay() > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, run, t.delay());
        } else {
            run.run();
        }
    }

    private List<Trigger> of(Kind k) {
        return byKind.getOrDefault(k, List.of());
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        List<Trigger> now = new ArrayList<>();
        List<Trigger> later = new ArrayList<>();
        boolean packComing = plugin.pack().canSend() &&!plugin.packStatus().hasPack(p);
        for (Trigger t : of(Kind.ENTRAR_SERVIDOR)) {
            (t.waitPack() && packComing ? later : now).add(t);
        }
        now.forEach(t -> fire(t, p, Map.of()));
        if (!later.isEmpty()) {
            waitingPack.put(p.getUniqueId(), later);
            Bukkit.getScheduler().runTaskLater(plugin, () -> packSettled(p), PACK_TIMEOUT_TICKS);
        }
        updateRegions(p, p.getLocation());
    }

    /** O jogador carregou, recusou ou não conseguiu baixar o pack (ou o tempo acabou). */
    public void packSettled(Player p) {
        List<Trigger> list = waitingPack.remove(p.getUniqueId());
        if (list != null && p.isOnline()) {
            list.forEach(t -> fire(t, p, Map.of()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        insideRegions.remove(event.getPlayer().getUniqueId());
        waitingPack.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            updateRegions(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        updateRegions(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        updateRegions(event.getPlayer(), event.getPlayer().getLocation());
    }

    private void updateRegions(Player p, Location at) {
        List<Trigger> enter = of(Kind.ENTRAR_REGIAO);
        List<Trigger> exit = of(Kind.SAIR_REGIAO);
        if (enter.isEmpty() && exit.isEmpty()) {
            return;
        }
        Set<String> inside = insideRegions.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        for (Trigger t : enter) {
            String key = "e:" + t.id();
            boolean in = t.region().contains(at);
            if (in && inside.add(key)) {
                fire(t, p, Map.of("regiao", t.id()));
            } else if (!in) {
                inside.remove(key);
            }
        }
        for (Trigger t : exit) {
            String key = "s:" + t.id();
            boolean in = t.region().contains(at);
            if (in) {
                inside.add(key);
            } else if (inside.remove(key)) {
                fire(t, p, Map.of("regiao", t.id()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player) {
            return;
        }
        String type = dead.getType().name();
        for (Trigger t : of(Kind.MATAR_MOB)) {
            if (t.filter().isEmpty() || t.filter().contains(type)) {
                fire(t, killer, Map.of("mob", type.toLowerCase(Locale.ROOT)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() <= event.getOldLevel()) {
            return;
        }
        for (Trigger t : of(Kind.SUBIR_NIVEL)) {
            if (t.level() <= 0 || t.level() == event.getNewLevel()) {
                fire(t, event.getPlayer(), Map.of("nivel", String.valueOf(event.getNewLevel()),
                        "level", String.valueOf(event.getNewLevel())));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        String type = event.getItem().getItemStack().getType().name();
        for (Trigger t : of(Kind.PEGAR_ITEM)) {
            if (t.filter().isEmpty() || t.filter().contains(type)) {
                fire(t, p, Map.of("item", type.toLowerCase(Locale.ROOT),
                        "quantidade", String.valueOf(event.getItem().getItemStack().getAmount())));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        for (Trigger t : of(Kind.MORRER)) {
            fire(t, event.getPlayer(), Map.of());
        }
    }
}
