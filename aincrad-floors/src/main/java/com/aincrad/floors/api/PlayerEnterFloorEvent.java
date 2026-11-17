package com.aincrad.floors.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** O jogador passou a estar em outro andar (andando, por teleporte ou ao entrar no servidor). */
public final class PlayerEnterFloorEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int from;
    private final int to;
    private final boolean firstVisit;

    public PlayerEnterFloorEvent(Player player, int from, int to, boolean firstVisit) {
        super(player);
        this.from = from;
        this.to = to;
        this.firstVisit = firstVisit;
    }

    /** Andar anterior (0 = nenhum). */
    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    /** É a primeira vez do jogador neste andar. */
    public boolean isFirstVisit() {
        return firstVisit;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
