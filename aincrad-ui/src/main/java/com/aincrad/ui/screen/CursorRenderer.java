package com.aincrad.ui.screen;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.glyph.Glyph;
import com.aincrad.ui.glyph.GlyphRegistry;
import com.aincrad.ui.sound.UiSound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Modo cursor: cada ícone é uma Text Display com um glifo do pack, visível só para o dono do menu.
 * O jogador fica parado; para onde ele olha é o cursor. Clique escolhe, agachar volta.
 */
public final class CursorRenderer implements Renderer {

    /** Um pixel de fonte numa Text Display com escala 1 mede 1/40 de bloco. */
    private static final double BLOCKS_PER_PX = 0.025;
    private static final double BACKGROUND_DEPTH = 0.12;
    private static final double CURSOR_DEPTH = -0.04;
    private static final double CURSOR_SIZE = 0.06;
    private static final double LABEL_SCALE = 0.45;

    private final AincradUIPlugin plugin;
    private final Player player;
    private final World world;
    private final PlaneFrame frame;
    private final Map<String, TextDisplay> displays = new HashMap<>();
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

    @Override
    public void show(Screen next) {
        this.screen = next;
        this.hovered = null;
        Set<String> keep = new HashSet<>();

        if (next.background() != null) {
            keep.add("bg");
            putGlyph("bg", next.background(), false, next.bgX(), next.bgY(), next.bgSize(), BACKGROUND_DEPTH);
        }
        for (ScreenElement e : next.elements()) {
            if (e.chestOnly()) {
                continue;
            }
            String key = "el:" + e.id();
            keep.add(key);
            putGlyph(key, glyphFor(e, e.selected()), e.locked(), e.x(), e.y(), e.size(), 0);
        }
        if (!next.lines().isEmpty()) {
            keep.add("txt");
            putText(next);
        }

        Iterator<Map.Entry<String, TextDisplay>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TextDisplay> en = it.next();
            if (!keep.contains(en.getKey())) {
                en.getValue().remove();
                it.remove();
            }
        }
        hideLabel();
    }

    private String glyphFor(ScreenElement e, boolean on) {
        GlyphRegistry glyphs = plugin.glyphs();
        if (on && e.glyphOn() != null && glyphs.has(e.glyphOn())) {
            return e.glyphOn();
        }
        return e.glyph();
    }

    private void putGlyph(String key, String glyphId, boolean locked, double x, double y, double size, double depth) {
        Glyph g = plugin.glyphs().get(glyphId);
        int heightPx = g != null && g.present() ? g.height() : 9;
        Component text = plugin.glyphs().component(glyphId, locked ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE);
        double scale = size / (heightPx * BLOCKS_PER_PX);
        // A Text Display desenha o texto para cima a partir da sua posição: desce meia altura.
        Location loc = frame.toWorld(x, y - size / 2.0, depth).toLocation(world);
        TextDisplay d = display(key, loc);
        d.text(text);
        d.setTransformation(scaled(scale));
    }

    private void putText(Screen s) {
        Component text = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), s.lines());
        Location loc = frame.toWorld(s.textX(), s.textY(), -0.01).toLocation(world);
        TextDisplay d = display("txt", loc);
        d.text(text);
        d.setAlignment(TextDisplay.TextAlignment.LEFT);
        d.setShadowed(true);
        d.setTransformation(scaled(s.textScale()));
    }

    private TextDisplay display(String key, Location loc) {
        TextDisplay d = displays.get(key);
        if (d != null && d.isValid()) {
            d.teleport(loc);
            return d;
        }
        d = spawn(loc);
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
            if (e.interactive() && !e.chestOnly() && PlaneFrame.inside(uv[0], uv[1], e.x(), e.y(), e.size())) {
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
        if (old != null && displays.containsKey("el:" + old.id())) {
            putGlyph("el:" + old.id(), glyphFor(old, old.selected()), old.locked(), old.x(), old.y(), old.size(), 0);
        }
        if (next == null) {
            hideLabel();
            return;
        }
        putGlyph("el:" + next.id(), glyphFor(next, true), next.locked(), next.x(), next.y(), next.size(), 0);
        showLabel(next);
        plugin.sounds().play(player, UiSound.MOVE);
    }

    private void showLabel(ScreenElement e) {
        Component text = e.locked()
                ? plugin.messages().get("menu.em_breve",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("nome", e.label()))
                : e.label();
        Location loc = frame.toWorld(e.x(), e.y() - e.size() / 2.0 - 0.14, -0.02).toLocation(world);
        if (label == null || !label.isValid()) {
            label = spawn(loc);
            label.setShadowed(true);
        } else {
            label.teleport(loc);
        }
        label.text(text);
        label.setTransformation(scaled(LABEL_SCALE));
    }

    private void hideLabel() {
        if (label != null && label.isValid()) {
            label.text(Component.empty());
        }
    }

    private void moveCursor(double u, double v) {
        Glyph g = plugin.glyphs().get("cursor");
        if (g == null || !g.present()) {
            return;
        }
        Vector pos = frame.toWorld(u, v - CURSOR_SIZE / 2.0, CURSOR_DEPTH);
        Location loc = pos.toLocation(world);
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
        if (label != null && label.isValid()) {
            label.remove();
        }
        label = null;
        screen = null;
        hovered = null;
    }
}
