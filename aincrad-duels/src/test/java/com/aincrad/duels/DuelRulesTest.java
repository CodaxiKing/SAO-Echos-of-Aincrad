package com.aincrad.duels;

import com.aincrad.duels.model.DuelMode;
import com.aincrad.duels.model.DuelScore;
import com.aincrad.duels.model.DuelService;
import com.aincrad.duels.model.DuelService.Ask;
import com.aincrad.duels.model.DuelService.Challenge;
import com.aincrad.duels.model.DuelService.Duel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras dos duelos, sem servidor: modos, desafios, apostas e placar. */
class DuelRulesTest {

    private final UUID kirito = UUID.randomUUID();
    private final UUID asuna = UUID.randomUUID();
    private final UUID klein = UUID.randomUUID();
    private final UUID agil = UUID.randomUUID();
    private final AtomicLong now = new AtomicLong(1_000);
    private final DuelService duels = new DuelService(now::get);

    @Test
    void modos() {
        assertTrue(DuelMode.PRIMEIRO_GOLPE.endsOnHit(19, 20));
        assertFalse(DuelMode.METADE.endsOnHit(10.5, 20));
        assertTrue(DuelMode.METADE.endsOnHit(10, 20));
        assertTrue(DuelMode.METADE.endsOnHit(-3, 20));
        assertFalse(DuelMode.TOTAL.endsOnHit(0.5, 20));
        assertTrue(DuelMode.TOTAL.lethal());
        assertFalse(DuelMode.PRIMEIRO_GOLPE.lethal());
        assertEquals(DuelMode.PRIMEIRO_GOLPE, DuelMode.parse("Primeiro-Golpe"));
        assertEquals(DuelMode.TOTAL, DuelMode.parse("3"));
        assertNull(DuelMode.parse("espada"));
        assertNull(DuelMode.parse(null));
    }

    @Test
    void desafioAceiteEFim() {
        assertEquals(Ask.SELF, duels.challenge(kirito, kirito, DuelMode.METADE, 30_000));
        assertEquals(Ask.SENT, duels.challenge(kirito, asuna, DuelMode.METADE, 30_000));
        assertEquals(Ask.ALREADY, duels.challenge(kirito, asuna, DuelMode.METADE, 30_000));
        assertEquals(Ask.SENT, duels.challenge(kirito, asuna, DuelMode.PRIMEIRO_GOLPE, 30_000));
        assertEquals(Ask.SENT, duels.challenge(klein, asuna, DuelMode.TOTAL, 30_000));
        assertEquals(klein, duels.pending(asuna, null).from());
        Challenge c = duels.pending(asuna, kirito);
        assertEquals(DuelMode.PRIMEIRO_GOLPE, c.mode());

        Duel d = duels.begin(c, 5_000, 180_000);
        assertNotNull(d);
        assertSame(d, duels.duelOf(kirito));
        assertTrue(duels.inDuel(kirito, asuna));
        assertFalse(duels.inDuel(kirito, klein));
        assertEquals(asuna, d.opponent(kirito));
        assertNull(duels.pending(asuna, klein));
        assertEquals(Ask.BUSY, duels.challenge(kirito, klein, DuelMode.METADE, 30_000));
        assertEquals(Ask.TARGET_BUSY, duels.challenge(klein, asuna, DuelMode.METADE, 30_000));

        assertFalse(d.started(now.get()));
        now.addAndGet(5_000);
        assertTrue(d.started(now.get()));
        now.addAndGet(180_000);
        assertTrue(d.timedOut(now.get()));
        assertTrue(duels.end(d));
        assertFalse(duels.end(d));
        assertNull(duels.duelOf(kirito));
    }

    @Test
    void desafioExpiraEPodeSerRecusado() {
        duels.challenge(kirito, asuna, DuelMode.METADE, 30_000);
        now.addAndGet(30_000);
        assertNull(duels.pending(asuna, kirito));
        duels.challenge(kirito, asuna, DuelMode.METADE, 30_000);
        assertEquals(kirito, duels.decline(asuna, null).from());
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
        assertNull(duels.begin(second, 0, 1_000));
        assertNotNull(duels.begin(duels.pending(agil, klein), 0, 1_000));
        assertEquals(2, duels.duels().size());
    }

    @Test
    void noTempoVenceQuemTemMaisVida() {
        assertEquals(1, DuelService.byHealth(15, 20, 10, 20));
        assertEquals(-1, DuelService.byHealth(15, 40, 10, 20));
        assertEquals(0, DuelService.byHealth(10, 20, 20, 40));
    }

    // ------------------------------------------------------------------ aposta

    @Test
    void aApostaAcompanhaODesafio() {
        assertEquals(Ask.SENT, duels.challenge(kirito, asuna, DuelMode.METADE, 30_000, 500));
        // Mesmo modo com outra aposta é outro desafio; aposta negativa vira zero.
        assertEquals(Ask.ALREADY, duels.challenge(kirito, asuna, DuelMode.METADE, 30_000, 500));
        assertEquals(Ask.SENT, duels.challenge(kirito, asuna, DuelMode.METADE, 30_000, 1_000));
        assertEquals(1_000, duels.pending(asuna, kirito).stake());
        assertEquals(Ask.SENT, duels.challenge(klein, agil, DuelMode.METADE, 30_000, -5));
        assertEquals(0, duels.pending(agil, klein).stake());

        Duel d = duels.begin(duels.pending(asuna, kirito), 0, 1_000);
        assertEquals(1_000, d.stake());
        assertNull(d.holdA());
        d.holds("custodia-kirito", "custodia-asuna");
        assertEquals("custodia-kirito", d.holdA());
        assertEquals("custodia-asuna", d.holdB());
        // Sem aposta, o desafio antigo continua igual.
        assertEquals(0, duels.begin(duels.pending(agil, klein), 0, 1_000).stake());
    }

    // ------------------------------------------------------------------ placar

    @Test
    void placarEsequencia() {
        DuelScore s = DuelScore.EMPTY.won().won().won();
        assertEquals(3, s.wins());
        assertEquals(3, s.streak());
        assertEquals(3, s.best());
        // O empate não mexe na sequência; a derrota zera, mas a melhor fica.
        s = s.drew();
        assertEquals(3, s.streak());
        s = s.lost();
        assertEquals(0, s.streak());
        assertEquals(3, s.best());
        s = s.won();
        assertEquals(1, s.streak());
        assertEquals(3, s.best());
        assertEquals(6, s.total());
        // 4 vitórias em 5 duelos decididos (o empate não conta).
        assertEquals(80, s.winRate());
        assertEquals(0, DuelScore.EMPTY.winRate());
        assertEquals(0, DuelScore.EMPTY.drew().winRate());
    }

    // ------------------------------------------------------------------ arquivos

    @Test
    void mensagensEConfigTemOQueOCodigoUsa() {
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                DuelRulesTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (String key : List.of("prefixo", "uso", "enviado", "recebido", "recebido_total", "aceito", "lutem", "contagem_sub",
                "aposta.linha", "aposta.invalida", "aposta.sem_saldo", "aposta.alguem_sem_saldo", "aposta.levou", "aposta.devolvida",
                "placar.linha", "placar.ranking_titulo", "placar.ranking_linha", "placar.ranking_vazio",
                "menu.titulo", "menu.titulo_modo", "menu.titulo_aposta", "menu.sem_aposta", "menu.aposta")) {
            assertTrue(msg.isString(key), "falta a mensagem " + key);
        }
        for (DuelMode m : DuelMode.values()) {
            assertTrue(msg.isString("modo." + m.key()), m.key());
            assertTrue(msg.isList("menu.modo_lore." + m.key()), m.key());
        }
        for (String reason : List.of("golpe", "metade", "morte", "tempo", "fugiu", "saiu", "desistiu", "empate")) {
            assertTrue(msg.isString("fim." + reason), "falta fim." + reason);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                DuelRulesTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertNotNull(DuelMode.parse(cfg.getString("modo_padrao")));
        assertFalse(cfg.getLongList("aposta.valores").isEmpty());
        for (long v : cfg.getLongList("aposta.valores")) {
            assertTrue(v > 0 && v <= cfg.getLong("aposta.maximo"), "aposta " + v + " acima do máximo");
        }
    }
}
