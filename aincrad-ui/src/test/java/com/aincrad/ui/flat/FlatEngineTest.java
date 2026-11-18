package com.aincrad.ui.flat;

import com.aincrad.ui.api.FlatDraw;
import com.aincrad.ui.api.FlatDraw.Font;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gera um pack de verdade numa pasta temporária, desenha a HUD com o simulador e confere as
 * posições no pixel. Os PNGs ficam em build/preview/ para olhar.
 */
class FlatEngineTest {

    private static File pack;
    private static FlatAtlas atlas;

    @BeforeAll
    static void buildPack() throws IOException {
        pack = new File("build/preview/pack");
        Path textures = pack.toPath().resolve("assets/aincrad/textures/flat");
        Files.createDirectories(textures);
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/resourcepack/assets/aincrad/textures/flat"))) {
            for (Path p : files.toList()) {
                Files.copy(p, textures.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        // As artes que a HUD usa: a moldura, as fatias, os ícones de skill e os do menu.
        for (String dir : new String[]{"hud", "skills", "under_icons", "banner"}) {
            Path to = pack.toPath().resolve("assets/aincrad/textures/ui/" + dir);
            Files.createDirectories(to);
            try (Stream<Path> files = Files.list(Path.of("src/main/resources/resourcepack/assets/aincrad/textures/ui/" + dir))) {
                for (Path p : files.toList()) {
                    Files.copy(p, to.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        atlas = new FlatAtlas();
        atlas.texturesRoot(new File(pack, "assets/aincrad/textures"));
        prepare(d -> FlatHud.paint(d, FlatHud.sample(3), -232));
        prepare(FlatEngineTest::sampleScreen);
        for (boolean art : new boolean[]{false, true}) {
            prepare(d -> FlatHud.paint(d, FlatHud.sample(3), -232, art));
            prepare(d -> FlatOverlay.prepare(d, 232, art));
        }
        atlas.write(pack);
    }

    private static void prepare(Consumer<FlatDraw> painter) {
        painter.accept(new FlatCanvas(atlas, true, 0, 0));
    }

    private static void sampleScreen(FlatDraw d) {
        d.rect(-100, 40, 200, 30, 0xFFFFFF, 100);
        d.rect(-250, 0, 500, 262, 0x000000, 45);
        d.text(-96, 44, Font.MEDIA, "Loja de teste — ação", 0x202020);
        d.icon(-96, 58, 12, "minecraft:item/bread");
        d.icon(10, 100, 8, "aincrad:flat/cursor");
    }

    private static Component paint(Consumer<FlatDraw> painter) {
        FlatCanvas c = new FlatCanvas(atlas, false, 0, 0);
        painter.accept(c);
        assertEquals(0, c.missing(), "tudo o que foi preparado tem de desenhar");
        return c.build();
    }

    private static int pixel(BufferedImage img, int x, int y) {
        return img.getRGB(x, y) & 0xFFFFFF;
    }

    @Test
    void larguraDaLinhaEhZero() throws IOException {
        FlatPreview preview = new FlatPreview(pack, atlas);
        assertEquals(0, preview.advance(paint(d -> FlatHud.paint(d, FlatHud.sample(3), -232))));
        assertEquals(0, preview.advance(paint(FlatEngineTest::sampleScreen)));
    }

    @Test
    void retanguloCaiNoPixelCerto() throws IOException {
        FlatPreview preview = new FlatPreview(pack, atlas);
        // 200×30 branco opaco com o canto em (-100, 40) numa tela de 480: de x=140 a 339, y=40 a 69.
        Component line = paint(d -> d.rect(-100, 40, 200, 30, 0xFF0000, 100));
        BufferedImage img = preview.render(line, 480, 270, 1, 0x000000);
        assertEquals(0xFF0000, pixel(img, 140, 40));
        assertEquals(0xFF0000, pixel(img, 339, 69));
        assertEquals(0xFF0000, pixel(img, 240, 55));
        assertNotEquals(0xFF0000, pixel(img, 139, 40));
        assertNotEquals(0xFF0000, pixel(img, 340, 69));
        assertNotEquals(0xFF0000, pixel(img, 140, 39));
        assertNotEquals(0xFF0000, pixel(img, 140, 70));
    }

    @Test
    void textoMedeOQueDesenha() throws IOException {
        FlatPreview preview = new FlatPreview(pack, atlas);
        FlatFont f = FlatFont.of(Font.MEDIA);
        String t = "Loja de teste — ação";
        // A linha de texto sozinha avança exatamente a largura que o plugin calculou.
        Component only = Component.text(t).font(atlas.text(Font.MEDIA, 44, false));
        assertEquals(f.width(t), preview.advance(only));
        assertEquals("?", f.clean("中"));
        assertTrue(f.width(f.fit("Um nome bem comprido de item", 60)) <= 60);
    }

    @Test
    void pecaNaoPreparadaNaoDesenhaEMarcaOAtlas() {
        FlatCanvas c = new FlatCanvas(atlas, false, 0, 0);
        c.rect(0, 200, 10, 7, 0xFFFFFF, 33);
        assertEquals(1, c.missing());
        assertTrue(atlas.dirty());
        assertNull(atlas.icon("minecraft:item/nao_existe", 12, 50, true));
    }

    @Test
    void botaoSabeSeOCursorEstaEmCima() {
        FlatCanvas c = new FlatCanvas(atlas, false, 15, 25);
        assertTrue(c.button(10, 20, 20, 10, () -> { }));
        assertTrue(!c.button(40, 20, 20, 10, () -> { }));
        assertEquals(10, c.hitAt(15, 25).x());
        assertNull(c.hitAt(35, 25));
        assertEquals(-20f, FlatManager.angleDelta(10f, -10f));
        assertEquals(20f, FlatManager.angleDelta(350f, 10f));
        assertEquals(-20f, FlatManager.angleDelta(-170f, 170f));
    }

    @Test
    void spritesDaHudDoMinecraft() {
        List<String> names = com.aincrad.ui.pack.PackBuilderAccess.vanillaHudSprites();
        assertTrue(names.contains("heart/absorbing_hardcore_half_blinking.png"));
        assertTrue(names.contains("food_empty_hunger.png"));
        assertTrue(names.contains("experience_bar_progress.png"));
        assertEquals(4 + 40 + 3 + 6 + 3 + 2, names.size());
    }

    /** O alvo, a bússola, o relógio, os avisos e as recargas: tudo o que desenham foi preparado, com e sem as artes. */
    @Test
    void camadaExtraDaHud() throws IOException {
        FlatPreview preview = new FlatPreview(pack, atlas);
        FlatOverlay.Data chefe = new FlatOverlay.Data(new FlatOverlay.Target("Illfang the Kobold Lord", 0.62, 0xB01325, 4),
                List.of(new FlatOverlay.Toast("Item recebido", "Anneal Blade", "items"), new FlatOverlay.Toast("Level", "12", "skills")),
                List.of(new FlatOverlay.Cooldown("Slant", 0, 0), new FlatOverlay.Cooldown("Vertical", 0.4, 1), new FlatOverlay.Cooldown("Horizontal", 1, 2)),
                "21:47", "primavera, dia 12", 170f,
                List.of(new FlatOverlay.Marker(-35, 0x5BE37D), new FlatOverlay.Marker(20, 0x5BE37D), new FlatOverlay.Marker(120, 0x5BE37D)));
        FlatHud.Data vida = new FlatHud.Data("Kirito", 9, 20, 0.45, 0xF0C83C, 60, 100, "120 / 283", 12,
                List.of(new FlatHud.Member("Asuna", 0.9, 0x7BD23A), new FlatHud.Member("Klein", 0.2, 0xE8413C)));
        for (boolean art : new boolean[]{false, true}) {
            // Em qualquer posição da HUD (o jogador a desloca nas Opções), com qualquer câmera e qualquer vida.
            for (int dx : new int[]{0, 40, -24}) {
                for (float yaw : new float[]{-180f, -90f, 0f, 45f, 179f}) {
                    for (double life : new double[]{0, 0.03, 0.5, 1}) {
                        FlatOverlay.Data v = new FlatOverlay.Data(new FlatOverlay.Target("Alvo", life, 0xE53B4B, 8), chefe.toasts(),
                                List.of(new FlatOverlay.Cooldown("A", life, 5), new FlatOverlay.Cooldown("B", 1 - life, 6)), chefe.clock(), chefe.date(), yaw, chefe.markers());
                        FlatHud.Data h = new FlatHud.Data("Kirito", (int) Math.round(20 * life), 20, life, 0x7BD23A, 17, 20, "MAX", 3, vida.party());
                        Component line = paint(d -> {
                            FlatHud.paint(d, h, -232 + dx, art);
                            FlatOverlay.paint(d, v, 232 - dx, art);
                        });
                        assertEquals(0, preview.advance(line));
                    }
                }
            }
            // Um mob comum (uma barra), sem bússola nem relógio, e um aviso com ícone desconhecido.
            paint(d -> FlatOverlay.paint(d, new FlatOverlay.Data(new FlatOverlay.Target("Frenzy Boar", 1, 0xF7C6D0, 1),
                    List.of(new FlatOverlay.Toast("Aviso", "de outro plugin", "nao_existe")), List.of(), null, null, null, List.of()), 232, art));
            Component tudo = paint(d -> {
                FlatHud.paint(d, vida, -232, art);
                FlatOverlay.paint(d, chefe, 232, art);
            });
            ImageIO.write(preview.render(tudo, 480, 270, 3, 0x40506A), "png", new File("build/preview/" + (art ? "hud_artes.png" : "hud_completa.png")));
        }
    }

    /** As faixas do anime (WARNING, Immortal Object, duelo...) desenham sem emenda e sem peça faltando. */
    @Test
    void faixasNoMeioDaTela() throws IOException {
        FlatPreview preview = new FlatPreview(pack, atlas);
        for (String id : FlatOverlay.BANNERS) {
            Component line = paint(d -> FlatOverlay.banner(d, id));
            assertEquals(0, preview.advance(line));
            ImageIO.write(preview.render(line, 480, 270, 3, 0x40506A), "png", new File("build/preview/faixa_" + id + ".png"));
        }
        // Faixa desconhecida não desenha nada (e não marca peça faltando).
        paint(d -> FlatOverlay.banner(d, "nao_existe"));
    }

    @Test
    void gravaAsPrevias() throws IOException {
        FlatPreview preview = new FlatPreview(pack, atlas);
        FlatHud.Data data = new FlatHud.Data("Kirito", 14, 20, 0.7, 0x7BD23A, 17, 20, "120 / 283", 3,
                List.of(new FlatHud.Member("Asuna", 0.9, 0x7BD23A), new FlatHud.Member("Klein", 0.2, 0xE8413C)));
        Component hud = paint(d -> FlatHud.paint(d, data, -232));
        ImageIO.write(preview.render(hud, 480, 270, 3, 0x40506A), "png", new File("build/preview/hud.png"));
        ImageIO.write(preview.render(paint(FlatEngineTest::sampleScreen), 480, 270, 3, 0x40506A), "png", new File("build/preview/tela.png"));
    }
}
