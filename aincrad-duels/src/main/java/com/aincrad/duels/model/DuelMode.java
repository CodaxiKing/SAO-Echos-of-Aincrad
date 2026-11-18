package com.aincrad.duels.model;

import java.util.Locale;

/** Os três modos de duelo do anime. Só o {@link #TOTAL} pode matar. */
public enum DuelMode {

    /** Quem acerta o primeiro golpe limpo vence. */
    PRIMEIRO_GOLPE("primeiro_golpe"),
    /** Vence quem deixar o outro com metade da vida ou menos. */
    METADE("metade"),
    /** Até a vida acabar: o perdedor morre de verdade. */
    TOTAL("total");

    private final String key;

    DuelMode(String key) {
        this.key = key;
    }

    /** Nome usado nos comandos, na config e em messages.yml (duelo.modo.&lt;key&gt;). */
    public String key() {
        return key;
    }

    public boolean lethal() {
        return this == TOTAL;
    }

    /**
     * Um golpe acertou: o duelo acaba aqui, com a vitória de quem bateu?
     *
     * @param healthAfter vida de quem levou o golpe, depois dele
     * @param maxHealth   vida máxima de quem levou o golpe
     */
    public boolean endsOnHit(double healthAfter, double maxHealth) {
        return switch (this) {
            case PRIMEIRO_GOLPE -> true;
            case METADE -> healthAfter <= maxHealth / 2.0;
            case TOTAL -> false; // só a morte encerra
        };
    }

    /** Aceita "primeiro_golpe", "primeiro", "1", "metade", "meia", "2", "total", "3"... ou null. */
    public static DuelMode parse(String s) {
        if (s == null) {
            return null;
        }
        String k = s.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (k) {
            case "primeiro_golpe", "primeiro", "golpe", "1" -> PRIMEIRO_GOLPE;
            case "metade", "meia", "meia_vida", "metade_da_vida", "2" -> METADE;
            case "total", "morte", "3" -> TOTAL;
            default -> null;
        };
    }
}
