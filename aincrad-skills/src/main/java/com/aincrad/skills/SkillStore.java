package com.aincrad.skills;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.skills.model.Passive;
import com.aincrad.skills.model.SkillRules;
import com.aincrad.skills.model.WeaponType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** A proficiência por arma e as skills equipadas de cada jogador, no banco do core. */
public final class SkillStore {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS skill_prof (uuid VARCHAR(36) NOT NULL, weapon VARCHAR(16) NOT NULL, value INT NOT NULL, "
                    + "PRIMARY KEY (uuid, weapon))",
            "CREATE TABLE IF NOT EXISTS skill_slots (uuid VARCHAR(36) NOT NULL, slot INT NOT NULL, skill VARCHAR(64) NOT NULL, "
                    + "PRIMARY KEY (uuid, slot))",
            "CREATE TABLE IF NOT EXISTS skill_unique (skill VARCHAR(32) NOT NULL PRIMARY KEY, uuid VARCHAR(36) NOT NULL, "
                    + "name VARCHAR(16) NOT NULL, at_time BIGINT NOT NULL)");

    /** Os espaços das passivas ficam a partir deste número na tabela. */
    private static final int PASSIVE_BASE = 10;

    /** Os dados de um jogador online. */
    public static final class Data {
        private final Map<WeaponType, Integer> prof = new EnumMap<>(WeaponType.class);
        private final String[] active = new String[SkillRules.ACTIVE_SLOTS];
        private final Passive[] passive = new Passive[SkillRules.PASSIVE_SLOTS];

        public int prof(WeaponType t) {
            return prof.getOrDefault(t, 0);
        }

        /** A skill do atalho (0 a 3), ou null. */
        public String active(int slot) {
            return slot < 0 || slot >= active.length ? null : active[slot];
        }

        public Passive passive(int slot) {
            return slot < 0 || slot >= passive.length ? null : passive[slot];
        }

        public int slotOf(String skill) {
            for (int i = 0; i < active.length; i++) {
                if (skill.equals(active[i])) {
                    return i;
                }
            }
            return -1;
        }

        public int slotOf(Passive p) {
            for (int i = 0; i < passive.length; i++) {
                if (passive[i] == p) {
                    return i;
                }
            }
            return -1;
        }

        public boolean has(Passive p) {
            return slotOf(p) >= 0;
        }
    }

    private final Plugin plugin;
    private final Database db;
    private final Map<UUID, Data> cache = new ConcurrentHashMap<>();

    public SkillStore(Plugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Os dados do jogador, ou null enquanto ainda carregam. */
    public Data data(Player p) {
        return cache.get(p.getUniqueId());
    }

    public void load(UUID id) {
        String u = id.toString();
        db.async(c -> {
            Data d = new Data();
            Database.query(c, "SELECT weapon, value FROM skill_prof WHERE uuid = ?", rs -> {
                WeaponType t = WeaponType.parse(rs.getString(1));
                if (t != null) {
                    d.prof.put(t, Math.max(0, Math.min(SkillRules.MAX_PROF, rs.getInt(2))));
                }
                return null;
            }, u);
            Database.query(c, "SELECT slot, skill FROM skill_slots WHERE uuid = ?", rs -> {
                int slot = rs.getInt(1);
                if (slot >= 0 && slot < d.active.length) {
                    d.active[slot] = rs.getString(2);
                } else if (slot >= PASSIVE_BASE && slot < PASSIVE_BASE + d.passive.length) {
                    d.passive[slot - PASSIVE_BASE] = Passive.parse(rs.getString(2));
                }
                return null;
            }, u);
            return d;
        }).thenAccept(d -> AincradCore.get().runSync(() -> {
            if (plugin.getServer().getPlayer(id) != null) {
                cache.put(id, d);
            }
        })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível carregar as skills de " + id, ex);
            return null;
        });
    }

    public void unload(UUID id) {
        cache.remove(id);
    }

    public void setProf(UUID id, Data d, WeaponType t, int value) {
        int v = Math.max(0, Math.min(SkillRules.MAX_PROF, value));
        d.prof.put(t, v);
        String sql = db.upsert("skill_prof", List.of("uuid", "weapon"), List.of("value"));
        save(db.update(sql, id.toString(), t.key(), v), "a proficiência");
    }

    public void setActive(UUID id, Data d, int slot, String skill) {
        d.active[slot] = skill;
        slot(id, slot, skill);
    }

    public void setPassive(UUID id, Data d, int slot, Passive p) {
        d.passive[slot] = p;
        slot(id, PASSIVE_BASE + slot, p == null ? null : p.key());
    }

    private void slot(UUID id, int slot, String value) {
        if (value == null) {
            save(db.update("DELETE FROM skill_slots WHERE uuid = ? AND slot = ?", id.toString(), slot), "o espaço de skill");
        } else {
            String sql = db.upsert("skill_slots", List.of("uuid", "slot"), List.of("skill"));
            save(db.update(sql, id.toString(), slot, value), "o espaço de skill");
        }
    }

    private void save(java.util.concurrent.CompletableFuture<Integer> f, String what) {
        f.exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar " + what, ex);
            return 0;
        });
    }
}
