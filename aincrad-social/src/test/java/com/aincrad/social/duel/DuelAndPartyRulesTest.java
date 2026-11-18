package com.aincrad.social.duel;

import com.aincrad.social.duel.DuelService.Ask;
import com.aincrad.social.duel.DuelService.Challenge;
import com.aincrad.social.duel.DuelService.Duel;
import com.aincrad.social.party.PartyRules;
import com.aincrad.social.party.PartyRules.DropRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Duelos (etapa 33) e a divisão de XP e drops da party (etapa 31), sem servidor. */
class DuelAndPartyRulesTest {

    private final UUID kirito = UUID.randomUUID();
    private final UUID asuna = UUID.randomUUID();
    private final UUID klein = UUID.randomUUID();
    private final UUID agil = UUID.randomUUID();
    private final AtomicLong now = new AtomicLong(1_000);
    private final DuelService duels = new DuelService(now::get);

    // ------------------------------------------------------------------ duelos

    @Test
    void modos() {
        // Primeiro golpe: qualquer golpe decide.
        assertTrue(DuelMode.PRIMEIRO_GOLPE.endsOnHit(19, 20));
        // Metade: só quando a vida chega à metade ou menos.
        assertFalse(DuelMode.METADE.endsOnHit(10.5, 20));
        assertTrue(DuelMode.METADE.endsOnHit(10, 20));
        assertTrue(DuelMode.METADE.endsOnHit(-3, 20));
        // Total: só a morte encerra, e é o único que mata.
        assertFalse(DuelMode.TOTAL.endsOnHit(0.5, 20));
        assertTrue(DuelMode.TOTAL.lethal());
        assertFalse(DuelMode.PRIMEIRO_GOLPE.lethal());
        assertFalse(DuelMode.METADE.lethal());
        assertEquals(DuelMode.PRIMEIRO_GOLPE, DuelMode.parse("Primeiro-Golpe"));
        assertEquals(DuelMode.METADE, DuelMode.parse("metade"));
        assertEquals(DuelMode.TOTAL, DuelMode.parse("3"));
        assertNull(DuelMode.parse("espada"));
        assertNull(DuelMode.parse(null));
    }

    @Test
    void desafioAceiteEFim() {
        assertEquals(Ask.SELF, duels.challenge(kirito, kirito, DuelMode.METADE, 30_000));
        assertEquals(Ask.SENT, duels.challenge(kirito, asuna, DuelMode.METADE, 30_000));
        assertEquals(Ask.ALREADY, duels.challenge(kirito, asuna, DuelMode.METADE, 30_000));
        // Trocar o modo refaz o desafio.
        assertEquals(Ask.SENT, duels.challenge(kirito, asuna, DuelMode.PRIMEIRO_GOLPE, 30_000));
        assertEquals(Ask.SENT, duels.challenge(klein, asuna, DuelMode.TOTAL, 30_000));

        // Sem dizer de quem, vale o mais recente.
        assertEquals(klein, duels.pending(asuna, null).from());
        Challenge c = duels.pending(asuna, kirito);
        assertEquals(DuelMode.PRIMEIRO_GOLPE, c.mode());

        Duel d = duels.begin(c, 5_000, 180_000);
        assertNotNull(d);
        assertSame(d, duels.duelOf(kirito));
        assertSame(d, duels.duelOf(asuna));
        assertTrue(duels.inDuel(kirito, asuna));
        assertTrue(duels.inDuel(asuna, kirito));
        assertFalse(duels.inDuel(kirito, klein));
        assertFalse(duels.inDuel(kirito, kirito));
        assertEquals(asuna, d.opponent(kirito));
        assertNull(d.opponent(klein));
        // O desafio do Klein caiu quando a Asuna entrou no duelo.
        assertNull(duels.pending(asuna, klein));
        // Quem está em duelo não desafia nem é desafiado.
        assertEquals(Ask.BUSY, duels.challenge(kirito, klein, DuelMode.METADE, 30_000));
        assertEquals(Ask.TARGET_BUSY, duels.challenge(klein, asuna, DuelMode.METADE, 30_000));

        // Contagem, luta e tempo limite.
        assertFalse(d.started(now.get()));
        now.addAndGet(5_000);
        assertTrue(d.started(now.get()));
        assertFalse(d.timedOut(now.get()));
        now.addAndGet(180_000);
        assertTrue(d.timedOut(now.get()));

        assertTrue(duels.end(d));
        assertFalse(duels.end(d)); // acabar duas vezes não faz nada
        assertNull(duels.duelOf(kirito));
        assertTrue(duels.duels().isEmpty());
    }

    @Test
    void desafioExpiraEPodeSerRecusado() {
        duels.challenge(kirito, asuna, DuelMode.METADE, 30_000);
        now.addAndGet(30_000);
        assertNull(duels.pending(asuna, kirito));
        assertNull(duels.decline(asuna, null));

        duels.challenge(kirito, asuna, DuelMode.METADE, 30_000);
        assertEquals(kirito, duels.decline(asuna, null).from());
        assertNull(duels.pending(asuna, null));

        // Quem sai do servidor leva os desafios feitos e recebidos.
        duels.challenge(kirito, asuna, DuelMode.METADE, 30_000);
        duels.challenge(klein, kirito, DuelMode.METADE, 30_000);
        duels.forget(kirito);
        assertNull(duels.pending(asuna, null));
        assertNull(duels.pending(kirito, null));
    }

    @Test
    void umSoDueloPorVez() {
        duels.challenge(kirito, asuna, DuelMode.METADE, 30_000);
        duels.challenge(klein, agil, DuelMode.METADE, 30_000);
        duels.challenge(asuna, agil, DuelMode.METADE, 30_000);
        Challenge second = duels.pending(agil, asuna);
        assertNotNull(duels.begin(duels.pending(asuna, kirito), 0, 1_000));
        // A Asuna já está num duelo: o desafio que ela fez ao Agil não começa outro.
        assertNull(duels.begin(second, 0, 1_000));
        assertNotNull(duels.begin(duels.pending(agil, klein), 0, 1_000));
        assertEquals(2, duels.duels().size());
    }

    @Test
    void noTempoVenceQuemTemMaisVida() {
        assertEquals(1, DuelService.byHealth(15, 20, 10, 20));
        assertEquals(-1, DuelService.byHealth(15, 40, 10, 20)); // conta a fração, não o número
        assertEquals(0, DuelService.byHealth(10, 20, 20, 40));
    }

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
