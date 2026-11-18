package com.aincrad.skills.hook;

import com.aincrad.rpg.api.AincradRPGAPI;
import org.bukkit.entity.Player;

/** Usa a API do AincradRPG. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class RpgBridge {

    private RpgBridge() {
    }

    static int level(Player p) {
        return AincradRPGAPI.getLevel(p);
    }
}
