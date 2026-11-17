package com.aincrad.bosses.data;

import com.aincrad.core.db.Database;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * O que a rede sabe de cada chefe: se já foi derrotado, quando foi a primeira e a última vez,
 * quem deu o Last Attack e quem participou. No banco do core; em memória para consultas.
 */
public final class BossStateStore {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS boss_state (id VARCHAR(64) NOT NULL PRIMARY KEY, kills INT NOT NULL, "
                    + "first_at BIGINT NOT NULL, last_at BIGINT NOT NULL, last_attack VARCHAR(16), participants TEXT)");

    /** @param participants nomes da primeira vitória, separados por vírgula */
    public record State(String id, int kills, long firstAt, long lastAt, String lastAttack, String participants) {
    }

    private final Database db;
    private volatile Map<String, State> states = Map.of();

    public BossStateStore(Database db) {
        this.db = db;
    }

    private static Map<String, State> read(java.sql.Connection c) throws SQLException {
        Map<String, State> m = new HashMap<>();
        for (State s : Database.query(c, "SELECT id, kills, first_at, last_at, last_attack, participants FROM boss_state",
                rs -> new State(rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getString(5), rs.getString(6)))) {
            m.put(s.id(), s);
        }
        return m;
    }

    public void loadBlocking() throws SQLException {
        states = db.sync(BossStateStore::read);
    }

    public CompletableFuture<Void> reloadAsync() {
        return db.async(BossStateStore::read).thenAccept(m -> states = m);
    }

    public State get(String id) {
        return states.get(id);
    }

    public boolean defeated(String id) {
        State s = states.get(id);
        return s != null && s.kills() > 0;
    }

    /** Registra uma vitória. A primeira guarda os participantes; as seguintes só contam. */
    public CompletableFuture<Integer> recordKill(String id, long at, String lastAttack, String participants) {
        State old = states.get(id);
        State now = old == null || old.kills() == 0
                ? new State(id, 1, at, at, lastAttack, participants)
                : new State(id, old.kills() + 1, old.firstAt(), at, old.lastAttack(), old.participants());
        Map<String, State> m = new HashMap<>(states);
        m.put(id, now);
        states = m;
        return db.update(db.upsert("boss_state", List.of("id"), List.of("kills", "first_at", "last_at", "last_attack", "participants")),
                id, now.kills(), now.firstAt(), now.lastAt(), now.lastAttack(), now.participants());
    }

    /** Esquece as vitórias (o chefe volta a liberar o andar). */
    public CompletableFuture<Integer> clear(String id) {
        Map<String, State> m = new HashMap<>(states);
        m.remove(id);
        states = m;
        return db.update("DELETE FROM boss_state WHERE id = ?", id);
    }
}
