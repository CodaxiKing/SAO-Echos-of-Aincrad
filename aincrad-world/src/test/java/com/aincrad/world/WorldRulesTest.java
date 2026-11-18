package com.aincrad.world;

import com.aincrad.world.model.WorldRules;
import com.aincrad.world.model.WorldRules.Calendar;
import com.aincrad.world.model.WorldRules.Chances;
import com.aincrad.world.model.WorldRules.Weather;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do AincradWorld (etapas 44, 57, 58 e 59), sem servidor. */
class WorldRulesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                WorldRulesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    private static long at(int y, int m, int d, int h) {
        return LocalDateTime.of(y, m, d, h, 0).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static Calendar calendar() {
        List<String> errors = new ArrayList<>();
        Calendar c = WorldRules.calendar(resource("calendar.yml"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return c;
    }

    // ------------------------------------------------------------------ 44 jornal

    @Test
    void aEdicaoEASemana() {
        // 18/11/2026 é quarta, na semana 47; a edição vai de segunda 16 a segunda 23.
        assertEquals("2026-W47", WorldRules.edition(at(2026, 11, 18, 12), ZONE));
        assertEquals("2026-W47", WorldRules.edition(at(2026, 11, 22, 23), ZONE));
        assertEquals("2026-W48", WorldRules.edition(at(2026, 11, 23, 0), ZONE));
        assertArrayEquals(new long[] {at(2026, 11, 16, 0), at(2026, 11, 23, 0)}, WorldRules.week("2026-W47", ZONE));
        assertArrayEquals(new long[] {at(2026, 11, 16, 0), at(2026, 11, 23, 0)}, WorldRules.week("2026-w47", ZONE));
        // Virada do ano: 01/01/2027 (sexta) ainda é a semana 53 de 2026.
        assertEquals("2026-W53", WorldRules.edition(at(2027, 1, 1, 12), ZONE));
        assertNull(WorldRules.week("2026-W99", ZONE));
        assertNull(WorldRules.week("semana passada", ZONE));
        assertNull(WorldRules.week("2026-47", ZONE));
    }

    @Test
    void asNoticiasSaoDivididasEmPaginas() {
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7);
        assertEquals(List.of(List.of(1, 2, 3, 4, 5), List.of(6, 7)), WorldRules.pages(items, 5));
        assertTrue(WorldRules.pages(List.of(), 5).isEmpty());
        assertEquals("uma linha só", WorldRules.clip(" uma\nlinha só ", 50));
        assertEquals(10, WorldRules.clip("x".repeat(40), 10).length());
        // Todo tipo de notícia que o plugin grava tem texto.
        YamlConfiguration msg = resource("messages.yml");
        for (String kind : List.of("andar", "chefe", "torneio", "casamento", "preso", "conquista", "estacao", "data")) {
            assertNotNull(msg.getString("noticia." + kind), kind);
        }
    }

    // ------------------------------------------------------------------ 57 calendário

    @Test
    void asEstacoesGiramDeSeteEmSeteDias() {
        Calendar c = calendar();
        LocalDate start = LocalDate.of(2026, 1, 1);
        assertEquals("primavera", c.season(start));
        assertEquals(1, c.seasonDay(start));
        assertEquals("primavera", c.season(start.plusDays(6)));
        assertEquals(7, c.seasonDay(start.plusDays(6)));
        assertEquals("verao", c.season(start.plusDays(7)));
        assertEquals("inverno", c.season(start.plusDays(27)));
        // O ano de Aincrad tem 28 dias: no dia 29 é primavera do ano 2.
        assertEquals("primavera", c.season(start.plusDays(28)));
        assertEquals(1, c.year(start.plusDays(27)));
        assertEquals(2, c.year(start.plusDays(28)));
        // Antes do início conta como o primeiro dia.
        assertEquals("primavera", c.season(start.minusDays(10)));
    }

    @Test
    void umPluginPerguntaSeHojeEANoiteDeNatal() {
        Calendar c = calendar();
        assertTrue(c.isDate("natal", LocalDate.of(2026, 12, 24)));
        assertTrue(c.isDate("NATAL", LocalDate.of(2031, 12, 24)));
        assertFalse(c.isDate("natal", LocalDate.of(2026, 12, 25)));
        assertFalse(c.isDate("pascoa", LocalDate.of(2026, 12, 24)));
        assertEquals(LocalDate.of(2026, 12, 24), c.next("natal", LocalDate.of(2026, 11, 18)));
        assertEquals(LocalDate.of(2027, 12, 24), c.next("natal", LocalDate.of(2026, 12, 25)));
        assertNull(c.next("pascoa", LocalDate.of(2026, 11, 18)));
        // Data de estação: o 3º dia de cada outono (o outono começa 14 dias depois do início).
        LocalDate autumn3 = LocalDate.of(2026, 1, 1).plusDays(14 + 2);
        assertTrue(c.isDate("festival_da_colheita", autumn3));
        assertFalse(c.isDate("festival_da_colheita", autumn3.plusDays(1)));
        assertEquals(autumn3.plusDays(28), c.next("festival_da_colheita", autumn3.plusDays(1)));
        assertEquals(List.of("festival_da_colheita"), c.today(autumn3).stream().map(WorldRules.DateDef::id).toList());
    }

    @Test
    void errosNoCalendarioSaoApontados() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                inicio: "ontem"
                dias_por_estacao: 5
                datas:
                  boa: { dia: "06-15" }
                  dia_ruim: { dia: "15/06" }
                  estacao_ruim: { estacao: monções, dia: 1 }
                  dia_fora: { estacao: verao, dia: 6 }
                """);
        List<String> errors = new ArrayList<>();
        Calendar c = WorldRules.calendar(y, errors);
        assertEquals(4, errors.size(), errors.toString());
        assertEquals(List.of("boa"), List.copyOf(c.dates().keySet()));
        assertEquals(LocalDate.of(2026, 1, 1), c.start());
        assertEquals(4, c.seasons().size());
    }

    // ------------------------------------------------------------------ 58 dia e noite

    @Test
    void aNoiteDeixaOMobMaisForteEOsDropsMaiores() {
        assertFalse(WorldRules.night(0));
        assertFalse(WorldRules.night(12999));
        assertTrue(WorldRules.night(13000));
        assertTrue(WorldRules.night(22999));
        assertFalse(WorldRules.night(23000));
        // O relógio do mundo não zera: 24000 + 18000 também é noite.
        assertTrue(WorldRules.night(42000));
        assertEquals(5, WorldRules.nightLevel(4, 1.25));
        assertEquals(13, WorldRules.nightLevel(10, 1.25));
        // Nunca abaixa o level.
        assertEquals(10, WorldRules.nightLevel(10, 0.5));
        assertEquals(1, WorldRules.nightLevel(1, 1.25));
        // Drop x1.5: 4 itens rendem +2; 1 item rende +1 em metade das vezes.
        assertEquals(2, WorldRules.extra(4, 1.5, 0.99));
        assertEquals(1, WorldRules.extra(1, 1.5, 0.49));
        assertEquals(0, WorldRules.extra(1, 1.5, 0.5));
        assertEquals(0, WorldRules.extra(8, 1.0, 0.0));
        assertEquals(1.5, resource("world.yml").getDouble("noite.drop"), 1e-9);
    }

    // ------------------------------------------------------------------ 59 clima

    @Test
    void cadaAndarTemOSeuClima() {
        YamlConfiguration w = resource("weather.yml");
        // O andar 35 tem névoa em 60% do tempo; no inverno, névoa e neve.
        Chances fog = WorldRules.chances(w, 35, "primavera");
        assertEquals(0.6, fog.fog(), 1e-9);
        assertEquals(Weather.CHUVA, fog.roll(0.05));
        assertEquals(Weather.NEVOA, fog.roll(0.5));
        assertEquals(Weather.LIMPO, fog.roll(0.75));
        Chances winter = WorldRules.chances(w, 35, "inverno");
        assertEquals(0.4, winter.snow(), 1e-9);
        assertEquals(Weather.NEVE, winter.roll(0.1));
        // Andar sem linha própria: o padrão do arquivo.
        Chances def = WorldRules.chances(w, 7, "verao");
        assertEquals(0.15, def.rain(), 1e-9);
        assertEquals(Weather.LIMPO, def.roll(0.5));
        // Sem o AincradFloors todo lugar é o andar 0.
        assertEquals(def, WorldRules.chances(w, 0, "verao"));
        assertEquals(Chances.NONE, WorldRules.chances(null, 1, "verao"));
        YamlConfiguration msg = resource("messages.yml");
        for (Weather x : Weather.values()) {
            assertNotNull(msg.getString("clima." + x.name().toLowerCase(Locale.ROOT)), x.name());
        }
    }

    @Test
    void chancesQueSomamMaisQueUmSaoAjustadas() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("padrao: { chuva: 1, neve: 1, nevoa: 2 }");
        Chances c = WorldRules.chances(y, 1, "verao");
        assertEquals(0.25, c.rain(), 1e-9);
        assertEquals(0.5, c.fog(), 1e-9);
        assertEquals(Weather.NEVOA, c.roll(0.99));
    }
}
