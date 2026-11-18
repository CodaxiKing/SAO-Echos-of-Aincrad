package com.aincrad.economy;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.economy.api.AincradEconomyAPI;
import com.aincrad.economy.auction.AuctionService;
import com.aincrad.economy.command.AdminCommands;
import com.aincrad.economy.command.MarketCommands;
import com.aincrad.economy.command.PlayerCommands;
import com.aincrad.economy.data.Accounts;
import com.aincrad.economy.house.HouseService;
import com.aincrad.economy.shop.ShopService;
import com.aincrad.economy.trade.TradeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

/** AincradEconomy: Col, lojas de NPC, /pagar e troca segura (A3); leilão e casas (A4). */
public final class AincradEconomyPlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private Messages msg;
    private EconomyService eco;
    private ShopService shops;
    private TradeService trade;
    private com.aincrad.economy.house.HouseUpkeep upkeep;
    private com.aincrad.economy.house.HouseRealty realty;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        try {
            core.database().migrate("economy", Accounts.MIGRATIONS);
            core.database().migrate("economy_shops", ShopService.MIGRATIONS);
            core.database().migrate("economy_auctions", AuctionService.MIGRATIONS);
            core.database().migrate("economy_houses", HouseService.MIGRATIONS);
            core.database().migrate("economy_house_auctions", com.aincrad.economy.house.HouseAuctionService.MIGRATIONS);
            core.database().migrate("economy_holds", HoldService.MIGRATIONS);
            core.database().migrate("economy_house_upkeep", com.aincrad.economy.house.HouseUpkeep.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradEconomy não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Accounts accounts = new Accounts(core.database(), getLogger(), core.server(),
                config.integer("saldo_inicial", 100, 0, Integer.MAX_VALUE), id -> getServer().getPlayer(id) != null);
        eco = new EconomyService(this, accounts, msg, config);
        HoldService holds = new HoldService(eco, core.database(), getLogger());
        AincradEconomyAPI.holds(holds);
        shops = new ShopService(this, eco, core.config(this, "shops.yml"), core.database());
        try {
            shops.loadLinksBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler as ligações de loja: " + e.getMessage());
        }
        trade = new TradeService(this, eco);
        if (getServer().getPluginManager().isPluginEnabled("AincradUI")) {
            try {
                shops.hookFlat();
            } catch (LinkageError | RuntimeException e) {
                getLogger().warning("AincradUI encontrado, mas a loja em tela plana não ligou: " + e);
            }
        }
        AuctionService auction = new AuctionService(this, eco, core.database());
        HouseService houses = new HouseService(this, eco, core.database());
        AincradEconomyAPI.houses(houses);
        try {
            houses.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler as casas: " + e.getMessage());
        }

        // Quem já está online (/reload): carrega agora.
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                accounts.loadBlocking(p.getUniqueId(), p.getName());
            } catch (SQLException e) {
                getLogger().warning("Não foi possível carregar o Col de " + p.getName() + ": " + e.getMessage());
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new EcoListener(this, eco), this);
        getServer().getPluginManager().registerEvents(shops, this);
        getServer().getPluginManager().registerEvents(trade, this);
        getServer().getPluginManager().registerEvents(houses, this);
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                Player p = event.getPlayer();
                getServer().getScheduler().runTaskLater(AincradEconomyPlugin.this, () -> {
                    if (p.isOnline()) {
                        auction.notifyMailbox(p);
                    }
                }, 60L);
            }
        }, this);
        core.subscribe(AuctionService.BUS_SOLD, auction::onSold);
        core.subscribe(HouseService.BUS_CHANNEL, m -> {
            if (!core.server().equals(m.origin())) {
                houses.reloadAsync();
            }
        });
        getServer().getScheduler().runTaskTimer(this, auction::expire, 20L * 30, 20L * 60);
        core.subscribe(Accounts.BUS_CHANNEL, accounts::onBus);
        core.subscribe(ShopService.BUS_CHANNEL, m -> {
            if (!core.server().equals(m.origin())) {
                shops.reloadLinksAsync();
            }
        });

        PlayerCommands cmds = new PlayerCommands(eco, trade);
        for (String name : new String[]{"col", "pagar", "trocar"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(cmds);
                c.setTabCompleter(cmds);
            }
        }
        core.commands().register(this, AdminCommands.col(eco));
        core.commands().register(this, AdminCommands.shop(eco, shops));
        com.aincrad.economy.house.HouseAuctionService houseAuctions =
                new com.aincrad.economy.house.HouseAuctionService(this, eco, holds, houses, core.database());
        try {
            houseAuctions.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler os leilões de casas: " + e.getMessage());
        }
        houses.auctions(houseAuctions);
        getServer().getScheduler().runTaskTimer(this, houseAuctions::tick, 200L, 600L);
        getServer().getScheduler().runTaskTimer(this, houseAuctions::refresh, 1200L, 1200L);
        MarketCommands market = new MarketCommands(eco, auction, houses);
        market.houseAuctions(houseAuctions);
        upkeep = new com.aincrad.economy.house.HouseUpkeep(this, eco, houses, houseAuctions, core.database());
        try {
            upkeep.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler as placas das casas: " + e.getMessage());
        }
        houses.upkeep(upkeep);
        market.upkeep(upkeep);
        getServer().getPluginManager().registerEvents(upkeep, this);
        getServer().getScheduler().runTaskTimer(this, upkeep::tickSigns, 100L, 100L);
        getServer().getScheduler().runTaskTimer(this, upkeep::tickResets, 600L, 1200L);
        getServer().getScheduler().runTaskTimer(this, upkeep::tickInactive, 1200L, 12000L);
        getServer().getScheduler().runTaskTimer(this, upkeep::tickTax, 400L, 12000L);
        realty = new com.aincrad.economy.house.HouseRealty(this, eco, houses, houseAuctions);
        getServer().getPluginManager().registerEvents(realty, this);
        getServer().getScheduler().runTaskTimer(this, realty::tick, 20L, 20L);
        PluginCommand list = getCommand("casas");
        if (list != null) {
            list.setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player p) {
                    realty.open(p);
                }
                return true;
            });
        }
        for (String name : new String[]{"leilao", "casa"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(market);
                c.setTabCompleter(market);
            }
        }
        core.commands().register(this, market.admin());

        AincradEconomyAPI.init(eco, shops);
        AincradEconomyAPI.mailbox(auction);
        getServer().getServicesManager().register(AincradEconomyAPI.class, AincradEconomyAPI.instance(), this, ServicePriority.Normal);
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                VaultBridge.register(this, eco);
            } catch (LinkageError e) {
                getLogger().warning("Vault encontrado, mas incompatível: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (trade != null) {
            trade.shutdown();
        }
        if (upkeep != null) {
            upkeep.shutdown();
        }
        if (realty != null) {
            realty.shutdown();
        }
        AincradEconomyAPI.init(null, null);
        AincradEconomyAPI.mailbox(null);
        getServer().getServicesManager().unregisterAll(this);
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        eco.reload(config);
        shops.reload();
        event.reloaded(getName());
    }
}
