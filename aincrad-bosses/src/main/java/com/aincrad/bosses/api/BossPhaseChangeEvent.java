package com.aincrad.bosses.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;

/** O chefe entrou numa fase nova. */
public final class BossPhaseChangeEvent extends BossEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int phase;
    private final String phaseName;

    public BossPhaseChangeEvent(String bossId, String bossName, int floor, LivingEntity entity, int phase, String phaseName) {
        super(bossId, bossName, floor, entity);
        this.phase = phase;
        this.phaseName = phaseName;
    }

    /** Número da fase, começando em 1. */
    public int getPhase() {
        return phase;
    }

    public String getPhaseName() {
        return phaseName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
