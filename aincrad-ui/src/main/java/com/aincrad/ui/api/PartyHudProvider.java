package com.aincrad.ui.api;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Quem aparece embaixo da barra de vida de um jogador, com barras menores (como os membros da
 * party no anime). Registrado por um plugin de party com {@link AincradUIAPI#setPartyHudProvider}.
 */
@FunctionalInterface
public interface PartyHudProvider {

    /** Outros membros da party do jogador, na ordem em que devem aparecer (sem o próprio). */
    List<Player> members(Player viewer);
}
