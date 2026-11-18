package com.aincrad.life;

import com.aincrad.life.orders.OrderRules;
import com.aincrad.life.stall.StallRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras da barraca e da banca (tarefa 23.4) e do kit de raid (23.16), sem servidor. */
class StallRulesTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void oColDaVendaChegaAoDono() {
        // Sem taxa: o dono recebe tudo o que o cliente pagou.
        assertEquals(120, StallRules.ownerTake(120, 0));
        // Com 10% de taxa.
        assertEquals(12, StallRules.tax(120, 10));
        assertEquals(108, StallRules.ownerTake(120, 10));
        // O que o cliente paga é sempre o que o dono recebe mais a taxa.
        for (long price : new long[]{1, 7, 99, 120, 12_345}) {
            assertEquals(price, StallRules.ownerTake(price, 10) + StallRules.tax(price, 10));
        }
    }

    @Test
    void peixeValeOPrecoCheioPor24HorasEMetadeDepois() {
        long caught = 1_000_000L;
        long freshUntil = StallRules.freshUntil(caught, 24, 0);
        assertEquals(caught + 24 * HOUR, freshUntil);
        // Dentro das 24 h: preço cheio.
        assertTrue(StallRules.fresh(freshUntil, caught + 23 * HOUR));
        assertEquals(80, StallRules.price(80, freshUntil, caught + 23 * HOUR, 0.5));
        // Depois: metade.
        assertFalse(StallRules.fresh(freshUntil, caught + 24 * HOUR));
        assertEquals(40, StallRules.price(80, freshUntil, caught + 25 * HOUR, 0.5));
        // Nunca menos que 1.
        assertEquals(1, StallRules.price(1, freshUntil, caught + 25 * HOUR, 0.5));
    }

    @Test
    void oFrescorContaDaPescariaENaoDeQuandoFoiPostoAVenda() {
        long caught = 5_000_000L;
        // Posto à venda 20 h depois de pescado: só restam 4 h de preço cheio.
        long freshUntil = StallRules.freshUntil(caught, 24, 0);
        long stockedAt = caught + 20 * HOUR;
        assertEquals(4 * HOUR, freshUntil - stockedAt);
        // O nó "Peixeiro" (frescor dobrado): 48 h.
        assertEquals(caught + 48 * HOUR, StallRules.freshUntil(caught, 24, 1.0));
        assertEquals(caught + 36 * HOUR, StallRules.freshUntil(caught, 24, 0.5));
    }

    @Test
    void oQueNaoEPeixeNuncaPassa() {
        // Um prato ou uma poção não tem hora de pescaria.
        assertEquals(0, StallRules.freshUntil(0, 24, 0));
        assertTrue(StallRules.fresh(0, Long.MAX_VALUE));
        assertEquals(80, StallRules.price(80, 0, Long.MAX_VALUE, 0.5));
    }

    @Test
    void oKitDeRaidEntregaOConjuntoACadaMembro() {
        // 6 inscritos, 5 poções para cada: 30 no total, todos servidos, nada sobra.
        assertEquals(6, OrderRules.banquetServed(30, 6, 5));
        assertEquals(0, OrderRules.banquetLeftover(30, 6, 5));
        // Um saiu da raid: cinco recebem, e as 5 poções dele vão para quem encomendou.
        assertEquals(5, OrderRules.banquetServed(30, 5, 5));
        assertEquals(5, OrderRules.banquetLeftover(30, 5, 5));
        // Entraram mais dois: só há conjunto inteiro para seis.
        assertEquals(6, OrderRules.banquetServed(30, 8, 5));
        assertEquals(0, OrderRules.banquetLeftover(30, 8, 5));
        // Ninguém recebe um conjunto pela metade.
        assertEquals(2, OrderRules.banquetServed(13, 4, 5));
        assertEquals(3, OrderRules.banquetLeftover(13, 4, 5));
        // O banquete é o kit de uma unidade por inscrito.
        assertEquals(OrderRules.banquetLeftover(8, 6), OrderRules.banquetLeftover(8, 6, 1));
    }
}
