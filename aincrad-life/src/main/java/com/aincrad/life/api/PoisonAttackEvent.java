package com.aincrad.life.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Um jogador acertou outro com uma arma envenenada (veneno de alquimia). O AincradReputation
 * ouve isto: conta como ataque a jogador.
 */
public final class PoisonAttackEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player attacker;
    private final Player victim;
    private final String poison;

    public PoisonAttackEvent(Player attacker, Player victim, String poison) {
        this.attacker = attacker;
        this.victim = victim;
        this.poison = poison;
    }

    public Player getAttacker() {
        return attacker;
    }

    public Player getVictim() {
        return victim;
    }

    /** Id da fórmula do veneno (formulas.yml). */
    public String getPoison() {
        return poison;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
