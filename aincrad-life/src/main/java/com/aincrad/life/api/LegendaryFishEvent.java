package com.aincrad.life.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;
import java.util.UUID;

/**
 * O Senhor do Lago de um ponto de pesca foi derrotado e o peixe lendário foi para quem o fisgou.
 * O AincradReputation ouve isto para a conquista.
 */
public final class LegendaryFishEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID fisher;
    private final String fisherName;
    private final String fish;
    private final int floor;
    private final double sizeCm;
    private final Set<UUID> hunters;

    public LegendaryFishEvent(UUID fisher, String fisherName, String fish, int floor, double sizeCm, Set<UUID> hunters) {
        this.fisher = fisher;
        this.fisherName = fisherName;
        this.fish = fish;
        this.floor = floor;
        this.sizeCm = sizeCm;
        this.hunters = Set.copyOf(hunters);
    }

    /** Quem fisgou (pode estar offline quando o chefe morre). */
    public UUID getFisher() {
        return fisher;
    }

    public String getFisherName() {
        return fisherName;
    }

    /** Id do peixe em fishing.yml. */
    public String getFish() {
        return fish;
    }

    public int getFloor() {
        return floor;
    }

    public double getSizeCm() {
        return sizeCm;
    }

    /** Os jogadores que feriram o chefe (para a caçada contratada pelo quadro). */
    public Set<UUID> getHunters() {
        return hunters;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
