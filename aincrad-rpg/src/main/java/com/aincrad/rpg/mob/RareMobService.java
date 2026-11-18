package com.aincrad.rpg.mob;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.rpg.item.ItemService;
import com.aincrad.rpg.model.RareRules;
import com.aincrad.rpg.model.RareRules.Def;
import com.aincrad.rpg.model.SpawnRules;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mobs raros de andar (rare-mobs.yml): aparecem com chance baixa perto de um jogador na região,
 * fogem dele e, se forem pegos, soltam um drop raro. O primeiro é o coelho do ragu.
 */
public final class RareMobService implements Listener {

    private static final double FLEE_RANGE = 10;

    private final Plugin plugin;
    private final Messages msg;
    private final SpawnService spawns;
    private final ItemService items;
    private final NamespacedKey keyRare;
    private final Map<UUID, String> alive = new HashMap<>();
    private final Map<UUID, Long> until = new HashMap<>();
    private Map<String, Def> defs = Map.of();
    private List<String> errors = List.of();
    private BukkitTask round;
    private BukkitTask flee;

    public RareMobService(Plugin plugin, Messages msg, SpawnService spawns, ItemService items) {
        this.plugin = plugin;
        this.msg = msg;
        this.spawns = spawns;
        this.items = items;
        keyRare = new NamespacedKey(plugin, "mob_raro");
    }

    public void reload(ConfigFile file) {
        List<String> errs = new ArrayList<>();
        defs = RareRules.parse(file.yaml().getConfigurationSection("mobs"), errs);
        errors = List.copyOf(errs);
        errs.forEach(e -> plugin.getLogger().warning("rare-mobs.yml: " + e));
        if (round != null) {
            round.cancel();
            flee.cancel();
            round = null;
        }
        if (!defs.isEmpty()) {
            long every = file.integer("intervalo", 30, 5, 3600) * 20L;
            round = Bukkit.getScheduler().runTaskTimer(plugin, this::round, every, every);
            flee = Bukkit.getScheduler().runTaskTimer(plugin, this::flee, 10L, 10L);
        }
    }

    public Map<String, Def> defs() {
        return defs;
    }

    public List<String> errors() {
        return errors;
    }

    public void shutdown() {
        if (round != null) {
            round.cancel();
            flee.cancel();
        }
        for (UUID id : alive.keySet()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        alive.clear();
        until.clear();
    }

    private boolean aliveOf(String def) {
        return alive.containsValue(def);
    }

    private void round() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Def d : defs.values()) {
            Region r = AincradCore.get().regions().get(d.region());
            World w = r == null ? null : Bukkit.getWorld(r.world());
            if (w == null || aliveOf(d.id())) {
                continue;
            }
            List<Player> all = new ArrayList<>();
            List<Player> inside = new ArrayList<>();
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                    continue;
                }
                all.add(p);
                if (r.contains(p.getLocation())) {
                    inside.add(p);
                }
            }
            for (Player p : inside) {
                if (!RareRules.matches(d.weather(), d.night(), Conditions.weather(p.getLocation()), Conditions.night(w))
                        || !RareRules.appears(d.chance(), rnd.nextDouble())) {
                    continue;
                }
                Location at = SpawnService.placeNear(28, 14, r, w, List.of(p), all, rnd);
                if (at != null && spawn(d, at)) {
                    break;
                }
            }
        }
    }

    /** Cria o mob raro no lugar (também usado por {@code /aincrad rpg raro}). */
    public boolean spawn(Def d, Location at) {
        Entity e = spawns.spawn(new SpawnRules.Entry(d.mob(), 1, d.minLevel(), d.maxLevel(), null, null, null), at, "rare-mobs.yml: " + d.id());
        if (e == null) {
            return false;
        }
        e.getPersistentDataContainer().set(keyRare, PersistentDataType.STRING, d.id());
        if (!d.name().isBlank()) {
            e.customName(Messages.parse(d.name()));
            e.setCustomNameVisible(true);
        }
        e.setGlowing(true);
        alive.put(e.getUniqueId(), d.id());
        until.put(e.getUniqueId(), System.currentTimeMillis() + d.lifeMs());
        return true;
    }

    /** Duas vezes por segundo: o mob raro corre para longe do jogador mais próximo; passado o tempo, some. */
    private void flee() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, String>> it = alive.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, String> en = it.next();
            Entity e = Bukkit.getEntity(en.getKey());
            if (e == null || !e.isValid()) {
                until.remove(en.getKey());
                it.remove();
                continue;
            }
            if (until.getOrDefault(en.getKey(), 0L) <= now) {
                e.remove();
                until.remove(en.getKey());
                it.remove();
                continue;
            }
            Def d = defs.get(en.getValue());
            if (d == null || !d.flees() || !(e instanceof Mob mob)) {
                continue;
            }
            Player near = null;
            double best = FLEE_RANGE * FLEE_RANGE;
            for (Player p : e.getWorld().getPlayers()) {
                double dist = p.getLocation().distanceSquared(e.getLocation());
                if (dist < best && p.getGameMode() != GameMode.SPECTATOR) {
                    best = dist;
                    near = p;
                }
            }
            if (near != null) {
                Vector away = e.getLocation().toVector().subtract(near.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 1e-6) {
                    away = new Vector(1, 0, 0);
                }
                Location to = e.getLocation().add(away.normalize().multiply(8));
                to.setY(e.getWorld().getHighestBlockYAt(to) + 1);
                mob.setTarget(null);
                mob.getPathfinder().moveTo(to, 1.6);
            }
        }
    }

    private ItemStack drop(Def d) {
        String spec = d.drop();
        String lower = spec.toLowerCase(Locale.ROOT);
        ItemStack it;
        if (lower.startsWith("life:")) {
            it = Conditions.lifeIngredient(spec.substring(5), d.dropAmount());
        } else if (lower.startsWith("rpg:")) {
            it = items.create(spec.substring(4), null);
        } else {
            Material m = Material.matchMaterial(spec);
            it = m == null || !m.isItem() ? null : new ItemStack(m, d.dropAmount());
        }
        if (it == null) {
            plugin.getLogger().warning("rare-mobs.yml: " + d.id() + ": o drop '" + spec + "' não existe (o plugin dele está ligado?).");
        }
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        String id = e.getPersistentDataContainer().get(keyRare, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        alive.remove(e.getUniqueId());
        until.remove(e.getUniqueId());
        Def d = defs.get(id);
        Player killer = e.getKiller();
        if (d == null || killer == null) {
            return;
        }
        if (d.drop() != null) {
            ItemStack it = drop(d);
            if (it != null) {
                event.getDrops().add(it);
            }
        }
        msg.send(killer, "raro.pegou", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("mob",
                d.name().isBlank() ? net.kyori.adventure.text.Component.text(d.id()) : Messages.parse(d.name())));
    }
}
