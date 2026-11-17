package com.aincrad.ui.glyph;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fonte aincrad:space: caracteres invisíveis que andam N pixels para a esquerda ou direita.
 * Usada para posicionar a HUD e o fundo do menu de baú.
 */
public final class SpaceFont {

    public static final Key FONT = Key.key("aincrad", "space");
    /** Maior potência de 2 disponível (2^10 = 1024 px). */
    public static final int MAX_POW = 10;
    private static final char NEG_BASE = '';
    private static final char POS_BASE = '';

    private SpaceFont() {
    }

    public static char negative(int pow) {
        return (char) (NEG_BASE + pow);
    }

    public static char positive(int pow) {
        return (char) (POS_BASE + pow);
    }

    /** Sequência de caracteres que soma exatamente {@code px} pixels. */
    public static String shift(int px) {
        StringBuilder sb = new StringBuilder();
        int remaining = Math.abs(px);
        boolean neg = px < 0;
        for (int pow = MAX_POW; pow >= 0; pow--) {
            int step = 1 << pow;
            while (remaining >= step) {
                sb.append(neg ? negative(pow) : positive(pow));
                remaining -= step;
            }
        }
        return sb.toString();
    }

    public static Component component(int px) {
        if (px == 0) {
            return Component.empty();
        }
        return Component.text(shift(px)).font(FONT);
    }

    /** Mapa caractere → avanço, para o provider "space" da fonte. */
    public static Map<String, Integer> advances() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int pow = 0; pow <= MAX_POW; pow++) {
            map.put(String.valueOf(negative(pow)), -(1 << pow));
            map.put(String.valueOf(positive(pow)), 1 << pow);
        }
        return map;
    }

    /** Soma dos avanços de uma sequência gerada por {@link #shift(int)}. */
    public static int measure(String s) {
        Map<String, Integer> adv = advances();
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            total += adv.getOrDefault(String.valueOf(s.charAt(i)), 0);
        }
        return total;
    }
}
