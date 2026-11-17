package com.aincrad.floors.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Um andar foi liberado em algum servidor da rede e este servidor vai anunciar aos seus jogadores.
 * Diferente do {@link FloorUnlockEvent} (que só acontece no servidor onde o andar foi liberado),
 * este acontece em todos. Um plugin de efeitos pode assumir o título com {@link #setTitleHandled}.
 */
public final class FloorAnnounceEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int floor;
    private final String floorName;
    private final String participants;
    private final String guild;
    private boolean titleHandled;

    public FloorAnnounceEvent(int floor, String floorName, String participants, String guild) {
        this.floor = floor;
        this.floorName = floorName;
        this.participants = participants;
        this.guild = guild;
    }

    public int getFloor() {
        return floor;
    }

    public String getFloorName() {
        return floorName;
    }

    /** Nomes de quem participou, separados por vírgula ("" quando foi um admin). */
    public String getParticipants() {
        return participants;
    }

    /** Guilda de todos os participantes ("" se não são da mesma). */
    public String getGuild() {
        return guild;
    }

    public boolean isTitleHandled() {
        return titleHandled;
    }

    /** true = outro plugin mostra o aviso na tela; o Floors não mostra o título dele (o chat continua). */
    public void setTitleHandled(boolean handled) {
        this.titleHandled = handled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
