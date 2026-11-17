package com.aincrad.social.api.event;

import org.bukkit.event.HandlerList;

/** Um membro mudou de cargo. Disparado na thread principal, depois da mudança. */
public final class GuildRankChangeEvent extends GuildEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID player;
    private final String oldRank;
    private final String newRank;

    public GuildRankChangeEvent(java.util.UUID guildId, String guildName, java.util.UUID player, String oldRank, String newRank) {
        super(guildId, guildName);
        this.player = player;
        this.oldRank = oldRank;
        this.newRank = newRank;
    }

    /** o membro */
    public java.util.UUID player() {
        return player;
    }

    /** cargo antigo (GuildRank.name()) */
    public String oldRank() {
        return oldRank;
    }

    /** cargo novo */
    public String newRank() {
        return newRank;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
