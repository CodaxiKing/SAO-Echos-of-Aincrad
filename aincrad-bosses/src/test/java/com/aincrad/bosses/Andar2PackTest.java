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

/** Os chefes do pacote do andar 2 (conteudo/andar2): Asterius e o Bullbous Bow. */
class Andar2PackTest {

    private static final File PACK = new File("../conteudo/andar2/plugins");

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
        reg.add(YamlConfiguration.loadConfiguration(new InputStreamReader(
                Andar2PackTest.class.getResourceAsStream("/bosses/campo_e_evento.yml"), StandardCharsets.UTF_8)), "campo_e_evento.yml");
        assertTrue(reg.errors().isEmpty(), reg.errors().toString());
        return reg;
    }

    @Test
    void asteriusEMaisForteQueOIllfangEChamaOGeneralEOCoronel() {
        BossRegistry reg = registry();
        BossDef illfang = reg.get("illfang");
        BossDef asterius = reg.get("asterius");
        assertNotNull(asterius);
        assertEquals(2, asterius.floor());
        assertTrue(asterius.antiCrystal());
        assertTrue(asterius.health() > illfang.health() && asterius.damage() > illfang.damage() && asterius.level() > illfang.level());
        assertEquals(4, asterius.phases().size());
        double last = 101;
        for (BossDef.Phase p : asterius.phases()) {
            assertTrue(p.at() < last, "as fases precisam descer");
            last = p.at();
        }
        // A luta começa contra os dois lacaios; na última fase o Rei luta sozinho.
        long helpers = asterius.phases().get(0).skills().stream().filter(s -> s.type() == BossDef.SkillType.LACAIOS).count();
        assertEquals(2, helpers);
        assertTrue(asterius.phases().get(3).skills().stream().noneMatch(s -> s.type() == BossDef.SkillType.LACAIOS));
        assertTrue(asterius.rewards().xp() > illfang.rewards().xp());
    }

    @Test
    void recompensasEChefesDeCampo() {
        BossRegistry reg = registry();
        Set<String> items = yaml("AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        for (String id : new String[]{"asterius", "bullbous_bow"}) {
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
        assertEquals("rpg:coroa_do_rei_taurus:lendario", reg.get("asterius").rewards().lastAttack());
        YamlConfiguration roaming = yaml("AincradBosses/roaming.yml");
        Set<String> regions = yaml("AincradRPG/spawns.yml").getConfigurationSection("regioes").getKeys(false);
        assertEquals(Set.of("lobo_alfa", "bullbous_bow"), roaming.getConfigurationSection("campo").getKeys(false));
        for (String id : roaming.getConfigurationSection("campo").getKeys(false)) {
            assertNotNull(reg.get(id), id + ": chefe não existe");
            assertTrue(regions.contains(roaming.getString("campo." + id + ".regiao")), id + ": região");
        }
        // O chefe de campo fica no andar dele.
        assertEquals(2, reg.get("bullbous_bow").floor());
    }
}
