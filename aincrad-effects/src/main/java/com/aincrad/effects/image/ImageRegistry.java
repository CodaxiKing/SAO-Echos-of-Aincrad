package com.aincrad.effects.image;

import com.aincrad.effects.config.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

/** Imagens de images.yml, cada uma um caractere da fonte aincrad:effects. */
public final class ImageRegistry {

    public static final Key FONT = Key.key("aincrad", "effects");
    public static final char FIRST_CHAR = '';

    /**
     * @param file     caminho relativo a assets/aincrad/textures/ (com .png)
     * @param fallback texto MiniMessage para quem está sem o pack
     * @param sizeHint tamanho recomendado da arte final (só informativo)
     */
    public record Image(String id, String file, int height, int ascent, char ch, int advance,
                        boolean present, String fallback, String sizeHint) {

        public Component glyph() {
            return Component.text(String.valueOf(ch)).font(FONT).color(NamedTextColor.WHITE);
        }

        public Component fallbackComponent() {
            return Messages.parse(fallback == null ? "" : fallback);
        }
    }

    private final Map<String, Image> images = new LinkedHashMap<>();

    public static ImageRegistry load(YamlConfiguration cfg, File texturesRoot, Logger log) {
        ImageRegistry reg = new ImageRegistry();
        List<String> missing = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection("imagens");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                String file = s.getString("arquivo", "effects/" + id + ".png").replace('\\', '/');
                int height = Math.max(1, s.getInt("altura", 32));
                int ascent = Math.min(height, s.getInt("subida", height / 2));
                File f = new File(texturesRoot, file);
                int[] size = f.isFile() ? PngSize.read(f) : null;
                if (size == null) {
                    missing.add(file);
                }
                int advance = size == null ? height + 1 : advanceFor(size[0], size[1], height);
                char ch = (char) (FIRST_CHAR + reg.images.size());
                reg.images.put(id, new Image(id, file, height, ascent, ch, advance, size != null,
                        s.getString("reserva", ""), s.getString("tamanho_px", "")));
            }
        }
        if (!missing.isEmpty() && log != null) {
            log.warning("Imagens de images.yml não encontradas (" + missing.size() + "): " + String.join(", ", missing));
        }
        return reg;
    }

    public static int advanceFor(int texWidth, int texHeight, int height) {
        return texHeight <= 0 ? height + 1 : (int) Math.round(texWidth * (double) height / texHeight) + 1;
    }

    public Image get(String id) {
        return id == null ? null : images.get(id);
    }

    public Collection<Image> all() {
        return Collections.unmodifiableCollection(images.values());
    }

    public int presentCount() {
        return (int) images.values().stream().filter(Image::present).count();
    }

    /** Glifo para quem tem o pack, texto reserva para quem não tem (ou se o PNG faltar). */
    public Component render(String id, boolean hasPack) {
        Image img = get(id);
        if (img == null) {
            return Component.text("[" + id + "]", NamedTextColor.RED);
        }
        return hasPack && img.present() ? img.glyph() : img.fallbackComponent();
    }
}
