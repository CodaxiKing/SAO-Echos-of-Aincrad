package com.aincrad.items.hook;

import com.aincrad.floors.api.AincradFloorsAPI;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/** Usa a API do AincradFloors. Só é carregada quando ele está ligado (ver {@link Hooks#hasFloors()}). */
public final class FloorsBridge {

    private FloorsBridge() {
    }

    /** Andares em que o jogador pode entrar: número → nome. */
    public static Map<Integer, String> destinations(Player p) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (int n = 1; n <= AincradFloorsAPI.getFloorCount(); n++) {
            if (AincradFloorsAPI.canEnter(p, n)) {
                out.put(n, AincradFloorsAPI.getFloorName(n));
            }
        }
        return out;
    }

    public static int current(Player p) {
        return AincradFloorsAPI.getFloor(p);
    }

    /** Leva ao ponto de chegada do andar (a cidade). @return true se o teleporte começou */
    public static boolean teleport(Player p, int floor) {
        return AincradFloorsAPI.teleport(p, floor);
    }
}
