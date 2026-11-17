package com.aincrad.ui.screen;

import org.bukkit.entity.Player;

/** O que acontece ao clicar num elemento de tela. */
@FunctionalInterface
public interface ElementAction {
    void run(Player player, MenuSession session);
}
