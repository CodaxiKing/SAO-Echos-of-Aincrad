package com.aincrad.economy.shop;

import com.aincrad.ui.api.FlatDraw;
import com.aincrad.ui.api.FlatDraw.Font;
import com.aincrad.ui.api.FlatScreen;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A loja como tela plana: cabeçalho com o nome e o saldo, abas Comprar/Vender, lista com páginas
 * à esquerda e, à direita, o item escolhido com quantidade, total e o botão. Só desenha e guarda
 * o estado (aba, página, item, quantidade); os dados vêm por {@link Model}, para dar para testar
 * sem servidor.
 */
public final class ShopScreen implements FlatScreen {

    /** Uma linha da lista. */
    public record Entry(String name, String icon, long price, int pack, int owned) {
    }

    /** O que a tela precisa saber da loja e do jogador. */
    public interface Model {
        String title();

        /** Textos fixos: shop, itens, comprar, vender, quantidade, total, vazio, dica, porCompra, voceTem. */
        String label(String key);

        String money(long amount);

        long balance(Player viewer);

        /** Itens da aba (comprar = o que a loja vende; vender = o que ela compra e o jogador tem). */
        List<Entry> entries(Player viewer, boolean selling);

        /** Todos os ícones que a loja pode mostrar (para a preparação). */
        List<String> icons();

        /** Compra ou vende {@code quantity} do item {@code index} da aba. */
        void trade(Player viewer, boolean selling, int index, int quantity);

        void close(Player viewer);
    }

    static final int ROWS = 6;
    private static final int WHITE = 0xFFFFFF;
    private static final int INK = 0x2B2F36;
    private static final int GRAY = 0x7C838C;
    private static final int SOFT = 0xD8DEE4;
    private static final int DARK = 0x0E1216;
    private static final int ORANGE = 0xF5A623;
    private static final int ORANGE_DARK = 0xD98C0F;
    private static final int HOVER = 0xFFE3B3;

    private static final int LEFT = -150;
    private static final int TOP = 40;
    private static final int LIST_Y = TOP + 52;
    private static final int ROW_H = 15;
    private static final int DETAIL_X = 0;

    private final Model model;
    private boolean selling;
    private int page;
    private int selected;
    private int quantity = 1;

    public ShopScreen(Model model) {
        this.model = model;
    }

    @Override
    public void draw(FlatDraw d, Player viewer) {
        boolean prep = d.preparing();
        List<Entry> entries = prep ? sample() : model.entries(viewer, selling);
        int pages = Math.max(1, (entries.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));
        selected = entries.isEmpty() ? -1 : Math.max(0, Math.min(selected, entries.size() - 1));
        Entry current = selected < 0 ? null : entries.get(selected);
        int maxQty = current == null ? 1 : selling ? Math.max(1, current.owned()) : 64;
        quantity = Math.max(1, Math.min(quantity, maxQty));

        // Fundo escurecido e cabeçalho.
        d.rect(-245, 0, 490, 262, 0x000000, 45);
        d.rect(LEFT, TOP, 60, 16, WHITE, 100);
        d.textCenter(LEFT + 30, TOP + 2, Font.MEDIA, model.label("shop"), INK);
        d.rect(LEFT + 62, TOP, 146, 16, DARK, 65);
        d.text(LEFT + 68, TOP + 2, Font.MEDIA, d.fit(Font.MEDIA, model.title(), 134), WHITE);
        d.rect(60, TOP, 68, 16, WHITE, 100);
        d.textRight(124, TOP + 2, Font.MEDIA, d.fit(Font.MEDIA, prep ? "0" : model.money(model.balance(viewer)), 60), ORANGE_DARK);
        boolean overClose = d.button(130, TOP, 20, 16, () -> model.close(viewer));
        d.rect(130, TOP, 20, 16, overClose ? ORANGE : WHITE, 100);
        d.textCenter(140, TOP + 2, Font.MEDIA, "X", INK);

        // Faixa de informações.
        d.rect(LEFT, TOP + 20, 98, 12, WHITE, 100);
        d.text(LEFT + 4, TOP + 22, Font.PEQUENA, model.label("itens"), INK);
        d.textRight(LEFT + 94, TOP + 22, Font.NEGRITO, String.valueOf(entries.size()), INK);
        d.rect(LEFT + 100, TOP + 20, 200, 12, WHITE, 100);
        d.text(LEFT + 104, TOP + 22, Font.PEQUENA, d.fit(Font.PEQUENA, model.label("dica"), 192), GRAY);

        // Abas e páginas.
        tab(d, LEFT, model.label("comprar"), !selling, () -> switchTab(false));
        tab(d, LEFT + 54, model.label("vender"), selling, () -> switchTab(true));
        int px = LEFT + 112;
        boolean overPrev = d.button(px, TOP + 36, 12, 12, () -> page = Math.max(0, page - 1));
        d.rect(px, TOP + 36, 12, 12, overPrev ? HOVER : WHITE, 100);
        d.textCenter(px + 6, TOP + 38, Font.NEGRITO, "<", INK);
        d.textCenter(px + 26, TOP + 38, Font.PEQUENA, (page + 1) + " / " + pages, SOFT);
        boolean overNext = d.button(px + 40, TOP + 36, 12, 12, () -> page = Math.min(pages - 1, page + 1));
        d.rect(px + 40, TOP + 36, 12, 12, overNext ? HOVER : WHITE, 100);
        d.textCenter(px + 46, TOP + 38, Font.NEGRITO, ">", INK);

        // Lista.
        for (int row = 0; row < ROWS; row++) {
            int index = page * ROWS + row;
            int y = LIST_Y + row * ROW_H;
            if (index >= entries.size()) {
                d.rect(LEFT, y, 140, ROW_H - 1, WHITE, 25);
                continue;
            }
            Entry e = entries.get(index);
            boolean over = d.button(LEFT, y, 140, ROW_H - 1, () -> {
                selected = index;
                quantity = 1;
            });
            boolean chosen = index == selected;
            d.rect(LEFT, y, 140, ROW_H - 1, chosen ? ORANGE : over ? HOVER : WHITE, 100);
            d.icon(LEFT + 2, y + 1, 12, e.icon());
            d.text(LEFT + 18, y + 3, Font.PEQUENA, d.fit(Font.PEQUENA, e.name(), 62), chosen ? WHITE : INK);
            d.textRight(LEFT + 136, y + 3, Font.NEGRITO, d.fit(Font.NEGRITO, model.money(e.price()), 56), chosen ? WHITE : INK);
        }
        if (prep) {
            // Estados que a amostra não mostra: linha vazia e o aviso de aba sem itens.
            d.rect(LEFT, LIST_Y, 140, ROW_H - 1, WHITE, 25);
            for (int row = 1; row < ROWS; row++) {
                d.rect(LEFT, LIST_Y + row * ROW_H, 140, ROW_H - 1, WHITE, 25);
            }
            d.textCenter(DETAIL_X + 75, LIST_Y + 36, Font.PEQUENA, "vazio", SOFT);
            // Todos os ícones em todas as posições onde podem aparecer.
            for (String icon : model.icons()) {
                for (int row = 0; row < ROWS; row++) {
                    d.icon(LEFT + 2, LIST_Y + row * ROW_H + 1, 12, icon);
                }
                d.icon(DETAIL_X + 63, LIST_Y + 16, 24, icon);
            }
        }

        // Detalhe do item escolhido.
        d.rect(DETAIL_X, LIST_Y, 150, 12, WHITE, 100);
        d.rect(DETAIL_X, LIST_Y + 12, 150, 58, DARK, 65);
        if (current == null) {
            d.text(DETAIL_X + 4, LIST_Y + 2, Font.NEGRITO, "—", INK);
            d.textCenter(DETAIL_X + 75, LIST_Y + 36, Font.PEQUENA, d.fit(Font.PEQUENA, model.label("vazio"), 140), SOFT);
            return;
        }
        d.text(DETAIL_X + 4, LIST_Y + 2, Font.NEGRITO, d.fit(Font.NEGRITO, current.name(), 142), INK);
        d.icon(DETAIL_X + 63, LIST_Y + 16, 24, current.icon());
        d.textCenter(DETAIL_X + 75, LIST_Y + 44, Font.NEGRITO, d.fit(Font.NEGRITO, current.name(), 140), WHITE);
        String sub = selling
                ? model.label("voceTem").replace("<n>", String.valueOf(current.owned()))
                : model.label("porCompra").replace("<n>", String.valueOf(current.pack()));
        d.textCenter(DETAIL_X + 75, LIST_Y + 55, Font.PEQUENA, d.fit(Font.PEQUENA, sub, 140), SOFT);

        int qy = LIST_Y + 72;
        d.rect(DETAIL_X, qy, 150, 12, WHITE, 100);
        d.text(DETAIL_X + 4, qy + 2, Font.PEQUENA, model.label("quantidade"), INK);
        stepper(d, DETAIL_X + 96, qy + 1, "-", () -> quantity = Math.max(1, quantity - 1));
        d.textCenter(DETAIL_X + 122, qy + 2, Font.NEGRITO, String.valueOf(quantity), INK);
        stepper(d, DETAIL_X + 136, qy + 1, "+", () -> quantity = Math.min(maxQty, quantity + 1));

        int ty = qy + 14;
        d.rect(DETAIL_X, ty, 94, 12, WHITE, 100);
        d.text(DETAIL_X + 4, ty + 2, Font.PEQUENA, model.label("total"), INK);
        d.textRight(DETAIL_X + 90, ty + 2, Font.NEGRITO, d.fit(Font.NEGRITO, model.money(current.price() * quantity), 56), ORANGE_DARK);
        int index = selected;
        boolean overGo = d.button(DETAIL_X + 96, ty, 54, 12, () -> model.trade(viewer, selling, index, quantity));
        d.rect(DETAIL_X + 96, ty, 54, 12, overGo ? ORANGE_DARK : ORANGE, 100);
        d.textCenter(DETAIL_X + 123, ty + 2, Font.NEGRITO, model.label(selling ? "vender" : "comprar"), WHITE);
    }

    private void tab(FlatDraw d, int x, String label, boolean active, Runnable onClick) {
        boolean over = d.button(x, TOP + 36, 52, 12, onClick);
        d.rect(x, TOP + 36, 52, 12, active ? ORANGE : over ? HOVER : WHITE, active || over ? 100 : 65);
        d.textCenter(x + 26, TOP + 38, Font.NEGRITO, d.fit(Font.NEGRITO, label, 50), active ? WHITE : INK);
    }

    private void stepper(FlatDraw d, int x, int y, String sign, Runnable onClick) {
        boolean over = d.button(x, y, 12, 10, onClick);
        d.rect(x, y, 12, 10, over ? ORANGE : INK, 100);
        d.textCenter(x + 6, y + 1, Font.NEGRITO, sign, WHITE);
    }

    private void switchTab(boolean sell) {
        if (selling != sell) {
            selling = sell;
            page = 0;
            selected = 0;
            quantity = 1;
        }
    }

    /** Linhas de exemplo para a preparação (enche a lista e o detalhe). */
    private List<Entry> sample() {
        List<String> icons = model.icons();
        String icon = icons.isEmpty() ? "minecraft:item/barrier" : icons.get(0);
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < ROWS + 1; i++) {
            out.add(new Entry("Item", icon, 10, 1, 1));
        }
        return out;
    }

    @Override
    public void onClose(Player viewer) {
        // nada para devolver: a loja não segura itens
    }
}
