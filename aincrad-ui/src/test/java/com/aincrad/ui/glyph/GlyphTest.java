package com.aincrad.ui.glyph;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlyphTest {

    @TempDir
    File tmp;

    @Test
    void espacosSomamExato() {
        for (int px : new int[]{0, 1, -1, 7, -8, 200, -333, 1500, -2047}) {
            assertEquals(px, SpaceFont.measure(SpaceFont.shift(px)), "px=" + px);
        }
    }

    @Test
    void avancoDeBitmap() {
        assertEquals(33, GlyphRegistry.advanceFor(128, 128, 32));
        assertEquals(17, GlyphRegistry.advanceFor(128, 256, 32));
        assertEquals(5, GlyphRegistry.advanceFor(4, 16, 16));
    }

    @Test
    void larguraDeTexto() {
        assertEquals(12, TextWidth.of("Ab"));
        assertEquals(2 + 3 + 4, TextWidth.of("il "));
    }

    @Test
    void registroLeGruposEMarcaArquivosFaltando() throws IOException {
        File icons = new File(tmp, "ui/icons");
        assertTrue(icons.mkdirs());
        png(new File(icons, "a.png"), 128, 128);
        png(new File(icons, "a_on.png"), 128, 128);
        png(new File(tmp, "ui/w.png"), 512, 256);

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("grupos.icons.pasta", "ui/icons");
        cfg.set("grupos.icons.altura", 32);
        cfg.set("grupos.icons.subida", 40); // maior que a altura: deve ser limitada
        cfg.set("grupos.icons.com_on", true);
        cfg.set("grupos.icons.nomes", java.util.List.of("a", "b"));
        cfg.set("avulsos.window.textura", "ui/w.png");
        cfg.set("avulsos.window.altura", 64);

        GlyphRegistry reg = GlyphRegistry.load(cfg, tmp, null);
        assertTrue(reg.has("icons/a"));
        assertTrue(reg.has("icons/a_on"));
        assertFalse(reg.has("icons/b"));
        assertNotNull(reg.get("icons/b"));
        assertEquals(32, reg.get("icons/a").ascent());
        assertEquals(129, reg.get("window").advance());
        assertEquals(3, reg.presentCount());
        assertNotEquals(reg.get("icons/a").ch(), reg.get("icons/a_on").ch());
        assertEquals(java.util.List.of("icons/a", "icons/b"), reg.groups().get("icons"));
        assertArrayEquals(new int[]{512, 256}, PngSize.read(new File(tmp, "ui/w.png")));
    }

    private static void png(File f, int w, int h) throws IOException {
        assertTrue(ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", f));
    }
}
