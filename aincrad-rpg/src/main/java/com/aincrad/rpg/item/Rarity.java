package com.aincrad.rpg.item;

import java.util.Locale;

/** Raridade: cor do nome e multiplicador dos bônus (valores em config.yml → raridades). */
public enum Rarity {
    COMUM, INCOMUM, RARO, EPICO, LENDARIO;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Rarity parse(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toUpperCase(Locale.ROOT).replace("É", "E");
        try {
            return valueOf(t);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
