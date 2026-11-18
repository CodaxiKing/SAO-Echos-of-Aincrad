package com.aincrad.rpg;

import com.aincrad.rpg.model.SpawnRules;
import com.aincrad.rpg.model.SpawnRules.Area;
import com.aincrad.rpg.model.SpawnRules.Entry;
import com.aincrad.rpg.model.SpawnRules.Kind;
import com.aincrad.rpg.model.SpawnRules.MobRef;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras dos mobs por região (spawns.yml), sem servidor. */
class SpawnRulesTest {

    private static final String YAML = """
            regioes:
              campos_andar1:
                maximo: 14
                por_vez: 2
                raio: 40
                distancia_minima: 10
                mobs:
                  - { mob: "mythic:JavaliFrenetico", peso: 3, level: "1-2" }
                  - { mob: "mythic:LoboTerrivel", peso: 1, level: {min: 2, max: 4} }
                  - { mob: pig, modelo: javali }
              com_erros:
                mobs:
                  - { peso: 2 }
                  - { mob: "outro:Coisa" }
                  - { mob: ZOMBIE, level: "abc" }
                  - { mob: ZOMBIE, peso: 0 }
                  - { mob: WOLF, level: 5 }
              vazia:
                mobs: []
            """;

    private static Map<String, Area> parse(List<String> errors) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(YAML);
        return SpawnRules.parse(y.getConfigurationSection("regioes"), errors);
    }

    @Test
    void oNomeDoMobDizDeOndeEleVem() {
        assertEquals(new MobRef(Kind.MYTHIC, "JavaliFrenetico"), MobRef.parse("mythic:JavaliFrenetico"));
        assertEquals(new MobRef(Kind.MYTHIC, "Lobo"), MobRef.parse("MM: Lobo"));
        assertEquals(new MobRef(Kind.VANILLA, "WOLF"), MobRef.parse("wolf"));
        assertEquals(new MobRef(Kind.VANILLA, "WOLF"), MobRef.parse("minecraft:wolf"));
        assertEquals("mythic:Lobo", MobRef.parse("mm:Lobo").text());
        assertNull(MobRef.parse("outro:Coisa"));
        assertNull(MobRef.parse("mythic:"));
        assertNull(MobRef.parse(" "));
        assertNull(MobRef.parse(null));
    }

    @Test
    void oLevelAceitaNumeroFaixaEMinMax() {
        assertArrayEquals(new int[] {3, 3}, SpawnRules.levels(3));
        assertArrayEquals(new int[] {2, 4}, SpawnRules.levels("2-4"));
        assertArrayEquals(new int[] {2, 4}, SpawnRules.levels("4 - 2"));
        assertArrayEquals(new int[] {2, 4}, SpawnRules.levels(Map.of("min", 2, "max", 4)));
        // Sem level: vale a faixa do lugar.
        assertArrayEquals(new int[] {0, 0}, SpawnRules.levels(null));
        assertNull(SpawnRules.levels("abc"));
        assertNull(SpawnRules.levels(0));
        assertNull(SpawnRules.levels("1-2-3"));
    }

    @Test
    void oArquivoELidoEOsErrosSaoApontados() throws Exception {
        List<String> errors = new ArrayList<>();
        Map<String, Area> areas = parse(errors);
        assertEquals(2, areas.size());

        Area fields = areas.get("campos_andar1");
        assertEquals(14, fields.max());
        assertEquals(2, fields.perRound());
        assertEquals(40, fields.radius());
        assertEquals(10, fields.minDistance());
        assertFalse(fields.natural());
        assertEquals(3, fields.mobs().size());
        Entry boar = fields.mobs().get(0);
        assertEquals("JavaliFrenetico", boar.mob().id());
        assertEquals(3, boar.weight());
        assertTrue(boar.fixedLevel());
        assertEquals(4, fields.mobs().get(1).maxLevel());
        Entry pig = fields.mobs().get(2);
        assertEquals(new MobRef(Kind.VANILLA, "PIG"), pig.mob());
        assertEquals("javali", pig.model());
        assertEquals(1, pig.weight());
        assertFalse(pig.fixedLevel());

        // Das cinco linhas de "com_erros" só a última vale; "vazia" fica de fora.
        Area bad = areas.get("com_erros");
        assertEquals(1, bad.mobs().size());
        assertEquals("WOLF", bad.mobs().get(0).mob().id());
        assertEquals(5, errors.size(), errors.toString());
        // Os padrões, e o sumiço sempre além do raio.
        assertEquals(10, bad.max());
        assertTrue(bad.despawn() > bad.radius());
    }

    @Test
    void oArquivoDoPluginVemSemRegioes() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new InputStreamReader(
                SpawnRulesTest.class.getResourceAsStream("/spawns.yml"), StandardCharsets.UTF_8));
        List<String> errors = new ArrayList<>();
        assertTrue(SpawnRules.parse(y.getConfigurationSection("regioes"), errors).isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void oSorteioRespeitaOPeso() throws Exception {
        List<Entry> mobs = parse(new ArrayList<>()).get("campos_andar1").mobs();
        // Pesos 3, 1, 1: o javali fica com os primeiros 60%.
        assertEquals("JavaliFrenetico", SpawnRules.pick(mobs, 0.0).mob().id());
        assertEquals("JavaliFrenetico", SpawnRules.pick(mobs, 0.59).mob().id());
        assertEquals("LoboTerrivel", SpawnRules.pick(mobs, 0.61).mob().id());
        assertEquals("PIG", SpawnRules.pick(mobs, 0.81).mob().id());
        assertEquals("PIG", SpawnRules.pick(mobs, 1.0).mob().id());
        assertNull(SpawnRules.pick(List.of(), 0.5));
    }

    @Test
    void nascemSoOsQueFaltamParaOMaximo() {
        assertEquals(2, SpawnRules.toSpawn(0, 14, 2));
        assertEquals(1, SpawnRules.toSpawn(13, 14, 2));
        assertEquals(0, SpawnRules.toSpawn(14, 14, 2));
        assertEquals(0, SpawnRules.toSpawn(20, 14, 2));
    }

    @Test
    void aDistanciaAteARegiaoEZeroDentroDela() {
        // Região de (0,60,0) a (9,70,9): ocupa de 0 até 10 em x e z.
        assertEquals(0, SpawnRules.distanceSq(0, 60, 0, 9, 70, 9, 5, 65, 5));
        assertEquals(0, SpawnRules.distanceSq(0, 60, 0, 9, 70, 9, 9.9, 70.9, 9.9));
        assertEquals(25, SpawnRules.distanceSq(0, 60, 0, 9, 70, 9, 15, 65, 5));
        assertEquals(9 + 16, SpawnRules.distanceSq(0, 60, 0, 9, 70, 9, -3, 65, 14));
    }
}
