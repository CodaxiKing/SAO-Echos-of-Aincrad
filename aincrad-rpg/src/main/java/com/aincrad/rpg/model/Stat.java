package com.aincrad.rpg.model;

import java.util.Locale;

/** Atributos que o jogador distribui com os pontos de level. */
public enum Stat {
    /** Dano. */
    FORCA,
    /** Velocidade de ataque e de movimento. */
    AGILIDADE,
    /** Vida máxima. */
    VITALIDADE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Stat parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
