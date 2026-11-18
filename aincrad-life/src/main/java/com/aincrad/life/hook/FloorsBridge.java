package com.aincrad.life.hook;

import com.aincrad.floors.api.AincradFloorsAPI;
import org.bukkit.entity.Player;

/** Usa a API do AincradFloors. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class FloorsBridge {

    private FloorsBridge() {
    }

    static boolean safeZone(org.bukkit.Location l) {
        return AincradFloorsAPI.isSafeZone(l);
    }

    static int floorOf(Player p) {
        return AincradFloorsAPI.getFloor(p);
    }

    static boolean teleport(Player p, int floor) {
        return AincradFloorsAPI.teleport(p, floor);
    }
}
