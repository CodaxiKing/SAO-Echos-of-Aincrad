package com.aincrad.effects.engine;

import com.aincrad.effects.image.ImageRegistry;
import com.aincrad.effects.step.Step;
import com.aincrad.effects.step.StepFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê os efeitos de effects/*.yml. Cada chave de primeiro nível de um arquivo é um efeito:
 * <pre>
 * meu_efeito:
 *   publico: jogador      # jogador | raio:16 | mundo | servidor
 *   passos:
 *     - { em: 0, tipo: som, som: minecraft:entity.player.levelup }
 * </pre>
 * Um efeito com erro é pulado; os erros voltam em {@link Result#errors()}.
 */
public final class EffectLoader {

    public record Result(Map<String, Effect> effects, List<String> errors) {
    }

    private EffectLoader() {
    }

    public static Result loadFolder(File folder, ImageRegistry images) {
        Map<String, Effect> effects = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return new Result(effects, errors);
        }
        Arrays.sort(files);
        for (File f : files) {
            YamlConfiguration cfg = new YamlConfiguration();
            try {
                cfg.load(f);
            } catch (Exception e) {
                errors.add(f.getName() + ": YAML inválido — " + e.getMessage());
                continue;
            }
            load(cfg, f.getName(), images, effects, errors);
        }
        return new Result(effects, errors);
    }

    public static void load(ConfigurationSection cfg, String source, ImageRegistry images,
                            Map<String, Effect> into, List<String> errors) {
        for (String id : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(id);
            if (sec == null) {
                errors.add(source + ": \"" + id + "\" não é um efeito (falta a lista de passos)");
                continue;
            }
            if (into.containsKey(id)) {
                errors.add(source + ": efeito \"" + id + "\" repetido (já existe em " + into.get(id).source() + ")");
                continue;
            }
            try {
                into.put(id, parse(id, sec, source, images));
            } catch (EffectParseException e) {
                errors.add(e.getMessage());
            }
        }
    }

    static Effect parse(String id, ConfigurationSection sec, String source, ImageRegistry images) {
        String where = source + " → " + id;
        Audience aud = null;
        if (sec.contains("publico")) {
            aud = Audience.parse(sec.getString("publico"), null);
            if (aud == null) {
                throw new EffectParseException(where + ": publico inválido \"" + sec.getString("publico")
                        + "\" (use jogador, raio:<n>, mundo ou servidor)");
            }
        }
        List<Map<?, ?>> raw = sec.getMapList("passos");
        if (raw.isEmpty()) {
            throw new EffectParseException(where + ": sem passos");
        }
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            steps.add(StepFactory.create(Spec.of(raw.get(i), where + " passo " + (i + 1)), images));
        }
        return new Effect(id, aud, List.copyOf(steps), source);
    }
}
