package com.aincrad.social.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Um duelo acabou. {@code winner} e {@code loser} são null no empate (tempo esgotado com a mesma
 * vida). {@code reason}: "golpe", "metade", "morte", "tempo", "fugiu", "saiu" ou "desistiu".
 */
public final class DuelEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID first;
    private final UUID second;
    private final UUID winner;
    private final String mode;
    private final String reason;

    public DuelEndEvent(UUID first, UUID second, UUID winner, String mode, String reason) {
        this.first = first;
        this.second = second;
        this.winner = winner;
        this.mode = mode;
        this.reason = reason;
    }

    /** Quem desafiou. */
    public UUID getChallenger() {
        return first;
    }

    /** Quem aceitou. */
    public UUID getChallenged() {
        return second;
    }

    public UUID getWinner() {
        return winner;
    }

    public UUID getLoser() {
        return winner == null ? null : winner.equals(first) ? second : first;
    }

    public String getMode() {
        return mode;
    }

    public String getReason() {
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
