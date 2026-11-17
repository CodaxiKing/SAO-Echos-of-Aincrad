package com.aincrad.floors.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** O jogador vai ser levado a um andar por um Teleport Gate ou pelo /andar. Cancelar impede. */
public final class GateTeleportEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int floor;
    private final boolean otherServer;
    private boolean cancelled;

    public GateTeleportEvent(Player player, int floor, boolean otherServer) {
        super(player);
        this.floor = floor;
        this.otherServer = otherServer;
    }

    public int getFloor() {
        return floor;
    }

    /** O andar fica em outro servidor da rede (o jogador vai trocar de servidor). */
    public boolean isOtherServer() {
        return otherServer;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
