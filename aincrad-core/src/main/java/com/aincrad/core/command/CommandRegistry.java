package com.aincrad.core.command;

import com.aincrad.core.message.Messages;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Dono do comando {@code /aincrad} (registrado pela API de comandos do Paper, com Brigadier).
 * Os plugins penduram subcomandos aqui; a busca é feita na hora de rodar, então um plugin que
 * liga depois do core funciona igual.
 */
public final class CommandRegistry {

    private record Entry(Plugin owner, SubCommand command) {
    }

    private final JavaPlugin core;
    private final Messages messages;
    private final Map<String, Entry> commands = new ConcurrentHashMap<>();

    public CommandRegistry(JavaPlugin core, Messages messages) {
        this.core = core;
        this.messages = messages;
    }

    /** Chamado uma vez no onEnable do core. */
    public void install() {
        core.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(Commands.literal("aincrad")
                            .executes(ctx -> {
                                help(ctx.getSource().getSender());
                                return 1;
                            })
                            .then(Commands.argument("args", StringArgumentType.greedyString())
                                    .suggests((ctx, builder) -> {
                                        suggest(ctx.getSource(), builder);
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        dispatch(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "args"));
                                        return 1;
                                    }))
                            .build(),
                    "Comandos do Aincrad", List.of());
        });
    }

    public void register(Plugin owner, SubCommand cmd) {
        String n = cmd.name().toLowerCase(Locale.ROOT);
        Entry old = commands.put(n, new Entry(owner, cmd));
        if (old != null && old.owner() != owner) {
            core.getLogger().warning("/aincrad " + n + " de " + old.owner().getName() + " foi substituído por " + owner.getName());
        }
    }

    /** Tira todos os subcomandos de um plugin (quando ele desliga). */
    public void unregisterAll(Plugin owner) {
        commands.values().removeIf(e -> e.owner() == owner);
    }

    public List<SubCommand> all() {
        Map<String, SubCommand> sorted = new TreeMap<>();
        commands.forEach((k, v) -> sorted.put(k, v.command()));
        return List.copyOf(sorted.values());
    }

    static String[] split(String line) {
        String t = line.trim();
        return t.isEmpty() ? new String[0] : t.split("\\s+");
    }

    private static boolean allowed(CommandSender s, SubCommand c) {
        return c.permission() == null || s.hasPermission(c.permission());
    }

    public void dispatch(CommandSender sender, String line) {
        String[] parts = split(line);
        if (parts.length == 0) {
            help(sender);
            return;
        }
        Entry e = commands.get(parts[0].toLowerCase(Locale.ROOT));
        if (e == null) {
            messages.send(sender, "geral.desconhecido", Messages.vars("sub", parts[0]));
            return;
        }
        if (!allowed(sender, e.command())) {
            messages.send(sender, "geral.sem_permissao");
            return;
        }
        try {
            e.command().execute(sender, Arrays.copyOfRange(parts, 1, parts.length));
        } catch (RuntimeException ex) {
            core.getLogger().log(Level.SEVERE, "Erro em /aincrad " + parts[0] + " (" + e.owner().getName() + ")", ex);
            messages.send(sender, "geral.erro");
        }
    }

    private void help(CommandSender sender) {
        List<SubCommand> visible = new ArrayList<>();
        for (SubCommand c : all()) {
            if (allowed(sender, c)) {
                visible.add(c);
            }
        }
        if (visible.isEmpty()) {
            messages.send(sender, "ajuda.vazio");
            return;
        }
        sender.sendMessage(messages.get("ajuda.titulo"));
        for (SubCommand c : visible) {
            sender.sendMessage(messages.get("ajuda.linha", Messages.vars("uso", c.usage(), "descricao", c.description())));
        }
    }

    /** Sugestões: o nome do subcomando ou o que ele mesmo sugere para o último argumento. */
    private void suggest(CommandSourceStack source, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.split(" ", -1);
        CommandSender sender = source.getSender();
        List<String> options;
        if (parts.length <= 1) {
            options = new ArrayList<>();
            for (SubCommand c : all()) {
                if (allowed(sender, c)) {
                    options.add(c.name());
                }
            }
        } else {
            Entry e = commands.get(parts[0].toLowerCase(Locale.ROOT));
            if (e == null || !allowed(sender, e.command())) {
                return;
            }
            try {
                options = e.command().complete(sender, Arrays.copyOfRange(parts, 1, parts.length));
            } catch (RuntimeException ex) {
                options = Collections.emptyList();
            }
        }
        String last = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        SuggestionsBuilder at = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                at.suggest(o);
            }
        }
        builder.add(at);
    }
}
