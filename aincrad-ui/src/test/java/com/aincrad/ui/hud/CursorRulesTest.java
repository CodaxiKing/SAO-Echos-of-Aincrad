package com.aincrad.ui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A cor do cursor dos mobs e as barras do alvo, sem servidor. */
class CursorRulesTest {

    @Test
    void corPelaDiferencaDeLevel() {
        // O mesmo mob de level 10, visto por jogadores de levels diferentes.
        assertEquals(CursorRules.PALE, CursorRules.color(10, 20));
        assertEquals(CursorRules.PALE, CursorRules.color(10, 40));
        assertEquals(CursorRules.PINK, CursorRules.color(10, 19));
        assertEquals(CursorRules.PINK, CursorRules.color(10, 13));
        assertEquals(CursorRules.RED, CursorRules.color(10, 12));
        assertEquals(CursorRules.RED, CursorRules.color(10, 10));
        assertEquals(CursorRules.RED, CursorRules.color(10, 8));
        assertEquals(CursorRules.DARK_RED, CursorRules.color(10, 7));
        assertEquals(CursorRules.DARK_RED, CursorRules.color(10, 4));
        assertEquals(CursorRules.CRIMSON, CursorRules.color(10, 3));
        assertEquals(CursorRules.CRIMSON, CursorRules.color(30, 1));
    }

    @Test
    void semLevelACorENeutra() {
        assertEquals(CursorRules.NEUTRAL, CursorRules.color(0, 10));
        assertEquals(CursorRules.NEUTRAL, CursorRules.color(10, 0));
    }

    @Test
    void chefeTemVariasBarras() {
        assertEquals(1, CursorRules.bars(20, 300, 4));
        assertEquals(1, CursorRules.bars(299, 300, 4));
        assertEquals(4, CursorRules.bars(300, 300, 4));
        assertEquals(4, CursorRules.bars(1600, 300, 4));
        // 0 desliga: todo alvo tem uma barra só.
        assertEquals(1, CursorRules.bars(1600, 0, 4));
    }
}
