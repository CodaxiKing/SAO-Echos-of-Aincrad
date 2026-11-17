package com.aincrad.life.data;

import com.aincrad.core.db.Database;
import com.aincrad.life.model.Profession;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dados de profissão de cada jogador, no banco do core (valem para a rede): proficiência, o que
 * já aprendeu e os nós da árvore. Carregados no pré-login; cada mudança é gravada na hora, fora
 * da thread principal.
 */
public final class LifeStore {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_professions (uuid VARCHAR(36) NOT NULL, profession VARCHAR(16) NOT NULL, "
                    + "proficiency DOUBLE NOT NULL, PRIMARY KEY (uuid, profession));"
                    + "CREATE TABLE IF NOT EXISTS life_known (uuid VARCHAR(36) NOT NULL, kind VARCHAR(32) NOT NULL, "
                    + "id VARCHAR(64) NOT NULL, learned BIGINT NOT NULL, PRIMARY KEY (uuid, kind, id));"
                    + "CREATE TABLE IF NOT EXISTS life_tree (uuid VARCHAR(36) NOT NULL, profession VARCHAR(16) NOT NULL, "
                    + "node VARCHAR(64) NOT NULL, bought BIGINT NOT NULL, PRIMARY KEY (uuid, profession, node))");

    /** O que se sabe de um jogador. */
    public static final class PlayerLife {
        public final Map<Profession, Double> proficiency = new EnumMap<>(Profession.class);
        /** "tipo:id" de tudo o que aprendeu. */
        public final Set<String> known = ConcurrentHashMap.newKeySet();
        /** Por profissão: nós comprados, "caminho:x" e "espec:y". */
        public final Map<Profession, Set<String>> tree = new EnumMap<>(Profession.class);

        public PlayerLife() {
            for (Profession p : Profession.values()) {
                proficiency.put(p, 0.0);
                tree.put(p, ConcurrentHashMap.newKeySet());
            }
        }
    }

    private final Database db;
    private final Map<UUID, PlayerLife> players = new ConcurrentHashMap<>();

    public LifeStore(Database db) {
        this.db = db;
    }

    public Database db() {
        return db;
    }

    /** Lê o jogador nesta thread (pré-login). */
    public void loadBlocking(UUID id) throws SQLException {
        PlayerLife pl = new PlayerLife();
        String key = id.toString();
        db.sync(c -> {
            for (Object[] r : Database.query(c, "SELECT profession, proficiency FROM life_professions WHERE uuid = ?",
                    rs -> new Object[]{rs.getString(1), rs.getDouble(2)}, key)) {
                Profession p = Profession.parse((String) r[0]);
                if (p != null) {
                    pl.proficiency.put(p, Math.max(0, Math.min(Profession.MAX, (Double) r[1])));
                }
            }
            pl.known.addAll(Database.query(c, "SELECT kind, id FROM life_known WHERE uuid = ?",
                    rs -> rs.getString(1) + ":" + rs.getString(2), key));
            for (String[] r : Database.query(c, "SELECT profession, node FROM life_tree WHERE uuid = ?",
                    rs -> new String[]{rs.getString(1), rs.getString(2)}, key)) {
                Profession p = Profession.parse(r[0]);
                if (p != null) {
                    pl.tree.get(p).add(r[1]);
                }
            }
            return null;
        });
        players.put(id, pl);
    }

    /** Dados do jogador online (vazios se o pré-login falhou). */
    public PlayerLife get(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerLife());
    }

    public void forget(UUID id) {
        players.remove(id);
    }

    public void saveProficiency(UUID id, Profession p, double value) {
        db.update(db.upsert("life_professions", List.of("uuid", "profession"), List.of("proficiency")), id.toString(), p.name(), value);
    }

    public void saveKnown(UUID id, String kind, String what) {
        db.update(db.upsert("life_known", List.of("uuid", "kind", "id"), List.of("learned")), id.toString(), kind, what, System.currentTimeMillis());
    }

    public void deleteKnown(UUID id, String kind, String what) {
        db.update("DELETE FROM life_known WHERE uuid = ? AND kind = ? AND id = ?", id.toString(), kind, what);
    }

    public void saveNode(UUID id, Profession p, String node) {
        db.update(db.upsert("life_tree", List.of("uuid", "profession", "node"), List.of("bought")), id.toString(), p.name(), node, System.currentTimeMillis());
    }

    public void clearTree(UUID id, Profession p) {
        db.update("DELETE FROM life_tree WHERE uuid = ? AND profession = ?", id.toString(), p.name());
    }
}
