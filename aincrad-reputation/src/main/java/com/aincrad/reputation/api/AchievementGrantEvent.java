package com.aincrad.reputation.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Um jogador ganhou uma conquista. O AincradAdmin ouve isto para anunciar as raras no Discord. */
public final class AchievementGrantEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String achievement;
    private final String name;
    private final String title;
    private final boolean announced;

    public AchievementGrantEvent(Player player, String achievement, String name, String title, boolean announced) {
        this.player = player;
        this.achievement = achievement;
        this.name = name;
        this.title = title;
        this.announced = announced;
    }

    public Player getPlayer() {
        return player;
    }

    /** A chave da conquista (a de achievements.yml, com "_andar" no fim quando ela é por andar). */
    public String getAchievement() {
        return achievement;
    }

    public String getName() {
        return name;
    }

    /** O título que ela liberou, ou null. */
    public String getTitle() {
        return title;
    }

    /** Foi anunciada para o servidor (conquista marcada com "anunciar: true")? */
    public boolean isAnnounced() {
        return announced;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
