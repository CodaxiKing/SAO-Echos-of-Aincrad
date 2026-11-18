package com.aincrad.reputation.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Usa a API do AincradEconomy (a custódia de Col). Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class EcoBridge {

    private EcoBridge() {
    }

    static String hold(Player p, long amount, String reason) {
        return AincradEconomyAPI.hold(p, amount, reason);
    }

    static CompletableFuture<Long> held(String hold) {
        return AincradEconomyAPI.held(hold);
    }

    static CompletableFuture<Boolean> release(String hold, UUID to, long amount, String reason) {
        return AincradEconomyAPI.release(hold, to, amount, reason);
    }

    static CompletableFuture<Long> refund(String hold, String reason) {
        return AincradEconomyAPI.refund(hold, reason);
    }
}
