package com.aincrad.life.alchemy;

import java.util.List;
import java.util.Locale;

/** As contas da alquimia: a pureza da mistura e o que ela faz com o produto. Só regras, sem servidor. */
public final class AlchemyMath {

    private AlchemyMath() {
    }

    /** A pureza de um produto e o quanto ela multiplica o efeito. */
    public enum Purity {
        /** Efeito pela metade e um efeito colateral leve. */
        IMPURA(0.5),
        COMUM(1.0),
        /** 25% mais forte ou mais longo. */
        PURA(1.25),
        /** 50% mais forte e uma dose a mais no frasco. */
        PERFEITA(1.5);

        private final double factor;

        Purity(double factor) {
            this.factor = factor;
        }

        public double factor() {
            return factor;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Purity parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Os pontos de cada parte do preparo (config.yml → alquimia.pureza). */
    public record Rules(double grindMin, double grindMax, double infusionRight, double infusionWrong, double distill,
                        double bottlePerfect, double bottleGood, double bottleBad, double proficiencyPer100,
                        double proficiencyMax, double common, double pure, double perfect) {
    }

    /** A pureza inicial, depois de moer: quanto mais regular o ritmo (0 a 1), mais fino o pó. */
    public static double grind(Rules r, double score) {
        return r.grindMin() + (r.grindMax() - r.grindMin()) * Math.max(0, Math.min(1, score));
    }

    /** Um reagente adicionado na infusão: na cor certa sobe a pureza; na errada, baixa. */
    public static double infusion(Rules r, boolean rightColor) {
        return rightColor ? r.infusionRight() : -r.infusionWrong();
    }

    /** A destilação: a fração do tempo na temperatura certa (0 a 1). */
    public static double distill(Rules r, double score) {
        return r.distill() * Math.max(0, Math.min(1, score));
    }

    /** A folga (ou falta) de proficiência em relação à dificuldade da fórmula. */
    public static double proficiency(Rules r, double proficiency, double difficulty) {
        double b = (proficiency - difficulty) / 100.0 * r.proficiencyPer100();
        return Math.max(-r.proficiencyMax(), Math.min(r.proficiencyMax(), b));
    }

    /**
     * A pureza final. "Perfeita" exige os pontos e engarrafar no instante certo; e nunca passa do
     * que o frasco comporta.
     *
     * @param points        soma dos pontos do preparo (0 a 100)
     * @param perfectBottle engarrafou no brilho
     * @param cap           teto do frasco
     */
    public static Purity purity(Rules r, double points, boolean perfectBottle, Purity cap) {
        Purity p = points < r.common() ? Purity.IMPURA : points < r.pure() ? Purity.COMUM
                : points >= r.perfect() && perfectBottle ? Purity.PERFEITA : Purity.PURA;
        return p.ordinal() > cap.ordinal() ? cap : p;
    }

    /** Doses que saem: as do frasco, mais uma se a mistura é perfeita, mais as dos nós da árvore. */
    public static int doses(int flask, Purity p, int treeDoses) {
        return Math.max(1, flask + (p == Purity.PERFEITA ? 1 : 0) + Math.max(0, treeDoses));
    }

    /** A cor do líquido num instante da infusão: as cores passam uma a uma, em roda. */
    public static String colorAt(List<String> cycle, int tick, int ticksPerColor) {
        return cycle.get(Math.floorMod(tick / Math.max(1, ticksPerColor), cycle.size()));
    }
}
