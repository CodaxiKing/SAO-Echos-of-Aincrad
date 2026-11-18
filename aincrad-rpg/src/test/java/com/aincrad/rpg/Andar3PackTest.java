package com.aincrad.rpg;

import com.aincrad.rpg.model.SpawnRules;
import com.aincrad.rpg.model.SpawnRules.Area;
import com.aincrad.rpg.model.SpawnRules.Entry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O pacote do andar 3 (conteudo/andar3): soma ao do andar 2 (que já traz o 1), carrega sem erro, e
 * o que um arquivo cita do outro existe.
 */
class Andar3PackTest {

    private static final File A1 = new File("../conteudo/andar2/plugins");
    private static final File A2 = new File("../conteudo/andar3/plugins");
    private static final Set<String> RARITIES = Set.of("comum", "incomum", "raro", "epico", "lendario");
    private static final List<String> FLOOR3 = List.of("floresta_da_nevoa", "bosque_elfico", "caverna_das_aranhas",
            "labirinto_andar3", "labirinto_andar3_topo");

    private static Material material(String name) {
        try {
            return Material.valueOf(String.valueOf(name).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static YamlConfiguration yaml(File pack, String path) {
        File f = new File(pack, path);
        assertTrue(f.isFile(), "falta " + f);
        return YamlConfiguration.loadConfiguration(f);
    }

    private static Map<String, Area> spawns(File pack) {
        List<String> errors = new ArrayList<>();
        Map<String, Area> areas = SpawnRules.parse(yaml(pack, "AincradRPG/spawns.yml").getConfigurationSection("regioes"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return areas;
    }

    private static void checkItem(String spec, Set<String> items, String where) {
        if (spec.startsWith("rpg:")) {
            String[] p = spec.split(":");
            assertTrue(items.contains(p[1]), where + ": o item " + spec + " não está no items.yml");
            assertTrue(p.length < 3 || RARITIES.contains(p[2]), where + ": raridade em " + spec);
        } else if (!spec.startsWith("life:")) {
            assertNotNull(material(spec), where + ": " + spec + " não é um material");
        }
    }

    @Test
    void oPacoteSomaAoDoAndar2() {
        Map<String, Area> one = spawns(A1);
        Map<String, Area> two = spawns(A2);
        assertEquals(15, two.size());
        // Nada dos andares 1 e 2 se perde: as mesmas regiões, com os mesmos mobs.
        for (Map.Entry<String, Area> e : one.entrySet()) {
            assertEquals(e.getValue(), two.get(e.getKey()), "a região " + e.getKey() + " dos andares 1 e 2 mudou");
        }
        assertTrue(two.keySet().containsAll(FLOOR3));
        Set<String> items1 = yaml(A1, "AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        Set<String> items2 = yaml(A2, "AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        assertTrue(items2.containsAll(items1));
        assertTrue(items2.size() >= items1.size() + 13);
        assertTrue(yaml(A2, "AincradEconomy/shops.yml").getConfigurationSection("lojas").getKeys(false)
                .containsAll(yaml(A1, "AincradEconomy/shops.yml").getConfigurationSection("lojas").getKeys(false)));
    }

    @Test
    void mobsDoAndar3() {
        Map<String, Area> areas = spawns(A2);
        ConfigurationSection zones = yaml(A2, "AincradRPG/mobs.yml").getConfigurationSection("zonas");
        Set<String> items = yaml(A2, "AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        Map<String, String> names = new HashMap<>();
        int previousMax = 19; // o andar 2 acaba no level 19
        for (String region : FLOOR3) {
            Area a = areas.get(region);
            assertNotNull(zones.getConfigurationSection(region), "mobs.yml sem a zona " + region);
            // As regiões vão ficando mais difíceis, sem buraco de level.
            assertTrue(zones.getInt(region + ".min") >= previousMax && zones.getInt(region + ".min") <= previousMax + 1,
                    region + ": a faixa de level não continua a da região anterior");
            previousMax = zones.getInt(region + ".max");
            Set<String> ids = new HashSet<>();
            for (Entry e : a.mobs()) {
                String where = region + "/" + e.key();
                assertNotNull(e.extra().id(), where + ": falta o id");
                assertTrue(ids.add(e.key()), where + ": id repetido na região");
                assertNotNull(e.extra().name(), where + ": falta o nome");
                String old = names.put(e.key(), e.extra().name());
                assertTrue(old == null || old.equals(e.extra().name()), e.key() + " tem dois nomes");
                assertTrue(e.fixedLevel() && e.minLevel() >= zones.getInt(region + ".min") && e.maxLevel() <= zones.getInt(region + ".max"),
                        where + ": level fora da faixa da zona");
                assertFalse(e.extra().drops().isEmpty(), where + ": sem drops");
                for (SpawnRules.Drop d : e.extra().drops()) {
                    checkItem(d.item(), items, where);
                }
                for (String item : e.extra().equipment().values()) {
                    assertNotNull(material(item), where + ": equipamento " + item);
                }
            }
            assertTrue(a.mobs().stream().anyMatch(e -> !Boolean.TRUE.equals(e.night())), region + ": só tem mob de noite");
        }
        assertEquals(29, previousMax);
    }

    @Test
    void itensLojasEMobRaro() {
        ConfigurationSection all = yaml(A2, "AincradRPG/items.yml").getConfigurationSection("itens");
        Set<String> items = all.getKeys(false);
        Set<String> floor1 = yaml(A1, "AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        for (String id : items) {
            if (floor1.contains(id)) {
                continue;
            }
            ConfigurationSection s = all.getConfigurationSection(id);
            assertNotNull(material(s.getString("material")), id + ": material");
            assertTrue(s.getInt("level_minimo") >= 20 && s.getInt("level_minimo") <= 30, id + ": level_minimo do andar 3 vai de 20 a 30");
            for (String b : s.getConfigurationSection("bonus").getKeys(false)) {
                List<Double> range = s.getDoubleList("bonus." + b);
                assertTrue(range.size() == 2 && range.get(0) <= range.get(1), id + "." + b);
            }
        }
        Set<String> sold = new HashSet<>();
        ConfigurationSection shops = yaml(A2, "AincradEconomy/shops.yml").getConfigurationSection("lojas");
        for (String shop : shops.getKeys(false)) {
            for (Map<?, ?> it : shops.getMapList(shop + ".itens")) {
                String spec = String.valueOf(it.get("item"));
                checkItem(spec, items, shop);
                if (it.get("compra") instanceof Number b && it.get("venda") instanceof Number v) {
                    int amount = it.get("quantidade") instanceof Number q ? q.intValue() : 1;
                    assertTrue(v.doubleValue() * amount < b.doubleValue(), shop + "/" + spec + ": revender dá lucro");
                }
                if (it.get("compra") != null && spec.startsWith("rpg:")) {
                    sold.add(spec.split(":")[1]);
                }
            }
        }
        // O que só se ganha lutando ou em quest não se compra.
        for (String earned : List.of("raizes_de_nerius", "manto_de_seda_de_aranha", "sabre_da_cavaleira", "machado_do_lenhador",
                "coroa_do_rei_taurus", "machado_taurino", "lamina_do_eremita")) {
            assertTrue(items.contains(earned), earned);
            assertFalse(sold.contains(earned), earned + " está à venda");
        }
        Map<String, Area> areas = spawns(A2);
        ConfigurationSection rare = yaml(A2, "AincradRPG/rare-mobs.yml").getConfigurationSection("mobs");
        assertEquals(3, rare.getKeys(false).size());
        for (String id : rare.getKeys(false)) {
            assertTrue(areas.containsKey(rare.getString(id + ".regiao")), id + ": região sem mobs");
        }
        YamlConfiguration floors = yaml(A2, "AincradFloors/floors.yml");
        assertEquals("zumfut", floors.getString("andares.3.cidade"));
        assertEquals(List.of("acampamento_elfico", "posto_avancado"), floors.getStringList("andares.3.vilas"));
        // O andar seguinte fica de reserva: o chefe do 3 precisa de um andar 4 para liberar.
        assertNotNull(floors.getConfigurationSection("andares.4"));
        assertEquals("urbus", floors.getString("andares.2.cidade"));
        assertEquals(List.of("marome", "taran"), floors.getStringList("andares.2.vilas"));
        assertEquals(List.of("horunka", "tolbana"), floors.getStringList("andares.1.vilas"));
    }
}
