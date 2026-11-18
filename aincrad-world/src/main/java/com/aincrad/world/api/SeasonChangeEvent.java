package com.aincrad.world.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A estação de Aincrad mudou. */
public final class SeasonChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String from;
    private final String to;

    public SeasonChangeEvent(String from, String to) {
        this.from = from;
        this.to = to;
    }

    /** A estação que acabou (null na primeira vez que o plugin liga). */
    public String getFrom() {
        return from;
    }

    /** A estação que começou: "primavera", "verao", "outono", "inverno" (ou as do calendar.yml). */
    public String getTo() {
        return to;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
