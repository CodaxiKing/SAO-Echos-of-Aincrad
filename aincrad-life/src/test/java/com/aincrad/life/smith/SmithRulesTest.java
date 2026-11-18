package com.aincrad.life.smith;

import com.aincrad.life.model.Profession;
import com.aincrad.life.model.TreeDef;
import com.aincrad.life.smith.ForgeRegistry.Blueprint;
import com.aincrad.life.smith.ForgeRegistry.Mat;
import com.aincrad.life.smith.ForgeRegistry.Rarity;
import com.aincrad.life.smith.ForgeRegistry.Role;
import com.aincrad.life.station.StationPanel.Hit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do ferreiro (tarefas 23.8 a 23.12), sem servidor. */
class SmithRulesTest {

    private static final double[] THRESHOLDS = {40, 60, 75, 90};

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                SmithRulesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    private static ForgeRegistry bundled() {
        List<String> errors = new ArrayList<>();
        ForgeRegistry r = ForgeRegistry.parse(resource("ingots.yml"), resource("forge.yml"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return r;
    }

    /** Os materiais na ordem dos encaixes do projeto (null = vazio). */
    private static List<Mat> mats(ForgeRegistry r, String... ids) {
        List<Mat> out = new ArrayList<>();
        for (String id : ids) {
            out.add(id == null ? null : r.mat(id));
        }
        return out;
    }

    // ------------------------------------------------------------------ 23.9 projetos e encaixes

    @Test
    void osArquivosDoPluginCarregamSemErro() {
        ForgeRegistry r = bundled();
        assertTrue(r.mats().size() >= 15);
        assertEquals(8, r.types().size());
        assertTrue(r.blueprints().size() >= 9);
        // Encaixes e batidas base do plano.
        assertEquals(List.of(Role.LAMINA, Role.CABO, Role.CATALISADOR), r.types().get("adaga").slots());
        assertEquals(10, r.types().get("adaga").hits());
        assertEquals(6, r.types().get("espada_duas_maos").slots().size());
        assertEquals(35, r.types().get("espada_duas_maos").hits());
        assertTrue(r.types().get("armadura").armor());
        assertFalse(r.types().get("machado").armor());
        // Os caminhos e especializações citados existem na árvore do ferreiro.
        YamlConfiguration tree = resource("trees/ferreiro.yml");
        assertNotNull(TreeDef.parse(Profession.FERREIRO, tree, new ArrayList<>()));
        for (Blueprint b : r.blueprints().values()) {
            if (b.path() != null) {
                assertTrue(tree.isConfigurationSection("caminhos." + b.path()), b.id());
                if (b.spec() != null) {
                    assertTrue(tree.isSet("caminhos." + b.path() + ".especializacoes." + b.spec()), b.id());
                }
            }
        }
    }

    @Test
    void cadaEncaixeAceitaSoAClasseCerta() {
        ForgeRegistry r = bundled();
        assertTrue(Role.LAMINA.accepts(r.mat("ferro")));
        assertFalse(Role.LAMINA.accepts(r.mat("diamante")));
        assertTrue(Role.NUCLEO.accepts(r.mat("diamante")));
        assertTrue(Role.NUCLEO.accepts(r.mat("ferro")));
        assertTrue(Role.CABO.accepts(r.mat("couro")));
        assertTrue(Role.CABO.accepts(r.mat("madeira")));
        assertFalse(Role.CABO.accepts(r.mat("ferro")));
        assertFalse(Role.HASTE.accepts(r.mat("couro")));
        assertTrue(Role.CATALISADOR.accepts(r.mat("po_de_blaze")));
        assertFalse(Role.CATALISADOR.accepts(null));
        assertTrue(Role.CATALISADOR.optional());
        assertFalse(Role.NUCLEO.optional());
        // DIAMOND comum é o cristal; a cristalita é item próprio.
        assertEquals("diamante", r.common("diamond").id());
        assertTrue(r.mat("cristalita").custom());
    }

    @Test
    void oMesmoProjetoComNucleosDiferentesDaAtributosDiferentes() {
        ForgeRegistry r = bundled();
        Blueprint bp = r.blueprint("espada_curta");
        // lâmina, núcleo, guarda, cabo, catalisador
        List<Mat> sharp = mats(r, "ferro", "diamante", "ferro", "couro", null);
        List<Mat> fast = mats(r, "ferro", "quartzo", "ferro", "couro", null);
        assertEquals("afiacao", ForgeMath.dominant(bp.type(), sharp));
        assertEquals("rapidez", ForgeMath.dominant(bp.type(), fast));
        Map<String, Double> a = ForgeMath.stats(bp.type(), sharp, r.dominant(), 50, 0, null);
        Map<String, Double> b = ForgeMath.stats(bp.type(), fast, r.dominant(), 50, 0, null);
        // Qualidade 50 = 100% do valor. Ferro na lâmina: 2 de dano; diamante (rank 3) no núcleo: +2,4.
        assertEquals(4.4, a.get("dano"), 1e-9);
        assertEquals(2.0, b.get("dano"), 1e-9);
        // Couro no cabo: 3% de velocidade; quartzo (rank 2) no núcleo: +6%.
        assertEquals(3.0, a.get("velocidade_ataque"), 1e-9);
        assertEquals(9.0, b.get("velocidade_ataque"), 1e-9);
        // Guarda de ferro: durabilidade.
        assertEquals(10.0, a.get("durabilidade"), 1e-9);
        // A qualidade escala tudo: de 60% a 140%.
        assertEquals(1.2, ForgeMath.stats(bp.type(), fast, r.dominant(), 0, 0, null).get("dano"), 1e-9);
        assertEquals(2.8, ForgeMath.stats(bp.type(), fast, r.dominant(), 100, 0, null).get("dano"), 1e-9);
    }

    @Test
    void armaduraUsaOBonusDePlaca() {
        ForgeRegistry r = bundled();
        Blueprint bp = r.blueprint("peitoral_de_placas");
        // placa, placa, forro, rebite, catalisador
        Map<String, Double> s = ForgeMath.stats(bp.type(), mats(r, "ferro", "ferro", "la", "ferro", null), r.dominant(), 50, 0, null);
        assertEquals(3.0, s.get("armadura"), 1e-9);
        assertEquals(1.0, s.get("vida"), 1e-9);
        assertNull(s.get("dano"));
    }

    @Test
    void aCombinacaoSecretaDaAArmaNomeada() {
        ForgeRegistry r = bundled();
        Blueprint bp = r.blueprint("espada_de_aco");
        var named = r.named(bp, mats(r, "aco_negro", "ametista", "ferro", "couro", "presa_do_kobold"));
        assertNotNull(named);
        assertEquals("Lâmina do Crepúsculo", named.name());
        // A guarda e o cabo não fazem parte da combinação; trocar o núcleo ou tirar o catalisador desfaz.
        assertNotNull(r.named(bp, mats(r, "aco_negro", "ametista", "ouro", "linha", "presa_do_kobold")));
        assertNull(r.named(bp, mats(r, "aco_negro", "diamante", "ferro", "couro", "presa_do_kobold")));
        assertNull(r.named(bp, mats(r, "aco_negro", "ametista", "ferro", "couro", null)));
        // Outro projeto com os mesmos materiais não é a arma nomeada.
        assertNull(r.named(r.blueprint("espada_curta"), mats(r, "aco_negro", "ametista", "ferro", "couro", "presa_do_kobold")));
        // O bônus da arma nomeada entra na conta.
        Map<String, Double> s = ForgeMath.stats(bp.type(), mats(r, "aco_negro", "ametista", "ferro", "couro", "presa_do_kobold"),
                r.dominant(), 50, 0, named.bonus());
        assertEquals(6.0, s.get("dano"), 1e-9);
        // Ametista (rank 2) no núcleo: 4; catalisador: 5; arma nomeada: 4.
        assertEquals(13.0, s.get("critico"), 1e-9);
    }

    // ------------------------------------------------------------------ 23.10 calor e batidas

    @Test
    void batidasNecessariasPeloTipoEPeloRankDoLingote() {
        ForgeRegistry r = bundled();
        Blueprint sword = r.blueprint("espada_curta");
        assertEquals(1, ForgeMath.rank(sword.type(), mats(r, "ferro", "diamante", "ferro", "couro", null)));
        assertEquals(4, ForgeMath.rank(sword.type(), mats(r, "aco_negro", "ferro", "ferro", "couro", null)));
        assertEquals(20, ForgeMath.hits(sword.type(), 1, 0));
        assertEquals(25, ForgeMath.hits(sword.type(), 2, 0));
        assertEquals(35, ForgeMath.hits(sword.type(), 4, 0));
        // Nó da árvore: 10% menos batidas.
        assertEquals(18, ForgeMath.hits(sword.type(), 1, -0.1));
        // O anel fecha mais rápido com rank maior, até o mínimo.
        assertEquals(26, ForgeMath.ringTicks(26, 1, 3, 10));
        assertEquals(17, ForgeMath.ringTicks(26, 4, 3, 10));
        assertEquals(10, ForgeMath.ringTicks(26, 9, 3, 10));
    }

    @Test
    void tresBatidasRuinsQuebramAPeca() {
        ForgeMath.Work w = new ForgeMath.Work(30, 3);
        assertEquals(50, w.quality(), 1e-9);
        w.strike(Hit.PERFEITA, true);
        w.strike(Hit.RUIM, true);
        w.strike(Hit.BOA, true);
        w.strike(Hit.RUIM, true);
        assertFalse(w.broken());
        assertEquals(2, w.cracks());
        w.strike(Hit.RUIM, true);
        assertTrue(w.broken());
        assertTrue(w.finished());
        assertEquals(5, w.done());
    }

    @Test
    void batidaComCalorErradoContaComoRuim() {
        ForgeMath.Work w = new ForgeMath.Work(10, 3);
        assertEquals(Hit.RUIM, w.strike(Hit.PERFEITA, false));
        assertEquals(1, w.cracks());
        assertEquals(45, w.quality(), 1e-9);
        assertEquals(Hit.PERFEITA, w.strike(Hit.PERFEITA, true));
        assertEquals(50, w.quality(), 1e-9);
    }

    @Test
    void qualidadeSobeComAsBatidasPerfeitas() {
        ForgeMath.Work perfect = new ForgeMath.Work(30, 3);
        ForgeMath.Work good = new ForgeMath.Work(30, 3);
        for (int i = 0; i < 30; i++) {
            perfect.strike(Hit.PERFEITA, true);
            good.strike(Hit.BOA, true);
        }
        assertTrue(perfect.finished());
        assertFalse(perfect.broken());
        assertEquals(100, perfect.quality(), 1e-6);
        assertEquals(66.7, good.quality(), 0.1);
        // Têmpera atrasada: perde 10 de qualidade.
        perfect.lose(10);
        assertEquals(90, perfect.quality(), 1e-6);
    }

    // ------------------------------------------------------------------ 23.11 resultado

    @Test
    void aRaridadeNuncaPassaDoTetoDoLingote() {
        assertEquals(Rarity.COMUM, ForgeMath.rarity(39, THRESHOLDS, Rarity.LENDARIO));
        assertEquals(Rarity.INCOMUM, ForgeMath.rarity(40, THRESHOLDS, Rarity.LENDARIO));
        assertEquals(Rarity.RARO, ForgeMath.rarity(60, THRESHOLDS, Rarity.LENDARIO));
        assertEquals(Rarity.EPICO, ForgeMath.rarity(89, THRESHOLDS, Rarity.LENDARIO));
        assertEquals(Rarity.LENDARIO, ForgeMath.rarity(100, THRESHOLDS, Rarity.LENDARIO));
        // Qualidade perfeita com lingote de teto baixo: fica no teto.
        assertEquals(Rarity.INCOMUM, ForgeMath.rarity(100, THRESHOLDS, Rarity.INCOMUM));
        assertEquals(Rarity.COMUM, ForgeMath.rarity(100, THRESHOLDS, Rarity.COMUM));

        ForgeRegistry r = bundled();
        Blueprint two = r.blueprint("montante");
        // Duas lâminas: vale o menor teto (ferro: incomum) e o maior rank (aço negro: 4).
        List<Mat> placed = mats(r, "aco_negro", "ferro", "diamante", "ferro", "couro", null);
        assertEquals(Rarity.INCOMUM, ForgeMath.cap(two.type(), placed));
        assertEquals(4, ForgeMath.rank(two.type(), placed));
        assertEquals(Rarity.EPICO, ForgeMath.cap(two.type(), mats(r, "aco_negro", "aco_negro", "diamante", "ferro", "couro", null)));
    }

    @Test
    void qualidadeAcimaDe90DaDuasTentativasExtras() {
        assertEquals(8, ForgeMath.tries(8, 89.9, 90, 2, 0));
        assertEquals(10, ForgeMath.tries(8, 90, 90, 2, 0));
        assertEquals(11, ForgeMath.tries(8, 95, 90, 2, 1));
        assertEquals(9, ForgeMath.tries(8, 50, 90, 2, 1));
    }

    // ------------------------------------------------------------------ 23.8 reparo

    @Test
    void reparoCobraPeloDesgasteEAProficienciaDaDesconto() {
        // Item todo gasto, aprendiz: preço cheio.
        assertEquals(new ForgeMath.RepairCost(300, 4), ForgeMath.repair(1.0, 300, 4, 0, 0.5, 0, 0));
        // Metade do desgaste: metade do preço.
        assertEquals(new ForgeMath.RepairCost(150, 2), ForgeMath.repair(0.5, 300, 4, 0, 0.5, 0, 0));
        // Proficiência 1000: 50% de desconto no Col.
        assertEquals(new ForgeMath.RepairCost(150, 4), ForgeMath.repair(1.0, 300, 4, 1000, 0.5, 0, 0));
        assertEquals(new ForgeMath.RepairCost(225, 4), ForgeMath.repair(1.0, 300, 4, 500, 0.5, 0, 0));
        // Nós da árvore: 15% a mais de desconto e 20% menos material.
        assertEquals(new ForgeMath.RepairCost(105, 4), ForgeMath.repair(1.0, 300, 4, 1000, 0.5, 0.15, -0.2));
        assertEquals(3, ForgeMath.repair(0.9, 300, 4, 0, 0.5, 0, -0.2).units());
        // Um arranhão ainda custa uma unidade; item inteiro não custa nada.
        assertEquals(new ForgeMath.RepairCost(3, 1), ForgeMath.repair(0.01, 300, 4, 0, 0.5, 0, 0));
        assertEquals(new ForgeMath.RepairCost(0, 0), ForgeMath.repair(0, 300, 4, 0, 0.5, 0, 0));
    }

    // ------------------------------------------------------------------ 23.12 aprimoramento

    @Test
    void chanceDoAprimoramento() {
        // base 50% + 30% no mestre − 6% por nível + 10% por material + 2% por batida perfeita
        assertEquals(0.5, ForgeMath.upgradeChance(0.5, 0.3, 0, 0, 0.06, 0, 0.1, 0, 0.02, 0, 0.95), 1e-9);
        assertEquals(0.65, ForgeMath.upgradeChance(0.5, 0.3, 500, 0, 0.06, 0, 0.1, 0, 0.02, 0, 0.95), 1e-9);
        assertEquals(0.41, ForgeMath.upgradeChance(0.5, 0.3, 500, 4, 0.06, 0, 0.1, 0, 0.02, 0, 0.95), 1e-9);
        assertEquals(0.71, ForgeMath.upgradeChance(0.5, 0.3, 500, 4, 0.06, 3, 0.1, 0, 0.02, 0, 0.95), 1e-9);
        // Cinco batidas perfeitas: +10%.
        assertEquals(0.81, ForgeMath.upgradeChance(0.5, 0.3, 500, 4, 0.06, 3, 0.1, 5, 0.02, 0, 0.95), 1e-9);
        // Teto de 95% e piso de 5%.
        assertEquals(0.95, ForgeMath.upgradeChance(0.5, 0.3, 1000, 0, 0.06, 3, 0.1, 5, 0.02, 0.05, 0.95), 1e-9);
        assertEquals(0.05, ForgeMath.upgradeChance(0.1, 0, 0, 10, 0.06, 0, 0.1, 0, 0.02, 0, 0.95), 1e-9);
    }

    @Test
    void sorteioFixoDoAprimoramento() {
        // Dentro da chance: sucesso.
        assertEquals(ForgeMath.Outcome.SUCESSO, ForgeMath.upgrade(0.6, 0.59, 0.9, 3, false));
        // Fora da chance: um dos três tipos de falha.
        assertEquals(ForgeMath.Outcome.SO_MATERIAIS, ForgeMath.upgrade(0.6, 0.6, 0.1, 3, false));
        assertEquals(ForgeMath.Outcome.CAIU, ForgeMath.upgrade(0.6, 0.6, 0.5, 3, false));
        assertEquals(ForgeMath.Outcome.DESVIOU, ForgeMath.upgrade(0.6, 0.6, 0.9, 3, false));
        // Atributo em +0 não tem como cair; o nó "falha sem queda" também protege.
        assertEquals(ForgeMath.Outcome.SO_MATERIAIS, ForgeMath.upgrade(0.6, 0.6, 0.5, 0, false));
        assertEquals(ForgeMath.Outcome.SO_MATERIAIS, ForgeMath.upgrade(0.6, 0.6, 0.5, 3, true));
    }

    @Test
    void dadosDoItemVaoEVoltamDoTexto() {
        Map<String, Double> stats = new LinkedHashMap<>();
        stats.put("dano", 4.4);
        stats.put("critico", 6.0);
        assertEquals(stats, ForgeMath.decode(ForgeMath.encode(stats)));
        assertTrue(ForgeMath.decode(null).isEmpty());
        assertTrue(ForgeMath.decode("").isEmpty());
        // Pedaço estragado é ignorado, o resto vale.
        assertEquals(Map.of("dano", 2.0), ForgeMath.decode("lixo;dano=2;peso=muito"));
        // Cada atributo do aprimoramento mexe num bônus que a forja conhece.
        for (ForgedItems.Upgrade u : ForgedItems.Upgrade.values()) {
            assertTrue(ForgeRegistry.STATS.contains(u.stat()), u.key());
        }
        assertEquals(5, ForgedItems.Upgrade.values().length);
    }

    @Test
    void errosNosArquivosSaoApontados() throws Exception {
        YamlConfiguration ingots = new YamlConfiguration();
        ingots.loadFromString("""
                materiais:
                  ferro: {nome: F, classe: lingote, material: IRON_INGOT, teto: raro, arma: {dano: 2}}
                  couro: {nome: C, classe: couro, material: LEATHER}
                  repetido: {nome: R, classe: lingote, material: IRON_INGOT}
                  classe_ruim: {nome: X, classe: pedra, material: STONE}
                  bonus_ruim: {nome: B, classe: lingote, material: GOLD_INGOT, arma: {magia: 3}}
                """);
        YamlConfiguration forge = new YamlConfiguration();
        forge.loadFromString("""
                tipos:
                  adaga: {nome: Adaga, encaixes: [lamina, cabo, catalisador], batidas: 10}
                  sem_base: {nome: S, encaixes: [cabo]}
                  encaixe_ruim: {nome: E, encaixes: [lamina, pomo]}
                projetos:
                  bom: {nome: Bom, tipo: adaga, material: IRON_SWORD}
                  tipo_ruim: {nome: T, tipo: foice, material: IRON_HOE}
                nomeadas:
                  certa: {nome: Certa, projeto: bom, materiais: {lamina: ferro}}
                  projeto_ruim: {nome: P, projeto: nada, materiais: {lamina: ferro}}
                  encaixe_fora: {nome: N, projeto: bom, materiais: {nucleo: ferro}}
                  classe_errada: {nome: K, projeto: bom, materiais: {lamina: couro}}
                """);
        List<String> errors = new ArrayList<>();
        ForgeRegistry r = ForgeRegistry.parse(ingots, forge, errors);
        // bonus_ruim entra sem o bônus desconhecido.
        assertEquals(List.of("ferro", "couro", "bonus_ruim"), new ArrayList<>(r.mats().keySet()));
        assertEquals(1, r.types().size());
        assertEquals(1, r.blueprints().size());
        assertEquals(1, r.named().size());
        assertEquals(9, errors.size(), errors.toString());
        assertNotNull(r.named(r.blueprint("bom"), Arrays.asList(r.mat("ferro"), r.mat("couro"), null)));
    }
}
