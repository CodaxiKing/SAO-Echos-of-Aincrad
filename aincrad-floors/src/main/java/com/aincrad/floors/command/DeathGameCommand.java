package com.aincrad.floors.command;

import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import com.aincrad.floors.DeathGameService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /aincrad anuncio iniciar|repetir|desfazer|estado} e
 * {@code /aincrad fim declarar|desfazer|estado}.
 */
public final class DeathGameCommand implements SubCommand {

    private final Messages msg;
    private final DeathGameService game;
    private final boolean end;

    /** @param end false = o comando do anúncio; true = o do fim de jogo */
    public DeathGameCommand(Messages msg, DeathGameService game, boolean end) {
        this.msg = msg;
        this.game = game;
        this.end = end;
    }

    @Override
    public String name() {
        return end ? "fim" : "anuncio";
    }

    @Override
    public String permission() {
        return "aincrad.admin";
    }

    @Override
    public String usage() {
        return end ? "fim declarar|desfazer|estado" : "anuncio iniciar|repetir|desfazer|estado";
    }

    @Override
    public Component description() {
        return msg.get(end ? "jogo.descricao_fim" : "jogo.descricao_anuncio");
    }

    private void log(CommandSender actor, String action) {
        Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(actor, "floors", action, ""));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (op.equals("estado")) {
            msg.send(sender, game.ended() ? "jogo.estado_fim" : game.announced() ? "jogo.estado_anunciado" : "jogo.estado_antes");
            return;
        }
        if (end) {
            switch (op) {
                case "declarar" -> {
                    if (game.ended()) {
                        msg.send(sender, "jogo.estado_fim");
                        return;
                    }
                    game.declare(List.of());
                    msg.send(sender, "jogo.fim_declarado");
                    log(sender, "fim.declarar");
                }
                case "desfazer" -> {
                    game.undoVictory();
                    msg.send(sender, "jogo.fim_desfeito");
                    log(sender, "fim.desfazer");
                }
                default -> msg.send(sender, "jogo.uso_fim");
            }
            return;
        }
        switch (op) {
            case "iniciar", "repetir" -> {
                switch (game.start(op.equals("repetir"))) {
                    case OK -> {
                        msg.send(sender, "jogo.anuncio_iniciado");
                        log(sender, "anuncio." + op);
                    }
                    case JA_FEITO -> msg.send(sender, "jogo.anuncio_ja_feito");
                    case EM_ANDAMENTO -> msg.send(sender, "jogo.anuncio_em_andamento");
                }
            }
            case "desfazer" -> {
                game.undo();
                msg.send(sender, "jogo.anuncio_desfeito");
                log(sender, "anuncio.desfazer");
            }
            default -> msg.send(sender, "jogo.uso_anuncio");
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> opts = new ArrayList<>(end ? List.of("declarar", "desfazer", "estado")
                : List.of("iniciar", "repetir", "desfazer", "estado"));
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        opts.removeIf(o -> args.length != 1 || !o.startsWith(last));
        return opts;
    }
}
