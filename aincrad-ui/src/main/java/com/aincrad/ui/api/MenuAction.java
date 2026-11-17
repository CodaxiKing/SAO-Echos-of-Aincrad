package com.aincrad.ui.api;

import org.bukkit.entity.Player;

/** Ação executada quando o jogador escolhe uma entrada do menu. Roda na thread principal. */
@FunctionalInterface
public interface MenuAction {
    void run(Player player);
}
