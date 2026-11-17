package com.aincrad.rpg.command;

import com.aincrad.core.message.Messages;
import com.aincrad.rpg.RpgService;
import com.aincrad.rpg.model.Progress;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /level [jogador]}. */
public final class LevelCommand implements TabExecutor {

    private final RpgService rpg;

    public LevelCommand(RpgService rpg) {
        this.rpg = rpg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = rpg.messages();
        if (args.length > 0) {
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t == null) {
                msg.send(sender, "level.offline");
                return true;
            }
            if (t != sender) {
                msg.send(sender, "level.outro", Messages.vars("jogador", t.getName(), "level", rpg.progress(t).level()));
                return true;
            }
        }
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return true;
        }
        Progress pr = rpg.progress(p);
        long next = rpg.settings().curve.toNext(pr.level());
        if (next <= 0) {
            msg.send(p, "level.maximo", Messages.vars("level", pr.level()));
            return true;
        }
        msg.send(p, "level.proprio", Messages.vars("level", pr.level(), "atual", pr.xp(), "proximo", next,
                "pct", Math.round(pr.xp() * 100.0 / next)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return null; // nomes dos jogadores online
    }
}
