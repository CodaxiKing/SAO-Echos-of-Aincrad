package com.aincrad.life.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** O jogador subiu de rank numa profissão (praticante, especialista, mestre ou completo). */
public final class ProfessionRankUpEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String profession;
    private final String rank;
    private final double proficiency;

    public ProfessionRankUpEvent(Player player, String profession, String rank, double proficiency) {
        super(player);
        this.profession = profession;
        this.rank = rank;
        this.proficiency = proficiency;
    }

    /** "culinaria", "pesca", "ferreiro" ou "alquimia". */
    public String getProfession() {
        return profession;
    }

    /** "praticante", "especialista", "mestre" ou "completo". */
    public String getRank() {
        return rank;
    }

    public double getProficiency() {
        return proficiency;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
