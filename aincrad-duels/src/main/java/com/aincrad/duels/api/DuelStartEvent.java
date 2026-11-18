package com.aincrad.duels.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Um duelo foi aceito e a contagem começou. {@code mode}: "primeiro_golpe", "metade" ou "total". */
public final class DuelStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player challenger;
    private final Player challenged;
    private final String mode;

    public DuelStartEvent(Player challenger, Player challenged, String mode) {
        this.challenger = challenger;
        this.challenged = challenged;
        this.mode = mode;
    }

    public Player getChallenger() {
        return challenger;
    }

    public Player getChallenged() {
        return challenged;
    }

    public String getMode() {
        return mode;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
