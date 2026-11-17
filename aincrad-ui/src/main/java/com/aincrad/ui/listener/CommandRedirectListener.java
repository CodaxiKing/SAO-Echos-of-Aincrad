package com.aincrad.ui.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/**
 * O comando /aincrad é compartilhado pelos plugins do Aincrad (o Floors registra o principal).
 * Para não disputar o nome, o AincradUI registra /aincradui e redireciona "/aincrad ui ..." para ele.
 */
public final class CommandRedirectListener implements Listener {

    static String rewrite(String line) {
        String trimmed = line.stripLeading();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("aincrad ui") || lower.startsWith("aincrad ui ")) {
            return "aincradui" + trimmed.substring("aincrad ui".length());
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith("/")) {
            return;
        }
        String out = rewrite(msg.substring(1));
        if (out != null) {
            event.setMessage("/" + out);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String cmd = event.getCommand();
        String out = rewrite(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        if (out != null) {
            event.setCommand(out);
        }
    }
}
