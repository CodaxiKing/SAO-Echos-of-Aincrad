package com.aincrad.social.guild;

import java.util.Locale;

/**
 * Permissões da guilda e o cargo mínimo padrão de cada uma (tabela de cargos do plano).
 * As marcadas como fixas não podem ser mudadas pelo líder.
 */
public enum GuildPerm {
    /** Convidar e aceitar candidaturas. */
    CONVIDAR(GuildRank.OFICIAL, false),
    /** Expulsar cargos abaixo do seu. */
    EXPULSAR(GuildRank.OFICIAL, false),
    /** Promover e rebaixar (até um cargo abaixo do seu). */
    PROMOVER(GuildRank.SUBLIDER, false),
    DEPOSITAR(GuildRank.MEMBRO, false),
    RETIRAR_COFRE(GuildRank.MEMBRO, false),
    /** Sacar do tesouro e comprar com ele. */
    TESOURO(GuildRank.SUBLIDER, false),
    CASA(GuildRank.RECRUTA, false),
    ALIANCAS(GuildRank.SUBLIDER, false),
    /** Tag, emblema, uniforme, regras, recrutamento e taxa. */
    EDITAR(GuildRank.LIDER, true),
    /** Passar a liderança e desfazer a guilda. */
    LIDERANCA(GuildRank.LIDER, true);

    private final GuildRank defaultMin;
    private final boolean fixed;

    GuildPerm(GuildRank defaultMin, boolean fixed) {
        this.defaultMin = defaultMin;
        this.fixed = fixed;
    }

    public GuildRank defaultMin() {
        return defaultMin;
    }

    public boolean fixed() {
        return fixed;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
