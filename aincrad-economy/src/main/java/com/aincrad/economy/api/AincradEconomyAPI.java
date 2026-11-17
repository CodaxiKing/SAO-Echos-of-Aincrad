package com.aincrad.economy.api;

import com.aincrad.economy.EconomyService;
import com.aincrad.economy.shop.ShopService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * API do AincradEconomy (Col). Os métodos são estáticos (dá para chamar por reflexão) e a
 * {@link #instance()} fica registrada no ServicesManager do Bukkit — é assim que o AincradSocial
 * e os outros plugins acham. Use na thread principal. Plugins de terceiros podem usar o Vault.
 */
public final class AincradEconomyAPI {

    private static final AincradEconomyAPI INSTANCE = new AincradEconomyAPI();

    private static EconomyService eco;
    private static ShopService shops;
    private static com.aincrad.economy.auction.AuctionService auction;

    private AincradEconomyAPI() {
    }

    public static void init(EconomyService e, ShopService s) {
        eco = e;
        shops = s;
    }

    /** Chamado pelo plugin: a caixa de retirada fica no serviço de leilão. */
    public static void mailbox(com.aincrad.economy.auction.AuctionService service) {
        auction = service;
    }

    public static AincradEconomyAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return eco != null;
    }

    /** Saldo de quem está online neste servidor. */
    public static long getBalance(Player p) {
        return eco == null ? 0 : eco.balance(p);
    }

    /** Saldo de qualquer jogador, direto do banco (-1 = sem conta). */
    public static CompletableFuture<Long> getBalanceAsync(OfflinePlayer p) {
        return eco == null ? CompletableFuture.completedFuture(-1L) : eco.accounts().fetch(p.getUniqueId());
    }

    public static boolean has(Player p, long amount) {
        return eco != null && eco.has(p, amount);
    }

    /** Tira Col. {@code reason} vai para o registro (eco_transactions). @return false sem saldo ou cancelado */
    public static boolean withdraw(Player p, long amount, String reason) {
        return eco != null && eco.withdraw(p.getUniqueId(), amount, reason);
    }

    /** Põe Col. @return false se cancelado */
    public static boolean deposit(Player p, long amount, String reason) {
        return eco != null && eco.deposit(p.getUniqueId(), amount, reason);
    }

    /** Põe Col em quem estiver offline ou em outro servidor (recompensa de guilda, leilão). */
    public static boolean deposit(UUID player, long amount, String reason) {
        return eco != null && eco.deposit(player, amount, reason);
    }

    /**
     * Manda um item para a caixa de retirada do jogador (/leilao retirar), online ou não, em
     * qualquer servidor. {@code reason} aparece na caixa (ex.: "estalagem").
     *
     * @return false se a economia está desligada
     */
    public static boolean mail(UUID player, org.bukkit.inventory.ItemStack item, String reason) {
        if (auction == null || item == null || item.getType().isAir()) {
            return false;
        }
        auction.mail(player, item, reason);
        return true;
    }

    /** "1.250 Col". */
    public static String format(long amount) {
        return eco == null ? amount + " Col" : eco.format(amount);
    }

    /** Abre uma loja de shops.yml (para NPCs de outros plugins, como o Citizens). @return false se não existe */
    public static boolean openShop(Player p, String shopId) {
        return shops != null && shops.open(p, shopId);
    }
}
