package com.aincrad.life.orders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** As regras dos pedidos de material (tarefa C4), sem servidor. */
class MaterialRulesTest {

    @Test
    void entregarQuatroDeDezPagaQuatroEDeixaSeisEmAberto() {
        // Pedido de 10 a 30 Col cada: 300 em custódia.
        assertEquals(300, MaterialRules.total(10, 30));
        // O fornecedor tem 4 na mão e ninguém reservou nada.
        int n = MaterialRules.deliverable(10, 0, 4, 0, 0);
        assertEquals(4, n);
        assertEquals(120, MaterialRules.payment(n, 30));
        assertEquals(6, MaterialRules.open(10, n));
        // O que sobra na custódia é exatamente o das 6 que faltam.
        assertEquals(MaterialRules.total(10, 30) - MaterialRules.payment(n, 30), MaterialRules.payment(MaterialRules.open(10, n), 30));
    }

    @Test
    void naoSeEntregaMaisDoQueFalta() {
        assertEquals(6, MaterialRules.deliverable(10, 4, 64, 0, 0));
        assertEquals(0, MaterialRules.deliverable(10, 10, 64, 0, 0));
        assertEquals(0, MaterialRules.open(10, 12));
    }

    @Test
    void reservaDosOutrosNaoPodeSerEntreguePorMim() {
        // Faltam 10; outro fornecedor reservou 7: só 3 estão livres.
        assertEquals(3, MaterialRules.reservable(10, 0, 7));
        assertEquals(3, MaterialRules.deliverable(10, 0, 64, 0, 7));
        // Eu reservei 5 e outro reservou 5: entrego os meus 5, e mais nada.
        assertEquals(5, MaterialRules.deliverable(10, 0, 64, 5, 5));
        // Eu reservei 4 e ninguém mais: entrego os meus 4 e os 6 livres.
        assertEquals(10, MaterialRules.deliverable(10, 0, 64, 4, 0));
        // Com 2 na mão, só 2.
        assertEquals(2, MaterialRules.deliverable(10, 0, 2, 4, 0));
    }

    @Test
    void reservaVencidaLiberaAQuantidade() {
        // Com a reserva de 7 ativa, 3 livres.
        assertEquals(3, MaterialRules.reservable(10, 0, 7));
        // Vencida, ela não entra mais na conta: os 10 voltam a ficar livres.
        assertEquals(10, MaterialRules.reservable(10, 0, 0));
        assertEquals(0, MaterialRules.reservable(10, 4, 6));
        assertEquals(0, MaterialRules.reservable(10, 4, 99));
    }

    @Test
    void taxaDeEntregaDoFornecedor() {
        assertEquals(1.0, MaterialRules.rate(0, 0), 1e-9);
        assertEquals(1.0, MaterialRules.rate(10, 10), 1e-9);
        assertEquals(0.4, MaterialRules.rate(10, 4), 1e-9);
        assertEquals(0.0, MaterialRules.rate(10, 0), 1e-9);
        // Entregou além do reservado (pegou também o que estava livre): não passa de 100%.
        assertEquals(1.0, MaterialRules.rate(4, 10), 1e-9);
    }
}
