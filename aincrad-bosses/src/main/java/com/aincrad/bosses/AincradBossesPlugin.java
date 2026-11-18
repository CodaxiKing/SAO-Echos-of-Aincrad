package com.aincrad.bosses;

import com.aincrad.bosses.api.AincradBossesAPI;
import com.aincrad.bosses.command.BossCommands;
import com.aincrad.bosses.command.GuideCommand;
import com.aincrad.bosses.command.RaidCommand;
import com.aincrad.bosses.data.BossStateStore;
import com.aincrad.bosses.data.GuideStore;
import com.aincrad.bosses.editor.BossEditor;
import com.aincrad.bosses.raid.RaidService;
import com.aincrad.bosses.data.RoomStore;
import com.aincrad.bosses.fight.FightManager;
import com.aincrad.bosses.hook.Hooks;
import com.aincrad.bosses.maze.MazeService;
import com.aincrad.bosses.model.BossRegistry;
import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

/** AincradBosses: chefes de andar, sala, fases, Last Attack, liberação do andar e labirinto (etapas 5–9). */
public final class AincradBossesPlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private Messages msg;
    private Hooks hooks;
    private FightManager fights;
    private MazeService maze;
    private RoomStore rooms;
    private BossStateStore states;
    private GuideStore guide;
    private RaidService raids;
    private ConfigFile roamingFile;
    private com.aincrad.bosses.fight.InformantService informants;
    private com.aincrad.bosses.fight.RoamingService roaming;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        states = new BossStateStore(core.database());
        try {
            core.database().migrate("bosses", BossStateStore.MIGRATIONS);
            core.database().migrate("bosses_guide", GuideStore.MIGRATIONS);
            core.database().migrate("bosses_raids", RaidService.MIGRATIONS);
            core.database().migrate("bosses_roaming", com.aincrad.bosses.fight.RoamingService.MIGRATIONS);
            core.database().migrate("bosses_pages", com.aincrad.bosses.fight.InformantService.MIGRATIONS);
            states.loadBlocking();
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradBosses não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        hooks = new Hooks(getLogger());
        rooms = new RoomStore(new File(getDataFolder(), "salas.yml"));
        fights = new FightManager(this, msg, config, hooks, rooms, states, loadBosses());
        maze = new MazeService(this, config, hooks);
        guide = new GuideStore(core.database());
        raids = new RaidService(this, fights, config, core.database());
        try {
            guide.loadBlocking();
            raids.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler o guia e as raids: " + e.getMessage());
        }
        fights.guide(guide);
        fights.raids(raids);
        AincradBossesAPI.raids(raids);
        // Se o servidor caiu no meio de uma luta, a porta ficou fechada: abre tudo ao ligar.
        fights.openAllDoors();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(fights, this);
        BossCommands commands = new BossCommands(this, fights, maze);
        BossEditor editor = new BossEditor(this, fights, hooks, () -> fights.registry(loadBosses()));
        commands.editor(editor);
        getServer().getPluginManager().registerEvents(editor, this);
        informants = new com.aincrad.bosses.fight.InformantService(this, msg, fights, guide, hooks, core.database());
        informants.reload(config);
        getServer().getPluginManager().registerEvents(informants, this);
        getServer().getOnlinePlayers().forEach(pl -> informants.load(pl.getUniqueId()));
        AincradBossesAPI.informants(informants);
        command("chefe", new GuideCommand(fights, guide, informants));
        command("raid", new RaidCommand(fights, raids));
        core.subscribe(RaidService.BUS, m -> {
            if (!core.server().equals(m.origin())) {
                raids.reloadAsync();
            }
        });
        getServer().getScheduler().runTaskTimer(this, raids::tick, 100L, 400L);
        roamingFile = core.config(this, "roaming.yml");
        roaming = new com.aincrad.bosses.fight.RoamingService(this, msg, fights, core.database());
        roaming.reload(roamingFile);
        try {
            roaming.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler o estado dos chefes de campo: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(roaming, this);
        getServer().getScheduler().runTaskTimer(this, roaming::tick, 200L, 200L);
        getServer().getScheduler().runTaskTimer(this, roaming::refresh, 1200L, 1200L);
        core.commands().register(this, commands.boss());
        core.commands().register(this, commands.maze());
        core.subscribe(FightManager.BUS_STATE, m -> {
            if (!core.server().equals(m.origin())) {
                states.reloadAsync();
                guide.reloadAsync();
            }
        });
        getServer().getScheduler().runTaskTimer(this, fights::tick, FightManager.STEP, FightManager.STEP);
        getServer().getScheduler().runTaskTimer(this, maze::tick, 40L, 20L);
        // Os plugins opcionais podem ligar depois deste: confere de novo no primeiro tick.
        getServer().getScheduler().runTask(this, hooks::detect);

        AincradBossesAPI.init(fights);
        getServer().getServicesManager().register(AincradBossesAPI.class, AincradBossesAPI.instance(), this, ServicePriority.Normal);
    }

    private void command(String name, org.bukkit.command.TabExecutor ex) {
        org.bukkit.command.PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(ex);
            c.setTabCompleter(ex);
        }
    }

    /** Lê a pasta bosses/ (na primeira vez, copia o Illfang de exemplo). */
    private BossRegistry loadBosses() {
        File folder = new File(getDataFolder(), "bosses");
        if (!folder.isDirectory()) {
            saveResource("bosses/andar1.yml", false);
            saveResource("bosses/campo_e_evento.yml", false);
        }
        BossRegistry reg = BossRegistry.load(folder);
        reg.errors().forEach(e -> getLogger().warning("bosses/" + e));
        getLogger().info(reg.all().size() + " chefe(s) carregado(s).");
        return reg;
    }

    @Override
    public void onDisable() {
        if (fights != null) {
            fights.endAll();
        }
        if (maze != null) {
            maze.clearAll();
        }
        AincradBossesAPI.init(null);
        getServer().getServicesManager().unregisterAll(this);
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        hooks.detect();
        // Lutas em andamento continuam com o chefe como estava; o YAML novo vale na próxima.
        fights.registry(loadBosses());
        roamingFile.reload();
        informants.reload(config);
        roaming.reload(roamingFile);
        rooms.load();
        maze.load();
        states.reloadAsync();
        guide.reloadAsync();
        raids.reloadAsync();
        event.reloaded(getName());
    }
}
