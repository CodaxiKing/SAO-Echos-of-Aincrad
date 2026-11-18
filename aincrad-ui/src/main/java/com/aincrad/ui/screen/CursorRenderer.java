package com.aincrad.ui.screen;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.glyph.Glyph;
import com.aincrad.ui.glyph.GlyphRegistry;
import com.aincrad.ui.glyph.LineBuilder;
import com.aincrad.ui.glyph.UiFonts;
import com.aincrad.ui.sound.UiSound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Modo cursor: o menu flutua na frente do jogador, como no anime. Cada parte é uma Text Display
 * (ou Item Display, para o equipamento) visível só para o dono do menu. O jogador fica parado;
 * para onde ele olha é o cursor. Clique escolhe, agachar volta.
 */
public final class CursorRenderer implements Renderer {

    /** Um pixel de fonte numa Text Display com escala 1 mede 1/40 de bloco. */
    static final double BLOCKS_PER_PX = 0.025;
    /** Altura de uma linha de texto da Text Display, em pixels de fonte (para blocos de texto). */
    static final double ROW_LINE_PX = 10;
    /**
     * A Text Display desenha a linha para cima a partir da sua posição: a base da linha fica na
     * posição e o topo da linha 9 px acima. Um glifo de subida A e altura H ocupa, contando do
     * topo da linha, de (7 - A) até (7 - A + H).
     */
    static final double LINE_BOTTOM_PX = 9;
    private static final double BACKGROUND_DEPTH = 0.12;
    private static final double CURSOR_DEPTH = -0.04;
    private static final double CURSOR_SIZE = 0.05;
    private static final double LABEL_SCALE = 0.42;
    /** Largura do quadro de descrição de uma linha, em pixels de fonte. */
    private static final int ROW_LORE_WIDTH_PX = 170;

    /** Linha do submenu: posições dentro do fundo row_bg (pixels de fonte). */
    private static final int ROW_ICON_X = 5;
    private static final int ROW_ICON_PX = 18;
    private static final int ROW_TEXT_GAP = 5;
    private static final TextColor ROW_DETAIL = TextColor.color(0x8C8C8C);
    private static final TextColor ROW_TEXT = TextColor.color(0x474747);
    private static final TextColor ROW_TEXT_LOCKED = TextColor.color(0x9A9A9A);

    private final AincradUIPlugin plugin;
    private final Player player;
    private final World world;
    private final PlaneFrame frame;
    private final Map<String, Entity> displays = new HashMap<>();
    /** Largura (blocos) das linhas do submenu na tela atual. */
    private final Map<String, Double> rowWidths = new HashMap<>();
    private TextDisplay cursor;
    private TextDisplay label;
    private Screen screen;
    private ScreenElement hovered;

    public CursorRenderer(AincradUIPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        Location eye = player.getEyeLocation();
        this.world = eye.getWorld();
        this.frame = PlaneFrame.facing(eye.toVector(), eye.getYaw(),
                plugin.settings().menuDistance, plugin.settings().menuOffsetY);
    }

    // ------------------------------------------------------------------ desenhar

    @Override
    public void show(Screen next) {
        this.screen = next;
        this.hovered = null;
        Set<String> keep = new HashSet<>();
        measureRows(next);

        if (next.background() != null) {
            keep.add("bg");
            putGlyph("bg", next.background(), NamedTextColor.WHITE, next.bgX(), next.bgY(), next.bgSize(), BACKGROUND_DEPTH);
        }
        for (ScreenElement e : next.elements()) {
            if (e.chestOnly()) {
                continue;
            }
            String key = "el:" + e.id();
            boolean hasItem = e.item() != null && !e.item().getType().isAir();
            if (e.kind() == ScreenElement.Kind.HIT) {
                if (hasItem) {
                    keep.add(key);
                    putItem(key, e.item(), e.x(), e.y(), e.size());
                }
                continue;
            }
            keep.add(key);
            draw(e, false);
            if (e.kind() == ScreenElement.Kind.ROW && hasItem) {
                // Item 3D no lugar do ícone da linha (o fundo deixa o espaço vazio).
                double s = e.size();
                keep.add(key + ":item");
                putItem(key + ":item", e.item(), e.x() + s * (ROW_ICON_X + ROW_ICON_PX / 2.0) / 24.0, e.y(), s * 0.8);
            }
        }
        for (Screen.TextBlock t : next.texts()) {
            String key = "txt:" + t.id();
            keep.add(key);
            putText(key, t);
        }

        Iterator<Map.Entry<String, Entity>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entity> en = it.next();
            if (!keep.contains(en.getKey())) {
                en.getValue().remove();
                it.remove();
            }
        }
        hideLabel();
    }

    /** Largura das linhas do submenu: a do fundo row_bg na altura da linha. */
    private void measureRows(Screen s) {
        rowWidths.clear();
        Glyph bg = plugin.glyphs().get("row_bg");
        double ratio = bg != null && bg.present() ? (double) (bg.advance() - 1) / bg.height() : 3.8;
        for (ScreenElement e : s.elements()) {
            if (e.kind() == ScreenElement.Kind.ROW) {
                rowWidths.put(e.id(), e.size() * ratio);
            }
        }
    }

    /** Quanto o centro de um glifo fica acima da posição da Text Display, em pixels de fonte. */
    static double centerAbovePx(int ascent, int height) {
        return LINE_BOTTOM_PX - (7 - ascent + height / 2.0);
    }

    private void draw(ScreenElement e, boolean hover) {
        String key = "el:" + e.id();
        if (e.kind() == ScreenElement.Kind.ROW) {
            putRow(key, e, hover);
            return;
        }
        String glyph = glyphFor(e, hover || e.selected());
        putGlyph(key, glyph, e.locked() ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE,
                e.x(), e.y(), e.size(), e.depth());
    }

    private String glyphFor(ScreenElement e, boolean on) {
        GlyphRegistry glyphs = plugin.glyphs();
        if (on && e.glyphOn() != null && glyphs.has(e.glyphOn())) {
            return e.glyphOn();
        }
        return e.glyph();
    }

    private void putGlyph(String key, String glyphId, TextColor color, double x, double y, double size, double depth) {
        Glyph g = plugin.glyphs().get(glyphId);
        boolean ok = g != null && g.present();
        int heightPx = ok ? g.height() : 9;
        double scale = size / (heightPx * BLOCKS_PER_PX);
        double above = centerAbovePx(ok ? g.ascent() : 7, heightPx) * BLOCKS_PER_PX * scale;
        Location loc = frame.toWorld(x, y - above, depth).toLocation(world);
        TextDisplay d = textDisplay(key, loc);
        d.text(plugin.glyphs().component(glyphId, color));
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setTransformation(scaled(scale));
    }

    // ------------------------------------------------------------------ linhas do submenu

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private String rowIcon(ScreenElement e, boolean hover) {
        String base = e.glyph() == null ? "" : e.glyph();
        String name = base.substring(base.lastIndexOf('/') + 1);
        String on = "linha/" + name + "_on";
        if (hover && plugin.glyphs().has(on)) {
            return on;
        }
        return "linha/" + name;
    }

    /**
     * Linha do submenu numa Text Display só: fundo row_bg (laranja com o cursor), ícone e nome na
     * fonte SAO por cima, posicionados com a fonte de espaços.
     */
    Component rowText(ScreenElement e, boolean hover) {
        GlyphRegistry glyphs = plugin.glyphs();
        Glyph bg = glyphs.get(hover && !e.locked() ? "row_bg_on" : e.locked() ? "row_bg_locked" : "row_bg");
        Glyph icon = glyphs.get(rowIcon(e, hover && !e.locked()));
        String name = plain(e.label());
        TextColor color = hover && !e.locked() ? NamedTextColor.WHITE : e.locked() ? ROW_TEXT_LOCKED : ROW_TEXT;

        LineBuilder line = new LineBuilder();
        int width = bg != null && bg.present() ? bg.advance() : 92;
        if (bg != null && bg.present()) {
            line.add(bg, bg.component());
        }
        line.moveTo(ROW_ICON_X);
        if (e.item() != null) {
            line.shift(ROW_ICON_PX + 1); // o item 3D fica neste espaço
        } else if (icon != null && icon.present()) {
            line.add(icon, icon.component(e.locked() ? NamedTextColor.GRAY : NamedTextColor.WHITE));
        }
        line.shift(ROW_TEXT_GAP);
        line.add(Component.text(name, color).font(UiFonts.SAO), UiFonts.saoWidth(name));
        if (e.detail() != null) {
            String detail = plain(e.detail());
            line.shift(4);
            line.add(Component.text(detail, hover && !e.locked() ? NamedTextColor.WHITE : ROW_DETAIL).font(UiFonts.SAO),
                    UiFonts.saoWidth(detail));
        }
        if (e.locked() && glyphs.has("lock")) {
            Glyph lock = glyphs.get("lock");
            line.shift(4);
            line.add(lock, lock.component(TextColor.color(0x8A8A8A)));
        }
        // Termina na borda direita do fundo: a Text Display centraliza pela largura total.
        line.moveTo(width);
        return line.build();
    }

    /** Linha do submenu: x é a borda esquerda. */
    private void putRow(String key, ScreenElement e, boolean hover) {
        Glyph bg = plugin.glyphs().get("row_bg");
        boolean ok = bg != null && bg.present();
        int heightPx = ok ? bg.height() : 24;
        double scale = e.size() / (heightPx * BLOCKS_PER_PX);
        double w = rowWidths.getOrDefault(e.id(), e.width());
        double above = centerAbovePx(ok ? bg.ascent() : 15, heightPx) * BLOCKS_PER_PX * scale;
        Location loc = frame.toWorld(e.x() + w / 2.0, e.y() - above, e.depth()).toLocation(world);
        TextDisplay d = textDisplay(key, loc);
        d.text(rowText(e, hover));
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setTransformation(scaled(scale));
    }

    // ------------------------------------------------------------------ itens e textos

    private void putItem(String key, ItemStack item, double x, double y, double size) {
        Location loc = frame.toWorld(x, y, -0.02).toLocation(world);
        Entity existing = displays.get(key);
        ItemDisplay d;
        if (existing instanceof ItemDisplay id && id.isValid()) {
            d = id;
            d.teleport(loc);
        } else {
            if (existing != null) {
                existing.remove();
            }
            d = world.spawn(loc, ItemDisplay.class, t -> {
                t.setPersistent(false);
                t.setVisibleByDefault(false);
                t.setBillboard(Display.Billboard.VERTICAL);
                t.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                t.setBrightness(new Display.Brightness(15, 15));
                t.setTeleportDuration(1);
            });
            player.showEntity(plugin, d);
            displays.put(key, d);
        }
        d.setItemStack(item);
        float s = (float) (size * 0.9);
        d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, 0.02f), new AxisAngle4f()));
    }

    private void putText(String key, Screen.TextBlock t) {
        Component text = Component.join(JoinConfiguration.newlines(), t.lines());
        if (t.onLight()) {
            text = Contrast.forLight(text).font(UiFonts.SAO);
        }
        Location loc = frame.toWorld(t.x(), t.y(), -0.01).toLocation(world);
        TextDisplay d = textDisplay(key, loc);
        d.text(text);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setAlignment(t.left() ? TextDisplay.TextAlignment.LEFT : TextDisplay.TextAlignment.CENTER);
        d.setShadowed(!t.onLight());
        d.setTransformation(scaled(t.scale()));
    }

    private TextDisplay textDisplay(String key, Location loc) {
        Entity e = displays.get(key);
        if (e instanceof TextDisplay d && d.isValid()) {
            d.teleport(loc);
            return d;
        }
        if (e != null) {
            e.remove();
        }
        TextDisplay d = spawn(loc);
        displays.put(key, d);
        return d;
    }

    private TextDisplay spawn(Location loc) {
        TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.VERTICAL);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(false);
            t.setSeeThrough(false);
            t.setLineWidth(400);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTeleportDuration(1);
        });
        player.showEntity(plugin, d);
        return d;
    }

    private static Transformation scaled(double scale) {
        float s = (float) scale;
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    // ------------------------------------------------------------------ cursor

    /** Centro e largura da área clicável no plano (linhas guardam a borda esquerda em x). */
    private double centerX(ScreenElement e) {
        return e.kind() == ScreenElement.Kind.ROW ? e.x() + hitWidth(e) / 2.0 : e.x();
    }

    private double hitWidth(ScreenElement e) {
        return e.kind() == ScreenElement.Kind.ROW ? rowWidths.getOrDefault(e.id(), e.width()) : e.width();
    }

    @Override
    public void tick() {
        if (screen == null || !player.isOnline()) {
            return;
        }
        Location eye = player.getEyeLocation();
        double[] uv = frame.project(eye.toVector(), eye.getDirection());
        if (uv == null) {
            setHovered(null);
            hideCursor();
            return;
        }
        ScreenElement hit = null;
        for (ScreenElement e : screen.elements()) {
            if (e.interactive() && !e.chestOnly()
                    && PlaneFrame.inside(uv[0], uv[1], centerX(e), e.y(), hitWidth(e), e.size())) {
                hit = e;
                break;
            }
        }
        setHovered(hit);
        moveCursor(uv[0], uv[1]);
    }

    private void setHovered(ScreenElement next) {
        if (next == hovered) {
            return;
        }
        ScreenElement old = hovered;
        hovered = next;
        if (old != null && old.kind() != ScreenElement.Kind.HIT && displays.containsKey("el:" + old.id())) {
            draw(old, false);
        }
        if (next == null) {
            hideLabel();
            return;
        }
        if (next.kind() != ScreenElement.Kind.HIT) {
            draw(next, true);
        }
        if (next.kind() == ScreenElement.Kind.ROW) {
            if (next.lore().isEmpty()) {
                hideLabel();
            } else {
                showRowLore(next);
            }
        } else {
            showLabel(next);
        }
        plugin.sounds().play(player, UiSound.MOVE);
    }

    private void showLabel(ScreenElement e) {
        Component text = e.locked()
                ? plugin.messages().get("menu.em_breve", Placeholder.component("nome", e.label()))
                : e.label();
        Location loc = frame.toWorld(e.x(), e.y() - e.size() / 2.0 - 0.12, -0.03).toLocation(world);
        if (label == null || !label.isValid()) {
            label = spawn(loc);
            label.setShadowed(true);
            label.setBackgroundColor(Color.fromARGB(0x90, 0x20, 0x20, 0x20));
        } else {
            label.teleport(loc);
        }
        label.setLineWidth(200);
        label.setAlignment(TextDisplay.TextAlignment.CENTER);
        label.text(text);
        label.setTransformation(scaled(LABEL_SCALE));
    }

    /**
     * A descrição inteira da linha em que o cursor está, num quadro à direita dela. A linha só tem
     * espaço para um detalhe curto; preço, prazo e o resto ficam aqui.
     */
    private void showRowLore(ScreenElement e) {
        Component text = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), e.lore());
        double half = ROW_LORE_WIDTH_PX * BLOCKS_PER_PX * LABEL_SCALE / 2.0;
        double height = e.lore().size() * ROW_LINE_PX * BLOCKS_PER_PX * LABEL_SCALE;
        // A Text Display é ancorada embaixo, no centro: o quadro fica centrado na altura da linha.
        Location loc = frame.toWorld(e.x() + hitWidth(e) + half + 0.06, e.y() - height / 2.0, -0.03).toLocation(world);
        if (label == null || !label.isValid()) {
            label = spawn(loc);
            label.setShadowed(true);
            label.setBackgroundColor(Color.fromARGB(0x90, 0x20, 0x20, 0x20));
        } else {
            label.teleport(loc);
        }
        label.setLineWidth(ROW_LORE_WIDTH_PX);
        label.setAlignment(TextDisplay.TextAlignment.LEFT);
        label.text(text);
        label.setTransformation(scaled(LABEL_SCALE));
    }

    private void hideLabel() {
        if (label != null && label.isValid()) {
            label.remove();
        }
        label = null;
    }

    private void moveCursor(double u, double v) {
        Glyph g = plugin.glyphs().get("cursor");
        if (g == null || !g.present()) {
            return;
        }
        Location loc = frame.toWorld(u, v - CURSOR_SIZE / 2.0, CURSOR_DEPTH).toLocation(world);
        if (cursor == null || !cursor.isValid()) {
            cursor = spawn(loc);
            cursor.text(g.component());
            cursor.setTransformation(scaled(CURSOR_SIZE / (g.height() * BLOCKS_PER_PX)));
        } else {
            cursor.teleport(loc);
        }
    }

    private void hideCursor() {
        if (cursor != null && cursor.isValid()) {
            cursor.remove();
        }
        cursor = null;
    }

    @Override
    public ScreenElement hovered() {
        return hovered;
    }

    @Override
    public ScreenElement atSlot(int slot) {
        return null;
    }

    @Override
    public void destroy() {
        displays.values().forEach(d -> {
            if (d.isValid()) {
                d.remove();
            }
        });
        displays.clear();
        hideCursor();
        hideLabel();
        screen = null;
        hovered = null;
    }
}
