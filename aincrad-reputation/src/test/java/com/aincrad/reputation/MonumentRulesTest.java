package com.aincrad.reputation;

import com.aincrad.reputation.model.MonumentRules;
import com.aincrad.reputation.model.MonumentRules.Cause;
import com.aincrad.reputation.model.MonumentRules.Entry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do Monumento da Vida (etapa 39), sem servidor. */
class MonumentRulesTest {

    private static final List<Entry> ALL = List.of(
            new Entry("3", "Sachi", 300, 900, Cause.MOB, "Golem", 1),
            new Entry("1", "Kirito", 100, 0, null, "", 0),
            new Entry("2", "Asuna", 100, 0, null, "", 0),
            new Entry("4", "Diabel", 400, 500, Cause.CHEFE, "illfang", 2));

    @Test
    void aCausaDaMorte() {
        assertEquals(Cause.JOGADOR, MonumentRules.cause("ENTITY_ATTACK", true, false, true));
        // Numa luta de chefe, morrer para um ajudante dele ou para o fogo dele é morrer para o chefe.
        assertEquals(Cause.CHEFE, MonumentRules.cause("ENTITY_ATTACK", false, true, true));
        assertEquals(Cause.CHEFE, MonumentRules.cause("FIRE", false, true, false));
        // Mas um jogador que mata outro na sala do chefe é a causa.
        assertEquals(Cause.JOGADOR, MonumentRules.cause("ENTITY_ATTACK", true, true, true));
        assertEquals(Cause.MOB, MonumentRules.cause("ENTITY_ATTACK", false, false, true));
        assertEquals(Cause.MOB, MonumentRules.cause("PROJECTILE", false, false, true));
        assertEquals(Cause.QUEDA, MonumentRules.cause("FALL", false, false, false));
        assertEquals(Cause.OUTRO, MonumentRules.cause("DROWNING", false, false, false));
        assertEquals(Cause.OUTRO, MonumentRules.cause(null, false, false, false));
    }

    @Test
    void osNomesFicamNaOrdemEmQueEntraram() {
        List<Entry> o = MonumentRules.ordered(ALL);
        assertEquals(List.of("Asuna", "Kirito", "Sachi", "Diabel"), o.stream().map(Entry::name).toList());
        assertFalse(o.get(0).struck());
        assertTrue(o.get(2).struck());
        assertArrayEquals(new int[] {2, 2}, MonumentRules.count(ALL));
    }

    @Test
    void aBuscaAchaPeloPedacoDoNome() {
        assertEquals(1, MonumentRules.search(ALL, "kir").size());
        assertEquals(1, MonumentRules.search(ALL, " SACH ").size());
        // "a" está em Sachi, Asuna e Diabel.
        assertEquals(3, MonumentRules.search(ALL, "A").size());
        assertEquals(4, MonumentRules.search(ALL, " ").size());
        assertEquals(4, MonumentRules.search(ALL, null).size());
        assertTrue(MonumentRules.search(ALL, "heathcliff").isEmpty());
    }

    @Test
    void oMuralPassaAsPaginasEDaAVolta() {
        List<Entry> o = MonumentRules.ordered(ALL);
        assertEquals(2, MonumentRules.pages(4, 3));
        assertEquals(1, MonumentRules.pages(0, 3));
        assertEquals(3, MonumentRules.page(o, 0, 3).size());
        assertEquals(List.of("Diabel"), MonumentRules.page(o, 1, 3).stream().map(Entry::name).toList());
        // A página 2 é a primeira de novo.
        assertEquals(MonumentRules.page(o, 0, 3), MonumentRules.page(o, 2, 3));
        assertTrue(MonumentRules.page(List.of(), 5, 3).isEmpty());
    }

    @Test
    void todaCausaTemMensagem() {
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                MonumentRulesTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (Cause c : Cause.values()) {
            assertNotNull(msg.getString("monumento.causa." + c.name().toLowerCase(Locale.ROOT)), c.name());
        }
    }
}
