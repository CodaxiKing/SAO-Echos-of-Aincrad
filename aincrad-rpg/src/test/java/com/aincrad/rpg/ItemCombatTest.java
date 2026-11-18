package com.aincrad.rpg;

import com.aincrad.rpg.model.ItemCombat;
import com.aincrad.rpg.model.ItemCombat.Limits;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Os bônus de combate dos itens (crítico, esquiva, aparar, roubo de vida), sem servidor. */
class ItemCombatTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                ItemCombatTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    /** Um sorteio que devolve sempre o mesmo número de 0 a 1. */
    private static Random fixed(double v) {
        return new Random() {
            private static final long serialVersionUID = 1L;

            @Override
            public double nextDouble() {
                return v;
            }
        };
    }

    @Test
    void aChanceRespeitaOLimite() {
        // 80% de esquiva nos itens, limite de 40%: um sorteio de 0,5 não esquiva.
        assertFalse(ItemCombat.happens(80, 40, fixed(0.5)));
        assertTrue(ItemCombat.happens(80, 40, fixed(0.39)));
        assertFalse(ItemCombat.happens(0, 40, fixed(0)));
        assertFalse(ItemCombat.happens(-5, 40, fixed(0)));
    }

    @Test
    void criticoApararERouboDeVida() {
        Limits l = Limits.DEFAULT;
        // Crítico: 1,5x, mais 25% de dano crítico = 1,75x.
        assertEquals(15.0, ItemCombat.critical(10, 0, l), 1e-9);
        assertEquals(17.5, ItemCombat.critical(10, 25, l), 1e-9);
        assertEquals(5.0, ItemCombat.parried(10, l), 1e-9);
        // 10% de roubo de vida em 20 de dano cura 2; acima do limite (20%) vale o limite.
        assertEquals(2.0, ItemCombat.heal(20, 10, l), 1e-9);
        assertEquals(4.0, ItemCombat.heal(20, 90, l), 1e-9);
        assertEquals(0.0, ItemCombat.heal(0, 10, l), 1e-9);
        assertEquals(0.0, ItemCombat.of(Map.of(), ItemCombat.CRIT), 1e-9);
        assertEquals(3.0, ItemCombat.of(Map.of(ItemCombat.CRIT, 3.0), ItemCombat.CRIT), 1e-9);
    }

    @Test
    void aConfigEAsMensagensDoArquivo() {
        Limits l = Limits.parse(resource("config.yml").getConfigurationSection("combate_itens"));
        assertEquals(Limits.DEFAULT, l);
        assertEquals(Limits.DEFAULT, Limits.parse(null));
        YamlConfiguration msg = resource("messages.yml");
        for (String stat : ItemCombat.STATS) {
            assertNotNull(msg.getString("item.bonus_" + stat), stat);
        }
        // Os bônus de combate do items.yml de exemplo existem.
        var bonus = resource("items.yml").getConfigurationSection("itens.anneal_blade.bonus");
        assertTrue(ItemCombat.STATS.contains("critico") && bonus.contains("critico"));
    }
}
