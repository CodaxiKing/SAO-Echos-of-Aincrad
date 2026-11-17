package com.aincrad.economy.api;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Antes de qualquer mudança de saldo feita neste servidor. {@code amount} positivo = entrada,
 * negativo = saída. {@code reason}: "mob", "loja:compra", "pagar", "troca", "leilao", "admin",
 * "vault" ou o que o plugin passou na API. Cancelar faz a operação devolver {@code false}.
 */
public final class TransactionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final long amount;
    private final String reason;
    private boolean cancelled;

    public TransactionEvent(UUID player, long amount, String reason) {
        this.player = player;
        this.amount = amount;
        this.reason = reason;
    }

    public UUID getPlayerId() {
        return player;
    }

    public long getAmount() {
        return amount;
    }

    public boolean isDeposit() {
        return amount > 0;
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
