package com.aincrad.bosses;

import com.aincrad.bosses.model.InformantRules;
import com.aincrad.bosses.model.InformantRules.View;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** As regras dos informantes (etapa 42), sem servidor. */
class InformantRulesTest {

    @Test
    void semComprarAPaginaApareceFechada() {
        // O servidor viu a fase, o jogador não tem a página: fica à venda.
        assertEquals(View.A_VENDA, InformantRules.view(true, true, false));
        // Comprou (ou estava na luta): aberta.
        assertEquals(View.ABERTA, InformantRules.view(true, true, true));
        // Ninguém chegou lá: nem o informante vende, mesmo que o jogador tenha a página de algum jeito.
        assertEquals(View.DESCONHECIDA, InformantRules.view(true, false, false));
        assertEquals(View.DESCONHECIDA, InformantRules.view(true, false, true));
        // Informantes desligados: o guia é aberto a todos, como antes.
        assertEquals(View.ABERTA, InformantRules.view(false, true, false));
        assertEquals(View.DESCONHECIDA, InformantRules.view(false, false, false));
    }

    @Test
    void oPrecoCresceComOAndarEComAFase() {
        // Illfang, andar 1: 200, 300, 400.
        assertEquals(200, InformantRules.price(200, 50, 100, 1, 0));
        assertEquals(300, InformantRules.price(200, 50, 100, 1, 1));
        assertEquals(400, InformantRules.price(200, 50, 100, 1, 2));
        // Andar 10, primeira fase: 200 + 9 x 50.
        assertEquals(650, InformantRules.price(200, 50, 100, 10, 0));
        assertEquals(0, InformantRules.price(0, 0, 0, 10, 3));
        // A cópia para outro jogador custa uma fração do preço.
        assertEquals(100, InformantRules.copyFee(400, 0.25));
        assertEquals(0, InformantRules.copyFee(400, 0));
        assertEquals(400, InformantRules.copyFee(400, 5));
    }

    @Test
    void soOLiderConvocaAReuniaoNaCidadeAntesDaRaid() {
        long now = 1_000_000;
        var M = com.aincrad.bosses.raid.MeetingService.Block.class;
        assertEquals(null, com.aincrad.bosses.raid.MeetingService.blocked(true, true, false, now + 1, now));
        assertEquals(Enum.valueOf(M, "NAO_E_LIDER"), com.aincrad.bosses.raid.MeetingService.blocked(false, true, false, now + 1, now));
        assertEquals(Enum.valueOf(M, "FORA_DA_CIDADE"), com.aincrad.bosses.raid.MeetingService.blocked(true, false, false, now + 1, now));
        assertEquals(Enum.valueOf(M, "JA_TEM"), com.aincrad.bosses.raid.MeetingService.blocked(true, true, true, now + 1, now));
        assertEquals(Enum.valueOf(M, "RAID_PASSOU"), com.aincrad.bosses.raid.MeetingService.blocked(true, true, false, now, now));
    }

    @Test
    void aFaseDigitadaComecaEm1() {
        assertEquals(0, InformantRules.phaseIndex("1", 3));
        assertEquals(2, InformantRules.phaseIndex(" 3 ", 3));
        assertEquals(-1, InformantRules.phaseIndex("0", 3));
        assertEquals(-1, InformantRules.phaseIndex("4", 3));
        assertEquals(-1, InformantRules.phaseIndex("dois", 3));
        assertEquals(-1, InformantRules.phaseIndex(null, 3));
        assertEquals("illfang:2", InformantRules.key("illfang", 2));
    }
}
