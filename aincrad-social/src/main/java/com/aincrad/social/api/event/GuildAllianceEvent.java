package com.aincrad.social.api.event;

import org.bukkit.event.HandlerList;

/** Duas guildas se aliaram ou desfizeram a aliança. Disparado na thread principal, depois da mudança. */
public final class GuildAllianceEvent extends GuildEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID otherGuild;
    private final boolean formed;

    public GuildAllianceEvent(java.util.UUID guildId, String guildName, java.util.UUID otherGuild, boolean formed) {
        super(guildId, guildName);
        this.otherGuild = otherGuild;
        this.formed = formed;
    }

    /** a outra guilda */
    public java.util.UUID otherGuild() {
        return otherGuild;
    }

    /** true = aliança formada, false = desfeita */
    public boolean formed() {
        return formed;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
