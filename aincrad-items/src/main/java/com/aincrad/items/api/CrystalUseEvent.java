package com.aincrad.items.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Um jogador vai usar um cristal (depois de conferidas a recarga e as áreas anti-cristal, antes
 * do efeito). Cancelar impede o uso: o cristal não é gasto e a recarga não conta.
 */
public final class CrystalUseEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String crystalId;
    private final String type;
    private final Player target;
    private boolean cancelled;

    public CrystalUseEvent(Player player, String crystalId, String type, Player target) {
        super(player);
        this.crystalId = crystalId;
        this.type = type;
        this.target = target;
    }

    /** Id do cristal em crystals.yml. */
    public String getCrystalId() {
        return crystalId;
    }

    /** "cura", "antidoto", "teleporte", "ressurreicao" ou "gravacao". */
    public String getType() {
        return type;
    }

    /** Em quem o cristal é usado (na ressurreição, o jogador salvo; nos outros, quem usa). */
    public Player getTarget() {
        return target;
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
