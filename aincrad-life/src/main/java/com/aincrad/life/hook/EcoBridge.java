package com.aincrad.life.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Usa a API do AincradEconomy. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class EcoBridge {

    private EcoBridge() {
    }

    static boolean withdraw(Player p, long amount, String reason) {
        return AincradEconomyAPI.withdraw(p, amount, reason);
    }

    static String format(long amount) {
        return AincradEconomyAPI.format(amount);
    }

    static boolean mail(UUID player, ItemStack item, String reason) {
        return AincradEconomyAPI.mail(player, item, reason);
    }
}
