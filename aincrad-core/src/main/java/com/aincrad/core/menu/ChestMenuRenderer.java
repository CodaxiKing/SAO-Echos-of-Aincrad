package com.aincrad.core.menu;

import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Renderizador padrão: menu de baú com páginas. Funciona para todos (inclusive sem resource pack). */
public final class ChestMenuRenderer implements MenuRenderer, Listener {

    /** Marca o inventário e guarda o que cada slot faz. */
    static final class Holder implements InventoryHolder {
        final Menu menu;
        final int page;
        final Map<Integer, Menu.Button> slots = new HashMap<>();
        Inventory inventory;
        boolean switching;

        Holder(Menu menu, int page) {
            this.menu = menu;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Plugin core;
    private final Messages msg;

    public ChestMenuRenderer(Plugin core, Messages msg) {
        this.core = core;
        this.msg = msg;
    }

    @Override
    public String id() {
        return "bau";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean supports(Player player) {
        return true;
    }

    @Override
    public void open(Player player, Menu menu) {
        open(player, menu, 0);
    }

    /** Slots da linha de botões: o primeiro e o último são as setas; os do meio, os botões. */
    static int[] buttonSlots(int rows) {
        int base = (rows - 1) * 9;
        return new int[]{base + 2, base + 3, base + 4, base + 5, base + 6};
    }

    void open(Player player, Menu menu, int page) {
        int pages = menu.pages();
        int pg = Math.max(0, Math.min(pages - 1, page));
        Holder h = new Holder(menu, pg);
        Component title = pages > 1 ? menu.title().append(Component.text(" " + (pg + 1) + "/" + pages)) : menu.title();
        Inventory inv = Bukkit.createInventory(h, menu.rows() * 9, title);
        h.inventory = inv;

        int size = menu.pageSize();
        List<Menu.Button> items = menu.items();
        for (int i = 0; i < size && pg * size + i < items.size(); i++) {
            Menu.Button b = items.get(pg * size + i);
            inv.setItem(i, render(b));
            h.slots.put(i, b);
        }
        int base = (menu.rows() - 1) * 9;
        if (pg > 0) {
            inv.setItem(base, render(nav(msg.get("menu.anterior"), p -> open(p, menu, pg - 1))));
            h.slots.put(base, nav(msg.get("menu.anterior"), p -> open(p, menu, pg - 1)));
        }
        if (pg < pages - 1) {
            inv.setItem(base + 8, render(nav(msg.get("menu.proxima"), p -> open(p, menu, pg + 1))));
            h.slots.put(base + 8, nav(msg.get("menu.proxima"), p -> open(p, menu, pg + 1)));
        }
        int[] bs = buttonSlots(menu.rows());
        List<Menu.Button> buttons = menu.buttons();
        for (int i = 0; i < buttons.size() && i < bs.length; i++) {
            inv.setItem(bs[i], render(buttons.get(i)));
            h.slots.put(bs[i], buttons.get(i));
        }
        Holder current = player.getOpenInventory().getTopInventory().getHolder(false) instanceof Holder ch ? ch : null;
        if (current != null) {
            current.switching = true; // trocar de página não conta como fechar
        }
        player.openInventory(inv);
    }

    private static Menu.Button nav(Component name, Consumer<Player> action) {
        return Menu.Button.of(new ItemStack(Material.ARROW), name, action);
    }

    private static ItemStack render(Menu.Button b) {
        ItemStack it = b.icon() == null ? new ItemStack(Material.PAPER) : b.icon().clone();
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(b.name().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (!b.lore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (Component l : b.lore()) {
                    lore.add(l.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                }
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder h) || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Menu.Button b = h.slots.get(event.getSlot());
        if (b == null || b.action() == null) {
            return;
        }
        // Trocar ou fechar inventário dentro do clique é instável: roda no próximo tick.
        Bukkit.getScheduler().runTask(core, () -> {
            if (b.closeOnClick()) {
                h.switching = true;
                p.closeInventory();
            }
            b.action().accept(p);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Holder h && !h.switching
                && h.menu.onClose() != null && event.getPlayer() instanceof Player p) {
            h.menu.onClose().accept(p);
        }
    }
}
