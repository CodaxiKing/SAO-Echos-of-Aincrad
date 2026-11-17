package com.aincrad.rpg.data;

import com.aincrad.core.db.Database;
import com.aincrad.rpg.model.Progress;
import com.aincrad.rpg.model.Stat;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Progresso dos jogadores online. Carrega no pré-login (thread do login, fora da principal) e
 * grava ao sair e de tempos em tempos, sempre fora da thread principal.
 */
public final class PlayerStore {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS rpg_players ("
                    + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "level INT NOT NULL, xp BIGINT NOT NULL, points INT NOT NULL, "
                    + "forca INT NOT NULL, agilidade INT NOT NULL, vitalidade INT NOT NULL, "
                    + "updated BIGINT NOT NULL)");

    private static final List<String> COLS = List.of("level", "xp", "points", "forca", "agilidade", "vitalidade", "updated");

    private final Database db;
    private final Logger log;
    private final Map<UUID, Progress> online = new ConcurrentHashMap<>();
    private final Consumer<Runnable> sync;
    private final Predicate<UUID> isOnline;

    /**
     * @param sync     roda na thread principal
     * @param isOnline se o jogador está no servidor (checado na thread principal)
     */
    public PlayerStore(Database db, Logger log, Consumer<Runnable> sync, Predicate<UUID> isOnline) {
        this.db = db;
        this.log = log;
        this.sync = sync;
        this.isOnline = isOnline;
    }

    /** Lê do banco nesta thread (use no {@code AsyncPlayerPreLoginEvent}). Sem registro: level 1. */
    public void loadBlocking(UUID id) throws SQLException {
        if (online.containsKey(id)) {
            return; // saiu e voltou rápido: o da memória é o mais novo
        }
        List<Progress> rows = db.sync(c -> Database.query(c,
                "SELECT level, xp, points, forca, agilidade, vitalidade FROM rpg_players WHERE uuid = ?",
                rs -> Progress.of(rs.getInt(1), rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)),
                id.toString()));
        online.put(id, rows.isEmpty() ? new Progress() : rows.get(0));
    }

    /**
     * Progresso do jogador online. Se não está na memória (o pré-login falhou ou ele voltou bem na
     * hora em que saía), lê do banco aqui mesmo — caso raro; sem banco, começa do level 1.
     */
    public Progress get(UUID id) {
        Progress p = online.get(id);
        if (p != null) {
            return p;
        }
        try {
            loadBlocking(id);
        } catch (SQLException e) {
            log.warning("Não foi possível carregar o RPG de " + id + ": " + e.getMessage());
            online.putIfAbsent(id, new Progress());
        }
        return online.get(id);
    }

    public boolean isLoaded(UUID id) {
        return online.containsKey(id);
    }

    public CompletableFuture<Integer> save(UUID id) {
        Progress p = online.get(id);
        if (p == null || !p.dirty()) {
            return CompletableFuture.completedFuture(0);
        }
        p.clean();
        Object[] params = params(id, p);
        return db.update(db.upsert("rpg_players", List.of("uuid"), COLS), params)
                .whenComplete((n, err) -> {
                    if (err != null) {
                        log.warning("Não foi possível salvar o RPG de " + id + ": " + err.getMessage());
                    }
                });
    }

    /** Grava e tira da memória (ao sair), a não ser que o jogador já tenha voltado. */
    public void unload(UUID id) {
        save(id).whenComplete((n, err) -> sync.accept(() -> {
            if (!isOnline.test(id)) {
                online.remove(id);
            }
        }));
    }

    public void saveAllAsync() {
        for (UUID id : online.keySet()) {
            save(id);
        }
    }

    /** Ao desligar: grava todos nesta thread. */
    public void saveAllBlocking() {
        String sql = db.upsert("rpg_players", List.of("uuid"), COLS);
        for (Map.Entry<UUID, Progress> e : online.entrySet()) {
            if (!e.getValue().dirty()) {
                continue;
            }
            e.getValue().clean();
            Object[] params = params(e.getKey(), e.getValue());
            try {
                db.sync(c -> Database.update(c, sql, params));
            } catch (SQLException ex) {
                log.warning("Não foi possível salvar o RPG de " + e.getKey() + ": " + ex.getMessage());
            }
        }
    }

    private static Object[] params(UUID id, Progress p) {
        return new Object[]{id.toString(), p.level(), p.xp(), p.points(),
                p.stat(Stat.FORCA), p.stat(Stat.AGILIDADE), p.stat(Stat.VITALIDADE), System.currentTimeMillis()};
    }
}
