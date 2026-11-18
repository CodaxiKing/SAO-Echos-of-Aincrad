package com.aincrad.quests.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import com.aincrad.floors.api.AincradFloorsAPI;
import com.aincrad.rpg.api.AincradRPGAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * As chamadas diretas às APIs dos plugins opcionais. Cada classe interna só é carregada quando o
 * plugin dela está ligado (ver {@link Hooks}).
 */
final class Bridges {

    private Bridges() {
    }

    static final class Rpg {
        private Rpg() {
        }

        static int level(Player p) {
            return AincradRPGAPI.getLevel(p);
        }

        static void xp(Player p, long amount, String reason) {
            AincradRPGAPI.addXp(p, amount, reason);
        }

        static ItemStack item(String id) {
            return AincradRPGAPI.createItem(id, null);
        }

        static String mobId(Entity e) {
            return AincradRPGAPI.getMobId(e);
        }
    }

    static final class Eco {
        private Eco() {
        }

        static boolean deposit(Player p, long amount, String reason) {
            return AincradEconomyAPI.deposit(p, amount, reason);
        }
    }

    static final class Floors {
        private Floors() {
        }

        static int frontline() {
            return AincradFloorsAPI.getFrontline();
        }
    }
}
