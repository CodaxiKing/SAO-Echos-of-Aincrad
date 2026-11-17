package com.aincrad.bosses.api;

import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Uma raid foi marcada no quadro (neste servidor). */
public final class RaidScheduledEvent extends BossEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long raidId;
    private final UUID leader;
    private final long at;

    public RaidScheduledEvent(String bossId, String bossName, int floor, long raidId, UUID leader, long at) {
        super(bossId, bossName, floor, null);
        this.raidId = raidId;
        this.leader = leader;
        this.at = at;
    }

    public long getRaidId() {
        return raidId;
    }

    public UUID getLeader() {
        return leader;
    }

    /** Quando a raid começa (milissegundos). */
    public long getAt() {
        return at;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
