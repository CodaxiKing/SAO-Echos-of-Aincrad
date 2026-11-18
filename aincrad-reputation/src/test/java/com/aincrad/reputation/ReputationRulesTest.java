package com.aincrad.reputation;

import com.aincrad.reputation.model.Achievements;
import com.aincrad.reputation.model.Achievements.Def;
import com.aincrad.reputation.model.Achievements.Type;
import com.aincrad.reputation.model.CursorRules;
import com.aincrad.reputation.model.CursorRules.Color;
import com.aincrad.reputation.model.CursorRules.Cursor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do AincradReputation (etapas 26 e 27), sem servidor. */
class ReputationRulesTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_000 * HOUR;

    private static Achievements bundled() {
        List<String> errors = new ArrayList<>();
        Achievements a = Achievements.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
                ReputationRulesTest.class.getResourceAsStream("/achievements.yml"), StandardCharsets.UTF_8)), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return a;
    }

    // ------------------------------------------------------------------ 26 conquistas e títulos

    @Test
    void oArquivoDoPluginCarregaSemErro() {
        Achievements a = bundled();
        assertTrue(a.all().size() >= 10);
        // As cinco do plano: primeiro no andar, primeira vitória de chefe, Last Attack, lendário, familiar.
        for (Type t : List.of(Type.PRIMEIRO_NO_ANDAR, Type.CHEFE_PRIMEIRA_VITORIA, Type.LAST_ATTACK, Type.PEIXE_LENDARIO, Type.FAMILIAR_DOMADO)) {
            assertFalse(a.matching(t, 3, "pesca").isEmpty(), t.name());
        }
        for (Def d : a.all().values()) {
            assertTrue(d.title() == null || d.title().length() <= 48, d.id());
        }
    }

    @Test
    void matarOChefeDoAndar1PelaPrimeiraVezDaAConquistaEOTitulo() {
        Achievements a = bundled();
        List<Def> floor1 = a.matching(Type.CHEFE_PRIMEIRA_VITORIA, 1, null);
        // A conquista geral da linha de frente e a do andar 1.
        assertEquals(2, floor1.size());
        Def first = a.get("conquistador_do_andar_1");
        assertTrue(floor1.contains(first));
        assertEquals("Conquistador do Andar 1", first.title());
        // No andar 2 só vale a geral.
        List<Def> floor2 = a.matching(Type.CHEFE_PRIMEIRA_VITORIA, 2, null);
        assertEquals(1, floor2.size());
        assertEquals("linha_de_frente", floor2.get(0).id());
        // Uma vitória que não é a primeira não dá a conquista de primeira vitória.
        assertTrue(a.matching(Type.CHEFE_DERROTADO, 1, null).stream().noneMatch(d -> d.id().equals("conquistador_do_andar_1")));
    }

    @Test
    void conquistaPorAndarEUmaParaCadaAndar() {
        Def d = bundled().get("primeiro_no_andar");
        assertTrue(d.perFloor());
        assertEquals("primeiro_no_andar_5", d.key(5));
        assertEquals("primeiro_no_andar_7", d.key(7));
        assertEquals("Primeiro no Andar 5", Def.fill(d.title(), 5));
        assertEquals("Foi o primeiro jogador a pisar no andar 5.", Def.fill(d.description(), 5));
        // Uma conquista comum tem uma chave só, seja qual for o andar.
        Def beater = bundled().get("beater");
        assertEquals("beater", beater.key(5));
        assertEquals("Beater", Def.fill(beater.title(), 5));
        assertNull(Def.fill(null, 5));
    }

    @Test
    void conquistaDeProfissaoSoValeParaAProfissaoDela() {
        Achievements a = bundled();
        List<Def> cook = a.matching(Type.PROFISSAO_COMPLETA, 0, "culinaria");
        assertEquals(1, cook.size());
        assertEquals("Mestre-Cuca", cook.get(0).title());
        assertEquals("mestre_ferreiro", a.matching(Type.PROFISSAO_COMPLETA, 0, "FERREIRO").get(0).id());
        assertTrue(a.matching(Type.PROFISSAO_COMPLETA, 0, "jardinagem").isEmpty());
        // Uma conquista pode não liberar título.
        assertNotNull(a.get("alianca"));
        assertNull(a.get("alianca").title());
    }

    @Test
    void errosNoArquivoSaoApontados() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                conquistas:
                  boa: {nome: Boa, tipo: last_attack, titulo: Beater}
                  tipo_ruim: {nome: X, tipo: voar}
                  titulo_longo: {nome: Y, tipo: casamento, titulo: "Um título comprido demais para caber antes do nome de alguém"}
                """);
        List<String> errors = new ArrayList<>();
        Achievements a = Achievements.parse(y, errors);
        assertEquals(1, a.all().size());
        assertEquals(2, errors.size(), errors.toString());
    }

    // ------------------------------------------------------------------ 27 cor do cursor

    @Test
    void atacarUmVerdeDeixaLaranja() {
        Cursor c = CursorRules.attacked(Cursor.CLEAN, Color.VERDE, NOW, 24 * HOUR);
        assertEquals(Color.LARANJA, c.color(NOW));
        assertEquals(Color.LARANJA, c.color(NOW + 23 * HOUR));
        // Depois de 24 h volta a ser verde.
        assertEquals(Color.VERDE, c.color(NOW + 24 * HOUR));
        // Atacar de novo renova o prazo.
        Cursor again = CursorRules.attacked(c, Color.VERDE, NOW + 10 * HOUR, 24 * HOUR);
        assertEquals(Color.LARANJA, again.color(NOW + 30 * HOUR));
        assertEquals(0, again.kills());
    }

    @Test
    void atacarLaranjaOuVermelhoNaoMudaACor() {
        assertEquals(Cursor.CLEAN, CursorRules.attacked(Cursor.CLEAN, Color.LARANJA, NOW, 24 * HOUR));
        assertEquals(Cursor.CLEAN, CursorRules.attacked(Cursor.CLEAN, Color.VERMELHO, NOW, 24 * HOUR));
        // Matar um vermelho (caça a criminoso) também não.
        assertEquals(Cursor.CLEAN, CursorRules.killed(Cursor.CLEAN, Color.VERMELHO, NOW, 48 * HOUR, 0.5));
        assertEquals(Cursor.CLEAN, CursorRules.killed(Cursor.CLEAN, Color.LARANJA, NOW, 48 * HOUR, 0.5));
    }

    @Test
    void matarUmVerdeDeixaVermelhoEOTempoCresceACadaMorte() {
        Cursor one = CursorRules.killed(Cursor.CLEAN, Color.VERDE, NOW, 48 * HOUR, 0.5);
        assertEquals(Color.VERMELHO, one.color(NOW));
        assertEquals(1, one.kills());
        assertEquals(NOW + 48 * HOUR, one.redUntil());
        // A segunda morte vale 72 h, somadas ao que restava.
        Cursor two = CursorRules.killed(one, Color.VERDE, NOW, 48 * HOUR, 0.5);
        assertEquals(2, two.kills());
        assertEquals(NOW + 48 * HOUR + 72 * HOUR, two.redUntil());
        // A terceira, 96 h — contadas de agora, se o vermelho anterior já tinha acabado.
        Cursor later = CursorRules.killed(new Cursor(0, NOW - HOUR, 2), Color.VERDE, NOW, 48 * HOUR, 0.5);
        assertEquals(NOW + 96 * HOUR, later.redUntil());
        // Vermelho vence laranja.
        assertEquals(Color.VERMELHO, new Cursor(NOW + HOUR, NOW + HOUR, 1).color(NOW));
    }

    @Test
    void laranjaEVermelhoNaoEntramNasCidades() {
        assertFalse(CursorRules.barredFromCity(Color.VERDE));
        assertTrue(CursorRules.barredFromCity(Color.LARANJA));
        assertTrue(CursorRules.barredFromCity(Color.VERMELHO));
    }

    @Test
    void vermelhoDerrotadoVaiPresoPeloTempoConfigurado() {
        // Só o vermelho, e só se foi outro jogador que o derrotou.
        assertTrue(CursorRules.goesToJail(Color.VERMELHO, true));
        assertFalse(CursorRules.goesToJail(Color.VERMELHO, false));
        assertFalse(CursorRules.goesToJail(Color.LARANJA, true));
        assertFalse(CursorRules.goesToJail(Color.VERDE, true));
        // 30 min por morte causada, até 24 h.
        long perKill = 30 * 60_000L;
        assertEquals(30 * 60_000L, CursorRules.jailMs(1, perKill, 24 * HOUR));
        assertEquals(2 * HOUR, CursorRules.jailMs(4, perKill, 24 * HOUR));
        assertEquals(24 * HOUR, CursorRules.jailMs(100, perKill, 24 * HOUR));
    }

    @Test
    void cumpridaAPrisaoVoltaLaranja() {
        Cursor red = new Cursor(0, NOW + 200 * HOUR, 3);
        Cursor out = CursorRules.released(red, NOW, 24 * HOUR);
        assertEquals(Color.LARANJA, out.color(NOW));
        assertEquals(Color.VERDE, out.color(NOW + 24 * HOUR));
        // As mortes causadas ficam: a próxima pesa mais.
        assertEquals(3, out.kills());
    }
}
