package com.aincrad.economy;

import com.aincrad.economy.shop.ShopScreen;
import com.aincrad.ui.flat.FlatAtlas;
import com.aincrad.ui.flat.FlatCanvas;
import com.aincrad.ui.flat.FlatPreview;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Desenha a loja com o simulador do AincradUI: nada falta no pack, a linha fecha em zero e os botões funcionam. */
class ShopScreenTest {

    private static final Map<String, String> LABELS = Map.of("shop", "SHOP", "itens", "ITENS", "comprar", "COMPRAR",
            "vender", "VENDER", "quantidade", "QUANTIDADE", "total", "TOTAL", "vazio", "Nada para vender aqui.",
            "dica", "Clique esquerdo: escolher · Agachar: fechar", "porCompra", "<n> por compra", "voceTem", "Você tem <n>");

    private final List<String> trades = new ArrayList<>();

    private ShopScreen.Model model() {
        List<ShopScreen.Entry> buy = List.of(
                new ShopScreen.Entry("Pão", "minecraft:item/bread", 20, 4, 0),
                new ShopScreen.Entry("Carne assada", "minecraft:item/cooked_beef", 30, 2, 0),
                new ShopScreen.Entry("Flechas", "minecraft:item/arrow", 40, 16, 0),
                new ShopScreen.Entry("Anneal Blade", "minecraft:item/iron_sword", 2500, 1, 0),
                new ShopScreen.Entry("Lingote de ferro", "minecraft:item/iron_ingot", 60, 1, 0),
                new ShopScreen.Entry("Casaco de couro do aventureiro iniciante", "minecraft:item/leather_chestplate", 1250000, 1, 0),
                new ShopScreen.Entry("Osso", "minecraft:item/bone", 5, 1, 0));
        List<ShopScreen.Entry> sell = List.of(new ShopScreen.Entry("Osso", "minecraft:item/bone", 2, 1, 12));
        return new ShopScreen.Model() {
            @Override public String title() { return "Mercador — Andar 1"; }
            @Override public String label(String key) { return LABELS.get(key); }
            @Override public String money(long amount) { return EcoMath.format(amount) + " Col"; }
            @Override public long balance(Player viewer) { return 47650; }
            @Override public List<ShopScreen.Entry> entries(Player viewer, boolean selling) { return selling ? sell : buy; }
            @Override public List<String> icons() { return buy.stream().map(ShopScreen.Entry::icon).toList(); }
            @Override public void trade(Player viewer, boolean selling, int index, int quantity) {
                trades.add((selling ? "vende " : "compra ") + index + " x" + quantity);
            }
            @Override public void close(Player viewer) { trades.add("fechou"); }
        };
    }

    private static FlatCanvas draw(FlatAtlas atlas, ShopScreen screen, int cx, int cy) {
        FlatCanvas c = new FlatCanvas(atlas, false, cx, cy);
        screen.draw(c, null);
        assertEquals(0, c.missing(), "a preparação tem de cobrir tudo o que a loja desenha");
        return c;
    }

    private static void click(FlatAtlas atlas, ShopScreen screen, int x, int y) {
        FlatCanvas.Hit hit = draw(atlas, screen, x, y).hitAt(x, y);
        assertNotNull(hit, "sem botão em " + x + ", " + y);
        hit.action().run();
    }

    @Test
    void lojaDesenhaCompraEVende() throws IOException {
        File pack = new File("build/preview/pack");
        Path textures = pack.toPath().resolve("assets/aincrad/textures/flat");
        Files.createDirectories(textures);
        try (Stream<Path> files = Files.list(Path.of("../aincrad-ui/src/main/resources/resourcepack/assets/aincrad/textures/flat"))) {
            for (Path p : files.toList()) {
                Files.copy(p, textures.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        FlatAtlas atlas = new FlatAtlas();
        atlas.texturesRoot(new File(pack, "assets/aincrad/textures"));
        new ShopScreen(model()).draw(new FlatCanvas(atlas, true, 0, 0), null);
        atlas.write(pack);
        FlatPreview preview = new FlatPreview(pack, atlas);

        ShopScreen screen = new ShopScreen(model());
        Component buy = draw(atlas, screen, -100, 115).build();
        assertEquals(0, preview.advance(buy));
        ImageIO.write(preview.render(buy, 480, 270, 3, 0x40506A), "png", new File("build/preview/loja_comprar.png"));

        // Escolhe a 3ª linha, soma 2 na quantidade e compra.
        click(atlas, screen, -100, 40 + 52 + 2 * 15 + 5);
        click(atlas, screen, 140, 40 + 52 + 72 + 5);
        click(atlas, screen, 140, 40 + 52 + 72 + 5);
        click(atlas, screen, 120, 40 + 52 + 86 + 5);
        assertEquals(List.of("compra 2 x3"), trades);

        // Página 2 (o 7º item) e depois a aba Vender, que tem um item só.
        click(atlas, screen, -150 + 112 + 45, 40 + 40);
        Component page2 = draw(atlas, screen, 0, 0).build();
        ImageIO.write(preview.render(page2, 480, 270, 3, 0x40506A), "png", new File("build/preview/loja_pagina2.png"));
        click(atlas, screen, -150 + 60, 40 + 40);
        Component sell = draw(atlas, screen, 120, 183).build();
        assertEquals(0, preview.advance(sell));
        ImageIO.write(preview.render(sell, 480, 270, 3, 0x40506A), "png", new File("build/preview/loja_vender.png"));
        click(atlas, screen, 120, 40 + 52 + 86 + 5);
        click(atlas, screen, 140, 45);
        assertEquals(List.of("compra 2 x3", "vende 0 x1", "fechou"), trades);
    }
}
