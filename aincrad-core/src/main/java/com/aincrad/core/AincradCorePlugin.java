package com.aincrad.core;

import com.aincrad.core.bus.MessageBus;
import com.aincrad.core.command.CommandRegistry;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.ChestMenuRenderer;
import com.aincrad.core.menu.MenuService;
import com.aincrad.core.message.Messages;
import com.aincrad.core.profile.ProfileService;
import com.aincrad.core.region.RegionManager;
import com.aincrad.core.util.Bedrock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * AincradCore: sem funcionalidade de jogo. Liga primeiro e oferece aos outros plugins config,
 * mensagens, /aincrad, banco, regiões, menus, perfil do jogador, MessageBus e utilitários.
 */
public final class AincradCorePlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private Messages coreMessages;
    private Database database;
    private MessageBus bus;
    private CommandRegistry commands;
    private RegionManager regions;
    private MenuService menus;
    private ProfileService profiles;
    private AincradCore api;

    @Override
    public void onEnable() {
        config = new ConfigFile(this, "config.yml");
        coreMessages = new Messages(this, null);
        try {
            database = openDatabase();
            database.migrate("AincradCore.profiles", ProfileService.MIGRATIONS);
            database.migrate("AincradCore.bus", MessageBus.MIGRATIONS);
            database.migrate("AincradCore.regions", RegionManager.MIGRATIONS);
        } catch (SQLException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "Não foi possível abrir o banco. Confira config.yml → banco. O AincradCore vai desligar.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        bus = new MessageBus(database, config.string("servidor", "servidor"), getLogger());
        try {
            bus.start();
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "MessageBus não iniciou: " + e.getMessage());
        }

        commands = new CommandRegistry(this, coreMessages);
        commands.install();

        regions = new RegionManager(this, database, bus, coreMessages);
        regions.setWandMaterial(Material.matchMaterial(config.string("regioes.varinha", "BLAZE_ROD")));
        try {
            regions.loadSync();
        } catch (SQLException e) {
            getLogger().log(Level.WARNING, "Regiões não carregadas: " + e.getMessage());
        }
        bus.subscribe(RegionManager.BUS_CHANNEL, m -> regions.reloadAsync());

        ChestMenuRenderer chest = new ChestMenuRenderer(this, coreMessages);
        menus = new MenuService(chest, coreMessages);
        profiles = new ProfileService(this, database);
        profiles.loadOnlineSync();

        var pm = getServer().getPluginManager();
        pm.registerEvents(regions, this);
        pm.registerEvents(chest, this);
        pm.registerEvents(profiles, this);
        pm.registerEvents(this, this);

        api = new AincradCore(this);
        AincradCore.set(api);
        CoreCommands.register(this);
        startTasks();
        getLogger().info("AincradCore ligado: banco " + database.type().name().toLowerCase() + ", servidor \"" + bus.server()
                + "\", " + regions.all().size() + " regiões.");
    }

    private Database openDatabase() {
        int threads = config.integer("banco.threads", 4, 1, 32);
        String type = config.string("banco.tipo", "sqlite").trim().toLowerCase();
        if (type.equals("mysql")) {
            return Database.mysql(config.string("banco.mysql.host", "localhost"),
                    config.integer("banco.mysql.porta", 3306, 1, 65535),
                    config.string("banco.mysql.banco", "aincrad"),
                    config.string("banco.mysql.usuario", "aincrad"),
                    config.string("banco.mysql.senha", ""),
                    config.string("banco.mysql.parametros", ""),
                    config.integer("banco.conexoes", 8, 2, 64), threads, getLogger());
        }
        if (!type.equals("sqlite")) {
            getLogger().warning("banco.tipo \"" + type + "\" desconhecido; usando sqlite.");
        }
        getDataFolder().mkdirs();
        return Database.sqlite(new File(getDataFolder(), config.string("banco.sqlite.arquivo", "aincrad.db")), getLogger());
    }

    private void startTasks() {
        long busTicks = config.integer("messagebus.intervalo_ticks", 20, 1, 200);
        Bukkit.getScheduler().runTaskTimer(this, () -> bus.poll(r -> Bukkit.getScheduler().runTask(this, r)), busTicks, busTicks);
        long keep = config.integer("messagebus.guardar_segundos", 300, 30, 86_400) * 1000L;
        Bukkit.getScheduler().runTaskTimer(this, () -> bus.cleanup(keep), 20L * 60, 20L * 60);
        long save = config.integer("perfil.salvar_segundos", 60, 5, 3600) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, profiles::flushAsync, save, save);
    }

    /** /aincrad reload: o core e todos os plugins que escutam o {@link AincradReloadEvent}. */
    List<String> reloadAll() {
        config.reload();
        coreMessages.reload();
        api.reloadMessages();
        regions.setWandMaterial(Material.matchMaterial(config.string("regioes.varinha", "BLAZE_ROD")));
        Bedrock.reset();
        AincradReloadEvent e = new AincradReloadEvent();
        e.reloaded(getName());
        Bukkit.getPluginManager().callEvent(e);
        return new ArrayList<>(e.reloadedPlugins());
    }

    /** Um plugin desligou: tira os subcomandos dele de /aincrad. */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (commands != null && event.getPlugin() != this) {
            commands.unregisterAll(event.getPlugin());
        }
    }

    @Override
    public void onDisable() {
        if (profiles != null) {
            profiles.flushSync();
        }
        if (database != null) {
            database.close();
        }
        AincradCore.set(null);
    }

    Messages coreMessages() {
        return coreMessages;
    }

    Database database() {
        return database;
    }

    MessageBus bus() {
        return bus;
    }

    CommandRegistry commands() {
        return commands;
    }

    RegionManager regions() {
        return regions;
    }

    MenuService menus() {
        return menus;
    }

    ProfileService profiles() {
        return profiles;
    }

    AincradCore api() {
        return api;
    }
}
