package com.aincrad.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Um YAML de plugin com valores padrão vindos do .jar. Ao carregar, chaves novas que uma versão
 * nova trouxe são acrescentadas ao arquivo do dono, sem mexer no que ele editou.
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final String name;
    private YamlConfiguration yaml;
    private List<String> added = List.of();

    public ConfigFile(Plugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = defaults();
        added = defaults == null ? List.of() : merge(cfg, defaults);
        if (defaults != null) {
            cfg.setDefaults(defaults);
        }
        if (!added.isEmpty()) {
            try {
                cfg.save(file);
                plugin.getLogger().info(name + ": chaves novas adicionadas: " + String.join(", ", added));
            } catch (IOException e) {
                plugin.getLogger().warning("Não foi possível atualizar " + name + ": " + e.getMessage());
            }
        }
        this.yaml = cfg;
    }

    private YamlConfiguration defaults() {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return null;
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(r);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível ler o " + name + " padrão: " + e.getMessage());
            return null;
        }
    }

    /**
     * Acrescenta em {@code target} as chaves-folha de {@code defaults} que faltam (com os
     * comentários do padrão). Não sobrescreve nada. @return as chaves adicionadas
     */
    public static List<String> merge(YamlConfiguration target, YamlConfiguration defaults) {
        List<String> out = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || target.isSet(key)) {
                continue;
            }
            // Uma seção inteira que o dono apagou de propósito (vira valor não-seção) não é recriada.
            String parent = key.contains(".") ? key.substring(0, key.lastIndexOf('.')) : null;
            if (parent != null && target.isSet(parent) && !target.isConfigurationSection(parent)) {
                continue;
            }
            target.set(key, defaults.get(key));
            target.setComments(key, defaults.getComments(key));
            out.add(key);
        }
        return out;
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public List<String> addedKeys() {
        return added;
    }

    // ------------------------------------------------------------------ leitura com validação

    public String string(String path, String def) {
        return yaml.getString(path, def);
    }

    public boolean bool(String path, boolean def) {
        return yaml.getBoolean(path, def);
    }

    /** Inteiro entre {@code min} e {@code max}; fora disso avisa e usa o limite. */
    public int integer(String path, int def, int min, int max) {
        int v = yaml.getInt(path, def);
        if (v < min || v > max) {
            warn(path, v, min, max);
            return Math.max(min, Math.min(max, v));
        }
        return v;
    }

    public double decimal(String path, double def, double min, double max) {
        double v = yaml.getDouble(path, def);
        if (v < min || v > max) {
            warn(path, v, min, max);
            return Math.max(min, Math.min(max, v));
        }
        return v;
    }

    /** Um valor de enum, sem diferenciar maiúsculas; inválido avisa e usa o padrão. */
    public <E extends Enum<E>> E enumValue(String path, Class<E> type, E def) {
        String raw = yaml.getString(path);
        if (raw == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log().warning(name + ": \"" + path + "\" inválido (" + raw + "), usando " + def);
            return def;
        }
    }

    private void warn(String path, Object v, Object min, Object max) {
        log().warning(name + ": \"" + path + "\" = " + v + " fora de " + min + "–" + max + "; usando o limite.");
    }

    private Logger log() {
        return plugin.getLogger();
    }
}
