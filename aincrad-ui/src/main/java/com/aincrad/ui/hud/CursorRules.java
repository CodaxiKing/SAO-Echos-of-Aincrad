package com.aincrad.ui.hud;

/**
 * A cor do cursor de um mob para quem olha, pela diferença de level, como no anime: rosa claro
 * para o mob fraco, carmesim quase preto para o que mata. Regra pura, sem servidor.
 */
public final class CursorRules {

    public static final int PALE = 0xF7C6D0;
    public static final int PINK = 0xF08BA0;
    public static final int RED = 0xE53B4B;
    public static final int DARK_RED = 0xB01325;
    public static final int CRIMSON = 0x8E1020;
    /** Sem level conhecido (sem o AincradRPG, ou outro jogador). */
    public static final int NEUTRAL = 0xFFFFFF;

    private CursorRules() {
    }

    /**
     * @param mobLevel    level do mob; 0 ou menos = desconhecido
     * @param playerLevel level de quem olha
     */
    public static int color(int mobLevel, int playerLevel) {
        if (mobLevel <= 0 || playerLevel <= 0) {
            return NEUTRAL;
        }
        int diff = mobLevel - playerLevel;
        if (diff <= -10) {
            return PALE;
        }
        if (diff <= -3) {
            return PINK;
        }
        if (diff <= 2) {
            return RED;
        }
        if (diff <= 6) {
            return DARK_RED;
        }
        return CRIMSON;
    }

    /** Quantas barras de vida o alvo mostra: uma, ou {@code bossBars} a partir de {@code bossHealth}. */
    public static int bars(double maxHealth, double bossHealth, int bossBars) {
        return bossHealth > 0 && maxHealth >= bossHealth ? Math.max(1, bossBars) : 1;
    }
}
