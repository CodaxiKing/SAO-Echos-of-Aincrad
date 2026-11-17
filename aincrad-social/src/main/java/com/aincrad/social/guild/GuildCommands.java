package com.aincrad.social.guild;

import com.aincrad.social.AincradSocialPlugin;
import com.aincrad.social.Names;
import com.aincrad.social.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /guilda, /g (chat da guilda) e /ga (chat das guildas aliadas). */
public final class GuildCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("criar", "info", "lista", "convidar", "aceitar", "recusar", "candidatar",
            "candidaturas", "sair", "expulsar", "promover", "rebaixar", "lider", "cofre", "tesouro", "casa", "uniforme",
            "alianca", "ranking", "desfazer", "config", "admin");

    private final AincradSocialPlugin plugin;

    public GuildCommands(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private GuildService svc() {
        return plugin.guilds();
    }

    private GuildManager gm() {
        return plugin.guildManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (args.length > 0 && args[0].equalsIgnoreCase("admin") && cmd.equals("guilda")) {
            admin(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (!(sender instanceof Player p)) {
            msg().send(sender, "geral.so_jogador");
            return true;
        }
        switch (cmd) {
            case "g" -> {
                if (args.length == 0) {
                    msg().send(p, "uso.guilda_chat");
                } else {
                    gm().chat(p, String.join(" ", args));
                }
            }
            case "ga" -> {
                if (args.length == 0) {
                    msg().send(p, "uso.guilda_chat");
                } else {
                    gm().allyChat(p, String.join(" ", args));
                }
            }
            default -> guild(p, args);
        }
        return true;
    }

    private Guild byTag(Player p, String[] args, int i) {
        if (args.length <= i) {
            msg().send(p, "uso.guilda");
            return null;
        }
        Guild g = svc().byName(String.join(" ", Arrays.copyOfRange(args, i, args.length)));
        if (g == null) {
            msg().send(p, "guilda.nao_existe", Placeholder.unparsed("guilda", args[i]));
        }
        return g;
    }

    private UUID player(Player p, String[] args, int i) {
        if (args.length <= i) {
            msg().send(p, "uso.guilda");
            return null;
        }
        UUID id = plugin.names().resolve(args[i]);
        if (id == null) {
            msg().send(p, "geral.jogador_nao_encontrado", Placeholder.unparsed("jogador", args[i]));
        }
        return id;
    }

    private void guild(Player p, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        UUID me = p.getUniqueId();
        switch (sub) {
            case "criar" -> {
                if (args.length < 3) {
                    msg().send(p, "uso.guilda_criar");
                    return;
                }
                String tag = args[args.length - 1];
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                gm().create(p, name, tag);
            }
            case "info" -> {
                Guild g = args.length > 1 ? byTag(p, args, 1) : svc().guildOf(me);
                if (g == null) {
                    if (args.length <= 1) {
                        msg().send(p, "guilda.sem_guilda");
                        msg().send(p, "uso.guilda");
                    }
                    return;
                }
                info(p, g);
            }
            case "lista" -> {
                List<Component> names = new ArrayList<>();
                for (Guild g : svc().all()) {
                    if (g.recruit() != Guild.Recruit.CONVITE) {
                        names.add(Component.text("[" + g.tag() + "] " + g.name(), NamedTextColor.WHITE));
                    }
                }
                msg().send(p, names.isEmpty() ? "guilda.nenhuma_aberta" : "guilda.lista_abertas",
                        Placeholder.component("guildas", Component.join(JoinConfiguration.commas(true), names)));
            }
            case "convidar" -> {
                UUID t = player(p, args, 1);
                if (t != null) {
                    gm().invite(p, t);
                }
            }
            case "aceitar" -> {
                Guild g = byTag(p, args, 1);
                if (g != null) {
                    gm().acceptInvite(p, g);
                }
            }
            case "recusar" -> {
                Guild g = byTag(p, args, 1);
                if (g != null) {
                    gm().declineInvite(p, g);
                }
            }
            case "candidatar" -> {
                Guild g = byTag(p, args, 1);
                if (g != null) {
                    gm().apply(p, g);
                }
            }
            case "candidaturas" -> {
                Guild g = svc().guildOf(me);
                if (g == null) {
                    msg().send(p, "guilda.sem_guilda");
                    return;
                }
                if (args.length >= 3) {
                    UUID t = player(p, args, 2);
                    if (t != null) {
                        gm().answerApplication(p, t, args[1].equalsIgnoreCase("aceitar"));
                    }
                    return;
                }
                List<String> names = g.applications().keySet().stream().map(plugin.names()::name).toList();
                msg().send(p, names.isEmpty() ? "guilda.sem_candidaturas" : "guilda.candidaturas",
                        Placeholder.unparsed("nomes", String.join(", ", names)));
            }
            case "sair" -> gm().leave(p);
            case "expulsar" -> {
                UUID t = player(p, args, 1);
                if (t != null) {
                    gm().kick(p, t);
                }
            }
            case "promover", "rebaixar" -> {
                UUID t = player(p, args, 1);
                if (t != null) {
                    gm().promote(p, t, sub.equals("promover"));
                }
            }
            case "lider" -> {
                UUID t = player(p, args, 1);
                if (t != null) {
                    gm().transfer(p, t);
                }
            }
            case "cofre" -> plugin.guildVault().open(p);
            case "tesouro" -> treasury(p, args);
            case "casa" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("definir")) {
                    gm().setHome(p);
                } else {
                    gm().home(p);
                }
            }
            case "uniforme" -> gm().uniform(p);
            case "alianca" -> {
                if (args.length < 3) {
                    Guild g = svc().guildOf(me);
                    if (g == null) {
                        msg().send(p, "guilda.sem_guilda");
                        return;
                    }
                    List<String> allies = g.allies().stream().map(svc()::get).filter(x -> x != null).map(Guild::name).toList();
                    msg().send(p, "guilda.aliados", Placeholder.unparsed("aliados", allies.isEmpty() ? "—" : String.join(", ", allies)));
                    return;
                }
                Guild other = byTag(p, args, 2);
                if (other == null) {
                    return;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "propor" -> gm().ally(p, other, false);
                    case "aceitar" -> gm().ally(p, other, true);
                    case "desfazer" -> gm().breakAlliance(p, other);
                    default -> msg().send(p, "uso.guilda");
                }
            }
            case "ranking" -> ranking(p);
            case "desfazer" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("confirmar")) {
                    gm().disband(p);
                } else {
                    msg().send(p, "guilda.confirmar_desfazer_cmd");
                }
            }
            case "config" -> config(p, args);
            default -> msg().send(p, "uso.guilda");
        }
    }

    private void info(Player p, Guild g) {
        UUID leader = g.leader();
        List<Component> members = new ArrayList<>();
        for (GuildRank r : GuildRank.values()) {
            for (UUID m : g.withRank(r)) {
                members.add(Component.text(plugin.names().name(m),
                        Names.online(m) != null ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
        }
        TagResolver t = TagResolver.resolver(
                Placeholder.unparsed("guilda", g.name()), Placeholder.unparsed("tag", g.tag()),
                Placeholder.unparsed("emblema", g.emblem()),
                Placeholder.unparsed("lider", leader == null ? "—" : plugin.names().name(leader)),
                Placeholder.unparsed("nivel", String.valueOf(g.level())), Placeholder.unparsed("xp", String.valueOf(g.xp())),
                Placeholder.unparsed("membros", String.valueOf(g.size())), Placeholder.unparsed("vagas", String.valueOf(svc().slots(g))),
                Placeholder.unparsed("chefes", String.valueOf(g.bossKills())), Placeholder.unparsed("la", String.valueOf(g.lastAttacks())),
                Placeholder.unparsed("andar", String.valueOf(g.highestFloor())),
                Placeholder.component("recrutamento", msg().get("guilda.recrutamento." + g.recruit().name().toLowerCase(Locale.ROOT))),
                Placeholder.component("lista", Component.join(JoinConfiguration.commas(true), members)));
        for (Component line : msg().list("guilda.info", t)) {
            p.sendMessage(line);
        }
    }

    private void treasury(Player p, String[] args) {
        Guild g = svc().guildOf(p.getUniqueId());
        if (g == null) {
            msg().send(p, "guilda.sem_guilda");
            return;
        }
        if (args.length >= 3) {
            long v;
            try {
                v = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                msg().send(p, "guilda.erro.invalid");
                return;
            }
            if (args[1].equalsIgnoreCase("depositar")) {
                gm().deposit(p, v);
            } else if (args[1].equalsIgnoreCase("sacar")) {
                gm().withdraw(p, v);
            } else {
                msg().send(p, "uso.guilda");
            }
            return;
        }
        msg().send(p, "guilda.tesouro", Placeholder.unparsed("valor", String.valueOf(g.treasury())),
                Placeholder.unparsed("taxa", String.valueOf(g.taxPercent())));
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
        List<Guild.Ledger> l = g.ledger();
        for (int i = Math.max(0, l.size() - 8); i < l.size(); i++) {
            Guild.Ledger e = l.get(i);
            p.sendMessage(msg().get("guilda.extrato_linha", Placeholder.unparsed("quando", fmt.format(new Date(e.time()))),
                    Placeholder.unparsed("jogador", e.whoName()), Placeholder.component("tipo", msg().get("guilda.extrato." + e.type())),
                    Placeholder.unparsed("valor", e.detail().isEmpty() ? String.valueOf(e.amount()) : e.detail())));
        }
    }

    private void ranking(Player p) {
        int pos = 0;
        msg().send(p, "guilda.ranking_titulo");
        for (Guild g : svc().ranking()) {
            if (gm().isRed(g)) {
                continue; // guilda vermelha fica fora do ranking
            }
            pos++;
            p.sendMessage(msg().get("guilda.ranking_linha", Placeholder.unparsed("pos", String.valueOf(pos)),
                    Placeholder.unparsed("guilda", g.name()), Placeholder.unparsed("tag", g.tag()),
                    Placeholder.unparsed("chefes", String.valueOf(g.bossKills())),
                    Placeholder.unparsed("la", String.valueOf(g.lastAttacks())),
                    Placeholder.unparsed("andar", String.valueOf(g.highestFloor()))));
            if (pos >= 10) {
                break;
            }
        }
        if (pos == 0) {
            msg().send(p, "guilda.ranking_vazio");
        }
    }

    private void config(Player p, String[] args) {
        if (args.length < 3) {
            msg().send(p, "uso.guilda_config");
            return;
        }
        UUID me = p.getUniqueId();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "recrutamento" -> {
                Guild.Recruit mode;
                try {
                    mode = Guild.Recruit.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    msg().send(p, "uso.guilda_config");
                    return;
                }
                gm().edit(p, svc().setRecruit(me, mode), "guilda.config_recrutamento",
                        Placeholder.component("modo", msg().get("guilda.recrutamento." + mode.name().toLowerCase(Locale.ROOT))));
            }
            case "taxa" -> {
                int v;
                try {
                    v = Integer.parseInt(args[2].replace("%", ""));
                } catch (NumberFormatException e) {
                    msg().send(p, "guilda.erro.invalid");
                    return;
                }
                gm().edit(p, svc().setTax(me, v), "guilda.config_taxa", Placeholder.unparsed("taxa", String.valueOf(v)));
            }
            case "cores" -> {
                if (args.length < 4) {
                    msg().send(p, "uso.guilda_config");
                    return;
                }
                gm().edit(p, svc().setColors(me, args[2], args[3]), "guilda.config_cores");
            }
            case "emblema" -> gm().edit(p, svc().setEmblem(me, args[2]), "guilda.config_emblema",
                    Placeholder.unparsed("emblema", args[2]));
            case "tag" -> gm().edit(p, svc().setTag(me, args[2]), "guilda.config_tag",
                    Placeholder.unparsed("tag", args[2].toUpperCase(Locale.ROOT)));
            case "permissao" -> {
                if (args.length < 4) {
                    msg().send(p, "uso.guilda_config");
                    return;
                }
                GuildPerm perm;
                try {
                    perm = GuildPerm.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    msg().send(p, "uso.guilda_config");
                    return;
                }
                GuildRank r = GuildRank.parse(args[3], null);
                if (r == null) {
                    msg().send(p, "uso.guilda_config");
                    return;
                }
                gm().edit(p, svc().setPermission(me, perm, r), "guilda.config_permissao",
                        Placeholder.unparsed("permissao", perm.key()), Placeholder.component("cargo", gm().rankName(r)));
            }
            default -> msg().send(p, "uso.guilda_config");
        }
    }

    private void admin(CommandSender s, String[] args) {
        if (!s.hasPermission("aincrad.admin")) {
            msg().send(s, "guilda.sem_permissao");
            return;
        }
        if (args.length < 2) {
            msg().send(s, "uso.guilda_admin");
            return;
        }
        Guild g = svc().byName(args[1]);
        if (g == null) {
            msg().send(s, "guilda.nao_existe", Placeholder.unparsed("guilda", args[1]));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "renomear" -> {
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                GuildService.ActResult r = svc().adminRename(g, name);
                msg().send(s, r == GuildService.ActResult.OK ? "guilda.admin_ok" : "guilda.erro." + r.name().toLowerCase(Locale.ROOT));
            }
            case "desfazer" -> {
                svc().adminDisband(g);
                plugin.guildVault().drain(g);
                for (UUID m : g.members().keySet()) {
                    Player o = Names.online(m);
                    if (o != null) {
                        msg().send(o, "guilda.desfeita", Placeholder.unparsed("guilda", g.name()), Placeholder.unparsed("tag", g.tag()));
                        gm().refreshName(o);
                    }
                }
                msg().send(s, "guilda.admin_ok");
            }
            case "dar-xp" -> {
                try {
                    gm().addXp(g, Long.parseLong(args[2]));
                    msg().send(s, "guilda.admin_ok");
                } catch (RuntimeException e) {
                    msg().send(s, "uso.guilda_admin");
                }
            }
            default -> msg().send(s, "uso.guilda_admin");
        }
        plugin.saveGuildsSoon();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("guilda")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "convidar", "expulsar", "promover", "rebaixar", "lider" -> filter(players(), args[1]);
                case "aceitar", "recusar", "candidatar", "info" -> filter(tags(), args[1]);
                case "tesouro" -> filter(List.of("depositar", "sacar"), args[1]);
                case "casa" -> filter(List.of("definir"), args[1]);
                case "alianca" -> filter(List.of("propor", "aceitar", "desfazer"), args[1]);
                case "candidaturas" -> filter(List.of("aceitar", "recusar"), args[1]);
                case "config" -> filter(List.of("recrutamento", "taxa", "cores", "emblema", "tag", "permissao"), args[1]);
                case "desfazer" -> filter(List.of("confirmar"), args[1]);
                case "admin" -> filter(List.of("renomear", "desfazer", "dar-xp"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "alianca" -> filter(tags(), args[2]);
                case "candidaturas" -> filter(players(), args[2]);
                case "admin" -> filter(tags(), args[2]);
                case "config" -> switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "recrutamento" -> filter(List.of("convite", "candidatura", "aberta"), args[2]);
                    case "emblema" -> svc().rules().emblems();
                    case "permissao" -> filter(Arrays.stream(GuildPerm.values()).filter(x -> !x.fixed()).map(GuildPerm::key).toList(), args[2]);
                    default -> List.of();
                };
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("config") && args[1].equalsIgnoreCase("permissao")) {
            return filter(Arrays.stream(GuildRank.values()).map(GuildRank::key).toList(), args[3]);
        }
        return List.of();
    }

    private List<String> tags() {
        return svc().all().stream().map(Guild::tag).toList();
    }

    private static List<String> players() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
