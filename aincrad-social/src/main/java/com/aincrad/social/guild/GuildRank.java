package com.aincrad.social.guild;

import java.util.Locale;

/** Cargos da guilda, do mais alto ao mais baixo. */
public enum GuildRank {
    LIDER, SUBLIDER, OFICIAL, MEMBRO, RECRUTA;

    /** Chave em messages.yml (guilda.cargo.&lt;chave&gt;). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@code this} está acima de {@code other}? */
    public boolean above(GuildRank other) {
        return ordinal() < other.ordinal();
    }

    /** O cargo logo acima (null para o líder). */
    public GuildRank up() {
        return this == LIDER ? null : values()[ordinal() - 1];
    }

    /** O cargo logo abaixo (null para o recruta). */
    public GuildRank down() {
        return this == RECRUTA ? null : values()[ordinal() + 1];
    }

    public static GuildRank parse(String s, GuildRank fallback) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
