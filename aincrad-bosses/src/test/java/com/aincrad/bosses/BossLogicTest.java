package com.aincrad.bosses;

import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.BossRegistry;
import com.aincrad.bosses.model.Cuboid;
import com.aincrad.bosses.model.DamageLedger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossLogicTest {

    private static BossRegistry example() {
        BossRegistry reg = new BossRegistry();
        reg.add(YamlConfiguration.loadConfiguration(new InputStreamReader(
                BossLogicTest.class.getResourceAsStream("/bosses/andar1.yml"), StandardCharsets.UTF_8)), "andar1.yml");
        return reg;
    }

    @Test
    void illfangDeExemploCarrega() {
        BossRegistry reg = example();
        assertTrue(reg.errors().isEmpty(), reg.errors().toString());
        BossDef b = reg.get("ILLFANG");
        assertNotNull(b);
        assertEquals(1, b.floor());
        assertEquals(10, b.level());
        assertEquals("WITHER_SKELETON", b.mob());
        assertEquals(600, b.health());
        assertEquals("IRON_AXE", b.equipment().get("mao"));
        assertEquals("SHIELD", b.equipment().get("mao_secundaria"));
        assertEquals(3, b.phases().size());
        assertEquals("rpg:casaco_couro:lendario", b.rewards().lastAttack());
        assertEquals(3, b.rewards().items().size());
    }

    @Test
    void aos25PorCentoTrocaDeArmaEFicaMaisRapido() {
        BossDef b = example().get("illfang");
        assertEquals(0, b.phaseAt(100));
        assertEquals(0, b.phaseAt(50.1));
        assertEquals(1, b.phaseAt(50));
        assertEquals(1, b.phaseAt(25.5));
        assertEquals(2, b.phaseAt(25));
        assertEquals(2, b.phaseAt(0));
        BossDef.Phase last = b.phases().get(2);
        assertEquals("NETHERITE_SWORD", last.equipment().get("mao"));
        assertEquals("AIR", last.equipment().get("mao_secundaria"));
        assertEquals(1.4, last.speedTimes());
        assertEquals(1.3, last.damageTimes());
        assertEquals(List.of(BossDef.SkillType.INVESTIDA, BossDef.SkillType.SALTO, BossDef.SkillType.FURIA),
                last.skills().stream().map(BossDef.Skill::type).toList());
        assertEquals(1.5, last.skills().get(2).multiplier());
    }

    @Test
    void errosDeCadastro() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                sem_andar: { nome: "X", vida: 10 }
                sem_vida: { andar: 2 }
                simples:
                  andar: 2
                  vida: 100
                  fases:
                    - vida: 40
                      habilidades:
                        - { tipo: voar }
                        - { tipo: projetil, mob: bola_de_fogo }
                """);
        BossRegistry reg = new BossRegistry();
        reg.add(y, "teste.yml");
        assertEquals(3, reg.errors().size(), reg.errors().toString());
        assertNull(reg.get("sem_andar"));
        BossDef b = reg.get("simples");
        // A única fase passa a valer desde o começo; a habilidade desconhecida fica de fora.
        assertEquals(100, b.phases().get(0).at());
        assertEquals(1, b.phases().get(0).skills().size());
        assertEquals(5, b.rewards().minDamagePercent());
    }

    @Test
    void danoMinimoEOrdem() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        DamageLedger l = new DamageLedger();
        l.add(a, 100);
        l.add(b, 400);
        l.add(c, 20);
        l.add(a, 50);
        l.add(c, 0);
        assertEquals(List.of(b, a), l.eligible(5, 600)); // 5% de 600 = 30: c (20) fica de fora
        assertEquals(a, l.lastAttacker());
        assertEquals(570, l.total());
        assertEquals(List.of(b, a, c), l.eligible(0, 600));
    }

    @Test
    void itensEmRodizio() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var drops = List.of(new BossDef.Drop("IRON_INGOT", 5), new BossDef.Drop("rpg:espada", 1));
        Map<UUID, Map<String, Integer>> split = DamageLedger.split(drops, List.of(a, b));
        assertEquals(3, split.get(a).get("IRON_INGOT"));
        assertEquals(2, split.get(b).get("IRON_INGOT"));
        // O rodízio continua: depois de 5 lingotes (a, b, a, b, a), a espada vai para b.
        assertEquals(1, split.get(b).get("rpg:espada"));
        assertTrue(DamageLedger.split(drops, List.of()).isEmpty());
    }

    @Test
    void caixaDaSala() {
        Cuboid box = Cuboid.of("world", 10, 70, -5, 0, 64, 5);
        assertEquals("world,0,64,-5,10,70,5", box.text());
        assertEquals(box, Cuboid.parse(box.text()));
        assertTrue(box.contains("world", 10.9, 70.9, 5.9));
        assertFalse(box.contains("world", 11, 65, 0));
        assertFalse(box.contains("nether", 5, 65, 0));
        assertEquals(11L * 7 * 11, box.volume());
        assertNull(Cuboid.parse("world,1,2"));
    }
}
