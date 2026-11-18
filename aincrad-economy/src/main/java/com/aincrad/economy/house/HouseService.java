package com.aincrad.economy.house;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.core.region.RegionManager;
import com.aincrad.economy.EcoMath;
import com.aincrad.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Cake;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.block.data.type.Switch;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Casas à venda nos andares. Cada casa é uma região do core; o dono e os convidados constroem,
 * abrem portas e baús lá dentro, os outros não. Casa sem dono: só admin mexe. Dados no banco (a
 * rede inteira vê a mesma coisa; mudanças avisam pelo MessageBus).
 */
public final class HouseService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS eco_houses (name VARCHAR(64) NOT NULL PRIMARY KEY, region VARCHAR(64) NOT NULL, "
                    + "price BIGINT NOT NULL, floor INT NOT NULL, owner VARCHAR(36), owner_name VARCHAR(16), "
                    + "members TEXT, spawn VARCHAR(128), bought BIGINT NOT NULL)");
    public static final String BUS_CHANNEL = "economy:houses";
    public static final String PREFIX = "casa_";
    private static final String BYPASS = "aincrad.economy.admin";

    private final Plugin plugin;
    private final EconomyService eco;
    private final Messages msg;
    private final Database db;
    /** Pelo nome da região. */
    private final Map<String, House> byRegion = new ConcurrentHashMap<>();

    public HouseService(Plugin plugin, EconomyService eco, Database db) {
        this.plugin = plugin;
        this.eco = eco;
        this.msg = eco.messages();
        this.db = db;
    }

    private static House row(java.sql.ResultSet rs) throws SQLException {
        String o = rs.getString(5);
        UUID owner = o == null || o.isBlank() ? null : UUID.fromString(o);
        return new House(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getInt(4), owner, rs.getString(6), rs.getString(7), rs.getString(8));
    }

    private static final String SELECT = "SELECT name, region, price, floor, owner, owner_name, members, spawn FROM eco_houses";

    public void loadBlocking() throws SQLException {
        List<House> all = db.sync(c -> Database.query(c, SELECT, HouseService::row));
        byRegion.clear();
        all.forEach(h -> byRegion.put(h.region(), h));
    }

    public void reloadAsync() {
        db.query(SELECT, HouseService::row).thenAccept(all -> AincradCore.get().runSync(() -> {
            byRegion.clear();
            all.forEach(h -> byRegion.put(h.region(), h));
        }));
    }

    public Collection<House> all() {
        return byRegion.values();
    }

    public House byName(String name) {
        return byRegion.get(PREFIX + name.toLowerCase(Locale.ROOT));
    }

    /** A casa onde está o lugar (a menor, se houver casas dentro de casas). */
    public House at(Location l) {
        House best = null;
        long vol = Long.MAX_VALUE;
        for (Region r : AincradCore.get().regions().at(l)) {
            House h = byRegion.get(r.name());
            if (h != null && r.volume() < vol) {
                best = h;
                vol = r.volume();
            }
        }
        return best;
    }

    public List<House> ownedBy(UUID id) {
        List<House> out = new ArrayList<>();
        for (House h : byRegion.values()) {
            if (id.equals(h.owner())) {
                out.add(h);
            }
        }
        return out;
    }

    private void save(House h) {
        db.update(db.upsert("eco_houses", List.of("name"), List.of("region", "price", "floor", "owner", "owner_name", "members", "spawn", "bought")),
                h.name(), h.region(), h.price(), h.floor(), h.owner() == null ? null : h.owner().toString(), h.ownerName(),
                h.membersText(), h.spawn(), System.currentTimeMillis())
                .thenRun(() -> AincradCore.get().publish(BUS_CHANNEL, h.name()));
    }

    public static String spawnText(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
    }

    public static Location spawnLocation(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(",");
        World w = p.length == 6 ? Bukkit.getWorld(p[0]) : null;
        if (w == null) {
            return null;
        }
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ admin

    /** Cria a casa na seleção da varinha do core. */
    public void create(Player admin, String name, long price, int floor) {
        String n = name.toLowerCase(Locale.ROOT);
        String region = PREFIX + n;
        if (!Region.validName(region)) {
            msg.send(admin, "casa.nome_invalido");
            return;
        }
        RegionManager rm = AincradCore.get().regions();
        RegionManager.Selection sel = rm.selection(admin);
        if (sel == null || !sel.complete()) {
            msg.send(admin, "casa.sem_selecao");
            return;
        }
        if (!sel.a().getWorld().equals(sel.b().getWorld())) {
            msg.send(admin, "casa.sem_selecao");
            return;
        }
        Location a = sel.a();
        Location b = sel.b();
        rm.save(Region.of(region, a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ(), null));
        House h = new House(n, region, price, floor, null, null, null, spawnText(admin.getLocation()));
        byRegion.put(region, h);
        save(h);
        msg.send(admin, "casa.criada", Messages.vars("casa", n), eco.col(price));
    }

    public boolean delete(String name) {
        House h = byName(name);
        if (h == null) {
            return false;
        }
        byRegion.remove(h.region());
        AincradCore.get().regions().remove(h.region());
        db.update("DELETE FROM eco_houses WHERE name = ?", h.name()).thenRun(() -> AincradCore.get().publish(BUS_CHANNEL, h.name()));
        return true;
    }

    /** Admin: tira o dono (sem devolver Col). */
    public boolean evict(String name) {
        House h = byName(name);
        if (h == null) {
            return false;
        }
        h.owner(null, null);
        save(h);
        return true;
    }

    // ------------------------------------------------------------------ jogador

    public void buy(Player p) {
        House h = at(p.getLocation());
        if (h == null) {
            msg.send(p, "casa.fora");
            return;
        }
        if (!h.forSale()) {
            msg.send(p, "casa.ja_tem_dono", Messages.vars("dono", String.valueOf(h.ownerName())));
            return;
        }
        int max = eco.config().integer("casas.max_por_jogador", 1, 0, 100);
        if (ownedBy(p.getUniqueId()).size() >= max) {
            msg.send(p, "casa.limite", Messages.vars("max", max));
            return;
        }
        if (!eco.withdraw(p.getUniqueId(), h.price(), "casa:compra")) {
            msg.send(p, "casa.sem_saldo", eco.col(h.price()));
            return;
        }
        UUID id = p.getUniqueId();
        String name = p.getName();
        // Só fica com a casa se ninguém comprou antes (outro servidor): UPDATE ... WHERE owner IS NULL.
        db.update("UPDATE eco_houses SET owner = ?, owner_name = ?, members = ?, bought = ? WHERE name = ? AND owner IS NULL",
                id.toString(), name, "", System.currentTimeMillis(), h.name()).whenComplete((n, err) -> AincradCore.get().runSync(() -> {
            Player online = plugin.getServer().getPlayer(id);
            if (err != null || n == null || n == 0) {
                eco.deposit(id, h.price(), "casa:estorno");
                if (online != null) {
                    msg.send(online, "casa.perdeu");
                }
                reloadAsync();
                return;
            }
            h.owner(id, name);
            AincradCore.get().publish(BUS_CHANNEL, h.name());
            if (online != null) {
                msg.send(online, "casa.comprou", Messages.vars("casa", h.name()), eco.col(h.price()));
            }
        }));
    }

    /** Devolve a casa ao mercado com reembolso parcial. */
    public void sell(Player p, House h) {
        double rate = eco.config().decimal("casas.reembolso", 0.5, 0, 1);
        long back = (long) Math.floor(h.price() * rate);
        h.owner(null, null);
        save(h);
        eco.deposit(p.getUniqueId(), back, "casa:venda");
        msg.send(p, "casa.vendeu", Messages.vars("casa", h.name()), eco.col(back));
    }

    public void invite(Player owner, House h, OfflinePlayer t) {
        int max = eco.config().integer("casas.max_convidados", 10, 0, 1000);
        if (h.members().size() >= max) {
            msg.send(owner, "casa.convidados_limite", Messages.vars("max", max));
            return;
        }
        if (t.getUniqueId().equals(owner.getUniqueId()) || !h.members().add(t.getUniqueId())) {
            msg.send(owner, "casa.ja_convidado");
            return;
        }
        save(h);
        String name = t.getName() == null ? "?" : t.getName();
        msg.send(owner, "casa.convidou", Messages.vars("jogador", name));
        if (t instanceof Player online) {
            msg.send(online, "casa.foi_convidado", Messages.vars("jogador", owner.getName(), "casa", h.name()));
        }
    }

    /**
     * Abre (ou fecha) todas as casas de um dono para outro jogador, sem passar pelo limite de
     * convidados nem pelas mensagens do /casa. Usado pelo casamento do AincradLife.
     *
     * @return quantas casas mudaram
     */
    public int access(UUID owner, UUID member, boolean allow) {
        int changed = 0;
        for (House h : ownedBy(owner)) {
            if (allow ? h.members().add(member) : h.members().remove(member)) {
                save(h);
                changed++;
            }
        }
        return changed;
    }

    public void uninvite(Player owner, House h, OfflinePlayer t) {
        if (!h.members().remove(t.getUniqueId())) {
            msg.send(owner, "casa.nao_convidado");
            return;
        }
        save(h);
        msg.send(owner, "casa.removeu", Messages.vars("jogador", t.getName() == null ? "?" : t.getName()));
    }

    public void setSpawn(Player owner, House h) {
        if (at(owner.getLocation()) != h) {
            msg.send(owner, "casa.fora");
            return;
        }
        h.spawn(spawnText(owner.getLocation()));
        save(h);
        msg.send(owner, "casa.spawn_definido");
    }

    public String priceText(House h) {
        return EcoMath.format(h.price());
    }

    // ------------------------------------------------------------------ proteção

    private boolean denied(Player p, Location l) {
        House h = at(l);
        if (h == null || p.hasPermission(BYPASS) || h.canUse(p.getUniqueId())) {
            return false;
        }
        msg.actionBar(p, "casa.protegida", Messages.vars("dono", h.forSale() ? "—" : String.valueOf(h.ownerName())));
        return true;
    }

    private void check(Cancellable e, Player p, Location l) {
        if (denied(p, l)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        check(e, e.getPlayer(), e.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent e) {
        check(e, e.getPlayer(), e.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }
        // Portas, alçapões, baús, botões, alavancas... e pisar em placas de pressão.
        boolean use = (e.getAction() == Action.RIGHT_CLICK_BLOCK && usable(b)) || e.getAction() == Action.PHYSICAL;
        if (use && denied(e.getPlayer(), b.getLocation())) {
            e.setCancelled(true);
        }
    }

    /** Blocos que se usam com o clique direito: portas, baús, botões, camas, repetidores... */
    private static boolean usable(Block b) {
        BlockData d = b.getBlockData();
        return d instanceof Openable || d instanceof Switch || d instanceof Powerable || d instanceof Bed
                || d instanceof Repeater || d instanceof Comparator || d instanceof NoteBlock || d instanceof Cake
                || b.getState(false) instanceof InventoryHolder || b.getState(false) instanceof Sign
                || b.getState(false) instanceof Jukebox || b.getState(false) instanceof Lectern;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        check(e, e.getPlayer(), e.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucketFill(PlayerBucketFillEvent e) {
        check(e, e.getPlayer(), e.getBlock().getLocation());
    }

    /** Molduras, suportes de armadura e afins. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityUse(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (!(t instanceof org.bukkit.entity.LivingEntity) || t instanceof org.bukkit.entity.ArmorStand) {
            check(e, e.getPlayer(), t.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p) {
            check(e, p, e.getEntity().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        Entity t = e.getEntity();
        if (e.getDamager() instanceof Player p && (!(t instanceof org.bukkit.entity.LivingEntity) || t instanceof org.bukkit.entity.ArmorStand)) {
            check(e, p, t.getLocation());
        }
    }
}
