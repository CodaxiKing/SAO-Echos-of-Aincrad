package com.aincrad.life;

import com.aincrad.life.fishing.FishingRegistry;
import com.aincrad.life.fishing.FishingRegistry.Fish;
import com.aincrad.life.fishing.FishingRegistry.Point;
import com.aincrad.life.fishing.FishingRegistry.Rarity;
import com.aincrad.life.fishing.FishingRegistry.Zone;
import com.aincrad.life.fishing.TensionFight;
import com.aincrad.life.fishing.TournamentService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras da pesca (tarefas 23.5 a 23.7), sem servidor. */
class FishingRulesTest {

    private static FishingRegistry bundled() {
        List<String> errors = new ArrayList<>();
        FishingRegistry r = FishingRegistry.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
                FishingRulesTest.class.getResourceAsStream("/fishing.yml"), StandardCharsets.UTF_8)), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return r;
    }

    private static TensionFight.Rules rules(int snapTicks) {
        return new TensionFight.Rules(0.35, 0.65, 0.9, 0.1, 0.012, 0.07, 0.02, 60, snapTicks, 40);
    }

    // ------------------------------------------------------------------ 23.5 pontos, zonas e tabela

    @Test
    void oArquivoDoPluginCarregaSemErro() {
        FishingRegistry r = bundled();
        assertEquals(3, r.rods().size());
        assertEquals("vara_simples", r.defaultRod().id());
        Point lake = r.pointOfRegion("lago_andar1");
        assertNotNull(lake);
        assertEquals(1, lake.floor());
        assertNotNull(lake.legendary());
        assertEquals(Rarity.LENDARIO, r.fish(lake.legendary().fish()).rarity());
        assertTrue(r.bait(lake.legendary().bait()).legendary());
        assertNull(r.pointOfRegion("outra_regiao"));
    }

    @Test
    void aForcaEscolheAZonaEAVaraFracaNaoAlcancaOFundo() {
        FishingRegistry r = bundled();
        assertEquals(Zone.RASO, r.zone(0.0));
        assertEquals(Zone.RASO, r.zone(0.4));
        assertEquals(Zone.MEIO, r.zone(0.41));
        assertEquals(Zone.MEIO, r.zone(0.75));
        assertEquals(Zone.FUNDO, r.zone(0.76));
        assertEquals(Zone.FUNDO, r.zone(1.0));
        // Lançar no fundo com vara fraca é recusado.
        assertTrue(r.reaches(r.rod("vara_simples"), Zone.RASO));
        assertFalse(r.reaches(r.rod("vara_simples"), Zone.MEIO));
        assertFalse(r.reaches(r.rod("vara_simples"), Zone.FUNDO));
        assertFalse(r.reaches(r.rod("vara_de_bambu"), Zone.FUNDO));
        assertTrue(r.reaches(r.rod("vara_de_aco"), Zone.FUNDO));
        assertEquals(Zone.RASO, r.deepest(r.rod("vara_simples")));
        assertEquals(Zone.MEIO, r.deepest(r.rod("vara_de_bambu")));
        assertEquals(Zone.FUNDO, r.deepest(r.rod("vara_de_aco")));
        assertFalse(r.reaches(null, Zone.RASO));
    }

    @Test
    void cadaZonaSorteiaDaPropriaTabela() {
        FishingRegistry r = bundled();
        Point lake = r.pointOfRegion("lago_andar1");
        for (Zone z : Zone.values()) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                Fish f = r.roll(lake, z, 0.2, i / 1000.0);
                assertTrue(lake.tables().get(z).containsKey(f.id()), z + ": " + f.id());
                assertFalse(f.rarity() == Rarity.LENDARIO);
                seen.add(f.id());
            }
            // Todos os peixes da tabela aparecem, e nenhum de fora.
            assertEquals(lake.tables().get(z).keySet(), seen);
        }
        // O raso não tem peixe raro; o fundo tem.
        assertFalse(lake.tables().get(Zone.RASO).containsKey("enguia_das_sombras"));
        assertTrue(lake.tables().get(Zone.FUNDO).containsKey("enguia_das_sombras"));
    }

    @Test
    void aSorteAumentaOsRaros() {
        FishingRegistry r = bundled();
        Point lake = r.pointOfRegion("lago_andar1");
        int plain = 0;
        int lucky = 0;
        for (int i = 0; i < 10_000; i++) {
            double roll = i / 10_000.0;
            if (r.roll(lake, Zone.FUNDO, 0, roll).rarity() == Rarity.RARO) {
                plain++;
            }
            if (r.roll(lake, Zone.FUNDO, 1.0, roll).rarity() == Rarity.RARO) {
                lucky++;
            }
        }
        // Sem sorte: 15 de 100 de peso. Com sorte 1.0 os raros triplicam de peso.
        assertEquals(1500, plain, 5);
        assertTrue(lucky > plain * 1.5, plain + " → " + lucky);
    }

    @Test
    void lendarioSoNoFundoComAIscaCertaERankDeMestre() {
        FishingRegistry r = bundled();
        Point lake = r.pointOfRegion("lago_andar1");
        var right = r.bait("isca_do_senhor_do_lago");
        assertTrue(r.legendaryPossible(lake, Zone.FUNDO, right, 750));
        assertFalse(r.legendaryPossible(lake, Zone.FUNDO, right, 749));
        assertFalse(r.legendaryPossible(lake, Zone.MEIO, right, 1000));
        assertFalse(r.legendaryPossible(lake, Zone.FUNDO, r.bait("minhoca"), 1000));
        assertFalse(r.legendaryPossible(lake, Zone.FUNDO, null, 1000));
    }

    @Test
    void tamanhoDentroDaFaixaDoPeixe() {
        Fish f = bundled().fish("tilapia");
        assertEquals(150, FishingRegistry.sizeMm(f, 0));
        assertEquals(400, FishingRegistry.sizeMm(f, 1));
        int mid = FishingRegistry.sizeMm(f, 0.5);
        // A maioria fica perto do mínimo: metade do sorteio dá um quarto da faixa.
        assertEquals(213, mid, 1);
    }

    @Test
    void errosNoArquivoSaoApontados() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                varas:
                  a: {nome: A, rank: 1}
                vara_padrao: nao_existe
                iscas:
                  minhoca: {nome: M, material: WHEAT_SEEDS}
                peixes:
                  bom: {nome: B, raridade: comum, tamanho_cm: [5, 10]}
                  lenda: {nome: L, raridade: lendario, tamanho_cm: [100, 200]}
                  sem_tamanho: {nome: S, raridade: comum}
                  raridade_ruim: {nome: R, raridade: mitico, tamanho_cm: [5, 10]}
                pontos:
                  certo:
                    regiao: lago
                    zonas: {raso: {bom: 1}, meio: {bom: 1}, fundo: {bom: 1}}
                    lendario: {peixe: lenda, isca: minhoca}
                  sem_regiao:
                    zonas: {raso: {bom: 1}, meio: {bom: 1}, fundo: {bom: 1}}
                  falta_zona:
                    regiao: lago2
                    zonas: {raso: {bom: 1}, meio: {bom: 1}}
                  lendario_na_tabela:
                    regiao: lago3
                    zonas: {raso: {lenda: 1}, meio: {bom: 1}, fundo: {bom: 1}}
                  lendario_errado:
                    regiao: lago4
                    zonas: {raso: {bom: 1}, meio: {bom: 1}, fundo: {bom: 1}}
                    lendario: {peixe: bom, isca: minhoca}
                """);
        List<String> errors = new ArrayList<>();
        FishingRegistry r = FishingRegistry.parse(y, errors);
        assertEquals(1, r.points().size());
        assertEquals(2, r.fishes().size());
        // A vara padrão errada cai na primeira vara.
        assertEquals("a", r.defaultRod().id());
        assertEquals(7, errors.size(), errors.toString());
    }

    // ------------------------------------------------------------------ 23.6 tensão da linha

    @Test
    void manterNoVerdeEntregaOPeixe() {
        TensionFight f = new TensionFight(rules(10));
        int ticks = 0;
        while (f.state() == TensionFight.State.LUTANDO && ticks < 1000) {
            // Puxa só quando a tensão cai para a parte de baixo da faixa.
            if (f.tension() < 0.45) {
                f.pull();
            }
            f.tick(false);
            ticks++;
        }
        assertEquals(TensionFight.State.PEGOU, f.state());
        assertEquals(60, ticks);
        assertEquals(0, f.staminaLeft(), 1e-9);
    }

    @Test
    void tensaoNoVermelhoArrebentaALinha() {
        TensionFight f = new TensionFight(rules(10));
        int ticks = 0;
        while (f.state() == TensionFight.State.LUTANDO && ticks < 1000) {
            f.pull();
            f.tick(false);
            ticks++;
        }
        assertEquals(TensionFight.State.ARREBENTOU, f.state());
        // O peixe não cansou: a linha foi embora antes.
        assertTrue(f.staminaLeft() > 0.5);

        // Uma vara mais resistente aguenta mais tempo no vermelho.
        TensionFight strong = new TensionFight(rules(30));
        int strongTicks = 0;
        while (strong.state() == TensionFight.State.LUTANDO && strongTicks < 1000) {
            strong.pull();
            strong.tick(false);
            strongTicks++;
        }
        assertEquals(TensionFight.State.ARREBENTOU, strong.state());
        assertEquals(ticks + 20, strongTicks);
    }

    @Test
    void linhaFrouxaSoltaOPeixe() {
        TensionFight f = new TensionFight(rules(10));
        int ticks = 0;
        while (f.state() == TensionFight.State.LUTANDO && ticks < 1000) {
            f.tick(false);
            ticks++;
        }
        assertEquals(TensionFight.State.ESCAPOU, f.state());
    }

    @Test
    void arrancadaNaoCompensadaFazATensaoDisparar() {
        TensionFight calm = new TensionFight(rules(10));
        TensionFight burst = new TensionFight(rules(10));
        for (int i = 0; i < 10; i++) {
            calm.tick(false);
            burst.tick(true);
        }
        assertTrue(calm.tension() < 0.5);
        assertTrue(burst.tension() > 0.5);
        // Sem puxar nada, só a arrancada leva a linha ao vermelho e ela arrebenta.
        int ticks = 0;
        while (burst.state() == TensionFight.State.LUTANDO && ticks < 1000) {
            burst.tick(true);
            ticks++;
        }
        assertEquals(TensionFight.State.ARREBENTOU, burst.state());
    }

    // ------------------------------------------------------------------ 23.7 torneio

    @Test
    void semanaDoTorneioViraNaSegunda() {
        // 15/11/2026 é domingo; 16/11/2026 é segunda.
        assertEquals("2026-W46", TournamentService.week(LocalDate.of(2026, 11, 15)));
        assertEquals("2026-W47", TournamentService.week(LocalDate.of(2026, 11, 16)));
        assertEquals("2026-W47", TournamentService.week(LocalDate.of(2026, 11, 22)));
        // A virada do ano segue a semana ISO, e a ordem de texto é a ordem do tempo.
        assertEquals("2026-W53", TournamentService.week(LocalDate.of(2027, 1, 1)));
        assertTrue(TournamentService.week(LocalDate.of(2027, 1, 1)).compareTo(TournamentService.week(LocalDate.of(2027, 1, 4))) < 0);
        assertTrue("2026-W09".compareTo("2026-W10") < 0);
    }

    @Test
    void premioEOTotalMenosATaxaEVenceOMaiorPeixe() {
        assertEquals(900, TournamentService.prize(1000, 10));
        assertEquals(1000, TournamentService.prize(1000, 0));
        assertEquals(0, TournamentService.prize(1000, 100));
        assertEquals(179, TournamentService.prize(199, 10));

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        var small = new TournamentService.Entry(a, "A", 200, 320, "tilapia", 10);
        var big = new TournamentService.Entry(b, "B", 200, 815, "enguia_das_sombras", 30);
        var bigLater = new TournamentService.Entry(c, "C", 200, 815, "enguia_das_sombras", 50);
        var nothing = new TournamentService.Entry(c, "C", 200, 0, "", 0);
        assertEquals(big, TournamentService.winner(List.of(small, big, nothing)));
        // Empate: vence quem pescou primeiro.
        assertEquals(big, TournamentService.winner(List.of(bigLater, small, big)));
        // Ninguém pescou: sem vencedor (as inscrições são devolvidas).
        assertNull(TournamentService.winner(List.of(nothing)));
        assertNull(TournamentService.winner(List.of()));
    }
}
