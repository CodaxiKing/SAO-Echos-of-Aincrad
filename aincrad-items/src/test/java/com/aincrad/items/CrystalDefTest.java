package com.aincrad.items;

import com.aincrad.items.crystal.CrystalDef;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalDefTest {

    private static ConfigurationSection example() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                CrystalDefTest.class.getResourceAsStream("/crystals.yml"), StandardCharsets.UTF_8)).getConfigurationSection("cristais");
    }

    @Test
    void osCincoCristaisDeExemploCarregam() {
        ConfigurationSection root = example();
        List<String> errors = new ArrayList<>();
        for (CrystalDef.Type type : CrystalDef.Type.values()) {
            String id = type.name().toLowerCase();
            CrystalDef def = CrystalDef.parse(id, root.getConfigurationSection(id), errors);
            assertNotNull(def, id);
            assertEquals(type, def.type());
            // O modelo de cada cristal existe no resource pack que vem no .jar.
            for (String model : new String[]{def.model(), def.recordedModel()}) {
                if (model != null) {
                    String path = model.substring(model.indexOf(':') + 1);
                    for (String kind : new String[]{"items/" + path + ".json", "models/" + path + ".json", "textures/" + path + ".png"}) {
                        assertTrue(new File("src/main/resources/resourcepack/assets/aincrad/" + kind).isFile(), id + ": falta " + kind);
                    }
                }
            }
        }
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void camposDeCadaTipo() {
        ConfigurationSection root = example();
        List<String> errors = new ArrayList<>();
        CrystalDef cura = CrystalDef.parse("cura", root.getConfigurationSection("cura"), errors);
        assertEquals(1.0, cura.amount());
        assertEquals(30, cura.cooldown());
        assertTrue(cura.consumed());
        CrystalDef antidoto = CrystalDef.parse("antidoto", root.getConfigurationSection("antidoto"), errors);
        assertEquals(List.of("POISON", "WITHER", "SLOWNESS"), antidoto.remove());
        CrystalDef tp = CrystalDef.parse("teleporte", root.getConfigurationSection("teleporte"), errors);
        assertEquals(3, tp.waitSeconds());
        CrystalDef res = CrystalDef.parse("ressurreicao", root.getConfigurationSection("ressurreicao"), errors);
        assertEquals(0.5, res.amount());
        assertEquals(10, res.dropMobLevel());
        CrystalDef grav = CrystalDef.parse("gravacao", root.getConfigurationSection("gravacao"), errors);
        assertFalse(grav.consumed());
        assertEquals(256, grav.maxLength());
        assertEquals("aincrad:crystal/gravacao_gravado", grav.recordedModel());
    }

    @Test
    void tipoDesconhecidoEPadroes() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                errado: { tipo: fogo }
                simples: { tipo: antidoto }
                meia_cura: { tipo: cura, cura: 0.5, grupo_recarga: CURA }
                """);
        List<String> errors = new ArrayList<>();
        assertNull(CrystalDef.parse("errado", y.getConfigurationSection("errado"), errors));
        assertEquals(1, errors.size());
        CrystalDef simples = CrystalDef.parse("simples", y.getConfigurationSection("simples"), errors);
        assertEquals(List.of("POISON", "WITHER", "SLOWNESS"), simples.remove());
        assertEquals("simples", simples.cooldownGroup());
        assertEquals("AMETHYST_SHARD", simples.material());
        // Dois cristais de cura com o mesmo grupo dividem a recarga.
        assertEquals("cura", CrystalDef.parse("meia_cura", y.getConfigurationSection("meia_cura"), errors).cooldownGroup());
    }

    @Test
    void mensagemGravadaEhCortada() {
        CrystalDef grav = CrystalDef.parse("gravacao", example().getConfigurationSection("gravacao"), new ArrayList<>());
        assertEquals("oi  tudo bem", grav.clip("  oi\n tudo bem  "));
        assertEquals(256, grav.clip("a".repeat(400)).length());
    }
}
