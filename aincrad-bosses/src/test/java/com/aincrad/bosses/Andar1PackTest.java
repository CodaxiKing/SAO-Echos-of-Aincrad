package com.aincrad.bosses;

import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.BossRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O chefe e o chefe de campo do pacote do andar 1 (conteudo/andar1). */
class Andar1PackTest {

    private static final File PACK = new File("../conteudo/andar1/plugins");

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

    private static BossRegistry registry() {
        BossRegistry reg = new BossRegistry();
        reg.add(yaml("AincradBosses/bosses/andar1.yml"), "andar1.yml");
        // O Lobo Alfa vem no arquivo que o plugin cria sozinho.
        reg.add(YamlConfiguration.loadConfiguration(new InputStreamReader(
                Andar1PackTest.class.getResourceAsStream("/bosses/campo_e_evento.yml"), StandardCharsets.UTF_8)), "campo_e_evento.yml");
        assertTrue(reg.errors().isEmpty(), reg.errors().toString());
        return reg;
    }

    @Test
    void illfangTemQuatroBarrasETrocaDeArmaNaUltima() {
        BossDef b = registry().get("illfang");
        assertNotNull(b);
        assertEquals(1, b.floor());
        assertTrue(b.antiCrystal());
        assertEquals(4, b.phases().size());
        double last = 101;
        for (BossDef.Phase p : b.phases()) {
            assertTrue(p.at() < last, "as fases precisam descer");
            last = p.at();
        }
        // Sentinelas nas três primeiras barras; na última, a nodachi e nenhum lacaio.
        for (int i = 0; i < 3; i++) {
            assertTrue(b.phases().get(i).skills().stream().anyMatch(s -> s.type() == BossDef.SkillType.LACAIOS), "fase " + (i + 1));
        }
        BossDef.Phase nodachi = b.phases().get(3);
        assertEquals(25, nodachi.at());
        assertEquals("NETHERITE_SWORD", nodachi.equipment().get("mao"));
        assertTrue(nodachi.skills().stream().noneMatch(s -> s.type() == BossDef.SkillType.LACAIOS));
        assertTrue(nodachi.damageTimes() > 1);
    }

    @Test
    void recompensasExistemNoPacote() {
        Set<String> items = yaml("AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        BossDef b = registry().get("illfang");
        assertEquals("rpg:casaco_da_meia_noite:lendario", b.rewards().lastAttack());
        assertTrue(items.contains("casaco_da_meia_noite"));
        for (BossDef.Drop d : b.rewards().items()) {
            String spec = d.item();
            if (spec.startsWith("rpg:")) {
                assertTrue(items.contains(spec.split(":")[1]), spec);
            } else {
                assertNotNull(material(spec), spec);
            }
        }
    }

    @Test
    void chefeDeCampoFicaNumaRegiaoDeMobsDoAndar() {
        BossRegistry reg = registry();
        YamlConfiguration roaming = yaml("AincradBosses/roaming.yml");
        Set<String> regions = yaml("AincradRPG/spawns.yml").getConfigurationSection("regioes").getKeys(false);
        for (String id : roaming.getConfigurationSection("campo").getKeys(false)) {
            BossDef b = reg.get(roaming.getString("campo." + id + ".chefe", id));
            assertNotNull(b, id + ": chefe não existe");
            assertEquals(1, b.floor(), id);
            assertTrue(regions.contains(roaming.getString("campo." + id + ".regiao")), id + ": região");
            assertTrue(roaming.getDouble("campo." + id + ".intervalo_horas") > 0, id);
        }
    }
}
