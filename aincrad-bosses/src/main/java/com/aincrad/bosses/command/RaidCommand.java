package com.aincrad.bosses.command;

import com.aincrad.bosses.fight.FightManager;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.Raid;
import com.aincrad.bosses.raid.RaidService;
import com.aincrad.core.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /raid}: abre o quadro. {@code /raid criar <chefe> <dd/MM> <HH:mm> [tanques] [danos] [suportes]},
 * {@code /raid entrar <n> <funcao>}, {@code /raid sair <n>}, {@code /raid cancelar <n>}.
 */
public final class RaidCommand implements TabExecutor {

    private final FightManager fights;
    private final RaidService raids;
    private final Messages msg;

    public RaidCommand(FightManager fights, RaidService raids) {
        this.fights = fights;
        this.raids = raids;
        this.msg = fights.messages();
    }

    private static int count(String[] args, int i, int def) {
        try {
            return args.length > i ? Math.max(0, Math.min(50, Integer.parseInt(args[i]))) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Raid raid(Player p, String[] args) {
        try {
            Raid r = raids.all().get(Long.parseLong(args[1]));
            if (r != null) {
                return r;
            }
        } catch (RuntimeException e) {
            // sem número ou número ilegível
        }
        msg.send(p, "raid.desconhecida");
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return true;
        }
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (op) {
            case "" -> raids.openBoard(p);
            case "criar" -> {
                BossDef def = args.length > 3 ? fights.registry().get(args[1]) : null;
                if (args.length < 4) {
                    msg.send(p, "raid.uso");
                    return true;
                }
                if (def == null) {
                    msg.send(p, "admin.chefe_desconhecido", Messages.vars("id", args[1]));
                    return true;
                }
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime when = Raid.parseWhen(args[2], args[3], now);
                if (when == null || when.isBefore(now)) {
                    msg.send(p, "raid.data_invalida");
                    return true;
                }
                int tanks = count(args, 4, 2);
                int damage = count(args, 5, 6);
                int support = count(args, 6, 2);
                if (tanks + damage + support == 0) {
                    msg.send(p, "raid.uso");
                    return true;
                }
                raids.create(p, def, when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), tanks, damage, support);
            }
            case "entrar" -> {
                Raid r = args.length > 2 ? raid(p, args) : null;
                Raid.Role role = args.length > 2 ? Raid.Role.parse(args[2]) : null;
                if (args.length < 3 || (r != null && role == null)) {
                    msg.send(p, "raid.uso");
                } else if (r != null) {
                    raids.join(p, r, role, true);
                }
            }
            case "sair" -> {
                Raid r = args.length > 1 ? raid(p, args) : null;
                if (args.length < 2) {
                    msg.send(p, "raid.uso");
                } else if (r != null) {
                    raids.leave(p, r);
                }
            }
            case "cancelar" -> {
                Raid r = args.length > 1 ? raid(p, args) : null;
                if (args.length < 2) {
                    msg.send(p, "raid.uso");
                } else if (r != null) {
                    if (r.leader().equals(p.getUniqueId()) || p.hasPermission("aincrad.admin")) {
                        raids.cancel(r);
                        msg.send(p, "raid.cancelada", raids.vars(r));
                    } else {
                        msg.send(p, "raid.so_lider");
                    }
                }
            }
            default -> msg.send(p, "raid.uso");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> opts = new ArrayList<>();
        if (args.length == 1) {
            opts.addAll(List.of("criar", "entrar", "sair", "cancelar"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("criar")) {
            fights.registry().all().forEach(d -> opts.add(d.id()));
        } else if (args.length == 2) {
            raids.all().keySet().forEach(id -> opts.add(String.valueOf(id)));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("entrar")) {
            opts.addAll(List.of("tanque", "dano", "suporte"));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        opts.removeIf(o -> !o.startsWith(last));
        return opts;
    }
}
