package com.aincrad.effects.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Carrega um YAML da pasta do plugin, criando-o a partir do .jar e usando o do .jar como padrão. */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    public static YamlConfiguration load(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    cfg.setDefaults(YamlConfiguration.loadConfiguration(reader));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Não foi possível ler o " + name + " padrão: " + e.getMessage());
        }
        return cfg;
    }
}
