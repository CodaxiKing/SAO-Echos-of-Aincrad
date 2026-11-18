package com.aincrad.floors.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Os andares de floors.yml, validados. Um andar com erro fica de fora e o erro vai para
 * {@link #errors()} (o plugin mostra no console).
 */
public final class FloorRegistry {

    private final Map<Integer, Floor> floors = new TreeMap<>();
    private final List<String> errors = new ArrayList<>();
    private final Set<String> cities = new HashSet<>();

    /** Lê a seção "andares" de floors.yml. */
    public static FloorRegistry load(ConfigurationSection root) {
        FloorRegistry reg = new FloorRegistry();
        if (root == null) {
            reg.errors.add("floors.yml não tem a seção \"andares\".");
            return reg;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            int n;
            try {
                n = Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                reg.errors.add("andares." + key + ": a chave precisa ser o número do andar.");
                continue;
            }
            if (s == null || n < 1) {
                reg.errors.add("andares." + key + ": andar inválido (use números a partir de 1, com os campos embaixo).");
                continue;
            }
            String type = s.getString("tipo", "mundo").toLowerCase(Locale.ROOT);
            String world = blank(s.getString("mundo"));
            String region = blank(s.getString("regiao"));
            if (type.equals("mundo")) {
                region = null;
                if (world == null) {
                    reg.errors.add("andares." + key + ": tipo \"mundo\" precisa do campo \"mundo\".");
                    continue;
                }
            } else if (type.equals("regiao")) {
                world = null;
                if (region == null) {
                    reg.errors.add("andares." + key + ": tipo \"regiao\" precisa do campo \"regiao\".");
                    continue;
                }
                region = region.toLowerCase(Locale.ROOT);
            } else {
                reg.errors.add("andares." + key + ": tipo \"" + type + "\" não existe (use mundo ou regiao).");
                continue;
            }
            String spawn = blank(s.getString("chegada"));
            if (spawn != null && !validSpawn(spawn)) {
                reg.errors.add("andares." + key + ": \"chegada\" precisa ser \"mundo,x,y,z\" ou \"mundo,x,y,z,yaw,pitch\".");
                spawn = null;
            }
            String city = blank(s.getString("cidade"));
            // Outras zonas seguras do andar (vilas), além da cidade principal.
            for (String v : s.getStringList("vilas")) {
                if (!v.isBlank()) {
                    reg.cities.add(v.trim().toLowerCase(Locale.ROOT));
                }
            }
            reg.floors.put(n, new Floor(n, s.getString("nome", "Andar " + n), s.getString("servidor", ""), world, region, spawn,
                    city == null ? null : city.toLowerCase(Locale.ROOT)));
        }
        // Os números não podem ter buraco: a linha de frente sobe de um em um.
        int expected = 1;
        for (int n : reg.floors.keySet()) {
            if (n != expected) {
                reg.errors.add("andares: falta o andar " + expected + " (os números vão de 1 em diante, sem pular).");
                break;
            }
            expected++;
        }
        for (Floor f : reg.floors.values()) {
            if (f.city() != null) {
                reg.cities.add(f.city());
            }
        }
        return reg;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static boolean validSpawn(String s) {
        String[] p = s.split(",");
        if (p.length != 4 && p.length != 6) {
            return false;
        }
        try {
            for (int i = 1; i < p.length; i++) {
                Double.parseDouble(p[i].trim());
            }
            return !p[0].isBlank();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Floor get(int number) {
        return floors.get(number);
    }

    public Collection<Floor> all() {
        return Collections.unmodifiableCollection(floors.values());
    }

    /** O número do andar mais alto do arquivo (0 se não há nenhum). */
    public int lastFloor() {
        int max = 0;
        for (Floor f : all()) {
            max = Math.max(max, f.number());
        }
        return max;
    }

    public int size() {
        return floors.size();
    }

    public List<String> errors() {
        return errors;
    }

    /** Nomes das regiões "cidade" (zonas seguras). */
    public Set<String> cities() {
        return cities;
    }

    /** Troca o andar (ex.: ponto de chegada novo). */
    public void put(Floor f) {
        floors.put(f.number(), f);
    }
}
