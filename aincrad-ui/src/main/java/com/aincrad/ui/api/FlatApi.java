package com.aincrad.ui.api;

import org.bukkit.entity.Player;

import java.util.function.Function;

/**
 * Telas planas do AincradUI (loja, e o que mais os outros plugins quiserem), via
 * {@link AincradUIAPI#flat()}.
 *
 * <pre>
 * FlatApi flat = ui.flat();
 * flat.prepare(new MinhaTela(...));          // ao ligar: põe as peças no pack
 * if (flat.canShow(player)) flat.open(player, new MinhaTela(...));
 * </pre>
 */
public interface FlatApi {

    /** O jogador tem o pack e as telas planas estão ligadas. Se false, use um menu de baú. */
    boolean canShow(Player player);

    /**
     * Passada de preparação: chama {@code screen.draw} sem jogador para registrar as peças no
     * pack. Chame ao ligar o plugin e depois de recarregar configs que mudem o que a tela mostra.
     * Se entrar peça nova, o pack é refeito sozinho logo depois.
     */
    void prepare(FlatScreen screen);

    /** Abre a tela (fecha outra tela plana ou menu que estiver aberto). */
    void open(Player player, FlatScreen screen);

    boolean isOpen(Player player);

    void close(Player player);

    /** Textura para usar em {@link FlatDraw#icon} como ícone do material (item ou bloco). */
    String itemTexture(org.bukkit.Material material);

    /**
     * Energia mostrada na barra azul da HUD em barras: devolve {atual, máximo}. Sem provedor,
     * mostra a fome (0 a 20).
     */
    void setEnergyProvider(Function<Player, int[]> provider);
}
