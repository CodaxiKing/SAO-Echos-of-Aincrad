package com.aincrad.bosses.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import org.bukkit.entity.Player;

/** Usa a API do AincradEconomy. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class EcoBridge {

    private EcoBridge() {
    }

    static boolean deposit(Player p, long amount) {
        return AincradEconomyAPI.deposit(p, amount, "chefe");
    }
}
