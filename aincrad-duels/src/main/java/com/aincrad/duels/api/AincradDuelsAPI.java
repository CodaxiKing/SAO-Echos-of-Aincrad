package com.aincrad.duels.api;

import com.aincrad.duels.DuelManager;
import com.aincrad.duels.ScoreStore;
import com.aincrad.duels.model.DuelMode;
import com.aincrad.duels.model.DuelScore;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * API do AincradDuels para os outros plugins. Métodos estáticos (dá para chamar por reflexão);
 * {@link #instance()} fica registrada no ServicesManager do Bukkit. Os eventos são o
 * {@link DuelStartEvent} e o {@link DuelEndEvent}.
 */
public final class AincradDuelsAPI {

    private static final AincradDuelsAPI INSTANCE = new AincradDuelsAPI();
    private static DuelManager duels;
    private static ScoreStore scores;

    private AincradDuelsAPI() {
    }

    /** Chamado pelo plugin ao ligar e ao desligar. */
    public static void init(DuelManager manager, ScoreStore store) {
        duels = manager;
        scores = store;
    }

    public static AincradDuelsAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return duels != null;
    }

    /** O jogador está num duelo (em contagem ou valendo)? */
    public static boolean isDueling(UUID player) {
        return duels != null && duels.service().duelOf(player) != null;
    }

    /** Os dois estão duelando um contra o outro? */
    public static boolean inDuel(UUID a, UUID b) {
        return duels != null && duels.service().inDuel(a, b);
    }

    /**
     * Desafia outro jogador (o botão "Duelar" de um menu).
     *
     * @param mode  "primeiro_golpe", "metade" ou "total"; null ou desconhecido usa o modo padrão
     * @param stake quanto cada um aposta, em Col (0 = sem aposta)
     */
    public static void challenge(Player from, Player to, String mode, long stake) {
        if (duels != null) {
            duels.challenge(from, to, DuelMode.parse(mode), stake);
        }
    }

    /** Vitórias do jogador (0 se o placar dele ainda não foi lido). */
    public static int getWins(UUID player) {
        return score(player).wins();
    }

    public static int getLosses(UUID player) {
        return score(player).losses();
    }

    /** Vitórias seguidas agora. */
    public static int getStreak(UUID player) {
        return score(player).streak();
    }

    private static DuelScore score(UUID player) {
        return scores == null ? DuelScore.EMPTY : scores.cached(player);
    }
}
