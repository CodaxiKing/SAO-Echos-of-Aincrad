package com.aincrad.bosses.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registro de dano de uma luta e a divisão das recompensas. Só contas. */
public final class DamageLedger {

    private final Map<UUID, Double> damage = new LinkedHashMap<>();
    private UUID lastAttacker;

    /** Soma o dano (já limitado à vida que o chefe tinha). */
    public void add(UUID player, double amount) {
        if (amount > 0) {
            damage.merge(player, amount, Double::sum);
            lastAttacker = player;
        }
    }

    public double of(UUID player) {
        return damage.getOrDefault(player, 0.0);
    }

    public double total() {
        return damage.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Quem deu o último golpe registrado. */
    public UUID lastAttacker() {
        return lastAttacker;
    }

    public Map<UUID, Double> all() {
        return Map.copyOf(damage);
    }

    /** Quem fez pelo menos {@code minPercent}% da vida do chefe, do maior dano para o menor. */
    public List<UUID> eligible(double minPercent, double bossHealth) {
        double min = bossHealth * minPercent / 100.0;
        List<UUID> out = new ArrayList<>();
        damage.entrySet().stream()
                .filter(e -> e.getValue() >= min)
                .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> out.add(e.getKey()));
        return out;
    }

    /**
     * Divide as unidades de cada item em rodízio, começando por quem causou mais dano. O rodízio
     * continua de um item para o outro, para o primeiro não ficar com tudo.
     *
     * @return jogador → (item → quantidade)
     */
    public static Map<UUID, Map<String, Integer>> split(List<BossDef.Drop> drops, List<UUID> eligible) {
        Map<UUID, Map<String, Integer>> out = new LinkedHashMap<>();
        if (eligible.isEmpty()) {
            return out;
        }
        int turn = 0;
        for (BossDef.Drop d : drops) {
            for (int i = 0; i < d.amount(); i++) {
                UUID who = eligible.get(turn++ % eligible.size());
                out.computeIfAbsent(who, k -> new LinkedHashMap<>()).merge(d.item(), 1, Integer::sum);
            }
        }
        return out;
    }
}
