package com.aincrad.rpg.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * Level, XP dentro do level, pontos livres e atributos de um jogador. Só contas: quem chama grava
 * e avisa.
 */
public final class Progress {

    private int level = 1;
    /** XP já juntado dentro do level atual (0 até {@link XpCurve#toNext}). */
    private long xp;
    private int points;
    private final Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
    private boolean dirty;

    public Progress() {
        for (Stat s : Stat.values()) {
            stats.put(s, 0);
        }
    }

    public static Progress of(int level, long xp, int points, int str, int agi, int vit) {
        Progress p = new Progress();
        p.level = Math.max(1, level);
        p.xp = Math.max(0, xp);
        p.points = Math.max(0, points);
        p.stats.put(Stat.FORCA, Math.max(0, str));
        p.stats.put(Stat.AGILIDADE, Math.max(0, agi));
        p.stats.put(Stat.VITALIDADE, Math.max(0, vit));
        return p;
    }

    public int level() {
        return level;
    }

    public long xp() {
        return xp;
    }

    public int points() {
        return points;
    }

    public int stat(Stat s) {
        return stats.get(s);
    }

    public boolean dirty() {
        return dirty;
    }

    public void clean() {
        dirty = false;
    }

    /** Progresso dentro do level (0 a 1), para a barra de XP. */
    public float fraction(XpCurve curve) {
        long need = curve.toNext(level);
        return need <= 0 ? 1f : Math.min(0.9999f, (float) xp / need);
    }

    /**
     * Soma XP, subindo quantos levels couberem; o que sobra fica no level novo. No level máximo o
     * XP para de juntar.
     *
     * @return quantos levels subiu
     */
    public int addXp(long amount, XpCurve curve, int pointsPerLevel) {
        if (amount <= 0 || level >= curve.maxLevel()) {
            return 0;
        }
        dirty = true;
        xp += amount;
        int gained = 0;
        long need;
        while ((need = curve.toNext(level)) > 0 && xp >= need) {
            xp -= need;
            level++;
            points += pointsPerLevel;
            gained++;
        }
        if (level >= curve.maxLevel()) {
            xp = 0;
        }
        return gained;
    }

    /**
     * Tira XP (admin ou morte). Sem {@code canLoseLevel}, para no 0 do level atual; com, desce de
     * level e tira os pontos daquele level (dos livres primeiro).
     *
     * @return quanto XP saiu de fato
     */
    public long removeXp(long amount, XpCurve curve, boolean canLoseLevel, int pointsPerLevel) {
        if (amount <= 0) {
            return 0;
        }
        dirty = true;
        long removed = 0;
        long left = amount;
        while (left > 0) {
            if (xp >= left) {
                xp -= left;
                removed += left;
                break;
            }
            removed += xp;
            left -= xp;
            xp = 0;
            if (!canLoseLevel || level <= 1) {
                break;
            }
            level--;
            xp = curve.toNext(level);
            takePoints(pointsPerLevel);
        }
        return removed;
    }

    private void takePoints(int n) {
        int fromFree = Math.min(points, n);
        points -= fromFree;
        int rest = n - fromFree;
        for (Stat s : Stat.values()) {
            while (rest > 0 && stats.get(s) > 0) {
                stats.put(s, stats.get(s) - 1);
                rest--;
            }
        }
    }

    /** Penalidade de morte: uma fração do XP já juntado no level. @return XP perdido */
    public long deathPenalty(double fraction) {
        long lost = (long) Math.floor(xp * Math.max(0, Math.min(1, fraction)));
        if (lost > 0) {
            xp -= lost;
            dirty = true;
        }
        return lost;
    }

    /** Gasta um ponto livre no atributo. @return false se não há pontos */
    public boolean spend(Stat s) {
        if (points <= 0) {
            return false;
        }
        points--;
        stats.put(s, stats.get(s) + 1);
        dirty = true;
        return true;
    }

    /** Devolve todos os pontos dos atributos para livres. */
    public void resetStats() {
        int total = 0;
        for (Stat s : Stat.values()) {
            total += stats.get(s);
            stats.put(s, 0);
        }
        points += total;
        dirty = true;
    }

    /** Define o level (admin): XP zera e os pontos ficam coerentes com o level novo. */
    public void setLevel(int newLevel, XpCurve curve, int pointsPerLevel) {
        level = Math.max(1, Math.min(curve.maxLevel(), newLevel));
        xp = 0;
        for (Stat s : Stat.values()) {
            stats.put(s, 0);
        }
        points = (level - 1) * pointsPerLevel;
        dirty = true;
    }
}
