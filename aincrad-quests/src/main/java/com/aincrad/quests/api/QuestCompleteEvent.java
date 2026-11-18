package com.aincrad.quests.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Um jogador entregou uma quest e recebeu a recompensa. */
public final class QuestCompleteEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String quest;
    private final String name;
    private final String kind;
    private final int times;

    public QuestCompleteEvent(Player player, String quest, String name, String kind, int times) {
        super(player);
        this.quest = quest;
        this.name = name;
        this.kind = kind;
        this.times = times;
    }

    /** O id da quest. */
    public String getQuest() {
        return quest;
    }

    public String getName() {
        return name;
    }

    /** "historia", "diaria" ou "semanal". */
    public String getKind() {
        return kind;
    }

    /** Quantas vezes o jogador já concluiu esta quest, contando esta. */
    public int getTimes() {
        return times;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
