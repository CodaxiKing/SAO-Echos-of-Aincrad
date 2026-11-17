package com.aincrad.core.region;

import com.aincrad.core.bus.MessageBus;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Regiões nomeadas salvas no banco (compartilhadas pela rede), seleção com a varinha e os
 * eventos {@link RegionEnterEvent} / {@link RegionLeaveEvent} ao andar, teleportar, entrar e sair.
 */
public final class RegionManager implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS aincrad_regions (name VARCHAR(64) NOT NULL PRIMARY KEY, world VARCHAR(64) NOT NULL, "
                    + "min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL, "
                    + "max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL, owner VARCHAR(64) NOT NULL)");

    /** Canal do MessageBus: outro servidor mudou as regiões. */
    public static final String BUS_CHANNEL = "core:regions";

    /** Uma seleção da varinha: dois cantos (podem faltar). */
    public static final class Selection {
        private Location a;
        private Location b;

        public Location a() {
            return a;
        }

        public Location b() {
            return b;
        }

        public boolean complete() {
            return a != null && b != null;
        }
    }

    private final Plugin core;
    private final Database db;
    private final MessageBus bus;
    private final Messages msg;
    private final RegionIndex index = new RegionIndex();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Set<String>> inside = new HashMap<>();
    private final NamespacedKey wandKey;
    private Material wandMaterial = Material.BLAZE_ROD;

    public RegionManager(Plugin core, Database db, MessageBus bus, Messages msg) {
        this.core = core;
        this.db = db;
        this.bus = bus;
        this.msg = msg;
        this.wandKey = new NamespacedKey(core, "varinha");
    }

    public void setWandMaterial(Material m) {
        this.wandMaterial = m == null || !m.isItem() ? Material.BLAZE_ROD : m;
    }

    // ------------------------------------------------------------------ dados

    /** Lê todas as regiões do banco (nesta thread: ao ligar). */
    public void loadSync() throws SQLException {
        List<Region> list = db.sync(c -> Database.query(c,
                "SELECT name, world, min_x, min_y, min_z, max_x, max_y, max_z, owner FROM aincrad_regions",
                rs -> new Region(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                        rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getString(9))));
        index.clear();
        list.forEach(index::put);
    }

    /** Recarrega do banco fora da thread principal (aviso do MessageBus). */
    public void reloadAsync() {
        db.async(c -> Database.query(c,
                        "SELECT name, world, min_x, min_y, min_z, max_x, max_y, max_z, owner FROM aincrad_regions",
                        rs -> new Region(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                                rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getString(9))))
                .thenAccept(list -> Bukkit.getScheduler().runTask(core, () -> {
                    index.clear();
                    list.forEach(index::put);
                }))
                .exceptionally(ex -> {
                    core.getLogger().log(Level.WARNING, "Falha ao recarregar regiões", ex);
                    return null;
                });
    }

    public Region get(String name) {
        return index.get(name);
    }

    public Collection<Region> all() {
        return index.all();
    }

    /** Regiões que contêm o ponto, a menor primeiro. */
    public List<Region> at(Location l) {
        return l.getWorld() == null ? List.of() : index.at(l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }

    public boolean isIn(Player p, String region) {
        Set<String> s = inside.get(p.getUniqueId());
        return s != null && s.contains(region);
    }

    /** Cria ou substitui (outros plugins: salas de chefe, cidades...). Grava e avisa a rede. */
    public CompletableFuture<Void> save(Region r) {
        index.put(r);
        String sql = db.upsert("aincrad_regions", List.of("name"),
                List.of("world", "min_x", "min_y", "min_z", "max_x", "max_y", "max_z", "owner"));
        return db.async(c -> {
            Database.update(c, sql, r.name(), r.world(), r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(), r.owner());
            return null;
        }).thenRun(this::announce);
    }

    public CompletableFuture<Boolean> remove(String name) {
        Region r = index.remove(name);
        if (r == null) {
            return CompletableFuture.completedFuture(false);
        }
        return db.update("DELETE FROM aincrad_regions WHERE name = ?", name)
                .thenApply(n -> {
                    announce();
                    return true;
                });
    }

    private void announce() {
        Bukkit.getScheduler().runTask(core, () -> bus.publish(BUS_CHANNEL, "changed", r -> { }));
    }

    // ------------------------------------------------------------------ varinha

    public ItemStack wand() {
        ItemStack it = new ItemStack(wandMaterial);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("varinha.nome"));
        meta.lore(msg.list("varinha.lore"));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isWand(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public Selection selection(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), k -> new Selection());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || !isWand(event.getItem()) || !event.getPlayer().hasPermission("aincrad.admin")) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        Location l = event.getClickedBlock().getLocation();
        Selection s = selection(p);
        int n;
        if (a == Action.LEFT_CLICK_BLOCK) {
            s.a = l;
            n = 1;
        } else {
            s.b = l;
            n = 2;
        }
        msg.send(p, "varinha.canto", Messages.vars("n", n, "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(),
                "mundo", l.getWorld().getName()));
        if (s.complete() && s.a.getWorld().equals(s.b.getWorld())) {
            long blocks = Region.of("x", "w", s.a.getBlockX(), s.a.getBlockY(), s.a.getBlockZ(),
                    s.b.getBlockX(), s.b.getBlockY(), s.b.getBlockZ(), "").volume();
            msg.send(p, "varinha.tamanho", Messages.vars("blocos", blocks));
        }
    }

    // ------------------------------------------------------------------ entrar e sair

    /** Compara as regiões de agora com as anteriores e dispara os eventos. */
    void update(Player p, Location to) {
        Set<String> now = new HashSet<>();
        List<Region> here = to == null ? List.of() : at(to);
        for (Region r : here) {
            now.add(r.name());
        }
        Set<String> before = inside.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (before.equals(now)) {
            return;
        }
        for (String name : new ArrayList<>(before)) {
            if (!now.contains(name)) {
                before.remove(name);
                Region r = index.get(name);
                Bukkit.getPluginManager().callEvent(new RegionLeaveEvent(p,
                        r != null ? r : new Region(name, "", 0, 0, 0, 0, 0, 0, "")));
            }
        }
        for (Region r : here) {
            if (before.add(r.name())) {
                Bukkit.getPluginManager().callEvent(new RegionEnterEvent(p, r));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            update(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        update(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        update(event.getPlayer(), null);
        inside.remove(event.getPlayer().getUniqueId());
        selections.remove(event.getPlayer().getUniqueId());
    }
}
