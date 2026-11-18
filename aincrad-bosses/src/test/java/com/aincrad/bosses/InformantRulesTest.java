package com.aincrad.bosses;

import com.aincrad.bosses.model.InformantRules;
import com.aincrad.bosses.model.InformantRules.View;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** As regras dos informantes (etapa 42), sem servidor. */
class InformantRulesTest {

    @Test
    void semComprarAPaginaApareceFechada() {
        // O servidor viu a fase, o jogador não tem a página: fica à venda.
        assertEquals(View.A_VENDA, InformantRules.view(true, true, false));
        // Comprou (ou estava na luta): aberta.
        assertEquals(View.ABERTA, InformantRules.view(true, true, true));
        // Ninguém chegou lá: nem o informante vende, mesmo que o jogador tenha a página de algum jeito.
        assertEquals(View.DESCONHECIDA, InformantRules.view(true, false, false));
        assertEquals(View.DESCONHECIDA, InformantRules.view(true, false, true));
        // Informantes desligados: o guia é aberto a todos, como antes.
        assertEquals(View.ABERTA, InformantRules.view(false, true, false));
        assertEquals(View.DESCONHECIDA, InformantRules.view(false, false, false));
    }

    @Test
    void oPrecoCresceComOAndarEComAFase() {
        // Illfang, andar 1: 200, 300, 400.
        assertEquals(200, InformantRules.price(200, 50, 100, 1, 0));
        assertEquals(300, InformantRules.price(200, 50, 100, 1, 1));
        assertEquals(400, InformantRules.price(200, 50, 100, 1, 2));
        // Andar 10, primeira fase: 200 + 9 x 50.
        assertEquals(650, InformantRules.price(200, 50, 100, 10, 0));
        assertEquals(0, InformantRules.price(0, 0, 0, 10, 3));
        // A cópia para outro jogador custa uma fração do preço.
        assertEquals(100, InformantRules.copyFee(400, 0.25));
        assertEquals(0, InformantRules.copyFee(400, 0));
        assertEquals(400, InformantRules.copyFee(400, 5));
    }

    @Test
    void soOLiderConvocaAReuniaoNaCidadeAntesDaRaid() {
        long now = 1_000_000;
        var M = com.aincrad.bosses.raid.MeetingService.Block.class;
        assertEquals(null, com.aincrad.bosses.raid.MeetingService.blocked(true, true, false, now + 1, now));
        assertEquals(Enum.valueOf(M, "NAO_E_LIDER"), com.aincrad.bosses.raid.MeetingService.blocked(false, true, false, now + 1, now));
        assertEquals(Enum.valueOf(M, "FORA_DA_CIDADE"), com.aincrad.bosses.raid.MeetingService.blocked(true, false, false, now + 1, now));
        assertEquals(Enum.valueOf(M, "JA_TEM"), com.aincrad.bosses.raid.MeetingService.blocked(true, true, true, now + 1, now));
        assertEquals(Enum.valueOf(M, "RAID_PASSOU"), com.aincrad.bosses.raid.MeetingService.blocked(true, true, false, now, now));
    }

    @Test
    void abrirOBauFechaAPortaEChamaLevasAteOGrupoVencerOuOTempoAcabar() throws Exception {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        y.loadFromString("""
                masmorras:
                  cripta: { regiao: Cripta_27, bau: "world,1,2,3", levas: 3, mobs_por_leva: 6, intervalo_segundos: 15, tempo_limite_segundos: 180, recarga_minutos: 60, mobs: [zombie] }
                  sem_regiao: { levas: 2 }
                """);
        var errors = new java.util.ArrayList<String>();
        var defs = com.aincrad.bosses.model.DungeonRules.parse(y.getConfigurationSection("masmorras"), errors);
        assertEquals(1, defs.size());
        assertEquals(1, errors.size());
        var d = defs.get("cripta");
        assertEquals("cripta_27", d.region());
        assertEquals(java.util.List.of("ZOMBIE"), d.mobs());
        assertEquals(true, d.antiCrystal());
        var R = com.aincrad.bosses.model.DungeonRules.class;
        // A armadilha arma de novo só depois da recarga.
        assertEquals(true, com.aincrad.bosses.model.DungeonRules.armed(false, 0, d.cooldownMs(), d.cooldownMs()));
        assertEquals(false, com.aincrad.bosses.model.DungeonRules.armed(false, 1000, d.cooldownMs(), d.cooldownMs()));
        assertEquals(false, com.aincrad.bosses.model.DungeonRules.armed(true, 0, d.cooldownMs(), d.cooldownMs()));
        // A primeira leva sai na hora; as outras, de 15 em 15 segundos; nunca mais que três.
        assertEquals(1, com.aincrad.bosses.model.DungeonRules.wavesDue(0, 0, d.waveMs(), d.waves()));
        assertEquals(2, com.aincrad.bosses.model.DungeonRules.wavesDue(0, 15_000, d.waveMs(), d.waves()));
        assertEquals(3, com.aincrad.bosses.model.DungeonRules.wavesDue(0, 999_000, d.waveMs(), d.waves()));
        var E = com.aincrad.bosses.model.DungeonRules.End.class;
        // Ainda há mobs, ou ainda faltam levas: continua.
        assertEquals(null, com.aincrad.bosses.model.DungeonRules.ended(3, 3, 2, 4, 0, 60_000, d.limitMs()));
        assertEquals(null, com.aincrad.bosses.model.DungeonRules.ended(2, 3, 0, 4, 0, 60_000, d.limitMs()));
        assertEquals(Enum.valueOf(E, "VENCERAM"), com.aincrad.bosses.model.DungeonRules.ended(3, 3, 0, 4, 0, 60_000, d.limitMs()));
        assertEquals(Enum.valueOf(E, "TEMPO"), com.aincrad.bosses.model.DungeonRules.ended(3, 3, 2, 4, 0, 180_000, d.limitMs()));
        assertEquals(Enum.valueOf(E, "TODOS_MORRERAM"), com.aincrad.bosses.model.DungeonRules.ended(1, 3, 6, 0, 0, 5_000, d.limitMs()));
        // Sem chave, a porta de uma dungeon trancada não abre; com a chave, só enquanto ela vale.
        assertEquals(true, com.aincrad.bosses.model.DungeonRules.mayEnter(false, 0, 1000));
        assertEquals(false, com.aincrad.bosses.model.DungeonRules.mayEnter(true, 0, 1000));
        assertEquals(true, com.aincrad.bosses.model.DungeonRules.mayEnter(true, 2000, 1000));
        assertEquals(false, com.aincrad.bosses.model.DungeonRules.mayEnter(true, 1000, 1000));
        assertEquals(R, d.getClass().getEnclosingClass());
    }

    @Test
    void aFaseDigitadaComecaEm1() {
        assertEquals(0, InformantRules.phaseIndex("1", 3));
        assertEquals(2, InformantRules.phaseIndex(" 3 ", 3));
        assertEquals(-1, InformantRules.phaseIndex("0", 3));
        assertEquals(-1, InformantRules.phaseIndex("4", 3));
        assertEquals(-1, InformantRules.phaseIndex("dois", 3));
        assertEquals(-1, InformantRules.phaseIndex(null, 3));
        assertEquals("illfang:2", InformantRules.key("illfang", 2));
    }
}
