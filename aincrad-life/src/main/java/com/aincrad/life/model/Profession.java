package com.aincrad.life.model;

import java.util.Locale;

/** As quatro profissões. O jogador pode praticar todas. */
public enum Profession {
    CULINARIA, PESCA, FERREIRO, ALQUIMIA;

    /** Proficiência máxima. */
    public static final int MAX = 1000;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Profession parse(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toUpperCase(Locale.ROOT).replace("Á", "A").replace("Í", "I");
        return switch (t) {
            case "CULINARIA", "COZINHA", "COZINHEIRO" -> CULINARIA;
            case "PESCA", "PESCADOR" -> PESCA;
            case "FERREIRO", "FORJA" -> FERREIRO;
            case "ALQUIMIA", "ALQUIMISTA" -> ALQUIMIA;
            default -> null;
        };
    }

    /** Ranks por proficiência, como no anime. */
    public enum Rank {
        APRENDIZ(0), PRATICANTE(250), ESPECIALISTA(500), MESTRE(750), COMPLETO(1000);

        private final int from;

        Rank(int from) {
            this.from = from;
        }

        /** Proficiência em que o rank começa. */
        public int from() {
            return from;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Rank of(double proficiency) {
            Rank r = APRENDIZ;
            for (Rank x : values()) {
                if (proficiency >= x.from) {
                    r = x;
                }
            }
            return r;
        }

        /** O próximo rank, ou null no último. */
        public Rank next() {
            return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
        }
    }

    /**
     * Pontos ganhos por uma ação. Item no limite da proficiência (dificuldade igual ou maior) dá o
     * máximo; quanto mais fácil o item, menos pontos, até zero quando está {@code easyRange} ou
     * mais abaixo. Falha dá {@code failFactor} do normal.
     */
    public static double gain(double proficiency, double difficulty, boolean success, double maxGain, double easyRange, double failFactor) {
        if (proficiency >= MAX) {
            return 0;
        }
        double below = proficiency - difficulty;
        double share = below <= 0 ? 1 : easyRange <= 0 ? 0 : Math.max(0, 1 - below / easyRange);
        double g = maxGain * share * (success ? 1 : failFactor);
        return Math.min(g, MAX - proficiency);
    }

    /** Pontos de ofício da árvore: 1 a cada 50 de proficiência (20 no total). */
    public static int craftPoints(double proficiency) {
        return (int) Math.min(MAX, Math.max(0, proficiency)) / 50;
    }
}
