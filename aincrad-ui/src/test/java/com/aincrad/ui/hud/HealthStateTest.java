package com.aincrad.ui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthStateTest {

    @Test
    void faixasDeCor() {
        assertEquals(HealthState.NORMAL, HealthState.of(1.0, 0.5, 0.25));
        assertEquals(HealthState.NORMAL, HealthState.of(0.5, 0.5, 0.25));
        assertEquals(HealthState.WARNING, HealthState.of(0.49, 0.5, 0.25));
        assertEquals(HealthState.WARNING, HealthState.of(0.25, 0.5, 0.25));
        assertEquals(HealthState.CRITICAL, HealthState.of(0.24, 0.5, 0.25));
        assertEquals(HealthState.CRITICAL, HealthState.of(0.0, 0.5, 0.25));
    }

    @Test
    void segmentosCheios() {
        assertEquals(20, HealthState.filledSegments(1.0, 20));
        assertEquals(10, HealthState.filledSegments(0.5, 20));
        assertEquals(1, HealthState.filledSegments(0.01, 20));
        assertEquals(0, HealthState.filledSegments(0, 20));
        assertEquals(20, HealthState.filledSegments(1.5, 20));
    }

    @Test
    void deslocamentoDaHud() {
        // Texto de 100 px começando 200 px à esquerda do centro: espaço final de 300 px.
        assertEquals(300, HealthHud.trailingShift(100, 200));
        // Largura total (100 + 300) / 2 = 200 → início em centro - 200.
        int width = 100;
        int trailing = HealthHud.trailingShift(width, 200);
        assertEquals(-200, -(width + trailing) / 2);
    }
}
