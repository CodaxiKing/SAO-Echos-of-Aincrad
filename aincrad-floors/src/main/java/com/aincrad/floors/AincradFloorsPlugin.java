package com.aincrad.floors;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.floors.api.AincradFloorsAPI;
import com.aincrad.floors.command.FloorCommands;
import com.aincrad.floors.data.FloorStore;
import com.aincrad.floors.listener.EntryGuard;
import com.aincrad.floors.listener.SafeZoneListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

/** AincradFloors: andares, bloqueio de entrada, zona segura, anúncio e Teleport Gates (etapas 1–4). */
public final class AincradFloorsPlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private ConfigFile gameFile;
    private DeathGameService game;
    private Messages msg;
    private FloorService floors;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        FloorStore store = new FloorStore(core.database());
        try {
            java.util.List<String> steps = new java.util.ArrayList<>(FloorStore.MIGRATIONS);
            steps.addAll(DeathGameService.MIGRATIONS);
            core.database().migrate("floors", steps);
            store.loadBlocking();
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradFloors não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        floors = new FloorService(this, msg, config, store);
        GateService gates = new GateService(floors);
        SafeZoneListener safe = new SafeZoneListener(floors);
        FloorCommands commands = new FloorCommands(this, floors, gates);

        var pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new EntryGuard(this, floors), this);
        pm.registerEvents(safe, this);
        pm.registerEvents(gates, this);
        pm.registerEvents(commands, this);
        for (String name : new String[]{"andares", "andar"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(commands);
                c.setTabCompleter(commands);
            }
        }
        core.commands().register(this, commands.floorAdmin());
        core.commands().register(this, commands.gateAdmin());
        gameFile = core.config(this, "deathgame.yml");
        game = new DeathGameService(this, msg, floors, gameFile);
        try {
            game.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler o estado do jogo da morte: " + e.getMessage());
        }
        pm.registerEvents(game, this);
        core.commands().register(this, new com.aincrad.floors.command.DeathGameCommand(msg, game, false));
        core.commands().register(this, new com.aincrad.floors.command.DeathGameCommand(msg, game, true));
        core.subscribe(DeathGameService.BUS, game::onBus);
        getServer().getScheduler().runTask(this, game::applyUi);
        getServer().getScheduler().runTaskTimer(this, () -> {
            game.refresh();
            game.tick();
        }, 1200L, 1200L);
        core.subscribe(FloorService.BUS_STATE, floors::onState);
        core.subscribe(FloorService.BUS_UNLOCK, floors::onUnlockAnnounce);

        long sweep = config.integer("zona_segura.limpar_mobs_ticks", 40, 5, 1200);
        getServer().getScheduler().runTaskTimer(this, safe::sweep, sweep, sweep);

        // /reload com gente online: carrega quem já está.
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                store.loadPlayerBlocking(p.getUniqueId());
            } catch (SQLException e) {
                getLogger().warning("Não foi possível carregar os andares de " + p.getName() + ": " + e.getMessage());
            }
            floors.track(p, p.getLocation());
        }

        AincradFloorsAPI.init(floors);
        AincradFloorsAPI.game(game);
        getServer().getServicesManager().register(AincradFloorsAPI.class, AincradFloorsAPI.instance(), this, ServicePriority.Normal);
        if (pm.getPlugin("PlaceholderAPI") != null) {
            try {
                new PapiHook(this, floors).register();
                getLogger().info("Placeholders registrados: %aincrad_frontline%, %aincrad_floor_atual%, %aincrad_floor_nome%.");
            } catch (LinkageError | RuntimeException e) {
                getLogger().warning("PlaceholderAPI encontrado, mas incompatível: " + e);
            }
        }
    }

    @Override
    public void onDisable() {
        AincradFloorsAPI.init(null);
        AincradFloorsAPI.game(null);
        getServer().getServicesManager().unregisterAll(this);
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        floors.reload();
        floors.store().reloadAsync();
        gameFile.reload();
        game.refresh();
        event.reloaded(getName());
    }
}
