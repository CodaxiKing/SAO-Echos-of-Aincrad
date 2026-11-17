package com.aincrad.ui.screen;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Descrição de uma tela (menu, perfil, confirmação...), desenhada por um {@link Renderer}. */
public final class Screen {

    private final String id;
    private Component title = Component.empty();
    private String background;
    private double bgX;
    private double bgY;
    private double bgSize;
    private String chestBackground;
    private final List<ScreenElement> elements = new ArrayList<>();
    private final List<Component> lines = new ArrayList<>();
    private double textX;
    private double textY;
    private double textScale = 0.5;
    private int infoSlot = -1;
    private Material infoMaterial = Material.BOOK;
    private int rows = 6;

    public Screen(String id) {
        this.id = id;
    }

    public Screen title(Component title) {
        this.title = title;
        return this;
    }

    /** Fundo do modo cursor: glifo centrado em (x, y) com altura size (blocos). */
    public Screen background(String glyph, double x, double y, double size) {
        this.background = glyph;
        this.bgX = x;
        this.bgY = y;
        this.bgSize = size;
        return this;
    }

    /** Fundo do modo baú: glifo desenhado no título do inventário. */
    public Screen chestBackground(String glyph) {
        this.chestBackground = glyph;
        return this;
    }

    public Screen add(ScreenElement e) {
        elements.add(e);
        return this;
    }

    /** Bloco de texto; no modo cursor fica com a base em (x, y), no baú vira o item de informação. */
    public Screen text(List<Component> lines, double x, double y, double scale) {
        this.lines.clear();
        this.lines.addAll(lines);
        this.textX = x;
        this.textY = y;
        this.textScale = scale;
        return this;
    }

    public Screen info(int slot, Material material) {
        this.infoSlot = slot;
        this.infoMaterial = material;
        return this;
    }

    public Screen rows(int rows) {
        this.rows = Math.max(1, Math.min(6, rows));
        return this;
    }

    public String id() {
        return id;
    }

    public Component title() {
        return title;
    }

    public String background() {
        return background;
    }

    public double bgX() {
        return bgX;
    }

    public double bgY() {
        return bgY;
    }

    public double bgSize() {
        return bgSize;
    }

    public String chestBackground() {
        return chestBackground;
    }

    public List<ScreenElement> elements() {
        return Collections.unmodifiableList(elements);
    }

    public List<Component> lines() {
        return Collections.unmodifiableList(lines);
    }

    public double textX() {
        return textX;
    }

    public double textY() {
        return textY;
    }

    public double textScale() {
        return textScale;
    }

    public int infoSlot() {
        return infoSlot;
    }

    public Material infoMaterial() {
        return infoMaterial;
    }

    public int rows() {
        return rows;
    }

    public ScreenElement element(String id) {
        for (ScreenElement e : elements) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }
}
