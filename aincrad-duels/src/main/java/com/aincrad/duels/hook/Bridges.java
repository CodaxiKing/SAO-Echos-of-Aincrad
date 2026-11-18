package com.aincrad.duels.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import com.aincrad.floors.api.AincradFloorsAPI;
import com.aincrad.reputation.api.AincradReputationAPI;
import org.bukkit.entity.Player;

import java.util.UUID;

/** As chamadas às APIs dos plugins opcionais. Cada classe só é carregada com o plugin dela ligado. */
final class Bridges {

    private Bridges() {
    }

    static final class Floors {
        private Floors() {
        }

        static void exempt(UUID a, UUID b, long until) {
            AincradFloorsAPI.exemptPvp(a, b, until);
        }
    }

    static final class Reputation {
        private Reputation() {
        }

        static void exempt(UUID a, UUID b, long until) {
            AincradReputationAPI.exemptPvp(a, b, until);
        }
    }

    static final class Eco {
        private Eco() {
        }

        static long balance(Player p) {
            return AincradEconomyAPI.getBalance(p);
        }

        static String hold(Player p, long amount) {
            return AincradEconomyAPI.hold(p, amount, "duelo:aposta");
        }

        static void release(String hold, UUID to, long amount) {
            AincradEconomyAPI.release(hold, to, amount, "duelo:premio");
        }

        static void refund(String hold) {
            AincradEconomyAPI.refund(hold, "duelo:devolucao");
        }

        static String format(long col) {
            return AincradEconomyAPI.format(col);
        }
    }
}
