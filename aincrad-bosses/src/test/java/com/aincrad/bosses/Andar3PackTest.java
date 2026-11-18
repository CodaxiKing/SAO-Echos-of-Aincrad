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

/** Os chefes do pacote do andar 3 (conteudo/andar3): Nerius e a Nephila Regina. */
class Andar3PackTest {

    private static final File PACK = new File("../conteudo/andar3/plugins");

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
        reg.add(yaml("AincradBosses/bosses/andar2.yml"), "andar2.yml");
        reg.add(yaml("AincradBosses/bosses/andar3.yml"), "andar3.yml");
        reg.add(YamlConfiguration.loadConfiguration(new InputStreamReader(
                Andar3PackTest.class.getResourceAsStream("/bosses/campo_e_evento.yml"), StandardCharsets.UTF_8)), "campo_e_evento.yml");
        assertTrue(reg.errors().isEmpty(), reg.errors().toString());
        return reg;
    }

    @Test
    void neriusEMaisForteQueOAsteriusEParalisa() {
        BossRegistry reg = registry();
        BossDef illfang = reg.get("asterius");
        BossDef asterius = reg.get("nerius");
        assertNotNull(asterius);
        assertEquals(3, asterius.floor());
        assertTrue(asterius.antiCrystal());
        assertTrue(asterius.health() > illfang.health() && asterius.damage() > illfang.damage() && asterius.level() > illfang.level());
        assertEquals(4, asterius.phases().size());
        double last = 101;
        for (BossDef.Phase p : asterius.phases()) {
            assertTrue(p.at() < last, "as fases precisam descer");
            last = p.at();
        }
        // O pólen (sopro) só aparece da terceira fase em diante; na última, o Nerius luta sem as sementes.
        for (int i = 0; i < 4; i++) {
            boolean breath = asterius.phases().get(i).skills().stream().anyMatch(s -> s.type() == BossDef.SkillType.SOPRO);
            assertEquals(i >= 2, breath, "fase " + (i + 1));
        }
        assertTrue(asterius.phases().get(3).skills().stream().noneMatch(s -> s.type() == BossDef.SkillType.LACAIOS));
        assertTrue(asterius.rewards().xp() > illfang.rewards().xp());
    }

    @Test
    void recompensasEChefesDeCampo() {
        BossRegistry reg = registry();
        Set<String> items = yaml("AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        for (String id : new String[]{"nerius", "nephila_regina"}) {
            BossDef b = reg.get(id);
            assertNotNull(b, id);
            if (b.rewards().lastAttack() != null && b.rewards().lastAttack().startsWith("rpg:")) {
                assertTrue(items.contains(b.rewards().lastAttack().split(":")[1]), id + ": last attack");
            }
            for (BossDef.Drop d : b.rewards().items()) {
                if (d.item().startsWith("rpg:")) {
                    assertTrue(items.contains(d.item().split(":")[1]), id + ": " + d.item());
                } else {
                    assertNotNull(material(d.item()), id + ": " + d.item());
                }
            }
        }
        assertEquals("rpg:raizes_de_nerius:lendario", reg.get("nerius").rewards().lastAttack());
        YamlConfiguration roaming = yaml("AincradBosses/roaming.yml");
        Set<String> regions = yaml("AincradRPG/spawns.yml").getConfigurationSection("regioes").getKeys(false);
        assertEquals(Set.of("lobo_alfa", "bullbous_bow", "nephila_regina"), roaming.getConfigurationSection("campo").getKeys(false));
        for (String id : roaming.getConfigurationSection("campo").getKeys(false)) {
            assertNotNull(reg.get(id), id + ": chefe não existe");
            assertTrue(regions.contains(roaming.getString("campo." + id + ".regiao")), id + ": região");
        }
        // O chefe de campo fica no andar dele.
        assertEquals(2, reg.get("bullbous_bow").floor());
        assertEquals(3, reg.get("nephila_regina").floor());
    }
}
