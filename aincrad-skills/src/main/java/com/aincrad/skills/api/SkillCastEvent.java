package com.aincrad.skills.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Um jogador vai usar uma Sword Skill. Cancelar impede o uso (a recarga não começa). */
public final class SkillCastEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String skill;
    private final String weapon;
    private boolean cancelled;

    public SkillCastEvent(Player player, String skill, String weapon) {
        super(player);
        this.skill = skill;
        this.weapon = weapon;
    }

    /** O id da skill ({@code corte_inclinado}). */
    public String getSkill() {
        return skill;
    }

    /** O tipo de arma ({@code espada}). */
    public String getWeapon() {
        return weapon;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
