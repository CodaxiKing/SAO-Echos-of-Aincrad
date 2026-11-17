package com.aincrad.ui.screen;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaneFrameTest {

    private static final double EPS = 1e-6;

    @Test
    void olharParaFrenteAcertaOCentro() {
        PlaneFrame f = PlaneFrame.facing(new Vector(0, 64, 0), 0f, 2.0, 0);
        // yaw 0 = olhando para +Z
        assertEquals(2.0, f.center().getZ(), EPS);
        double[] uv = f.project(new Vector(0, 64, 0), new Vector(0, 0, 1));
        assertNotNull(uv);
        assertEquals(0, uv[0], EPS);
        assertEquals(0, uv[1], EPS);
    }

    @Test
    void direitaDoJogadorEhXPositivoNoPlano() {
        PlaneFrame f = PlaneFrame.facing(new Vector(0, 0, 0), 0f, 2.0, 0);
        // Olhando para +Z (sul), a direita do jogador é -X (oeste).
        Vector right = f.toWorld(1, 0).subtract(f.center());
        assertEquals(-1, right.getX(), EPS);
        Vector look = new Vector(-0.5, 0, 2).normalize();
        double[] uv = f.project(new Vector(0, 0, 0), look);
        assertNotNull(uv);
        assertEquals(0.5, uv[0], EPS);
    }

    @Test
    void olharParaCimaSobeNoPlano() {
        PlaneFrame f = PlaneFrame.facing(new Vector(0, 0, 0), 90f, 2.0, 0);
        // yaw 90 = olhando para -X
        Vector look = new Vector(-2, 1, 0).normalize();
        double[] uv = f.project(new Vector(0, 0, 0), look);
        assertNotNull(uv);
        assertEquals(1.0, uv[1], EPS);
        assertEquals(0.0, uv[0], EPS);
    }

    @Test
    void olharParaTrasNaoAcerta() {
        PlaneFrame f = PlaneFrame.facing(new Vector(0, 0, 0), 0f, 2.0, 0);
        assertNull(f.project(new Vector(0, 0, 0), new Vector(0, 0, -1)));
        assertNull(f.project(new Vector(0, 0, 0), new Vector(1, 0, 0)));
    }

    @Test
    void ajusteVerticalMoveOCentro() {
        PlaneFrame f = PlaneFrame.facing(new Vector(0, 10, 0), 0f, 2.0, -0.5);
        assertEquals(9.5, f.center().getY(), EPS);
    }

    @Test
    void dentroDoQuadrado() {
        assertTrue(PlaneFrame.inside(0.1, 0.1, 0, 0, 0.3));
        assertTrue(PlaneFrame.inside(0.15, -0.15, 0, 0, 0.3));
        assertFalse(PlaneFrame.inside(0.16, 0, 0, 0, 0.3));
        assertFalse(PlaneFrame.inside(0, 0.5, 0, 0, 0.3));
    }

    @Test
    void slotsDoSubmenuQuebramLinha() {
        assertEquals(12, Screens.submenuSlot(1, 0));
        assertEquals(17, Screens.submenuSlot(1, 5));
        assertEquals(21, Screens.submenuSlot(1, 6));
        assertEquals(48, Screens.submenuSlot(5, 0));
        assertEquals(53, Screens.submenuSlot(5, 11));
    }
}
