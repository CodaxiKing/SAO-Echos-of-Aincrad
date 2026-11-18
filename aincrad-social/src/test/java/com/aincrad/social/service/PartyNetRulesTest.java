package com.aincrad.social.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A party que um servidor descreve é a mesma que o outro monta. */
class PartyNetRulesTest {

    private final UUID kirito = UUID.randomUUID();
    private final UUID asuna = UUID.randomUUID();
    private final UUID klein = UUID.randomUUID();

    @Test
    void outroServidorMontaAMesmaParty() {
        PartyService lobby = new PartyService(8, 60_000, System::currentTimeMillis);
        PartyService andar = new PartyService(8, 60_000, System::currentTimeMillis);
        lobby.invite(kirito, asuna);
        lobby.accept(asuna, kirito);
        Map<UUID, String> snap = lobby.snapshot();
        assertEquals(1, snap.size());
        UUID id = snap.keySet().iterator().next();
        assertEquals(PartyService.encode(kirito, List.of(kirito, asuna)), snap.get(id));

        andar.apply(id, kirito, List.of(kirito, asuna));
        assertTrue(andar.sameParty(kirito, asuna));
        assertEquals(kirito, andar.partyOf(asuna).leader());
        // O mesmo estado dos dois lados: nada a reenviar.
        assertEquals(snap, andar.snapshot());

        // No andar 1 entra o Klein e a liderança passa para a Asuna; o lobby recebe.
        andar.invite(kirito, klein);
        andar.accept(klein, kirito);
        andar.promote(kirito, asuna);
        lobby.apply(id, asuna, andar.partyOf(kirito).members());
        assertEquals(3, lobby.partyOf(klein).size());
        assertEquals(asuna, lobby.partyOf(kirito).leader());
        assertEquals(andar.snapshot(), lobby.snapshot());

        // A party acaba num servidor e some no outro.
        andar.remove(id);
        assertNull(andar.partyOf(kirito));
        assertFalse(andar.sameParty(asuna, klein));
        assertTrue(andar.snapshot().isEmpty());
    }

    @Test
    void quemEntraNumaPartyEmOutroServidorSaiDaAntigaAqui() {
        PartyService here = new PartyService(8, 60_000, System::currentTimeMillis);
        here.invite(kirito, klein);
        here.accept(klein, kirito);
        // Em outro servidor o Klein entrou na party da Asuna.
        UUID other = UUID.randomUUID();
        here.apply(other, asuna, List.of(asuna, klein));
        assertTrue(here.sameParty(asuna, klein));
        assertFalse(here.sameParty(kirito, klein));
    }
}
