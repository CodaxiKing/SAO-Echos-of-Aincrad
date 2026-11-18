package com.aincrad.reputation.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Um jogador vermelho foi derrotado por outro jogador e vai para a prisão. */
public final class PlayerJailEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player captor;
    private final long until;
    private final int kills;

    public PlayerJailEvent(Player jailed, Player captor, long until, int kills) {
        super(jailed);
        this.captor = captor;
        this.until = until;
        this.kills = kills;
    }

    /** Quem o derrotou. */
    public Player getCaptor() {
        return captor;
    }

    /** Até quando fica preso (milissegundos). */
    public long getUntil() {
        return until;
    }

    /** As mortes que o preso causou. */
    public int getKills() {
        return kills;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
