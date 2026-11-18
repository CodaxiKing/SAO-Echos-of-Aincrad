package com.aincrad.life.orders;

import com.aincrad.life.orders.OrderRules.ItemFate;
import com.aincrad.life.orders.OrderRules.Kind;
import com.aincrad.life.orders.OrderRules.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do contrato de serviço (/contrato): onde o item do cliente está e quando ele volta. Sem servidor. */
class ServiceContractTest {

    @Test
    void oItemFicaGuardadoAteOArtesaoAceitar() {
        assertEquals(ItemFate.GUARDADO, OrderRules.itemFate(State.AGUARDANDO));
        // Aceito: vai para o inventário do artesão, preso.
        assertEquals(ItemFate.COM_O_ARTESAO, OrderRules.itemFate(State.CONTRATADO));
    }

    @Test
    void seNaoForFeitoNoPrazoOItemVoltaAoDono() {
        // O artesão passou do prazo.
        assertEquals(ItemFate.DEVOLVIDO_AO_DONO, OrderRules.itemFate(State.ATRASADO));
        // Não respondeu ao pedido.
        assertEquals(ItemFate.DEVOLVIDO_AO_DONO, OrderRules.itemFate(State.VENCIDO));
        // Recusou, ou um dos dois cancelou.
        assertEquals(ItemFate.DEVOLVIDO_AO_DONO, OrderRules.itemFate(State.CANCELADO));
        // Entregue: o dono recebe o item trabalhado.
        assertEquals(ItemFate.ENTREGUE_AO_DONO, OrderRules.itemFate(State.ENTREGUE));
        // Todo estado que encerra o contrato manda o item para o dono: ele nunca fica com o artesão.
        for (State s : State.values()) {
            if (s.finished()) {
                assertTrue(OrderRules.itemFate(s) == ItemFate.ENTREGUE_AO_DONO || OrderRules.itemFate(s) == ItemFate.DEVOLVIDO_AO_DONO, s.name());
            }
        }
    }

    @Test
    void soOItemDoContratoPodeSerEntregue() {
        assertTrue(OrderRules.serviceItem(12, 12));
        // O item de outro contrato.
        assertFalse(OrderRules.serviceItem(12, 13));
        // Um item comum (o artesão não pode trocar o item do cliente por outro igual).
        assertFalse(OrderRules.serviceItem(12, 0));
        assertFalse(OrderRules.serviceItem(0, 0));
    }

    @Test
    void oItemComOArtesaoSomeQuandoOContratoAcabaENuncaPorEngano() {
        long handed = 1_000_000;
        // Contrato em produção: o item fica.
        assertFalse(OrderRules.purge(true, handed, handed + 60_000, 5_000));
        // Contrato encerrado, e o banco foi lido depois da entrega: o item é recolhido.
        assertTrue(OrderRules.purge(false, handed, handed + 60_000, 5_000));
        // O artesão acabou de aceitar e a lista de contratos ainda é a de antes: não recolhe.
        assertFalse(OrderRules.purge(false, handed, handed - 30_000, 5_000));
        assertFalse(OrderRules.purge(false, handed, handed + 4_000, 5_000));
        // O banco ainda não foi lido desde que o servidor ligou.
        assertFalse(OrderRules.purge(false, handed, 0, 5_000));
    }

    @Test
    void oServicoPagaComoQualquerContrato() {
        // 500 Col em custódia; na entrega o artesão recebe o preço menos a taxa do quadro.
        assertEquals(500, OrderRules.total(500, 1));
        assertEquals(475, OrderRules.pay(500, 5));
        assertEquals(Kind.SERVICO, Kind.parse("SERVICO"));
    }
}
