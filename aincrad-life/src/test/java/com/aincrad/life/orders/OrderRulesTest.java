package com.aincrad.life.orders;

import com.aincrad.life.model.Profession;
import com.aincrad.life.orders.OrderRules.Check;
import com.aincrad.life.orders.OrderRules.State;
import com.aincrad.life.orders.OrderRules.Stats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras das encomendas (tarefas C1, C2, C5 e C6), sem servidor. */
class OrderRulesTest {

    // ------------------------------------------------------------------ C2 aceite

    @Test
    void aceitarDevolveASobraDoOrcamento() {
        assertEquals(300, OrderRules.change(1000, 700));
        assertEquals(0, OrderRules.change(1000, 1000));
        // Uma proposta nunca passa do orçamento; se passasse, nada voltaria (e nada a mais sairia).
        assertEquals(0, OrderRules.change(1000, 1200));
    }

    // ------------------------------------------------------------------ C5 entrega

    @Test
    void artesaoRecebeAMaoDeObraMenosATaxa() {
        assertEquals(35, OrderRules.fee(700, 5));
        assertEquals(665, OrderRules.pay(700, 5));
        // A taxa arredonda para baixo: quem perde o centavo é o quadro, não o artesão.
        assertEquals(0, OrderRules.fee(19, 5));
        assertEquals(19, OrderRules.pay(19, 5));
        assertEquals(0, OrderRules.fee(700, 0));
        assertEquals(0, OrderRules.pay(700, 100));
        // O que sai da custódia nunca passa do preço: pagamento + taxa = preço.
        for (long price : new long[]{1, 19, 20, 333, 700, 99_999}) {
            assertEquals(price, OrderRules.pay(price, 5) + OrderRules.fee(price, 5));
        }
    }

    @Test
    void itemAbaixoDaExigenciaNaoEEntregueSozinho() {
        // Pedido: 2x espada_curta com qualidade a partir de 70.
        assertEquals(Check.OK, OrderRules.check("espada_curta", 70, 2, "espada_curta", 70, 2));
        assertEquals(Check.OK, OrderRules.check("espada_curta", 70, 2, "Espada_Curta", 95, 5));
        assertEquals(Check.NOTA, OrderRules.check("espada_curta", 70, 2, "espada_curta", 69, 2));
        assertEquals(Check.QUANTIDADE, OrderRules.check("espada_curta", 70, 2, "espada_curta", 80, 1));
        assertEquals(Check.PRODUTO, OrderRules.check("espada_curta", 70, 2, "adaga_simples", 100, 2));
        // Um item que não é produto de profissão nunca serve.
        assertEquals(Check.PRODUTO, OrderRules.check("espada_curta", 0, 1, null, 0, 64));
    }

    @Test
    void notaMinimaDeCadaProfissao() {
        // Ferreiro: qualidade de 0 a 100.
        assertEquals(70, OrderService.parseGrade(Profession.FERREIRO, "70"));
        assertEquals(70, OrderService.parseGrade(Profession.FERREIRO, "70%"));
        assertEquals(-1, OrderService.parseGrade(Profession.FERREIRO, "101"));
        assertEquals(-1, OrderService.parseGrade(Profession.FERREIRO, "boa"));
        // Culinária: a qualidade do prato (queimado não se encomenda).
        assertEquals(1, OrderService.parseGrade(Profession.CULINARIA, "comum"));
        assertEquals(3, OrderService.parseGrade(Profession.CULINARIA, "Excelente"));
        assertEquals(-1, OrderService.parseGrade(Profession.CULINARIA, "queimado"));
        // Alquimia: a pureza.
        assertEquals(2, OrderService.parseGrade(Profession.ALQUIMIA, "pura"));
        assertEquals(3, OrderService.parseGrade(Profession.ALQUIMIA, "perfeita"));
        assertEquals(-1, OrderService.parseGrade(Profession.ALQUIMIA, "divina"));
        // Pesca: o tamanho em centímetros, guardado em milímetros.
        assertEquals(400, OrderService.parseGrade(Profession.PESCA, "40"));
        assertEquals(235, OrderService.parseGrade(Profession.PESCA, "23,5"));
        assertEquals(-1, OrderService.parseGrade(Profession.PESCA, "grande"));
    }

    @Test
    void estadosQueEncerramAEncomenda() {
        assertFalse(State.ABERTO.finished());
        assertFalse(State.CONTRATADO.finished());
        assertFalse(State.OFERTA.finished());
        assertTrue(State.ENTREGUE.finished());
        assertTrue(State.CANCELADO.finished());
        assertTrue(State.VENCIDO.finished());
        assertTrue(State.ATRASADO.finished());
        assertEquals(State.OFERTA, State.parse("OFERTA"));
        assertNull(State.parse("perdido"));
        assertNull(State.parse(null));
    }

    // ------------------------------------------------------------------ C6 reputação

    @Test
    void umaAvaliacaoMudaAMedia() {
        Stats s = new Stats(UUID.randomUUID(), "Lisbeth", 0, 0, 3, 0, 0);
        assertEquals(0, s.average(), 1e-9);
        s = s.plusReview(5);
        assertEquals(5, s.average(), 1e-9);
        s = s.plusReview(2);
        assertEquals(3.5, s.average(), 1e-9);
        assertEquals(2, s.reviews());
        assertEquals(3, s.done());
    }

    @Test
    void oRankingOrdenaPorMediaEVolume() {
        Stats best = new Stats(UUID.randomUUID(), "Lisbeth", 10, 48, 12, 0, 0);
        Stats sameAverageFewer = new Stats(UUID.randomUUID(), "Agil", 5, 24, 5, 0, 0);
        Stats lower = new Stats(UUID.randomUUID(), "Klein", 20, 80, 30, 1, 2);
        Stats unrated = new Stats(UUID.randomUUID(), "Novato", 0, 0, 1, 0, 0);
        List<Stats> list = new ArrayList<>(List.of(unrated, lower, sameAverageFewer, best));
        list.sort(OrderRules.RANKING);
        // 4,8 com 12 contratos; 4,8 com 5; 4,0 com 30; sem avaliação por último.
        assertEquals(List.of(best, sameAverageFewer, lower, unrated), list);
    }
}
