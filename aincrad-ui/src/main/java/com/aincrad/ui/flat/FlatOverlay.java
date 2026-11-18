package com.aincrad.ui.flat;

import com.aincrad.ui.api.FlatDraw;
import com.aincrad.ui.api.FlatDraw.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * O que a HUD em barras mostra além da vida: o alvo no alto da tela, a bússola, o relógio, os
 * avisos e as recargas das skills. Com as artes do pack ({@code art}), o alvo usa a barra do
 * anime, os avisos são placas claras com ícone e as recargas são os ícones de skill; sem elas,
 * tudo é retângulo e letra, como a {@link FlatHud}.
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
    /** A cor das placas do menu (o fundo das linhas) e a do texto sobre elas. */
    static final int PLATE = 0xDCE0E6;
    static final int INK = 0x3C3C3C;
    static final int INK_SOFT = 0x70767E;

    /** Quantas barras, no máximo, o alvo pode ter (um chefe). */
    public static final int MAX_TARGET_BARS = 8;
    public static final int MAX_TOASTS = 3;
    public static final int MAX_COOLDOWNS = 4;
    /** Metade da faixa da bússola, em graus e em pixels (1 px por grau). */
    public static final int COMPASS_HALF = 70;
    /** Os ícones de skill do pack (ui/skills/skill_1 a skill_7). */
    public static final int SKILL_ICONS = 7;
    /** Os ícones que um aviso pode ter (ui/under_icons). */
    public static final List<String> TOAST_ICONS = List.of("items", "skills", "quests", "player", "friends", "guild", "help");

    private static final int TARGET_NAME_Y = 44;
    private static final int TARGET_BAR_Y = 56;
    private static final int TOAST_Y = 30;
    private static final int COOLDOWN_Y = 228;
    private static final int SKILL_Y = 210;
    private static final int SKILL_SIZE = 22;

    /** O mob (ou jogador) que o jogador mira. {@code bars} maior que 1 para chefes. */
    public record Target(String name, double ratio, int color, int bars) {
    }

    /** Um aviso: título, uma linha de detalhe (pode ser vazia) e o ícone (um de {@link #TOAST_ICONS}). */
    public record Toast(String title, String detail, String icon) {

        public Toast(String title, String detail) {
            this(title, detail, "quests");
        }
    }

    /** A recarga de uma skill: {@code remaining} de 0 (pronta) a 1; {@code slot} escolhe o ícone. */
    public record Cooldown(String label, double remaining, int slot) {

        public Cooldown(String label, double remaining) {
            this(label, remaining, 0);
        }
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
     * @param xr  borda direita da HUD (o relógio, os avisos e as recargas encostam nela)
     * @param art usa as artes do pack
     */
    public static void paint(FlatDraw d, Data v, int xr, boolean art) {
        if (v.yaw() != null) {
            // Com as artes, a moldura de vida chega perto do meio da tela: a bússola desce uma linha.
            compass(d, v.yaw(), v.markers(), art ? 27 : 3);
        }
        if (v.clock() != null) {
            d.textRight(xr, 4, Font.NEGRITO, v.clock(), WHITE);
            if (v.date() != null && !v.date().isEmpty()) {
                d.textRight(xr, 14, Font.PEQUENA, v.date(), SOFT);
            }
        }
        if (v.target() != null) {
            target(d, v.target(), art);
        }
        int y = TOAST_Y;
        for (int i = 0; i < Math.min(MAX_TOASTS, v.toasts().size()); i++) {
            Toast t = v.toasts().get(i);
            if (art) {
                d.rect(xr - 150, y, 150, 20, PLATE, 100);
                d.rect(xr - 150, y, 2, 20, GOLD, 100);
                d.icon(xr - 146, y + 1, 18, "aincrad:ui/under_icons/" + (TOAST_ICONS.contains(t.icon()) ? t.icon() : "quests"));
                d.text(xr - 124, y + 1, Font.NEGRITO, d.fit(Font.NEGRITO, t.title(), 120), INK);
                d.text(xr - 124, y + 11, Font.PEQUENA, d.fit(Font.PEQUENA, t.detail(), 120), INK_SOFT);
            } else {
                d.rect(xr - 150, y, 150, 20, DARK, 70);
                d.rect(xr - 150, y, 2, 20, GOLD, 100);
                d.text(xr - 144, y + 1, Font.NEGRITO, d.fit(Font.NEGRITO, t.title(), 140), WHITE);
                d.text(xr - 144, y + 11, Font.PEQUENA, d.fit(Font.PEQUENA, t.detail(), 140), SOFT);
            }
            y += 22;
        }
        for (int i = 0; i < Math.min(MAX_COOLDOWNS, v.cooldowns().size()); i++) {
            Cooldown c = v.cooldowns().get(i);
            boolean ready = c.remaining() <= 0;
            if (art) {
                // O ícone da skill (apagado enquanto recarrega), a barra da recarga e o nome.
                int shown = Math.min(MAX_COOLDOWNS, v.cooldowns().size());
                int x = xr - 30 * shown + 8 + i * 30;
                d.icon(x, SKILL_Y, SKILL_SIZE, "aincrad:ui/skills/skill_" + (Math.floorMod(c.slot(), SKILL_ICONS) + 1), ready ? WHITE : 0x808080);
                d.rect(x, SKILL_Y + SKILL_SIZE + 1, SKILL_SIZE, 3, DARK, 70);
                d.rect(x, SKILL_Y + SKILL_SIZE + 1, (int) Math.round(SKILL_SIZE * clamp(1 - c.remaining())), 3, ready ? BLUE : GOLD, 100);
                String label = d.fit(Font.PEQUENA, c.label(), 28);
                d.text(x + SKILL_SIZE / 2 - d.width(Font.PEQUENA, label) / 2, SKILL_Y + SKILL_SIZE + 5, Font.PEQUENA, label, ready ? WHITE : SOFT);
            } else {
                int x = xr - 152 + i * 38;
                d.text(x, COOLDOWN_Y, Font.PEQUENA, d.fit(Font.PEQUENA, c.label(), 33), ready ? WHITE : SOFT);
                d.rect(x, COOLDOWN_Y + 10, 36, 5, DARK, 70);
                int fill = (int) Math.round(34 * clamp(1 - c.remaining()));
                d.rect(x + 1, COOLDOWN_Y + 11, fill, 3, ready ? BLUE : GOLD, 100);
            }
        }
    }

    private static void target(FlatDraw d, Target t, boolean art) {
        // O nome não vai na cor do cursor (as mais fortes somem no cenário): a cor aparece no
        // quadrado ao lado do nome e nas barras.
        String name = d.fit(Font.NEGRITO, t.name(), 170);
        int w = d.width(Font.NEGRITO, name);
        d.rect(-w / 2 - 12, TARGET_NAME_Y - 1, w + 18, 11, art ? PLATE : DARK, art ? 100 : 70);
        d.rect(-w / 2 - 9, TARGET_NAME_Y + 2, 5, 5, t.color(), 100);
        d.text(-w / 2, TARGET_NAME_Y, Font.NEGRITO, name, art ? INK : WHITE);
        int bars = Math.max(1, Math.min(MAX_TARGET_BARS, t.bars()));
        double left = clamp(t.ratio()) * bars;
        // A barra de cima esvazia primeiro.
        for (int b = 0; b < bars; b++) {
            double part = clamp(left - (bars - 1 - b));
            if (art) {
                // A barra pequena do anime (ui/hud/mini, 248×32) com as 20 fatias pintadas.
                int y = TARGET_BAR_Y + 2 + b * 11;
                d.icon(-62, y, 16, "aincrad:ui/hud/mini");
                slices(d, -60, y, 16, 6, part, "aincrad:ui/hud/mini_", t.color());
            } else {
                int y = TARGET_BAR_Y + b * 6;
                d.rect(-61, y, 122, 5, DARK, 70);
                d.rect(-60, y + 1, (int) Math.round(120 * part), 3, t.color(), 100);
            }
        }
    }

    /**
     * As 20 fatias de uma barra do anime, pintadas: desenha as que cabem em {@code ratio}. Na
     * preparação desenha todas (é como entram no pack).
     */
    static void slices(FlatDraw d, int x, int y, int size, int step, double ratio, String prefix, int rgb) {
        int full = d.preparing() ? 20 : (int) Math.round(20 * clamp(ratio));
        for (int i = 0; i < full; i++) {
            d.icon(x + step * i, y, size, prefix + (i < 10 ? "0" : "") + i, rgb);
        }
    }

    private static void compass(FlatDraw d, float yaw, List<Marker> markers, int y) {
        d.rect(-COMPASS_HALF - 4, y, COMPASS_HALF * 2 + 8, 12, DARK, 40);
        // Yaw do Minecraft: 0 = sul, 90 = oeste, 180 = norte, 270 = leste; cresce para a direita.
        String[] names = {"S", "O", "N", "L"};
        for (int i = 0; i < 4; i++) {
            float delta = FlatManager.angleDelta(yaw, i * 90f);
            if (Math.abs(delta) <= COMPASS_HALF) {
                d.textCenter(Math.round(delta), y + 1, Font.NEGRITO, names[i], i == 2 ? GOLD : WHITE);
            }
        }
        d.rect(0, y, 1, 2, WHITE, 100);
        for (Marker m : markers) {
            if (Math.abs(m.angle()) <= COMPASS_HALF) {
                d.rect((int) Math.round(m.angle()) - 2, y + 10, 4, 2, m.color(), 100);
            }
        }
    }

    /** Tudo desenhado uma vez, em todas as variações, para as peças entrarem no pack. */
    public static void prepare(FlatDraw d, int xr, boolean art) {
        List<Cooldown> cds = new ArrayList<>();
        for (int i = 0; i < MAX_COOLDOWNS; i++) {
            cds.add(new Cooldown("Skill", 0.5, i));
        }
        Target target = new Target("Alvo", 1, WHITE, MAX_TARGET_BARS);
        // Um aviso de cada ícone em cada uma das alturas.
        for (int first = 0; first < TOAST_ICONS.size(); first++) {
            List<Toast> toasts = new ArrayList<>();
            for (int i = 0; i < MAX_TOASTS; i++) {
                toasts.add(new Toast("Aviso", "detalhe", TOAST_ICONS.get((first + i) % TOAST_ICONS.size())));
            }
            paint(d, new Data(target, toasts, cds, "00:00", "data", 0f, List.of(new Marker(0, WHITE))), xr, art);
        }
        if (art) {
            // Todos os ícones de skill.
            for (int s = 0; s < SKILL_ICONS; s++) {
                d.icon(0, SKILL_Y, SKILL_SIZE, "aincrad:ui/skills/skill_" + (s + 1));
            }
        }
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
