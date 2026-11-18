package com.aincrad.skills.hook;

import com.aincrad.floors.api.AincradFloorsAPI;
import org.bukkit.Location;

/** Usa a API do AincradFloors. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class FloorsBridge {

    private FloorsBridge() {
    }

    static boolean safeZone(Location l) {
        return AincradFloorsAPI.isSafeZone(l);
    }
}
