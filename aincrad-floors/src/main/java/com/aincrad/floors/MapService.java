package com.aincrad.floors;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.floors.model.Floor;
import com.aincrad.floors.model.MapRules;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Zonas fora do mapa: o mapa de cada jogador começa em branco e é revelado a pé, setor por setor.
 * {@code /mapa} mostra o que ele já viu do andar; os dados podem ser copiados num item ("dados de
 * mapa") e passados a outro jogador.
 */
public final class MapService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS floor_sectors (uuid VARCHAR(36) NOT NULL, floor INT NOT NULL, sx INT NOT NULL, sz INT NOT NULL, "
                    + "PRIMARY KEY (uuid, floor, sx, sz))");

    private static final int ITEM_MAX = 1500;

    private final Plugin plugin;
    private final Messages msg;
    private final FloorService floors;
    private final ConfigFile config;
    private final Database db;
    private final NamespacedKey keyFloor;
    private final NamespacedKey keyData;
    /** jogador → andar → setores vistos. */
    private final Map<UUID, Map<Integer, Set<Long>>> seen = new ConcurrentHashMap<>();

    public MapService(Plugin plugin, Messages msg, FloorService floors, ConfigFile config) {
        this.plugin = plugin;
        this.msg = msg;
        this.floors = floors;
        this.config = config;
        this.db = AincradCore.get().database();
        keyFloor = new NamespacedKey(plugin, "mapa_andar");
        keyData = new NamespacedKey(plugin, "mapa_setores");
    }

    private boolean enabled() {
        return config.bool("mapa.ativo", true);
    }

    private int size() {
        return config.integer("mapa.setor", 64, 8, 512);
    }

    // ------------------------------------------------------------------ dados

    public void load(UUID id) {
        db.query("SELECT floor, sx, sz FROM floor_sectors WHERE uuid = ?", rs -> new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3)}, id.toString())
                .thenAccept(rows -> {
                    Map<Integer, Set<Long>> m = seen.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
                    for (int[] r : rows) {
                        m.computeIfAbsent(r[0], k -> ConcurrentHashMap.newKeySet()).add(MapRules.key(r[1], r[2]));
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível ler o mapa de " + id, ex);
                    return null;
                });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        seen.remove(event.getPlayer().getUniqueId());
    }

    private Set<Long> of(UUID id, int floor) {
        return seen.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).computeIfAbsent(floor, k -> ConcurrentHashMap.newKeySet());
    }

    /** @return true se o setor era novo para o jogador */
    private boolean reveal(UUID id, int floor, long key) {
        if (!of(id, floor).add(key)) {
            return false;
        }
        db.async(c -> {
            if (Database.query(c, "SELECT sx FROM floor_sectors WHERE uuid = ? AND floor = ? AND sx = ? AND sz = ?", rs -> rs.getInt(1),
                    id.toString(), floor, MapRules.sx(key), MapRules.sz(key)).isEmpty()) {
                Database.update(c, "INSERT INTO floor_sectors (uuid, floor, sx, sz) VALUES (?, ?, ?, ?)", id.toString(), floor, MapRules.sx(key), MapRules.sz(key));
            }
            return null;
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar um setor do mapa de " + id, ex);
            return null;
        });
        return true;
    }

    // ------------------------------------------------------------------ explorar a pé

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        int size = size();
        if (MapRules.sector(from.getBlockX(), size) == MapRules.sector(to.getBlockX(), size)
                && MapRules.sector(from.getBlockZ(), size) == MapRules.sector(to.getBlockZ(), size)) {
            return;
        }
        Player p = event.getPlayer();
        Floor f = floors.floorAt(to);
        if (f != null && reveal(p.getUniqueId(), f.number(), MapRules.key(MapRules.sector(to.getBlockX(), size), MapRules.sector(to.getBlockZ(), size)))
                && config.bool("mapa.avisar_setor_novo", true)) {
            msg.actionBar(p, "mapa.setor_novo");
        }
    }

    // ------------------------------------------------------------------ /mapa

    private long total(Floor f) {
        Region r = f.region() == null || f.region().isBlank() ? null : AincradCore.get().regions().get(f.region());
        return r == null ? 0 : MapRules.total(r.minX(), r.maxX(), r.minZ(), r.maxZ(), size());
    }

    /** {@code /mapa}: o que o jogador já viu do andar onde está. */
    public void show(Player p) {
        Floor f = floors.floorAt(p.getLocation());
        if (!enabled() || f == null) {
            msg.send(p, "mapa.fora");
            return;
        }
        int size = size();
        int sx = MapRules.sector(p.getLocation().getBlockX(), size);
        int sz = MapRules.sector(p.getLocation().getBlockZ(), size);
        Set<Long> mine = of(p.getUniqueId(), f.number());
        mine.add(MapRules.key(sx, sz));
        msg.send(p, "mapa.titulo", Messages.vars("andar", f.name(), "pct", MapRules.percent(mine.size(), total(f)), "setores", mine.size()));
        for (String line : MapRules.grid(mine, sx, sz, config.integer("mapa.raio", 6, 2, 12),
                msg.raw("mapa.voce"), msg.raw("mapa.visto"), msg.raw("mapa.desconhecido"))) {
            p.sendMessage(Messages.parse(line));
        }
        msg.sendRaw(p, "mapa.legenda", Messages.vars("blocos", size));
    }

    /** {@code /mapa dados}: copia o mapa do andar num item, para vender ou dar. Gasta 1 papel. */
    public void write(Player p) {
        Floor f = floors.floorAt(p.getLocation());
        if (!enabled() || f == null) {
            msg.send(p, "mapa.fora");
            return;
        }
        Set<Long> mine = of(p.getUniqueId(), f.number());
        if (mine.isEmpty() || !p.getInventory().containsAtLeast(new ItemStack(Material.PAPER), 1)) {
            msg.send(p, "mapa.sem_papel");
            return;
        }
        p.getInventory().removeItem(new ItemStack(Material.PAPER, 1));
        ItemStack it = new ItemStack(Material.MAP);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("mapa.item_nome", Messages.vars("andar", f.name())));
        meta.lore(List.of(msg.get("mapa.item_lore", Messages.vars("jogador", p.getName(), "setores", Math.min(mine.size(), ITEM_MAX))),
                msg.get("mapa.item_usar")));
        meta.getPersistentDataContainer().set(keyFloor, PersistentDataType.INTEGER, f.number());
        meta.getPersistentDataContainer().set(keyData, PersistentDataType.STRING, MapRules.encode(mine, ITEM_MAX));
        it.setItemMeta(meta);
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        msg.send(p, "mapa.escreveu", Messages.vars("andar", f.name(), "setores", Math.min(mine.size(), ITEM_MAX)));
    }

    /** Botão direito com os dados de mapa na mão: os setores entram no mapa do jogador e o item é gasto. */
    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        ItemStack it = event.getItem();
        if (it == null || !it.hasItemMeta() || event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        var pdc = it.getItemMeta().getPersistentDataContainer();
        Integer floor = pdc.get(keyFloor, PersistentDataType.INTEGER);
        String data = pdc.get(keyData, PersistentDataType.STRING);
        if (floor == null || data == null) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        int added = 0;
        for (long key : MapRules.decode(data)) {
            if (reveal(p.getUniqueId(), floor, key)) {
                added++;
            }
        }
        it.setAmount(it.getAmount() - 1);
        msg.send(p, added > 0 ? "mapa.aprendeu" : "mapa.nada_novo", Messages.vars("setores", added, "andar", floors.name(floor)));
    }

    // ------------------------------------------------------------------ menu do AincradUI

    /** Faz a entrada "mapa" do menu do AincradUI abrir o {@code /mapa}. Sem o AincradUI, nada acontece. */
    public void hookUi() {
        try {
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (!c.getName().equals("com.aincrad.ui.api.AincradUIAPI")) {
                    continue;
                }
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                if (r == null) {
                    return;
                }
                Class<?> action = Class.forName("com.aincrad.ui.api.MenuAction", false, c.getClassLoader());
                Object proxy = Proxy.newProxyInstance(c.getClassLoader(), new Class<?>[] {action}, (self, method, args) -> {
                    if (method.getName().equals("run") && args != null && args.length == 1 && args[0] instanceof Player p) {
                        p.closeInventory();
                        show(p);
                    }
                    return null;
                });
                c.getMethod("registerMenuEntry", String.class, String.class, action).invoke(r.getProvider(), "mapa", "under_icons/map", proxy);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("AincradUI incompatível (entrada de mapa no menu): " + e);
        }
    }

    /** Para os outros plugins: o jogador já viu o lugar? */
    public boolean explored(Player p, Location l) {
        Floor f = floors.floorAt(l);
        Map<Integer, Set<Long>> m = seen.getOrDefault(p.getUniqueId(), new HashMap<>());
        int size = size();
        return f != null && m.getOrDefault(f.number(), new HashSet<>())
                .contains(MapRules.key(MapRules.sector(l.getBlockX(), size), MapRules.sector(l.getBlockZ(), size)));
    }
}
