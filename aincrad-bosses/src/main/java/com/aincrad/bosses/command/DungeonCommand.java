package com.aincrad.bosses.command;

import com.aincrad.bosses.maze.DungeonService;
import com.aincrad.bosses.model.DungeonRules.Def;
import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /aincrad masmorra criar|bau|trancar|destrancar|chave|mapa|listar|remover}. */
public final class DungeonCommand implements SubCommand {

    private final Messages msg;
    private final DungeonService dungeons;

    public DungeonCommand(Messages msg, DungeonService dungeons) {
        this.msg = msg;
        this.dungeons = dungeons;
    }

    @Override
    public String name() {
        return "masmorra";
    }

    @Override
    public String permission() {
        return "aincrad.admin";
    }

    @Override
    public String usage() {
        return "masmorra criar <id> <regiao>|bau <id>|trancar <id>|destrancar <id>|chave <id> [jogador]|mapa <id> [jogador]|listar|remover <id>";
    }

    @Override
    public Component description() {
        return msg.get("masmorra.descricao");
    }

    private void log(CommandSender actor, String action, String detail) {
        Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(actor, "bosses", action, detail));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
        Def d = id == null ? null : dungeons.defs().get(id);
        try {
            switch (op) {
                case "listar" -> {
                    if (dungeons.defs().isEmpty()) {
                        msg.send(sender, "masmorra.nenhuma");
                    }
                    for (Def x : dungeons.defs().values()) {
                        msg.send(sender, "masmorra.linha", Messages.vars("id", x.id(), "regiao", x.region(),
                                "bau", x.chest() == null ? "—" : x.chest(), "trancada", x.locked() ? "sim" : "não"));
                    }
                    dungeons.errors().forEach(e -> msg.send(sender, "masmorra.erro", Messages.vars("erro", e)));
                }
                case "criar" -> {
                    if (id == null || !id.matches("[a-z0-9_\\-]{1,32}") || args.length < 3
                            || AincradCore.get().regions().get(args[2].toLowerCase(Locale.ROOT)) == null) {
                        msg.send(sender, "masmorra.uso");
                        return;
                    }
                    dungeons.create(id, args[2].toLowerCase(Locale.ROOT));
                    msg.send(sender, "masmorra.criada", Messages.vars("id", id));
                    log(sender, "masmorra.criar", id + " em " + args[2]);
                }
                case "bau" -> {
                    Block b = sender instanceof Player p ? p.getTargetBlockExact(5) : null;
                    if (d == null || b == null) {
                        msg.send(sender, "masmorra.uso");
                        return;
                    }
                    dungeons.setChest(id, b);
                    msg.send(sender, "masmorra.bau_definido", Messages.vars("id", id));
                    log(sender, "masmorra.bau", id);
                }
                case "trancar", "destrancar" -> {
                    if (d == null) {
                        msg.send(sender, "masmorra.uso");
                        return;
                    }
                    dungeons.setLocked(id, op.equals("trancar"));
                    msg.send(sender, op.equals("trancar") ? "masmorra.trancou" : "masmorra.destrancou", Messages.vars("id", id));
                    log(sender, "masmorra." + op, id);
                }
                case "chave", "mapa" -> {
                    Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
                    if (d == null || t == null) {
                        msg.send(sender, "masmorra.uso");
                        return;
                    }
                    ItemStack it = op.equals("chave") ? dungeons.key(d) : dungeons.map(d);
                    t.getInventory().addItem(it).values().forEach(rest -> t.getWorld().dropItemNaturally(t.getLocation(), rest));
                    msg.send(sender, "masmorra.item_dado", Messages.vars("item", op, "id", id, "jogador", t.getName()));
                    log(sender, "masmorra." + op, id + " para " + t.getName());
                }
                case "remover" -> {
                    if (d == null) {
                        msg.send(sender, "masmorra.uso");
                        return;
                    }
                    dungeons.delete(id);
                    msg.send(sender, "masmorra.removida", Messages.vars("id", id));
                    log(sender, "masmorra.remover", id);
                }
                default -> msg.send(sender, "masmorra.uso");
            }
        } catch (IOException e) {
            msg.send(sender, "masmorra.erro", Messages.vars("erro", String.valueOf(e.getMessage())));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> opts = new ArrayList<>();
        if (args.length == 1) {
            opts.addAll(List.of("criar", "bau", "trancar", "destrancar", "chave", "mapa", "listar", "remover"));
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("criar")) {
            opts.addAll(dungeons.defs().keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("criar")) {
            AincradCore.get().regions().all().forEach(r -> opts.add(r.name()));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return opts;
    }
}
