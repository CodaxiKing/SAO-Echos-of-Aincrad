package com.aincrad.core.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Descrição de um menu, sem dizer como ele é desenhado: título, itens (que passam de página sozinhos)
 * e botões fixos. Um {@link MenuRenderer} desenha (baú, cursor do AincradUI, formulário do Bedrock).
 *
 * <pre>
 * Menu m = Menu.of(Component.text("Lojas"))
 *     .item(Menu.Button.of(icone, Component.text("Ferreiro"), p -&gt; abrirFerreiro(p)))
 *     .button(Menu.Button.of(icone, Component.text("Fechar"), Player::closeInventory));
 * core.menus().open(player, m);
 * </pre>
 */
public final class Menu {

    /**
     * Um item clicável. {@code lore} são as linhas embaixo do nome; {@code action} null = só mostra.
     * {@code closeOnClick}: fecha o menu antes de rodar a ação.
     */
    public record Button(ItemStack icon, Component name, List<Component> lore, Consumer<Player> action, boolean closeOnClick,
                         String category, java.util.Map<String, Double> sorts) {

        public static Button of(ItemStack icon, Component name, Consumer<Player> action) {
            return new Button(icon, name, List.of(), action, false, null, java.util.Map.of());
        }

        public Button lore(List<Component> lines) {
            return new Button(icon, name, List.copyOf(lines), action, closeOnClick, category, sorts);
        }

        public Button closing() {
            return new Button(icon, name, lore, action, true, category, sorts);
        }

        /**
         * A aba deste item (ex.: "Comprar", "Vender"). Com mais de uma categoria no menu, a
         * interface flutuante ganha um botão de filtro; no baú, nada muda.
         */
        public Button category(String name) {
            return new Button(icon, name(), lore, action, closeOnClick, name, sorts);
        }

        /**
         * Um valor para ordenar a lista (ex.: {@code sort("Preço", 120)}). A interface flutuante
         * ganha um botão de ordem com cada nome usado no menu; no baú, nada muda.
         */
        public Button sort(String key, double value) {
            java.util.Map<String, Double> m = new java.util.LinkedHashMap<>(sorts);
            m.put(key, value);
            return new Button(icon, name, lore, action, closeOnClick, category, java.util.Collections.unmodifiableMap(m));
        }
    }

    private final Component title;
    private final List<Button> items = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private int rows = 6;
    private boolean floating;
    private Consumer<Player> onClose;

    private Menu(Component title) {
        this.title = title;
    }

    public static Menu of(Component title) {
        return new Menu(title);
    }

    /** Conteúdo (paginado quando não cabe). */
    public Menu item(Button b) {
        items.add(b);
        return this;
    }

    public Menu items(List<Button> list) {
        items.addAll(list);
        return this;
    }

    /** Botões fixos da última linha (até 5; o baú usa as pontas para trocar de página). */
    public Menu button(Button b) {
        buttons.add(b);
        return this;
    }

    /**
     * Marca o menu como feito para o jogador (e não para o admin): com o AincradUI, ele abre no
     * estilo SAO, flutuando, em vez do baú.
     */
    public Menu floating() {
        this.floating = true;
        return this;
    }

    public boolean isFloating() {
        return floating;
    }

    /** Linhas do baú (2 a 6). */
    public Menu rows(int rows) {
        this.rows = Math.max(2, Math.min(6, rows));
        return this;
    }

    public Menu onClose(Consumer<Player> onClose) {
        this.onClose = onClose;
        return this;
    }

    public Component title() {
        return title;
    }

    public List<Button> items() {
        return Collections.unmodifiableList(items);
    }

    public List<Button> buttons() {
        return Collections.unmodifiableList(buttons);
    }

    public int rows() {
        return rows;
    }

    public Consumer<Player> onClose() {
        return onClose;
    }

    /** Itens por página no baú: as linhas menos a de botões, 9 por linha. */
    public int pageSize() {
        return (rows - 1) * 9;
    }

    public int pages() {
        return Math.max(1, (items.size() + pageSize() - 1) / pageSize());
    }
}
