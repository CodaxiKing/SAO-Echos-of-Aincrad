package com.aincrad.ui.screen;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Um ícone ou botão de uma tela. A mesma descrição serve aos dois modos de desenho:
 * x/y/size (blocos, no plano do menu) para o modo cursor e slot para o modo baú.
 */
public final class ScreenElement {

    private final String id;
    private String glyph;
    private String glyphOn;
    private Component label = Component.empty();
    private List<Component> lore = List.of();
    private double x;
    private double y;
    private double size = 0.3;
    private int slot = -1;
    private Material fallback = Material.PAPER;
    private ItemStack item;
    private boolean locked;
    private boolean selected;
    private boolean interactive = true;
    private boolean chestOnly;
    private boolean cursorOnly;
    private ElementAction action;

    public ScreenElement(String id) {
        this.id = id;
    }

    public ScreenElement glyph(String glyph, String glyphOn) {
        this.glyph = glyph;
        this.glyphOn = glyphOn;
        return this;
    }

    public ScreenElement label(Component label) {
        this.label = label;
        return this;
    }

    public ScreenElement lore(List<Component> lore) {
        this.lore = List.copyOf(lore);
        return this;
    }

    public ScreenElement at(double x, double y, double size) {
        this.x = x;
        this.y = y;
        this.size = size;
        return this;
    }

    public ScreenElement slot(int slot) {
        this.slot = slot;
        return this;
    }

    public ScreenElement fallback(Material fallback) {
        this.fallback = fallback;
        return this;
    }

    public ScreenElement item(ItemStack item) {
        this.item = item;
        return this;
    }

    public ScreenElement locked(boolean locked) {
        this.locked = locked;
        return this;
    }

    public ScreenElement selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public ScreenElement decorative() {
        this.interactive = false;
        return this;
    }

    public ScreenElement onlyInChest() {
        this.chestOnly = true;
        return this;
    }

    public ScreenElement onlyInCursor() {
        this.cursorOnly = true;
        return this;
    }

    public ScreenElement action(ElementAction action) {
        this.action = action;
        return this;
    }

    public String id() {
        return id;
    }

    public String glyph() {
        return glyph;
    }

    public String glyphOn() {
        return glyphOn;
    }

    public Component label() {
        return label;
    }

    public List<Component> lore() {
        return lore;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double size() {
        return size;
    }

    public int slot() {
        return slot;
    }

    public Material fallback() {
        return fallback;
    }

    public ItemStack item() {
        return item;
    }

    public boolean locked() {
        return locked;
    }

    public boolean selected() {
        return selected;
    }

    public boolean interactive() {
        return interactive;
    }

    public boolean chestOnly() {
        return chestOnly;
    }

    public boolean cursorOnly() {
        return cursorOnly;
    }

    public ElementAction action() {
        return action;
    }
}
