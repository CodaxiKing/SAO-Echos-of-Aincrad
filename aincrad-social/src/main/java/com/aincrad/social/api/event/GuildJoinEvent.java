package com.aincrad.social.api.event;

import org.bukkit.event.HandlerList;

/** Um jogador entrou numa guilda (como recruta). Disparado na thread principal, depois da mudança. */
public final class GuildJoinEvent extends GuildEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID player;

    public GuildJoinEvent(java.util.UUID guildId, String guildName, java.util.UUID player) {
        super(guildId, guildName);
        this.player = player;
    }

    /** quem entrou */
    public java.util.UUID player() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
