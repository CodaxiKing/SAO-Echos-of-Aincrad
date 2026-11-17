package com.aincrad.effects.step;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShapesTest {

    private static final double EPS = 1e-9;

    @Test
    void circuloTemRaioCertoENoChao() {
        List<Vector> pts = Shapes.circle(2, 12);
        assertEquals(12, pts.size());
        for (Vector v : pts) {
            assertEquals(2, Math.hypot(v.getX(), v.getZ()), EPS);
            assertEquals(0, v.getY(), EPS);
        }
    }

    @Test
    void espiralVaiDoChaoAteAAltura() {
        List<Vector> pts = Shapes.spiral(1, 3, 2, 30);
        assertEquals(0, pts.get(0).getY(), EPS);
        assertEquals(3, pts.get(29).getY(), EPS);
        // 2 voltas completas: o último ponto volta para o ângulo 0
        assertEquals(1, pts.get(29).getX(), 1e-6);
    }

    @Test
    void colunaSobeDeZeroAteH() {
        List<Vector> pts = Shapes.column(0, 2, 5);
        assertEquals(0, pts.get(0).getY(), EPS);
        assertEquals(2, pts.get(4).getY(), EPS);
        assertEquals(0, pts.get(2).getX(), EPS);
    }

    @Test
    void direcoesDaEsferaSaoUnitarias() {
        for (Vector v : Shapes.sphereDirections(50)) {
            assertEquals(1, v.length(), 1e-9);
        }
    }

    @Test
    void linhaTemComprimentoCerto() {
        List<Vector> pts = Shapes.line(new Vector(0, 0, 3), 5, 6);
        assertEquals(5, pts.get(5).getZ(), EPS);
        assertEquals(1, pts.get(1).getZ(), EPS);
    }

    @Test
    void formasEmPortugues() {
        assertEquals(Shapes.Shape.CIRCULO, Shapes.Shape.parse("círculo"));
        assertEquals(Shapes.Shape.EXPLOSAO, Shapes.Shape.parse("EXPLOSAO"));
        assertThrows(IllegalArgumentException.class, () -> Shapes.Shape.parse("cubo"));
    }

    @Test
    void opacidadeDoFade() {
        // 40 ticks, 10 de entrada, 10 de saída
        assertEquals(16, WorldSteps.TextDisplayStep.opacityAt(0, 40, 10, 10));
        assertEquals(255, WorldSteps.TextDisplayStep.opacityAt(20, 40, 10, 10));
        assertEquals(16, WorldSteps.TextDisplayStep.opacityAt(40, 40, 10, 10));
        assertEquals(255, WorldSteps.TextDisplayStep.opacityAt(5, 40, 0, 0));
    }
}
