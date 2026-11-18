package com.aincrad.life.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * O torneio semanal de pesca de um andar fechou e o prêmio foi pago. Dispara só no servidor que
 * fez o pagamento. O AincradReputation ouve isto para o título do vencedor.
 */
public final class FishingTournamentEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String week;
    private final int floor;
    private final UUID winner;
    private final String winnerName;
    private final double sizeCm;
    private final long prize;

    public FishingTournamentEndEvent(String week, int floor, UUID winner, String winnerName, double sizeCm, long prize) {
        this.week = week;
        this.floor = floor;
        this.winner = winner;
        this.winnerName = winnerName;
        this.sizeCm = sizeCm;
        this.prize = prize;
    }

    /** A semana, como "2026-W47". */
    public String getWeek() {
        return week;
    }

    public int getFloor() {
        return floor;
    }

    public UUID getWinner() {
        return winner;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public double getSizeCm() {
        return sizeCm;
    }

    /** Col pago ao vencedor. */
    public long getPrize() {
        return prize;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
