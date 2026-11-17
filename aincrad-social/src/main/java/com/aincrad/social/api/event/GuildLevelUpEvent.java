package com.aincrad.social.api.event;

import org.bukkit.event.HandlerList;

/** A guilda subiu de nível. Disparado na thread principal, depois da mudança. */
public final class GuildLevelUpEvent extends GuildEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final int newLevel;

    public GuildLevelUpEvent(java.util.UUID guildId, String guildName, int newLevel) {
        super(guildId, guildName);
        this.newLevel = newLevel;
    }

    /** nível novo */
    public int newLevel() {
        return newLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
