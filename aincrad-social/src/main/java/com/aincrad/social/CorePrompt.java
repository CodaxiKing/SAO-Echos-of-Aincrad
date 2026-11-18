package com.aincrad.social;

import com.aincrad.core.AincradCore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * A janela de aceitar ou recusar um convite, pela interface do AincradCore (o AincradUI, se
 * instalado). Só é carregada quando o AincradCore está no servidor.
 */
final class CorePrompt {

    private CorePrompt() {
    }

    /** @return false se a janela não abriu (sem a interface, ou o jogador está com um menu aberto) */
    static boolean show(Player player, Component question, String yesCommand, String noCommand) {
        return AincradCore.get().ui().prompt(player, question, p -> p.performCommand(yesCommand), p -> p.performCommand(noCommand));
    }
}
