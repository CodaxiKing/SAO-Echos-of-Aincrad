package com.aincrad.bosses.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;

/** A luta acabou sem vitória (a vitória é o {@link BossDefeatEvent}). A porta já abriu. */
public final class BossFightEndEvent extends BossEvent {

    /** Por que acabou. */
    public enum Reason {
        /** Todos na sala morreram ou saíram. */
        DERROTA,
        /** O tempo limite acabou. */
        TEMPO,
        /** Um admin encerrou (ou o plugin desligou). */
        RESET
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;

    public BossFightEndEvent(String bossId, String bossName, int floor, LivingEntity entity, Reason reason) {
        super(bossId, bossName, floor, entity);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
