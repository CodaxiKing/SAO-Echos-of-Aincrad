package com.aincrad.ui.api;

import org.bukkit.entity.Player;

/**
 * Uma tela plana: desenhada direto na tela do jogador (não no mundo), com cursor. É redesenhada
 * sempre que o cursor anda ou um botão é clicado, então {@link #draw} deve ser rápido e ler o
 * estado da própria tela.
 */
public interface FlatScreen {

    /**
     * Desenha a tela. Na preparação ({@link FlatDraw#preparing()}), {@code viewer} é null e a
     * tela deve desenhar todas as variações (todas as linhas, ícones e estados).
     */
    void draw(FlatDraw d, Player viewer);

    /** A tela fechou (agachar, dano, sair...). */
    default void onClose(Player viewer) {
    }
}
