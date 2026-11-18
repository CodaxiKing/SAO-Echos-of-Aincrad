package com.aincrad.reputation;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.reputation.api.PlayerJailEvent;
import com.aincrad.reputation.hook.Hooks;
import com.aincrad.reputation.model.CursorRules;
import com.aincrad.reputation.model.WorldMarkRules;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Recompensa por cabeça: qualquer jogador põe Col pela prisão de um vermelho. O valor fica em
 * custódia no AincradEconomy e vai para quem o derrotar e mandar para a prisão; se o vermelho
 * voltar a verde sem ser preso, volta a quem pôs. Cada mudança de estado é uma só linha do banco
 * ("UPDATE ... WHERE state = 'ABERTA'"), então dois servidores nunca pagam a mesma recompensa.
 */
public final class BountyService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS rep_bounties ({AUTO_ID}, target VARCHAR(36) NOT NULL, target_name VARCHAR(32) NOT NULL, "
                    + "placer VARCHAR(36) NOT NULL, placer_name VARCHAR(32) NOT NULL, amount BIGINT NOT NULL, hold_id VARCHAR(64) NOT NULL, "
                    + "at_time BIGINT NOT NULL, state VARCHAR(12) NOT NULL)");

    private record Open(long id, String hold, long amount, String placer) {
    }

    /**
     * Paga a {@code captor} as recompensas abertas de todos os alvos que {@code isTarget} aceitar
     * (os membros de uma guilda de crime, quando o líder cai na base). O teste roda na thread do
     * servidor.
     */
    public void payMatching(Player captor, String why, java.util.function.Predicate<UUID> isTarget) {
        if (!hooks.has("AincradEconomy")) {
            return;
        }
        db.query("SELECT id, hold_id, amount, target FROM rep_bounties WHERE state = 'ABERTA'",
                rs -> new Object[] {rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)}).whenComplete((rows, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível ler as recompensas da guilda " + why, ex);
                return;
            }
            for (Object[] r : rows) {
                if (!isTarget.test(UUID.fromString((String) r[3]))) {
                    continue;
                }
                long id = (Long) r[0];
                String hold = (String) r[1];
                long amount = (Long) r[2];
                // Só paga a que este servidor conseguir marcar como paga.
                db.update("UPDATE rep_bounties SET state = 'PAGA' WHERE id = ? AND state = 'ABERTA'", id).thenAccept(n -> {
                    if (n > 0) {
                        AincradCore.get().runSync(() -> {
                            hooks.release(hold, captor.getUniqueId(), amount, "recompensa da guilda " + why);
                            if (captor.isOnline()) {
                                msg.send(captor, "recompensa.recebeu", Messages.vars("jogador", why, "valor", amount));
                            }
                        });
                    }
                });
            }
        }));
    }

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;

    public BountyService(Plugin plugin, Database db, Messages msg, ConfigFile config, Hooks hooks) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
    }

    private boolean enabled() {
        return config.bool("recompensa.ativa", true) && hooks.has("AincradEconomy");
    }

    // ------------------------------------------------------------------ pôr

    /** {@code /recompensa <jogador> <valor>}. */
    public void place(Player placer, String targetName, String amountText) {
        if (!enabled()) {
            msg.send(placer, "recompensa.desligada");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(amountText);
        } catch (NumberFormatException e) {
            msg.send(placer, "recompensa.uso");
            return;
        }
        long min = config.integer("recompensa.minimo", 100, 1, 1_000_000_000);
        boolean onlyRed = config.bool("recompensa.so_vermelhos", true);
        long now = System.currentTimeMillis();
        // O alvo pode estar em outro servidor, ou offline: a cor vem do banco.
        db.query("SELECT uuid, name, orange_until, red_until FROM rep_players WHERE LOWER(name) = ?",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)}, targetName.toLowerCase(Locale.ROOT))
                .whenComplete((rows, ex) -> AincradCore.get().runSync(() -> {
                    if (ex != null || rows.isEmpty() || !placer.isOnline()) {
                        msg.send(placer, "recompensa.desconhecido", Messages.vars("jogador", targetName));
                        return;
                    }
                    Object[] t = rows.get(0);
                    CursorRules.Color color = (Long) t[3] > now ? CursorRules.Color.VERMELHO
                            : (Long) t[2] > now ? CursorRules.Color.LARANJA : CursorRules.Color.VERDE;
                    WorldMarkRules.BountyBlock block = WorldMarkRules.bountyBlocked(placer.getUniqueId().toString().equals(t[0]),
                            amount, min, color, onlyRed);
                    if (block != null) {
                        msg.send(placer, "recompensa.bloqueio." + block.name().toLowerCase(Locale.ROOT),
                                Messages.vars("jogador", String.valueOf(t[1]), "minimo", min));
                        return;
                    }
                    String hold = hooks.hold(placer, amount, "recompensa: " + t[1]);
                    if (hold == null) {
                        msg.send(placer, "recompensa.sem_col", Messages.vars("valor", amount));
                        return;
                    }
                    db.update("INSERT INTO rep_bounties (target, target_name, placer, placer_name, amount, hold_id, at_time, state) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ABERTA')", t[0], t[1], placer.getUniqueId().toString(), placer.getName(),
                            amount, hold, now).whenComplete((n, err) -> AincradCore.get().runSync(() -> {
                        if (err != null) {
                            // Não gravou: o Col volta na hora.
                            hooks.refund(hold, "recompensa não registrada");
                            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar a recompensa por " + t[1], err);
                            msg.send(placer, "recompensa.erro");
                            return;
                        }
                        msg.send(placer, "recompensa.posta", Messages.vars("jogador", String.valueOf(t[1]), "valor", amount));
                        Player target = Bukkit.getPlayer(UUID.fromString((String) t[0]));
                        if (target != null) {
                            msg.send(target, "recompensa.na_sua_cabeca", Messages.vars("valor", amount));
                        }
                    }));
                }));
    }

    // ------------------------------------------------------------------ pagar e devolver

    /** O vermelho foi preso: quem o derrotou leva tudo o que havia pela cabeça dele. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJail(PlayerJailEvent event) {
        if (!hooks.has("AincradEconomy")) {
            return;
        }
        Player jailed = event.getPlayer();
        Player captor = event.getCaptor();
        String target = jailed.getUniqueId().toString();
        db.async(c -> {
            List<Open> open = Database.query(c, "SELECT id, hold_id, amount, placer FROM rep_bounties WHERE target = ? AND state = 'ABERTA'",
                    rs -> new Open(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)), target);
            // Só as que este servidor conseguir marcar como pagas são dele para pagar.
            open.removeIf(o -> {
                try {
                    return Database.update(c, "UPDATE rep_bounties SET state = 'PAGA' WHERE id = ? AND state = 'ABERTA'", o.id()) == 0;
                } catch (java.sql.SQLException e) {
                    return true;
                }
            });
            return open;
        }).whenComplete((open, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível pagar a recompensa por " + jailed.getName(), ex);
                return;
            }
            long total = 0;
            for (Open o : open) {
                hooks.release(o.hold(), captor.getUniqueId(), o.amount(), "recompensa por " + jailed.getName());
                total += o.amount();
            }
            if (total > 0) {
                msg.send(captor, "recompensa.recebeu", Messages.vars("jogador", jailed.getName(), "valor", total));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(captor)) {
                        msg.send(p, "recompensa.anuncio", Messages.vars("captor", captor.getName(), "jogador", jailed.getName(), "valor", total));
                    }
                }
            }
        }));
    }

    /** A cada minuto: o alvo voltou a verde sem ser preso? O Col volta a quem pôs. */
    public void tick() {
        if (!hooks.has("AincradEconomy")) {
            return;
        }
        long now = System.currentTimeMillis();
        db.async(c -> {
            List<Open> open = Database.query(c, "SELECT b.id, b.hold_id, b.amount, b.placer, p.red_until, p.jail_until FROM rep_bounties b "
                            + "JOIN rep_players p ON p.uuid = b.target WHERE b.state = 'ABERTA'",
                    rs -> WorldMarkRules.bountyExpired(rs.getLong(5), rs.getLong(6), now)
                            ? new Open(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)) : null);
            open.removeIf(o -> {
                try {
                    return o == null || Database.update(c, "UPDATE rep_bounties SET state = 'DEVOLVIDA' WHERE id = ? AND state = 'ABERTA'", o.id()) == 0;
                } catch (java.sql.SQLException e) {
                    return true;
                }
            });
            return open;
        }).whenComplete((open, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível conferir as recompensas vencidas", ex);
                return;
            }
            for (Open o : open) {
                hooks.refund(o.hold(), "recompensa devolvida");
                Player placer = Bukkit.getPlayer(UUID.fromString(o.placer()));
                if (placer != null) {
                    msg.send(placer, "recompensa.devolvida", Messages.vars("valor", o.amount()));
                }
            }
        }));
    }

    // ------------------------------------------------------------------ listar

    /** {@code /recompensas}: as cabeças a prêmio, da maior para a menor. */
    public void list(Player p) {
        db.query("SELECT target_name, SUM(amount), COUNT(*) FROM rep_bounties WHERE state = 'ABERTA' GROUP BY target, target_name ORDER BY 2 DESC",
                rs -> new Object[] {rs.getString(1), rs.getLong(2), rs.getInt(3)}).whenComplete((rows, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null || rows.isEmpty()) {
                msg.send(p, "recompensa.nenhuma");
                return;
            }
            Menu menu = Menu.of(msg.get("recompensa.menu")).rows(6);
            for (Object[] r : rows) {
                menu.item(Menu.Button.of(new ItemStack(Material.SKELETON_SKULL), msg.get("recompensa.item", Messages.vars("jogador", String.valueOf(r[0]))),
                        pl -> { }).lore(List.of(msg.get("recompensa.item_valor", Messages.vars("valor", r[1], "qtd", r[2])))));
            }
            AincradCore.get().menus().open(p, menu);
        }));
    }
}
