package com.aincrad.life.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Dois jogadores se casaram ou se divorciaram. Dispara no servidor onde aconteceu. */
public final class MarriageEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID first;
    private final UUID second;
    private final boolean married;

    public MarriageEvent(UUID first, UUID second, boolean married) {
        this.first = first;
        this.second = second;
        this.married = married;
    }

    /** Quem pediu (ou quem pediu o divórcio). */
    public UUID getFirst() {
        return first;
    }

    public UUID getSecond() {
        return second;
    }

    /** true = casamento; false = divórcio. */
    public boolean isMarried() {
        return married;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
