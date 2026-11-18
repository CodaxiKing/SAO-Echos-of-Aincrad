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
import com.aincrad.rpg.mob.SpawnService;
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
    private ConfigFile spawnFile;
    private SpawnService spawns;
    private ConfigFile rareFile;
    private com.aincrad.rpg.item.UniqueItemService uniques;
    private com.aincrad.rpg.mob.RareMobService rares;
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
            core.database().migrate("rpg_unique", com.aincrad.rpg.item.UniqueItemService.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradRPG não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        store = new PlayerStore(core.database(), getLogger(), core::runSync, id -> getServer().getPlayer(id) != null);
        rpg = new RpgService(this, store, msg, settings);
        items = new ItemService(this, msg, core.config(this, "items.yml"), settings);
        uniques = new com.aincrad.rpg.item.UniqueItemService(this, core.database(), msg, items);
        uniques.reload(config);
        try {
            uniques.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler os itens únicos: " + e.getMessage());
        }
        items.uniques(uniques);
        getServer().getPluginManager().registerEvents(uniques, this);
        getServer().getScheduler().runTaskTimer(this, uniques::tick, 200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, uniques::tickGround, 20L, 20L);
        org.bukkit.command.Command list = new org.bukkit.command.Command("unicos", "", "/unicos", java.util.List.of("itensunicos")) {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                if (sender instanceof Player p) {
                    uniques.list(p);
                }
                return true;
            }
        };
        getServer().getCommandMap().register("aincradrpg", list);
        MobService mobService = new MobService(this, rpg, items);
        PlayerListener players = new PlayerListener(this, rpg, items);
        StatusCommand status = new StatusCommand(rpg);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(players, this);
        getServer().getPluginManager().registerEvents(mobService, this);
        getServer().getPluginManager().registerEvents(new com.aincrad.rpg.item.ItemCombatListener(rpg, items, config), this);
        command("status", status);
        command("level", new LevelCommand(rpg));
        spawnFile = core.config(this, "spawns.yml");
        spawns = new SpawnService(this, mobService, items);
        getServer().getPluginManager().registerEvents(spawns, this);
        spawns.reload(spawnFile);
        rareFile = core.config(this, "rare-mobs.yml");
        rares = new com.aincrad.rpg.mob.RareMobService(this, msg, spawns, items);
        getServer().getPluginManager().registerEvents(rares, this);
        rares.reload(rareFile);
        core.commands().register(this, new AdminCommand(rpg, items, spawns, rares));
        core.commands().register(this, new com.aincrad.rpg.command.SpawnCommand(this, msg, spawns, spawnFile, items));
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
        if (rares != null) {
            rares.shutdown();
        }
        if (spawns != null) {
            spawns.shutdown();
        }
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
        uniques.reload(config);
        uniques.refresh();
        spawnFile.reload();
        spawns.reload(spawnFile);
        rareFile.reload();
        rares.reload(rareFile);
        for (Player p : getServer().getOnlinePlayers()) {
            rpg.applyAttributes(p);
            rpg.refresh(p);
        }
        event.reloaded(getName());
    }
}
