package com.aincrad.ui.core;

import com.aincrad.core.AincradCore;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.menu.MenuRenderer;
import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.api.AincradUIAPI;
import com.aincrad.ui.api.UiList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Faz os menus do AincradCore marcados como flutuantes ({@code Menu.floating()}) abrirem no
 * estilo SAO: uma lista do AincradUI, com cursor, no lugar do baú. Quem está sem o resource
 * pack continua vendo o baú. Só é carregada quando o AincradCore está no servidor.
 */
public final class CoreMenuBridge implements MenuRenderer {

    private final AincradUIPlugin plugin;
    private final AincradUIAPI ui;

    private CoreMenuBridge(AincradUIPlugin plugin, AincradUIAPI ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    public static void register(AincradUIPlugin plugin, AincradUIAPI ui) {
        AincradCore.get().menus().register(new CoreMenuBridge(plugin, ui));
    }

    public static void unregister() {
        AincradCore.get().menus().unregister("cursor");
    }

    @Override
    public String id() {
        return "cursor";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(Player player) {
        return false; // só os menus marcados como flutuantes
    }

    @Override
    public boolean supports(Player player, Menu menu) {
        return menu.isFloating() && plugin.packStatus().hasPack(player);
    }

    @Override
    public void open(Player player, Menu menu) {
        UiList list = UiList.of(menu.title());
        for (Menu.Button b : menu.items()) {
            list.row(UiList.Row.item(b.icon(), b.name(), detail(b.lore()), pl -> click(pl, b)));
        }
        for (Menu.Button b : menu.buttons()) {
            list.button(new UiList.Button("buttons/list", b.name(), pl -> click(pl, b)));
        }
        // Um menu aberto a partir de outro troca a lista em vez de empilhar.
        if (ui.isMenuOpen(player)) {
            ui.replaceList(player, list);
        } else {
            ui.openList(player, list);
        }
    }

    private void click(Player p, Menu.Button b) {
        if (b.closeOnClick()) {
            ui.closeMenu(p);
        }
        if (b.action() != null) {
            b.action().accept(p);
        }
    }

    /** A lista mostra uma linha de detalhe: as duas primeiras da descrição, juntas. */
    private static Component detail(List<Component> lore) {
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        return Component.join(JoinConfiguration.separator(Component.text(" · ")), lore.subList(0, Math.min(2, lore.size())));
    }
}
