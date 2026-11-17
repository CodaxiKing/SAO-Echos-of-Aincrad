package com.aincrad.rpg;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.rpg.api.AincradRPGAPI;
import com.aincrad.rpg.command.AdminCommand;
import com.aincrad.rpg.command.LevelCommand;
import com.aincrad.rpg.command.StatusCommand;
import com.aincrad.rpg.data.PlayerStore;
import com.aincrad.rpg.item.ItemService;
import com.aincrad.rpg.mob.MobService;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

/** AincradRPG: level, XP, atributos, level dos mobs e itens com raridade (etapas A1–A2). */
public final class AincradRPGPlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private ConfigFile levels;
    private ConfigFile mobs;
    private Messages msg;
    private PlayerStore store;
    private RpgService rpg;
    private ItemService items;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        levels = core.config(this, "levels.yml");
        mobs = core.config(this, "mobs.yml");
        RpgSettings settings = new RpgSettings(config, levels, mobs);

        try {
            core.database().migrate("rpg", PlayerStore.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradRPG não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        store = new PlayerStore(core.database(), getLogger(), core::runSync, id -> getServer().getPlayer(id) != null);
        rpg = new RpgService(this, store, msg, settings);
        items = new ItemService(this, msg, core.config(this, "items.yml"), settings);
        MobService mobService = new MobService(this, rpg, items);
        PlayerListener players = new PlayerListener(this, rpg, items);
        StatusCommand status = new StatusCommand(rpg);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(players, this);
        getServer().getPluginManager().registerEvents(mobService, this);
        command("status", status);
        command("level", new LevelCommand(rpg));
        core.commands().register(this, new AdminCommand(rpg, items));
        AincradRPGAPI.init(rpg, mobService, items, status);
        getServer().getServicesManager().register(AincradRPGAPI.class, AincradRPGAPI.instance(), this, ServicePriority.Normal);

        // /reload ou plugin ligado com gente online: carrega quem já está.
        for (Player p : getServer().getOnlinePlayers()) {
            rpg.progress(p);
            rpg.applyAttributes(p);
            rpg.refresh(p);
        }
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                players.checkArmor(p);
            }
        }, 40L, 40L);
        long every = settings.saveEvery * 20L;
        getServer().getScheduler().runTaskTimer(this, store::saveAllAsync, every, every);
    }

    private void command(String name, TabExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(ex);
            c.setTabCompleter(ex);
        }
    }

    @Override
    public void onDisable() {
        AincradRPGAPI.init(null, null, null, null);
        getServer().getServicesManager().unregisterAll(this);
        if (rpg != null) {
            for (Player p : getServer().getOnlinePlayers()) {
                rpg.clearAttributes(p);
            }
        }
        if (store != null) {
            store.saveAllBlocking();
        }
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        levels.reload();
        mobs.reload();
        RpgSettings s = new RpgSettings(config, levels, mobs);
        rpg.settings(s);
        items.reload(s);
        for (Player p : getServer().getOnlinePlayers()) {
            rpg.applyAttributes(p);
            rpg.refresh(p);
        }
        event.reloaded(getName());
    }
}
