package com.aincrad.ui.screen;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.glyph.Glyph;
import com.aincrad.ui.glyph.SpaceFont;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Modo baú: a tela vira um inventário. Com o pack, o fundo é window_blank (no título) e os
 * ícones são itens com item_model aincrad:ui/...; sem o pack, usa os materiais reserva.
 */
public final class ChestRenderer implements Renderer {

    /** O título do inventário começa 8 px à direita da borda. */
    private static final int TITLE_OFFSET = 8;

    private final AincradUIPlugin plugin;
    private final Player player;
    private final UiHolder holder = new UiHolder();
    private Screen screen;
    private boolean switching;

    public ChestRenderer(AincradUIPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void show(Screen next) {
        this.screen = next;
        boolean pack = plugin.packStatus().hasPack(player);
        Inventory inv = Bukkit.createInventory(holder, next.rows() * 9, title(next, pack));
        holder.inventory(inv);

        for (ScreenElement e : next.elements()) {
            if (e.cursorOnly() || e.slot() < 0 || e.slot() >= inv.getSize()) {
                continue;
            }
            inv.setItem(e.slot(), item(e, pack));
        }
        if (next.infoSlot() >= 0 && next.infoSlot() < inv.getSize() && !next.lines().isEmpty()) {
            List<Component> lines = next.lines();
            ItemStack info = new ItemStack(next.infoMaterial());
            ItemMeta meta = info.getItemMeta();
            meta.displayName(noItalic(lines.get(0)));
            List<Component> lore = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                lore.add(noItalic(lines.get(i)));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            info.setItemMeta(meta);
            inv.setItem(next.infoSlot(), info);
        }

        switching = true;
        try {
            player.openInventory(inv);
        } finally {
            switching = false;
        }
    }

    private Component title(Screen s, boolean pack) {
        Glyph bg = s.chestBackground() == null ? null : plugin.glyphs().get(s.chestBackground());
        if (!pack || bg == null || !bg.present()) {
            return s.title();
        }
        return Component.empty()
                .append(SpaceFont.component(-TITLE_OFFSET))
                .append(bg.component(NamedTextColor.WHITE))
                .append(SpaceFont.component(-bg.advance() + TITLE_OFFSET))
                .append(s.title());
    }

    private ItemStack item(ScreenElement e, boolean pack) {
        ItemStack stack;
        boolean art = false;
        if (e.item() != null) {
            stack = e.item().clone();
        } else {
            stack = new ItemStack(e.fallback());
            String glyphId = e.selected() && e.glyphOn() != null && plugin.glyphs().has(e.glyphOn()) ? e.glyphOn() : e.glyph();
            Glyph g = plugin.glyphs().get(glyphId);
            art = pack && g != null && g.present();
            if (art) {
                stack = new ItemStack(org.bukkit.Material.PAPER);
                ItemMeta meta = stack.getItemMeta();
                meta.setItemModel(new NamespacedKey("aincrad", g.modelPath()));
                stack.setItemMeta(meta);
            }
        }
        if (e.item() == null) {
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(noItalic(e.label()));
            List<Component> lore = new ArrayList<>(e.lore());
            if (e.locked()) {
                lore.add(plugin.messages().get("menu.em_breve_lore"));
            }
            meta.lore(lore.stream().map(ChestRenderer::noItalic).toList());
            meta.addItemFlags(ItemFlag.values());
            if (e.selected() && !art) {
                meta.setEnchantmentGlintOverride(true);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** True enquanto o renderizador troca de inventário (o evento de fechar deve ser ignorado). */
    public boolean switching() {
        return switching;
    }

    public boolean owns(InventoryHolder h) {
        return h == holder;
    }

    @Override
    public void tick() {
    }

    @Override
    public ScreenElement hovered() {
        return null;
    }

    @Override
    public ScreenElement atSlot(int slot) {
        if (screen == null) {
            return null;
        }
        for (ScreenElement e : screen.elements()) {
            if (!e.cursorOnly() && e.slot() == slot) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void destroy() {
        screen = null;
        if (player.getOpenInventory().getTopInventory().getHolder(false) == holder) {
            switching = true;
            try {
                player.closeInventory();
            } finally {
                switching = false;
            }
        }
    }
}
