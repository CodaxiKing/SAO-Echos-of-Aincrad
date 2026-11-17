package com.aincrad.life;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.life.api.AincradLifeAPI;
import com.aincrad.life.command.LifeCommands;
import com.aincrad.life.cooking.CookingService;
import com.aincrad.life.cooking.FoodBuffService;
import com.aincrad.life.data.LifeStore;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.hook.RpgXpListener;
import com.aincrad.life.inn.Inn;
import com.aincrad.life.inn.InnService;
import com.aincrad.life.profession.ProfessionMenus;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.station.PanelManager;
import com.aincrad.life.station.StationService;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * AincradLife: a vida fora do combate. Esta versão traz a base (etapa 22: profissões com
 * proficiência, "aprender antes de fazer", árvore de especialização, bancadas com painel
 * flutuante e estalagens) e a culinária (etapa 23: receitas, preparo na bancada e buffs de
 * comida). Cada módulo liga e desliga em config.yml → modulos.
 */
public final class AincradLifePlugin extends JavaPlugin implements Listener {

    /** Módulos do plano que ainda não têm código nesta versão (ficam só anunciados no console). */
    private static final List<String> PENDING = List.of("pesca", "ferreiro", "alquimia", "familiares", "casamento");

    private ConfigFile config;
    private ConfigFile professionsConfig;
    private Messages msg;
    private Hooks hooks;
    private ProfessionService professions;
    private StationService stations;
    private PanelManager panels;
    private InnService inns;
    private CookingService cooking;
    private FoodBuffService foodBuffs;
    private LifeCommands commands;
    private final List<Listener> moduleListeners = new ArrayList<>();
    private final List<BukkitTask> moduleTasks = new ArrayList<>();
    private final List<String> moduleCommands = new ArrayList<>();

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        professionsConfig = core.config(this, "professions.yml");
        hooks = new Hooks(this);
        LifeStore store = new LifeStore(core.database());
        try {
            core.database().migrate("life", LifeStore.MIGRATIONS);
            core.database().migrate("life_stations", StationService.MIGRATIONS);
            core.database().migrate("life_inns", InnService.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradLife não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        professions = new ProfessionService(this, msg, store, hooks, professionsConfig);
        stations = new StationService(this, msg, hooks, core.database());
        panels = new PanelManager(this, msg);
        commands = new LifeCommands(this, professions, stations, panels);
        getServer().getPluginManager().registerEvents(this, this);
        AincradLifeAPI.init(professions, null);
        getServer().getServicesManager().register(AincradLifeAPI.class, AincradLifeAPI.instance(), this, ServicePriority.Normal);

        copyPack();
        if (hooks.hasEffects() && hooks.registerPack(new File(getDataFolder(), "resourcepack"))) {
            getLogger().info("Modelos das bancadas registrados no pack do AincradEffects.");
        }
        // /reload com gente online: carrega quem já está.
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                store.loadBlocking(p.getUniqueId());
            } catch (SQLException e) {
                getLogger().warning("Não foi possível carregar as profissões de " + p.getName() + ": " + e.getMessage());
            }
        }
        startModules();
    }

    private boolean on(String module) {
        return config.bool("modulos." + module, true);
    }

    /** Liga o que a config pede. Chamado ao ligar e depois de um reload. */
    private void startModules() {
        AincradCore core = AincradCore.get();
        boolean anyProfession = on("culinaria") || on("pesca") || on("ferreiro") || on("alquimia");
        if (anyProfession) {
            listen(professions);
            listen(stations);
            listen(panels);
            stations.loadTypes(config.string("estacoes.modelos", "auto"));
            try {
                stations.loadPlacedBlocking();
            } catch (SQLException e) {
                getLogger().warning("Não foi possível ler as bancadas: " + e.getMessage());
            }
            moduleTasks.add(getServer().getScheduler().runTaskTimer(this, panels::tick, 1L, 1L));
            moduleTasks.add(getServer().getScheduler().runTaskTimer(this, stations::syncViewers, 40L, 40L));
            ProfessionMenus menus = new ProfessionMenus(professions);
            command("profissoes", List.of("profissao", "profs"), (sender, args) -> {
                if (sender instanceof Player p) {
                    menus.open(p);
                } else {
                    msg.send(sender, "geral.so_jogador");
                }
            });
            core.commands().register(this, commands.station());
            core.commands().register(this, commands.knowledge());
            core.commands().register(this, commands.tree());
            core.commands().register(this, commands.profession());
            if (on("culinaria")) {
                startCooking(core);
            }
        }
        if (on("estalagens")) {
            inns = new InnService(this, msg, hooks, core.database(), config);
            try {
                inns.loadBlocking();
            } catch (SQLException e) {
                getLogger().warning("Não foi possível ler os quartos alugados: " + e.getMessage());
            }
            listen(inns);
            if (hooks.hasRpg()) {
                try {
                    listen(new RpgXpListener(p -> inns == null ? 0 : inns.xpBonus(p)));
                } catch (LinkageError e) {
                    getLogger().warning("AincradRPG incompatível: o descanso não dá bônus de XP (" + e + ").");
                }
            }
            core.subscribe(InnService.BUS, m -> {
                if (inns != null && !core.server().equals(m.origin())) {
                    inns.reloadAsync();
                }
            });
            moduleTasks.add(getServer().getScheduler().runTaskTimer(this, () -> inns.second(), 20L, 20L));
            moduleTasks.add(getServer().getScheduler().runTaskTimer(this, () -> inns.minute(), 200L, 1200L));
            command("estalagem", List.of("quarto"), this::innCommand);
            core.commands().register(this, commands.inn(inns));
        }
        AincradLifeAPI.init(professions, inns);
        AincradLifeAPI.food(foodBuffs);
        List<String> later = new ArrayList<>();
        for (String m : PENDING) {
            if (on(m)) {
                later.add(m);
            }
        }
        getLogger().info("Módulos ligados: " + (anyProfession ? "profissões (base) " : "") + (cooking != null ? "culinária " : "") + (inns != null ? "estalagens " : "")
                + (later.isEmpty() ? "" : "— ainda sem código nesta versão: " + String.join(", ", later)));
    }

    /** Culinária: receitas, preparo na bancada de cozinha e buffs de comida. */
    private void startCooking(AincradCore core) {
        foodBuffs = new FoodBuffService(this, msg);
        cooking = new CookingService(this, msg, config, professions, stations, panels, foodBuffs);
        cooking.load();
        listen(cooking);
        listen(foodBuffs);
        foodBuffs.refreshAll();
        if (hooks.hasRpg()) {
            try {
                listen(new RpgXpListener(p -> foodBuffs == null ? 0 : foodBuffs.xpBonus(p)));
            } catch (LinkageError e) {
                getLogger().warning("AincradRPG incompatível: a comida não dá bônus de XP (" + e + ").");
            }
        }
        moduleTasks.add(getServer().getScheduler().runTaskTimer(this, cooking::tick, 1L, 1L));
        moduleTasks.add(getServer().getScheduler().runTaskTimer(this, foodBuffs::second, 20L, 20L));
        core.commands().register(this, commands.kitchen(cooking));
    }

    private void stopModules() {
        if (panels != null) {
            panels.closeAll();
        }
        if (foodBuffs != null) {
            foodBuffs.stripAll();
        }
        if (commands != null) {
            commands.shutdown();
        }
        moduleTasks.forEach(BukkitTask::cancel);
        moduleTasks.clear();
        moduleListeners.forEach(HandlerList::unregisterAll);
        moduleListeners.clear();
        var known = getServer().getCommandMap().getKnownCommands();
        for (String name : moduleCommands) {
            known.remove(name);
            known.remove("aincradlife:" + name);
        }
        moduleCommands.clear();
        AincradCore.get().commands().unregisterAll(this);
        AincradCore.get().bus().unsubscribeAll("life:");
        if (stations != null) {
            stations.removeAllEntities();
            stations.clearHandlers();
        }
        inns = null;
        cooking = null;
        foodBuffs = null;
    }

    private void listen(Listener l) {
        moduleListeners.add(l);
        getServer().getPluginManager().registerEvents(l, this);
    }

    /** Registra um comando de jogador só enquanto o módulo dele está ligado. */
    private void command(String name, List<String> aliases, BiConsumer<CommandSender, String[]> run) {
        Command c = new Command(name, "", "/" + name, aliases) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                run.accept(sender, args);
                return true;
            }
        };
        getServer().getCommandMap().register("aincradlife", c);
        moduleCommands.add(name);
        moduleCommands.addAll(aliases);
    }

    /** /estalagem [convidar|remover <jogador> | sair] */
    private void innCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (op) {
            case "" -> p.sendMessage(inns.describe(p));
            case "convidar", "remover" -> {
                OfflinePlayer t = args.length > 1 ? getServer().getOfflinePlayerIfCached(args[1]) : null;
                if (t == null) {
                    msg.send(p, "admin.offline");
                    return;
                }
                inns.invite(p, t, op.equals("convidar"));
            }
            case "sair" -> {
                Inn.Rental r = inns.of(p.getUniqueId());
                if (r == null) {
                    msg.send(p, "estalagem.sem_quarto");
                    return;
                }
                AincradCore.get().menus().confirm(p, msg.get("estalagem.confirmar_sair"), yes -> {
                    Inn.Rental now = inns.of(yes.getUniqueId());
                    if (now != null) {
                        inns.checkout(now);
                    }
                }, no -> { });
            }
            default -> msg.send(p, "estalagem.uso");
        }
    }

    /** Copia do .jar os arquivos de resourcepack/ que ainda não existem (as artes que você trocou ficam). */
    private void copyPack() {
        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("resourcepack/")) {
                    continue;
                }
                File out = new File(getDataFolder(), e.getName());
                if (out.exists()) {
                    continue;
                }
                Files.createDirectories(out.getParentFile().toPath());
                try (InputStream in = jar.getInputStream(e)) {
                    Files.copy(in, out.toPath());
                }
            }
        } catch (IOException e) {
            getLogger().warning("Não foi possível copiar os modelos das bancadas: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (professions != null) {
            stopModules();
        }
        AincradLifeAPI.init(null, null);
        AincradLifeAPI.food(null);
        getServer().getServicesManager().unregisterAll(this);
    }

    /** /aincrad reload: relê tudo e religa os módulos conforme a config (um módulo desligado some com os comandos dele). */
    @EventHandler
    public void onReload(AincradReloadEvent event) {
        stopModules();
        msg.reload();
        config.reload();
        professionsConfig.reload();
        professions.reload(professionsConfig);
        startModules();
        event.reloaded(getName());
    }
}
