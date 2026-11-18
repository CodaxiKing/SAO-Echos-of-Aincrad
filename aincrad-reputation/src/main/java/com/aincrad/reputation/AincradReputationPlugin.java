package com.aincrad.reputation;

import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.reputation.api.AincradReputationAPI;
import com.aincrad.reputation.hook.BossesListener;
import com.aincrad.reputation.hook.FloorsListener;
import com.aincrad.reputation.hook.Hooks;
import com.aincrad.reputation.hook.ItemsListener;
import com.aincrad.reputation.hook.LifeListener;
import com.aincrad.reputation.model.Achievements;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * AincradReputation: registra o que cada jogador fez e mostra isso aos outros — conquistas,
 * títulos e a cor do cursor (verde, laranja, vermelho), com as consequências dela: cidades
 * fechadas, prisão e PvP desligado por andar.
 */
public final class AincradReputationPlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private Messages msg;
    private ReputationService reputation;
    private CursorService cursor;
    private MonumentService monument;
    private BountyService bounties;
    private GraveService graves;
    private final List<String> commands = new ArrayList<>();

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        try {
            List<String> steps = new ArrayList<>(ReputationService.MIGRATIONS);
            steps.addAll(MonumentService.MIGRATIONS);
            steps.addAll(BountyService.MIGRATIONS);
            steps.addAll(GraveService.MIGRATIONS);
            core.database().migrate("reputation", steps);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradReputation não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Hooks hooks = new Hooks(this);
        reputation = new ReputationService(this, msg, config);
        reputation.load();
        cursor = new CursorService(this, msg, config, hooks, reputation);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(reputation, this);
        getServer().getPluginManager().registerEvents(cursor, this);
        monument = new MonumentService(this, core.database(), msg, hooks);
        monument.load(config.integer("monumento.nomes_por_pagina", 16, 4, 40));
        getServer().getPluginManager().registerEvents(monument, this);
        getServer().getScheduler().runTaskTimer(this, monument::tick, 100L, 200L);
        getServer().getScheduler().runTaskTimer(this, monument::refresh, 1200L, 1200L);

        // As conquistas vêm dos eventos dos outros plugins: cada ouvinte só entra se o plugin dele está ligado.
        List<String> on = new ArrayList<>();
        hook(hooks, "AincradFloors", on, () -> new FloorsListener(reputation));
        hook(hooks, "AincradBosses", on, () -> new BossesListener(reputation));
        hook(hooks, "AincradLife", on, () -> new LifeListener(reputation, cursor));
        hook(hooks, "AincradItems", on, () -> new ItemsListener(cursor, msg));

        getServer().getScheduler().runTaskTimer(this, () -> {
            reputation.tickColors();
            cursor.tick();
        }, 100L, 100L);

        command("titulos", List.of("titulo"), (sender, args) -> player(sender, reputation::titlesMenu));
        command("conquistas", List.of("conquista"), (sender, args) -> player(sender, reputation::achievementsMenu));
        command("reputacao", List.of("rep", "cursor"), (sender, args) -> {
            Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : sender instanceof Player p ? p : null;
            if (target == null) {
                msg.send(sender, "admin.offline");
            } else {
                reputation.describe(sender, target);
            }
        });
        bounties = new BountyService(this, core.database(), msg, config, hooks);
        getServer().getPluginManager().registerEvents(bounties, this);
        getServer().getScheduler().runTaskTimer(this, bounties::tick, 600L, 1200L);
        graves = new GraveService(this, core.database(), msg, config);
        try {
            graves.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler os túmulos: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(graves, this);
        getServer().getScheduler().runTaskTimer(this, graves::tick, 100L, 200L);
        command("recompensa", List.of(), (sender, args) -> player(sender, p -> {
            if (args.length < 2) {
                msg.send(p, "recompensa.uso");
            } else {
                bounties.place(p, args[0], args[1]);
            }
        }));
        command("recompensas", List.of("cabecas"), (sender, args) -> player(sender, bounties::list));
        command("monumento", List.of("monumentodavida"), (sender, args) ->
                player(sender, p -> monument.menu(p, args.length == 0 ? null : args[0])));
        core.commands().register(this, new SubCommand() {
            @Override
            public String name() {
                return "monumento";
            }

            @Override
            public String permission() {
                return "aincrad.admin";
            }

            @Override
            public String usage() {
                return "monumento definir";
            }

            @Override
            public Component description() {
                return msg.get("admin.descricao_monumento");
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                if (!(sender instanceof Player p) || args.length == 0 || !args[0].equalsIgnoreCase("definir")) {
                    msg.send(sender, "admin.uso_monumento");
                    return;
                }
                monument.define(p);
                msg.send(sender, "admin.monumento_definido");
                log(sender, "monumento.definir", p.getWorld().getName() + " " + p.getLocation().getBlockX() + " "
                        + p.getLocation().getBlockY() + " " + p.getLocation().getBlockZ());
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return args.length == 1 ? List.of("definir") : List.of();
            }
        });
        core.commands().register(this, prisonCommand());
        core.commands().register(this, reputationCommand());
        core.commands().register(this, titleCommand());
        core.commands().register(this, achievementCommand());

        AincradReputationAPI.init(reputation, cursor);
        getServer().getServicesManager().register(AincradReputationAPI.class, AincradReputationAPI.instance(), this, ServicePriority.Normal);
        getLogger().info("Ligado. Conquistas de: " + (on.isEmpty() ? "nenhum outro plugin (só por comando e pela API)" : String.join(", ", on))
                + (cursor.hasPrison() ? "" : ". A prisão ainda não foi definida neste servidor (/aincrad prisao definir)."));
    }

    private void hook(Hooks hooks, String plugin, List<String> on, Supplier<Listener> listener) {
        if (!hooks.has(plugin)) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(listener.get(), this);
            on.add(plugin);
        } catch (LinkageError e) {
            getLogger().warning(plugin + " incompatível: as conquistas dele não vão funcionar (" + e + ").");
        }
    }

    private void player(CommandSender sender, java.util.function.Consumer<Player> run) {
        if (sender instanceof Player p) {
            run.accept(p);
        } else {
            msg.send(sender, "geral.so_jogador");
        }
    }

    private void command(String name, List<String> aliases, BiConsumer<CommandSender, String[]> run) {
        Command c = new Command(name, "", "/" + name, aliases) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                run.accept(sender, args);
                return true;
            }
        };
        getServer().getCommandMap().register("aincradreputation", c);
        commands.add(name);
        commands.addAll(aliases);
    }

    // ------------------------------------------------------------------ admin

    private void log(CommandSender actor, String action, String detail) {
        Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(actor, "reputation", action, detail));
    }

    private static List<String> filter(List<String> opts, String last) {
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last.toLowerCase(Locale.ROOT))) {
                out.add(o);
            }
        }
        return out;
    }

    private static List<String> players() {
        List<String> out = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        return out;
    }

    private SubCommand sub(String name, String usage, String descKey, BiConsumer<CommandSender, String[]> exec, Function<String[], List<String>> complete) {
        return new SubCommand() {
            @Override public String name() { return name; }
            @Override public String permission() { return "aincrad.admin"; }
            @Override public String usage() { return usage; }
            @Override public Component description() { return msg.get(descKey); }
            @Override public void execute(CommandSender sender, String[] args) { exec.accept(sender, args); }
            @Override public List<String> complete(CommandSender sender, String[] args) { return complete.apply(args); }
        };
    }

    private Player online(CommandSender sender, String[] args, int index) {
        Player t = args.length > index ? Bukkit.getPlayerExact(args[index]) : null;
        if (t == null) {
            msg.send(sender, "admin.offline");
        }
        return t;
    }

    /** /aincrad prisao definir */
    private SubCommand prisonCommand() {
        return sub("prisao", "prisao definir", "admin.descricao_prisao", (sender, args) -> {
            if (!(sender instanceof Player p) || args.length == 0 || !args[0].equalsIgnoreCase("definir")) {
                msg.send(sender, "admin.uso_prisao");
                return;
            }
            if (cursor.definePrison(p)) {
                msg.send(p, "prisao.definida");
                log(sender, "prisao.definir", p.getWorld().getName() + " " + p.getLocation().getBlockX() + "," + p.getLocation().getBlockY() + ","
                        + p.getLocation().getBlockZ());
            } else {
                msg.send(p, "prisao.selecao");
            }
        }, args -> args.length == 1 ? filter(List.of("definir"), args[0]) : List.of());
    }

    /** /aincrad reputacao limpar <jogador> */
    private SubCommand reputationCommand() {
        return sub("reputacao", "reputacao limpar <jogador>", "admin.descricao_reputacao", (sender, args) -> {
            if (args.length < 2 || !args[0].equalsIgnoreCase("limpar")) {
                msg.send(sender, "admin.uso_reputacao");
                return;
            }
            Player t = online(sender, args, 1);
            if (t != null) {
                reputation.clear(t);
                msg.send(sender, "admin.reputacao_limpa", Messages.vars("jogador", t.getName()));
                msg.send(t, "cursor.limpo");
                log(sender, "reputacao.limpar", t.getName());
            }
        }, args -> args.length == 1 ? filter(List.of("limpar"), args[0]) : args.length == 2 ? filter(players(), args[1]) : List.of());
    }

    /** /aincrad titulo dar <jogador> <título> */
    private SubCommand titleCommand() {
        return sub("titulo", "titulo dar <jogador> <título>", "admin.descricao_titulo", (sender, args) -> {
            if (args.length < 3 || !args[0].equalsIgnoreCase("dar")) {
                msg.send(sender, "admin.uso_titulo");
                return;
            }
            Player t = online(sender, args, 1);
            String title = String.join(" ", List.of(args).subList(2, args.length)).trim();
            if (t == null) {
                return;
            }
            if (title.isEmpty() || title.length() > 48 || title.contains("<")) {
                msg.send(sender, "admin.titulo_invalido");
                return;
            }
            reputation.giveTitle(t, title);
            msg.send(sender, "admin.titulo_dado", Messages.vars("jogador", t.getName(), "titulo", title));
            msg.send(t, "titulo.recebeu", Messages.vars("titulo", title));
            log(sender, "titulo.dar", t.getName() + " " + title);
        }, args -> args.length == 1 ? filter(List.of("dar"), args[0]) : args.length == 2 ? filter(players(), args[1]) : List.of());
    }

    /** /aincrad conquista dar <jogador> <id> */
    private SubCommand achievementCommand() {
        return sub("conquista", "conquista dar <jogador> <id>", "admin.descricao_conquista", (sender, args) -> {
            if (args.length < 3 || !args[0].equalsIgnoreCase("dar")) {
                msg.send(sender, "admin.uso_conquista");
                return;
            }
            Player t = online(sender, args, 1);
            Achievements.Def d = reputation.achievements().get(args[2]);
            if (t == null) {
                return;
            }
            if (d == null) {
                msg.send(sender, "admin.conquista_desconhecida", Messages.vars("lista", String.join(", ", reputation.achievements().all().keySet())));
                return;
            }
            boolean given = reputation.grant(t, d, d.floor());
            msg.send(sender, given ? "admin.conquista_dada" : "admin.conquista_ja_tem", Messages.vars("jogador", t.getName(), "conquista", d.name()));
            if (given) {
                log(sender, "conquista.dar", t.getName() + " " + d.id());
            }
        }, args -> args.length == 1 ? filter(List.of("dar"), args[0]) : args.length == 2 ? filter(players(), args[1])
                : args.length == 3 ? filter(new ArrayList<>(reputation.achievements().all().keySet()), args[2]) : List.of());
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        reputation.load();
        monument.load(config.integer("monumento.nomes_por_pagina", 16, 4, 40));
        event.reloaded(getName());
    }

    @Override
    public void onDisable() {
        if (reputation != null) {
            reputation.shutdown();
        }
        if (monument != null) {
            monument.removeBoard();
        }
        if (graves != null) {
            graves.removeBodies();
        }
        var known = getServer().getCommandMap().getKnownCommands();
        for (String name : commands) {
            known.remove(name);
            known.remove("aincradreputation:" + name);
        }
        if (AincradCore.get() != null && reputation != null) {
            AincradCore.get().commands().unregisterAll(this);
        }
        AincradReputationAPI.init(null, null);
        getServer().getServicesManager().unregisterAll(this);
    }
}
