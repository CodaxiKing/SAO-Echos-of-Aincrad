package com.aincrad.life.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Um jogador domou um familiar. O AincradReputation ouve isto para a conquista. */
public final class PetTameEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String species;

    public PetTameEvent(Player player, String species) {
        this.player = player;
        this.species = species;
    }

    public Player getPlayer() {
        return player;
    }

    /** Id da espécie em pets.yml. */
    public String getSpecies() {
        return species;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
