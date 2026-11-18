package com.aincrad.bosses;

import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.BossRegistry;
import com.aincrad.bosses.model.RoamingRules;
import com.aincrad.bosses.model.RoamingRules.Entry;
import com.aincrad.bosses.model.RoamingRules.Window;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras dos chefes de campo e de evento (etapa 41), sem servidor. */
class RoamingRulesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final long HOUR = 3_600_000L;

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static Map<String, Entry> parse(String yaml, List<String> errors) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        return RoamingRules.parse(y, errors);
    }

    private static final String YAML = """
            campo:
              lobo_alfa: { regiao: Campos_Andar1, intervalo_horas: 6 }
              sem_regiao: { intervalo_horas: 1 }
            evento:
              nicholas: { regiao: floresta_andar35, data: "12-24", hora: "23:00", duracao_minutos: 120 }
              virada: { chefe: nicholas, regiao: x, data: "12-31", hora: "23:30", duracao_minutos: 60 }
              data_ruim: { regiao: x, data: "24/12" }
              lobo_alfa: { regiao: x, data: "01-01" }
              colheita: { chefe: nicholas, regiao: x, calendario: Festival_da_Colheita, hora: "20:00", duracao_minutos: 90 }
            """;

    @Test
    void oArquivoELidoEOsErrosSaoApontados() throws Exception {
        List<String> errors = new ArrayList<>();
        Map<String, Entry> m = parse(YAML, errors);
        assertEquals(List.of("lobo_alfa", "nicholas", "virada", "colheita"), List.copyOf(m.keySet()));
        // Evento pela data do calendário do AincradWorld: sem "data" fixa, a janela vem do dia que ele der.
        Entry harvest = m.get("colheita");
        assertTrue(harvest.event());
        assertEquals("festival_da_colheita", harvest.calendar());
        assertNull(RoamingRules.window(harvest, at(2026, 6, 1, 12, 0), ZONE));
        Window hw = RoamingRules.windowOn(harvest, at(2026, 6, 1, 0, 0));
        assertEquals(at(2026, 6, 1, 20, 0), hw.start());
        assertEquals(at(2026, 6, 1, 21, 30), hw.end());
        assertEquals(3, errors.size(), errors.toString());
        Entry wolf = m.get("lobo_alfa");
        assertFalse(wolf.event());
        assertEquals("lobo_alfa", wolf.boss());
        assertEquals("campos_andar1", wolf.region());
        assertEquals(6 * HOUR, wolf.intervalMs());
        assertNull(wolf.spot());
        Entry nick = m.get("nicholas");
        assertTrue(nick.event());
        assertEquals(2 * HOUR, nick.durationMs());
        assertEquals("nicholas", m.get("virada").boss());
        // O arquivo do plugin vem sem chefes: nada aparece sem o dono querer.
        List<String> none = new ArrayList<>();
        assertTrue(RoamingRules.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
                RoamingRulesTest.class.getResourceAsStream("/roaming.yml"), StandardCharsets.UTF_8)), none).isEmpty());
        assertTrue(none.isEmpty());
    }

    @Test
    void oChefeDeCampoVoltaNoIntervalo() {
        long killed = 100 * HOUR;
        long next = killed + 6 * HOUR;
        // Nunca apareceu: pode aparecer já.
        assertTrue(RoamingRules.fieldDue(killed, 0));
        assertFalse(RoamingRules.fieldDue(killed + 5 * HOUR, next));
        assertTrue(RoamingRules.fieldDue(killed + 6 * HOUR, next));
    }

    @Test
    void oNicholasApareceNaNoiteDeNatalESoUmaVezPorAno() throws Exception {
        Entry nick = parse(YAML, new ArrayList<>()).get("nicholas");
        Window w = RoamingRules.window(nick, at(2026, 12, 24, 23, 30), ZONE);
        assertEquals(at(2026, 12, 24, 23, 0), w.start());
        assertEquals(at(2026, 12, 25, 1, 0), w.end());
        assertFalse(RoamingRules.eventActive(RoamingRules.window(nick, at(2026, 12, 24, 22, 59), ZONE), at(2026, 12, 24, 22, 59), 0));
        assertTrue(RoamingRules.eventActive(w, at(2026, 12, 24, 23, 0), 0));
        assertTrue(RoamingRules.eventActive(w, at(2026, 12, 25, 0, 59), 0));
        // Passou da janela: some.
        assertFalse(RoamingRules.eventActive(RoamingRules.window(nick, at(2026, 12, 25, 1, 0), ZONE), at(2026, 12, 25, 1, 0), 0));
        // Derrotado às 23h40: não volta na mesma noite, mas volta no Natal seguinte.
        long killed = at(2026, 12, 24, 23, 40);
        assertFalse(RoamingRules.eventActive(w, at(2026, 12, 24, 23, 50), killed));
        long next = at(2027, 12, 24, 23, 10);
        assertTrue(RoamingRules.eventActive(RoamingRules.window(nick, next, ZONE), next, killed));
    }

    @Test
    void aJanelaQueAtravessaAViradaDoAno() throws Exception {
        Entry e = parse(YAML, new ArrayList<>()).get("virada");
        long after = at(2027, 1, 1, 0, 10);
        Window w = RoamingRules.window(e, after, ZONE);
        // Dez minutos depois da meia-noite ainda é a janela que começou em 31/12/2026.
        assertEquals(at(2026, 12, 31, 23, 30), w.start());
        assertTrue(RoamingRules.eventActive(w, after, 0));
        long later = at(2027, 1, 1, 0, 40);
        assertFalse(RoamingRules.eventActive(RoamingRules.window(e, later, ZONE), later, 0));
    }

    @Test
    void osChefesDeExemploSaoValidosEONicholasDaOCristal() {
        BossRegistry reg = new BossRegistry();
        reg.add(YamlConfiguration.loadConfiguration(new InputStreamReader(
                RoamingRulesTest.class.getResourceAsStream("/bosses/campo_e_evento.yml"), StandardCharsets.UTF_8)), "campo_e_evento.yml");
        assertTrue(reg.errors().isEmpty(), reg.errors().toString());
        BossDef nick = reg.get("nicholas");
        assertNotNull(nick);
        assertEquals("cristal:ressurreicao", nick.rewards().lastAttack());
        assertEquals(2, nick.phases().size());
        assertNotNull(reg.get("lobo_alfa"));
    }
}
