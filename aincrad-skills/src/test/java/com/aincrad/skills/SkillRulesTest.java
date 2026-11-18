package com.aincrad.skills;

import com.aincrad.skills.model.Passive;
import com.aincrad.skills.model.SkillDef;
import com.aincrad.skills.model.SkillRules;
import com.aincrad.skills.model.WeaponType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do AincradSkills (etapas 34 a 36), sem servidor. */
class SkillRulesTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                SkillRulesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    private static Map<String, SkillDef> bundled() {
        Map<String, SkillDef> all = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (String f : SkillEngine.BUNDLED) {
            all.putAll(SkillDef.parse(resource("skills/" + f + ".yml"), f + ".yml", errors));
        }
        assertTrue(errors.isEmpty(), errors.toString());
        return all;
    }

    private static Map<WeaponType, WeaponType.Match> weapons() {
        YamlConfiguration c = resource("config.yml");
        Map<WeaponType, WeaponType.Match> out = new EnumMap<>(WeaponType.class);
        for (String k : c.getConfigurationSection("armas").getKeys(false)) {
            out.put(WeaponType.parse(k), new WeaponType.Match(c.getStringList("armas." + k + ".nomes"),
                    c.getStringList("armas." + k + ".materiais")));
        }
        return out;
    }

    // ------------------------------------------------------------------ 34 proficiência e motor

    @Test
    void tresSkillsPorTipoDeArma() {
        Map<String, SkillDef> all = bundled();
        for (WeaponType t : WeaponType.values()) {
            long n = all.values().stream().filter(d -> d.weapon() == t).count();
            assertEquals(3, n, t.name());
            // Cada arma tem uma skill para quem começa do zero.
            assertTrue(all.values().stream().anyMatch(d -> d.weapon() == t && d.minProf() == 0), t.name());
        }
        // As de Duas Espadas, e só elas, exigem a skill única.
        for (SkillDef d : all.values()) {
            assertEquals(d.weapon() == WeaponType.DUAS_ESPADAS, UniqueService.DUAL.equals(d.unique()), d.id());
        }
        // Toda mensagem de arma e de passiva existe.
        YamlConfiguration msg = resource("messages.yml");
        for (WeaponType t : WeaponType.values()) {
            assertNotNull(msg.getString("armas." + t.key()), t.key());
        }
        for (Passive p : Passive.values()) {
            assertNotNull(msg.getString("passivas." + p.key() + ".nome"), p.key());
            assertNotNull(msg.getString("passivas." + p.key() + ".descricao"), p.key());
        }
    }

    @Test
    void aSkillDeExemploTemDanoRecargaEPausa() {
        SkillDef slant = bundled().get("corte_inclinado");
        assertEquals("Slant", slant.name());
        assertEquals(WeaponType.ESPADA, slant.weapon());
        assertEquals(5000, slant.cooldownMs());
        assertEquals(400, slant.freezeMs());
        assertEquals(SkillDef.Shape.ALVO, slant.shape());
        // 7 de dano de ataque x 1.6; com proficiência 1000 e +0.0005 por ponto, +50%.
        assertEquals(11.2, SkillRules.hitDamage(7, slant.damage(), 0, 0.0005), 1e-9);
        assertEquals(16.8, SkillRules.hitDamage(7, slant.damage(), 1000, 0.0005), 1e-9);
        // A recarga conta do uso; a pausa começa depois do último golpe.
        assertEquals(5000, SkillRules.remaining(1_000 + slant.cooldownMs(), 1_000));
        assertEquals(0, SkillRules.remaining(1_000 + slant.cooldownMs(), 9_000));
        SkillDef burst = bundled().get("explosao_estelar");
        assertEquals(16, burst.hits());
        assertEquals(45, SkillRules.castTicks(burst.hits(), burst.hitTicks()));
        assertEquals(0, SkillRules.castTicks(1, 4));
    }

    @Test
    void errosNoArquivoDeSkillSaoApontados() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                boa: {arma: espada, dano: 2}
                sem_arma: {dano: 2}
                arma_ruim: {arma: arco}
                forma_ruim: {arma: adaga, forma: cone}
                golpes_demais: {arma: adaga, golpes: 99}
                dano_zero: {arma: adaga, dano: 0}
                """);
        List<String> errors = new ArrayList<>();
        Map<String, SkillDef> out = SkillDef.parse(y, "teste.yml", errors);
        assertEquals(List.of("boa"), List.copyOf(out.keySet()));
        assertEquals(5, errors.size(), errors.toString());
        // Os padrões de uma skill mínima.
        SkillDef d = out.get("boa");
        assertEquals(1, d.hits());
        assertEquals(8000, d.cooldownMs());
        assertEquals(500, d.freezeMs());
        assertNull(d.unique());
    }

    @Test
    void oNomeDoItemValeMaisQueOMaterial() {
        Map<WeaponType, WeaponType.Match> w = weapons();
        assertEquals(WeaponType.ESPADA, WeaponType.detect("IRON_SWORD", null, w));
        assertEquals(WeaponType.ESPADA, WeaponType.detect("IRON_SWORD", "Lâmina do Anoitecer", w));
        assertEquals(WeaponType.FLORETE, WeaponType.detect("IRON_SWORD", "Florete de Ferro", w));
        assertEquals(WeaponType.ADAGA, WeaponType.detect("GOLDEN_SWORD", "Adaga do Ladrão", w));
        assertEquals(WeaponType.MACHADO, WeaponType.detect("DIAMOND_AXE", null, w));
        assertEquals(WeaponType.LANCA, WeaponType.detect("TRIDENT", null, w));
        // "Espada de Duas Mãos" contém as duas palavras: a mais comprida ganha.
        assertEquals(WeaponType.DUAS_MAOS, WeaponType.detect("NETHERITE_SWORD", "Espada de Duas Mãos", w));
        assertNull(WeaponType.detect("STICK", null, w));
        assertNull(WeaponType.detect("STICK", "Graveto", w));
        assertNull(WeaponType.detect(null, "Florete", w));
        assertEquals(WeaponType.DUAS_MAOS, WeaponType.parse("duas maos"));
        assertNull(WeaponType.parse("arco"));
    }

    @Test
    void aProficienciaSobeMaisDevagarPertoDoMaximo() {
        assertEquals(0.5, SkillRules.gainChance(0, 0.5, 0.05), 1e-9);
        assertEquals(0.25, SkillRules.gainChance(500, 0.5, 0.05), 1e-9);
        assertEquals(0.05, SkillRules.gainChance(950, 0.5, 0.05), 1e-9);
        assertEquals(0, SkillRules.gainChance(1000, 0.5, 0.05), 1e-9);
    }

    // ------------------------------------------------------------------ 35 atalhos e espaços

    @Test
    void osEspacosCrescemComOLevel() {
        Map<Integer, Integer> table = Map.of(1, 2, 10, 3, 25, 4);
        assertEquals(2, SkillRules.slots(table, 1, 4));
        assertEquals(2, SkillRules.slots(table, 9, 4));
        assertEquals(3, SkillRules.slots(table, 10, 4));
        assertEquals(4, SkillRules.slots(table, 80, 4));
        // Sem o RPG o level vem no máximo: todos os espaços.
        assertEquals(4, SkillRules.slots(table, Integer.MAX_VALUE, 4));
        assertEquals(0, SkillRules.slots(table, 0, 4));
        assertEquals(4, SkillRules.slots(Map.of(1, 9), 5, 4));
    }

    @Test
    void asTeclasDosAtalhosPulamOItemNaMao() {
        // Arma na primeira tecla: os atalhos são 2, 3, 4 e 5 (índices 1 a 4).
        assertArrayEquals(new int[] {1, 2, 3, 4}, SkillRules.keys(0));
        assertArrayEquals(new int[] {0, 1, 3, 4}, SkillRules.keys(2));
        assertArrayEquals(new int[] {0, 1, 2, 3}, SkillRules.keys(8));
        assertEquals(0, SkillRules.slotOfKey(0, 1));
        assertEquals(2, SkillRules.slotOfKey(2, 3));
        assertEquals(-1, SkillRules.slotOfKey(2, 2));
        assertEquals(-1, SkillRules.slotOfKey(0, 7));
    }

    @Test
    void aLinhaAFrenteAcertaSoQuemEstaNela() {
        // Olhando para +X a partir da origem, alcance 5, meia largura 1.
        assertTrue(SkillRules.inLine(0, 0, 0, 1, 0, 0, 3, 0, 0.5, 5, 1));
        assertTrue(SkillRules.inLine(0, 0, 0, 1, 0, 0, 5, 0, 0, 5, 1));
        assertFalse(SkillRules.inLine(0, 0, 0, 1, 0, 0, 6, 0, 0, 5, 1));
        assertFalse(SkillRules.inLine(0, 0, 0, 1, 0, 0, 3, 0, 2, 5, 1));
        // Atrás do jogador não acerta.
        assertFalse(SkillRules.inLine(0, 0, 0, 1, 0, 0, -1, 0, 0, 5, 1));
    }

    // ------------------------------------------------------------------ 36 passivas

    @Test
    void aSetaDoRastreioApontaParaOAlvo() {
        // Yaw 0 olha para +Z: um alvo em +Z está à frente; em -X (oeste) fica à direita.
        assertEquals("↑", SkillRules.arrow(0, 0, 0, 0, 10));
        assertEquals("↓", SkillRules.arrow(0, 0, 0, 0, -10));
        assertEquals("→", SkillRules.arrow(0, 0, 0, -10, 0));
        assertEquals("←", SkillRules.arrow(0, 0, 0, 10, 0));
        // Olhando para o alvo, de qualquer lado, a seta é para a frente.
        assertEquals("↑", SkillRules.arrow(90, 0, 0, -10, 0));
        assertEquals("↑", SkillRules.arrow(-90, 0, 0, 10, 0));
        assertEquals("↗", SkillRules.arrow(0, 0, 0, -10, 10));
        assertEquals(Passive.APARAR, Passive.parse("aparar"));
        assertNull(Passive.parse("voar"));
    }
}
