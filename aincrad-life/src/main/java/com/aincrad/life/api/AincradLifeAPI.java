package com.aincrad.life.api;

import com.aincrad.life.inn.InnService;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import org.bukkit.entity.Player;

/**
 * API do AincradLife. Métodos estáticos e a {@link #instance()} registrada no ServicesManager.
 * Use na thread principal. Evento: {@link ProfessionRankUpEvent}.
 */
public final class AincradLifeAPI {

    private static final AincradLifeAPI INSTANCE = new AincradLifeAPI();
    private static ProfessionService professions;
    private static InnService inns;

    private AincradLifeAPI() {
    }

    public static void init(ProfessionService p, InnService i) {
        professions = p;
        inns = i;
    }

    public static AincradLifeAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return professions != null;
    }

    /** Proficiência (0 a 1000) na profissão: "culinaria", "pesca", "ferreiro" ou "alquimia". Desconhecida: 0. */
    public static double getProficiency(Player player, String profession) {
        Profession p = Profession.parse(profession);
        return professions == null || p == null ? 0 : professions.proficiency(player, p);
    }

    /** "aprendiz", "praticante", "especialista", "mestre" ou "completo". */
    public static String getRank(Player player, String profession) {
        Profession p = Profession.parse(profession);
        return professions == null || p == null ? "aprendiz" : professions.rank(player, p).key();
    }

    /** Caminho escolhido na árvore da profissão (null = ainda não escolheu). */
    public static String getPath(Player player, String profession) {
        Profession p = Profession.parse(profession);
        return professions == null || p == null ? null : com.aincrad.life.model.TreeDef.pathOf(professions.owned(player, p));
    }

    /** Especialização escolhida (null = ainda não escolheu). */
    public static String getSpecialization(Player player, String profession) {
        Profession p = Profession.parse(profession);
        return professions == null || p == null ? null : com.aincrad.life.model.TreeDef.specOf(professions.owned(player, p));
    }

    /** O jogador conhece o item ("receita", "projeto", "formula"...)? */
    public static boolean knows(Player player, String kind, String id) {
        var l = professions == null ? null : professions.learnable(kind, id);
        return l != null && professions.knows(player, l);
    }

    /** Está com o bônus "Descansado" da estalagem? */
    public static boolean isRested(Player player) {
        return inns != null && inns.rested(player);
    }

    /** Tem um quarto alugado numa estalagem? */
    public static boolean hasRoom(Player player) {
        return inns != null && inns.of(player.getUniqueId()) != null;
    }
}
