package com.aincrad.economy.api;

import com.aincrad.economy.EconomyService;
import com.aincrad.economy.HoldService;
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
    private static HoldService holds;
    private static com.aincrad.economy.house.HouseService houses;

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

    /** Chamado pelo plugin: as casas. */
    public static void houses(com.aincrad.economy.house.HouseService service) {
        houses = service;
    }

    /** O dono da casa onde está o lugar. Null se não há casa ali, ou se ela está à venda. */
    public static UUID getHouseOwner(org.bukkit.Location location) {
        com.aincrad.economy.house.House h = houses == null || location == null ? null : houses.at(location);
        return h == null ? null : h.owner();
    }

    /** O preço da casa à venda onde está o lugar; -1 se não há casa ali ou se ela já tem dono. */
    public static long getHousePrice(org.bukkit.Location location) {
        com.aincrad.economy.house.House h = houses == null || location == null ? null : houses.at(location);
        return h == null || !h.forSale() ? -1 : h.price();
    }

    /** Compra, para o jogador, a casa onde ele está: cobra dele e avisa, como o /casa comprar. */
    public static void buyHouse(Player player) {
        if (houses != null) {
            houses.buy(player);
        }
    }

    /**
     * Abre (ou fecha) as casas de {@code owner} deste servidor para {@code member}: portas, baús e blocos.
     *
     * @return quantas casas mudaram
     */
    public static int setHouseAccess(UUID owner, UUID member, boolean allow) {
        return houses == null ? 0 : houses.access(owner, member, allow);
    }

    /** Chamado pelo plugin: o serviço de custódia. */
    public static void holds(HoldService service) {
        holds = service;
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

    // ------------------------------------------------------------------ custódia

    /**
     * Põe Col do jogador em custódia: sai do saldo dele e fica guardado até {@link #release} ou
     * {@link #refund}. Enquanto está lá, não pode ser gasto.
     *
     * @return o id da custódia, ou null se ele não tinha saldo
     */
    public static String hold(Player p, long amount, String reason) {
        return holds == null ? null : holds.hold(p.getUniqueId(), amount, reason);
    }

    /** Quanto ainda está guardado (0 se a custódia não existe ou já foi usada). O resultado chega na thread principal. */
    public static CompletableFuture<Long> held(String holdId) {
        return holds == null ? CompletableFuture.completedFuture(0L) : holds.held(holdId);
    }

    /**
     * Libera parte (ou tudo) da custódia para alguém, online ou não, em qualquer servidor. Nunca
     * libera mais do que há: duas liberações ao mesmo tempo, só uma passa.
     */
    public static CompletableFuture<Boolean> release(String holdId, UUID to, long amount, String reason) {
        return holds == null ? CompletableFuture.completedFuture(false) : holds.release(holdId, to, amount, reason);
    }

    /** Devolve ao dono tudo o que resta na custódia. @return o valor exato devolvido */
    public static CompletableFuture<Long> refund(String holdId, String reason) {
        return holds == null ? CompletableFuture.completedFuture(0L) : holds.refund(holdId, reason);
    }

    /** Tira de circulação parte da custódia (a taxa de uma entrega). @return false se a custódia não tem esse valor */
    public static CompletableFuture<Boolean> burn(String holdId, long amount) {
        return holds == null ? CompletableFuture.completedFuture(false) : holds.burn(holdId, amount);
    }

    /** Tira de circulação o que resta na custódia (a taxa de um contrato). @return o valor retirado */
    public static CompletableFuture<Long> burn(String holdId) {
        return holds == null ? CompletableFuture.completedFuture(0L) : holds.burn(holdId);
    }
}
