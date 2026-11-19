package com.aincrad.ui.hud;

/**
 * Onde a HUD em barras fica na tela de cada jogador.
 *
 * <p>A HUD é desenhada a partir do centro da tela, e o servidor não sabe a largura da tela de
 * ninguém. O padrão ({@code hud.barras.deslocamento_x: 232}) encosta nos cantos numa interface de
 * 480 pixels de largura (1920×1080 com a escala 4). Com outra janela ou outra escala a interface
 * tem outra largura, e a HUD fica longe do canto. Estas contas dizem quanto ela precisa andar.</p>
 */
public final class HudCalibration {

    /** Largura e altura mínimas da interface: o Minecraft não deixa a escala passar disto. */
    private static final int MIN_WIDTH = 320;
    private static final int MIN_HEIGHT = 240;

    private HudCalibration() {
    }

    /** A maior escala de interface que o Minecraft aceita nessa janela (a opção "Auto"). */
    public static int autoScale(int windowWidth, int windowHeight) {
        int scale = 1;
        while (windowWidth / (scale + 1) >= MIN_WIDTH && windowHeight / (scale + 1) >= MIN_HEIGHT) {
            scale++;
        }
        return scale;
    }

    /** A escala que o jogo usa de verdade: a escolhida, até o máximo da janela. 0 = "Auto". */
    public static int effectiveScale(int windowWidth, int windowHeight, int chosen) {
        int auto = autoScale(windowWidth, windowHeight);
        return chosen <= 0 ? auto : Math.min(chosen, auto);
    }

    /** A largura da interface, em pixels de interface. */
    public static int guiWidth(int windowWidth, int scale) {
        return (int) Math.ceil(windowWidth / (double) Math.max(1, scale));
    }

    /**
     * O ajuste do jogador (pixels para dentro; negativo = para fora) que encosta a HUD nos cantos.
     *
     * @param baseOffset o {@code deslocamento_x} da config, certo para {@code 2 × (baseOffset + 8)}
     *                   pixels de largura
     */
    public static int dx(int guiWidth, int baseOffset) {
        // 8 pixels de margem entre a HUD e a borda, como no padrão (240 - 232).
        return baseOffset - (guiWidth / 2 - 8);
    }
}
