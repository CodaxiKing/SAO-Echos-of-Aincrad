package com.aincrad.bosses;

import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.GuideData;
import com.aincrad.bosses.model.Raid;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras das etapas 18 e 19: guia de estratégia e quadro de raid. */
class ExtrasTest {

    @Test
    void guiaSoMostraOQueJaFoiVisto() {
        GuideData g = new GuideData();
        assertFalse(g.phaseSeen(0));
        g.attempt();
        assertTrue(g.seePhase(0));
        assertFalse(g.seePhase(0)); // já vista: não precisa gravar de novo
        assertTrue(g.seeSkill(0, BossDef.SkillType.AREA));
        assertTrue(g.seePhase(2));
        assertEquals(1, g.attempts());
        assertEquals(2, g.bestPhase());
        assertTrue(g.phaseSeen(2));
        assertFalse(g.phaseSeen(1));
        assertTrue(g.skillSeen(0, BossDef.SkillType.AREA));
        assertFalse(g.skillSeen(2, BossDef.SkillType.AREA));
        assertFalse(g.skillSeen(0, BossDef.SkillType.FURIA));

        // O que vai para o banco volta igual.
        GuideData back = new GuideData(g.attempts(), g.bestPhase(), g.seenText());
        assertEquals(g.seenText(), back.seenText());
        assertTrue(back.skillSeen(0, BossDef.SkillType.AREA));
        assertEquals("", new GuideData(0, 0, null).seenText());
    }

    @Test
    void vagasDaRaidPorFuncao() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Raid r = new Raid(1, "illfang", a, "Kirito", 0, 1, 2, 0);
        assertTrue(r.join(a, "Kirito", Raid.Role.TANQUE));
        assertFalse(r.join(b, "Asuna", Raid.Role.TANQUE)); // só 1 tanque
        assertFalse(r.join(b, "Asuna", Raid.Role.SUPORTE)); // 0 vagas de suporte
        assertTrue(r.join(b, "Asuna", Raid.Role.DANO));
        assertTrue(r.join(c, "Klein", Raid.Role.DANO));
        assertEquals(2, r.taken(Raid.Role.DANO));
        // Trocar de função libera a vaga antiga; ficar na mesma não conta duas vezes.
        assertTrue(r.join(a, "Kirito", Raid.Role.TANQUE));
        assertFalse(r.join(a, "Kirito", Raid.Role.DANO));
        assertTrue(r.leave(c));
        assertTrue(r.join(a, "Kirito", Raid.Role.DANO));
        assertEquals(0, r.taken(Raid.Role.TANQUE));
        assertFalse(r.leave(c));
        assertEquals(Raid.Role.SUPORTE, Raid.Role.parse(" suporte "));
        assertNull(Raid.Role.parse("mago"));
    }

    @Test
    void salaReservadaDoAvisoAteOFim() {
        long hour = 3_600_000L;
        Raid r = new Raid(1, "illfang", UUID.randomUUID(), "Kirito", 10 * hour, 1, 1, 1);
        assertFalse(r.reserved(10 * hour - 16 * 60_000L, 15, 60));
        assertTrue(r.reserved(10 * hour - 15 * 60_000L, 15, 60));
        assertTrue(r.reserved(10 * hour + 59 * 60_000L, 15, 60));
        assertFalse(r.reserved(11 * hour, 15, 60));
        assertFalse(r.over(11 * hour - 1, 60));
        assertTrue(r.over(11 * hour, 60));
    }

    @Test
    void dataDaRaid() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 15, 0);
        assertEquals(LocalDateTime.of(2026, 12, 25, 20, 30), Raid.parseWhen("25/12", "20:30", now));
        // Sem ano e já passou: é no ano que vem.
        assertEquals(LocalDateTime.of(2027, 1, 5, 9, 0), Raid.parseWhen("05/01", "9", now));
        assertEquals(LocalDateTime.of(2028, 2, 29, 18, 0), Raid.parseWhen("29/02/2028", "18:00", now));
        // Só a hora: hoje, ou amanhã se já passou.
        assertEquals(LocalDateTime.of(2026, 11, 17, 21, 0), Raid.parseWhen("", "21:00", now));
        assertEquals(LocalDateTime.of(2026, 11, 18, 8, 0), Raid.parseWhen(null, "08:00", now));
        assertNull(Raid.parseWhen("31/02", "20:00", now));
        assertNull(Raid.parseWhen("amanha", "20:00", now));
        assertNull(Raid.parseWhen("25/12", "25:00", now));
    }
}
