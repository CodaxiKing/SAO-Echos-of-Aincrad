package com.aincrad.bosses.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import org.bukkit.entity.Player;

/** Usa a API do AincradEconomy. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class EcoBridge {

    private EcoBridge() {
    }

    static boolean withdraw(Player p, long amount, String reason) {
        return AincradEconomyAPI.has(p, amount) && AincradEconomyAPI.withdraw(p, amount, reason);
    }

    static boolean deposit(Player p, long amount) {
        return AincradEconomyAPI.deposit(p, amount, "chefe");
    }
}
