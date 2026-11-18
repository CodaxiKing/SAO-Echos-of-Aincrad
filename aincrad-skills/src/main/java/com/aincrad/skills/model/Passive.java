package com.aincrad.skills.model;

import java.util.Locale;

/** As skills passivas. */
public enum Passive {
    /** Recupera vida aos poucos, em batalha ou fora dela. */
    CURA_EM_BATALHA("GLISTERING_MELON_SLICE"),
    /** Chance de anular um golpe que vem de frente, com a arma na mão. */
    APARAR("SHIELD"),
    /** Mostra, só para o jogador, os mobs próximos. */
    BUSCA("SPYGLASS"),
    /** Segue um alvo marcado com /rastrear. */
    RASTREIO("COMPASS"),
    /** Arremessa lâminas com o botão direito. */
    ARREMESSO("IRON_NUGGET");

    private final String icon;

    Passive(String icon) {
        this.icon = icon;
    }

    public String icon() {
        return icon;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Passive parse(String s) {
        try {
            return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
