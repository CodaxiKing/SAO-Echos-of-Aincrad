package com.aincrad.ui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Onde a HUD fica na tela de cada jogador, sem servidor. */
class HudCalibrationTest {

    @Test
    void aEscalaAutoEAMaiorQueCabe() {
        // 1920×1080: a escala 4 dá 480×270; a 5 daria 384×216, baixo demais.
        assertEquals(4, HudCalibration.autoScale(1920, 1080));
        assertEquals(2, HudCalibration.autoScale(1357, 699));
        // 854×480: a escala 2 ainda dá 427×240, o mínimo; em 640×400 só cabe a 1.
        assertEquals(2, HudCalibration.autoScale(854, 480));
        assertEquals(1, HudCalibration.autoScale(640, 400));
        // A escala escolhida vale até o máximo da janela; 0 é "Auto".
        assertEquals(2, HudCalibration.effectiveScale(1920, 1080, 2));
        assertEquals(4, HudCalibration.effectiveScale(1920, 1080, 0));
        assertEquals(4, HudCalibration.effectiveScale(1920, 1080, 9));
    }

    @Test
    void aHudEncostaNoCanto() {
        // O padrão já está certo para 480 pixels de interface.
        assertEquals(480, HudCalibration.guiWidth(1920, 4));
        assertEquals(0, HudCalibration.dx(480, 232));
        // 1920×1080 na escala 2: 960 de interface, a HUD anda 240 pixels para fora.
        assertEquals(960, HudCalibration.guiWidth(1920, 2));
        assertEquals(-240, HudCalibration.dx(960, 232));
        // Janela de 1357 na escala 2: 679 de interface.
        assertEquals(679, HudCalibration.guiWidth(1357, 2));
        assertEquals(-99, HudCalibration.dx(679, 232));
        // Interface mais estreita que o padrão: a HUD vem para dentro.
        assertEquals(40, HudCalibration.dx(400, 232));
    }
}
