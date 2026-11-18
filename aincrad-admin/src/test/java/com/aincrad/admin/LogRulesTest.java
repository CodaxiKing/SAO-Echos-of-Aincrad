package com.aincrad.admin;

import com.aincrad.admin.model.LogRules;
import com.aincrad.admin.model.LogRules.Filter;
import com.aincrad.admin.model.LogRules.Where;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do registro de admin (etapa 28), sem servidor. */
class LogRulesTest {

    private static Filter f(String... args) {
        return Filter.parse(args);
    }

    @Test
    void semArgumentosMostraTudoNaPrimeiraPagina() {
        assertEquals(new Filter(null, null, 1), f());
        assertEquals(new Filter(null, null, 1), f("*"));
        assertEquals(new Filter(null, null, 1), f("*", "*"));
    }

    @Test
    void jogadorTipoEPagina() {
        assertEquals(new Filter("Kirito", null, 1), f("Kirito"));
        assertEquals(new Filter("Kirito", "andar", 1), f("Kirito", "Andar"));
        assertEquals(new Filter(null, "floors", 1), f("*", "floors"));
        assertEquals(new Filter("Kirito", "andar.liberar", 3), f("Kirito", "andar.liberar", "3"));
        // Um número no fim é a página, com ou sem os outros.
        assertEquals(new Filter(null, null, 2), f("2"));
        assertEquals(new Filter("Kirito", null, 2), f("Kirito", "2"));
    }

    @Test
    void paginaInvalidaEArgumentosDemais() {
        assertNull(f("Kirito", "andar", "abc"));
        assertNull(f("Kirito", "andar", "0"));
        assertNull(f("0"));
        assertNull(f("a", "b", "1", "2"));
    }

    @Test
    void semFiltroAConsultaNaoTemWhere() {
        Where w = LogRules.where(f());
        assertEquals("", w.sql());
        assertTrue(w.params().isEmpty());
    }

    @Test
    void jogadorValeParaQuemFezEParaQuemApareceNoDetalhe() {
        Where w = LogRules.where(f("Kirito"));
        assertEquals(" WHERE (LOWER(actor) = ? OR LOWER(detail) LIKE ? ESCAPE '!')", w.sql());
        assertEquals(List.of("kirito", "%kirito%"), w.params());
        // Um nome com sublinhado não vira curinga.
        assertEquals(List.of("black_cat", "%black!_cat%"), LogRules.where(f("Black_Cat")).params());
    }

    @Test
    void tipoValeParaOPluginAAcaoInteiraOuOComecoDela() {
        Where w = LogRules.where(f("*", "andar"));
        assertEquals(" WHERE (plugin = ? OR action = ? OR action LIKE ? ESCAPE '!')", w.sql());
        assertEquals(List.of("andar", "andar", "andar.%"), w.params());
        // Os dois filtros juntos.
        Where both = LogRules.where(f("Kirito", "floors"));
        assertTrue(both.sql().contains(" AND "));
        assertEquals(5, both.params().size());
    }

    @Test
    void textoCortadoNoTamanhoDaColunaESemQuebraDeLinha() {
        assertEquals("", LogRules.clip(null, 10));
        assertEquals("andar 5", LogRules.clip("  andar 5 ", 10));
        assertEquals("uma linha", LogRules.clip("uma\r\nlinha", 20));
        String cut = LogRules.clip("x".repeat(600), LogRules.DETAIL_MAX);
        assertEquals(LogRules.DETAIL_MAX, cut.length());
        assertTrue(cut.endsWith("…"));
        assertEquals("100!% de a!_b!!", LogRules.escapeLike("100% de a_b!"));
    }

    @Test
    void paginasEPrazoDeGuarda() {
        assertEquals(0, LogRules.offset(1, 10));
        assertEquals(20, LogRules.offset(3, 10));
        assertEquals(0, LogRules.offset(0, 10));
        long day = 86_400_000L;
        assertEquals(10 * day, LogRules.cutoff(100 * day, 90));
        // 0 dias = guarda para sempre.
        assertEquals(0, LogRules.cutoff(100 * day, 0));
    }
}
