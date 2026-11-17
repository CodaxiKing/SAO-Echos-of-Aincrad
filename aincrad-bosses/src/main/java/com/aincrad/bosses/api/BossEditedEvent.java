package com.aincrad.bosses.api;

import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;

/** Um admin mudou um chefe pelo editor do jogo. O YAML já foi gravado; vale na próxima luta. */
public final class BossEditedEvent extends BossEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender editor;
    private final String field;
    private final String value;

    public BossEditedEvent(String bossId, String bossName, int floor, CommandSender editor, String field, String value) {
        super(bossId, bossName, floor, null);
        this.editor = editor;
        this.field = field;
        this.value = value;
    }

    public CommandSender getEditor() {
        return editor;
    }

    /** O campo do YAML que mudou (ex.: "vida", "fases.2.dano_x", "recompensas.itens"). */
    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
