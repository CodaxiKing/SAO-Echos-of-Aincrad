package com.aincrad.reputation.api;

import com.aincrad.reputation.CursorService;
import com.aincrad.reputation.ReputationService;
import com.aincrad.reputation.model.Achievements;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * API do AincradReputation. Os métodos são estáticos (dá para chamar por reflexão) e a
 * {@link #instance()} fica registrada no ServicesManager do Bukkit. Use na thread principal.
 */
public final class AincradReputationAPI {

    private static final AincradReputationAPI INSTANCE = new AincradReputationAPI();

    private static ReputationService reputation;
    private static CursorService cursor;

    private AincradReputationAPI() {
    }

    public static void init(ReputationService r, CursorService c) {
        reputation = r;
        cursor = c;
    }

    public static AincradReputationAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return reputation != null;
    }

    /** A cor do cursor do jogador: "verde", "laranja" ou "vermelho". */
    public static String getCursorColor(Player player) {
        return reputation == null ? "verde" : reputation.color(player).key();
    }

    /** O título em uso (null = nenhum). */
    public static String getTitle(Player player) {
        return reputation == null ? null : reputation.rep(player).title();
    }

    /** Jogadores verdes que ele já matou. */
    public static int getKills(Player player) {
        return reputation == null ? 0 : reputation.rep(player).cursor().kills();
    }

    /** Está preso? */
    public static boolean isJailed(Player player) {
        return cursor != null && cursor.jailed(player);
    }

    /**
     * Dá uma conquista de achievements.yml ao jogador (quests e eventos de outros plugins).
     *
     * @return true se ele ganhou agora; false se já tinha ou se a conquista não existe
     */
    public static boolean grantAchievement(Player player, String id) {
        Achievements.Def d = reputation == null ? null : reputation.achievements().get(id);
        return d != null && reputation.grant(player, d, d.floor());
    }

    /**
     * Um combate combinado (duelo): os dois podem se ferir, e até se matar, sem mudar a cor do
     * cursor de ninguém, até a hora dada (milissegundos). O AincradSocial chama isto ao começar um duelo.
     */
    public static void exemptPvp(UUID a, UUID b, long until) {
        if (cursor != null) {
            cursor.exempt(a, b, until);
        }
    }
}
