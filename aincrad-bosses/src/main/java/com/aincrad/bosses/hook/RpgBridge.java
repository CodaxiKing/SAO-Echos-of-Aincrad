package com.aincrad.bosses.hook;

import com.aincrad.rpg.api.AincradRPGAPI;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Usa a API do AincradRPG. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class RpgBridge {

    private RpgBridge() {
    }

    static long addXp(Player p, long amount) {
        return AincradRPGAPI.addXp(p, amount, "chefe");
    }

    static void setMobLevel(LivingEntity e, int level) {
        AincradRPGAPI.setMobLevel(e, level);
    }

    static ItemStack createItem(String id, String rarity) {
        return AincradRPGAPI.createItem(id, rarity);
    }
}
