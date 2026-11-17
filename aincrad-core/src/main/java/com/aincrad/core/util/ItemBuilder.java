package com.aincrad.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Construtor de itens: nome e lore sem itálico, encantos, modelo do pack e marcas (PDC). */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material m) {
        return new ItemBuilder(new ItemStack(m));
    }

    public static ItemBuilder of(ItemStack base) {
        return new ItemBuilder(base.clone());
    }

    private static Component plain(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public ItemBuilder amount(int n) {
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), n)));
        return this;
    }

    public ItemBuilder name(Component name) {
        meta.displayName(plain(name));
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        lines.forEach(l -> out.add(plain(l)));
        meta.lore(out);
        return this;
    }

    public ItemBuilder enchant(Enchantment e, int level) {
        meta.addEnchant(e, level, true);
        return this;
    }

    public ItemBuilder glint(boolean on) {
        meta.setEnchantmentGlintOverride(on);
        return this;
    }

    /** Modelo de item do resource pack (ex.: aincrad:ui/icons/profile). */
    public ItemBuilder model(NamespacedKey key) {
        meta.setItemModel(key);
        return this;
    }

    public ItemBuilder hideAll() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    /** Marca de texto no item (para reconhecer depois: itens de quest, uniforme...). */
    public ItemBuilder tag(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    public static String tag(ItemStack it, NamespacedKey key) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
