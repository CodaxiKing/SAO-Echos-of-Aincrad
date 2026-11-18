package com.aincrad.quests;

import com.aincrad.quests.model.QuestDef;
import com.aincrad.quests.model.QuestDef.Goal;
import com.aincrad.quests.model.QuestRules;
import com.aincrad.quests.model.QuestRules.Advance;
import com.aincrad.quests.model.QuestRules.Block;
import com.aincrad.quests.model.QuestRules.Progress;
import com.aincrad.quests.model.QuestRules.State;
import com.aincrad.quests.model.QuestRules.Step;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do AincradQuests (etapas 37 e 38), sem servidor. */
class QuestRulesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static long at(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static Map<String, QuestDef> bundled() {
        List<String> errors = new ArrayList<>();
        Map<String, QuestDef> q = QuestDef.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
                QuestRulesTest.class.getResourceAsStream("/quests/exemplo.yml"), StandardCharsets.UTF_8)), "exemplo.yml", errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return q;
    }

    // ------------------------------------------------------------------ 37 motor

    @Test
    void matar5LobosEAceitaConcluidaEPagaXpECol() {
        QuestDef def = bundled().get("lobos_na_estrada");
        assertEquals(120, def.reward().xp());
        assertEquals(80, def.reward().col());
        assertEquals("guarda", def.npc());
        // Aceita: sem bloqueio para quem nunca fez.
        assertNull(QuestRules.blocked(def, null, 0, ZONE, 1, 1, Set.of()));
        Progress p = Progress.started(null);
        // Um zumbi não conta; um lobo conta, pelo tipo ou pelo mob do spawns.yml.
        assertFalse(def.stages().get(0).matches(Goal.MATAR, "ZOMBIE", null));
        assertTrue(def.stages().get(0).matches(Goal.MATAR, "WOLF", "mythic:LoboTerrivel"));
        assertFalse(def.stages().get(0).matches(Goal.PESCAR, "WOLF"));
        for (int i = 1; i <= 4; i++) {
            Advance a = QuestRules.advance(def, p, 1);
            assertEquals(Step.AVANCOU, a.step());
            p = a.progress();
            assertEquals(i, p.count());
            assertFalse(QuestRules.ready(def, p));
        }
        Advance last = QuestRules.advance(def, p, 1);
        assertEquals(Step.PRONTA, last.step());
        assertTrue(QuestRules.ready(def, last.progress()));
        // Pronta, mais lobos não mudam nada.
        assertEquals(Step.NADA, QuestRules.advance(def, last.progress(), 1).step());
        Progress done = QuestRules.completed(last.progress(), 1000);
        assertEquals(State.FEITA, done.state());
        assertEquals(1, done.times());
        // História não se repete.
        assertEquals(Block.JA_FEITA, QuestRules.blocked(def, done, at(2030, 1, 1, 0), ZONE, 1, 1, Set.of()));
    }

    @Test
    void etapasEmSequenciaEColetarOlhaOInventario() {
        QuestDef def = bundled().get("peles_para_o_guarda");
        assertEquals(2, def.stages().size());
        assertEquals(Goal.COLETAR, def.stages().get(0).goal());
        assertEquals("LEATHER", def.stages().get(0).target());
        Progress p = Progress.started(null);
        // 2 couros no inventário; depois o jogador perde um; depois junta 3.
        p = QuestRules.setCount(def, p, 2).progress();
        assertEquals(2, p.count());
        Advance lost = QuestRules.setCount(def, p, 1);
        assertEquals(Step.AVANCOU, lost.step());
        assertEquals(1, lost.progress().count());
        assertEquals(Step.NADA, QuestRules.setCount(def, lost.progress(), 1).step());
        Advance got = QuestRules.setCount(def, lost.progress(), 5);
        assertEquals(Step.ETAPA_CONCLUIDA, got.step());
        assertEquals(1, got.progress().stage());
        assertEquals(0, got.progress().count());
        // A entrega fecha a quest.
        assertEquals(Step.PRONTA, QuestRules.advance(def, got.progress(), 3).step());
        assertEquals(List.of("BREAD 4"), def.reward().items());
    }

    @Test
    void requisitosDeLevelAndarEQuestAnterior() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                dificil:
                  npc: Guarda
                  requisitos: {level: 10, andar: 2, quest: lobos}
                  etapas: [{tipo: chefe, alvo: illfang}]
                """);
        QuestDef def = QuestDef.parse(y, "t.yml", new ArrayList<>()).get("dificil");
        assertEquals("guarda", def.npc());
        assertEquals(Block.LEVEL, QuestRules.blocked(def, null, 0, ZONE, 9, 5, Set.of("lobos")));
        assertEquals(Block.ANDAR, QuestRules.blocked(def, null, 0, ZONE, 10, 1, Set.of("lobos")));
        assertEquals(Block.QUEST_ANTERIOR, QuestRules.blocked(def, null, 0, ZONE, 10, 2, Set.of()));
        assertNull(QuestRules.blocked(def, null, 0, ZONE, 10, 2, Set.of("lobos")));
        // Sem o RPG e sem o Floors o level e o andar vêm no máximo: nada barra.
        assertNull(QuestRules.blocked(def, null, 0, ZONE, Integer.MAX_VALUE, Integer.MAX_VALUE, Set.of("lobos")));
        assertEquals(Block.ATIVA, QuestRules.blocked(def, Progress.started(null), 0, ZONE, 10, 2, Set.of("lobos")));
        assertTrue(def.stages().get(0).matches(Goal.CHEFE, "Illfang"));
    }

    @Test
    void errosNoArquivoSaoApontados() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                boa: {npc: a, etapas: [{tipo: pescar, quantidade: 3}]}
                sem_npc: {etapas: [{tipo: pescar}]}
                tipo_ruim: {npc: a, tipo: mensal, etapas: [{tipo: pescar}]}
                etapa_ruim: {npc: a, etapas: [{tipo: voar}]}
                sem_item: {npc: a, etapas: [{tipo: coletar, quantidade: 2}]}
                sem_etapas: {npc: a}
                """);
        List<String> errors = new ArrayList<>();
        Map<String, QuestDef> q = QuestDef.parse(y, "t.yml", errors);
        assertEquals(List.of("boa"), List.copyOf(q.keySet()));
        assertEquals(5, errors.size(), errors.toString());
        assertEquals(QuestDef.Kind.HISTORIA, q.get("boa").kind());
        assertNull(q.get("boa").stages().get(0).target());
        // Sem alvo, qualquer coisa do tipo conta.
        assertTrue(q.get("boa").stages().get(0).matches(Goal.PESCAR));
    }

    @Test
    void itensDaRecompensa() {
        assertArrayEquals(new String[] {"BREAD", "3"}, QuestRules.item("BREAD 3"));
        assertArrayEquals(new String[] {"rpg:espada_de_ferro", "1"}, QuestRules.item(" rpg:espada_de_ferro "));
        assertNull(QuestRules.item("BREAD tres"));
        assertNull(QuestRules.item("BREAD 0"));
        assertNull(QuestRules.item("A B C"));
        assertNull(QuestRules.item(""));
    }

    // ------------------------------------------------------------------ 38 diárias e party

    @Test
    void aDiariaRenovaNoDiaSeguinte() {
        QuestDef def = bundled().get("ronda_diaria");
        assertEquals(QuestDef.Kind.DIARIA, def.kind());
        assertTrue(def.party());
        long done = at(2026, 11, 18, 23);
        Progress p = QuestRules.completed(Progress.started(null), done);
        // Uma hora depois já é outro dia: renovou. No mesmo dia, não.
        assertEquals(Block.JA_FEITA, QuestRules.blocked(def, QuestRules.completed(null, at(2026, 11, 18, 9)), at(2026, 11, 18, 22), ZONE, 1, 1, Set.of()));
        assertNull(QuestRules.blocked(def, p, at(2026, 11, 19, 0), ZONE, 1, 1, Set.of()));
        // Ao pegar de novo, a contagem zera e o número de vezes fica.
        Progress again = Progress.started(p);
        assertEquals(State.ATIVA, again.state());
        assertEquals(0, again.count());
        assertEquals(1, again.times());
        assertEquals(2, QuestRules.completed(again, done).times());
    }

    @Test
    void aSemanalRenovaNaSegunda() {
        // 18/11/2026 é quarta. Domingo 22 ainda é a mesma semana; segunda 23 já é outra.
        long done = at(2026, 11, 18, 12);
        assertFalse(QuestRules.renewed(QuestDef.Kind.SEMANAL, done, at(2026, 11, 22, 23), ZONE));
        assertTrue(QuestRules.renewed(QuestDef.Kind.SEMANAL, done, at(2026, 11, 23, 0), ZONE));
        // Virada de ano: 31/12/2026 (quinta) e 01/01/2027 (sexta) são a mesma semana.
        assertFalse(QuestRules.renewed(QuestDef.Kind.SEMANAL, at(2026, 12, 31, 12), at(2027, 1, 1, 12), ZONE));
        assertTrue(QuestRules.renewed(QuestDef.Kind.SEMANAL, at(2026, 12, 31, 12), at(2027, 1, 4, 0), ZONE));
        assertFalse(QuestRules.renewed(QuestDef.Kind.HISTORIA, done, at(2030, 1, 1, 0), ZONE));
    }

    @Test
    void todaMensagemDeObjetivoETipoExiste() {
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                QuestRulesTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (Goal g : Goal.values()) {
            assertNotNull(msg.getString("objetivo." + g.name().toLowerCase(Locale.ROOT)), g.name());
        }
        for (QuestDef.Kind k : QuestDef.Kind.values()) {
            assertNotNull(msg.getString("tipo." + k.name().toLowerCase(Locale.ROOT)), k.name());
        }
        for (Block b : Block.values()) {
            assertNotNull(msg.getString("quest.bloqueio." + b.name().toLowerCase(Locale.ROOT)), b.name());
        }
    }
}
