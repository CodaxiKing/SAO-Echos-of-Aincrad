package com.aincrad.rpg.mob;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.region.Region;
import com.aincrad.rpg.RpgSettings;
import com.aincrad.rpg.item.ItemService;
import com.aincrad.rpg.model.MobRewards;
import com.aincrad.rpg.model.SpawnRules;
import com.aincrad.rpg.model.SpawnRules.Area;
import com.aincrad.rpg.model.SpawnRules.Entry;
import com.aincrad.rpg.model.SpawnRules.Kind;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mobs por região (spawns.yml): em cada região do AincradCore listada, mantém até um máximo de
 * mobs vivos perto dos jogadores, sorteados entre mobs do Minecraft e do MythicMobs. Os mobs não
 * são salvos com o mapa: somem quando o pedaço descarrega ou ninguém está por perto.
 */
public final class SpawnService implements Listener {

    private static final int TRIES = 8;
    private static final int Y_SCAN = 24;

    private final Plugin plugin;
    private final MobService mobs;
    private final ItemService items;
    private final MythicBridge mythic = new MythicBridge();
    private final ModelBridge models = new ModelBridge();
    private final NamespacedKey keyRegion;
    private final Map<String, Set<UUID>> alive = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Map<String, Area> areas = Map.of();
    private List<String> errors = List.of();
    private BukkitTask task;

    public SpawnService(Plugin plugin, MobService mobs, ItemService items) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.items = items;
        keyRegion = new NamespacedKey(plugin, "spawn_regiao");
    }

    /** Lê o spawns.yml e (re)começa as rodadas. */
    public void reload(ConfigFile file) {
        List<String> errs = new ArrayList<>();
        areas = SpawnRules.parse(file.yaml().getConfigurationSection("regioes"), errs);
        errors = List.copyOf(errs);
        warned.clear();
        for (String e : errors) {
            plugin.getLogger().warning("spawns.yml: " + e);
        }
        // Regiões que saíram do arquivo: os mobs delas somem.
        for (Iterator<Map.Entry<String, Set<UUID>>> it = alive.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Set<UUID>> en = it.next();
            if (!areas.containsKey(en.getKey())) {
                removeAll(en.getValue());
                it.remove();
            }
        }
        if (task != null) {
            task.cancel();
        }
        long every = file.integer("intervalo", 5, 1, 600) * 20L;
        task = areas.isEmpty() ? null : Bukkit.getScheduler().runTaskTimer(plugin, this::round, every, every);
    }

    /** Ao desligar: tira do mundo tudo o que este serviço criou. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        alive.values().forEach(SpawnService::removeAll);
        alive.clear();
    }

    private static void removeAll(Set<UUID> ids) {
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        ids.clear();
    }

    public Map<String, Area> areas() {
        return areas;
    }

    public List<String> errors() {
        return errors;
    }

    public int alive(String region) {
        Set<UUID> s = alive.get(region);
        return s == null ? 0 : s.size();
    }

    /** O que impede a região de funcionar agora (null = nada). */
    public String problem(Area a) {
        Region r = AincradCore.get().regions().get(a.region());
        if (r == null) {
            return "a região não existe (/aincrad regiao criar " + a.region() + ")";
        }
        if (Bukkit.getWorld(r.world()) == null) {
            return "o mundo '" + r.world() + "' não está carregado neste servidor";
        }
        for (Entry e : a.mobs()) {
            String p = problem(e);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private String problem(Entry e) {
        if (e.mob().kind() == Kind.MYTHIC) {
            if (!mythic.available()) {
                return "o MythicMobs não está ligado (" + e.mob().text() + ")";
            }
            if (!mythic.exists(e.mob().id())) {
                return "o MythicMobs não conhece o mob '" + e.mob().id() + "'";
            }
        } else if (type(e.mob().id()) == null) {
            return "'" + e.mob().id() + "' não é um mob do Minecraft";
        }
        if (e.model() != null && !models.available()) {
            return "o ModelEngine não está ligado (modelo '" + e.model() + "')";
        }
        return null;
    }

    private static EntityType type(String id) {
        try {
            EntityType t = EntityType.valueOf(id);
            return t.isSpawnable() && t.isAlive() ? t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ rodada

    private void round() {
        for (Area a : areas.values()) {
            Region r = AincradCore.get().regions().get(a.region());
            World w = r == null ? null : Bukkit.getWorld(r.world());
            Set<UUID> ids = alive.computeIfAbsent(a.region(), k -> new HashSet<>());
            if (w == null) {
                ids.clear();
                continue;
            }
            List<Player> all = new ArrayList<>();
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR && !p.isDead()) {
                    all.add(p);
                }
            }
            prune(ids, all, a.despawn());

            List<Player> near = new ArrayList<>();
            double reach = (double) a.radius() * a.radius();
            for (Player p : all) {
                Location l = p.getLocation();
                if (SpawnRules.distanceSq(r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(),
                        l.getX(), l.getY(), l.getZ()) <= reach) {
                    near.add(p);
                }
            }
            if (near.isEmpty()) {
                continue;
            }
            int n = SpawnRules.toSpawn(ids.size(), a.max(), a.perRound());
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < n; i++) {
                Location at = place(a, r, w, near, all, rnd);
                if (at == null) {
                    continue;
                }
                // Só as linhas que valem com o clima e o período de agora: é assim que a névoa troca os mobs.
                Entry e = SpawnRules.pick(SpawnRules.fitting(a.mobs(), Conditions.weather(at), Conditions.night(w)), rnd.nextDouble());
                Entity mob = e == null ? null : spawn(e, at, a.region());
                if (mob != null) {
                    mob.getPersistentDataContainer().set(keyRegion, PersistentDataType.STRING, a.region());
                    ids.add(mob.getUniqueId());
                }
            }
        }
    }

    /** Tira da conta os que morreram ou descarregaram, e do mundo os que ficaram longe de todos. */
    private static void prune(Set<UUID> ids, List<Player> players, int despawn) {
        double far = (double) despawn * despawn;
        for (Iterator<UUID> it = ids.iterator(); it.hasNext(); ) {
            Entity e = Bukkit.getEntity(it.next());
            if (e == null || !e.isValid()) {
                it.remove();
                continue;
            }
            boolean someone = false;
            for (Player p : players) {
                if (p.getLocation().distanceSquared(e.getLocation()) <= far) {
                    someone = true;
                    break;
                }
            }
            if (!someone) {
                e.remove();
                it.remove();
            }
        }
    }

    /** Um lugar dentro da região, perto de um jogador mas não em cima de nenhum, com chão e espaço. */
    private static Location place(Area a, Region r, World w, List<Player> near, List<Player> all, ThreadLocalRandom rnd) {
        return placeNear(a.radius(), a.minDistance(), r, w, near, all, rnd);
    }

    /** O mesmo, com o raio e a distância mínima dados (os mobs raros usam). */
    static Location placeNear(int radius, int minDistance, Region r, World w, List<Player> near, List<Player> all, ThreadLocalRandom rnd) {
        double min = (double) minDistance * minDistance;
        for (int t = 0; t < TRIES; t++) {
            Location pl = near.get(rnd.nextInt(near.size())).getLocation();
            int x = clamp(pl.getBlockX() + rnd.nextInt(-radius, radius + 1), r.minX(), r.maxX());
            int z = clamp(pl.getBlockZ() + rnd.nextInt(-radius, radius + 1), r.minZ(), r.maxZ());
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = floor(w, r, x, z, pl.getBlockY());
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            Location at = new Location(w, x + 0.5, y, z + 0.5, rnd.nextFloat() * 360f, 0f);
            boolean close = false;
            for (Player p : all) {
                if (p.getLocation().distanceSquared(at) < min) {
                    close = true;
                    break;
                }
            }
            if (!close) {
                return at;
            }
        }
        return null;
    }

    /** O Y dos pés mais próximo de {@code hint} onde há chão sólido e dois blocos livres. */
    private static int floor(World w, Region r, int x, int z, int hint) {
        int lo = Math.max(r.minY(), w.getMinHeight() + 1);
        int hi = Math.min(r.maxY(), w.getMaxHeight() - 2);
        if (lo > hi) {
            return Integer.MIN_VALUE;
        }
        int start = clamp(hint, lo, hi);
        for (int d = 0; d <= Y_SCAN; d++) {
            if (start - d >= lo && stands(w, x, start - d, z)) {
                return start - d;
            }
            if (d > 0 && start + d <= hi && stands(w, x, start + d, z)) {
                return start + d;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean stands(World w, int x, int y, int z) {
        Block feet = w.getBlockAt(x, y, z);
        Block head = w.getBlockAt(x, y + 1, z);
        return w.getBlockAt(x, y - 1, z).getType().isSolid()
                && feet.isPassable() && !feet.isLiquid()
                && head.isPassable() && !head.isLiquid();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------ criar

    /**
     * Cria o mob de uma linha do spawns.yml (também usado por {@code /aincrad rpg mob}).
     *
     * @param where só para o aviso no console
     */
    public Entity spawn(Entry e, Location at, String where) {
        int level;
        if (e.fixedLevel()) {
            level = MobRewards.pick(e.minLevel(), e.maxLevel(), ThreadLocalRandom.current().nextDouble());
        } else {
            RpgSettings.LevelRange range = mobs.rangeAt(at);
            level = MobRewards.pick(range.min(), range.max(), ThreadLocalRandom.current().nextDouble());
        }
        Entity mob;
        if (e.mob().kind() == Kind.MYTHIC) {
            mob = mythic.spawn(e.mob().id(), at, level);
        } else {
            EntityType type = type(e.mob().id());
            mob = type == null ? null : at.getWorld().spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
        if (mob == null) {
            warn(where, problem(e) != null ? problem(e) : "não foi possível criar " + e.mob().text());
            return null;
        }
        mob.setPersistent(false);
        mob.getPersistentDataContainer().set(com.aincrad.rpg.api.AincradRPGAPI.MOB_ID, PersistentDataType.STRING, e.key());
        if (mob instanceof LivingEntity le) {
            if (le instanceof Mob m) {
                m.setRemoveWhenFarAway(false);
            }
            // O nome que o MythicMobs deu fica; o level entra na frente.
            Component name = e.mob().kind() == Kind.MYTHIC ? le.customName()
                    : e.extra().name() != null ? com.aincrad.core.message.Messages.parse(e.extra().name()) : null;
            dress(le, e, where);
            mobs.setLevel(le, level, name);
        }
        if (e.model() != null && !models.apply(mob, e.model())) {
            warn(where, models.available()
                    ? "o ModelEngine não conhece o modelo '" + e.model() + "'"
                    : "o ModelEngine não está ligado (modelo '" + e.model() + "')");
        }
        return mob;
    }

    /** Faz do mob do Minecraft um mob do andar: adulto, sem queimar no sol, com o equipamento da linha. */
    private void dress(LivingEntity le, Entry e, String where) {
        if (le instanceof org.bukkit.entity.Ageable age) {
            age.setAdult();
        }
        if (le instanceof org.bukkit.entity.Zombie z) {
            z.setShouldBurnInDay(false);
        }
        if (le instanceof org.bukkit.entity.AbstractSkeleton s) {
            s.setShouldBurnInDay(false);
        }
        if (le instanceof org.bukkit.entity.Hoglin h) {
            h.setImmuneToZombification(true);
        }
        if (le instanceof org.bukkit.entity.PiglinAbstract pg) {
            pg.setImmuneToZombification(true);
        }
        if (e.extra().hostile() && le instanceof org.bukkit.entity.Wolf w) {
            w.setAngry(true);
        }
        org.bukkit.inventory.EntityEquipment eq = le.getEquipment();
        if (eq == null || e.mob().kind() == Kind.MYTHIC) {
            return;
        }
        for (Map.Entry<String, String> en : e.extra().equipment().entrySet()) {
            org.bukkit.inventory.ItemStack worn = en.getValue().equalsIgnoreCase("AIR") ? new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR) : itemOf(en.getValue(), 1);
            if (worn == null) {
                warn(where, "equipamento." + en.getKey() + ": '" + en.getValue() + "' não é um item");
                continue;
            }
            org.bukkit.inventory.EquipmentSlot slot = switch (en.getKey()) {
                case "mao" -> org.bukkit.inventory.EquipmentSlot.HAND;
                case "mao_secundaria" -> org.bukkit.inventory.EquipmentSlot.OFF_HAND;
                case "cabeca" -> org.bukkit.inventory.EquipmentSlot.HEAD;
                case "peito" -> org.bukkit.inventory.EquipmentSlot.CHEST;
                case "pernas" -> org.bukkit.inventory.EquipmentSlot.LEGS;
                default -> org.bukkit.inventory.EquipmentSlot.FEET;
            };
            eq.setItem(slot, worn);
            eq.setDropChance(slot, 0f); // o que ele veste não cai; os drops são os da lista
        }
    }

    /** O item de um drop ou do equipamento: MATERIAL, rpg:&lt;id&gt;, life:&lt;ingrediente&gt; ou ia:&lt;namespace:id&gt;. Null se não existe. */
    public org.bukkit.inventory.ItemStack itemOf(String spec, int amount) {
        String lower = spec.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("ia:")) {
            return Packs.itemsAdderItem(spec.substring(3), amount);
        }
        if (lower.startsWith("life:")) {
            return Conditions.lifeIngredient(spec.substring(5), amount);
        }
        if (lower.startsWith("rpg:")) {
            String[] parts = spec.substring(4).split(":", 2);
            return items.create(parts[0], parts.length > 1 ? com.aincrad.rpg.item.Rarity.parse(parts[1]) : null);
        }
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(spec);
        return m == null || !m.isItem() ? null : new org.bukkit.inventory.ItemStack(m, amount);
    }

    /**
     * Os drops da linha do spawns.yml, quando o mob é morto por um jogador. Uma linha com
     * {@code drops} troca os drops normais do Minecraft pelos dela. Roda antes do sorteio de
     * itens do items.yml, que continua valendo.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        Entity mob = event.getEntity();
        String region = mob.getPersistentDataContainer().get(keyRegion, PersistentDataType.STRING);
        String key = mob.getPersistentDataContainer().get(com.aincrad.rpg.api.AincradRPGAPI.MOB_ID, PersistentDataType.STRING);
        Area a = region == null ? null : areas.get(region);
        if (a == null || key == null) {
            return;
        }
        for (Entry e : a.mobs()) {
            if (!e.key().equals(key) || e.extra().drops().isEmpty()) {
                continue;
            }
            event.getDrops().clear();
            if (event.getEntity().getKiller() == null) {
                return;
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (SpawnRules.Drop d : e.extra().drops()) {
                if (rnd.nextDouble() >= d.chance()) {
                    continue;
                }
                org.bukkit.inventory.ItemStack it = itemOf(d.item(), d.min() >= d.max() ? d.min() : rnd.nextInt(d.min(), d.max() + 1));
                if (it == null) {
                    warn(region, "o drop '" + d.item() + "' não existe (o plugin dele está ligado?)");
                } else {
                    event.getDrops().add(it);
                }
            }
            return;
        }
    }

    private void warn(String where, String what) {
        if (warned.add(where + "|" + what)) {
            plugin.getLogger().warning("spawns.yml, " + where + ": " + what);
        }
    }

    // ------------------------------------------------------------------ spawn natural

    /** Nas regiões com {@code spawn_natural: false}, o Minecraft não põe mobs por conta própria. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onNatural(CreatureSpawnEvent event) {
        if (areas.isEmpty()) {
            return;
        }
        switch (event.getSpawnReason()) {
            case NATURAL, REINFORCEMENTS, PATROL, RAID, VILLAGE_INVASION, JOCKEY, MOUNT, TRAP -> {
                for (Region r : AincradCore.get().regions().at(event.getLocation())) {
                    Area a = areas.get(r.name());
                    if (a != null && !a.natural()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            default -> {
            }
        }
    }
}
