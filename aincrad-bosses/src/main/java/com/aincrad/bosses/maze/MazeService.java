package com.aincrad.bosses.maze;

import com.aincrad.bosses.hook.Hooks;
import com.aincrad.bosses.model.Cuboid;
import com.aincrad.core.config.ConfigFile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O labirinto de cada andar: zonas de spawn com level de mob, limite e intervalo. O admin cria as
 * zonas com levels cada vez maiores conforme chega perto da sala do chefe. Os mobs só nascem com
 * jogador por perto e não são salvos com o mundo.
 */
public final class MazeService {

    /** Uma zona de labirinto.yml. */
    public record Zone(String id, int floor, int level, Cuboid box, List<String> mobs, int limit, int intervalSeconds) {
    }

    /** Distância (blocos) de um jogador até a zona para ela ficar ativa. */
    private static final int ACTIVE_RANGE = 40;

    private final Plugin plugin;
    private final ConfigFile config;
    private final Hooks hooks;
    private final File file;
    private final NamespacedKey key;
    private final Map<String, Zone> zones = new TreeMap<>();
    private final Map<String, List<LivingEntity>> alive = new HashMap<>();
    private final Map<String, Long> lastSpawn = new HashMap<>();

    public MazeService(Plugin plugin, ConfigFile config, Hooks hooks) {
        this.plugin = plugin;
        this.config = config;
        this.hooks = hooks;
        this.file = new File(plugin.getDataFolder(), "labirinto.yml");
        this.key = new NamespacedKey(plugin, "labirinto");
        load();
    }

    public void load() {
        zones.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("zonas");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            Cuboid box = s == null ? null : Cuboid.parse(s.getString("area"));
            if (box == null) {
                plugin.getLogger().warning("labirinto.yml: a zona " + id + " não tem \"area\" válida.");
                continue;
            }
            List<String> mobs = s.getStringList("mobs");
            zones.put(id, new Zone(id, s.getInt("andar", 1), Math.max(1, s.getInt("nivel", 1)), box,
                    mobs.isEmpty() ? defaultMobs() : List.copyOf(mobs), Math.max(1, s.getInt("limite", defaultLimit())),
                    Math.max(1, s.getInt("intervalo", defaultInterval()))));
        }
    }

    private List<String> defaultMobs() {
        List<String> m = config.yaml().getStringList("labirinto.mobs");
        return m.isEmpty() ? List.of("ZOMBIE", "SKELETON", "SPIDER") : List.copyOf(m);
    }

    private int defaultLimit() {
        return config.integer("labirinto.limite", 6, 1, 200);
    }

    private int defaultInterval() {
        return config.integer("labirinto.intervalo_segundos", 8, 1, 3600);
    }

    private void save() throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Zonas do labirinto. Criadas por /aincrad labirinto zona <andar> <nivel>;",
                "mobs, limite e intervalo de cada zona podem ser editados aqui (depois, /aincrad reload)."));
        for (Zone z : zones.values()) {
            String b = "zonas." + z.id() + ".";
            y.set(b + "andar", z.floor());
            y.set(b + "nivel", z.level());
            y.set(b + "area", z.box().text());
            y.set(b + "mobs", z.mobs());
            y.set(b + "limite", z.limit());
            y.set(b + "intervalo", z.intervalSeconds());
        }
        y.save(file);
    }

    public Map<String, Zone> zones() {
        return zones;
    }

    /** Cria a zona "a&lt;andar&gt;_&lt;n&gt;" com os padrões da config. */
    public Zone create(int floor, int level, Cuboid box) throws IOException {
        int n = 1;
        while (zones.containsKey("a" + floor + "_" + n)) {
            n++;
        }
        Zone z = new Zone("a" + floor + "_" + n, floor, level, box, defaultMobs(), defaultLimit(), defaultInterval());
        zones.put(z.id(), z);
        save();
        return z;
    }

    public boolean remove(String id) throws IOException {
        if (zones.remove(id.toLowerCase(Locale.ROOT)) == null) {
            return false;
        }
        clear(id.toLowerCase(Locale.ROOT));
        save();
        return true;
    }

    private void clear(String id) {
        List<LivingEntity> list = alive.remove(id);
        if (list != null) {
            list.forEach(Entity::remove);
        }
    }

    public void clearAll() {
        for (String id : new ArrayList<>(alive.keySet())) {
            clear(id);
        }
    }

    /** A zona (a menor) que contém o lugar, ou null. */
    public Zone at(Location l) {
        Zone best = null;
        for (Zone z : zones.values()) {
            if (z.box().contains(l) && (best == null || z.box().volume() < best.box().volume())) {
                best = z;
            }
        }
        return best;
    }

    /** Roda uma vez por segundo. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Zone z : zones.values()) {
            World w = Bukkit.getWorld(z.box().world());
            if (w == null) {
                continue;
            }
            List<LivingEntity> list = alive.computeIfAbsent(z.id(), k -> new ArrayList<>());
            list.removeIf(e -> !e.isValid() || e.isDead());
            if (list.size() >= z.limit() || now - lastSpawn.getOrDefault(z.id(), 0L) < z.intervalSeconds() * 1000L || !active(z, w)) {
                continue;
            }
            lastSpawn.put(z.id(), now);
            LivingEntity e = spawn(z, w);
            if (e != null) {
                list.add(e);
            }
        }
    }

    private static boolean active(Zone z, World w) {
        Cuboid b = z.box();
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (p.getGameMode() != GameMode.SPECTATOR
                    && l.getX() >= b.minX() - ACTIVE_RANGE && l.getX() <= b.maxX() + ACTIVE_RANGE
                    && l.getZ() >= b.minZ() - ACTIVE_RANGE && l.getZ() <= b.maxZ() + ACTIVE_RANGE
                    && l.getY() >= b.minY() - ACTIVE_RANGE && l.getY() <= b.maxY() + ACTIVE_RANGE) {
                return true;
            }
        }
        return false;
    }

    private LivingEntity spawn(Zone z, World w) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Cuboid b = z.box();
        EntityType type;
        try {
            type = EntityType.valueOf(z.mobs().get(rnd.nextInt(z.mobs().size())).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!type.isAlive() || !type.isSpawnable()) {
            return null;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = rnd.nextInt(b.minX(), b.maxX() + 1);
            int zz = rnd.nextInt(b.minZ(), b.maxZ() + 1);
            if (!w.isChunkLoaded(x >> 4, zz >> 4)) {
                continue;
            }
            // De cima para baixo: o primeiro chão com dois blocos livres em cima.
            for (int y = Math.min(b.maxY(), w.getMaxHeight() - 2); y > Math.max(b.minY(), w.getMinHeight()); y--) {
                Block floor = w.getBlockAt(x, y - 1, zz);
                if (floor.getType().isSolid() && w.getBlockAt(x, y, zz).isPassable() && w.getBlockAt(x, y + 1, zz).isPassable()
                        && !w.getBlockAt(x, y, zz).isLiquid()) {
                    Location at = new Location(w, x + 0.5, y, zz + 0.5, rnd.nextFloat() * 360, 0);
                    if (tooClose(at, w)) {
                        break;
                    }
                    Entity e = w.spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM, raw -> {
                        raw.getPersistentDataContainer().set(key, PersistentDataType.STRING, z.id());
                        raw.setPersistent(false);
                    });
                    if (e instanceof LivingEntity le) {
                        hooks.setMobLevel(le, z.level());
                        return le;
                    }
                    e.remove();
                    return null;
                }
            }
        }
        return null;
    }

    /** Não nasce na cara de ninguém. */
    private static boolean tooClose(Location at, World w) {
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) < 36) {
                return true;
            }
        }
        return false;
    }
}
