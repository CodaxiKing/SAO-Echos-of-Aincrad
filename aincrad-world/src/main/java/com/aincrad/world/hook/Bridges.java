package com.aincrad.world.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import com.aincrad.floors.api.AincradFloorsAPI;
import com.aincrad.rpg.api.AincradRPGAPI;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * As chamadas diretas às APIs dos plugins opcionais. Cada classe interna só é carregada quando o
 * plugin dela está ligado (ver {@link Hooks}).
 */
final class Bridges {

    private Bridges() {
    }

    static final class Floors {
        private Floors() {
        }

        static int floorAt(Location l) {
            return AincradFloorsAPI.getFloorAt(l);
        }

        static boolean safeZone(Location l) {
            return AincradFloorsAPI.isSafeZone(l);
        }

        static String name(int floor) {
            return AincradFloorsAPI.getFloorName(floor);
        }
    }

    static final class Rpg {
        private Rpg() {
        }

        static int mobLevel(Entity e) {
            return AincradRPGAPI.getMobLevel(e);
        }

        static void setMobLevel(LivingEntity e, int level) {
            AincradRPGAPI.setMobLevel(e, level);
        }
    }

    static final class Eco {
        private Eco() {
        }

        static boolean withdraw(Player p, long amount, String reason) {
            return AincradEconomyAPI.has(p, amount) && AincradEconomyAPI.withdraw(p, amount, reason);
        }
    }
}
