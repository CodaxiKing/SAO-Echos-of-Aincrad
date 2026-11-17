package com.aincrad.bosses.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.List;

/** A luta começou: a porta fechou e o chefe apareceu (parado durante a introdução). */
public final class BossFightStartEvent extends BossEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final List<Player> players;
    private final int introSeconds;

    public BossFightStartEvent(String bossId, String bossName, int floor, LivingEntity entity, List<Player> players, int introSeconds) {
        super(bossId, bossName, floor, entity);
        this.players = List.copyOf(players);
        this.introSeconds = introSeconds;
    }

    /** Quem está na sala. */
    public List<Player> getPlayers() {
        return players;
    }

    /** Segundos em que o chefe fica parado e invulnerável. */
    public int getIntroSeconds() {
        return introSeconds;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
