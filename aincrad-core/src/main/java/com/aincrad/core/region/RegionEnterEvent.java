package com.aincrad.core.region;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** O jogador entrou numa região (andando, teleportado ou ao entrar no servidor). */
public final class RegionEnterEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Region region;

    public RegionEnterEvent(Player player, Region region) {
        super(player);
        this.region = region;
    }

    public Region region() {
        return region;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
