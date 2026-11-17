package com.aincrad.core.profile;

import com.aincrad.core.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Perfis dos jogadores online (carregados antes de entrar, gravados ao sair e periodicamente).
 * Para quem está offline, {@link #load(UUID)} lê do banco sem guardar em memória.
 */
public final class ProfileService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS aincrad_profiles ("
                    + "uuid VARCHAR(36) NOT NULL, owner VARCHAR(64) NOT NULL, k VARCHAR(64) NOT NULL, v TEXT NOT NULL, "
                    + "PRIMARY KEY (uuid, owner, k))");

    private final Plugin core;
    private final Database db;
    private final Map<UUID, PlayerProfile> online = new ConcurrentHashMap<>();

    public ProfileService(Plugin core, Database db) {
        this.core = core;
        this.db = db;
    }

    /** Perfil de quem está online (sempre carregado). */
    public PlayerProfile get(Player p) {
        return online.computeIfAbsent(p.getUniqueId(), PlayerProfile::new);
    }

    /** Perfil de qualquer jogador, lido do banco (para offline). */
    public CompletableFuture<PlayerProfile> load(UUID id) {
        PlayerProfile cached = online.get(id);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return db.async(c -> read(c, id));
    }

    PlayerProfile read(Connection c, UUID id) throws SQLException {
        PlayerProfile p = new PlayerProfile(id);
        for (String[] row : Database.query(c, "SELECT owner, k, v FROM aincrad_profiles WHERE uuid = ?",
                rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, id.toString())) {
            p.loadValue(row[0], row[1], row[2]);
        }
        return p;
    }

    /** Grava as mudanças do perfil (nesta thread). */
    void write(Connection c, PlayerProfile p) throws SQLException {
        PlayerProfile.Changes ch = p.takeChanges();
        if (ch.empty()) {
            return;
        }
        try {
            String upsert = db.upsert("aincrad_profiles", List.of("uuid", "owner", "k"), List.of("v"));
            for (Map.Entry<String, String> e : ch.upserts().entrySet()) {
                String[] ok = e.getKey().split("\u0000", 2);
                Database.update(c, upsert, p.id().toString(), ok[0], ok[1], e.getValue());
            }
            for (String k : ch.deletes()) {
                String[] ok = k.split("\u0000", 2);
                Database.update(c, "DELETE FROM aincrad_profiles WHERE uuid = ? AND owner = ? AND k = ?",
                        p.id().toString(), ok[0], ok[1]);
            }
        } catch (SQLException e) {
            p.restoreChanges(ch);
            throw e;
        }
    }

    /** Grava um perfil de jogador offline alterado com {@link #load}. */
    public CompletableFuture<Void> save(PlayerProfile p) {
        return db.async(c -> {
            write(c, p);
            return null;
        });
    }

    /** Grava todos os perfis online com mudanças (tarefa periódica). */
    public void flushAsync() {
        for (PlayerProfile p : online.values()) {
            if (p.isDirty()) {
                save(p).exceptionally(ex -> {
                    core.getLogger().log(Level.WARNING, "Falha ao gravar perfil " + p.id(), ex);
                    return null;
                });
            }
        }
    }

    /** Ao desligar: grava tudo nesta thread. */
    public void flushSync() {
        for (PlayerProfile p : online.values()) {
            try {
                db.sync(c -> {
                    write(c, p);
                    return null;
                });
            } catch (SQLException e) {
                core.getLogger().log(Level.WARNING, "Falha ao gravar perfil " + p.id(), e);
            }
        }
    }

    /** Carrega antes de entrar (já roda fora da thread principal). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            PlayerProfile p = db.sync(c -> read(c, event.getUniqueId()));
            online.put(event.getUniqueId(), p);
        } catch (SQLException e) {
            core.getLogger().log(Level.SEVERE, "Não foi possível carregar o perfil de " + event.getName(), e);
            online.put(event.getUniqueId(), new PlayerProfile(event.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerProfile p = online.remove(event.getPlayer().getUniqueId());
        if (p != null && p.isDirty()) {
            save(p).exceptionally(ex -> {
                core.getLogger().log(Level.WARNING, "Falha ao gravar perfil de " + event.getPlayer().getName(), ex);
                return null;
            });
        }
    }

    /** Reload do core com jogadores online: garante perfil para quem já está no servidor. */
    public void loadOnlineSync() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                online.put(p.getUniqueId(), db.sync(c -> read(c, p.getUniqueId())));
            } catch (SQLException e) {
                core.getLogger().log(Level.WARNING, "Falha ao carregar perfil de " + p.getName(), e);
            }
        }
    }
}
