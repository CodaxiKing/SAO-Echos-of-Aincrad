package com.aincrad.ui.screen;

import com.aincrad.ui.glyph.GlyphRegistry;
import com.aincrad.ui.glyph.LineBuilder;
import com.aincrad.ui.glyph.SpaceFont;
import com.aincrad.ui.glyph.UiFonts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaoLayoutTest {

    private static final File RES = new File("src/main/resources");
    private static final File TEXTURES = new File(RES, "resourcepack/assets/aincrad/textures");

    private static GlyphRegistry glyphs() {
        return GlyphRegistry.load(YamlConfiguration.loadConfiguration(new File(RES, "glyphs.yml")), TEXTURES, null);
    }

    @Test
    void todoGlifoTemImagemNoPack() {
        GlyphRegistry g = glyphs();
        List<String> missing = g.all().stream().filter(x -> !x.present()).map(x -> x.texture()).toList();
        assertTrue(missing.isEmpty(), "glifos sem PNG em resourcepack/: " + missing);
        assertEquals(20, g.groups().get("hud_fill").size());
    }

    /** Lido direto do YAML: o MenuRegistry resolve materiais, o que exige um servidor rodando. */
    @Test
    void iconesDoMenuExistemNasDuasAlturas() {
        GlyphRegistry g = glyphs();
        YamlConfiguration menu = YamlConfiguration.loadConfiguration(new File(RES, "menu.yml"));
        for (String group : menu.getConfigurationSection("grupos").getKeys(false)) {
            String icon = menu.getString("grupos." + group + ".icone");
            assertTrue(g.has(icon), icon);
            assertTrue(g.has(icon + "_on"), icon + "_on");
            for (String entry : menu.getStringList("grupos." + group + ".entradas")) {
                String eicon = menu.getString("entradas." + entry + ".icone");
                assertNotNull(eicon, entry);
                String name = eicon.substring(eicon.lastIndexOf('/') + 1);
                assertTrue(g.has(eicon), eicon);
                assertTrue(g.has("linha/" + name), "linha/" + name);
                assertTrue(g.has("linha/" + name + "_on"), "linha/" + name + "_on");
            }
        }
        for (String button : List.of("buttons/yes", "buttons/no", "buttons/cancel", "buttons/details")) {
            assertTrue(g.has(button), button);
        }
    }

    @Test
    void fonteSaoMedeTextos() {
        UiFonts.load(new File(RES, "resourcepack/assets/aincrad/font/" + UiFonts.SAO_TTF), null);
        assertTrue(UiFonts.saoAvailable());
        int w = UiFonts.saoWidth("Equipamento");
        // A fonte SAO é estreita: no tamanho 11, "Equipamento" tem uns 43 px.
        assertTrue(w > 36 && w < 50, "largura " + w);
        assertTrue(UiFonts.saoWidth("Opções") > UiFonts.saoWidth("Op"));
        assertEquals(0, UiFonts.saoWidth(""));
    }

    @Test
    void linhasDaFonteAscii() {
        List<String> rows = UiFonts.asciiRows();
        assertEquals(16, rows.size());
        rows.forEach(r -> assertEquals(16, r.length()));
        assertEquals('@', rows.get(4).charAt(0));
        assertEquals('0', rows.get(3).charAt(0));
        assertEquals('\u0000', rows.get(7).charAt(15));
    }

    @Test
    void linhaPosicionaPorPixel() {
        LineBuilder line = new LineBuilder();
        line.add(Component.text("x"), 187);
        line.moveTo(57);
        assertEquals(57, line.x());
        line.add(Component.text("y"), 7).shift(-1);
        assertEquals(63, line.x());
        line.moveTo(187);
        assertEquals(187, line.x());
        assertEquals(0, SpaceFont.measure(""));
    }

    @Test
    void centroDoGlifoAcimaDaPosicao() {
        // Ícone 32 px com subida 28: ocupa de -21 a 11 na linha; centro 14 px acima da base (9).
        assertEquals(14, CursorRenderer.centerAbovePx(28, 32), 1e-9);
        // Fundo da linha do submenu (24 px, subida 15): centro em 4, 5 px acima da base.
        assertEquals(5, CursorRenderer.centerAbovePx(15, 24), 1e-9);
    }

    @Test
    void pontosDoPainel() {
        double ph = 1.2;
        double pw = ph * Screens.PANEL_RATIO;
        double[] tl = Screens.panelPoint(0, 0, 0, 0, ph);
        assertEquals(-pw / 2, tl[0], 1e-9);
        assertEquals(ph / 2, tl[1], 1e-9);
        double[] mid = Screens.panelPoint(256, 300, 1, 2, ph);
        assertEquals(1, mid[0], 1e-9);
        assertEquals(2, mid[1], 1e-9);
        assertNotNull(Screens.PANEL_NODES.get(org.bukkit.inventory.EquipmentSlot.HEAD));
    }

    @Test
    void coresEscurecemSobreBranco() {
        Component c = Component.text("a", NamedTextColor.WHITE)
                .append(Component.text("b", NamedTextColor.AQUA))
                .append(Component.text("c", TextColor.color(0x123456)));
        Component out = Contrast.forLight(c);
        assertEquals(Contrast.DEFAULT, out.color());
        assertEquals(TextColor.color(0x1B7FB5), out.children().get(0).color());
        assertEquals(TextColor.color(0x123456), out.children().get(1).color());
        assertEquals(Contrast.DEFAULT, Contrast.forLight(Component.text("sem cor")).color());
    }
}
