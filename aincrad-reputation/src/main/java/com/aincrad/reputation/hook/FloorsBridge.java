package com.aincrad.reputation.hook;

import com.aincrad.floors.api.AincradFloorsAPI;
import org.bukkit.Location;

/** Usa a API do AincradFloors. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class FloorsBridge {

    private FloorsBridge() {
    }

    static boolean safeZone(Location l) {
        return AincradFloorsAPI.isSafeZone(l);
    }

    static int floorAt(Location l) {
        return AincradFloorsAPI.getFloorAt(l);
    }

    static Location spawn(int floor) {
        return AincradFloorsAPI.getSpawn(floor);
    }
}
