package com.aincrad.life.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Um jogador concluiu com sucesso uma ação de profissão: cozinhou um prato, pescou um peixe,
 * forjou ou melhorou um item, fez uma poção. O AincradQuests ouve isto para os objetivos de
 * pescar e cozinhar.
 */
public final class ProfessionPracticeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String profession;
    private final double difficulty;

    public ProfessionPracticeEvent(Player player, String profession, double difficulty) {
        this.player = player;
        this.profession = profession;
        this.difficulty = difficulty;
    }

    public Player getPlayer() {
        return player;
    }

    /** "culinaria", "pesca", "ferreiro" ou "alquimia". */
    public String getProfession() {
        return profession;
    }

    /** A dificuldade do que foi feito (0 a 1000). */
    public double getDifficulty() {
        return difficulty;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
