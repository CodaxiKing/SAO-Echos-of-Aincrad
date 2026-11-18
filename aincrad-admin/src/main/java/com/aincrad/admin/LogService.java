package com.aincrad.admin;

import com.aincrad.admin.model.LogRules;
import com.aincrad.admin.model.LogRules.Entry;
import com.aincrad.admin.model.LogRules.Filter;
import com.aincrad.admin.model.LogRules.Where;
import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * O registro de admin: grava no banco do core cada {@link AincradAdminActionEvent} que os plugins
 * Aincrad disparam (quem, quando, o quê, em qual servidor) e responde às consultas.
 */
public final class LogService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS admin_log ({AUTO_ID}, at_time BIGINT NOT NULL, actor_uuid VARCHAR(36), "
                    + "actor VARCHAR(32) NOT NULL, plugin VARCHAR(32) NOT NULL, action VARCHAR(64) NOT NULL, "
                    + "detail VARCHAR(512) NOT NULL, server VARCHAR(64) NOT NULL)");

    private final Plugin plugin;
    private final Database db;
    private boolean console;

    public LogService(Plugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void console(boolean on) {
        console = on;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAction(AincradAdminActionEvent event) {
        String actor = LogRules.clip(event.getActor() == null ? "?" : event.getActor().getName(), LogRules.ACTOR_MAX);
        String uuid = event.getActor() instanceof Player p ? p.getUniqueId().toString() : null;
        String from = LogRules.clip(event.getPlugin(), LogRules.PLUGIN_MAX).toLowerCase(java.util.Locale.ROOT);
        String action = LogRules.clip(event.getAction(), LogRules.ACTION_MAX).toLowerCase(java.util.Locale.ROOT);
        String detail = LogRules.clip(event.getDetail(), LogRules.DETAIL_MAX);
        if (console) {
            plugin.getLogger().info("[registro] " + actor + " [" + from + "] " + action + " " + detail);
        }
        db.update("INSERT INTO admin_log (at_time, actor_uuid, actor, plugin, action, detail, server) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        System.currentTimeMillis(), uuid, actor, from, action, detail,
                        LogRules.clip(AincradCore.get().server(), 64))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível gravar a ação no registro: " + actor + " " + action, ex);
                    return 0;
                });
    }

    /** Uma página, da ação mais nova para a mais velha. Traz uma linha a mais para saber se há próxima. */
    public CompletableFuture<List<Entry>> page(Filter f, int perPage) {
        Where w = LogRules.where(f);
        List<Object> params = new ArrayList<>(w.params());
        params.add(perPage + 1);
        params.add(LogRules.offset(f.page(), perPage));
        return db.query("SELECT id, at_time, actor, plugin, action, detail, server FROM admin_log" + w.sql()
                        + " ORDER BY id DESC LIMIT ? OFFSET ?",
                rs -> new Entry(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7)),
                params.toArray());
    }

    /** Apaga o que passou do prazo de guarda. */
    public void purge(int keepDays) {
        long cutoff = LogRules.cutoff(System.currentTimeMillis(), keepDays);
        if (cutoff <= 0) {
            return;
        }
        db.update("DELETE FROM admin_log WHERE at_time < ?", cutoff)
                .thenAccept(n -> {
                    if (n > 0) {
                        plugin.getLogger().info("Registro de admin: " + n + " linha(s) com mais de " + keepDays + " dias apagada(s).");
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível limpar o registro de admin", ex);
                    return null;
                });
    }
}
