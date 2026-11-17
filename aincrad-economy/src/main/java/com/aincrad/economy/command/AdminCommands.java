package com.aincrad.economy.command;

import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import com.aincrad.economy.EcoMath;
import com.aincrad.economy.EconomyService;
import com.aincrad.economy.shop.ShopService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /aincrad col ...} e {@code /aincrad loja ...}. */
public final class AdminCommands {

    private AdminCommands() {
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

    /** /aincrad col dar|tirar|definir|ver. */
    public static SubCommand col(EconomyService eco) {
        return new SubCommand() {
            @Override public String name() { return "col"; }
            @Override public String permission() { return "aincrad.economy.admin"; }
            @Override public String usage() { return "col dar|tirar|definir <jogador> <qtd>"; }
            @Override public Component description() { return eco.messages().get("admin.descricao_col"); }

            @Override
            public void execute(CommandSender sender, String[] args) {
                Messages msg = eco.messages();
                if (args.length < 2) {
                    msg.send(sender, "admin.uso_col");
                    return;
                }
                OfflinePlayer t = EconomyService.find(args[1]);
                if (t == null) {
                    msg.send(sender, "col.desconhecido", Messages.vars("jogador", args[1]));
                    return;
                }
                String name = t.getName() == null ? args[1] : t.getName();
                String op = args[0].toLowerCase(Locale.ROOT);
                if (op.equals("ver")) {
                    eco.accounts().fetch(t.getUniqueId()).thenAccept(b -> com.aincrad.core.AincradCore.get().runSync(() ->
                            msg.send(sender, "admin.feito", Messages.vars("jogador", name), eco.col(Math.max(0, b)))));
                    return;
                }
                long n = args.length > 2 ? EcoMath.parse(args[2]) : -1;
                if (n < 0) {
                    msg.send(sender, "admin.uso_col");
                    return;
                }
                boolean ok = switch (op) {
                    case "dar" -> eco.deposit(t.getUniqueId(), n, "admin");
                    case "tirar" -> eco.withdraw(t.getUniqueId(), n, "admin");
                    case "definir" -> eco.set(t.getUniqueId(), n, "admin");
                    default -> {
                        msg.send(sender, "admin.uso_col");
                        yield true;
                    }
                };
                if (!ok) {
                    msg.send(sender, "admin.falhou");
                    return;
                }
                long cached = eco.accounts().cached(t.getUniqueId());
                if (cached >= 0) {
                    msg.send(sender, "admin.feito", Messages.vars("jogador", name), eco.col(cached));
                } else {
                    // Offline: o depósito ainda está a caminho do banco; mostra o saldo depois.
                    Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("AincradEconomy"), () ->
                            eco.accounts().fetch(t.getUniqueId()).thenAccept(b -> com.aincrad.core.AincradCore.get().runSync(() ->
                                    msg.send(sender, "admin.feito", Messages.vars("jogador", name), eco.col(Math.max(0, b))))), 10L);
                }
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return switch (args.length) {
                    case 1 -> filter(List.of("dar", "tirar", "definir", "ver"), args[0]);
                    case 2 -> filter(players(), args[1]);
                    default -> List.of();
                };
            }
        };
    }

    /** /aincrad loja criar|npc|remover|listar|abrir. */
    public static SubCommand shop(EconomyService eco, ShopService shops) {
        return new SubCommand() {
            @Override public String name() { return "loja"; }
            @Override public String permission() { return "aincrad.economy.admin"; }
            @Override public String usage() { return "loja criar|npc <id>, remover, listar, abrir <id> [jogador]"; }
            @Override public Component description() { return eco.messages().get("admin.descricao_loja"); }

            @Override
            public void execute(CommandSender sender, String[] args) {
                Messages msg = eco.messages();
                if (args.length == 0) {
                    msg.send(sender, "admin.uso_loja");
                    return;
                }
                String op = args[0].toLowerCase(Locale.ROOT);
                if (op.equals("listar")) {
                    msg.send(sender, "admin.loja_lista", Messages.vars("lojas", String.join(", ", shops.ids())));
                    msg.send(sender, "admin.loja_ligacoes", Messages.vars("n", shops.linkCount()));
                    return;
                }
                if (op.equals("abrir") && args.length > 1) {
                    Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
                    if (t == null) {
                        msg.send(sender, "col.desconhecido", Messages.vars("jogador", args.length > 2 ? args[2] : "?"));
                        return;
                    }
                    shops.open(t, args[1]);
                    return;
                }
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "geral.so_jogador");
                    return;
                }
                switch (op) {
                    case "criar", "npc" -> {
                        if (args.length < 2 || !shops.ids().contains(args[1].toLowerCase(Locale.ROOT))) {
                            msg.send(p, "loja.desconhecida", Messages.vars("id", args.length < 2 ? "?" : args[1]));
                            return;
                        }
                        String id = args[1].toLowerCase(Locale.ROOT);
                        if (op.equals("criar")) {
                            Block b = p.getTargetBlockExact(5);
                            if (b == null) {
                                msg.send(p, "admin.olhe_bloco");
                                return;
                            }
                            shops.link(ShopService.ref(b), id);
                            msg.send(p, "admin.loja_ligada", Messages.vars("id", id, "alvo", ShopService.describe(b.getLocation())));
                        } else {
                            Entity e = p.getTargetEntity(5);
                            if (e == null || e instanceof Player) {
                                msg.send(p, "admin.olhe_entidade");
                                return;
                            }
                            shops.link(ShopService.ref(e), id);
                            msg.send(p, "admin.loja_ligada", Messages.vars("id", id, "alvo", e.getType().getKey().getKey()));
                        }
                    }
                    case "remover" -> {
                        Entity e = p.getTargetEntity(5);
                        Block b = p.getTargetBlockExact(5);
                        boolean removed = (e != null && shops.unlink(ShopService.ref(e))) || (b != null && shops.unlink(ShopService.ref(b)));
                        msg.send(p, removed ? "admin.loja_removida" : "admin.loja_nada");
                    }
                    default -> msg.send(p, "admin.uso_loja");
                }
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return switch (args.length) {
                    case 1 -> filter(List.of("criar", "npc", "remover", "listar", "abrir"), args[0]);
                    case 2 -> args[0].equalsIgnoreCase("remover") || args[0].equalsIgnoreCase("listar")
                            ? List.of() : filter(new ArrayList<>(shops.ids()), args[1]);
                    case 3 -> args[0].equalsIgnoreCase("abrir") ? filter(players(), args[2]) : List.of();
                    default -> List.of();
                };
            }
        };
    }
}
