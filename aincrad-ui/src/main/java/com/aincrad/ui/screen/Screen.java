package com.aincrad.ui.screen;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Descrição de uma tela (menu, perfil, confirmação...), desenhada por um {@link Renderer}. */
public final class Screen {

    /**
     * Bloco de texto do modo cursor, com a base em (x, y).
     *
     * @param onLight texto sobre painel branco: cores claras viram escuras e a fonte é a SAO
     * @param left    alinhado à esquerda (senão, centralizado)
     */
    public record TextBlock(String id, List<Component> lines, double x, double y, double scale,
                            boolean onLight, boolean left) {
    }

    private final String id;
    private Component title = Component.empty();
    private String background;
    private double bgX;
    private double bgY;
    private double bgSize;
    private String chestBackground;
    private final List<ScreenElement> elements = new ArrayList<>();
    private final List<Component> lines = new ArrayList<>();
    private final List<TextBlock> texts = new ArrayList<>();
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

    /**
     * Texto principal: no modo cursor vira um bloco sobre o painel (base em x, y); no modo baú
     * vira o item de informação.
     */
    public Screen text(List<Component> lines, double x, double y, double scale) {
        this.lines.clear();
        this.lines.addAll(lines);
        texts.removeIf(t -> t.id().equals("txt"));
        texts.add(new TextBlock("txt", List.copyOf(lines), x, y, scale, background != null, true));
        return this;
    }

    /** Linhas só do item de informação do modo baú (sem bloco de texto no modo cursor). */
    public Screen chestInfo(List<Component> lines) {
        this.lines.clear();
        this.lines.addAll(lines);
        return this;
    }

    /** Texto extra só do modo cursor (título de janela, nome no painel...). */
    public Screen label(String id, List<Component> lines, double x, double y, double scale, boolean onLight, boolean left) {
        texts.add(new TextBlock(id, List.copyOf(lines), x, y, scale, onLight, left));
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

    /** Linhas do texto principal (item de informação do modo baú). */
    public List<Component> lines() {
        return Collections.unmodifiableList(lines);
    }

    public List<TextBlock> texts() {
        return Collections.unmodifiableList(texts);
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
