package com.aincrad.bosses.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Todos os chefes da pasta bosses/ (cada arquivo .yml pode ter vários; a chave é o id). */
public final class BossRegistry {

    private final Map<String, BossDef> bosses = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    public static BossRegistry load(File folder) {
        BossRegistry reg = new BossRegistry();
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return reg;
        }
        Arrays.sort(files);
        for (File f : files) {
            reg.add(YamlConfiguration.loadConfiguration(f), f.getName());
        }
        return reg;
    }

    /** Lê os chefes de um YAML já carregado ({@code source} só aparece nas mensagens de erro). */
    public void add(ConfigurationSection yaml, String source) {
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String key = id.toLowerCase(Locale.ROOT);
            if (bosses.containsKey(key)) {
                errors.add(source + ": o chefe " + key + " já existe em outro arquivo.");
                continue;
            }
            List<String> errs = new ArrayList<>();
            BossDef def = BossDef.parse(key, s, errs);
            errs.forEach(e -> errors.add(source + ": " + e));
            if (def != null) {
                bosses.put(key, def);
            }
        }
    }

    public BossDef get(String id) {
        return id == null ? null : bosses.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<BossDef> all() {
        return Collections.unmodifiableCollection(bosses.values());
    }

    public List<String> errors() {
        return errors;
    }
}
