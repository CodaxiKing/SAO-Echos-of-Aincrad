package com.aincrad.core.menu;

import org.bukkit.entity.Player;

/**
 * Desenha um {@link Menu} para um jogador. O core traz o de baú; o AincradUI registra o de cursor
 * e um plugin de Bedrock pode registrar o de formulário. O {@link MenuService} escolhe o de maior
 * prioridade que aceita o jogador.
 */
public interface MenuRenderer {

    /** Nome para logs e config (ex.: "bau", "cursor", "bedrock"). */
    String id();

    /** Maior ganha. O baú é 0. */
    int priority();

    /** Este renderizador serve para o jogador agora? (ex.: Bedrock, tem o resource pack) */
    boolean supports(Player player);

    void open(Player player, Menu menu);
}
