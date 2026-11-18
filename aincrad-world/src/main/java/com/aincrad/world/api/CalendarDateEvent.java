package com.aincrad.world.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Uma data marcada do calendar.yml começou (disparado uma vez por dia, na rede inteira). */
public final class CalendarDateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String id;
    private final String name;

    public CalendarDateEvent(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** O id da data no calendar.yml ("natal"). */
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
