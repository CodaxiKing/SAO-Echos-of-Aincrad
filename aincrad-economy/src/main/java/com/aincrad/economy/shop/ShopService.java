package com.aincrad.economy.shop;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.economy.EconomyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lojas de NPC de shops.yml, com preços fixos. Abrem clicando num bloco ou entidade ligados pelo
 * admin (ligações no banco, valem para a rede) ou pela API ({@code openShop}).
 */
public final class ShopService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS eco_shop_links (ref VARCHAR(128) NOT NULL PRIMARY KEY, "
                    + "kind VARCHAR(8) NOT NULL, shop VARCHAR(64) NOT NULL)");
    public static final String BUS_CHANNEL = "economy:shops";

    /** Um item à venda. {@code buy}/{@code sell} = -1 quando não compra/vende. */
    public record Offer(String spec, int amount, long buy, long sell) {
    }

    /** Uma loja de shops.yml. */
    public record Shop(String id, String title, List<Offer> offers) {
    }

    private final Plugin plugin;
    private final EconomyService eco;
    private final Messages msg;
    private final ConfigFile file;
    private final Database db;
    private final NamespacedKey rpgItemKey = NamespacedKey.fromString("aincradrpg:item");
    private Map<String, Shop> shops = Map.of();
    private final Map<String, String> links = new ConcurrentHashMap<>();

    public ShopService(Plugin plugin, EconomyService eco, ConfigFile file, Database db) {
        this.plugin = plugin;
        this.eco = eco;
        this.msg = eco.messages();
        this.file = file;
        this.db = db;
        load();
    }

    public void reload() {
        file.reload();
        load();
    }

    private void load() {
        Map<String, Shop> out = new LinkedHashMap<>();
        ConfigurationSection root = file.yaml().getConfigurationSection("lojas");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                List<Offer> offers = new ArrayList<>();
                for (Map<?, ?> m : s.getMapList("itens")) {
                    Object item = m.get("item");
                    if (item == null) {
                        continue;
                    }
                    offers.add(new Offer(item.toString(), Math.max(1, num(m.get("quantidade"), 1)),
                            num(m.get("compra"), -1), num(m.get("venda"), -1)));
                }
                String key = id.toLowerCase(Locale.ROOT);
                out.put(key, new Shop(key, s.getString("titulo", id), List.copyOf(offers)));
            }
        }
        shops = Collections.unmodifiableMap(out);
    }

    private static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    /** Lê as ligações do banco nesta thread (ao ligar). */
    public void loadLinksBlocking() throws SQLException {
        Map<String, String> m = new LinkedHashMap<>();
        db.sync(c -> {
            for (String[] row : Database.query(c, "SELECT ref, shop FROM eco_shop_links", rs -> new String[]{rs.getString(1), rs.getString(2)})) {
                m.put(row[0], row[1]);
            }
            return null;
        });
        links.clear();
        links.putAll(m);
    }

    public void reloadLinksAsync() {
        db.query("SELECT ref, shop FROM eco_shop_links", rs -> new String[]{rs.getString(1), rs.getString(2)})
                .thenAccept(rows -> AincradCore.get().runSync(() -> {
                    links.clear();
                    for (String[] r : rows) {
                        links.put(r[0], r[1]);
                    }
                }));
    }

    public Set<String> ids() {
        return shops.keySet();
    }

    public int linkCount() {
        return links.size();
    }

    public static String ref(Block b) {
        return "b:" + b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public static String ref(Entity e) {
        return "e:" + e.getUniqueId();
    }

    public void link(String ref, String shop) {
        links.put(ref, shop);
        db.update(db.upsert("eco_shop_links", List.of("ref"), List.of("kind", "shop")), ref, ref.substring(0, 1), shop)
                .thenRun(() -> AincradCore.get().publish(BUS_CHANNEL, "link"));
    }

    public boolean unlink(String ref) {
        if (links.remove(ref) == null) {
            return false;
        }
        db.update("DELETE FROM eco_shop_links WHERE ref = ?", ref)
                .thenRun(() -> AincradCore.get().publish(BUS_CHANNEL, "unlink"));
        return true;
    }

    public String linked(String ref) {
        return links.get(ref);
    }

    // ------------------------------------------------------------------ cliques

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        String shop = links.get(ref(event.getClickedBlock()));
        if (shop != null && !event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            open(event.getPlayer(), shop);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String shop = links.get(ref(event.getRightClicked()));
        if (shop != null) {
            event.setCancelled(true);
            open(event.getPlayer(), shop);
        }
    }

    /** NPC de loja não morre. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (links.containsKey(ref(event.getEntity()))) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ menus

    public boolean open(Player p, String id) {
        Shop shop = id == null ? null : shops.get(id.toLowerCase(Locale.ROOT));
        if (shop == null) {
            msg.send(p, "loja.desconhecida", Messages.vars("id", String.valueOf(id)));
            return false;
        }
        Menu menu = Menu.of(Messages.parse(shop.title())).rows(6);
        for (Offer o : shop.offers()) {
            ItemStack icon = create(o, 1);
            if (icon == null) {
                continue;
            }
            List<Component> lore = new ArrayList<>();
            if (o.buy() >= 0) {
                lore.add(msg.get("loja.comprar_lore", eco.col(o.buy()), Messages.vars("qtd", o.amount())));
            }
            if (o.sell() >= 0) {
                lore.add(msg.get("loja.vender_lore", eco.col(o.sell()), Messages.vars("qtd", 1)));
            }
            lore.add(msg.get("loja.clique_lore"));
            menu.item(Menu.Button.of(icon, name(icon), pl -> detail(pl, shop, o)).lore(lore));
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("loja.fechar"), pl -> { }).closing());
        AincradCore.get().menus().open(p, menu);
        return true;
    }

    private void detail(Player p, Shop shop, Offer o) {
        ItemStack icon = create(o, 1);
        if (icon == null) {
            msg.send(p, "loja.indisponivel");
            return;
        }
        Menu menu = Menu.of(msg.get("loja.detalhe_titulo")).rows(3);
        menu.item(Menu.Button.of(icon, name(icon), null));
        if (o.buy() >= 0) {
            menu.item(Menu.Button.of(ItemBuilder.of(Material.LIME_DYE).build(),
                    msg.get("loja.comprar", eco.col(o.buy()), Messages.vars("qtd", o.amount())), pl -> {
                        buy(pl, o, 1);
                        detail(pl, shop, o);
                    }));
            int max = Math.max(1, icon.getMaxStackSize() / o.amount());
            if (max >= 8) {
                menu.item(Menu.Button.of(ItemBuilder.of(Material.LIME_DYE).amount(8).build(),
                        msg.get("loja.comprar", eco.col(o.buy() * 8), Messages.vars("qtd", o.amount() * 8)), pl -> {
                            buy(pl, o, 8);
                            detail(pl, shop, o);
                        }));
            }
        }
        if (o.sell() >= 0) {
            int have = count(p, o);
            menu.item(Menu.Button.of(ItemBuilder.of(Material.GOLD_NUGGET).build(),
                    msg.get("loja.vender", eco.col(o.sell()), Messages.vars("qtd", 1)), pl -> {
                        sell(pl, o, 1);
                        detail(pl, shop, o);
                    }));
            if (have > 1) {
                menu.item(Menu.Button.of(ItemBuilder.of(Material.GOLD_INGOT).build(),
                        msg.get("loja.vender_tudo", eco.col(o.sell() * have), Messages.vars("qtd", have)), pl -> {
                            sell(pl, o, count(pl, o));
                            detail(pl, shop, o);
                        }));
            }
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.ARROW).build(), msg.get("loja.voltar"), pl -> open(pl, shop.id())));
        AincradCore.get().menus().open(p, menu);
    }

    private Component name(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        Component n = m != null && m.hasDisplayName() ? m.displayName() : null;
        return n != null ? n : Component.translatable(it.translationKey());
    }

    private void buy(Player p, Offer o, int times) {
        long price = o.buy() * times;
        if (!eco.has(p, price)) {
            msg.send(p, "loja.sem_saldo", eco.col(price));
            return;
        }
        List<ItemStack> give = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            ItemStack it = create(o, o.amount());
            if (it == null) {
                msg.send(p, "loja.indisponivel");
                return;
            }
            give.add(it);
        }
        if (!eco.withdraw(p.getUniqueId(), price, "loja:compra")) {
            msg.send(p, "loja.sem_saldo", eco.col(price));
            return;
        }
        boolean dropped = false;
        for (ItemStack it : give) {
            for (ItemStack rest : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
                dropped = true;
            }
        }
        msg.send(p, "loja.comprou", Messages.vars("qtd", o.amount() * times), eco.col(price),
                Placeholder.component("item", name(give.get(0))));
        if (dropped) {
            msg.send(p, "loja.inventario_cheio");
        }
    }

    private void sell(Player p, Offer o, int amount) {
        if (amount <= 0 || count(p, o) < amount) {
            msg.send(p, "loja.sem_item");
            return;
        }
        ItemStack sample = create(o, 1);
        take(p, o, amount);
        long got = o.sell() * amount;
        eco.deposit(p.getUniqueId(), got, "loja:venda");
        msg.send(p, "loja.vendeu", Messages.vars("qtd", amount), eco.col(got),
                Placeholder.component("item", sample == null ? Component.text(o.spec()) : name(sample)));
    }

    // ------------------------------------------------------------------ itens

    /** Item da oferta: material ou "rpg:id[:raridade]" (pela API do AincradRPG). */
    private ItemStack create(Offer o, int amount) {
        String spec = o.spec();
        if (spec.toLowerCase(Locale.ROOT).startsWith("rpg:")) {
            String[] parts = spec.split(":");
            ItemStack it = rpgItem(parts[1], parts.length > 2 ? parts[2] : "comum");
            if (it != null) {
                it.setAmount(Math.min(amount, it.getMaxStackSize()));
            }
            return it;
        }
        Material m = Material.matchMaterial(spec);
        return m == null || !m.isItem() ? null : new ItemStack(m, Math.min(amount, m.getMaxStackSize()));
    }

    private Method rpgCreate;

    private ItemStack rpgItem(String id, String rarity) {
        try {
            if (rpgCreate == null) {
                if (plugin.getServer().getPluginManager().getPlugin("AincradRPG") == null) {
                    return null;
                }
                Class<?> api = Class.forName("com.aincrad.rpg.api.AincradRPGAPI", true,
                        plugin.getServer().getPluginManager().getPlugin("AincradRPG").getClass().getClassLoader());
                rpgCreate = api.getMethod("createItem", String.class, String.class);
            }
            Object r = rpgCreate.invoke(null, id, rarity);
            return r instanceof ItemStack it ? it : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Item do RPG " + id + " indisponível: " + e.getMessage());
            return null;
        }
    }

    /** O item do inventário serve para vender esta oferta? (itens com nome/marca de outros plugins não). */
    private boolean matches(ItemStack it, Offer o) {
        if (it == null || it.getType().isAir()) {
            return false;
        }
        String spec = o.spec();
        if (spec.toLowerCase(Locale.ROOT).startsWith("rpg:")) {
            String id = spec.split(":")[1];
            if (!it.hasItemMeta() || rpgItemKey == null) {
                return false;
            }
            return id.equalsIgnoreCase(it.getItemMeta().getPersistentDataContainer().get(rpgItemKey, PersistentDataType.STRING));
        }
        Material m = Material.matchMaterial(spec);
        if (m == null || it.getType() != m) {
            return false;
        }
        if (!it.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = it.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && meta.getPersistentDataContainer().isEmpty();
    }

    private int count(Player p, Offer o) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (matches(it, o)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private void take(Player p, Offer o, int amount) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] items = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < items.length && left > 0; i++) {
            if (matches(items[i], o)) {
                int t = Math.min(left, items[i].getAmount());
                items[i].setAmount(items[i].getAmount() - t);
                left -= t;
                if (items[i].getAmount() <= 0) {
                    items[i] = null;
                }
            }
        }
        inv.setStorageContents(items);
    }

    /** Para o /aincrad loja: alvo legível ("bloco em x, y, z" / tipo da entidade). */
    public static String describe(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }
}
