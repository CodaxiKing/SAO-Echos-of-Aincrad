package com.aincrad.reputation;

import com.aincrad.reputation.model.CursorRules.Color;
import com.aincrad.reputation.model.WorldMarkRules;
import com.aincrad.reputation.model.WorldMarkRules.BountyBlock;
import com.aincrad.reputation.model.WorldMarkRules.Grave;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras da recompensa por cabeça (etapa 46) e dos túmulos (etapa 47), sem servidor. */
class WorldMarkRulesTest {

    private static final long DAY = 86_400_000L;
    private static final long NOW = 1_000 * DAY;

    @Test
    void soCriminosoTemACabecaAPremio() {
        assertNull(WorldMarkRules.bountyBlocked(false, 500, 100, Color.VERMELHO, true));
        assertEquals(BountyBlock.NAO_E_CRIMINOSO, WorldMarkRules.bountyBlocked(false, 500, 100, Color.VERDE, true));
        assertEquals(BountyBlock.NAO_E_CRIMINOSO, WorldMarkRules.bountyBlocked(false, 500, 100, Color.LARANJA, true));
        // Com "so_vermelhos: false", laranja também vale; verde, nunca.
        assertNull(WorldMarkRules.bountyBlocked(false, 500, 100, Color.LARANJA, false));
        assertEquals(BountyBlock.NAO_E_CRIMINOSO, WorldMarkRules.bountyBlocked(false, 500, 100, Color.VERDE, false));
        assertEquals(BountyBlock.VALOR, WorldMarkRules.bountyBlocked(false, 99, 100, Color.VERMELHO, true));
        assertEquals(BountyBlock.VALOR, WorldMarkRules.bountyBlocked(false, 0, 0, Color.VERMELHO, true));
        assertEquals(BountyBlock.VOCE_MESMO, WorldMarkRules.bountyBlocked(true, 500, 100, Color.VERMELHO, true));
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                WorldMarkRulesTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (BountyBlock b : BountyBlock.values()) {
            assertNotNull(msg.getString("recompensa.bloqueio." + b.name().toLowerCase(Locale.ROOT)), b.name());
        }
    }

    @Test
    void seOVermelhoVoltaAVerdeSemSerPresoOColVolta() {
        // Ainda vermelho: a recompensa segue de pé.
        assertFalse(WorldMarkRules.bountyExpired(NOW + DAY, 0, NOW));
        // Foi preso (e quem prendeu já recebeu): a que sobrar não volta enquanto ele está na prisão.
        assertFalse(WorldMarkRules.bountyExpired(NOW - 1, NOW + DAY, NOW));
        // O vermelho venceu e ele está solto: volta a quem pôs.
        assertTrue(WorldMarkRules.bountyExpired(NOW - 1, 0, NOW));
        assertTrue(WorldMarkRules.bountyExpired(NOW, NOW, NOW));
    }

    @Test
    void aBaseEscondidaEAEmboscada() {
        // A base é um raio em volta do ponto: só entra no mapa de quem pisa dentro.
        CrimeService.Base base = new CrimeService.Base("g1", "Laughing Coffin", "andar19", "world", 100, 40, -50);
        assertTrue(CrimeService.inRadius(100.5, 40, -49.5, 105, 41, -49.5, 12));
        assertFalse(CrimeService.inRadius(100.5, 40, -49.5, 113, 40, -49.5, 12));
        assertEquals("g1", base.guild());
        // Na emboscada, o cristal de teleporte da vítima falha pelo tempo da config; os outros, não.
        assertTrue(CrimeService.crystalBlocked("teleporte", NOW + 15_000, NOW));
        assertFalse(CrimeService.crystalBlocked("teleporte", NOW + 15_000, NOW + 15_000));
        assertFalse(CrimeService.crystalBlocked("cura", NOW + 15_000, NOW));
        assertFalse(CrimeService.crystalBlocked(null, NOW + 15_000, NOW));
        assertFalse(CrimeService.crystalBlocked("teleporte", 0, NOW));
        assertTrue(new CrimeService.Guild("g1", "LC", "LIDER", true).leader());
        assertFalse(new CrimeService.Guild("g1", "LC", "MEMBRO", true).leader());
    }

    @Test
    void aLapideSomeNoPrazoDaConfig() {
        long expires = WorldMarkRules.expiresAt(NOW, 30);
        Grave g = new Grave(1, "u", "Sachi", "world", 100, 64, -20, NOW, expires, 0);
        assertFalse(g.expired(NOW + 29 * DAY));
        assertTrue(g.expired(NOW + 30 * DAY));
        // 0 dias = nunca some.
        Grave forever = new Grave(2, "u", "Diabel", "world", 0, 64, 0, NOW, WorldMarkRules.expiresAt(NOW, 0), 3);
        assertFalse(forever.expired(NOW + 10_000 * DAY));
        assertEquals(6, g.chunkX());
        assertEquals(-2, g.chunkZ());
    }

    @Test
    void oPedacoDeMapaTemLimiteEAMaisVelhaSai() {
        Grave a = new Grave(1, "u", "A", "w", 1, 64, 1, NOW, 0, 0);
        Grave b = new Grave(2, "u", "B", "w", 2, 64, 2, NOW + 10, 0, 0);
        Grave c = new Grave(3, "u", "C", "w", 3, 64, 3, NOW + 20, 0, 0);
        // Limite 4: com três e mais a nova, cabe.
        assertTrue(WorldMarkRules.overflow(List.of(c, a, b), 4).isEmpty());
        // Limite 3: a mais velha sai.
        assertEquals(List.of(a), WorldMarkRules.overflow(List.of(c, a, b), 3));
        // Limite 1: saem todas as antigas.
        assertEquals(List.of(a, b, c), WorldMarkRules.overflow(List.of(c, a, b), 1));
        assertTrue(WorldMarkRules.overflow(List.of(), 1).isEmpty());
    }
}
