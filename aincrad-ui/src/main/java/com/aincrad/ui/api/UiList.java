package com.aincrad.ui.api;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma lista no estilo do SAO (amigos, membros da party, mensagens, itens...): título, linhas com
 * ícone e nome, e botões redondos embaixo. Passa de página sozinha quando há muitas linhas.
 *
 * <pre>
 * ui.openList(player, UiList.of(Component.text("Amigos"))
 *         .row(UiList.Row.item(cabeca, Component.text("Asuna"), Component.text("online"), p -&gt; ...))
 *         .button(new UiList.Button("buttons/plus", Component.text("Adicionar"), p -&gt; ...)));
 * </pre>
 */
public final class UiList {

    /**
     * Uma linha. Use {@code icon} (id de glifo de glyphs.yml, ex.: under_icons/friends) ou
     * {@code item} (desenhado em 3D na linha; cabeças de jogador funcionam).
     *
     * @param detail texto menor depois do nome (ex.: "online", "x32"); pode ser null
     * @param action clique na linha; null deixa a linha só de leitura
     * @param lore   descrição inteira, mostrada ao lado quando o cursor está na linha; pode ser vazia
     */
    public record Row(String icon, ItemStack item, Component label, Component detail, MenuAction action, List<Component> lore) {

        public Row {
            lore = lore == null ? List.of() : List.copyOf(lore);
        }

        public Row(String icon, ItemStack item, Component label, Component detail, MenuAction action) {
            this(icon, item, label, detail, action, List.of());
        }

        /** A mesma linha com a descrição inteira (várias linhas), para quem aponta o cursor nela. */
        public Row lore(List<Component> lines) {
            return new Row(icon, item, label, detail, action, lines);
        }

        public static Row icon(String icon, Component label, Component detail, MenuAction action) {
            return new Row(icon, null, label, detail, action);
        }

        public static Row item(ItemStack item, Component label, Component detail, MenuAction action) {
            return new Row(null, item, label, detail, action);
        }
    }

    /** Botão redondo embaixo da lista; {@code icon} é um glifo de buttons/ (ex.: buttons/plus). */
    public record Button(String icon, Component label, MenuAction action) {
    }

    private final Component title;
    private final List<Row> rows = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private Component empty = Component.text("—");

    private UiList(Component title) {
        this.title = title;
    }

    public static UiList of(Component title) {
        return new UiList(title);
    }

    public UiList row(Row row) {
        rows.add(row);
        return this;
    }

    public UiList rows(List<Row> more) {
        rows.addAll(more);
        return this;
    }

    public UiList button(Button button) {
        buttons.add(button);
        return this;
    }

    /** Texto mostrado quando a lista está vazia. */
    public UiList empty(Component text) {
        this.empty = text;
        return this;
    }

    public Component title() {
        return title;
    }

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    public List<Button> buttons() {
        return List.copyOf(buttons);
    }

    public Component emptyText() {
        return empty;
    }
}
