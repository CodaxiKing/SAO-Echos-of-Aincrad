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
        List<MenuRenderer> sorted = new ArrayList<>(renderers);
        sorted.sort(Comparator.comparingInt(MenuRenderer::priority).reversed());
        for (MenuRenderer r : sorted) {
            if (r.supports(p, menu)) {
                r.open(p, menu);
                return;
            }
        }
        renderers.get(0).open(p, menu);
    }

    /**
     * Escolher uma quantidade sem digitar: −10, −1, +1, +10 (vezes {@code step}) e confirmar.
     * Com o AincradUI é uma janela do SAO; sem ele, um menu de baú com os mesmos botões.
     *
     * @param chosen roda com o valor quando o jogador confirma
     */
    public void amount(Player p, Component title, long min, long max, long start, long step, Consumer<Long> chosen) {
        long lo = Math.min(min, max);
        long hi = Math.max(min, max);
        long st = Math.max(1, step);
        long value = Math.max(lo, Math.min(hi, start));
        List<MenuRenderer> sorted = new ArrayList<>(renderers);
        sorted.sort(Comparator.comparingInt(MenuRenderer::priority).reversed());
        for (MenuRenderer r : sorted) {
            if (r.amount(p, title, lo, hi, value, st, chosen)) {
                return;
            }
        }
        amountMenu(p, title, lo, hi, value, st, chosen);
    }

    private void amountMenu(Player p, Component title, long lo, long hi, long value, long st, Consumer<Long> chosen) {
        Menu m = Menu.of(title).rows(3)
                .item(Menu.Button.of(new ItemStack(Material.PAPER), Component.text(String.valueOf(value)), null));
        long[] deltas = {-10 * st, -st, st, 10 * st};
        for (long d : deltas) {
            long next = Math.max(lo, Math.min(hi, value + d));
            m.button(Menu.Button.of(new ItemStack(d < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE),
                    Component.text((d > 0 ? "+" : "") + d), pl -> amountMenu(pl, title, lo, hi, next, st, chosen)));
        }
        m.button(Menu.Button.of(new ItemStack(Material.LIME_WOOL), msg.get("menu.confirmar"), pl -> chosen.accept(value)).closing());
        open(p, m);
    }

    /**
     * Confirmação sim/não. Com o AincradUI e o resource pack, é a janela de alerta do SAO, com os
     * botões de aceitar e recusar; sem eles, um menu de baú com os dois botões.
     */
    public void confirm(Player p, Component question, Consumer<Player> yes, Consumer<Player> no) {
        List<MenuRenderer> sorted = new ArrayList<>(renderers);
        sorted.sort(Comparator.comparingInt(MenuRenderer::priority).reversed());
        for (MenuRenderer r : sorted) {
            if (r.confirm(p, question, yes, no)) {
                return;
            }
        }
        Menu m = Menu.of(question).rows(3)
                .item(Menu.Button.of(new ItemStack(Material.PAPER), question, null))
                .button(Menu.Button.of(new ItemStack(Material.LIME_WOOL), msg.get("menu.confirmar"), yes).closing())
                .button(Menu.Button.of(new ItemStack(Material.RED_WOOL), msg.get("menu.cancelar"),
                        no != null ? no : pl -> { }).closing());
        open(p, m);
    }
}
