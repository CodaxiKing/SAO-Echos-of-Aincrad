package com.aincrad.rpg.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Bônus de combate dos itens que o Minecraft não tem como atributo: crítico, dano crítico, roubo
 * de vida, esquiva e aparar. Todos em %. As contas ficam aqui, sem servidor.
 */
public final class ItemCombat {

    public static final String CRIT = "critico";
    public static final String CRIT_DAMAGE = "dano_critico";
    public static final String LIFESTEAL = "roubo_vida";
    public static final String DODGE = "esquiva";
    public static final String PARRY = "aparar";

    /** Os bônus de combate aceitos em items.yml, na ordem em que aparecem no item. */
    public static final List<String> STATS = List.of(CRIT, CRIT_DAMAGE, LIFESTEAL, DODGE, PARRY);

    /**
     * Limites e valores de config.yml → combate_itens.
     *
     * @param critBase multiplicador do golpe crítico sem nenhum "dano crítico" (1.5 = +50%)
     * @param parryCut fração do dano que um golpe aparado tira (0.5 = metade)
     */
    public record Limits(double crit, double dodge, double parry, double lifesteal, double critBase, double parryCut) {

        public static final Limits DEFAULT = new Limits(60, 40, 50, 20, 1.5, 0.5);

        public static Limits parse(ConfigurationSection s) {
            if (s == null) {
                return DEFAULT;
            }
            return new Limits(
                    clamp(s.getDouble("limites.critico", DEFAULT.crit), 0, 100),
                    clamp(s.getDouble("limites.esquiva", DEFAULT.dodge), 0, 100),
                    clamp(s.getDouble("limites.aparar", DEFAULT.parry), 0, 100),
                    clamp(s.getDouble("limites.roubo_vida", DEFAULT.lifesteal), 0, 100),
                    clamp(s.getDouble("critico_base", DEFAULT.critBase), 1, 10),
                    clamp(s.getDouble("aparar_reducao", DEFAULT.parryCut), 0, 1));
        }
    }

    private ItemCombat() {
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** O valor de um bônus na soma dos itens (0 se não houver). */
    public static double of(Map<String, Double> stats, String stat) {
        Double v = stats == null ? null : stats.get(stat);
        return v == null ? 0 : v;
    }

    /** Sorteia uma chance em %, já cortada no limite. 0 ou menos nunca acontece. */
    public static boolean happens(double percent, double limit, Random rnd) {
        double p = Math.min(percent, limit);
        return p > 0 && rnd.nextDouble() * 100 < p;
    }

    /** O dano de um golpe crítico: base do crítico mais o "dano crítico" dos itens. */
    public static double critical(double damage, double critDamagePercent, Limits limits) {
        return damage * (limits.critBase() + Math.max(0, critDamagePercent) / 100.0);
    }

    /** O dano que passa de um golpe aparado. */
    public static double parried(double damage, Limits limits) {
        return damage * (1 - limits.parryCut());
    }

    /** Quanto de vida o atacante recupera com o dano que causou. */
    public static double heal(double finalDamage, double lifestealPercent, Limits limits) {
        double p = Math.min(lifestealPercent, limits.lifesteal());
        return p <= 0 || finalDamage <= 0 ? 0 : finalDamage * p / 100.0;
    }
}
