package com.aincrad.life.orders;

import com.aincrad.life.orders.OrderRules.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras dos contratos especiais (tarefa C7), sem servidor. */
class SpecialContractsTest {

    // ------------------------------------------------------------------ fornecimento recorrente

    @Test
    void recorrenteGuardaTodasAsEntregasEPagaUmaAUma() {
        // 7 entregas de até 150 Col: 1.050 em custódia.
        assertEquals(1050, OrderRules.total(150, 7));
        // Proposta de 120 por entrega: voltam 30 de cada uma das 7.
        assertEquals(210, OrderRules.change(150, 120, 7));
        long held = OrderRules.total(150, 7) - OrderRules.change(150, 120, 7);
        assertEquals(840, held);
        // Cada entrega tira da custódia o pagamento do artesão e a taxa: exatamente o preço.
        long perRound = OrderRules.pay(120, 5) + OrderRules.fee(120, 5);
        assertEquals(120, perRound);
        // Depois das 7, a custódia zera.
        assertEquals(0, held - 7 * perRound);
        // Cancelado depois de 3 entregas: voltam ao cliente as 4 que faltavam.
        assertEquals(480, held - 3 * perRound);
    }

    @Test
    void aProximaEntregaVenceUmIntervaloDepois() {
        long day = 86_400_000L;
        assertEquals(5 * day, OrderRules.nextDue(4 * day, day));
        assertEquals(11 * day, OrderRules.nextDue(4 * day, 7 * day));
        // Entregar adiantado não encurta o prazo da seguinte.
        assertTrue(OrderRules.nextDue(4 * day, day) > 4 * day);
    }

    @Test
    void pedidoComumTemUmaEntregaSo() {
        assertEquals(800, OrderRules.total(800, 1));
        assertEquals(100, OrderRules.change(800, 700, 1));
        assertEquals(OrderRules.change(800, 700), OrderRules.change(800, 700, 1));
        // Um contrato gravado antes dos tipos (ou com o tipo estragado) é um pedido comum.
        assertEquals(Kind.PEDIDO, Kind.parse(null));
        assertEquals(Kind.PEDIDO, Kind.parse("???"));
        assertEquals(Kind.BANQUETE, Kind.parse("BANQUETE"));
    }

    // ------------------------------------------------------------------ banquete de raid

    @Test
    void oBanqueteEntregaUmPratoACadaInscrito() {
        // 8 pratos, 8 inscritos: nada sobra.
        assertEquals(0, OrderRules.banquetLeftover(8, 8));
        // Dois saíram da raid depois do pedido: os dois pratos a mais vão para quem encomendou.
        assertEquals(2, OrderRules.banquetLeftover(8, 6));
        // Entraram mais dois: os 8 primeiros recebem, ninguém fica com dois.
        assertEquals(0, OrderRules.banquetLeftover(8, 10));
        // A raid não existe mais: todos os pratos vão para o cliente.
        assertEquals(8, OrderRules.banquetLeftover(8, 0));
    }

    // ------------------------------------------------------------------ caçada ao Senhor do Lago

    @Test
    void naCacadaRecebeQuemSeInscreveuEFeriuOChefe() {
        List<String> enrolled = List.of("Klein", "Agil", "Silica");
        // Klein e Silica feriram o chefe; Agil ficou olhando; Kirito ajudou sem estar inscrito.
        assertEquals(List.of("Klein", "Silica"), OrderRules.paidHunters(enrolled, Set.of("Silica", "Klein", "Kirito")));
        assertEquals(List.of(), OrderRules.paidHunters(enrolled, Set.of("Kirito")));
        assertEquals(List.of(), OrderRules.paidHunters(List.of(), Set.of("Klein")));
        // 4 vagas a 200 Col: 800 em custódia. Dois pagos: 400 saem, 400 voltam ao pescador.
        long held = OrderRules.total(200, 4);
        long paid = OrderRules.total(200, OrderRules.paidHunters(enrolled, Set.of("Silica", "Klein")).size());
        assertEquals(800, held);
        assertEquals(400, paid);
        assertEquals(400, held - paid);
    }
}
