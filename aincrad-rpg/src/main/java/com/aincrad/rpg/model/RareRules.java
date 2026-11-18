package com.aincrad.rpg.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Regras dos mobs raros de andar (rare-mobs.yml): leitura do arquivo e o sorteio, sem servidor. */
public final class RareRules {

    private RareRules() {
    }

    /**
     * Um mob raro.
     *
     * @param region   região do core onde ele pode aparecer
     * @param chance   chance por rodada, por jogador na região
     * @param weather  só com este clima ("nevoa"), ou null
     * @param night    só de noite (true), só de dia (false), ou null
     * @param flees    foge do jogador
     * @param drop     {@code MATERIAL}, {@code rpg:<id>} ou {@code life:<ingrediente>}
     * @param lifeMs   quanto tempo fica no mundo se ninguém o pegar
     */
    public record Def(String id, SpawnRules.MobRef mob, String region, double chance, String weather, Boolean night,
                      int minLevel, int maxLevel, boolean flees, String name, String drop, int dropAmount, long lifeMs) {
    }

    /** Lê {@code periodo}: "dia", "noite" ou vazio. Devolve {null} para "qualquer" e null para inválido. */
    public static Boolean[] period(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Boolean[] {null};
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "noite" -> new Boolean[] {true};
            case "dia" -> new Boolean[] {false};
            default -> null;
        };
    }

    /** O clima e o período de agora servem? Null = tanto faz. */
    public static boolean matches(String wantWeather, Boolean wantNight, String weather, boolean night) {
        return (wantWeather == null || wantWeather.equalsIgnoreCase(weather)) && (wantNight == null || wantNight == night);
    }

    /** O mob aparece nesta rodada? */
    public static boolean appears(double chance, double roll) {
        return roll < chance;
    }

    /** Lê a seção {@code mobs}. Os com erro ficam de fora e entram em {@code errors}. */
    public static Map<String, Def> parse(ConfigurationSection root, List<String> errors) {
        Map<String, Def> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                errors.add(key + ": não é uma seção");
                continue;
            }
            SpawnRules.MobRef mob = SpawnRules.MobRef.parse(s.getString("mob"));
            String region = s.getString("regiao");
            int[] lv = SpawnRules.levels(s.get("level"));
            Boolean[] night = period(s.getString("periodo"));
            double chance = s.getDouble("chance", 0);
            if (mob == null || region == null || region.isBlank() || lv == null || night == null || chance <= 0 || chance > 1) {
                errors.add(key + ": confira 'mob', 'regiao', 'level', 'periodo' (dia ou noite) e 'chance' (entre 0 e 1)");
                continue;
            }
            String weather = s.getString("clima");
            String drop = s.getString("drop");
            out.put(key.toLowerCase(Locale.ROOT), new Def(key.toLowerCase(Locale.ROOT), mob, region.toLowerCase(Locale.ROOT), chance,
                    weather == null || weather.isBlank() ? null : weather.toLowerCase(Locale.ROOT), night[0], lv[0], lv[1],
                    s.getBoolean("foge", true), s.getString("nome", ""), drop == null || drop.isBlank() ? null : drop.trim(),
                    Math.max(1, Math.min(64, s.getInt("drop_quantidade", 1))),
                    Math.max(10, s.getInt("some_em_segundos", 120)) * 1000L));
        }
        return out;
    }
}
