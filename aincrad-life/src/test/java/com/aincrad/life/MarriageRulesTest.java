package com.aincrad.life;

import com.aincrad.life.marriage.MarriageRules;
import com.aincrad.life.marriage.MarriageRules.Refusal;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do casamento (etapa 25), sem servidor. */
class MarriageRulesTest {

    private static final long DAY = 86_400_000L;
    private static final UUID KIRITO = UUID.randomUUID();
    private static final UUID ASUNA = UUID.randomUUID();

    // ------------------------------------------------------------------ 25.1 pedido

    @Test
    void semAnelOPedidoERecusado() {
        assertEquals(Refusal.SEM_ANEL, MarriageRules.canPropose(false, false, false, false, 2, 8, true));
        assertEquals(Refusal.OK, MarriageRules.canPropose(false, false, false, true, 2, 8, true));
    }

    @Test
    void osDoisPrecisamEstarJuntosEEmZonaSegura() {
        assertEquals(Refusal.OK, MarriageRules.canPropose(false, false, false, true, 8, 8, true));
        assertEquals(Refusal.LONGE, MarriageRules.canPropose(false, false, false, true, 8.1, 8, true));
        // Mundos diferentes.
        assertEquals(Refusal.LONGE, MarriageRules.canPropose(false, false, false, true, -1, 8, true));
        assertEquals(Refusal.FORA_DE_ZONA_SEGURA, MarriageRules.canPropose(false, false, false, true, 2, 8, false));
    }

    @Test
    void quemJaECasadoNaoCasaDeNovo() {
        assertEquals(Refusal.SI_MESMO, MarriageRules.canPropose(true, false, false, true, 0, 8, true));
        assertEquals(Refusal.JA_CASADO, MarriageRules.canPropose(false, true, false, true, 2, 8, true));
        assertEquals(Refusal.PAR_CASADO, MarriageRules.canPropose(false, false, true, true, 2, 8, true));
        // Ser casado vem antes de tudo: nem adianta ter o anel.
        assertEquals(Refusal.JA_CASADO, MarriageRules.canPropose(false, true, true, false, 99, 8, false));
    }

    @Test
    void esperaDeSeteDiasParaCasarDeNovo() {
        long divorced = 100 * DAY;
        assertEquals(7 * DAY, MarriageRules.waitLeft(divorced, divorced, 7));
        assertEquals(4 * DAY, MarriageRules.waitLeft(divorced, divorced + 3 * DAY, 7));
        assertEquals(0, MarriageRules.waitLeft(divorced, divorced + 7 * DAY, 7));
        assertEquals(0, MarriageRules.waitLeft(divorced, divorced + 30 * DAY, 7));
        // Quem nunca se divorciou não espera.
        assertEquals(0, MarriageRules.waitLeft(0, divorced, 7));
        // Espera desligada na config.
        assertEquals(0, MarriageRules.waitLeft(divorced, divorced, 0));
    }

    // ------------------------------------------------------------------ 25.2 baú, teleporte

    @Test
    void osDoisNaoAbremOBauAoMesmoTempo() {
        long now = 1_000_000;
        // Livre: ninguém tem a trava.
        assertTrue(MarriageRules.lockFree(null, 0, now, KIRITO.toString()));
        assertTrue(MarriageRules.lockFree("", 0, now, KIRITO.toString()));
        // A Asuna abriu num servidor: o Kirito não abre no outro.
        assertFalse(MarriageRules.lockFree(ASUNA.toString(), now + 60_000, now, KIRITO.toString()));
        // Ela mesma renova a trava.
        assertTrue(MarriageRules.lockFree(ASUNA.toString(), now + 60_000, now, ASUNA.toString()));
        // O servidor dela caiu com o baú aberto: a trava vence e o Kirito consegue abrir.
        assertTrue(MarriageRules.lockFree(ASUNA.toString(), now - 1, now, KIRITO.toString()));
        // O divórcio usa a mesma trava: com o baú aberto, espera.
        assertFalse(MarriageRules.lockFree(ASUNA.toString(), now + 60_000, now, "divorcio"));
    }

    @Test
    void oBauGuardaOAutorDeCadaDeposito() {
        // Espaço vazio: a Asuna põe uma espada.
        assertEquals(ASUNA, MarriageRules.author(null, 0, null, "espada", 1, ASUNA));
        // O Kirito abre e não mexe: a espada continua dela.
        assertEquals(ASUNA, MarriageRules.author("espada", 1, ASUNA, "espada", 1, KIRITO));
        // O Kirito tira metade de uma pilha da Asuna: o que ficou continua dela.
        assertEquals(ASUNA, MarriageRules.author("pocao", 8, ASUNA, "pocao", 4, KIRITO));
        // O Kirito troca a espada por um escudo: o escudo é dele.
        assertEquals(KIRITO, MarriageRules.author("espada", 1, ASUNA, "escudo", 1, KIRITO));
        // O Kirito põe mais poções na pilha da Asuna: a pilha passa a ser de quem a aumentou.
        assertEquals(KIRITO, MarriageRules.author("pocao", 4, ASUNA, "pocao", 10, KIRITO));
        // O espaço ficou vazio: sem autor.
        assertNull(MarriageRules.author("espada", 1, ASUNA, null, 0, KIRITO));
    }

    @Test
    void teleporteAteOParUmaVezPorDia() {
        assertTrue(MarriageRules.canTeleport(null, "2026-11-17"));
        assertFalse(MarriageRules.canTeleport("2026-11-17", "2026-11-17"));
        assertTrue(MarriageRules.canTeleport("2026-11-16", "2026-11-17"));
    }

    // ------------------------------------------------------------------ 25.3 divórcio

    @Test
    void noDivorcioCadaItemVoltaAoAutor() {
        // O baú depois de várias sessões: cada espaço tem um item e o autor dele.
        String[] items = {"espada", "pocao", "escudo", null};
        UUID[] authors = {
                MarriageRules.author(null, 0, null, "espada", 1, ASUNA),
                MarriageRules.author("pocao", 8, ASUNA, "pocao", 4, KIRITO),
                MarriageRules.author("espada", 1, ASUNA, "escudo", 1, KIRITO),
                MarriageRules.author("espada", 1, ASUNA, null, 0, KIRITO),
        };
        int toAsuna = 0;
        int toKirito = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                assertNull(authors[i]);
            } else if (authors[i].equals(ASUNA)) {
                toAsuna++;
            } else {
                toKirito++;
            }
        }
        // A espada e as poções voltam para a Asuna; o escudo, para o Kirito. Nada some e nada duplica.
        assertEquals(2, toAsuna);
        assertEquals(1, toKirito);
    }
}
