package com.aincrad.rpg.item;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Itens únicos do servidor: um modelo com {@code unico: true} existe uma vez só na rede inteira.
 * Cada exemplar leva um número de série gravado no banco; criar o segundo é recusado, o item no
 * chão não é destruído nem some, e uma cópia com série velha se desfaz. Se o dono não é visto com
 * o item por um prazo, ele volta a poder ser criado.
 */
public final class UniqueItemService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS rpg_unique (item VARCHAR(64) NOT NULL PRIMARY KEY, serial VARCHAR(36) NOT NULL, "
                    + "holder VARCHAR(36), holder_name VARCHAR(32), created_at BIGINT NOT NULL, seen_at BIGINT NOT NULL)");

    /** O exemplar que existe de um item único. */
    public record Entry(String serial, String holderName, long createdAt, long seenAt) {
    }

    /** O dono some: sem ser visto com o item por tantos dias (0 = nunca). */
    public static boolean stale(long seenAt, long now, int days) {
        return days > 0 && now - seenAt >= days * 86_400_000L;
    }

    /** O item na mão é o exemplar de verdade? Uma cópia com a série velha não vale. */
    public static boolean genuine(String serialOnItem, Entry current) {
        return current != null && current.serial().equals(serialOnItem);
    }

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private final ItemService items;
    private final NamespacedKey keySerial;
    private volatile Map<String, Entry> entries = Map.of();
    private final Set<UUID> onGround = new HashSet<>();
    private int staleDays = 30;

    public UniqueItemService(Plugin plugin, Database db, Messages msg, ItemService items) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.items = items;
        keySerial = new NamespacedKey(plugin, "unico_serie");
    }

    public void reload(ConfigFile config) {
        staleDays = config.integer("unicos.dono_some_dias", 30, 0, 36_500);
    }

    private static Map<String, Entry> read(java.sql.Connection c) throws SQLException {
        Map<String, Entry> m = new ConcurrentHashMap<>();
        Database.query(c, "SELECT item, serial, holder_name, created_at, seen_at FROM rpg_unique",
                rs -> m.put(rs.getString(1), new Entry(rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5))));
        return m;
    }

    public void loadBlocking() throws SQLException {
        entries = db.sync(UniqueItemService::read);
    }

    /**
     * Reserva o exemplar de um item único. Roda na hora (uma consulta ao banco na thread do
     * servidor: é raro, e é o que garante que dois servidores não criem o mesmo item).
     *
     * @return o número de série para gravar no item, ou null se o item já existe
     */
    public String reserve(String id) {
        if (entries.containsKey(id)) {
            return null;
        }
        String serial = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        try {
            db.sync(c -> Database.update(c, "INSERT INTO rpg_unique (item, serial, holder, holder_name, created_at, seen_at) "
                    + "VALUES (?, ?, NULL, NULL, ?, ?)", id, serial, now, now));
        } catch (SQLException e) {
            // A chave primária recusou: outro servidor criou primeiro.
            refresh();
            return null;
        }
        Map<String, Entry> m = new ConcurrentHashMap<>(entries);
        m.put(id, new Entry(serial, null, now, now));
        entries = m;
        return serial;
    }

    public void tag(ItemStack it, String serial) {
        it.editMeta(meta -> meta.getPersistentDataContainer().set(keySerial, PersistentDataType.STRING, serial));
    }

    private String serial(ItemStack it) {
        return it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(keySerial, PersistentDataType.STRING);
    }

    public void refresh() {
        db.async(UniqueItemService::read).thenAccept(m -> entries = m).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler os itens únicos", ex);
            return null;
        });
    }

    // ------------------------------------------------------------------ quem tem, e as cópias velhas

    private void scan(Player p, Inventory inv, long now, List<Object[]> seen) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            String serial = serial(it);
            if (serial == null) {
                continue;
            }
            String id = items.id(it);
            if (!genuine(serial, id == null ? null : entries.get(id))) {
                // Série velha: o exemplar de verdade é outro (este ficou guardado enquanto o item voltava a cair).
                inv.setItem(i, null);
                msg.send(p, "unico.desfez");
                continue;
            }
            seen.add(new Object[] {id, p.getUniqueId().toString(), p.getName(), now});
        }
    }

    /** A cada minuto: anota quem está com cada item único, e libera os de dono sumido. */
    public void tick() {
        long now = System.currentTimeMillis();
        List<Object[]> seen = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            scan(p, p.getInventory(), now, seen);
            scan(p, p.getEnderChest(), now, seen);
        }
        List<String> gone = new ArrayList<>();
        entries.forEach((id, e) -> {
            if (stale(e.seenAt(), now, staleDays) && seen.stream().noneMatch(s -> s[0].equals(id))) {
                gone.add(id);
            }
        });
        if (seen.isEmpty() && gone.isEmpty()) {
            return;
        }
        db.async(c -> {
            for (Object[] s : seen) {
                Database.update(c, "UPDATE rpg_unique SET holder = ?, holder_name = ?, seen_at = ? WHERE item = ?", s[1], s[2], s[3], s[0]);
            }
            for (String id : gone) {
                // Só apaga se ninguém foi visto com ele nesse meio-tempo, em outro servidor.
                Database.update(c, "DELETE FROM rpg_unique WHERE item = ? AND seen_at <= ?", id, now - staleDays * 86_400_000L);
            }
            return read(c);
        }).thenAccept(m -> entries = m).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível atualizar os itens únicos", ex);
            return null;
        });
    }

    // ------------------------------------------------------------------ o item no chão não se perde

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (serial(item.getItemStack()) != null) {
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
            item.setGlowing(true);
            onGround.add(item.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (serial(event.getEntity().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && serial(item.getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    /** A cada segundo: um item único que caiu no vazio volta ao ponto de chegada do mundo. */
    public void tickGround() {
        onGround.removeIf(id -> {
            if (!(Bukkit.getEntity(id) instanceof Item item) || !item.isValid()) {
                return true;
            }
            if (item.getLocation().getY() < item.getWorld().getMinHeight() + 2) {
                item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                item.teleport(item.getWorld().getSpawnLocation().add(0.5, 1, 0.5));
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ /unicos

    public void list(Player p) {
        Set<String> ids = items.uniqueIds();
        if (ids.isEmpty()) {
            msg.send(p, "unico.nenhum");
            return;
        }
        Menu menu = Menu.of(msg.get("unico.menu")).rows(3);
        menu.floating();
        for (String id : ids) {
            ItemService.Template t = items.template(id);
            Entry e = entries.get(id);
            menu.item(Menu.Button.of(new ItemStack(e == null ? Material.GRAY_DYE : t.material()),
                    msg.get(e == null ? "unico.item_oculto" : "unico.item", Messages.vars("nome", t.name())), pl -> { })
                    .lore(List.of(e == null ? msg.get("unico.nao_encontrado")
                            : msg.get(e.holderName() == null ? "unico.sem_dono" : "unico.dono", Messages.vars("jogador", String.valueOf(e.holderName()))))));
        }
        AincradCore.get().menus().open(p, menu);
    }
}
