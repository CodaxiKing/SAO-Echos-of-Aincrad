package com.aincrad.life.smith;

import com.aincrad.life.smith.ForgeRegistry.Mat;
import com.aincrad.life.smith.ForgeRegistry.Rarity;
import com.aincrad.life.smith.ForgeRegistry.Role;
import com.aincrad.life.smith.ForgeRegistry.Type;
import com.aincrad.life.station.StationPanel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** As contas da forja, do reparo e do aprimoramento. Só regras, sem servidor. */
public final class ForgeMath {

    private ForgeMath() {
    }

    // ------------------------------------------------------------------ texto ↔ mapa (dados gravados no item)

    static String encode(Map<String, ? extends Number> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ";").append(k).append('=').append(v));
        return sb.toString();
    }

    static Map<String, Double> decode(String raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                try {
                    out.put(part.substring(0, eq), Double.parseDouble(part.substring(eq + 1)));
                } catch (NumberFormatException e) {
                    // pedaço estragado: ignora
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ forja

    /** O material base de mais rank entre os encaixes base (lâmina, cabeça, placa). */
    public static int rank(Type type, List<Mat> placed) {
        int rank = 1;
        for (int i = 0; i < type.slots().size(); i++) {
            if (type.slots().get(i).primary() && placed.get(i) != null) {
                rank = Math.max(rank, placed.get(i).rank());
            }
        }
        return rank;
    }

    /** O teto de raridade: o menor entre os materiais base. */
    public static Rarity cap(Type type, List<Mat> placed) {
        Rarity cap = Rarity.LENDARIO;
        for (int i = 0; i < type.slots().size(); i++) {
            Mat m = placed.get(i);
            if (type.slots().get(i).primary() && m != null && m.cap().ordinal() < cap.ordinal()) {
                cap = m.cap();
            }
        }
        return cap;
    }

    /** O atributo dominante: o do material que está no núcleo (null: sem núcleo, ou material sem atributo). */
    public static String dominant(Type type, List<Mat> placed) {
        for (int i = 0; i < type.slots().size(); i++) {
            if (type.slots().get(i) == Role.NUCLEO && placed.get(i) != null) {
                return placed.get(i).core();
            }
        }
        return null;
    }

    /**
     * Batidas necessárias: a base do tipo, mais 25% por rank do material base acima do primeiro.
     *
     * @param modifier nós da árvore (ex.: -0.1 = 10% menos batidas)
     */
    public static int hits(Type type, int rank, double modifier) {
        return (int) Math.max(3, Math.round(type.hits() * (1 + 0.25 * (rank - 1)) * Math.max(0.3, 1 + modifier)));
    }

    /** O anel fecha mais rápido com material de rank maior. */
    public static int ringTicks(int base, int rank, int perRank, int min) {
        return Math.max(min, base - perRank * (rank - 1));
    }

    /** Estado da peça durante as batidas: qualidade (0 a 100) e trincas. */
    public static final class Work {
        private final int needed;
        private final int maxCracks;
        private double quality = 50;
        private int done;
        private int cracks;

        /**
         * @param needed    batidas necessárias
         * @param maxCracks trincas que quebram a peça
         */
        public Work(int needed, int maxCracks) {
            this.needed = Math.max(1, needed);
            this.maxCracks = Math.max(1, maxCracks);
        }

        /**
         * Uma batida. Perfeita sobe bastante a qualidade, boa sobe pouco, ruim baixa e soma uma
         * trinca. Fora da faixa de calor, toda batida conta como ruim.
         *
         * @return como a batida contou
         */
        public StationPanel.Hit strike(StationPanel.Hit hit, boolean heatOk) {
            StationPanel.Hit real = heatOk ? hit : StationPanel.Hit.RUIM;
            double step = 50.0 / needed;
            switch (real) {
                case PERFEITA -> quality += step;
                case BOA -> quality += step / 3;
                case RUIM -> {
                    quality -= step;
                    cracks++;
                }
            }
            quality = Math.max(0, Math.min(100, quality));
            done++;
            return real;
        }

        /** Perde qualidade (ex.: têmpera atrasada). */
        public void lose(double points) {
            quality = Math.max(0, quality - points);
        }

        public double quality() {
            return quality;
        }

        public int done() {
            return done;
        }

        public int needed() {
            return needed;
        }

        public int cracks() {
            return cracks;
        }

        public int maxCracks() {
            return maxCracks;
        }

        public boolean broken() {
            return cracks >= maxCracks;
        }

        public boolean finished() {
            return done >= needed || broken();
        }
    }

    /**
     * A raridade pela qualidade final, sem nunca passar do teto do material base.
     *
     * @param thresholds qualidade mínima para incomum, raro, épico e lendário
     */
    public static Rarity rarity(double quality, double[] thresholds, Rarity cap) {
        int index = 0;
        for (int i = 0; i < thresholds.length && i < Rarity.values().length - 1; i++) {
            if (quality >= thresholds[i]) {
                index = i + 1;
            }
        }
        return Rarity.values()[Math.min(index, cap.ordinal())];
    }

    /** Tentativas de aprimoramento do item: a base, mais as extras de uma peça muito bem feita. */
    public static int tries(int base, double quality, double extraFrom, int extra, int treeBonus) {
        return Math.max(0, base + (quality >= extraFrom ? extra : 0) + Math.max(0, treeBonus));
    }

    /**
     * Os bônus do item: a soma do que cada material dá no encaixe em que está, vezes a qualidade
     * (de 60% do valor com qualidade 0 a 140% com qualidade 100).
     *
     * @param dominant      bônus de cada atributo dominante, por rank do material do núcleo
     * @param dominantBonus fração a mais no atributo dominante (nós da árvore)
     * @param extra         bônus fixo a mais (arma nomeada), ou null
     */
    public static Map<String, Double> stats(Type type, List<Mat> placed, Map<String, Map<String, Double>> dominant,
                                            double quality, double dominantBonus, Map<String, Double> extra) {
        Map<String, Double> sum = new LinkedHashMap<>();
        for (int i = 0; i < type.slots().size(); i++) {
            Mat m = placed.get(i);
            if (m == null) {
                continue;
            }
            Role role = type.slots().get(i);
            if (role.primary()) {
                add(sum, type.armor() ? m.armor() : m.weapon(), 1);
            } else if (role == Role.NUCLEO) {
                Map<String, Double> d = m.core() == null ? null : dominant.get(m.core());
                if (d != null) {
                    add(sum, d, m.rank() * (1 + Math.max(0, dominantBonus)));
                }
            } else if (role == Role.CATALISADOR) {
                add(sum, m.catalyst(), 1);
            } else {
                add(sum, m.support(), 1);
            }
        }
        if (extra != null) {
            add(sum, extra, 1);
        }
        double factor = 0.6 + 0.8 * Math.max(0, Math.min(100, quality)) / 100.0;
        sum.replaceAll((k, v) -> Math.round(v * factor * 10) / 10.0);
        sum.values().removeIf(v -> v == 0);
        return sum;
    }

    private static void add(Map<String, Double> sum, Map<String, Double> part, double times) {
        part.forEach((k, v) -> sum.merge(k, v * times, Double::sum));
    }

    // ------------------------------------------------------------------ reparo

    /** O custo de um reparo: Col e unidades do material do item. */
    public record RepairCost(long col, int units) {
    }

    /**
     * Custo proporcional ao desgaste. A proficiência dá desconto no Col (até {@code maxDiscount}
     * com 1000), somado ao dos nós da árvore.
     *
     * @param wear          fração gasta da durabilidade (0 a 1)
     * @param fullCol       Col de um reparo completo
     * @param fullUnits     unidades de material de um reparo completo
     * @param treeDiscount  desconto no Col dos nós (0.15 = 15%)
     * @param unitsModifier nós que mexem no material (-0.2 = 20% menos)
     */
    public static RepairCost repair(double wear, long fullCol, int fullUnits, double proficiency, double maxDiscount,
                                    double treeDiscount, double unitsModifier) {
        double w = Math.max(0, Math.min(1, wear));
        if (w <= 0) {
            return new RepairCost(0, 0);
        }
        double discount = Math.min(0.9, maxDiscount * Math.max(0, Math.min(1000, proficiency)) / 1000.0 + Math.max(0, treeDiscount));
        long col = (long) Math.ceil(fullCol * w * (1 - discount));
        int units = (int) Math.max(1, Math.ceil(fullUnits * w * Math.max(0.1, 1 + unitsModifier)));
        return new RepairCost(col, units);
    }

    // ------------------------------------------------------------------ aprimoramento

    /** Como uma tentativa de aprimoramento terminou. */
    public enum Outcome {
        /** +1 no atributo escolhido. */
        SUCESSO,
        /** Falha: só os materiais se perdem. */
        SO_MATERIAIS,
        /** Falha: o atributo cai 1. */
        CAIU,
        /** Falha: o +1 vai para outro atributo. */
        DESVIOU
    }

    /**
     * Chance de sucesso: cresce com a proficiência, com os materiais extras e com as batidas
     * perfeitas; cai a cada nível que o atributo já tem. Nunca passa do teto.
     */
    public static double upgradeChance(double base, double atMaster, double proficiency, int level, double perLevel,
                                       int materials, double perMaterial, int perfectHits, double perHit, double bonus, double cap) {
        double c = base + atMaster * Math.max(0, Math.min(1000, proficiency)) / 1000.0 - perLevel * level
                + perMaterial * materials + perHit * perfectHits + bonus;
        return Math.max(0.05, Math.min(cap, c));
    }

    /**
     * Sorteia o resultado.
     *
     * @param roll     sorteio do sucesso, de 0 a 1
     * @param failRoll sorteio do tipo de falha, de 0 a 1 (um terço para cada)
     * @param level    nível atual do atributo (em 0 ele não tem como cair)
     * @param noDrop   nó "falha sem queda": o atributo nunca cai
     */
    public static Outcome upgrade(double chance, double roll, double failRoll, int level, boolean noDrop) {
        if (roll < chance) {
            return Outcome.SUCESSO;
        }
        if (failRoll < 1 / 3.0) {
            return Outcome.SO_MATERIAIS;
        }
        if (failRoll < 2 / 3.0) {
            return level > 0 && !noDrop ? Outcome.CAIU : Outcome.SO_MATERIAIS;
        }
        return Outcome.DESVIOU;
    }
}
