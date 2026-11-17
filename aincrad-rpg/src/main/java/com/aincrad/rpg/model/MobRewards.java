package com.aincrad.rpg.model;

/** Contas de mob: XP do abate com redução por diferença de level, e escala de vida e dano. */
public final class MobRewards {

    private MobRewards() {
    }

    /**
     * XP de um abate. {@code base} × level do mob; se o jogador está mais de {@code freeGap} levels
     * acima, perde {@code stepLoss} por level a mais, até o mínimo {@code minFactor}.
     */
    public static long xp(double base, int mobLevel, int playerLevel, int freeGap, double stepLoss, double minFactor) {
        double v = base * Math.max(1, mobLevel);
        int over = playerLevel - mobLevel - freeGap;
        if (over > 0) {
            v *= Math.max(minFactor, 1 - stepLoss * over);
        }
        return Math.max(0, Math.round(v));
    }

    /** Multiplicador de vida ou dano para o level: 1 no level 1, +{@code perLevel} por level. */
    public static double scale(int level, double perLevel) {
        return 1 + Math.max(0, perLevel) * (Math.max(1, level) - 1);
    }

    /** Level sorteado na faixa [min, max] da zona, com {@code roll} entre 0 e 1. */
    public static int pick(int min, int max, double roll) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return lo + (int) Math.min(hi - lo, Math.floor(roll * (hi - lo + 1)));
    }
}
