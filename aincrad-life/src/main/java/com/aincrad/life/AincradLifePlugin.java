package com.aincrad.life;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.life.alchemy.AlchemyService;
import com.aincrad.life.alchemy.GatherService;
import com.aincrad.life.api.AincradLifeAPI;
import com.aincrad.life.command.LifeCommands;
import com.aincrad.life.cooking.CookingService;
import com.aincrad.life.cooking.FoodBuffService;
import com.aincrad.life.data.LifeStore;
import com.aincrad.life.fishing.FishingService;
import com.aincrad.life.fishing.TournamentService;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.hook.RpgPetXpListener;
import com.aincrad.life.hook.RpgXpListener;
import com.aincrad.life.inn.Inn;
import com.aincrad.life.inn.InnService;
import com.aincrad.life.marriage.MarriageService;
import com.aincrad.life.orders.HuntListener;
import com.aincrad.life.orders.MaterialService;
import com.aincrad.life.orders.OrderService;
import com.aincrad.life.pets.PetService;
import com.aincrad.life.profession.ProfessionMenus;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.smith.SmithService;
import com.aincrad.life.stall.StallService;
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
 * flutuante e estalagens) e, da etapa 23, a culinária (receitas, preparo na bancada e buffs de
 * comida), a pesca (pontos com zonas, lançar, fisgar e cansar, Senhor do Lago e torneio semanal),
 * o ferreiro (reparo, forja com calor e batidas, e aprimoramento) e a alquimia (coleta, preparo
 * na mesa, poções, óleos, venenos, frascos e insumos).
 * Cada módulo liga e desliga em config.yml → modulos.
 */
public final class AincradLifePlugin extends JavaPlugin implements Listener {

    /** Módulos do plano que ainda não têm código nesta versão (ficam só anunciados no console). */
    private static final List<String> PENDING = List.of();

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
    private FishingService fishing;
    private SmithService smith;
    private AlchemyService alchemy;
    private OrderService orders;
    private PetService pets;
    private MarriageService marriages;
    private final List<String> uiEntries = new ArrayList<>();
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
            core.database().migrate("life_fishing", TournamentService.MIGRATIONS);
            core.database().migrate("life_gather", GatherService.MIGRATIONS);
            core.database().migrate("life_orders", OrderService.MIGRATIONS);
            core.database().migrate("life_materials", MaterialService.MIGRATIONS);
            core.database().migrate("life_stalls", StallService.MIGRATIONS);
            core.database().migrate("life_pets", PetService.MIGRATIONS);
            core.database().migrate("life_marriage", MarriageService.MIGRATIONS);
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

    /** Módulos ligados que não têm um campo próprio, para o resumo do console. */
    private final List<String> extraModules = new ArrayList<>();

    private void later(String name) {
        extraModules.add(name);
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
            if (on("pesca")) {
                startFishing(core);
            }
            if (on("ferreiro")) {
                smith = new SmithService(this, msg, config, hooks, professions, stations, panels);
                smith.load();
                listen(smith);
                moduleTasks.add(getServer().getScheduler().runTaskTimer(this, smith::tick, 1L, 1L));
                core.commands().register(this, commands.smith(smith));
            }
            if (on("alquimia")) {
                // Depois das outras três: os insumos da alquimia são itens delas.
                alchemy = new AlchemyService(this, msg, config, hooks, professions, stations, panels, () -> cooking, () -> fishing, () -> smith);
                alchemy.load();
                listen(alchemy.potions());
                listen(alchemy.gather());
                moduleTasks.add(getServer().getScheduler().runTaskTimer(this, alchemy::tick, 1L, 1L));
                moduleTasks.add(getServer().getScheduler().runTaskTimer(this, () -> alchemy.gather().sparkle(), 40L, 40L));
                command("alquimia", List.of("livrodealquimia"), (sender, args) -> {
                    if (sender instanceof Player p) {
                        alchemy.book(p);
                    } else {
                        msg.send(sender, "geral.so_jogador");
                    }
                });
                core.commands().register(this, commands.alchemy(alchemy));
            }
            if (on("barracas")) {
                // Barraca do cozinheiro, banca de peixe e botica: um bloco na casa do dono, que vende com ele offline.
                StallService stalls = new StallService(this, msg, config, hooks, professions, () -> fishing);
                try {
                    stalls.loadBlocking();
                } catch (SQLException e) {
                    getLogger().warning("Não foi possível ler as barracas: " + e.getMessage());
                }
                listen(stalls);
                command("barraca", List.of("banca", "botica"), stalls::command);
                later("barracas");
            }
            if (on("encomendas")) {
                // O quadro de encomendas liga as quatro profissões; precisa do AincradEconomy (custódia).
                orders = new OrderService(this, msg, config, hooks, professions, () -> cooking, () -> fishing, () -> smith, () -> alchemy);
                core.subscribe(OrderService.BUS, m -> {
                    if (orders != null && !core.server().equals(m.origin())) {
                        orders.onBus(m.payload());
                    }
                });
                stations.onUse(config.string("encomendas.estacao", "order_board").toLowerCase(Locale.ROOT), (p, st) -> orders.menu(p));
                moduleTasks.add(getServer().getScheduler().runTaskTimer(this, () -> orders.minute(), 140L, 1200L));
                listen(new HuntListener(orders));
                command("encomenda", List.of("encomendas", "pedido"), (sender, args) -> orders.command(sender, args));
                command("artesaos", List.of("artesoes"), (sender, args) -> orders.rankingCommand(sender, args));
                command("catalogo", List.of(), (sender, args) -> orders.catalogCommand(sender, args));
                command("contratar", List.of(), (sender, args) -> orders.hireCommand(sender, args));
                MaterialService materials = new MaterialService(this, msg, config, hooks, orders);
                moduleTasks.add(getServer().getScheduler().runTaskTimer(this, materials::minute, 180L, 1200L));
                command("material", List.of("materiais"), materials::command);
                core.commands().register(this, commands.contract(orders));
            }
        }
        if (on("familiares")) {
            pets = new PetService(this, msg, config, hooks);
            pets.load();
            listen(pets);
            if (hooks.hasRpg()) {
                try {
                    listen(new RpgPetXpListener((p, xp) -> {
                        if (pets != null) {
                            pets.addXp(p, xp);
                        }
                    }));
                } catch (LinkageError e) {
                    getLogger().warning("AincradRPG incompatível: o familiar não ganha XP do dono (" + e + ").");
                }
            }
            moduleTasks.add(getServer().getScheduler().runTaskTimer(this, pets::follow, 10L, 10L));
            moduleTasks.add(getServer().getScheduler().runTaskTimer(this, pets::second, 20L, 20L));
            moduleTasks.add(getServer().getScheduler().runTaskTimer(this, pets::minute, 1200L, 1200L));
            command("familiar", List.of("familiares", "pet"), pets::command);
            core.commands().register(this, commands.pet(pets));
        }
        if (on("casamento")) {
            marriages = new MarriageService(this, msg, config, hooks);
            marriages.load();
            listen(marriages);
            core.subscribe(MarriageService.BUS, m -> {
                if (marriages != null && !core.server().equals(m.origin())) {
                    marriages.onBus(m.payload());
                }
            });
            moduleTasks.add(getServer().getScheduler().runTaskTimer(this, marriages::second, 20L, 20L));
            command("casar", List.of(), marriages::marryCommand);
            command("divorciar", List.of(), marriages::divorceCommand);
            command("casal", List.of(), marriages::coupleCommand);
            core.commands().register(this, commands.marriage(marriages));
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
        AincradLifeAPI.pets(pets);
        AincradLifeAPI.marriages(marriages);
        uiEntries();
        List<String> later = new ArrayList<>();
        for (String m : PENDING) {
            if (on(m)) {
                later.add(m);
            }
        }
        getLogger().info("Módulos ligados: " + (anyProfession ? "profissões (base) " : "") + (cooking != null ? "culinária " : "") + (fishing != null ? "pesca " : "") + (smith != null ? "ferreiro " : "") + (alchemy != null ? "alquimia " : "") + (orders != null ? "encomendas " : "") + (extraModules.isEmpty() ? "" : String.join(" ", extraModules) + " ") + (pets != null ? "familiares " : "") + (marriages != null ? "casamento " : "") + (inns != null ? "estalagens " : "")
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

    /** Pesca: pontos com zonas, a pescaria em três fases, Senhor do Lago e torneio semanal. */
    private void startFishing(AincradCore core) {
        fishing = new FishingService(this, msg, config, hooks, professions, () -> foodBuffs);
        fishing.load();
        listen(fishing);
        moduleTasks.add(getServer().getScheduler().runTaskTimer(this, fishing::tick, 1L, 1L));
        moduleTasks.add(getServer().getScheduler().runTaskTimer(this, () -> fishing.tournament().minute(), 100L, 1200L));
        command("pesca", List.of("pescaria"), this::fishingCommand);
        core.commands().register(this, commands.fishing(fishing));
    }

    /** /pesca [torneio [entrar <andar> | ranking <andar>]] */
    private void fishingCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        if (args.length == 0) {
            fishing.showRecords(p);
            return;
        }
        var here = fishing.pointAt(p.getLocation());
        int floor = here == null ? 0 : here.floor();
        if (args.length > 2) {
            try {
                floor = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                floor = 0;
            }
        }
        String op = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "ranking";
        if (!args[0].equalsIgnoreCase("torneio") || floor <= 0 || !(op.equals("entrar") || op.equals("ranking"))) {
            msg.send(p, "pesca.uso");
            return;
        }
        if (op.equals("entrar")) {
            fishing.tournament().enter(p, floor);
        } else {
            fishing.tournament().ranking(p, floor);
        }
    }

    /** As telas do AincradLife no menu do AincradUI (se ele estiver no servidor). */
    private void uiEntries() {
        if (!getServer().getPluginManager().isPluginEnabled("AincradUI")) {
            return;
        }
        if (professions != null && (on("culinaria") || on("pesca") || on("ferreiro") || on("alquimia"))) {
            uiEntry("profissoes", "under_icons/skills", "ui.profissoes", p -> p.performCommand("profissoes"));
        }
        if (orders != null) {
            uiEntry("encomendas", "under_icons/quests", "ui.encomendas", orders::menu);
        }
        if (pets != null) {
            uiEntry("familiar", "under_icons/call", "ui.familiar", pets::panel);
        }
        if (marriages != null) {
            uiEntry("casal", "under_icons/friends", "ui.casal", marriages::menu);
        }
    }

    private void uiEntry(String id, String icon, String nameKey, java.util.function.Consumer<Player> action) {
        if (hooks.uiEntry("life_" + id, icon, msg.get(nameKey), action)) {
            uiEntries.add("life_" + id);
        }
    }

    private void stopModules() {
        uiEntries.forEach(hooks::uiRemove);
        uiEntries.clear();
        if (marriages != null) {
            marriages.shutdown();
        }
        if (panels != null) {
            panels.closeAll();
        }
        if (fishing != null) {
            fishing.shutdown();
        }
        if (smith != null) {
            smith.shutdown();
        }
        if (pets != null) {
            pets.shutdown();
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
        fishing = null;
        smith = null;
        alchemy = null;
        orders = null;
        pets = null;
        marriages = null;
        extraModules.clear();
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
        AincradLifeAPI.pets(null);
        AincradLifeAPI.marriages(null);
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
