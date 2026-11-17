package com.aincrad.social.api.event;

import org.bukkit.event.HandlerList;

/** Uma guilda foi criada. Disparado na thread principal, depois da mudança. */
public final class GuildCreateEvent extends GuildEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID founder;

    public GuildCreateEvent(java.util.UUID guildId, String guildName, java.util.UUID founder) {
        super(guildId, guildName);
        this.founder = founder;
    }

    /** fundador */
    public java.util.UUID founder() {
        return founder;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
