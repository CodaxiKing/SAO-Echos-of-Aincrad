package com.aincrad.effects.image;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fonte aincrad:space (mesmos caracteres do AincradUI): caracteres invisíveis que andam N pixels.
 * Usada para encaixar a imagem de fundo no título dos menus.
 */
public final class SpaceFont {

    public static final Key FONT = Key.key("aincrad", "space");
    public static final int MAX_POW = 10;
    private static final char NEG_BASE = '';
    private static final char POS_BASE = '';

    private SpaceFont() {
    }

    public static String shift(int px) {
        StringBuilder sb = new StringBuilder();
        int remaining = Math.abs(px);
        boolean neg = px < 0;
        for (int pow = MAX_POW; pow >= 0; pow--) {
            int step = 1 << pow;
            while (remaining >= step) {
                sb.append((char) ((neg ? NEG_BASE : POS_BASE) + pow));
                remaining -= step;
            }
        }
        return sb.toString();
    }

    public static Component component(int px) {
        return px == 0 ? Component.empty() : Component.text(shift(px)).font(FONT);
    }

    public static Map<String, Integer> advances() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int pow = 0; pow <= MAX_POW; pow++) {
            map.put(String.valueOf((char) (NEG_BASE + pow)), -(1 << pow));
            map.put(String.valueOf((char) (POS_BASE + pow)), 1 << pow);
        }
        return map;
    }

    public static int measure(String s) {
        Map<String, Integer> adv = advances();
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            total += adv.getOrDefault(String.valueOf(s.charAt(i)), 0);
        }
        return total;
    }
}
