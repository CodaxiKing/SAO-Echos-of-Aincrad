package com.aincrad.life.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Usa a API do AincradEconomy. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class EcoBridge {

    private EcoBridge() {
    }

    static boolean withdraw(Player p, long amount, String reason) {
        return AincradEconomyAPI.withdraw(p, amount, reason);
    }

    static boolean deposit(UUID player, long amount, String reason) {
        return AincradEconomyAPI.deposit(player, amount, reason);
    }

    static String hold(Player p, long amount, String reason) {
        return AincradEconomyAPI.hold(p, amount, reason);
    }

    static CompletableFuture<Boolean> release(String hold, UUID to, long amount, String reason) {
        return AincradEconomyAPI.release(hold, to, amount, reason);
    }

    static CompletableFuture<Long> refund(String hold, String reason) {
        return AincradEconomyAPI.refund(hold, reason);
    }

    static CompletableFuture<Boolean> burn(String hold, long amount) {
        return AincradEconomyAPI.burn(hold, amount);
    }

    static CompletableFuture<Long> burn(String hold) {
        return AincradEconomyAPI.burn(hold);
    }

    static int houseAccess(UUID owner, UUID member, boolean allow) {
        return AincradEconomyAPI.setHouseAccess(owner, member, allow);
    }

    static UUID houseOwner(org.bukkit.Location l) {
        return AincradEconomyAPI.getHouseOwner(l);
    }

    static String format(long amount) {
        return AincradEconomyAPI.format(amount);
    }

    static boolean mail(UUID player, ItemStack item, String reason) {
        return AincradEconomyAPI.mail(player, item, reason);
    }
}
