package com.aincrad.bosses.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A tabela de ameaça de um chefe (trazida do BossAI). Sem Bukkit, para dar para testar.
 *
 * <ul>
 *   <li>O dano causado ao chefe vira ameaça.</li>
 *   <li>A ameaça cai com o tempo, mas só depois de uma carência sem o jogador acertar: quem parou
 *       de atacar perde a vez aos poucos; quem está no meio de um combo não é punido.</li>
 *   <li>Para roubar o alvo é preciso passar a ameaça do alvo atual por uma margem; sem ela o
 *       chefe ficaria trocando entre dois jogadores empatados. É isso que deixa um tanque segurar
 *       o chefe e o grupo fazer o "switch".</li>
 * </ul>
 */
public final class ThreatTable {

    private static final class Entry {
        private double threat;
        private long lastHit;

        private Entry(long now) {
            this.lastHit = now;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    /** Soma ameaça e marca a hora, para a carência da perda. */
    public void add(UUID player, double amount, long now) {
        if (amount <= 0) {
            return;
        }
        Entry e = entries.computeIfAbsent(player, id -> new Entry(now));
        e.threat += amount;
        e.lastHit = now;
    }

    public double of(UUID player) {
        Entry e = entries.get(player);
        return e == null ? 0 : e.threat;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    /**
     * A ameaça de quem está sem acertar há mais que a carência perde {@code percentPerSecond}% do
     * valor por segundo. Quem chega perto de zero sai da tabela.
     */
    public void decay(double percentPerSecond, double elapsedSeconds, double graceSeconds, long now) {
        if (percentPerSecond <= 0 || elapsedSeconds <= 0) {
            return;
        }
        long grace = (long) (graceSeconds * 1000);
        double factor = Math.pow(1 - Math.min(1, percentPerSecond / 100.0), elapsedSeconds);
        entries.values().removeIf(e -> {
            if (now - e.lastHit < grace) {
                return false;
            }
            e.threat *= factor;
            return e.threat <= 0.01;
        });
    }

    /** Tira quem não serve mais (saiu da luta, morreu, deslogou). */
    public void prune(Predicate<UUID> valid) {
        entries.keySet().removeIf(id -> !valid.test(id));
    }

    private Optional<UUID> top(Predicate<UUID> valid) {
        return entries.entrySet().stream().filter(e -> valid.test(e.getKey()))
                .max(Comparator.comparingDouble(e -> e.getValue().threat)).map(Map.Entry::getKey);
    }

    /**
     * Quem o chefe ataca: o alvo atual continua, a não ser que outro jogador tenha mais ameaça que
     * a dele vezes {@code margin}. Se o alvo atual não vale mais, o primeiro da tabela assume.
     */
    public Optional<UUID> selectTarget(UUID current, double margin, Predicate<UUID> valid) {
        Optional<UUID> top = top(valid);
        if (top.isEmpty() || current == null || !valid.test(current) || !entries.containsKey(current) || top.get().equals(current)) {
            return top;
        }
        return of(top.get()) > of(current) * Math.max(1, margin) ? top : Optional.of(current);
    }

    /** A tabela, da maior ameaça para a menor (para o painel de ameaça). */
    public Map<UUID, Double> snapshot() {
        Map<UUID, Double> out = new LinkedHashMap<>();
        entries.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, Entry>>comparingDouble(e -> e.getValue().threat).reversed())
                .forEach(e -> out.put(e.getKey(), e.getValue().threat));
        return Collections.unmodifiableMap(out);
    }

    /** A fatia de cada um no total da tabela, em % (0 a 100), na mesma ordem do {@link #snapshot()}. */
    public Map<UUID, Integer> shares() {
        Map<UUID, Double> snap = snapshot();
        double total = snap.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Integer> out = new LinkedHashMap<>();
        snap.forEach((id, v) -> out.put(id, total <= 0 ? 0 : (int) Math.round(v * 100 / total)));
        return out;
    }
}
