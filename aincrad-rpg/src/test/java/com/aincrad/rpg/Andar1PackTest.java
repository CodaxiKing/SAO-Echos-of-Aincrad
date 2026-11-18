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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O pacote de conteúdo do andar 1 (conteudo/andar1): os arquivos carregam sem erro e o que um
 * cita do outro existe — mob, item, região, loja.
 */
class Andar1PackTest {

    private static final File PACK = new File("../conteudo/andar1/plugins");
    private static final Set<String> RARITIES = Set.of("comum", "incomum", "raro", "epico", "lendario");
    private static final Set<String> SLOTS = Set.of("MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET");
    private static final Set<String> BONUS = Set.of("dano", "vida", "armadura", "resistencia", "velocidade_ataque",
            "velocidade", "critico", "dano_critico", "roubo_vida", "esquiva", "aparar");

    /** O material pelo nome, sem precisar de servidor. Null se não existe. */
    private static Material material(String name) {
        try {
            return Material.valueOf(String.valueOf(name).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static YamlConfiguration yaml(String path) {
        File f = new File(PACK, path);
        assertTrue(f.isFile(), "falta " + f);
        return YamlConfiguration.loadConfiguration(f);
    }

    private static Map<String, Area> spawns() {
        List<String> errors = new ArrayList<>();
        Map<String, Area> areas = SpawnRules.parse(yaml("AincradRPG/spawns.yml").getConfigurationSection("regioes"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return areas;
    }

    private static Set<String> items() {
        return yaml("AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
    }

    /** "rpg:id" ou "rpg:id:raridade" aponta para um item do pacote? Outros itens precisam ser materiais. */
    private static void checkItem(String spec, String where) {
        if (spec.startsWith("rpg:")) {
            String[] p = spec.split(":");
            assertTrue(items().contains(p[1]), where + ": o item " + spec + " não está no items.yml");
            if (p.length > 2) {
                assertTrue(RARITIES.contains(p[2]), where + ": raridade desconhecida em " + spec);
            }
        } else if (!spec.startsWith("life:")) {
            Material m = material(spec);
            assertNotNull(m, where + ": " + spec + " não é um material");
        }
    }

    @Test
    void mobsDasSeisRegioes() {
        Map<String, Area> areas = spawns();
        assertEquals(Set.of("campos_oeste", "floresta_horunka", "planicie_tolbana", "labirinto_andar1", "labirinto_andar1_topo"),
                areas.keySet());
        ConfigurationSection zones = yaml("AincradRPG/mobs.yml").getConfigurationSection("zonas");
        for (Area a : areas.values()) {
            assertNotNull(zones.getConfigurationSection(a.region()), "mobs.yml sem a zona " + a.region());
            Set<String> ids = new HashSet<>();
            for (Entry e : a.mobs()) {
                String where = a.region() + "/" + e.key();
                // Todo mob do andar tem id (as quests usam), nome, level próprio e drops.
                assertNotNull(e.extra().id(), where + ": falta o id");
                assertTrue(ids.add(e.key()), where + ": id repetido na região");
                assertNotNull(e.extra().name(), where + ": falta o nome");
                assertTrue(e.fixedLevel(), where + ": falta o level");
                assertTrue(e.minLevel() >= zones.getInt(a.region() + ".min") && e.maxLevel() <= zones.getInt(a.region() + ".max"),
                        where + ": level fora da faixa da zona no mobs.yml");
                assertFalse(e.extra().drops().isEmpty(), where + ": sem drops");
                for (SpawnRules.Drop d : e.extra().drops()) {
                    checkItem(d.item(), where);
                }
                for (String item : e.extra().equipment().values()) {
                    assertNotNull(material(item), where + ": equipamento " + item);
                }
            }
            // De dia sempre há o que caçar: nem todas as linhas são só da noite.
            assertTrue(a.mobs().stream().anyMatch(e -> !Boolean.TRUE.equals(e.night())), a.region() + ": só tem mob de noite");
        }
    }

    @Test
    void oMesmoMobTemOMesmoNomeEmTodasAsRegioes() {
        Map<String, String> names = new java.util.HashMap<>();
        for (Area a : spawns().values()) {
            for (Entry e : a.mobs()) {
                String old = names.put(e.key(), e.extra().name());
                assertTrue(old == null || old.equals(e.extra().name()), e.key() + " tem dois nomes: " + old + " e " + e.extra().name());
            }
        }
    }

    @Test
    void itensDoAndar() {
        ConfigurationSection all = yaml("AincradRPG/items.yml").getConfigurationSection("itens");
        assertTrue(all.getKeys(false).size() >= 20);
        for (String id : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(id);
            Material m = material(s.getString("material", ""));
            assertNotNull(m, id + ": material");
            assertTrue(SLOTS.contains(s.getString("slot")), id + ": slot");
            assertTrue(s.getInt("level_minimo") >= 1 && s.getInt("level_minimo") <= 10, id + ": level_minimo do andar 1 vai de 1 a 10");
            ConfigurationSection bonus = s.getConfigurationSection("bonus");
            assertNotNull(bonus, id + ": bonus");
            for (String b : bonus.getKeys(false)) {
                assertTrue(BONUS.contains(b), id + ": bônus desconhecido " + b);
                List<Double> range = bonus.getDoubleList(b);
                assertEquals(2, range.size(), id + "." + b + ": precisa de [mín, máx]");
                assertTrue(range.get(0) <= range.get(1), id + "." + b + ": mín maior que máx");
            }
            if (s.contains("drop")) {
                double chance = s.getDouble("drop.chance");
                assertTrue(chance > 0 && chance <= 0.05, id + ": chance de drop");
                List<Integer> lv = s.getIntegerList("drop.level_mob");
                assertEquals(2, lv.size(), id + ": drop.level_mob");
                // O item não cai de mob muito abaixo do level para usá-lo.
                assertTrue(lv.get(1) >= s.getInt("level_minimo"), id + ": cai só de mobs fracos demais");
            }
        }
    }

    @Test
    void mobRaroELojasCitamCoisasQueExistem() {
        Map<String, Area> areas = spawns();
        ConfigurationSection rare = yaml("AincradRPG/rare-mobs.yml").getConfigurationSection("mobs");
        for (String id : rare.getKeys(false)) {
            assertTrue(areas.containsKey(rare.getString(id + ".regiao")), id + ": região sem mobs");
            assertNotNull(material(rare.getString(id + ".mob")), id + ": mob");
        }
        ConfigurationSection shops = yaml("AincradEconomy/shops.yml").getConfigurationSection("lojas");
        assertTrue(shops.getKeys(false).size() >= 6);
        Set<String> sold = new HashSet<>();
        for (String shop : shops.getKeys(false)) {
            List<Map<?, ?>> list = shops.getMapList(shop + ".itens");
            assertFalse(list.isEmpty(), shop + ": sem itens");
            for (Map<?, ?> it : list) {
                String spec = String.valueOf(it.get("item"));
                checkItem(spec, shop);
                Object buy = it.get("compra");
                Object sell = it.get("venda");
                assertTrue(buy != null || sell != null, shop + "/" + spec + ": sem preço");
                if (buy instanceof Number b && sell instanceof Number v) {
                    int amount = it.get("quantidade") instanceof Number q ? q.intValue() : 1;
                    // Comprar e revender na mesma loja nunca dá lucro.
                    assertTrue(v.doubleValue() * amount < b.doubleValue(), shop + "/" + spec + ": revender dá lucro");
                }
                if (buy != null && spec.startsWith("rpg:")) {
                    sold.add(spec.split(":")[1]);
                }
            }
        }
        // As armas de começo de cada tipo estão à venda na Cidade do Início.
        assertTrue(sold.containsAll(Set.of("espada_pequena", "adaga_de_ferro", "florete_de_treino", "machado_de_lenhador", "lanca_curta")));
        // O que só se ganha lutando não se compra.
        assertFalse(sold.contains("anneal_blade"));
        assertFalse(sold.contains("casaco_da_meia_noite"));
        assertFalse(sold.contains("machado_kobold"));
    }

    @Test
    void oAndarTemACidadeEAsVilas() {
        YamlConfiguration floors = yaml("AincradFloors/floors.yml");
        assertEquals("cidade_inicio", floors.getString("andares.1.cidade"));
        assertEquals(List.of("horunka", "tolbana"), floors.getStringList("andares.1.vilas"));
        // Nenhuma zona segura é também região de mobs.
        Set<String> safe = new HashSet<>(floors.getStringList("andares.1.vilas"));
        safe.add(floors.getString("andares.1.cidade"));
        for (String r : spawns().keySet()) {
            assertFalse(safe.contains(r), r + " é zona segura e região de mobs");
        }
    }
}
