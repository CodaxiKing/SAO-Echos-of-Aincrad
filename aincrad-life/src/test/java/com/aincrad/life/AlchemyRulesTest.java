package com.aincrad.life;

import com.aincrad.life.alchemy.AlchemyMath;
import com.aincrad.life.alchemy.AlchemyMath.Purity;
import com.aincrad.life.alchemy.AlchemyRegistry;
import com.aincrad.life.alchemy.AlchemyRegistry.Formula;
import com.aincrad.life.alchemy.AlchemyRegistry.Kind;
import com.aincrad.life.alchemy.AlchemyRegistry.Slot;
import com.aincrad.life.cooking.FireStep;
import com.aincrad.life.cooking.RecipeRegistry;
import com.aincrad.life.fishing.FishingRegistry;
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

/** As regras da alquimia (tarefas 23.13 a 23.16), sem servidor. */
class AlchemyRulesTest {

    /** Os números padrão de config.yml → alquimia.pureza. */
    private static final AlchemyMath.Rules RULES = new AlchemyMath.Rules(35, 60, 8, 15, 20, 5, 2, -5, 3, 10, 40, 70, 90);

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                AlchemyRulesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    private static AlchemyRegistry bundled() {
        List<String> errors = new ArrayList<>();
        AlchemyRegistry r = AlchemyRegistry.parse(resource("reagents.yml"), resource("formulas.yml"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return r;
    }

    // ------------------------------------------------------------------ 23.13 fórmulas e reagentes

    @Test
    void osArquivosDoPluginCarregamSemErro() {
        AlchemyRegistry r = bundled();
        assertTrue(r.reagents().size() >= 20);
        assertEquals(15, r.formulas().size());
        assertEquals(3, r.gathers().size());
        // Os caminhos e especializações citados existem na árvore da alquimia.
        YamlConfiguration tree = resource("trees/alquimia.yml");
        for (Formula f : r.formulas().values()) {
            if (f.path() != null) {
                assertTrue(tree.isConfigurationSection("caminhos." + f.path()), f.id());
                if (f.spec() != null) {
                    assertTrue(tree.isSet("caminhos." + f.path() + ".especializacoes." + f.spec()), f.id());
                }
            }
            assertTrue(f.produce() >= f.study());
            assertEquals(2, f.infusion().size());
        }
        // Os cristais são do rank Mestre.
        assertEquals(750, r.formula("cristal_de_cura").produce());
        assertEquals(Kind.CRISTAL, r.formula("cristal_de_cura").product().kind());
    }

    @Test
    void aCombinacaoDosEncaixesAchaAFormula() {
        AlchemyRegistry r = bundled();
        assertEquals("pocao_de_cura_menor", r.match("agua", "erva_curativa", "flor_azul").id());
        // Trocar a base muda o produto: o mesmo pó de blaze dá óleo (com óleo) ou poção (com álcool).
        assertEquals(Kind.OLEO, r.match("oleo", "po_de_blaze", "carvao").product().kind());
        assertEquals(Kind.BUFF, r.match("alcool", "po_de_blaze", "glowstone").product().kind());
        assertNull(r.match("agua", "po_de_blaze", "carvao"));
        assertNull(r.match("agua", "erva_curativa", null));
    }

    @Test
    void reagenteComumPeloMaterialEProprioSoPelaMarca() {
        AlchemyRegistry r = bundled();
        assertEquals("frasco_de_vidro", r.common("GLASS_BOTTLE").id());
        assertTrue(r.reagent("frasco_de_cristal").custom());
        assertEquals(Slot.FRASCO, r.reagent("frasco_de_cristal").slot());
        // O frasco define as doses e o teto de pureza.
        assertEquals(1, r.reagent("frasco_de_vidro").doses());
        assertEquals(Purity.PURA, r.reagent("frasco_de_vidro").cap());
        assertEquals(3, r.reagent("frasco_reforcado").doses());
        assertEquals(Purity.PERFEITA, r.reagent("frasco_reforcado").cap());
        assertNull(r.common("FERN"));
        assertFalse(Slot.CATALISADOR.required());
    }

    @Test
    void pontoDeColetaTemTempoDeRenascimentoEReagenteRaro() {
        AlchemyRegistry r = bundled();
        var herbs = r.gather("canteiro_de_ervas");
        assertEquals("erva_curativa", herbs.reagent());
        assertEquals(120, herbs.respawnSeconds());
        assertEquals("erva_lunar", herbs.rare());
        assertEquals(250, herbs.rareProficiency());
        assertNull(r.gather("cogumelos").rare());
    }

    @Test
    void oCatalisadorEntraPorUltimoNaInfusao() {
        AlchemyRegistry r = bundled();
        Formula f = r.formula("antidoto");
        // O antídoto começa pelo secundário.
        assertEquals(Slot.SECUNDARIO, f.infusion().get(0).reagent());
        assertEquals("amarelo", f.infusion().get(0).color());
        assertEquals(2, AlchemyRegistry.steps(f, false).size());
        var withCatalyst = AlchemyRegistry.steps(f, true);
        assertEquals(3, withCatalyst.size());
        assertEquals(Slot.CATALISADOR, withCatalyst.get(2).reagent());
        assertEquals("roxo", withCatalyst.get(2).color());
    }

    // ------------------------------------------------------------------ 23.14 preparo e pureza

    @Test
    void corErradaBaixaAPureza() {
        assertEquals(8, AlchemyMath.infusion(RULES, true), 1e-9);
        assertEquals(-15, AlchemyMath.infusion(RULES, false), 1e-9);
        // O líquido passa pelas cores em roda.
        List<String> cycle = List.of("verde", "azul", "roxo");
        assertEquals("verde", AlchemyMath.colorAt(cycle, 0, 24));
        assertEquals("verde", AlchemyMath.colorAt(cycle, 23, 24));
        assertEquals("azul", AlchemyMath.colorAt(cycle, 24, 24));
        assertEquals("roxo", AlchemyMath.colorAt(cycle, 60, 24));
        assertEquals("verde", AlchemyMath.colorAt(cycle, 72, 24));
    }

    @Test
    void superaquecerPorTresSegundosExplode() {
        // A destilação usa a mesma regra do fogo da cozinha: 60 ticks (3 s) acima da faixa.
        FireStep hot = new FireStep(0.4, 0.62, 160, 0.006, 60);
        int ticks = 0;
        while (!hot.done()) {
            hot.add(0.12);
            hot.tick();
            ticks++;
        }
        assertTrue(hot.burned());
        assertTrue(ticks < 160);
        // Mantida na faixa, não explode e vale os pontos cheios.
        FireStep kept = new FireStep(0.4, 0.62, 160, 0.006, 60);
        while (!kept.done()) {
            if (kept.heat() < 0.45) {
                kept.add(0.12);
            }
            kept.tick();
        }
        assertFalse(kept.burned());
        assertEquals(20, AlchemyMath.distill(RULES, kept.score()), 0.5);
        assertEquals(0, AlchemyMath.distill(RULES, 0), 1e-9);
    }

    @Test
    void aPurezaSaiDosPontosDoPreparo() {
        // Moer: do pior ritmo ao perfeito.
        assertEquals(35, AlchemyMath.grind(RULES, 0), 1e-9);
        assertEquals(60, AlchemyMath.grind(RULES, 1), 1e-9);
        // Preparo perfeito: moer 60 + duas cores certas 16 + destilar 20 + engarrafar 5 = 101.
        double perfect = AlchemyMath.grind(RULES, 1) + 2 * AlchemyMath.infusion(RULES, true) + AlchemyMath.distill(RULES, 1) + RULES.bottlePerfect();
        assertEquals(101, perfect, 1e-9);
        assertEquals(Purity.PERFEITA, AlchemyMath.purity(RULES, perfect, true, Purity.PERFEITA));
        // Os mesmos pontos sem engarrafar no brilho: pura.
        assertEquals(Purity.PURA, AlchemyMath.purity(RULES, perfect, false, Purity.PERFEITA));
        // Uma cor errada: 60 − 15 + 8 + 20 + 5 = 78, pura.
        assertEquals(Purity.PURA, AlchemyMath.purity(RULES, 78, true, Purity.PERFEITA));
        assertEquals(Purity.COMUM, AlchemyMath.purity(RULES, 69.9, true, Purity.PERFEITA));
        assertEquals(Purity.COMUM, AlchemyMath.purity(RULES, 40, false, Purity.PERFEITA));
        assertEquals(Purity.IMPURA, AlchemyMath.purity(RULES, 39.9, true, Purity.PERFEITA));
        // O frasco de vidro só comporta até "pura".
        assertEquals(Purity.PURA, AlchemyMath.purity(RULES, perfect, true, Purity.PURA));
        assertEquals(Purity.COMUM, AlchemyMath.purity(RULES, perfect, true, Purity.COMUM));
    }

    @Test
    void proficienciaAcimaDaDificuldadeAjudaAteUmLimite() {
        assertEquals(0, AlchemyMath.proficiency(RULES, 300, 300), 1e-9);
        assertEquals(6, AlchemyMath.proficiency(RULES, 500, 300), 1e-9);
        assertEquals(10, AlchemyMath.proficiency(RULES, 1000, 50), 1e-9);
        assertEquals(-3, AlchemyMath.proficiency(RULES, 200, 300), 1e-9);
        assertEquals(-10, AlchemyMath.proficiency(RULES, 0, 850), 1e-9);
    }

    // ------------------------------------------------------------------ 23.15 produtos

    @Test
    void purezaEscalaOEfeitoEDaDoseExtra() {
        assertEquals(0.5, Purity.IMPURA.factor(), 1e-9);
        assertEquals(1.0, Purity.COMUM.factor(), 1e-9);
        assertEquals(1.25, Purity.PURA.factor(), 1e-9);
        assertEquals(1.5, Purity.PERFEITA.factor(), 1e-9);
        // Perfeita: uma dose a mais no frasco.
        assertEquals(1, AlchemyMath.doses(1, Purity.PURA, 0));
        assertEquals(2, AlchemyMath.doses(1, Purity.PERFEITA, 0));
        assertEquals(4, AlchemyMath.doses(3, Purity.PERFEITA, 0));
        assertEquals(3, AlchemyMath.doses(2, Purity.COMUM, 1));
        assertEquals(Purity.PURA, Purity.parse("Pura"));
        assertNull(Purity.parse("divina"));
    }

    @Test
    void aPocaoDeCuraRecuperaAosPoucos() {
        AlchemyRegistry r = bundled();
        var cure = r.formula("pocao_de_cura_menor").product();
        assertEquals(Kind.CURA, cure.kind());
        assertEquals(8, cure.value(), 1e-9);
        // Em 10 s, e não de uma vez.
        assertEquals(10, cure.seconds(), 1e-9);
        var venom = r.formula("veneno_de_aranha").product();
        assertEquals("poison", venom.effect());
        assertEquals(15, venom.hits());
        var smoke = r.formula("frasco_de_fumaca").product();
        assertEquals(4, smoke.radius(), 1e-9);
        assertEquals("poison,wither,slowness", r.formula("antidoto").product().effect());
        // Buff: os minutos viram segundos.
        assertEquals(300, r.formula("pocao_de_forca").product().seconds(), 1e-9);
    }

    // ------------------------------------------------------------------ 23.16 ligações

    @Test
    void osInsumosExistemNasOutrasProfissoes() {
        AlchemyRegistry r = bundled();
        FishingRegistry fishing = FishingRegistry.parse(resource("fishing.yml"), new ArrayList<>());
        RecipeRegistry cooking = RecipeRegistry.parse(resource("ingredients.yml"), resource("recipes.yml"), new ArrayList<>());
        int checked = 0;
        for (Formula f : r.formulas().values()) {
            var p = f.product();
            if (p.kind() != Kind.INSUMO) {
                continue;
            }
            checked++;
            switch (p.effect()) {
                case "isca" -> assertNotNull(fishing.bait(p.ref()), f.id());
                case "ingrediente" -> assertNotNull(cooking.ingredient(p.ref()), f.id());
                default -> assertEquals("po_de_tempera", p.effect());
            }
            assertTrue(p.value() >= 1);
        }
        assertEquals(3, checked);
        // Os cristais são os do AincradItems.
        assertEquals("cura", r.formula("cristal_de_cura").product().ref());
        assertEquals("antidoto", r.formula("cristal_de_antidoto").product().ref());
    }

    @Test
    void errosNosArquivosSaoApontados() throws Exception {
        YamlConfiguration reagents = new YamlConfiguration();
        reagents.loadFromString("""
                reagentes:
                  frasco: {nome: F, encaixe: frasco, material: GLASS_BOTTLE}
                  agua: {nome: A, encaixe: base, material: POTION}
                  erva: {nome: E, encaixe: principal, material: FERN}
                  flor: {nome: L, encaixe: secundario, material: POPPY}
                  repetido: {nome: R, encaixe: base, material: POTION}
                  encaixe_ruim: {nome: X, encaixe: panela, material: BOWL}
                coleta:
                  boa: {reagente: erva, renasce_segundos: 60}
                  ruim: {reagente: nada}
                """);
        YamlConfiguration formulas = new YamlConfiguration();
        formulas.loadFromString("""
                formulas:
                  boa:
                    base: agua
                    principal: erva
                    secundario: flor
                    infusao: [{reagente: principal, cor: verde}, {reagente: secundario, cor: azul}]
                    produto: {tipo: cura, vida: 8, segundos: 10}
                  mesma_combinacao:
                    base: agua
                    principal: erva
                    secundario: flor
                    infusao: [{reagente: principal, cor: verde}, {reagente: secundario, cor: azul}]
                    produto: {tipo: antidoto, remove: [poison]}
                  reagente_no_lugar_errado:
                    base: erva
                    principal: agua
                    secundario: flor
                    infusao: [{reagente: principal, cor: verde}, {reagente: secundario, cor: azul}]
                    produto: {tipo: cura, vida: 8, segundos: 10}
                  cor_ruim:
                    base: agua
                    principal: erva
                    secundario: flor
                    infusao: [{reagente: principal, cor: rosa}, {reagente: secundario, cor: azul}]
                    produto: {tipo: cura, vida: 8, segundos: 10}
                  um_passo_so:
                    base: agua
                    principal: erva
                    secundario: flor
                    infusao: [{reagente: principal, cor: verde}]
                    produto: {tipo: cura, vida: 8, segundos: 10}
                  produto_ruim:
                    base: agua
                    principal: erva
                    secundario: flor
                    infusao: [{reagente: principal, cor: verde}, {reagente: secundario, cor: azul}]
                    produto: {tipo: veneno, efeito: poison}
                """);
        List<String> errors = new ArrayList<>();
        AlchemyRegistry r = AlchemyRegistry.parse(reagents, formulas, errors);
        assertEquals(4, r.reagents().size());
        assertEquals(1, r.gathers().size());
        assertEquals(1, r.formulas().size());
        assertEquals("boa", r.match("agua", "erva", "flor").id());
        assertEquals(9, errors.size(), errors.toString());
    }
}
