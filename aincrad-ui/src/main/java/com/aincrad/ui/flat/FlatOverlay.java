package com.aincrad.ui.flat;

import com.aincrad.ui.api.FlatDraw;
import com.aincrad.ui.api.FlatDraw.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * O que a HUD em barras mostra além da vida: o alvo no alto da tela, a bússola, o relógio, os
 * avisos e as recargas das skills. Tudo em retângulos e letras, como a {@link FlatHud}.
 *
 * <p>As alturas (y) são fixas: é por elas que as peças entram no resource pack. As posições
 * horizontais mudam livremente, então a HUD pode ser deslocada por jogador.
 */
public final class FlatOverlay {

    private static final int DARK = 0x0E1216;
    private static final int WHITE = 0xFFFFFF;
    private static final int SOFT = 0xD8DEE4;
    private static final int BLUE = 0x3FA9F5;
    private static final int GOLD = 0xF5A623;

    /** Quantas barras, no máximo, o alvo pode ter (um chefe). */
    public static final int MAX_TARGET_BARS = 8;
    public static final int MAX_TOASTS = 3;
    public static final int MAX_COOLDOWNS = 4;
    /** Metade da faixa da bússola, em graus e em pixels (1 px por grau). */
    public static final int COMPASS_HALF = 70;

    private static final int TARGET_NAME_Y = 44;
    private static final int TARGET_BAR_Y = 56;
    private static final int TOAST_Y = 30;
    private static final int COOLDOWN_Y = 228;

    /** O mob (ou jogador) que o jogador mira. {@code bars} maior que 1 para chefes. */
    public record Target(String name, double ratio, int color, int bars) {
    }

    /** Um aviso: título e uma linha de detalhe (pode ser vazia). */
    public record Toast(String title, String detail) {
    }

    /** A recarga de uma skill: {@code remaining} de 0 (pronta) a 1. */
    public record Cooldown(String label, double remaining) {
    }

    /** Um ponto na bússola: {@code angle} em graus em relação à frente do jogador (negativo = esquerda). */
    public record Marker(double angle, int color) {
    }

    /** {@code yaw} null esconde a bússola; {@code clock} null esconde o relógio. */
    public record Data(Target target, List<Toast> toasts, List<Cooldown> cooldowns, String clock, String date, Float yaw,
                       List<Marker> markers) {
    }

    private FlatOverlay() {
    }

    /**
     * @param xr borda direita da HUD (o relógio, os avisos e as recargas encostam nela)
     */
    public static void paint(FlatDraw d, Data v, int xr) {
        if (v.yaw() != null) {
            compass(d, v.yaw(), v.markers());
        }
        if (v.clock() != null) {
            d.textRight(xr, 4, Font.NEGRITO, v.clock(), WHITE);
            if (v.date() != null && !v.date().isEmpty()) {
                d.textRight(xr, 14, Font.PEQUENA, v.date(), SOFT);
            }
        }
        if (v.target() != null) {
            target(d, v.target());
        }
        int y = TOAST_Y;
        for (int i = 0; i < Math.min(MAX_TOASTS, v.toasts().size()); i++) {
            Toast t = v.toasts().get(i);
            d.rect(xr - 150, y, 150, 20, DARK, 70);
            d.rect(xr - 150, y, 2, 20, GOLD, 100);
            d.text(xr - 144, y + 1, Font.NEGRITO, d.fit(Font.NEGRITO, t.title(), 140), WHITE);
            d.text(xr - 144, y + 11, Font.PEQUENA, d.fit(Font.PEQUENA, t.detail(), 140), SOFT);
            y += 22;
        }
        for (int i = 0; i < Math.min(MAX_COOLDOWNS, v.cooldowns().size()); i++) {
            Cooldown c = v.cooldowns().get(i);
            int x = xr - 152 + i * 38;
            boolean ready = c.remaining() <= 0;
            d.text(x, COOLDOWN_Y, Font.PEQUENA, d.fit(Font.PEQUENA, c.label(), 33), ready ? WHITE : SOFT);
            d.rect(x, COOLDOWN_Y + 10, 36, 5, DARK, 70);
            int fill = (int) Math.round(34 * clamp(1 - c.remaining()));
            d.rect(x + 1, COOLDOWN_Y + 11, fill, 3, ready ? BLUE : GOLD, 100);
        }
    }

    private static void target(FlatDraw d, Target t) {
        // O nome vai em branco sobre fundo escuro (as cores do cursor mais fortes somem no cenário);
        // a cor do cursor aparece no quadrado ao lado do nome e nas barras.
        String name = d.fit(Font.NEGRITO, t.name(), 170);
        int w = d.width(Font.NEGRITO, name);
        d.rect(-w / 2 - 12, TARGET_NAME_Y - 1, w + 18, 11, DARK, 70);
        d.rect(-w / 2 - 9, TARGET_NAME_Y + 2, 5, 5, t.color(), 100);
        d.text(-w / 2, TARGET_NAME_Y, Font.NEGRITO, name, WHITE);
        int bars = Math.max(1, Math.min(MAX_TARGET_BARS, t.bars()));
        double left = clamp(t.ratio()) * bars;
        // A barra de cima esvazia primeiro.
        for (int b = 0; b < bars; b++) {
            int y = TARGET_BAR_Y + b * 6;
            double part = clamp(left - (bars - 1 - b));
            d.rect(-61, y, 122, 5, DARK, 70);
            d.rect(-60, y + 1, (int) Math.round(120 * part), 3, t.color(), 100);
        }
    }

    private static void compass(FlatDraw d, float yaw, List<Marker> markers) {
        d.rect(-COMPASS_HALF - 4, 3, COMPASS_HALF * 2 + 8, 12, DARK, 40);
        // Yaw do Minecraft: 0 = sul, 90 = oeste, 180 = norte, 270 = leste; cresce para a direita.
        String[] names = {"S", "O", "N", "L"};
        for (int i = 0; i < 4; i++) {
            float delta = FlatManager.angleDelta(yaw, i * 90f);
            if (Math.abs(delta) <= COMPASS_HALF) {
                d.textCenter(Math.round(delta), 4, Font.NEGRITO, names[i], i == 2 ? GOLD : WHITE);
            }
        }
        d.rect(0, 3, 1, 2, WHITE, 100);
        for (Marker m : markers) {
            if (Math.abs(m.angle()) <= COMPASS_HALF) {
                d.rect((int) Math.round(m.angle()) - 2, 13, 4, 2, m.color(), 100);
            }
        }
    }

    /** Tudo desenhado uma vez, para as peças entrarem no pack. */
    public static Data sample() {
        List<Toast> toasts = new ArrayList<>();
        for (int i = 0; i < MAX_TOASTS; i++) {
            toasts.add(new Toast("Aviso", "detalhe"));
        }
        List<Cooldown> cds = new ArrayList<>();
        for (int i = 0; i < MAX_COOLDOWNS; i++) {
            cds.add(new Cooldown("Skill", 0.5));
        }
        return new Data(new Target("Alvo", 1, WHITE, MAX_TARGET_BARS), toasts, cds, "00:00", "data", 0f,
                List.of(new Marker(0, WHITE)));
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
