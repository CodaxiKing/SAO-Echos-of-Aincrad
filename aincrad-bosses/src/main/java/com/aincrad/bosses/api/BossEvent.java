package com.aincrad.bosses.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;

/** Base dos eventos de chefe: qual chefe (id do YAML), o nome, o andar e a entidade. */
public abstract class BossEvent extends Event {

    private final String bossId;
    private final String bossName;
    private final int floor;
    private final LivingEntity entity;

    protected BossEvent(String bossId, String bossName, int floor, LivingEntity entity) {
        this.bossId = bossId;
        this.bossName = bossName;
        this.floor = floor;
        this.entity = entity;
    }

    public String getBossId() {
        return bossId;
    }

    public String getBossName() {
        return bossName;
    }

    public int getFloor() {
        return floor;
    }

    /** A entidade do chefe (pode já ter sido removida nos eventos de fim de luta). */
    public LivingEntity getEntity() {
        return entity;
    }
}
