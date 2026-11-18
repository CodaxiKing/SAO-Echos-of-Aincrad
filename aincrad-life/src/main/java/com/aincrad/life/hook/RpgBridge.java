package com.aincrad.life.hook;

import com.aincrad.rpg.api.AincradRPGAPI;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/** Usa a API do AincradRPG. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class RpgBridge {

    private RpgBridge() {
    }

    static int level(org.bukkit.entity.Player p) {
        return AincradRPGAPI.getLevel(p);
    }

    static void setMobLevel(LivingEntity e, int level) {
        AincradRPGAPI.setMobLevel(e, level);
    }

    static ItemStack createItem(String id, String rarity) {
        return AincradRPGAPI.createItem(id, rarity);
    }
}
