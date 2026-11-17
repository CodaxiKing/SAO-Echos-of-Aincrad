package com.aincrad.floors.model;

import java.util.Locale;
import java.util.Set;

/** Quem entra em qual andar e o que o Teleport Gate mostra, em cada modo. Só contas. */
public final class FloorRules {

    /** Modo de liberação (config.yml → modo). */
    public enum Mode {
        /** Andar liberado vale para todos. */
        GLOBAL,
        /** Só entra quem participou da vitória ou foi liberado por um admin. */
        POR_JOGADOR,
        /** Andar liberado é aberto a todos, mas o Gate só lista os que o jogador já visitou a pé. */
        MISTO;

        public static Mode parse(String s) {
            try {
                return valueOf(String.valueOf(s).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return GLOBAL;
            }
        }
    }

    private FloorRules() {
    }

    /** O maior andar liberado em sequência a partir do 1 (a linha de frente). */
    public static int frontline(Set<Integer> unlocked, int total) {
        int n = 1;
        while (n < total && unlocked.contains(n + 1)) {
            n++;
        }
        return n;
    }

    /** O andar 1 está sempre liberado. */
    public static boolean unlocked(int floor, Set<Integer> unlocked) {
        return floor == 1 || unlocked.contains(floor);
    }

    /** O jogador pode pisar no andar? */
    public static boolean canEnter(Mode mode, int floor, Set<Integer> unlocked, Set<Integer> granted, boolean bypass) {
        if (bypass) {
            return true;
        }
        if (!unlocked(floor, unlocked)) {
            return false;
        }
        return mode != Mode.POR_JOGADOR || floor == 1 || granted.contains(floor);
    }

    /** O Gate oferece teleporte para o andar? */
    public static boolean gateOffers(Mode mode, int floor, Set<Integer> unlocked, Set<Integer> granted, Set<Integer> visited, boolean bypass) {
        if (!canEnter(mode, floor, unlocked, granted, bypass)) {
            return false;
        }
        return mode != Mode.MISTO || bypass || floor == 1 || visited.contains(floor);
    }
}
