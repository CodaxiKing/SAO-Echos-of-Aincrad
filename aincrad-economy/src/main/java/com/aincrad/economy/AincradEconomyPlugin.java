package com.aincrad.economy;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.economy.api.AincradEconomyAPI;
import com.aincrad.economy.command.AdminCommands;
import com.aincrad.economy.command.PlayerCommands;
import com.aincrad.economy.data.Accounts;
import com.aincrad.economy.shop.ShopService;
import com.aincrad.economy.trade.TradeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        try {
            core.database().migrate("economy", Accounts.MIGRATIONS);
            core.database().migrate("economy_shops", ShopService.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradEconomy não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Accounts accounts = new Accounts(core.database(), getLogger(), core.server(),
                config.integer("saldo_inicial", 100, 0, Integer.MAX_VALUE), id -> getServer().getPlayer(id) != null);
        eco = new EconomyService(this, accounts, msg, config);
        shops = new ShopService(this, eco, core.config(this, "shops.yml"), core.database());
        try {
            shops.loadLinksBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler as ligações de loja: " + e.getMessage());
        }
        trade = new TradeService(this, eco);

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

        AincradEconomyAPI.init(eco, shops);
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
        AincradEconomyAPI.init(null, null);
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
