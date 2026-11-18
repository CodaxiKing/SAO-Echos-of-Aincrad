package com.aincrad.ui.flat;

import com.aincrad.ui.api.FlatDraw;
import com.aincrad.ui.glyph.SpaceFont;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Junta os desenhos de uma tela numa linha de texto só. Cada peça é um caractere posicionado
 * com espaços (positivos e negativos); no fim a linha volta ao ponto de partida, então a largura
 * total é zero e o título da BossBar, que é centralizado, começa exatamente no meio da tela.
 */
public final class FlatCanvas implements FlatDraw {

    /** Área clicável. */
    public record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private final FlatAtlas atlas;
    private final boolean preparing;
    private final int cursorX;
    private final int cursorY;
    private final List<Hit> hits = new ArrayList<>();

    /** Trechos seguidos com a mesma fonte e cor viram um componente só. */
    private final List<Component> runs = new ArrayList<>();
    private final StringBuilder run = new StringBuilder();
    private final StringBuilder signature = new StringBuilder();
    private Key runFont = FlatAtlas.FONT;
    private int runColor = 0xFFFFFF;
    private int x;
    private int missing;

    public FlatCanvas(FlatAtlas atlas, boolean preparing, int cursorX, int cursorY) {
        this.atlas = atlas;
        this.preparing = preparing;
        this.cursorX = cursorX;
        this.cursorY = cursorY;
    }

    @Override
    public boolean preparing() {
        return preparing;
    }

    @Override
    public int cursorX() {
        return cursorX;
    }

    @Override
    public int cursorY() {
        return cursorY;
    }

    private void flush() {
        if (run.isEmpty()) {
            return;
        }
        runs.add(Component.text(run.toString(), TextColor.color(runColor)).font(runFont));
        signature.append(runFont.value()).append('#').append(runColor).append(run).append('|');
        run.setLength(0);
    }

    private void style(Key font, int rgb) {
        if (!font.equals(runFont) || rgb != runColor) {
            flush();
            runFont = font;
            runColor = rgb;
        }
    }

    /** Anda até {@code target} (x a partir do centro) com os caracteres de espaço da fonte atual. */
    private void moveTo(int target) {
        if (target == x) {
            return;
        }
        // Os espaços só existem na fonte aincrad:flat; a cor não importa (são invisíveis).
        if (!runFont.equals(FlatAtlas.FONT)) {
            style(FlatAtlas.FONT, runColor);
        }
        run.append(SpaceFont.shift(target - x));
        x = target;
    }

    @Override
    public void rect(int rx, int ry, int w, int h, int rgb, int alpha) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int a = Math.max(10, Math.min(100, alpha));
        // Um glifo não passa de 256 px de altura: retângulo alto vira dois.
        for (int top = ry; top < ry + h; top += 256) {
            int part = Math.min(256, ry + h - top);
            String[] pieces = atlas.rect(part, a, top, preparing);
            if (pieces == null) {
                missing++;
                continue;
            }
            if (preparing) {
                continue;
            }
            moveTo(rx);
            style(FlatAtlas.FONT, rgb);
            int left = w;
            for (int i = 0; i < FlatAtlas.WIDTHS.length; i++) {
                int pw = FlatAtlas.WIDTHS[i];
                while (left >= pw) {
                    run.append(pieces[i]);
                    // O jogo soma 1 px depois de cada glifo; os pedaços precisam encostar.
                    run.append(SpaceFont.shift(-1));
                    x += pw;
                    left -= pw;
                }
            }
        }
    }

    @Override
    public int width(Font font, String text) {
        FlatFont f = FlatFont.of(font);
        return f.width(f.clean(text));
    }

    @Override
    public String fit(Font font, String text, int maxWidth) {
        return FlatFont.of(font).fit(text, maxWidth);
    }

    @Override
    public int text(int tx, int ty, Font font, String text, int rgb) {
        FlatFont f = FlatFont.of(font);
        String t = f.clean(text);
        int w = f.width(t);
        Key key = atlas.text(font, ty, preparing);
        if (key == null) {
            missing++;
            return w;
        }
        if (preparing || t.isEmpty()) {
            return w;
        }
        moveTo(tx);
        style(key, rgb);
        run.append(t);
        x += w;
        return w;
    }

    @Override
    public int textRight(int right, int ty, Font font, String text, int rgb) {
        return text(right - width(font, text), ty, font, text, rgb);
    }

    @Override
    public int textCenter(int center, int ty, Font font, String text, int rgb) {
        return text(center - width(font, text) / 2, ty, font, text, rgb);
    }

    @Override
    public void icon(int ix, int iy, int size, String texture) {
        icon(ix, iy, size, texture, 0xFFFFFF);
    }

    @Override
    public void icon(int ix, int iy, int size, String texture, int rgb) {
        FlatAtlas.Piece p = atlas.icon(texture, size, iy, preparing);
        if (p == null) {
            missing++;
            return;
        }
        if (preparing) {
            return;
        }
        moveTo(ix);
        style(FlatAtlas.FONT, rgb);
        run.append(p.text());
        x += p.advance();
    }

    @Override
    public boolean button(int bx, int by, int w, int h, Runnable onClick) {
        Hit hit = new Hit(bx, by, w, h, onClick);
        hits.add(hit);
        return !preparing && hit.contains(cursorX, cursorY);
    }

    /** O botão de cima (desenhado por último) que contém o ponto. */
    public Hit hitAt(int px, int py) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(px, py)) {
                return hits.get(i);
            }
        }
        return null;
    }

    /** Peças pedidas que ainda não estão no pack (não foram desenhadas). */
    public int missing() {
        return missing;
    }

    /** Muda quando o desenho muda (para não reenviar a mesma tela). */
    public String signature() {
        flush();
        return signature.toString();
    }

    /** A linha pronta: largura total zero, sem sombra. */
    public Component build() {
        moveTo(0);
        flush();
        TextComponent.Builder b = Component.text().shadowColor(ShadowColor.none());
        runs.forEach(b::append);
        return b.build();
    }
}
