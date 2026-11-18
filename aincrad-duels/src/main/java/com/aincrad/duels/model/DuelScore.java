package com.aincrad.duels.model;

/**
 * O placar de duelos de um jogador.
 *
 * @param streak vitórias seguidas agora (zera na derrota; o empate não mexe)
 * @param best   a maior sequência de vitórias que ele já teve
 */
public record DuelScore(int wins, int losses, int draws, int streak, int best) {

    public static final DuelScore EMPTY = new DuelScore(0, 0, 0, 0, 0);

    public DuelScore won() {
        int s = streak + 1;
        return new DuelScore(wins + 1, losses, draws, s, Math.max(best, s));
    }

    public DuelScore lost() {
        return new DuelScore(wins, losses + 1, draws, 0, best);
    }

    public DuelScore drew() {
        return new DuelScore(wins, losses, draws + 1, streak, best);
    }

    public int total() {
        return wins + losses + draws;
    }

    /** Vitórias em % dos duelos que tiveram vencedor (0 sem nenhum). */
    public int winRate() {
        int decided = wins + losses;
        return decided == 0 ? 0 : (int) Math.round(wins * 100.0 / decided);
    }
}
