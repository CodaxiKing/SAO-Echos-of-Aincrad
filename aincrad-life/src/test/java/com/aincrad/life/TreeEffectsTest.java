package com.aincrad.life;

import com.aincrad.life.cooking.DishPerks;
import com.aincrad.life.cooking.FoodBuff;
import com.aincrad.life.cooking.Quality;
import com.aincrad.life.cooking.Recipe;
import com.aincrad.life.fishing.FishingPerks;
import com.aincrad.life.fishing.FishingRegistry;
import com.aincrad.life.fishing.FishingRegistry.Bait;
import com.aincrad.life.fishing.FishingRegistry.Fish;
import com.aincrad.life.fishing.FishingRegistry.Rarity;
import com.aincrad.life.fishing.FishingRegistry.Zone;
import com.aincrad.life.model.Profession;
import com.aincrad.life.model.TreeDef;
import com.aincrad.life.smith.ForgeMath;
import com.aincrad.life.station.StationPanel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Os efeitos das árvores de especialização: nenhum nó fica sem fazer nada. */
class TreeEffectsTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                TreeEffectsTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    private static TreeDef tree(Profession p) {
        List<String> errors = new ArrayList<>();
        TreeDef t = TreeDef.parse(p, resource("trees/" + p.key() + ".yml"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return t;
    }

    /** Uma árvore de mentira: os efeitos que o artesão "comprou". */
    private static ToDoubleFunction<String> mods(Object... pairs) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return key -> m.getOrDefault(key, 0.0);
    }

    // ------------------------------------------------------------------ nenhum efeito sem uso

    @Test
    void todoEfeitoDasQuatroArvoresFazAlgumaCoisa() throws IOException {
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(f));
            }
        }
        List<String> idle = new ArrayList<>();
        for (Profession p : Profession.values()) {
            TreeDef t = tree(p);
            for (TreeDef.Node n : t.nodes()) {
                assertFalse(n.effects().isEmpty(), p.key() + "/" + n.id() + ": nó sem efeito");
                for (String key : n.effects().keySet()) {
                    // O efeito é lido pelo código de alguma profissão...
                    boolean read = source.indexOf("\"" + key + "\"") >= 0;
                    // ...ou é o nó "desbloqueio" do ramo, que libera os itens exclusivos dele.
                    TreeDef.Node unlock = t.unlockNode(n.path(), n.spec());
                    boolean unlocks = unlock != null && unlock.id().equals(n.id());
                    if (!read && !unlocks) {
                        idle.add(p.key() + "/" + n.id() + " → " + key);
                    }
                }
            }
        }
        assertTrue(idle.isEmpty(), "efeitos que não fazem nada: " + idle);
    }

    // ------------------------------------------------------------------ o nó que libera o ramo

    @Test
    void itemExclusivoDoRamoPedeONoQueOLibera() {
        TreeDef t = tree(Profession.FERREIRO);
        TreeDef.Node armas = t.unlockNode("armeiro", null);
        TreeDef.Node laminas = t.unlockNode("armeiro", "laminas");
        assertNotNull(armas);
        assertNotNull(laminas);
        assertEquals("desbloqueio", armas.kind());
        assertTrue(armas.effects().containsKey("desbloqueia_armas_b"));
        assertTrue(laminas.effects().containsKey("desbloqueia_laminas_a"));

        Set<String> owned = new HashSet<>(Set.of("caminho:armeiro"));
        // Escolheu o caminho, mas ainda não comprou o nó: os projetos do ramo continuam fechados.
        assertFalse(t.unlocked("armeiro", null, owned));
        owned.add(armas.id());
        assertTrue(t.unlocked("armeiro", null, owned));
        assertFalse(t.unlocked("armeiro", "laminas", owned));
        owned.add(laminas.id());
        assertTrue(t.unlocked("armeiro", "laminas", owned));
        // Item de qualquer caminho não pede nó nenhum.
        assertTrue(t.unlocked(null, null, Set.of()));
        assertNull(t.unlockNode(null, null));
    }

    // ------------------------------------------------------------------ ferreiro

    @Test
    void aMarteladaEsmagadoraValePorTres() {
        ForgeMath.Work normal = new ForgeMath.Work(10, 3);
        ForgeMath.Work heavy = new ForgeMath.Work(10, 3);
        for (int i = 0; i < 10; i++) {
            normal.strike(StationPanel.Hit.PERFEITA, true);
            StationPanel.Hit real = heavy.strike(StationPanel.Hit.PERFEITA, true);
            if (i == 9) {
                heavy.amplify(real, 2);
            }
        }
        assertEquals(10, heavy.done()); // não conta batidas a mais
        assertEquals(100, normal.quality());
        assertEquals(100, heavy.quality()); // nunca passa de 100

        ForgeMath.Work a = new ForgeMath.Work(10, 3);
        ForgeMath.Work b = new ForgeMath.Work(10, 3);
        a.strike(StationPanel.Hit.BOA, true);
        b.amplify(b.strike(StationPanel.Hit.BOA, true), 2);
        assertEquals((a.quality() - 50) * 3, b.quality() - 50, 1e-9);
        // Batida ruim não é repetida.
        ForgeMath.Work c = new ForgeMath.Work(10, 3);
        double before = c.quality();
        c.amplify(StationPanel.Hit.RUIM, 2);
        assertEquals(before, c.quality());
    }

    // ------------------------------------------------------------------ culinária

    private static Recipe recipe(FoodBuff.Type type, int portions) {
        return new Recipe("teste", "Teste", Map.of(), 0, 0, 100, List.of(), new Recipe.Buff(type, 10, 10), false, portions, null, null, "BREAD");
    }

    @Test
    void oQueAArvoreDoCozinheiroPoeNoPrato() {
        Recipe meat = recipe(FoodBuff.Type.FORCA, 1);
        Recipe sweet = recipe(FoodBuff.Type.VELOCIDADE, 1);
        Recipe feast = recipe(FoodBuff.Type.VIDA_MAXIMA, 4);

        // Sem nó nenhum, o prato é só o prato.
        assertEquals(DishPerks.NONE, DishPerks.of(meat, Quality.EXCELENTE, mods(), 4, 5));
        // Queimado não ganha nada, com a árvore que for.
        assertEquals(DishPerks.NONE, DishPerks.of(meat, Quality.QUEIMADO, mods("tecnica_ragu", 1, "tecnica_rancho", 1), 4, 5));

        // Ragu: só prato de carne excelente.
        assertEquals(3600, DishPerks.of(meat, Quality.EXCELENTE, mods("tecnica_ragu", 1), 4, 5).minSeconds());
        assertEquals(0, DishPerks.of(meat, Quality.BOM, mods("tecnica_ragu", 1), 4, 5).minSeconds());
        assertEquals(0, DishPerks.of(sweet, Quality.EXCELENTE, mods("tecnica_ragu", 1), 4, 5).minSeconds());

        // Néctar: só doce ou bebida excelente.
        assertTrue(DishPerks.of(sweet, Quality.EXCELENTE, mods("tecnica_nectar", 1), 4, 5).extraSlot());
        assertFalse(DishPerks.of(meat, Quality.EXCELENTE, mods("tecnica_nectar", 1), 4, 5).extraSlot());

        // Festim: regeneração, alcance e o segundo buff (vida; se o prato já dá vida, força).
        DishPerks f = DishPerks.of(feast, Quality.BOM, mods("festim_regeneracao", 1, "alcance_buff", 5, "tecnica_banquete_real", 1), 4, 5);
        assertTrue(f.regen());
        assertEquals(5, f.shareRadius());
        assertEquals(FoodBuff.Type.FORCA, f.second());
        assertEquals(5, f.secondValue());
        Recipe stew = recipe(FoodBuff.Type.REGENERACAO, 4);
        assertEquals(FoodBuff.Type.VIDA_MAXIMA, DishPerks.of(stew, Quality.BOM, mods("tecnica_banquete_real", 1), 4, 5).second());
        // Prato individual não é festim.
        DishPerks single = DishPerks.of(meat, Quality.BOM, mods("festim_regeneracao", 1, "alcance_buff", 5, "tecnica_banquete_real", 1), 4, 5);
        assertFalse(single.regen());
        assertEquals(0, single.shareRadius());
        assertNull(single.second());

        // Rações e conservas: só pratos individuais.
        DishPerks ration = DishPerks.of(meat, Quality.EXCELENTE, mods("empilha", 1, "tecnica_pao_do_viajante", 1, "tecnica_despensa", 1, "tecnica_rancho", 1), 4, 5);
        assertTrue(ration.stack64() && ration.dayFood() && ration.twoBites() && ration.instant());
        DishPerks pot = DishPerks.of(feast, Quality.EXCELENTE, mods("empilha", 1, "tecnica_pao_do_viajante", 1, "tecnica_despensa", 1), 4, 5);
        assertFalse(pot.stack64() || pot.dayFood() || pot.twoBites());
        assertEquals(List.of("despensa", "rancho", "pao_do_viajante"), ration.labels());

        // O bônus de ramo soma só no tipo de prato do ramo.
        ToDoubleFunction<String> m = mods("forca_buff", 0.05, "forca_buff_carne", 0.1, "forca_buff_doce", 0.2);
        assertEquals(0.15, DishPerks.strength(meat, m), 1e-9);
        assertEquals(0.25, DishPerks.strength(sweet, m), 1e-9);
        assertEquals(0.05, DishPerks.strength(recipe(FoodBuff.Type.REGENERACAO, 1), m), 1e-9);
    }

    // ------------------------------------------------------------------ pesca

    @Test
    void zonaFundaETesourosSoComOsNos() {
        assertEquals(Zone.MEIO, FishingPerks.allowed(Zone.FUNDO, mods()));
        assertEquals(Zone.FUNDO, FishingPerks.allowed(Zone.FUNDO, mods("desbloqueia_fundo", 1)));
        assertEquals(Zone.RASO, FishingPerks.allowed(Zone.RASO, mods()));

        // Sem liberar os tesouros, nunca vem um.
        assertEquals(0, FishingPerks.treasureChance(Zone.FUNDO, 0.05, mods("chance_tesouro", 0.08)));
        ToDoubleFunction<String> hunter = mods("desbloqueia_tesouros", 1, "chance_tesouro", 0.08);
        assertEquals(0.13, FishingPerks.treasureChance(Zone.FUNDO, 0.05, hunter), 1e-9);
        assertEquals(0, FishingPerks.treasureChance(Zone.MEIO, 0.05, hunter));
        assertEquals(0, FishingPerks.treasureChance(Zone.RASO, 0.05, hunter));
        assertEquals(0.05, FishingPerks.treasureChance(Zone.MEIO, 0.05, mods("desbloqueia_tesouros", 1, "tesouro_no_meio", 1)), 1e-9);

        assertEquals(1, FishingPerks.materialAmount(mods(), true)); // sem a técnica, o veio não triplica
        assertEquals(3, FishingPerks.materialAmount(mods("materiais_extras", 2), false));
        assertEquals(9, FishingPerks.materialAmount(mods("materiais_extras", 2, "tecnica_veio", 1), true));
        assertEquals(3, FishingPerks.chestItems(2, mods("itens_bau", 1)));
    }

    @Test
    void iscasPedemONoEUmPeixeAAltura() {
        Bait worm = new Bait("minhoca", "Minhoca", "WHEAT_SEEDS", 0.1, false);
        Bait shiny = new Bait("isca_brilhante", "Isca Brilhante", "GLOWSTONE_DUST", 0.25, false);
        Bait lord = new Bait("isca_do_senhor_do_lago", "Isca do Senhor do Lago", "NAUTILUS_SHELL", 0, true);
        Bait golden = new Bait(FishingPerks.GOLDEN_BAIT, "Isca Dourada", "GOLD_NUGGET", 0, false);
        Fish common = new Fish("lambari", "Lambari", "COD", Rarity.COMUM, 20, 6, 15);
        Fish uncommon = new Fish("truta", "Truta", "SALMON", Rarity.INCOMUM, 200, 25, 60);
        Fish rare = new Fish("enguia", "Enguia", "SALMON", Rarity.RARO, 500, 60, 140);

        assertEquals(0, FishingPerks.baitTier(worm));
        assertEquals(1, FishingPerks.baitTier(shiny));
        assertEquals(2, FishingPerks.baitTier(lord));
        assertEquals(2, FishingPerks.baitTier(golden));

        // A minhoca qualquer pescador faz, de qualquer peixe; sem peixe na mão, não.
        assertNull(FishingPerks.baitRefusal(worm, common, mods()));
        assertEquals("peixe", FishingPerks.baitRefusal(worm, null, mods()));
        // Rank B: pede o nó e um peixe incomum.
        assertEquals("no", FishingPerks.baitRefusal(shiny, rare, mods()));
        assertEquals("peixe", FishingPerks.baitRefusal(shiny, common, mods("desbloqueia_iscas_b", 1)));
        assertNull(FishingPerks.baitRefusal(shiny, uncommon, mods("desbloqueia_iscas_b", 1)));
        // Rank A e a de lendário: o outro nó e um peixe raro.
        assertEquals("no", FishingPerks.baitRefusal(lord, rare, mods("desbloqueia_iscas_b", 1)));
        assertNull(FishingPerks.baitRefusal(lord, rare, mods("desbloqueia_iscas_a", 1)));
        // A dourada é só da técnica.
        assertEquals("no", FishingPerks.baitRefusal(golden, rare, mods("desbloqueia_iscas_a", 1)));
        assertNull(FishingPerks.baitRefusal(golden, rare, mods("tecnica_isca_dourada", 1)));
        assertEquals(3, FishingPerks.baitsPerFish(2, mods("iscas_extras", 1)));
    }

    @Test
    void aIscaDouradaExisteETrazUmRaroDaZona() {
        List<String> errors = new ArrayList<>();
        FishingRegistry reg = FishingRegistry.parse(resource("fishing.yml"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertNotNull(reg.bait(FishingPerks.GOLDEN_BAIT), "a isca dourada precisa estar no fishing.yml");
        boolean some = false;
        for (FishingRegistry.Point point : reg.points().values()) {
            for (Zone z : Zone.values()) {
                for (Fish f : FishingPerks.rare(point, z, reg.fishes())) {
                    assertEquals(Rarity.RARO, f.rarity());
                    some = true;
                }
            }
        }
        assertTrue(some, "nenhum ponto de pesca tem peixe raro para a isca dourada trazer");
    }

    // ------------------------------------------------------------------ mensagens

    @Test
    void asMensagensNovasExistem() {
        YamlConfiguration msg = resource("messages.yml");
        for (String key : List.of("saber.falta_no", "saber.catalisador_nome", "saber.catalisador_usado",
                "forja.tecnica_golpe_de_mestre", "forja.tecnica_martelada", "forja.tecnica_fio_lendario", "forja.tecnica_renascer",
                "forja.reparo_so_restaurador", "forja.up_no", "forja.esquivou", "forja.bonus.esquiva", "forja.bonus.velocidade",
                "cozinha.buff_nectar", "cozinha.buff_compartilhado", "cozinha.perk_despensa_sobrou",
                "pesca.tecnica_arpao", "pesca.tecnica_naufragio", "pesca.tecnica_pesca_do_dia", "pesca.pegou_bau", "pesca.pegou_material",
                "pesca.pegou_veio", "pesca.iscas_lista", "pesca.isca_desconhecida", "pesca.isca_recusa_no", "pesca.isca_recusa_peixe",
                "pesca.isca_feita", "pesca.mergulho_espera", "pesca.mergulho_nada", "pesca.mergulho_pegou",
                "alquimia.bebeu_elixir", "alquimia.cristal_poupado", "alquimia.cristal_puro_lore", "alquimia.tecnica_catalisador")) {
            assertTrue(msg.isString(key), "falta a mensagem " + key);
        }
        for (String perk : DishPerks.of(recipe(FoodBuff.Type.FORCA, 1), Quality.EXCELENTE, key -> 1, 4, 5).labels()) {
            assertTrue(msg.isString("cozinha.perk." + perk), "falta cozinha.perk." + perk);
        }
        for (String perk : DishPerks.of(recipe(FoodBuff.Type.VELOCIDADE, 4), Quality.EXCELENTE, key -> 5, 4, 5).labels()) {
            assertTrue(msg.isString("cozinha.perk." + perk), "falta cozinha.perk." + perk);
        }
        for (String mark : List.of("elixir", "rapido", "peconha", "fio_de_fogo", "tempestade")) {
            assertTrue(msg.isString("alquimia.marca." + mark), "falta alquimia.marca." + mark);
        }
        assertTrue(msg.isList("saber.catalisador_lore"));
    }
}
