package com.aincrad.floors.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Um andar vai ser liberado neste servidor (chefe derrotado ou admin). Cancelar impede.
 * Os outros servidores da rede recebem só o resultado (o estado e o anúncio).
 */
public final class FloorUnlockEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int floor;
    private final List<UUID> participants;
    private boolean cancelled;

    public FloorUnlockEvent(int floor, Collection<UUID> participants) {
        this.floor = floor;
        this.participants = List.copyOf(participants);
    }

    public int getFloor() {
        return floor;
    }

    /** Quem participou da vitória (vazio quando foi um admin). */
    public List<UUID> getParticipants() {
        return participants;
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
