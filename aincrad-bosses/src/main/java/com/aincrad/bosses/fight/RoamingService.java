package com.aincrad.bosses.fight;

import com.aincrad.bosses.api.BossDefeatEvent;
import com.aincrad.bosses.api.BossFightEndEvent;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.RoamingRules;
import com.aincrad.bosses.model.RoamingRules.Entry;
import com.aincrad.bosses.model.RoamingRules.Window;
import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Os chefes de campo e de evento (roaming.yml). Aparecem soltos numa região, quando há alguém por
 * perto; quem chega depois entra na luta; nenhum libera andar. O estado (quando o de campo volta,
 * quando o de evento foi derrotado) fica no banco, então vale para a rede e sobrevive ao reinício.
 */
public final class RoamingService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS boss_roaming (id VARCHAR(64) NOT NULL PRIMARY KEY, next_at BIGINT NOT NULL, "
                    + "last_killed BIGINT NOT NULL)");

    private static final double NEAR = 40;
    private static final long RETRY_MS = 60_000L;

    private final Plugin plugin;
    private final Messages msg;
    private final FightManager fights;
    private final Database db;
    private Map<String, Entry> entries = Map.of();
    private List<String> errors = List.of();
    private final Map<String, long[]> state = new HashMap<>();
    private final Set<String> announced = new HashSet<>();

    public RoamingService(Plugin plugin, Messages msg, FightManager fights, Database db) {
        this.plugin = plugin;
        this.msg = msg;
        this.fights = fights;
        this.db = db;
    }

    public void reload(ConfigFile file) {
        List<String> errs = new ArrayList<>();
        entries = RoamingRules.parse(file.yaml(), errs);
        for (Entry e : entries.values()) {
            if (fights.registry().get(e.boss()) == null) {
                errs.add(e.id() + ": o chefe '" + e.boss() + "' não existe em bosses/");
            }
        }
        errors = List.copyOf(errs);
        errs.forEach(e -> plugin.getLogger().warning("roaming.yml: " + e));
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    public List<String> errors() {
        return errors;
    }

    public void loadBlocking() throws SQLException {
        Map<String, long[]> m = new HashMap<>();
        db.sync(c -> Database.query(c, "SELECT id, next_at, last_killed FROM boss_roaming",
                rs -> m.put(rs.getString(1), new long[] {rs.getLong(2), rs.getLong(3)})));
        state.clear();
        state.putAll(m);
    }

    /** Relê o estado (outro servidor da rede pode ter derrotado o chefe). */
    public void refresh() {
        db.async(c -> {
            Map<String, long[]> m = new HashMap<>();
            Database.query(c, "SELECT id, next_at, last_killed FROM boss_roaming",
                    rs -> m.put(rs.getString(1), new long[] {rs.getLong(2), rs.getLong(3)}));
            return m;
        }).thenAccept(m -> AincradCore.get().runSync(() -> {
            state.clear();
            state.putAll(m);
        })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler o estado dos chefes de campo", ex);
            return null;
        });
    }

    private long[] of(String id) {
        return state.computeIfAbsent(id, k -> new long[] {0, 0});
    }

    private void save(String id) {
        long[] s = of(id);
        db.update(db.upsert("boss_roaming", List.of("id"), List.of("next_at", "last_killed")), id, s[0], s[1]);
    }

    private Entry byBoss(String bossId) {
        for (Entry e : entries.values()) {
            if (e.boss().equals(bossId)) {
                return e;
            }
        }
        return null;
    }

    /** Quando o chefe de campo volta (0 = pode aparecer já). */
    public long nextAt(String id) {
        return of(id)[0];
    }

    // ------------------------------------------------------------------ aparecer

    private static Location spot(Entry e, Region r, World w) {
        Location fixed = e.spot() == null ? null : com.aincrad.bosses.data.RoomStore.location(e.spot());
        if (fixed != null) {
            return fixed;
        }
        int x = (r.minX() + r.maxX()) / 2;
        int z = (r.minZ() + r.maxZ()) / 2;
        int y = Math.max(r.minY(), Math.min(r.maxY(), w.getHighestBlockYAt(x, z) + 1));
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    /** A cada 10 segundos: quem está na hora de aparecer e tem alguém por perto aparece. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Entry e : entries.values()) {
            BossDef def = fights.registry().get(e.boss());
            Region r = AincradCore.get().regions().get(e.region());
            World w = r == null ? null : Bukkit.getWorld(r.world());
            if (def == null || w == null || fights.fight(def.id()) != null) {
                continue;
            }
            long[] s = of(e.id());
            long despawn = 0;
            if (e.event()) {
                Window win = RoamingRules.window(e, now, ZoneId.systemDefault());
                if (!RoamingRules.eventActive(win, now, s[1])) {
                    announced.remove(e.id());
                    continue;
                }
                despawn = win.end();
                // O aviso sai uma vez por janela, apareça o chefe ou não: ele está lá esperando.
                if (e.announce() && announced.add(e.id())) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        msg.send(p, "campo.evento_apareceu", Messages.vars("chefe", def.name(), "regiao", e.region()));
                    }
                }
            } else if (!RoamingRules.fieldDue(now, s[0])) {
                continue;
            }
            Location at = spot(e, r, w);
            if (!at.isChunkLoaded() || !someoneNear(at)) {
                continue;
            }
            if (fights.summonRoaming(def, at, despawn) && !e.event() && e.announce()) {
                for (Player p : w.getPlayers()) {
                    msg.send(p, "campo.apareceu", Messages.vars("chefe", def.name()));
                }
            }
        }
    }

    private static boolean someoneNear(Location at) {
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR && !p.isDead() && p.getLocation().distanceSquared(at) <= NEAR * NEAR) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ derrotado ou sumiu

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDefeat(BossDefeatEvent event) {
        Entry e = byBoss(event.getBossId());
        if (e == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long[] s = of(e.id());
        s[0] = now + e.intervalMs();
        s[1] = now;
        save(e.id());
    }

    /** A luta acabou sem vitória (o chefe sumiu, o tempo do evento acabou): o de campo tenta de novo logo. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(BossFightEndEvent event) {
        Entry e = byBoss(event.getBossId());
        if (e != null && !e.event()) {
            of(e.id())[0] = System.currentTimeMillis() + RETRY_MS;
        }
    }
}
