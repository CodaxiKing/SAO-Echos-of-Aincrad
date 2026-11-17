package com.aincrad.core;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Uma ação de administração de qualquer plugin Aincrad (liberar andar, dar Col, mudar level...).
 * Os plugins disparam depois de fazer a ação; o AincradAdmin ouve para montar o registro.
 *
 * <pre>
 * Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(sender, "floors", "andar.liberar", "andar 5"));
 * </pre>
 */
public final class AincradAdminActionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender actor;
    private final String plugin;
    private final String action;
    private final String detail;

    /**
     * @param actor  quem fez (jogador ou console)
     * @param plugin nome curto do plugin: "floors", "rpg", "economy"...
     * @param action ação em minúsculas com pontos: "andar.liberar", "col.dar"...
     * @param detail o que foi feito, em texto ("andar 5", "500 Col para Kirito")
     */
    public AincradAdminActionEvent(CommandSender actor, String plugin, String action, String detail) {
        this.actor = actor;
        this.plugin = plugin;
        this.action = action;
        this.detail = detail == null ? "" : detail;
    }

    public CommandSender getActor() {
        return actor;
    }

    public String getPlugin() {
        return plugin;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
