package com.aincrad.life.cooking;

import java.util.Locale;

/** As etapas de preparo de um prato, na bancada. */
public enum Step {
    /** Clicar nas marcas que passam pela tábua, no ritmo. */
    CORTAR,
    /** Manter a chama na faixa certa pelo tempo pedido. */
    FOGO,
    /** Seguir as marcas em volta da panela, no sentido indicado. */
    MEXER,
    /** Uma marca acende de repente; temperar enquanto ela está acesa. */
    TEMPERAR,
    /** Um último clique no tempo certo; perfeito, ajuda o prato a sair excelente. */
    EMPRATAR;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Step parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
