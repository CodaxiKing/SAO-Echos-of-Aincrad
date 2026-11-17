package com.aincrad.rpg.command;

import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import com.aincrad.rpg.RpgService;
import com.aincrad.rpg.item.ItemService;
import com.aincrad.rpg.item.Rarity;
import com.aincrad.rpg.model.Progress;
import com.aincrad.rpg.model.Stat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /aincrad rpg ...}: xp dar|tirar, level, item, resetar, info.
 */
public final class AdminCommand implements SubCommand {

    private final RpgService rpg;
    private final ItemService items;

    public AdminCommand(RpgService rpg, ItemService items) {
        this.rpg = rpg;
        this.items = items;
    }

    @Override
    public String name() {
        return "rpg";
    }

    @Override
    public String permission() {
        return "aincrad.rpg.admin";
    }

    @Override
    public String usage() {
        return "rpg xp|level|item|resetar|info ...";
    }

    @Override
    public Component description() {
        return rpg.messages().get("admin.descricao");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Messages msg = rpg.messages();
        if (args.length == 0) {
            msg.send(sender, "admin.uso");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "xp" -> xp(sender, args);
            case "level" -> level(sender, args);
            case "item" -> item(sender, args);
            case "resetar" -> {
                Player t = target(sender, args, 1);
                if (t != null) {
                    rpg.progress(t).resetStats();
                    rpg.applyAttributes(t);
                    msg.send(sender, "admin.resetado", Messages.vars("jogador", t.getName()));
                }
            }
            case "info" -> {
                Player t = target(sender, args, 1);
                if (t != null) {
                    Progress p = rpg.progress(t);
                    msg.send(sender, "admin.info", Messages.vars("jogador", t.getName(), "level", p.level(), "xp", p.xp(),
                            "pontos", p.points(), "forca", p.stat(Stat.FORCA), "agilidade", p.stat(Stat.AGILIDADE),
                            "vitalidade", p.stat(Stat.VITALIDADE)));
                }
            }
            default -> msg.send(sender, "admin.uso");
        }
    }

    private void xp(CommandSender sender, String[] args) {
        Messages msg = rpg.messages();
        if (args.length < 4) {
            msg.send(sender, "admin.uso");
            return;
        }
        Player t = target(sender, args, 2);
        Long n = number(sender, args[3]);
        if (t == null || n == null) {
            return;
        }
        if (args[1].equalsIgnoreCase("dar")) {
            long got = rpg.giveXp(t, n, "admin", true);
            msg.send(sender, "admin.xp_dado", Messages.vars("qtd", got, "jogador", t.getName()));
        } else if (args[1].equalsIgnoreCase("tirar")) {
            long took = rpg.takeXp(t, n, true);
            msg.send(sender, "admin.xp_tirado", Messages.vars("qtd", took, "jogador", t.getName()));
        } else {
            msg.send(sender, "admin.uso");
        }
    }

    private void level(CommandSender sender, String[] args) {
        if (args.length < 3) {
            rpg.messages().send(sender, "admin.uso");
            return;
        }
        Player t = target(sender, args, 1);
        Long n = number(sender, args[2]);
        if (t == null || n == null) {
            return;
        }
        rpg.setLevel(t, (int) Math.min(Integer.MAX_VALUE, n));
        rpg.messages().send(sender, "admin.level_definido", Messages.vars("jogador", t.getName(), "level", rpg.progress(t).level()));
    }

    private void item(CommandSender sender, String[] args) {
        Messages msg = rpg.messages();
        if (args.length < 2) {
            msg.send(sender, "admin.uso");
            return;
        }
        if (items.template(args[1]) == null) {
            msg.send(sender, "admin.item_desconhecido", Messages.vars("id", args[1]));
            return;
        }
        Rarity r = null;
        if (args.length > 2 && !args[2].equalsIgnoreCase("aleatoria")) {
            r = Rarity.parse(args[2]);
            if (r == null) {
                msg.send(sender, "admin.raridade_invalida");
                return;
            }
        }
        Player t = args.length > 3 ? target(sender, args, 3) : sender instanceof Player p ? p : null;
        if (t == null) {
            if (args.length <= 3) {
                msg.send(sender, "geral.so_jogador");
            }
            return;
        }
        ItemStack it = items.create(args[1], r);
        t.getInventory().addItem(it).values().forEach(rest -> t.getWorld().dropItemNaturally(t.getLocation(), rest));
        msg.send(sender, "admin.item_dado", Messages.vars("id", args[1].toLowerCase(Locale.ROOT), "jogador", t.getName()));
    }

    private Player target(CommandSender sender, String[] args, int i) {
        if (args.length <= i) {
            rpg.messages().send(sender, "admin.uso");
            return null;
        }
        Player t = Bukkit.getPlayerExact(args[i]);
        if (t == null) {
            rpg.messages().send(sender, "level.offline");
        }
        return t;
    }

    private Long number(CommandSender sender, String s) {
        try {
            long v = Long.parseLong(s);
            if (v < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            rpg.messages().send(sender, "admin.numero_invalido", Messages.vars("valor", s));
            return null;
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> opts = switch (args.length) {
            case 1 -> List.of("xp", "level", "item", "resetar", "info");
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "xp" -> List.of("dar", "tirar");
                case "item" -> new ArrayList<>(items.ids());
                default -> players();
            };
            case 3 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "xp" -> players();
                case "item" -> List.of("aleatoria", "comum", "incomum", "raro", "epico", "lendario");
                default -> List.of();
            };
            case 4 -> args[0].equalsIgnoreCase("item") ? players() : List.of();
            default -> List.of();
        };
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }

    private static List<String> players() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(p.getName());
        }
        return out;
    }
}
