package com.aincrad.social.duel;

import com.aincrad.social.AincradSocialPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /duelo &lt;jogador&gt; [modo] · aceitar [jogador] · recusar [jogador] · desistir */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("aceitar", "recusar", "desistir");
    private static final List<String> MODES = List.of("primeiro_golpe", "metade", "total");

    private final AincradSocialPlugin plugin;

    public DuelCommand(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "geral.so_jogador");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(p, "uso.duelo");
            return true;
        }
        DuelManager duels = plugin.duels();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "aceitar", "recusar" -> {
                UUID from = null;
                if (args.length > 1) {
                    Player other = Bukkit.getPlayerExact(args[1]);
                    if (other == null) {
                        plugin.messages().send(p, "geral.jogador_nao_encontrado", Placeholder.unparsed("jogador", args[1]));
                        return true;
                    }
                    from = other.getUniqueId();
                }
                if (args[0].equalsIgnoreCase("aceitar")) {
                    duels.accept(p, from);
                } else {
                    duels.decline(p, from);
                }
            }
            case "desistir", "render" -> duels.surrender(p);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    plugin.messages().send(p, "geral.jogador_nao_encontrado", Placeholder.unparsed("jogador", args[0]));
                    return true;
                }
                DuelMode mode = null;
                if (args.length > 1) {
                    mode = DuelMode.parse(args[1]);
                    if (mode == null) {
                        plugin.messages().send(p, "uso.duelo");
                        return true;
                    }
                }
                duels.challenge(p, target, mode);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.addAll(SUBS);
            names(sender, out);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("aceitar") || args[0].equalsIgnoreCase("recusar")) {
                names(sender, out);
            } else if (!args[0].equalsIgnoreCase("desistir")) {
                out.addAll(MODES);
            }
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private static void names(CommandSender sender, List<String> out) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(sender)) {
                out.add(o.getName());
            }
        }
    }
}
