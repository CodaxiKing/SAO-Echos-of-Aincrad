package com.aincrad.bosses.data;

import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.GuideData;
import com.aincrad.core.db.Database;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guia de estratégia: o que a rede já viu de cada chefe. Fica no banco do core; cada novidade
 * (tentativa, fase ou habilidade vista pela primeira vez) é gravada na hora.
 */
public final class GuideStore {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS boss_guide (id VARCHAR(64) NOT NULL PRIMARY KEY, attempts INT NOT NULL, "
                    + "best_phase INT NOT NULL, seen TEXT)");

    private final Database db;
    private final Map<String, GuideData> guides = new ConcurrentHashMap<>();

    public GuideStore(Database db) {
        this.db = db;
    }

    private static Map<String, GuideData> read(java.sql.Connection c) throws SQLException {
        Map<String, GuideData> m = new HashMap<>();
        for (Object[] row : Database.query(c, "SELECT id, attempts, best_phase, seen FROM boss_guide",
                rs -> new Object[]{rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4)})) {
            m.put((String) row[0], new GuideData((Integer) row[1], (Integer) row[2], (String) row[3]));
        }
        return m;
    }

    public void loadBlocking() throws SQLException {
        guides.clear();
        guides.putAll(db.sync(GuideStore::read));
    }

    public CompletableFuture<Void> reloadAsync() {
        return db.async(GuideStore::read).thenAccept(m -> {
            guides.clear();
            guides.putAll(m);
        });
    }

    /** O guia do chefe (vazio se ninguém nunca tentou). */
    public GuideData get(String id) {
        return guides.computeIfAbsent(id, k -> new GuideData());
    }

    private void save(String id) {
        GuideData g = get(id);
        db.update(db.upsert("boss_guide", List.of("id"), List.of("attempts", "best_phase", "seen")),
                id, g.attempts(), g.bestPhase(), g.seenText());
    }

    public void attempt(String id) {
        get(id).attempt();
        save(id);
    }

    public void phase(String id, int index) {
        if (get(id).seePhase(index)) {
            save(id);
        }
    }

    public void skill(String id, int phase, BossDef.SkillType type) {
        if (get(id).seeSkill(phase, type)) {
            save(id);
        }
    }

    public CompletableFuture<Integer> clear(String id) {
        guides.remove(id);
        return db.update("DELETE FROM boss_guide WHERE id = ?", id);
    }
}
