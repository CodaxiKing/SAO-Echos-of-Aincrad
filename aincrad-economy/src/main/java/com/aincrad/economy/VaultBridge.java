package com.aincrad.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.List;
import java.util.UUID;

/**
 * Provedor do Vault: plugins de terceiros veem o Col como a economia do servidor. Só é carregada
 * quando o Vault está instalado. O Col é inteiro: valores com centavos são arredondados para baixo.
 * Contas por banco (bank) não existem — use as guildas do AincradSocial.
 */
@SuppressWarnings("deprecation") // o Vault obriga a implementar os métodos antigos por nome
public final class VaultBridge implements Economy {

    private final Plugin plugin;
    private final EconomyService eco;

    private VaultBridge(Plugin plugin, EconomyService eco) {
        this.plugin = plugin;
        this.eco = eco;
    }

    public static void register(Plugin plugin, EconomyService eco) {
        Bukkit.getServicesManager().register(Economy.class, new VaultBridge(plugin, eco), plugin, ServicePriority.High);
        plugin.getLogger().info("Registrado como economia do Vault.");
    }

    private static long col(double v) {
        return v <= 0 || Double.isNaN(v) ? 0 : (long) Math.min(Long.MAX_VALUE, Math.floor(v));
    }

    private double bal(UUID id) {
        long c = eco.accounts().cached(id);
        if (c >= 0) {
            return c;
        }
        // Offline: o Vault é síncrono, então lê do banco esperando (raro; evite em laços).
        try {
            return Math.max(0, eco.accounts().fetch(id).get());
        } catch (Exception e) {
            return 0;
        }
    }

    private EconomyResponse withdraw(UUID id, double amount) {
        long a = col(amount);
        boolean ok = eco.withdraw(id, a, "vault");
        return new EconomyResponse(a, bal(id), ok ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                ok ? null : "Saldo insuficiente ou jogador fora deste servidor");
    }

    private EconomyResponse deposit(UUID id, double amount) {
        long a = col(amount);
        boolean ok = eco.deposit(id, a, "vault");
        return new EconomyResponse(a, bal(id), ok ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                ok ? null : "Cancelado");
    }

    private static UUID id(String name) {
        OfflinePlayer p = EconomyService.find(name);
        return p == null ? null : p.getUniqueId();
    }

    private static final EconomyResponse NO_PLAYER =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Jogador desconhecido");
    private static final EconomyResponse NO_BANKS =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Sem contas de banco");

    @Override public boolean isEnabled() { return plugin.isEnabled(); }
    @Override public String getName() { return "AincradEconomy"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 0; }
    @Override public String format(double amount) { return eco.format(col(amount)); }
    @Override public String currencyNamePlural() { return eco.currency(2); }
    @Override public String currencyNameSingular() { return eco.currency(1); }

    @Override public boolean hasAccount(String playerName) { return id(playerName) != null; }
    @Override public boolean hasAccount(OfflinePlayer player) { return true; }
    @Override public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return true; }

    @Override public double getBalance(String playerName) { UUID u = id(playerName); return u == null ? 0 : bal(u); }
    @Override public double getBalance(OfflinePlayer player) { return bal(player.getUniqueId()); }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override public boolean has(String playerName, double amount) { return getBalance(playerName) >= col(amount); }
    @Override public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= col(amount); }
    @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { UUID u = id(playerName); return u == null ? NO_PLAYER : withdraw(u, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) { return withdraw(player.getUniqueId(), amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }

    @Override public EconomyResponse depositPlayer(String playerName, double amount) { UUID u = id(playerName); return u == null ? NO_PLAYER : deposit(u, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) { return deposit(player.getUniqueId(), amount); }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }

    @Override public EconomyResponse createBank(String name, String player) { return NO_BANKS; }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return NO_BANKS; }
    @Override public EconomyResponse deleteBank(String name) { return NO_BANKS; }
    @Override public EconomyResponse bankBalance(String name) { return NO_BANKS; }
    @Override public EconomyResponse bankHas(String name, double amount) { return NO_BANKS; }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return NO_BANKS; }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return NO_BANKS; }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return NO_BANKS; }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return NO_BANKS; }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return NO_BANKS; }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return NO_BANKS; }
    @Override public List<String> getBanks() { return List.of(); }

    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return true; }
}
