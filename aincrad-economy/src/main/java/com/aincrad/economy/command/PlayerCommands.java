package com.aincrad.economy.command;

import com.aincrad.core.message.Messages;
import com.aincrad.economy.EcoMath;
import com.aincrad.economy.EconomyService;
import com.aincrad.economy.trade.TradeService;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /col}, {@code /pagar} e {@code /trocar}. */
public final class PlayerCommands implements TabExecutor {

    private final EconomyService eco;
    private final TradeService trade;

    public PlayerCommands(EconomyService eco, TradeService trade) {
        this.eco = eco;
        this.trade = trade;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "col" -> col(sender, args);
            case "pagar" -> pay(sender, args);
            case "trocar" -> trade(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void col(CommandSender sender, String[] args) {
        Messages msg = eco.messages();
        if (args.length > 0 && sender.hasPermission("aincrad.economy.admin")) {
            OfflinePlayer t = EconomyService.find(args[0]);
            if (t == null) {
                msg.send(sender, "col.desconhecido", Messages.vars("jogador", args[0]));
                return;
            }
            String name = t.getName() == null ? args[0] : t.getName();
            long cached = eco.accounts().cached(t.getUniqueId());
            if (cached >= 0) {
                msg.send(sender, "col.saldo_outro", Messages.vars("jogador", name), eco.col(cached));
                return;
            }
            eco.accounts().fetch(t.getUniqueId()).thenAccept(bal -> com.aincrad.core.AincradCore.get().runSync(() ->
                    msg.send(sender, "col.saldo_outro", Messages.vars("jogador", name), eco.col(Math.max(0, bal)))));
            return;
        }
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        msg.send(p, "col.saldo", eco.col(eco.balance(p)));
    }

    private void pay(CommandSender sender, String[] args) {
        Messages msg = eco.messages();
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        if (args.length < 2) {
            msg.send(p, "pagar.uso");
            return;
        }
        OfflinePlayer t = EconomyService.find(args[0]);
        if (t == null) {
            msg.send(p, "col.desconhecido", Messages.vars("jogador", args[0]));
            return;
        }
        if (t.getUniqueId().equals(p.getUniqueId())) {
            msg.send(p, "pagar.voce_mesmo");
            return;
        }
        long amount = EcoMath.parse(args[1]);
        if (amount <= 0) {
            msg.send(p, "pagar.invalido", Messages.vars("valor", args[1]));
            return;
        }
        long min = eco.config().integer("pagar.minimo", 1, 1, Integer.MAX_VALUE);
        if (amount < min) {
            msg.send(p, "pagar.minimo", eco.col(min));
            return;
        }
        if (!eco.has(p, amount)) {
            msg.send(p, "pagar.sem_saldo", eco.col(eco.balance(p)));
            return;
        }
        long tax = EcoMath.tax(amount, eco.config().decimal("pagar.taxa", 0, 0, 1));
        String name = t.getName() == null ? args[0] : t.getName();
        if (!eco.transfer(p.getUniqueId(), t.getUniqueId(), amount, tax, "pagar")) {
            msg.send(p, "pagar.falhou");
            return;
        }
        msg.send(p, "pagar.enviado", eco.col(amount), Messages.vars("jogador", name));
        if (tax > 0) {
            msg.send(p, "pagar.enviado_taxa", Messages.vars("taxa", eco.format(tax)));
        }
        if (t instanceof Player online) {
            msg.send(online, "pagar.recebido", eco.col(amount - tax), Messages.vars("jogador", p.getName()));
        }
    }

    private void trade(CommandSender sender, String[] args) {
        Messages msg = eco.messages();
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        if (args.length == 0) {
            msg.send(p, "troca.uso");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "aceitar" -> trade.accept(p);
            case "recusar" -> trade.deny(p);
            default -> {
                Player t = p.getServer().getPlayerExact(args[0]);
                if (t == null) {
                    msg.send(p, "col.desconhecido", Messages.vars("jogador", args[0]));
                    return;
                }
                trade.request(p, t);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("trocar")) {
            for (String o : List.of("aceitar", "recusar")) {
                if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o);
                }
            }
        }
        for (Player pl : sender.getServer().getOnlinePlayers()) {
            if (pl.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add(pl.getName());
            }
        }
        return out;
    }
}
