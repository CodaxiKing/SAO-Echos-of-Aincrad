package com.aincrad.bosses.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** Um jogador causou dano ao chefe (depois de aplicado). */
public final class BossDamageEvent extends BossEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final double damage;
    private final boolean critical;

    public BossDamageEvent(String bossId, String bossName, int floor, LivingEntity entity, Player player, double damage, boolean critical) {
        super(bossId, bossName, floor, entity);
        this.player = player;
        this.damage = damage;
        this.critical = critical;
    }

    public Player getPlayer() {
        return player;
    }

    public double getDamage() {
        return damage;
    }

    /** Golpe crítico do Minecraft (caindo, sem correr). */
    public boolean isCritical() {
        return critical;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
