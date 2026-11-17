package com.aincrad.rpg.model;

import java.util.List;

/**
 * Quanto XP falta de um level para o próximo (levels.yml). Fórmula
 * {@code base × level^expoente} (arredondada) ou uma tabela com o valor de cada level.
 */
public final class XpCurve {

    private final int maxLevel;
    private final double base;
    private final double exponent;
    private final List<Long> table;

    private XpCurve(int maxLevel, double base, double exponent, List<Long> table) {
        this.maxLevel = Math.max(1, maxLevel);
        this.base = base;
        this.exponent = exponent;
        this.table = table;
    }

    public static XpCurve formula(int maxLevel, double base, double exponent) {
        return new XpCurve(maxLevel, base, exponent, null);
    }

    /** {@code table.get(0)} = XP do level 1 para o 2, e assim por diante. */
    public static XpCurve table(int maxLevel, List<Long> table) {
        if (table.isEmpty()) {
            throw new IllegalArgumentException("tabela de XP vazia");
        }
        return new XpCurve(maxLevel, 0, 0, List.copyOf(table));
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** XP para ir de {@code level} a {@code level + 1}; 0 no level máximo. */
    public long toNext(int level) {
        if (level >= maxLevel) {
            return 0;
        }
        if (table != null) {
            int i = Math.min(table.size() - 1, Math.max(0, level - 1));
            return Math.max(1, table.get(i));
        }
        return Math.max(1, Math.round(base * Math.pow(Math.max(1, level), exponent)));
    }

    /** XP total acumulado para chegar ao level (do level 1). */
    public long totalFor(int level) {
        long t = 0;
        for (int l = 1; l < Math.min(level, maxLevel); l++) {
            t += toNext(l);
        }
        return t;
    }
}
