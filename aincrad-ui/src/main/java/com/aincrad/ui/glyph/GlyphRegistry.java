package com.aincrad.ui.glyph;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Lê glyphs.yml, confere os PNGs e distribui os caracteres da fonte aincrad:ui. */
public final class GlyphRegistry {

    public static final Key FONT = Key.key("aincrad", "ui");
    public static final char FIRST_CHAR = '';

    private final Map<String, Glyph> glyphs = new LinkedHashMap<>();
    private final Map<String, List<String>> groups = new LinkedHashMap<>();

    public static GlyphRegistry load(YamlConfiguration cfg, File texturesRoot, Logger log) {
        GlyphRegistry reg = new GlyphRegistry();
        List<String> missing = new ArrayList<>();

        ConfigurationSection groupSec = cfg.getConfigurationSection("grupos");
        if (groupSec != null) {
            for (String group : groupSec.getKeys(false)) {
                ConfigurationSection g = groupSec.getConfigurationSection(group);
                if (g == null) {
                    continue;
                }
                String folder = trimSlashes(g.getString("pasta", "ui/" + group));
                int height = g.getInt("altura", 32);
                int ascent = g.getInt("subida", height - 4);
                boolean withOn = g.getBoolean("com_on", false);
                List<String> base = new ArrayList<>();
                for (String name : g.getStringList("nomes")) {
                    String id = group + "/" + name;
                    base.add(id);
                    reg.add(id, folder + "/" + name + ".png", height, ascent, texturesRoot, missing);
                    if (withOn) {
                        reg.add(id + "_on", folder + "/" + name + "_on.png", height, ascent, texturesRoot, missing);
                    }
                }
                reg.groups.put(group, Collections.unmodifiableList(base));
            }
        }

        ConfigurationSection single = cfg.getConfigurationSection("avulsos");
        if (single != null) {
            for (String id : single.getKeys(false)) {
                ConfigurationSection g = single.getConfigurationSection(id);
                if (g == null) {
                    continue;
                }
                int height = g.getInt("altura", 16);
                reg.add(id, trimSlashes(g.getString("textura", "ui/" + id + ".png")), height,
                        g.getInt("subida", height - 2), texturesRoot, missing);
            }
        }

        if (!missing.isEmpty() && log != null) {
            log.warning("Imagens não encontradas em " + texturesRoot.getPath() + " (" + missing.size()
                    + "): " + String.join(", ", missing.subList(0, Math.min(10, missing.size())))
                    + (missing.size() > 10 ? ", ..." : ""));
        }
        return reg;
    }

    private void add(String id, String texture, int height, int ascent, File root, List<String> missing) {
        if (glyphs.containsKey(id)) {
            return;
        }
        int safeHeight = Math.max(1, height);
        int safeAscent = Math.min(ascent, safeHeight);
        char ch = (char) (FIRST_CHAR + glyphs.size());
        File file = new File(root, texture);
        int[] size = file.isFile() ? PngSize.read(file) : null;
        boolean present = size != null;
        if (!present) {
            missing.add(texture);
        }
        int advance = present ? advanceFor(size[0], size[1], safeHeight) : safeHeight + 1;
        glyphs.put(id, new Glyph(id, texture, safeHeight, safeAscent, ch, advance, present));
    }

    /** Largura de um glifo bitmap: largura da imagem escalada para a altura, mais 1 px de espaço. */
    public static int advanceFor(int texWidth, int texHeight, int height) {
        if (texHeight <= 0) {
            return height + 1;
        }
        return (int) Math.round(texWidth * (double) height / texHeight) + 1;
    }

    private static String trimSlashes(String s) {
        String out = s.replace('\\', '/');
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    public Glyph get(String id) {
        return id == null ? null : glyphs.get(id);
    }

    public boolean has(String id) {
        Glyph g = get(id);
        return g != null && g.present();
    }

    public Component component(String id) {
        return component(id, NamedTextColor.WHITE);
    }

    public Component component(String id, TextColor color) {
        Glyph g = get(id);
        if (g == null) {
            return Component.text("[" + id + "]", color);
        }
        return g.component(color);
    }

    public Collection<Glyph> all() {
        return Collections.unmodifiableCollection(glyphs.values());
    }

    public int presentCount() {
        return (int) glyphs.values().stream().filter(Glyph::present).count();
    }

    /** Ids base (sem _on) de cada grupo de glyphs.yml, na ordem da config. */
    public Map<String, List<String>> groups() {
        return Collections.unmodifiableMap(groups);
    }
}
