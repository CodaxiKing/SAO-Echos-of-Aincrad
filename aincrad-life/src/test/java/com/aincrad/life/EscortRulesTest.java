package com.aincrad.life;

import com.aincrad.life.escort.EscortRules;
import com.aincrad.life.escort.EscortRules.Block;
import com.aincrad.life.escort.EscortRules.End;
import com.aincrad.life.escort.EscortRules.State;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do contrato de escolta (etapa 48), sem servidor. */
class EscortRulesTest {

    @Test
    void oEscoltaRecebeQuandoOClienteChegaVivoComElePorPerto() {
        assertTrue(EscortRules.arrived(State.ATIVO, 10, 32));
        assertTrue(EscortRules.arrived(State.ATIVO, 32, 32));
        // O escolta ficou para trás, está em outro mundo ou saiu: ainda não fecha.
        assertFalse(EscortRules.arrived(State.ATIVO, 33, 32));
        assertFalse(EscortRules.arrived(State.ATIVO, -1, 32));
        // Proposta não aceita não paga ninguém.
        assertFalse(EscortRules.arrived(State.PROPOSTO, 5, 32));
        assertTrue(End.CHEGOU.pays());
        assertFalse(End.CHEGOU.fails());
    }

    @Test
    void seOClienteMorreOColVoltaEOEscoltaPerdeReputacao() {
        assertFalse(End.CLIENTE_MORREU.pays());
        assertTrue(End.CLIENTE_MORREU.fails());
        assertTrue(End.ESCOLTA_FALHOU.fails());
        assertTrue(End.TEMPO.fails());
        // O cliente que cancela ou sai não mancha o histórico do escolta.
        assertFalse(End.CANCELADO.fails());
        assertFalse(End.RECUSADO.fails());
        for (End e : End.values()) {
            assertEquals(e == End.CHEGOU, e.pays(), e.name());
        }
        assertEquals(100, EscortRules.successRate(0, 0));
        assertEquals(75, EscortRules.successRate(3, 1));
        assertEquals(0, EscortRules.successRate(0, 2));
    }

    @Test
    void oTempoDoContrato() {
        long minute = 60_000L;
        assertFalse(EscortRules.timedOut(0, 59 * minute, 60 * minute));
        assertTrue(EscortRules.timedOut(0, 60 * minute, 60 * minute));
        assertFalse(EscortRules.timedOut(0, 999 * minute, 0));
    }

    @Test
    void quandoAPropostaNaoPodeSerFeita() {
        assertNull(EscortRules.blocked(false, 100, 50, false, false, true, false));
        assertEquals(Block.VOCE_MESMO, EscortRules.blocked(true, 100, 50, false, false, true, false));
        assertEquals(Block.VALOR, EscortRules.blocked(false, 49, 50, false, false, true, false));
        assertEquals(Block.JA_TEM_CONTRATO, EscortRules.blocked(false, 100, 50, true, false, true, false));
        assertEquals(Block.ESCOLTA_OCUPADO, EscortRules.blocked(false, 100, 50, false, true, true, false));
        assertEquals(Block.DESTINO, EscortRules.blocked(false, 100, 50, false, false, false, false));
        assertEquals(Block.JA_ESTA_LA, EscortRules.blocked(false, 100, 50, false, false, true, true));
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                EscortRulesTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (Block b : Block.values()) {
            assertNotNull(msg.getString("escolta.bloqueio." + b.name().toLowerCase(Locale.ROOT)), b.name());
        }
        for (End e : End.values()) {
            assertNotNull(msg.getString("escolta.fim." + e.name().toLowerCase(Locale.ROOT)), e.name());
        }
    }
}
