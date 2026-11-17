package com.aincrad.rpg.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Antes de o jogador ganhar XP do RPG. Dá para mudar a quantidade (bônus de party, evento de
 * fim de semana) ou cancelar. {@code reason}: "mob", "quest", "admin", "api"... ou o que o plugin
 * que chamou {@link AincradRPGAPI#addXp} passou.
 */
public final class XpGainEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String reason;
    private long amount;
    private boolean cancelled;

    public XpGainEvent(Player player, long amount, String reason) {
        super(player);
        this.amount = amount;
        this.reason = reason;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = Math.max(0, amount);
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
