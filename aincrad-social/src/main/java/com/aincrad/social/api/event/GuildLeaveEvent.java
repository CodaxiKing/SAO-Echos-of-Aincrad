package com.aincrad.social.api.event;

import org.bukkit.event.HandlerList;

/** Um jogador saiu de uma guilda (saiu, foi expulso ou a guilda acabou). Disparado na thread principal, depois da mudança. */
public final class GuildLeaveEvent extends GuildEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final java.util.UUID player;
    private final String reason;

    public GuildLeaveEvent(java.util.UUID guildId, String guildName, java.util.UUID player, String reason) {
        super(guildId, guildName);
        this.player = player;
        this.reason = reason;
    }

    /** quem saiu */
    public java.util.UUID player() {
        return player;
    }

    /** saiu, expulso ou desfeita */
    public String reason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
