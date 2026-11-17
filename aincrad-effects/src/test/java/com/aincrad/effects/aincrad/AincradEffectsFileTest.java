package com.aincrad.effects.aincrad;

import com.aincrad.effects.engine.Audience;
import com.aincrad.effects.image.ImageRegistry;
import com.aincrad.effects.step.StepFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Os arquivos da integração com o Aincrad que vêm no .jar apontam para coisas que existem. */
class AincradEffectsFileTest {

    private static final File RES = new File("src/main/resources");

    private static ImageRegistry images() {
        return ImageRegistry.load(YamlConfiguration.loadConfiguration(new File(RES, "images.yml")),
                new File(RES, "resourcepack/assets/aincrad/textures"), null);
    }

    @Test
    void efeitosDoAincradUsamTiposEImagensValidos() throws Exception {
        ImageRegistry images = images();
        YamlConfiguration fx = new YamlConfiguration();
        fx.load(new File(RES, "effects/aincrad.yml"));
        assertFalse(fx.getKeys(false).isEmpty());
        for (String id : fx.getKeys(false)) {
            ConfigurationSection sec = fx.getConfigurationSection(id);
            assertNotNull(sec, id);
            assertTrue(id.startsWith("aincrad_"), id + ": os efeitos da integração começam com aincrad_");
            if (sec.contains("publico")) {
                assertNotNull(Audience.parse(sec.getString("publico"), null), id + ": publico");
            }
            List<Map<?, ?>> steps = sec.getMapList("passos");
            assertFalse(steps.isEmpty(), id);
            for (Map<?, ?> step : steps) {
                assertTrue(StepFactory.TYPES.contains(String.valueOf(step.get("tipo"))), id + ": tipo " + step.get("tipo"));
                if (step.containsKey("imagem")) {
                    assertNotNull(images.get(String.valueOf(step.get("imagem"))), id + ": imagem " + step.get("imagem"));
                }
            }
        }
    }

    @Test
    void configDaIntegracaoApontaParaEfeitosEImagensQueExistem() throws Exception {
        ImageRegistry images = images();
        YamlConfiguration fx = new YamlConfiguration();
        fx.load(new File(RES, "effects/aincrad.yml"));
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.load(new File(RES, "aincrad.yml"));
        ConfigurationSection map = cfg.getConfigurationSection("efeitos");
        assertNotNull(map);
        for (String key : List.of("floor_cleared", "chegada_andar", "gate", "chefe_entrada", "dano", "dano_critico", "fase", "congratulations")) {
            assertTrue(fx.contains(map.getString(key, "?")), "efeitos." + key + " → " + map.getString(key));
        }
        assertNotNull(images.get(cfg.getString("janela_resultado.fundo")));
        // As imagens citadas com <img:id> nos textos da placa existem.
        Pattern img = Pattern.compile("<img:([a-z0-9_]+)>");
        for (String key : List.of("placa.texto", "placa.texto_derrotado")) {
            Matcher m = img.matcher(cfg.getString(key, ""));
            assertTrue(m.find(), key + " mostra a imagem da placa");
            assertNotNull(images.get(m.group(1)), key + ": imagem " + m.group(1));
        }
    }
}
