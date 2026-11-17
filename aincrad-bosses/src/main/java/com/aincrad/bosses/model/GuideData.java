package com.aincrad.bosses.model;

import java.util.Set;
import java.util.TreeSet;

/**
 * O que o servidor já viu de um chefe (guia de estratégia): quantas tentativas, a fase mais longe
 * e quais fases e habilidades já apareceram. Fases são guardadas como "f&lt;índice&gt;" e
 * habilidades como "h&lt;índice da fase&gt;:&lt;TIPO&gt;", juntas num texto separado por vírgulas.
 */
public final class GuideData {

    private int attempts;
    private int bestPhase;
    private final Set<String> seen = new TreeSet<>();

    public GuideData() {
    }

    public GuideData(int attempts, int bestPhase, String seen) {
        this.attempts = Math.max(0, attempts);
        this.bestPhase = Math.max(0, bestPhase);
        if (seen != null) {
            for (String t : seen.split(",")) {
                if (!t.isBlank()) {
                    this.seen.add(t.trim());
                }
            }
        }
    }

    public int attempts() {
        return attempts;
    }

    /** Índice (a partir de 0) da fase mais longe alcançada. */
    public int bestPhase() {
        return bestPhase;
    }

    public void attempt() {
        attempts++;
    }

    /** @return true se a fase ainda não tinha sido vista */
    public boolean seePhase(int index) {
        bestPhase = Math.max(bestPhase, index);
        return seen.add("f" + index);
    }

    /** @return true se a habilidade ainda não tinha sido vista nesta fase */
    public boolean seeSkill(int phase, BossDef.SkillType type) {
        return seen.add("h" + phase + ":" + type.name());
    }

    public boolean phaseSeen(int index) {
        return seen.contains("f" + index);
    }

    public boolean skillSeen(int phase, BossDef.SkillType type) {
        return seen.contains("h" + phase + ":" + type.name());
    }

    public String seenText() {
        return String.join(",", seen);
    }
}
