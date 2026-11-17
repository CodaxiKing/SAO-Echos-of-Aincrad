package com.aincrad.effects.engine;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Orçamentos de desempenho por jogador: partículas por tick e entidades visíveis. */
public final class Limits {

    private final Map<UUID, long[]> particles = new HashMap<>();
    private final Map<UUID, Integer> entities = new HashMap<>();
    private int maxParticles;
    private int maxEntities;

    public Limits(int maxParticles, int maxEntities) {
        configure(maxParticles, maxEntities);
    }

    public void configure(int maxParticles, int maxEntities) {
        this.maxParticles = maxParticles;
        this.maxEntities = maxEntities;
    }

    /** Reserva até {@code wanted} partículas deste tick; devolve quantas podem ser enviadas. */
    public int takeParticles(Player p, int wanted) {
        return takeParticles(p.getUniqueId(), Bukkit.getCurrentTick(), wanted);
    }

    int takeParticles(UUID id, long tick, int wanted) {
        long[] slot = particles.computeIfAbsent(id, k -> new long[]{tick, 0});
        if (slot[0] != tick) {
            slot[0] = tick;
            slot[1] = 0;
        }
        int allowed = (int) Math.max(0, Math.min(wanted, maxParticles - slot[1]));
        slot[1] += allowed;
        return allowed;
    }

    /** Tenta reservar uma entidade visível para o jogador. */
    public boolean takeEntity(Player p) {
        return takeEntity(p.getUniqueId());
    }

    boolean takeEntity(UUID id) {
        int n = entities.getOrDefault(id, 0);
        if (n >= maxEntities) {
            return false;
        }
        entities.put(id, n + 1);
        return true;
    }

    public void releaseEntity(UUID id) {
        entities.computeIfPresent(id, (k, n) -> n <= 1 ? null : n - 1);
    }

    public void forget(UUID id) {
        particles.remove(id);
        entities.remove(id);
    }
}
