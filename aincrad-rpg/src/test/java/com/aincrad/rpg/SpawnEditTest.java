package com.aincrad.rpg;

import com.aincrad.rpg.model.SpawnEdit;
import com.aincrad.rpg.model.SpawnRules;
import com.aincrad.rpg.model.SpawnRules.Area;
import com.aincrad.rpg.model.SpawnRules.Entry;
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

/** O que /aincrad spawn e o menu escrevem no spawns.yml é lido de volta sem erro. */
class SpawnEditTest {

    /** Grava em texto e lê de novo, como acontece no servidor a cada mudança. */
    private static YamlConfiguration roundTrip(YamlConfiguration y) throws Exception {
        YamlConfiguration again = new YamlConfiguration();
        again.loadFromString(y.saveToString());
        return again;
    }

    private static Map<String, Area> parse(YamlConfiguration y) {
        List<String> errors = new ArrayList<>();
        Map<String, Area> areas = SpawnRules.parse(y.getConfigurationSection("regioes"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return areas;
    }

    @Test
    void marcarUmaAreaPorMobsEMudarOLevel() throws Exception {
        // Começa do arquivo que vem no plugin, sem região nenhuma.
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new InputStreamReader(
                SpawnEditTest.class.getResourceAsStream("/spawns.yml"), StandardCharsets.UTF_8));
        assertTrue(SpawnEdit.addRegion(y, "Campos_Oeste"));
        assertFalse(SpawnEdit.addRegion(y, "campos_oeste")); // já existe, sem diferenciar maiúsculas
        assertEquals("campos_oeste", SpawnEdit.regionKey(y, "CAMPOS_OESTE"));
        assertNull(SpawnEdit.regionKey(y, "floresta"));
        assertEquals(-1, SpawnEdit.addMob(y, "floresta", "WOLF", 1, 1, 1));

        assertEquals(0, SpawnEdit.addMob(y, "campos_oeste", "PIG", 1, 2, 5));
        assertEquals(1, SpawnEdit.addMob(y, "campos_oeste", "WOLF", 2, 2, 1));
        assertEquals(2, SpawnEdit.addMob(y, "campos_oeste", "WOLF", 3, 5, 1));
        assertEquals(3, SpawnEdit.addMob(y, "campos_oeste", "mythic:JavaliFrenetico", 1, 1, 2));
        y = roundTrip(y);

        // Cada linha ganhou um id próprio, e o segundo lobo não repete o do primeiro.
        List<Map<String, Object>> lines = SpawnEdit.lines(y, "campos_oeste");
        assertEquals(List.of("pig", "wolf", "wolf_2", "javalifrenetico"), lines.stream().map(SpawnEdit::key).toList());
        assertEquals(2, SpawnEdit.find(y, "campos_oeste", "WOLF_2"));
        assertEquals(0, SpawnEdit.find(y, "campos_oeste", "PIG"));
        assertEquals(-1, SpawnEdit.find(y, "campos_oeste", "zombie"));

        Area a = parse(y).get("campos_oeste");
        assertEquals(4, a.mobs().size());
        assertEquals(12, a.max());
        Entry pig = a.mobs().get(0);
        assertEquals(1, pig.minLevel());
        assertEquals(2, pig.maxLevel());
        assertEquals(5, pig.weight());
        assertEquals("pig", pig.key());
        assertEquals(SpawnRules.Kind.MYTHIC, a.mobs().get(3).mob().kind());

        // Mudar level, peso, nome e período de um mob; e o máximo da área.
        assertTrue(SpawnEdit.set(y, "campos_oeste", 2, "level", SpawnEdit.levelValue(4, 6)));
        assertTrue(SpawnEdit.set(y, "campos_oeste", 2, "peso", 3));
        assertTrue(SpawnEdit.set(y, "campos_oeste", 2, "nome", "<white>Dire Wolf"));
        assertTrue(SpawnEdit.set(y, "campos_oeste", 2, "periodo", "noite"));
        assertTrue(SpawnEdit.setRegion(y, "campos_oeste", "maximo", 20));
        assertFalse(SpawnEdit.set(y, "campos_oeste", 9, "peso", 3));
        y = roundTrip(y);
        a = parse(y).get("campos_oeste");
        Entry wolf = a.mobs().get(2);
        assertEquals(4, wolf.minLevel());
        assertEquals(6, wolf.maxLevel());
        assertEquals(3, wolf.weight());
        assertEquals("<white>Dire Wolf", wolf.extra().name());
        assertEquals(Boolean.TRUE, wolf.night());
        assertEquals(20, a.max());

        // "sempre" tira o período; tirar um mob; tirar a área.
        assertTrue(SpawnEdit.set(y, "campos_oeste", 2, "periodo", null));
        assertTrue(SpawnEdit.removeMob(y, "campos_oeste", 0));
        y = roundTrip(y);
        a = parse(y).get("campos_oeste");
        assertEquals(3, a.mobs().size());
        assertNull(a.mobs().get(1).night());
        assertTrue(SpawnEdit.removeRegion(y, "campos_oeste"));
        assertFalse(SpawnEdit.removeRegion(y, "campos_oeste"));
        assertTrue(parse(roundTrip(y)).isEmpty());
    }

    @Test
    void osBotoesDeLevelNuncaDeixamOMinimoPassarDoMaximo() {
        assertArrayEquals(new int[]{3, 5}, SpawnEdit.shift(new int[]{2, 5}, 1, 0));
        assertArrayEquals(new int[]{1, 5}, SpawnEdit.shift(new int[]{1, 5}, -1, 0)); // nunca abaixo de 1
        // Subir o mínimo acima do máximo leva o máximo junto; descer o máximo abaixo do mínimo, o mínimo.
        assertArrayEquals(new int[]{4, 4}, SpawnEdit.shift(new int[]{3, 3}, 1, 0));
        assertArrayEquals(new int[]{2, 2}, SpawnEdit.shift(new int[]{3, 3}, 0, -1));
        assertArrayEquals(new int[]{3, 4}, SpawnEdit.shift(new int[]{3, 3}, 0, 1));
        assertEquals(3, SpawnEdit.levelValue(3, 3));
        assertEquals("2-4", SpawnEdit.levelValue(2, 4));
        assertArrayEquals(new int[]{2, 4}, SpawnEdit.level(Map.of("level", "2-4")));
        assertArrayEquals(new int[]{1, 1}, SpawnEdit.level(Map.of("mob", "PIG")));
    }

    @Test
    void editarOPacoteDoAndar1NaoEstragaOQueJaEstaLa() throws Exception {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new java.io.File("../conteudo/andar1/plugins/AincradRPG/spawns.yml"));
        int index = SpawnEdit.find(y, "labirinto_andar1", "kobold_soldado");
        assertEquals(0, index);
        assertTrue(SpawnEdit.set(y, "labirinto_andar1", index, "level", SpawnEdit.levelValue(7, 9)));
        y = roundTrip(y);
        Entry kobold = parse(y).get("labirinto_andar1").mobs().get(0);
        assertEquals(7, kobold.minLevel());
        // O nome, o equipamento e os drops que estavam na linha continuam.
        assertEquals("<white>Ruin Kobold Trooper", kobold.extra().name());
        assertEquals("IRON_SWORD", kobold.extra().equipment().get("mao"));
        assertEquals(3, kobold.extra().drops().size());
        assertEquals(5, parse(y).size());
    }
}
