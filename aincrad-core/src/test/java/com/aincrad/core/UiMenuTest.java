package com.aincrad.core;

import com.aincrad.core.menu.Menu;
import com.aincrad.core.ui.UiService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O que os plugins pedem à interface (UiService) e as abas e a ordem dos menus, sem servidor. */
class UiMenuTest {

    /** Uma interface de mentira, que só anota o que recebeu. */
    private static final class Fake implements UiService.Backend {
        final List<String> entries = new ArrayList<>();

        @Override
        public void menuEntry(UiService.Entry entry) {
            entries.add(entry.id() + "@" + entry.group());
        }

        @Override
        public boolean toast(Player player, Component title, Component detail) {
            return true;
        }

        @Override
        public boolean prompt(Player player, Component question, Consumer<Player> yes, Consumer<Player> no) {
            return true;
        }

        @Override
        public boolean banner(Player player, String id) {
            return "duel".equals(id);
        }

        @Override
        public Component chestTitle(Player player, Component title, int rows) {
            return Component.text("fundo").append(title);
        }
    }

    @Test
    void entradasPedidasAntesDaInterfaceEntramQuandoElaLiga() {
        UiService ui = new UiService();
        ui.menuEntry("skills", "under_icons/skills", Component.text("Skills"), "perfil", p -> { });
        ui.menuEntry("raid", "under_icons/dungeon", Component.text("Raids"), "party", p -> { });
        // A mesma entrada pedida de novo (um reload) troca a antiga, não duplica.
        ui.menuEntry("skills", "under_icons/skills", Component.text("Skills"), "perfil", p -> { });
        assertFalse(ui.attached());

        Fake fake = new Fake();
        ui.attach(fake);
        assertTrue(ui.attached());
        assertEquals(List.of("skills@perfil", "raid@party"), fake.entries);
        // Com a interface ligada, a entrada nova vai na hora.
        ui.menuEntry("jornal", "under_icons/help", Component.text("Jornal"), "mensagens", p -> { });
        assertEquals("jornal@mensagens", fake.entries.get(2));
    }

    @Test
    void semInterfaceCadaPedidoTemUmaSaidaSimples() {
        UiService ui = new UiService();
        Component title = Component.text("Troca");
        assertSame(title, ui.chestTitle(null, title, 6));
        assertFalse(ui.prompt(null, title, p -> { }, p -> { }));
        assertFalse(ui.banner(null, "duel"));
        assertNull(ui.energy((Player) null));
        assertTrue(ui.skillBars((Player) null).isEmpty());

        ui.attach(new Fake());
        assertEquals(Component.text("fundo").append(title), ui.chestTitle(null, title, 6));
        assertTrue(ui.prompt(null, title, p -> { }, p -> { }));
        assertTrue(ui.banner(null, "duel"));
        ui.detach();
        assertSame(title, ui.chestTitle(null, title, 6));
    }

    @Test
    void provedorComErroNaoDerrubaAHud() {
        UiService ui = new UiService();
        ui.skillBars(p -> {
            throw new IllegalStateException("plugin com erro");
        });
        ui.energy(p -> {
            throw new IllegalStateException("plugin com erro");
        });
        assertTrue(ui.skillBars((Player) null).isEmpty());
        assertNull(ui.energy((Player) null));
        ui.skillBars(p -> List.of(new UiService.Bar("Slant", 0.5)));
        assertEquals(0.5, ui.skillBars((Player) null).get(0).remaining());
    }

    @Test
    void abaEOrdemAcompanhamOBotao() {
        Menu.Button b = Menu.Button.of(null, Component.text("Pão"), null);
        assertNull(b.category());
        assertTrue(b.sorts().isEmpty());
        Menu.Button full = b.category("Comprar").sort("Preço", 20).sort("Level", 3).lore(List.of(Component.text("20 Col"))).closing();
        assertEquals("Comprar", full.category());
        assertEquals(List.of("Preço", "Level"), List.copyOf(full.sorts().keySet()));
        assertEquals(20, full.sorts().get("Preço"));
        assertTrue(full.closeOnClick());
        assertEquals(1, full.lore().size());
        // O botão original não muda.
        assertTrue(b.sorts().isEmpty());
    }
}
