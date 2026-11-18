package com.aincrad.reputation;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.reputation.model.WorldMarkRules;
import com.aincrad.reputation.model.WorldMarkRules.Grave;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Túmulos: quando um jogador morre, fica uma lápide no lugar, com o nome e a data. É feita de
 * entidades de enfeite, não de blocos — não dá para quebrar e não mexe no mapa. Some depois de um
 * prazo (ou nunca), há um limite por pedaço de mapa, e outro jogador pode deixar uma flor.
 */
public final class GraveService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS rep_graves ({AUTO_ID}, uuid VARCHAR(36) NOT NULL, name VARCHAR(32) NOT NULL, server VARCHAR(64) NOT NULL, "
                    + "world VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, at_time BIGINT NOT NULL, "
                    + "expires_at BIGINT NOT NULL, flowers INT NOT NULL)");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    /** As entidades de um túmulo que está no mundo agora. */
    private record Body(BlockDisplay stone, TextDisplay text, Interaction touch) {
    }

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private final ConfigFile config;
    private final NamespacedKey key;
    private final Map<Long, Grave> graves = new HashMap<>();
    private final Map<Long, Body> bodies = new HashMap<>();
    private final Map<UUID, Long> flowerCooldown = new HashMap<>();

    public GraveService(Plugin plugin, Database db, Messages msg, ConfigFile config) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.config = config;
        key = new NamespacedKey(plugin, "tumulo");
    }

    private boolean enabled() {
        return config.bool("tumulos.ativo", true);
    }

    public void loadBlocking() throws SQLException {
        removeBodies();
        graves.clear();
        String server = AincradCore.get().server();
        for (Grave g : db.sync(c -> Database.query(c, "SELECT id, uuid, name, world, x, y, z, at_time, expires_at, flowers FROM rep_graves WHERE server = ?",
                rs -> new Grave(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7),
                        rs.getLong(8), rs.getLong(9), rs.getInt(10)), server))) {
            graves.put(g.id(), g);
        }
    }

    // ------------------------------------------------------------------ no mundo

    private Component text(Grave g) {
        Component c = msg.get("tumulo.texto", Messages.vars("jogador", g.name(), "data", DATE.format(Instant.ofEpochMilli(g.at()))));
        return g.flowers() <= 0 ? c : c.append(Component.newline()).append(msg.get("tumulo.flores", Messages.vars("flores", g.flowers())));
    }

    private void spawn(Grave g) {
        World w = Bukkit.getWorld(g.world());
        if (w == null || bodies.containsKey(g.id()) || !w.isChunkLoaded(g.chunkX(), g.chunkZ())) {
            return;
        }
        Location base = new Location(w, g.x(), g.y(), g.z());
        Material stone = Material.matchMaterial(config.string("tumulos.bloco", "MOSSY_COBBLESTONE_WALL"));
        Material block = stone == null || !stone.isBlock() ? Material.MOSSY_COBBLESTONE_WALL : stone;
        // Nada disto é salvo com o mapa: o plugin põe de volta quando o pedaço carrega, então não duplica.
        BlockDisplay s = w.spawn(base, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(block.createBlockData());
            d.getPersistentDataContainer().set(key, PersistentDataType.LONG, g.id());
        });
        TextDisplay t = w.spawn(base.clone().add(0.5, 1.35, 0.5), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(text(g));
            d.getPersistentDataContainer().set(key, PersistentDataType.LONG, g.id());
        });
        Interaction i = w.spawn(base.clone().add(0.5, 0, 0.5), Interaction.class, d -> {
            d.setPersistent(false);
            d.setInteractionWidth(1.0f);
            d.setInteractionHeight(1.2f);
            d.getPersistentDataContainer().set(key, PersistentDataType.LONG, g.id());
        });
        bodies.put(g.id(), new Body(s, t, i));
    }

    private static void remove(Body b) {
        if (b != null) {
            b.stone().remove();
            b.text().remove();
            b.touch().remove();
        }
    }

    public void removeBodies() {
        bodies.values().forEach(GraveService::remove);
        bodies.clear();
    }

    private void delete(Grave g) {
        graves.remove(g.id());
        remove(bodies.remove(g.id()));
        db.update("DELETE FROM rep_graves WHERE id = ?", g.id());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Grave g : graves.values()) {
            if (g.world().equals(event.getWorld().getName()) && g.chunkX() == event.getChunk().getX() && g.chunkZ() == event.getChunk().getZ()) {
                remove(bodies.remove(g.id()));
            }
        }
    }

    /** A cada 10 segundos: os túmulos vencidos somem; os de pedaços carregados aparecem. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Grave g : new ArrayList<>(graves.values())) {
            if (g.expired(now) || !enabled()) {
                if (g.expired(now)) {
                    delete(g);
                } else {
                    remove(bodies.remove(g.id()));
                }
                continue;
            }
            Body b = bodies.get(g.id());
            if (b != null && !b.stone().isValid()) {
                remove(bodies.remove(g.id()));
                b = null;
            }
            if (b == null) {
                spawn(g);
            }
        }
    }

    // ------------------------------------------------------------------ morrer e deixar flor

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled()) {
            return;
        }
        Player p = event.getEntity();
        Location l = p.getLocation();
        World w = l.getWorld();
        // No chão: a lápide não fica no ar se o jogador morreu caindo.
        int y = Math.max(w.getMinHeight(), Math.min(w.getMaxHeight() - 2, l.getBlockY()));
        while (y > w.getMinHeight() && w.getBlockAt(l.getBlockX(), y - 1, l.getBlockZ()).isPassable()) {
            y--;
        }
        if (y <= w.getMinHeight()) {
            return; // Caiu no vazio: não há onde pôr a lápide.
        }
        long now = System.currentTimeMillis();
        long expires = WorldMarkRules.expiresAt(now, config.integer("tumulos.dias", 30, 0, 36_500));
        int x = l.getBlockX();
        int z = l.getBlockZ();
        int fy = y;
        String server = AincradCore.get().server();
        List<Grave> sameChunk = new ArrayList<>();
        for (Grave g : graves.values()) {
            if (g.world().equals(w.getName()) && g.chunkX() == (x >> 4) && g.chunkZ() == (z >> 4)) {
                sameChunk.add(g);
            }
        }
        WorldMarkRules.overflow(sameChunk, config.integer("tumulos.maximo_por_pedaco", 4, 1, 64)).forEach(this::delete);
        db.async(c -> {
            Database.update(c, AincradCore.get().database().sql("INSERT INTO rep_graves (uuid, name, server, world, x, y, z, at_time, expires_at, flowers) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"), p.getUniqueId().toString(), p.getName(), server, w.getName(), x, fy, z, now, expires);
            List<Long> ids = Database.query(c, "SELECT MAX(id) FROM rep_graves WHERE uuid = ? AND at_time = ?", rs -> rs.getLong(1),
                    p.getUniqueId().toString(), now);
            return ids.isEmpty() ? 0L : ids.get(0);
        }).whenComplete((id, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null || id == 0) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível gravar o túmulo de " + p.getName(), ex);
                return;
            }
            Grave g = new Grave(id, p.getUniqueId().toString(), p.getName(), w.getName(), x, fy, z, now, expires, 0);
            graves.put(id, g);
            spawn(g);
        }));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        Long id = e.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        Grave g = graves.get(id);
        if (g == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!Tag.SMALL_FLOWERS.isTagged(hand.getType())) {
            msg.send(p, "tumulo.aqui_jaz", Messages.vars("jogador", g.name(), "data", DATE.format(Instant.ofEpochMilli(g.at())),
                    "flores", g.flowers()));
            return;
        }
        long now = System.currentTimeMillis();
        if (flowerCooldown.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        flowerCooldown.put(p.getUniqueId(), now + 5000);
        hand.setAmount(hand.getAmount() - 1);
        Grave next = new Grave(g.id(), g.uuid(), g.name(), g.world(), g.x(), g.y(), g.z(), g.at(), g.expiresAt(), g.flowers() + 1);
        graves.put(g.id(), next);
        db.update("UPDATE rep_graves SET flowers = flowers + 1 WHERE id = ?", g.id());
        Body b = bodies.get(g.id());
        if (b != null) {
            b.text().text(text(next));
        }
        e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, 1.2, 0), 3, 0.2, 0.2, 0.2, 0);
        msg.send(p, "tumulo.flor", Messages.vars("jogador", g.name()));
    }
}
