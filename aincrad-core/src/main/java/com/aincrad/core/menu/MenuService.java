package com.aincrad.core.menu;

import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * "Abrir menu X" para os plugins: escolhe o renderizador (maior prioridade que aceita o jogador)
 * e oferece atalhos como a confirmação sim/não.
 */
public final class MenuService {

    private final List<MenuRenderer> renderers = new CopyOnWriteArrayList<>();
    private final Messages msg;

    public MenuService(MenuRenderer fallback, Messages msg) {
        renderers.add(fallback);
        this.msg = msg;
    }

    public void register(MenuRenderer r) {
        renderers.removeIf(x -> x.id().equals(r.id()));
        renderers.add(r);
    }

    public void unregister(String id) {
        renderers.removeIf(x -> x.id().equals(id) && x.priority() > 0);
    }

    /** O renderizador que vai desenhar para o jogador. */
    public MenuRenderer rendererFor(Player p) {
        List<MenuRenderer> sorted = new ArrayList<>(renderers);
        sorted.sort(Comparator.comparingInt(MenuRenderer::priority).reversed());
        for (MenuRenderer r : sorted) {
            if (r.supports(p)) {
                return r;
            }
        }
        return renderers.get(0);
    }

    public void open(Player p, Menu menu) {
        rendererFor(p).open(p, menu);
    }

    /** Confirmação sim/não. */
    public void confirm(Player p, Component question, Consumer<Player> yes, Consumer<Player> no) {
        Menu m = Menu.of(question).rows(3)
                .item(Menu.Button.of(new ItemStack(Material.PAPER), question, null))
                .button(Menu.Button.of(new ItemStack(Material.LIME_WOOL), msg.get("menu.confirmar"), yes).closing())
                .button(Menu.Button.of(new ItemStack(Material.RED_WOOL), msg.get("menu.cancelar"),
                        no != null ? no : pl -> { }).closing());
        open(p, m);
    }
}
