package com.aincrad.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EcoMathTest {

    @Test
    void formataComPontoDeMilhar() {
        assertEquals("0", EcoMath.format(0));
        assertEquals("999", EcoMath.format(999));
        assertEquals("1.000", EcoMath.format(1000));
        assertEquals("1.234.567", EcoMath.format(1234567));
        assertEquals("-12.000", EcoMath.format(-12000));
    }

    @Test
    void taxaArredondaParaBaixo() {
        assertEquals(0, EcoMath.tax(100, 0));
        assertEquals(5, EcoMath.tax(100, 0.05));
        assertEquals(4, EcoMath.tax(99, 0.05));
        assertEquals(10, EcoMath.tax(10, 2));
        assertEquals(0, EcoMath.tax(-5, 0.5));
    }

    @Test
    void colDeMob() {
        assertEquals(15, EcoMath.mobCol(5, 3, 0.25, 0.5));
        assertEquals(11, EcoMath.mobCol(5, 3, 0.25, 0)); // 15 × 0.75 = 11.25
        assertEquals(19, EcoMath.mobCol(5, 3, 0.25, 1)); // 15 × 1.25 = 18.75
        assertEquals(0, EcoMath.mobCol(0, 3, 0.25, 0.5));
    }

    @Test
    void somaNaoEstoura() {
        assertEquals(Long.MAX_VALUE, EcoMath.add(Long.MAX_VALUE, 10));
        assertEquals(7, EcoMath.add(10, -3));
    }

    @Test
    void lerQuantidade() {
        assertEquals(1500, EcoMath.parse("1500"));
        assertEquals(1500, EcoMath.parse("1.500"));
        assertEquals(1500, EcoMath.parse("1,5k"));
        assertEquals(2_000_000, EcoMath.parse("2m"));
        assertEquals(-1, EcoMath.parse("-5"));
        assertEquals(-1, EcoMath.parse("abc"));
        assertEquals(-1, EcoMath.parse(""));
    }
}
