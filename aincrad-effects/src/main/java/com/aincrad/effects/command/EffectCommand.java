package com.aincrad.effects.command;

import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.config.Messages;
import com.aincrad.effects.engine.Audience;
import com.aincrad.effects.engine.Effect;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /efeito tocar &lt;efeito&gt; [jogador | @a | @s | raio:&lt;n&gt;] [variavel=valor ...]
 * /efeito lista | parar [jogador] | pack | reload
 * Funciona no console, em blocos de comando, NPCs e quests.
 */
public final class EffectCommand implements CommandExecutor, TabCompleter {

    private final AincradEffectsPlugin plugin;

    public EffectCommand(AincradEffectsPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            msg().send(sender, "uso");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "tocar", "play" -> {
                if (check(sender, "aincrad.effects.play")) {
                    play(sender, args);
                }
            }
            case "lista", "list" -> {
                if (check(sender, "aincrad.effects.play")) {
                    msg().send(sender, "lista",
                            Placeholder.unparsed("total", String.valueOf(plugin.engine().ids().size())),
                            Placeholder.unparsed("efeitos", String.join(", ", plugin.engine().ids())));
                }
            }
            case "parar", "stop" -> {
                if (check(sender, "aincrad.effects.play")) {
                    stop(sender, args);
                }
            }
            case "pack" -> {
                if (!(sender instanceof Player p)) {
                    msg().send(sender, "geral.so_jogador");
                } else if (check(sender, "aincrad.effects.pack")) {
                    msg().send(p, plugin.pack().sendTo(p) ? "pack_enviado" : "pack_indisponivel");
                }
            }
            case "reload" -> {
                if (check(sender, "aincrad.admin")) {
                    List<String> errors = plugin.reloadAll();
                    msg().send(sender, "reload",
                            Placeholder.unparsed("efeitos", String.valueOf(plugin.engine().ids().size())),
                            Placeholder.unparsed("imagens", String.valueOf(plugin.images().presentCount())),
                            Placeholder.unparsed("gatilhos", String.valueOf(plugin.triggers().count())));
                    if (!errors.isEmpty()) {
                        msg().send(sender, "erros", Placeholder.unparsed("total", String.valueOf(errors.size())));
                        if (sender instanceof Player) {
                            errors.stream().limit(5).forEach(e -> sender.sendMessage(Messages.parse("<red> • <gray>" + escape(e))));
                        }
                    }
                }
            }
            default -> msg().send(sender, "uso");
        }
        return true;
    }

    private boolean check(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) {
            return true;
        }
        msg().send(sender, "geral.sem_permissao");
        return false;
    }

    private void play(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg().send(sender, "uso");
            return;
        }
        Effect effect = plugin.engine().get(args[1]);
        if (effect == null) {
            msg().send(sender, "efeito_nao_existe", Placeholder.unparsed("efeito", args[1]));
            return;
        }
        String target = null;
        Map<String, String> vars = new HashMap<>();
        for (int i = 2; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) {
                vars.put(args[i].substring(0, eq), args[i].substring(eq + 1).replace('_', ' '));
            } else if (target == null) {
                target = args[i];
            }
        }

        if (target == null || target.equalsIgnoreCase("@s")) {
            if (!(sender instanceof Player p)) {
                msg().send(sender, "geral.so_jogador");
                return;
            }
            plugin.engine().play(effect, p.getLocation(), p, null, null, vars);
            msg().send(sender, "tocado", Placeholder.unparsed("efeito", effect.id()), Placeholder.unparsed("alvo", p.getName()));
            return;
        }
        if (target.equalsIgnoreCase("@a")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.engine().play(effect, p.getLocation(), p, Audience.player(), null, vars);
            }
            msg().send(sender, "tocado", Placeholder.unparsed("efeito", effect.id()), Placeholder.unparsed("alvo", "@a"));
            return;
        }
        if (target.toLowerCase(Locale.ROOT).startsWith("raio:")) {
            Audience aud = Audience.parse(target, null);
            if (aud == null) {
                msg().send(sender, "raio_invalido", Placeholder.unparsed("valor", target));
                return;
            }
            Location at = locationOf(sender);
            if (at == null) {
                msg().send(sender, "sem_local");
                return;
            }
            plugin.engine().play(effect, at, sender instanceof Player p ? p : null, aud, null, vars);
            msg().send(sender, "tocado", Placeholder.unparsed("efeito", effect.id()), Placeholder.unparsed("alvo", target));
            return;
        }
        Player p = Bukkit.getPlayerExact(target);
        if (p == null) {
            msg().send(sender, "jogador_nao_encontrado", Placeholder.unparsed("jogador", target));
            return;
        }
        plugin.engine().play(effect, p.getLocation(), p, null, null, vars);
        msg().send(sender, "tocado", Placeholder.unparsed("efeito", effect.id()), Placeholder.unparsed("alvo", p.getName()));
    }

    private static Location locationOf(CommandSender sender) {
        if (sender instanceof Entity e) {
            return e.getLocation();
        }
        if (sender instanceof BlockCommandSender b) {
            return b.getBlock().getLocation().add(0.5, 1, 0.5);
        }
        return null;
    }

    private void stop(CommandSender sender, String[] args) {
        int n;
        if (args.length >= 2) {
            Player p = Bukkit.getPlayerExact(args[1]);
            if (p == null) {
                msg().send(sender, "jogador_nao_encontrado", Placeholder.unparsed("jogador", args[1]));
                return;
            }
            n = plugin.engine().stop(p);
        } else if (sender instanceof Player p) {
            n = plugin.engine().stop(p);
        } else {
            n = plugin.engine().stopAll();
        }
        msg().send(sender, "parado", Placeholder.unparsed("total", String.valueOf(n)));
    }

    private static String escape(String s) {
        return s.replace("<", "\\<");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("pack"));
            if (sender.hasPermission("aincrad.effects.play")) {
                subs.addAll(List.of("tocar", "lista", "parar"));
            }
            if (sender.hasPermission("aincrad.admin")) {
                subs.add("reload");
            }
            return filter(subs, args[0]);
        }
        if (!sender.hasPermission("aincrad.effects.play")) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("tocar")) {
            return filter(new ArrayList<>(plugin.engine().ids()), args[1]);
        }
        if ((args.length == 3 && sub.equals("tocar")) || (args.length == 2 && sub.equals("parar"))) {
            List<String> opts = new ArrayList<>();
            if (sub.equals("tocar")) {
                opts.addAll(List.of("@a", "@s", "raio:16"));
            }
            Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
            return filter(opts, args[args.length - 1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
