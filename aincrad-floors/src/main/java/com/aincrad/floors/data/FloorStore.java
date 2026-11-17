package com.aincrad.floors.data;

import com.aincrad.core.db.Database;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado dos andares no banco do core (vale para a rede): quais estão liberados, quando e por
 * quem; e, por jogador, quais ele visitou e a quais tem acesso (modos por_jogador e misto).
 * Tudo fica em memória para as consultas da thread principal; as gravações vão fora dela.
 */
public final class FloorStore {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS floor_state (floor INT NOT NULL PRIMARY KEY, unlocked INT NOT NULL, "
                    + "unlocked_at BIGINT NOT NULL, participants TEXT);"
                    + "CREATE TABLE IF NOT EXISTS player_floors (uuid VARCHAR(36) NOT NULL, floor INT NOT NULL, "
                    + "visited INT NOT NULL, granted INT NOT NULL, PRIMARY KEY (uuid, floor));"
                    + "CREATE TABLE IF NOT EXISTS floor_gates (ref VARCHAR(160) NOT NULL PRIMARY KEY, floor INT NOT NULL)");

    /** Um andar liberado. {@code participants}: nomes separados por vírgula (pode ser vazio). */
    public record Unlock(int floor, long at, String participants) {
    }

    /** O que se sabe de um jogador. */
    public static final class PlayerFloors {
        public final Set<Integer> visited = ConcurrentHashMap.newKeySet();
        public final Set<Integer> granted = ConcurrentHashMap.newKeySet();
    }

    private final Database db;
    private volatile Map<Integer, Unlock> unlocks = Map.of();
    private volatile Map<String, Integer> gates = Map.of();
    private final Map<UUID, PlayerFloors> players = new ConcurrentHashMap<>();

    public FloorStore(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ andares

    private record State(Map<Integer, Unlock> unlocks, Map<String, Integer> gates) {
    }

    private State read(java.sql.Connection c) throws SQLException {
        Map<Integer, Unlock> u = new HashMap<>();
        for (Unlock x : Database.query(c, "SELECT floor, unlocked_at, participants FROM floor_state WHERE unlocked = 1",
                rs -> new Unlock(rs.getInt(1), rs.getLong(2), rs.getString(3) == null ? "" : rs.getString(3)))) {
            u.put(x.floor(), x);
        }
        Map<String, Integer> g = new HashMap<>();
        for (Object[] row : Database.query(c, "SELECT ref, floor FROM floor_gates", rs -> new Object[]{rs.getString(1), rs.getInt(2)})) {
            g.put((String) row[0], (Integer) row[1]);
        }
        return new State(u, g);
    }

    /** Lê andares liberados e Gates nesta thread (ao ligar). */
    public void loadBlocking() throws SQLException {
        State s = db.sync(this::read);
        unlocks = s.unlocks();
        gates = s.gates();
    }

    /** Relê do banco (outro servidor avisou que mudou). {@code after} roda fora da thread principal. */
    public CompletableFuture<Void> reloadAsync() {
        return db.async(this::read).thenAccept(s -> {
            unlocks = s.unlocks();
            gates = s.gates();
        });
    }

    public Set<Integer> unlocked() {
        return unlocks.keySet();
    }

    public Unlock unlock(int floor) {
        return unlocks.get(floor);
    }

    /** Marca como liberado (na memória já; no banco em seguida). */
    public CompletableFuture<Integer> setUnlocked(int floor, long at, String participants) {
        Map<Integer, Unlock> m = new HashMap<>(unlocks);
        m.put(floor, new Unlock(floor, at, participants));
        unlocks = m;
        return db.update(db.upsert("floor_state", List.of("floor"), List.of("unlocked", "unlocked_at", "participants")),
                floor, 1, at, participants);
    }

    public CompletableFuture<Integer> setLocked(int floor) {
        Map<Integer, Unlock> m = new HashMap<>(unlocks);
        m.remove(floor);
        unlocks = m;
        return db.update("UPDATE floor_state SET unlocked = 0 WHERE floor = ?", floor);
    }

    // ------------------------------------------------------------------ gates

    public Integer gate(String ref) {
        return gates.get(ref);
    }

    public Map<String, Integer> gates() {
        return gates;
    }

    public CompletableFuture<Integer> setGate(String ref, int floor) {
        Map<String, Integer> m = new HashMap<>(gates);
        m.put(ref, floor);
        gates = m;
        return db.update(db.upsert("floor_gates", List.of("ref"), List.of("floor")), ref, floor);
    }

    public CompletableFuture<Integer> removeGate(String ref) {
        Map<String, Integer> m = new HashMap<>(gates);
        m.remove(ref);
        gates = m;
        return db.update("DELETE FROM floor_gates WHERE ref = ?", ref);
    }

    // ------------------------------------------------------------------ jogadores

    /** Lê o jogador nesta thread (pré-login). */
    public void loadPlayerBlocking(UUID id) throws SQLException {
        PlayerFloors pf = new PlayerFloors();
        db.sync(c -> {
            for (int[] row : Database.query(c, "SELECT floor, visited, granted FROM player_floors WHERE uuid = ?",
                    rs -> new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)}, id.toString())) {
                if (row[1] != 0) {
                    pf.visited.add(row[0]);
                }
                if (row[2] != 0) {
                    pf.granted.add(row[0]);
                }
            }
            return null;
        });
        players.put(id, pf);
    }

    /** Dados do jogador online (vazio se o pré-login falhou). */
    public PlayerFloors player(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerFloors());
    }

    public void forget(UUID id) {
        players.remove(id);
    }

    /** Marca que o jogador pisou no andar. @return true se é a primeira vez */
    public boolean visit(UUID id, int floor) {
        if (!player(id).visited.add(floor)) {
            return false;
        }
        write(id, floor);
        return true;
    }

    /** Dá ou tira o acesso (modo por_jogador). Vale também para quem está offline. */
    public void grant(UUID id, int floor, boolean on) {
        PlayerFloors pf = players.get(id);
        if (pf != null) {
            if (on) {
                pf.granted.add(floor);
            } else {
                pf.granted.remove(floor);
            }
            write(id, floor);
            return;
        }
        String key = id.toString();
        db.async(c -> {
            int n = Database.update(c, "UPDATE player_floors SET granted = ? WHERE uuid = ? AND floor = ?", on ? 1 : 0, key, floor);
            if (n == 0 && on) {
                Database.update(c, "INSERT INTO player_floors (uuid, floor, visited, granted) VALUES (?, ?, 0, 1)", key, floor);
            }
            return null;
        });
    }

    private void write(UUID id, int floor) {
        PlayerFloors pf = player(id);
        db.update(db.upsert("player_floors", List.of("uuid", "floor"), List.of("visited", "granted")),
                id.toString(), floor, pf.visited.contains(floor) ? 1 : 0, pf.granted.contains(floor) ? 1 : 0);
    }

    /** Só para os testes: jogadores em memória. */
    Set<UUID> loadedPlayers() {
        return new HashSet<>(players.keySet());
    }
}
