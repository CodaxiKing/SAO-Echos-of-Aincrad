package com.aincrad.life;

import com.aincrad.life.cooking.FireStep;
import com.aincrad.life.cooking.FoodBuff;
import com.aincrad.life.cooking.Quality;
import com.aincrad.life.cooking.Recipe;
import com.aincrad.life.cooking.RecipeRegistry;
import com.aincrad.life.cooking.SlotKind;
import com.aincrad.life.cooking.Step;
import com.aincrad.life.model.Profession;
import com.aincrad.life.model.TreeDef;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras da culinária (tarefas 23.1 a 23.3), sem servidor. */
class CookingRulesTest {

    private static final Quality.Rules RULES = new Quality.Rules(0.25, 0.55, 0.85, 0.5, 0.15, 0.2);

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                CookingRulesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return y;
    }

    private static RecipeRegistry bundled() {
        List<String> errors = new ArrayList<>();
        RecipeRegistry r = RecipeRegistry.parse(resource("ingredients.yml"), resource("recipes.yml"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return r;
    }

    private static Map<SlotKind, String> combo(String base, String protein, String vegetable, String seasoning) {
        Map<SlotKind, String> m = new EnumMap<>(SlotKind.class);
        m.put(SlotKind.BASE, base);
        m.put(SlotKind.PROTEINA, protein);
        m.put(SlotKind.VEGETAL, vegetable);
        m.put(SlotKind.TEMPERO, seasoning);
        return m;
    }

    // ------------------------------------------------------------------ 23.1 ingredientes e combinações

    @Test
    void osArquivosDoPluginCarregamSemErro() {
        RecipeRegistry r = bundled();
        assertTrue(r.ingredients().size() >= 15);
        assertTrue(r.recipes().size() >= 8);
        // Os caminhos citados nas receitas existem na árvore da culinária.
        List<String> errors = new ArrayList<>();
        TreeDef tree = TreeDef.parse(Profession.CULINARIA, resource("trees/culinaria.yml"), errors);
        assertNotNull(tree);
        YamlConfiguration treeYaml = resource("trees/culinaria.yml");
        for (Recipe recipe : r.recipes().values()) {
            if (recipe.path() != null) {
                assertTrue(treeYaml.isConfigurationSection("caminhos." + recipe.path()), recipe.id() + ": caminho " + recipe.path());
            }
            assertTrue(recipe.produceMin() >= recipe.studyMin());
        }
    }

    @Test
    void combinacaoConhecidaDaONomeEASecretaEAchadaPelaCombinacao() {
        RecipeRegistry r = bundled();
        Recipe stew = r.match(combo("batata", "carne_bovina", "cenoura", "ervas_finas"));
        assertNotNull(stew);
        assertEquals("Ensopado do Viajante", stew.name());
        assertFalse(stew.secret());
        assertEquals(List.of(Step.CORTAR, Step.FOGO), stew.steps());

        Recipe secret = r.match(combo("batata", "peixe", "algas", "cacau"));
        assertNotNull(secret);
        assertTrue(secret.secret());
        // Trocar um ingrediente muda (ou desfaz) o prato.
        assertEquals("peixe_grelhado", r.match(combo("batata", "peixe", "algas", "ervas_finas")).id());
        assertNull(r.match(combo("trigo", "peixe", "algas", "cacau")));
        // Falta um encaixe obrigatório: nada.
        Map<SlotKind, String> incomplete = combo("batata", "carne_bovina", "cenoura", "ervas_finas");
        incomplete.remove(SlotKind.TEMPERO);
        assertNull(r.match(incomplete));
    }

    @Test
    void oEspecialAMaisNaoDesfazAReceita() {
        RecipeRegistry r = bundled();
        Map<SlotKind, String> c = combo("batata", "carne_bovina", "cenoura", "ervas_finas");
        c.put(SlotKind.ESPECIAL, "frutas_brilhantes");
        assertEquals("ensopado_do_viajante", r.match(c).id());
    }

    @Test
    void ingredienteComumPeloMaterialEProprioSoPelaMarca() {
        RecipeRegistry r = bundled();
        assertEquals("cenoura", r.common("CARROT").id());
        // RABBIT comum é a carne de coelho; a do coelho raro é item próprio.
        assertEquals("carne_coelho", r.common("rabbit").id());
        assertTrue(r.ingredient("carne_coelho_raro").custom());
        assertNull(r.common("GLOWSTONE_DUST"));
        assertEquals(5, r.ingredient("carne_coelho_raro").rankIndex());
        assertEquals(SlotKind.PROTEINA, r.ingredient("carne_coelho_raro").slot());
    }

    @Test
    void errosNosArquivosSaoApontadosEOItemEPulado() {
        YamlConfiguration ing = yaml("""
                ingredientes:
                  a: {nome: A, encaixe: base, rank: E, material: WHEAT}
                  b: {nome: B, encaixe: proteina, rank: E, material: BEEF}
                  c: {nome: C, encaixe: vegetal, rank: E, material: CARROT}
                  d: {nome: D, encaixe: tempero, rank: E, material: SUGAR}
                  repetido: {nome: R, encaixe: base, rank: E, material: WHEAT}
                  sem_encaixe: {nome: X, encaixe: sobremesa, rank: E, material: APPLE}
                  rank_ruim: {nome: Y, encaixe: base, rank: Z, material: POTATO}
                """);
        YamlConfiguration rec = yaml("""
                receitas:
                  boa:
                    encaixes: {base: a, proteina: b, vegetal: c, tempero: d}
                    etapas: [cortar]
                    buff: {tipo: forca, valor: 5, minutos: 10}
                  mesma_combinacao:
                    encaixes: {base: a, proteina: b, vegetal: c, tempero: d}
                    etapas: [fogo]
                    buff: {tipo: xp, valor: 5, minutos: 10}
                  falta_encaixe:
                    encaixes: {base: a, proteina: b, vegetal: c}
                    etapas: [cortar]
                    buff: {tipo: forca, valor: 5, minutos: 10}
                  encaixe_trocado:
                    encaixes: {base: b, proteina: a, vegetal: c, tempero: d}
                    etapas: [cortar]
                    buff: {tipo: forca, valor: 5, minutos: 10}
                  etapa_ruim:
                    encaixes: {base: a, proteina: b, vegetal: c, tempero: d, especial: nada}
                    etapas: [fritar]
                    buff: {tipo: forca, valor: 5, minutos: 10}
                  buff_ruim:
                    encaixes: {base: a, proteina: b, vegetal: c, tempero: d}
                    etapas: [cortar]
                    buff: {tipo: voar, valor: 5, minutos: 10}
                """);
        List<String> errors = new ArrayList<>();
        RecipeRegistry r = RecipeRegistry.parse(ing, rec, errors);
        assertEquals(4, r.ingredients().size());
        assertEquals(1, r.recipes().size());
        assertEquals("boa", r.match(combo("a", "b", "c", "d")).id());
        assertEquals(8, errors.size(), errors.toString());
    }

    // ------------------------------------------------------------------ 23.2 etapas e qualidade

    @Test
    void acertosForcadosDaoExcelenteEErrosQueimam() {
        // Tudo perfeito e o sorteio dentro da chance: excelente.
        assertEquals(Quality.EXCELENTE, Quality.decide(RULES, 1.0, false, 0.5, 0.49));
        // Tudo perfeito, mas o sorteio fora da chance: bom.
        assertEquals(Quality.BOM, Quality.decide(RULES, 1.0, false, 0.5, 0.5));
        assertEquals(Quality.BOM, Quality.decide(RULES, 0.7, false, 1.0, 0.0));
        assertEquals(Quality.COMUM, Quality.decide(RULES, 0.4, false, 1.0, 0.0));
        assertEquals(Quality.QUEIMADO, Quality.decide(RULES, 0.1, false, 1.0, 0.0));
        // Errar o fogo queima, por melhor que tenha sido o resto.
        assertEquals(Quality.QUEIMADO, Quality.decide(RULES, 1.0, true, 1.0, 0.0));
    }

    @Test
    void chanceDeExcelenteCresceComAProficienciaEOEmpratar() {
        double atLimit = Quality.excellentChance(RULES, 300, 300, false, 0);
        assertEquals(0.5, atLimit, 1e-9);
        assertEquals(0.8, Quality.excellentChance(RULES, 500, 300, false, 0), 1e-9);
        assertEquals(0.7, Quality.excellentChance(RULES, 300, 300, true, 0), 1e-9);
        assertEquals(0.53, Quality.excellentChance(RULES, 300, 300, false, 0.03), 1e-9);
        // Nunca abaixo de 5% nem acima de 100%.
        assertEquals(0.05, Quality.excellentChance(RULES, 0, 1000, false, 0), 1e-9);
        assertEquals(1.0, Quality.excellentChance(RULES, 1000, 0, true, 0), 1e-9);
    }

    @Test
    void fogoNaFaixaDaNotaCheiaEQuenteDemaisQueima() {
        // Mantido na faixa: a cada tick abaixo dela, mais lenha.
        FireStep kept = new FireStep(0.4, 0.64, 160, 0.006, 60);
        while (!kept.done()) {
            if (kept.heat() < 0.45) {
                kept.add(0.12);
            }
            kept.tick();
        }
        assertFalse(kept.burned());
        assertTrue(kept.score() >= 0.95, "nota " + kept.score());

        // Lenha sem parar: passa da faixa, fica lá e queima antes do fim.
        FireStep hot = new FireStep(0.4, 0.64, 160, 0.006, 60);
        int ticks = 0;
        while (!hot.done()) {
            hot.add(0.12);
            hot.tick();
            ticks++;
        }
        assertTrue(hot.burned());
        assertTrue(ticks < 160);

        // Fogo esquecido: apaga, não queima, e a nota é baixa.
        FireStep cold = new FireStep(0.4, 0.64, 160, 0.006, 60);
        while (!cold.done()) {
            cold.tick();
        }
        assertFalse(cold.burned());
        assertEquals(0, cold.score(), 1e-9);
        assertEquals(1.0, cold.progress(), 1e-9);
    }

    // ------------------------------------------------------------------ 23.3 buffs

    @Test
    void duracaoDoBuffPelaQualidade() {
        assertEquals(0, FoodBuff.seconds(10, Quality.QUEIMADO, 0.8, 1.0, 1.5, 0));
        assertEquals(480, FoodBuff.seconds(10, Quality.COMUM, 0.8, 1.0, 1.5, 0));
        assertEquals(600, FoodBuff.seconds(10, Quality.BOM, 0.8, 1.0, 1.5, 0));
        // Excelente: 50% mais longo.
        assertEquals(900, FoodBuff.seconds(10, Quality.EXCELENTE, 0.8, 1.0, 1.5, 0));
        // Bônus do cozinheiro (nós da árvore).
        assertEquals(720, FoodBuff.seconds(10, Quality.BOM, 0.8, 1.0, 1.5, 0.2));
    }

    @Test
    void buffVaiEVoltaDoPerfilEVence() {
        FoodBuff b = new FoodBuff(FoodBuff.Type.VIDA_MAXIMA, 20, 5_000, "Ensopado; de Coelho");
        FoodBuff back = FoodBuff.decode(b.encode());
        assertEquals(b, back);
        assertTrue(back.active(4_999));
        assertFalse(back.active(5_000));
        assertNull(FoodBuff.decode(null));
        assertNull(FoodBuff.decode(""));
        assertNull(FoodBuff.decode("VOAR;1;2;x"));
        assertNull(FoodBuff.decode("FORCA;muito;2;x"));
        assertEquals(FoodBuff.Type.XP, FoodBuff.Type.parse("xp_extra"));
        assertEquals(FoodBuff.Type.FORCA, FoodBuff.Type.parse("força"));
    }
}
