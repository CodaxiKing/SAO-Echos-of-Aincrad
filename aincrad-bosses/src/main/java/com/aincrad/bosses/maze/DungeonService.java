package com.aincrad.bosses.maze;

import com.aincrad.bosses.data.RoomStore;
import com.aincrad.bosses.hook.Hooks;
import com.aincrad.bosses.model.DungeonRules;
import com.aincrad.bosses.model.DungeonRules.Def;
import com.aincrad.bosses.model.DungeonRules.End;
import com.aincrad.core.AincradCore;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Dungeons escondidas (dungeons.yml): uma sala fora do caminho com um baú-armadilha. Abrir o baú
 * fecha a saída, bloqueia os cristais e dispara o alarme, que chama levas de mobs até o grupo
 * vencer, o tempo acabar ou todos caírem. Uma dungeon trancada só abre com a chave; o mapa mostra
 * onde ela fica.
 */
public final class DungeonService implements Listener {

    /** Uma armadilha disparada. */
    private static final class Trap {
        final long startedAt = System.currentTimeMillis();
        int spawned;
        final Set<UUID> mobs = new HashSet<>();
    }

    private final Plugin plugin;
    private final Messages msg;
    private final Hooks hooks;
    private final File file;
    private final NamespacedKey keyKey;
    private final NamespacedKey keyMap;
    private final NamespacedKey keyMob;
    private Map<String, Def> defs = Map.of();
    private List<String> errors = List.of();
    private final Map<String, Trap> traps = new HashMap<>();
    private final Map<String, Long> lastTrap = new HashMap<>();
    /** dungeon → jogador → até quando a chave vale. */
    private final Map<String, Map<UUID, Long>> access = new HashMap<>();

    public DungeonService(Plugin plugin, Messages msg, Hooks hooks) {
        this.plugin = plugin;
        this.msg = msg;
        this.hooks = hooks;
        file = new File(plugin.getDataFolder(), "dungeons.yml");
        keyKey = new NamespacedKey(plugin, "chave_masmorra");
        keyMap = new NamespacedKey(plugin, "mapa_masmorra");
        keyMob = new NamespacedKey(plugin, "mob_alarme");
    }

    /** "mundo,x,y,z" de um bloco: como o baú fica gravado no arquivo. */
    private static String blockKey(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("dungeons.yml", false);
        }
        List<String> errs = new ArrayList<>();
        defs = DungeonRules.parse(YamlConfiguration.loadConfiguration(file).getConfigurationSection("masmorras"), errs);
        errors = List.copyOf(errs);
        errs.forEach(e -> plugin.getLogger().warning("dungeons.yml: " + e));
    }

    public Map<String, Def> defs() {
        return defs;
    }

    public List<String> errors() {
        return errors;
    }

    // ------------------------------------------------------------------ arquivo (comandos de admin)

    private void set(String id, String path, Object value) throws IOException {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        y.set("masmorras." + id + (path == null ? "" : "." + path), value);
        y.save(file);
        load();
    }

    public void create(String id, String region) throws IOException {
        set(id, "regiao", region);
    }

    public void setChest(String id, Block chest) throws IOException {
        set(id, "bau", blockKey(chest.getLocation()));
    }

    public void setLocked(String id, boolean locked) throws IOException {
        set(id, "trancada", locked);
    }

    public void delete(String id) throws IOException {
        stop(id, false);
        set(id, null, null);
    }

    // ------------------------------------------------------------------ onde fica

    private Region region(Def d) {
        return AincradCore.get().regions().get(d.region());
    }

    private Def at(Location l) {
        for (Def d : defs.values()) {
            Region r = region(d);
            if (r != null && r.contains(l)) {
                return d;
            }
        }
        return null;
    }

    private List<Player> inside(Def d) {
        List<Player> out = new ArrayList<>();
        Region r = region(d);
        World w = r == null ? null : Bukkit.getWorld(r.world());
        if (w != null) {
            for (Player p : w.getPlayers()) {
                if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && r.contains(p.getLocation())) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Com a armadilha disparada numa dungeon anti-cristal, nenhum cristal funciona lá dentro. */
    public boolean crystalBlocked(Location l) {
        Def d = at(l);
        return d != null && d.antiCrystal() && traps.containsKey(d.id());
    }

    // ------------------------------------------------------------------ o baú-armadilha

    @EventHandler(priority = EventPriority.LOW)
    public void onChest(PlayerInteractEvent event) {
        Block b = event.getClickedBlock();
        if (b == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String here = blockKey(b.getLocation());
        for (Def d : defs.values()) {
            if (!here.equals(d.chest())) {
                continue;
            }
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (traps.containsKey(d.id())) {
                return;
            }
            if (!DungeonRules.armed(false, lastTrap.getOrDefault(d.id(), 0L), now, d.cooldownMs())) {
                msg.send(event.getPlayer(), "masmorra.bau_vazio");
                return;
            }
            traps.put(d.id(), new Trap());
            lastTrap.put(d.id(), now);
            for (Player p : inside(d)) {
                msg.title(p, "masmorra.armadilha", java.time.Duration.ofSeconds(3));
                msg.send(p, d.antiCrystal() ? "masmorra.alarme_sem_cristal" : "masmorra.alarme");
                p.playSound(p.getLocation(), "minecraft:block.bell.use", 1f, 0.6f);
            }
            return;
        }
    }

    private void wave(Def d, Trap t) {
        Location chest = RoomStore.location(d.chest());
        Region r = region(d);
        if (chest == null || r == null) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < d.perWave(); i++) {
            EntityType type;
            try {
                type = EntityType.valueOf(d.mobs().get(rnd.nextInt(d.mobs().size())));
            } catch (IllegalArgumentException e) {
                type = EntityType.ZOMBIE;
            }
            if (!type.isSpawnable() || !type.isAlive()) {
                type = EntityType.ZOMBIE;
            }
            // Em volta do baú, sempre dentro da sala.
            double x = Math.max(r.minX() + 0.5, Math.min(r.maxX() + 0.5, chest.getX() + 0.5 + rnd.nextDouble(-4, 4)));
            double z = Math.max(r.minZ() + 0.5, Math.min(r.maxZ() + 0.5, chest.getZ() + 0.5 + rnd.nextDouble(-4, 4)));
            Entity e = chest.getWorld().spawnEntity(new Location(chest.getWorld(), x, chest.getY() + 0.1, z), type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            e.setPersistent(false);
            e.getPersistentDataContainer().set(keyMob, PersistentDataType.STRING, d.id());
            if (e instanceof LivingEntity le) {
                hooks.setMobLevel(le, d.level());
                if (le instanceof Mob m) {
                    m.setRemoveWhenFarAway(false);
                }
            }
            t.mobs.add(e.getUniqueId());
        }
        t.spawned++;
        for (Player p : inside(d)) {
            msg.actionBar(p, "masmorra.leva", Messages.vars("n", t.spawned, "total", d.waves()));
        }
    }

    private void stop(String id, boolean reward) {
        Trap t = traps.remove(id);
        Def d = defs.get(id);
        if (t == null) {
            return;
        }
        for (UUID mob : t.mobs) {
            Entity e = Bukkit.getEntity(mob);
            if (e != null) {
                e.remove();
            }
        }
        if (!reward || d == null) {
            return;
        }
        Location chest = RoomStore.location(d.chest());
        for (String spec : d.loot()) {
            String[] p = spec.trim().split("\\s+");
            int amount = 1;
            try {
                amount = p.length > 1 ? Math.max(1, Integer.parseInt(p[1])) : 1;
            } catch (NumberFormatException ignored) {
                // Sem quantidade legível: um.
            }
            ItemStack it = item(p[0], amount);
            if (it != null && chest != null) {
                chest.getWorld().dropItemNaturally(chest.clone().add(0.5, 1.2, 0.5), it);
            } else if (it == null) {
                plugin.getLogger().warning("dungeons.yml: " + id + ": recompensa '" + spec + "' não existe.");
            }
        }
    }

    /** A cada segundo: solta as levas na hora e confere se a armadilha acabou. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (String id : new ArrayList<>(traps.keySet())) {
            Trap t = traps.get(id);
            Def d = defs.get(id);
            if (d == null) {
                stop(id, false);
                continue;
            }
            while (t.spawned < DungeonRules.wavesDue(t.startedAt, now, d.waveMs(), d.waves())) {
                wave(d, t);
            }
            t.mobs.removeIf(m -> {
                Entity e = Bukkit.getEntity(m);
                return e == null || !e.isValid();
            });
            List<Player> in = inside(d);
            End end = DungeonRules.ended(t.spawned, d.waves(), t.mobs.size(), in.size(), t.startedAt, now, d.limitMs());
            if (end != null) {
                stop(id, end == End.VENCERAM);
                for (Player p : in) {
                    msg.send(p, "masmorra.fim." + end.name().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
    }

    public void shutdown() {
        new ArrayList<>(traps.keySet()).forEach(id -> stop(id, false));
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(keyMob, PersistentDataType.STRING)) {
            // Os mobs do alarme não dão drop: a recompensa é o baú.
            event.getDrops().clear();
        }
    }

    // ------------------------------------------------------------------ porta fechada e sala trancada

    private boolean mayEnter(Player p, Def d) {
        if (p.hasPermission("aincrad.admin")) {
            return true;
        }
        Map<UUID, Long> m = access.get(d.id());
        return DungeonRules.mayEnter(d.locked(), m == null ? 0 : m.getOrDefault(p.getUniqueId(), 0L), System.currentTimeMillis());
    }

    /** @return true se o movimento deve ser barrado */
    private boolean barred(Player p, Location from, Location to) {
        Def a = at(from);
        Def b = at(to);
        if (a == b) {
            return false;
        }
        // Sair de uma sala com a armadilha disparada: a porta fechou.
        if (a != null && traps.containsKey(a.id()) && !p.hasPermission("aincrad.admin")) {
            msg.actionBar(p, "masmorra.porta_fechada");
            return true;
        }
        // Entrar numa dungeon trancada sem chave, ou com a armadilha já disparada lá dentro.
        if (b != null && (!mayEnter(p, b) || (traps.containsKey(b.id()) && !p.hasPermission("aincrad.admin")))) {
            msg.actionBar(p, traps.containsKey(b.id()) ? "masmorra.porta_fechada" : "masmorra.trancada");
            return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (!defs.isEmpty() && event.hasChangedBlock() && barred(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!defs.isEmpty() && barred(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ chaves e mapas

    private ItemStack tagged(Material m, NamespacedKey key, Def d, String namePath, String lorePath) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get(namePath, Messages.vars("masmorra", d.name())));
        meta.lore(List.of(msg.get(lorePath)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, d.id());
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack key(Def d) {
        return tagged(Material.TRIPWIRE_HOOK, keyKey, d, "masmorra.chave_nome", "masmorra.chave_lore");
    }

    public ItemStack map(Def d) {
        return tagged(Material.PAPER, keyMap, d, "masmorra.mapa_nome", "masmorra.mapa_lore");
    }

    /** {@code chave:<id>}, {@code mapa:<id>} ou o que o resto do plugin entende (material, rpg:, cristal:). */
    public ItemStack item(String spec, int amount) {
        String lower = spec.toLowerCase(java.util.Locale.ROOT);
        Def d = lower.startsWith("chave:") ? defs.get(lower.substring(6)) : lower.startsWith("mapa:") ? defs.get(lower.substring(5)) : null;
        if (d != null) {
            ItemStack it = lower.startsWith("chave:") ? key(d) : map(d);
            it.setAmount(Math.min(amount, it.getMaxStackSize()));
            return it;
        }
        return lower.startsWith("chave:") || lower.startsWith("mapa:") ? null : hooks.item(spec, amount);
    }

    private Collection<UUID> party(UUID player) {
        try {
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (c.getName().equals("com.aincrad.social.api.AincradSocialAPI")) {
                    RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                    Object list = r == null ? null : c.getMethod("getParty", UUID.class).invoke(r.getProvider(), player);
                    if (list instanceof Collection<?> col && !col.isEmpty()) {
                        Set<UUID> out = new HashSet<>();
                        col.forEach(o -> {
                            if (o instanceof UUID u) {
                                out.add(u);
                            }
                        });
                        out.add(player);
                        return out;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "AincradSocial incompatível (party da chave)", e);
        }
        return List.of(player);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        ItemStack it = event.getItem();
        if (it == null || !it.hasItemMeta() || event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        var pdc = it.getItemMeta().getPersistentDataContainer();
        String keyOf = pdc.get(keyKey, PersistentDataType.STRING);
        String mapOf = pdc.get(keyMap, PersistentDataType.STRING);
        if (keyOf == null && mapOf == null) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        Def d = defs.get(keyOf != null ? keyOf : mapOf);
        Region r = d == null ? null : region(d);
        if (r == null) {
            msg.send(p, "masmorra.item_invalido");
            return;
        }
        if (keyOf != null) {
            // A chave abre a dungeon para a party de quem a usa, por um tempo, e é gasta.
            long until = System.currentTimeMillis() + d.accessMs();
            Map<UUID, Long> m = access.computeIfAbsent(d.id(), k -> new HashMap<>());
            int n = 0;
            for (UUID id : party(p.getUniqueId())) {
                m.put(id, until);
                Player member = Bukkit.getPlayer(id);
                if (member != null) {
                    msg.send(member, "masmorra.chave_usada", Messages.vars("masmorra", d.name(), "jogador", p.getName(), "minutos", d.accessMs() / 60_000));
                    n++;
                }
            }
            it.setAmount(it.getAmount() - 1);
            plugin.getLogger().info(p.getName() + " usou a chave de " + d.id() + " para " + n + " jogador(es).");
            return;
        }
        // O mapa mostra onde fica a entrada (a bússola aponta para lá) e some.
        World w = Bukkit.getWorld(r.world());
        if (w != null) {
            Location center = new Location(w, (r.minX() + r.maxX()) / 2.0, r.minY(), (r.minZ() + r.maxZ()) / 2.0);
            if (w.equals(p.getWorld())) {
                p.setCompassTarget(center);
            }
            msg.send(p, "masmorra.mapa_usado", Messages.vars("masmorra", d.name(), "mundo", r.world(), "x", center.getBlockX(),
                    "y", center.getBlockY(), "z", center.getBlockZ()));
            it.setAmount(it.getAmount() - 1);
        } else {
            msg.send(p, "masmorra.mapa_outro_servidor", Messages.vars("masmorra", d.name()));
        }
    }
}
