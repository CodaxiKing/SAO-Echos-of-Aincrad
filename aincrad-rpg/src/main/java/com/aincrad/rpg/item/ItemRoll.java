package com.aincrad.rpg.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Sorteio dos bônus de um item: cada bônus do modelo tem uma faixa [mín, máx]; o valor sorteado
 * é multiplicado pela raridade e arredondado para uma casa decimal.
 */
public final class ItemRoll {

    /** Uma faixa de bônus do modelo (items.yml). */
    public record Range(double min, double max) {
        public Range {
            if (min > max) {
                double t = min;
                min = max;
                max = t;
            }
        }
    }

    private ItemRoll() {
    }

    public static Map<String, Double> roll(Map<String, Range> ranges, double rarityMultiplier, Random rnd) {
        Map<String, Double> out = new LinkedHashMap<>();
        ranges.forEach((stat, r) -> {
            double v = r.min() + rnd.nextDouble() * (r.max() - r.min());
            out.put(stat, Math.round(v * rarityMultiplier * 10.0) / 10.0);
        });
        return out;
    }

    /** Sorteia a raridade pelos pesos (peso 0 ou negativo nunca sai). Sem pesos: comum. */
    public static Rarity rarity(Map<Rarity, Integer> weights, Random rnd) {
        int total = 0;
        for (int w : weights.values()) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return Rarity.COMUM;
        }
        int r = rnd.nextInt(total);
        for (Rarity rar : Rarity.values()) {
            int w = Math.max(0, weights.getOrDefault(rar, 0));
            if (r < w) {
                return rar;
            }
            r -= w;
        }
        return Rarity.COMUM;
    }
}
