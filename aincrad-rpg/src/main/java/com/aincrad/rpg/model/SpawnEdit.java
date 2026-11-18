package com.aincrad.rpg.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mexe no spawns.yml sem abrir o arquivo: é o que os comandos {@code /aincrad spawn} e o menu
 * usam. Só altera o YAML em memória; quem chama grava e recarrega. Sem servidor, para testar.
 */
public final class SpawnEdit {

    private SpawnEdit() {
    }

    /** A chave da região como está no arquivo (sem diferenciar maiúsculas), ou null. */
    public static String regionKey(YamlConfiguration y, String region) {
        ConfigurationSection all = y.getConfigurationSection("regioes");
        if (all == null || region == null) {
            return null;
        }
        for (String k : all.getKeys(false)) {
            if (k.equalsIgnoreCase(region)) {
                return k;
            }
        }
        return null;
    }

    /** Põe a região no arquivo, com os números padrão e sem mobs. @return false se já estava */
    public static boolean addRegion(YamlConfiguration y, String region) {
        if (regionKey(y, region) != null) {
            return false;
        }
        String b = "regioes." + region.toLowerCase(Locale.ROOT) + ".";
        y.set(b + "maximo", 12);
        y.set(b + "por_vez", 2);
        y.set(b + "raio", 40);
        y.set(b + "distancia_minima", 10);
        y.set(b + "sumir_longe", 72);
        y.set(b + "spawn_natural", false);
        y.set(b + "mobs", new ArrayList<>());
        return true;
    }

    public static boolean removeRegion(YamlConfiguration y, String region) {
        String k = regionKey(y, region);
        if (k == null) {
            return false;
        }
        y.set("regioes." + k, null);
        return true;
    }

    /** Muda um número da região (maximo, por_vez, raio, distancia_minima, sumir_longe). */
    public static boolean setRegion(YamlConfiguration y, String region, String field, Object value) {
        String k = regionKey(y, region);
        if (k == null) {
            return false;
        }
        y.set("regioes." + k + "." + field, value);
        return true;
    }

    /** As linhas de mob da região, numa cópia que pode ser alterada. Vazio se a região não existe. */
    public static List<Map<String, Object>> lines(YamlConfiguration y, String region) {
        List<Map<String, Object>> out = new ArrayList<>();
        String k = regionKey(y, region);
        if (k == null) {
            return out;
        }
        for (Map<?, ?> m : y.getMapList("regioes." + k + ".mobs")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((a, b) -> copy.put(String.valueOf(a), b));
            out.add(copy);
        }
        return out;
    }

    private static void write(YamlConfiguration y, String region, List<Map<String, Object>> lines) {
        y.set("regioes." + regionKey(y, region) + ".mobs", lines);
    }

    /** Como a linha é conhecida: o {@code id}, ou o próprio mob. */
    public static String key(Map<String, Object> line) {
        Object id = line.get("id");
        return id != null && !id.toString().isBlank() ? id.toString() : String.valueOf(line.get("mob"));
    }

    /** A posição da linha com este id (ou este mob), ou -1. */
    public static int find(YamlConfiguration y, String region, String key) {
        List<Map<String, Object>> lines = lines(y, region);
        for (int i = 0; i < lines.size(); i++) {
            if (key(lines.get(i)).equalsIgnoreCase(key)) {
                return i;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            if (String.valueOf(lines.get(i).get("mob")).equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }

    /** {@code 3} quando os dois são iguais, {@code "2-4"} quando não. */
    public static Object levelValue(int min, int max) {
        return min >= max ? (Object) min : min + "-" + max;
    }

    /** Um id que ainda não existe na região: "zombie", "zombie_2", "zombie_3"... */
    static String uniqueId(List<Map<String, Object>> lines, String mob) {
        String base = mob.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "mob";
        }
        String id = base;
        int n = 1;
        while (true) {
            String candidate = id;
            if (lines.stream().noneMatch(l -> key(l).equalsIgnoreCase(candidate))) {
                return id;
            }
            id = base + "_" + (++n);
        }
    }

    /**
     * Acrescenta um mob à região.
     *
     * @param mob ZOMBIE ou mythic:Nome
     * @return a posição da linha nova, ou -1 se a região não existe
     */
    public static int addMob(YamlConfiguration y, String region, String mob, int min, int max, int weight) {
        if (regionKey(y, region) == null) {
            return -1;
        }
        List<Map<String, Object>> lines = lines(y, region);
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("mob", mob);
        line.put("id", uniqueId(lines, mob.contains(":") ? mob.substring(mob.indexOf(':') + 1) : mob));
        line.put("peso", Math.max(1, weight));
        line.put("level", levelValue(Math.max(1, min), Math.max(1, max)));
        lines.add(line);
        write(y, region, lines);
        return lines.size() - 1;
    }

    /** Muda (ou, com {@code value} null, tira) um campo de uma linha. */
    public static boolean set(YamlConfiguration y, String region, int index, String field, Object value) {
        List<Map<String, Object>> lines = lines(y, region);
        if (index < 0 || index >= lines.size()) {
            return false;
        }
        if (value == null) {
            lines.get(index).remove(field);
        } else {
            lines.get(index).put(field, value);
        }
        write(y, region, lines);
        return true;
    }

    public static boolean removeMob(YamlConfiguration y, String region, int index) {
        List<Map<String, Object>> lines = lines(y, region);
        if (index < 0 || index >= lines.size()) {
            return false;
        }
        lines.remove(index);
        write(y, region, lines);
        return true;
    }

    /** O equipamento da linha (espaço → item), numa cópia que pode ser alterada. */
    public static Map<String, Object> equipment(Map<String, Object> line) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (line.get("equipamento") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }

    /** Os drops da linha, numa cópia que pode ser alterada. */
    public static List<Map<String, Object>> drops(Map<String, Object> line) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (line.get("drops") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    out.add(copy);
                }
            }
        }
        return out;
    }

    /** A chance de um drop, de 0 a 1 (1 quando não está escrita). */
    public static double chance(Map<String, Object> drop) {
        return drop.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
    }

    /** A quantidade de um drop como {mín, máx}. */
    public static int[] amount(Map<String, Object> drop) {
        int[] a = SpawnRules.levels(drop.get("quantidade"));
        return a == null || a[0] < 1 ? new int[]{1, 1} : a;
    }

    /** O level de uma linha como {mín, máx}; {1, 1} se não tem ou está inválido. */
    public static int[] level(Map<String, Object> line) {
        int[] lv = SpawnRules.levels(line.get("level"));
        return lv == null || lv[0] < 1 ? new int[]{1, 1} : lv;
    }

    /** Soma {@code dMin} e {@code dMax} ao level, mantendo 1 ≤ mín ≤ máx ≤ 999. */
    public static int[] shift(int[] level, int dMin, int dMax) {
        int min = Math.max(1, Math.min(999, level[0] + dMin));
        int max = Math.max(1, Math.min(999, level[1] + dMax));
        if (dMin != 0 && min > max) {
            max = min;
        }
        if (dMax != 0 && max < min) {
            min = max;
        }
        return new int[]{min, max};
    }
}
