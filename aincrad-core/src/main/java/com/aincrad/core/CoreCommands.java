package com.aincrad.core;

import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.core.region.RegionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Subcomandos do próprio core: varinha, regiao e reload. */
final class CoreCommands {

    private CoreCommands() {
    }

    static void register(AincradCorePlugin core) {
        Messages msg = core.coreMessages();
        RegionManager regions = core.regions();

        core.commands().register(core, new SubCommand() {
            @Override
            public String name() {
                return "varinha";
            }

            @Override
            public String permission() {
                return "aincrad.admin";
            }

            @Override
            public String usage() {
                return "varinha";
            }

            @Override
            public Component description() {
                return Component.text("Varinha para marcar os cantos de uma região");
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "geral.so_jogador");
                    return;
                }
                p.getInventory().addItem(regions.wand());
                msg.send(p, "varinha.recebida");
            }
        });

        core.commands().register(core, new SubCommand() {
            @Override
            public String name() {
                return "regiao";
            }

            @Override
            public String permission() {
                return "aincrad.admin";
            }

            @Override
            public String usage() {
                return "regiao criar|remover|listar|info [nome]";
            }

            @Override
            public Component description() {
                return Component.text("Regiões nomeadas (salas, cidades, labirintos...)");
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "criar" -> create(sender, args);
                    case "remover" -> {
                        if (args.length < 2) {
                            msg.send(sender, "regiao.uso");
                            return;
                        }
                        String name = args[1].toLowerCase(Locale.ROOT);
                        regions.remove(name).thenAccept(ok -> core.api().runSync(() ->
                                msg.send(sender, ok ? "regiao.removida" : "regiao.nao_existe", Messages.vars("nome", name))));
                    }
                    case "listar" -> {
                        String names = regions.all().stream().map(Region::name).sorted().collect(Collectors.joining(", "));
                        msg.send(sender, "regiao.lista", Messages.vars("total", regions.all().size(), "nomes", names.isEmpty() ? "—" : names));
                    }
                    case "info" -> info(sender, args);
                    default -> msg.send(sender, "regiao.uso");
                }
            }

            private void create(CommandSender sender, String[] args) {
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "geral.so_jogador");
                    return;
                }
                if (args.length < 2) {
                    msg.send(p, "regiao.uso");
                    return;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!Region.validName(name)) {
                    msg.send(p, "regiao.nome_invalido");
                    return;
                }
                if (regions.get(name) != null) {
                    msg.send(p, "regiao.ja_existe", Messages.vars("nome", name));
                    return;
                }
                RegionManager.Selection s = regions.selection(p);
                if (!s.complete()) {
                    msg.send(p, "regiao.sem_selecao");
                    return;
                }
                if (!s.a().getWorld().equals(s.b().getWorld())) {
                    msg.send(p, "regiao.mundos_diferentes");
                    return;
                }
                Location a = s.a();
                Location b = s.b();
                Region r = Region.of(name, a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                        b.getBlockX(), b.getBlockY(), b.getBlockZ(), "admin");
                regions.save(r);
                msg.send(p, "regiao.criada", Messages.vars("nome", name, "blocos", r.volume()));
            }

            private void info(CommandSender sender, String[] args) {
                if (args.length >= 2) {
                    Region r = regions.get(args[1].toLowerCase(Locale.ROOT));
                    if (r == null) {
                        msg.send(sender, "regiao.nao_existe", Messages.vars("nome", args[1]));
                        return;
                    }
                    msg.send(sender, "regiao.info", Messages.vars("nome", r.name(), "mundo", r.world(),
                            "min", r.minX() + " " + r.minY() + " " + r.minZ(), "max", r.maxX() + " " + r.maxY() + " " + r.maxZ()));
                    return;
                }
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "regiao.uso");
                    return;
                }
                List<Region> here = regions.at(p.getLocation());
                if (here.isEmpty()) {
                    msg.send(p, "regiao.nenhuma_aqui");
                } else {
                    msg.send(p, "regiao.aqui", Messages.vars("nomes", here.stream().map(Region::name).collect(Collectors.joining(", "))));
                }
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                if (args.length <= 1) {
                    return List.of("criar", "remover", "listar", "info");
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("remover") || args[0].equalsIgnoreCase("info"))) {
                    return regions.all().stream().map(Region::name).toList();
                }
                return List.of();
            }
        });

        core.commands().register(core, new SubCommand() {
            @Override
            public String name() {
                return "reload";
            }

            @Override
            public String permission() {
                return "aincrad.admin";
            }

            @Override
            public String usage() {
                return "reload";
            }

            @Override
            public Component description() {
                return Component.text("Recarrega configs e mensagens");
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                List<String> reloaded = core.reloadAll();
                msg.send(sender, "geral.recarregado", Messages.vars("plugins", String.join(", ", reloaded)));
            }
        });
    }
}
