package com.aincrad.effects.engine;

import com.aincrad.effects.image.ImageRegistry;
import com.aincrad.effects.step.StepFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os YAML que vêm no .jar. Materiais e partículas só são validados com o servidor rodando
 * (o registro do Paper exige um servidor); aqui conferimos o resto.
 */
class ExamplesTest {

    private static final File RES = new File("src/main/resources");

    @Test
    void todaImagemTemPngProvisorio() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(RES, "images.yml"));
        ImageRegistry images = ImageRegistry.load(cfg, new File(RES, "resourcepack/assets/aincrad/textures"), null);
        assertFalse(images.all().isEmpty());
        assertEquals(images.all().size(), images.presentCount());
    }

    @Test
    void exemplosUsamTiposEImagensValidos() throws Exception {
        ImageRegistry images = ImageRegistry.load(YamlConfiguration.loadConfiguration(new File(RES, "images.yml")),
                new File(RES, "resourcepack/assets/aincrad/textures"), null);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.load(new File(RES, "effects/exemplos.yml"));
        assertTrue(cfg.getKeys(false).containsAll(List.of("teste", "boas_vindas", "subir_nivel", "aincrad_estilhacos", "menu_teste")));
        for (String id : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(id);
            assertNotNull(sec, id);
            if (sec.contains("publico")) {
                assertNotNull(Audience.parse(sec.getString("publico"), null), id + ": publico");
            }
            List<Map<?, ?>> steps = sec.getMapList("passos");
            assertFalse(steps.isEmpty(), id);
            for (Map<?, ?> step : steps) {
                String tipo = String.valueOf(step.get("tipo"));
                assertTrue(StepFactory.TYPES.contains(tipo), id + ": tipo " + tipo);
                for (String key : List.of("imagem", "fundo")) {
                    if (step.containsKey(key)) {
                        assertNotNull(images.get(String.valueOf(step.get(key))), id + ": " + key);
                    }
                }
            }
        }
    }

    @Test
    void gatilhosApontamParaEfeitosQueExistem() throws Exception {
        YamlConfiguration fx = new YamlConfiguration();
        fx.load(new File(RES, "effects/exemplos.yml"));
        YamlConfiguration tr = YamlConfiguration.loadConfiguration(new File(RES, "triggers.yml"));
        ConfigurationSection g = tr.getConfigurationSection("gatilhos");
        assertNotNull(g);
        for (String id : g.getKeys(false)) {
            assertTrue(fx.contains(g.getString(id + ".efeito")), id);
        }
    }
}
