package com.aincrad.social.party;

import com.aincrad.social.party.PartyRules.DropRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A divisão de XP e drops da party (etapa 31), sem servidor. */
class PartyRulesTest {

    private final UUID kirito = UUID.randomUUID();
    private final UUID asuna = UUID.randomUUID();
    private final UUID klein = UUID.randomUUID();
    private final UUID agil = UUID.randomUUID();

    // ------------------------------------------------------------------ party

    @Test
    void xpDivididoComBonusPorMembro() {
        assertEquals(100, PartyRules.share(100, 1, 0.1)); // sozinho: tudo
        assertEquals(55, PartyRules.share(100, 2, 0.1));  // 110 / 2
        assertEquals(40, PartyRules.share(100, 3, 0.1));  // 120 / 3
        assertEquals(50, PartyRules.share(100, 2, 0));    // sem bônus: metade
        assertEquals(1, PartyRules.share(1, 8, 0));       // nunca zero
        assertEquals(0, PartyRules.share(0, 4, 0.1));
        assertEquals(50, PartyRules.share(100, 2, -5));   // bônus negativo não existe
    }

    @Test
    void dropsPorVezFazemRodizioEntreQuemEstaPerto() {
        List<UUID> order = List.of(kirito, asuna, klein);
        Set<UUID> all = Set.of(kirito, asuna, klein);
        assertEquals(kirito, PartyRules.nextTurn(order, all, null));
        assertEquals(asuna, PartyRules.nextTurn(order, all, kirito));
        assertEquals(klein, PartyRules.nextTurn(order, all, asuna));
        assertEquals(kirito, PartyRules.nextTurn(order, all, klein));
        // A Asuna está longe: pula a vez dela.
        assertEquals(klein, PartyRules.nextTurn(order, Set.of(kirito, klein), kirito));
        // Quem recebeu por último saiu da party: recomeça do primeiro.
        assertEquals(kirito, PartyRules.nextTurn(order, all, agil));
        assertNull(PartyRules.nextTurn(order, Set.of(), kirito));
    }

    @Test
    void donoDoDropConformeARegra() {
        List<UUID> order = List.of(kirito, asuna, klein);
        Set<UUID> near = Set.of(asuna, klein);
        assertNull(PartyRules.dropOwner(DropRule.LIVRE, order, kirito, near, klein, null));
        // Líder longe: fica com quem matou.
        assertEquals(klein, PartyRules.dropOwner(DropRule.LIDER, order, kirito, near, klein, null));
        assertEquals(asuna, PartyRules.dropOwner(DropRule.LIDER, order, asuna, near, klein, null));
        assertEquals(asuna, PartyRules.dropOwner(DropRule.POR_VEZ, order, kirito, near, klein, null));
        assertEquals(klein, PartyRules.dropOwner(DropRule.POR_VEZ, order, kirito, near, klein, asuna));
        assertEquals(DropRule.POR_VEZ, DropRule.parse("por_vez"));
        assertEquals(DropRule.LIDER, DropRule.parse("Lider"));
        assertEquals(DropRule.LIVRE, DropRule.parse("qualquer"));
        assertEquals(DropRule.LIVRE, DropRule.parse(null));
    }
}
