package com.aincrad.effects.engine;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Leitura tolerante de um passo do YAML, com mensagens de erro que dizem onde está o problema. */
public final class Spec {

    private final ConfigurationSection s;
    private final String where;

    public Spec(ConfigurationSection s, String where) {
        this.s = s;
        this.where = where;
    }

    public static Spec of(Map<?, ?> map, String where) {
        MemoryConfiguration root = new MemoryConfiguration();
        return new Spec(root.createSection("passo", map), where);
    }

    public String where() {
        return where;
    }

    public boolean has(String key) {
        return s.contains(key);
    }

    public String str(String key, String def) {
        Object v = s.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public String required(String key) {
        String v = str(key, null);
        if (v == null || v.isBlank()) {
            throw new EffectParseException(where + ": falta \"" + key + "\"");
        }
        return v;
    }

    public double dbl(String key, double def) {
        Object v = s.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new EffectParseException(where + ": \"" + key + "\" precisa ser um número (veio \"" + v + "\")");
        }
    }

    public int integer(String key, int def) {
        return (int) Math.round(dbl(key, def));
    }

    public boolean bool(String key, boolean def) {
        Object v = s.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String t = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return t.equals("true") || t.equals("sim") || t.equals("yes");
    }

    /** Segundos no YAML → ticks (20 por segundo). */
    public int ticks(String key, double defSeconds) {
        return toTicks(dbl(key, defSeconds));
    }

    public static int toTicks(double seconds) {
        return Math.max(0, (int) Math.round(seconds * 20.0));
    }

    /** Lista [x, y, z] ou texto "x, y, z". */
    public Vector vector(String key, Vector def) {
        Object v = s.get(key);
        if (v == null) {
            return def.clone();
        }
        List<Double> nums = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                nums.add(o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o).trim()));
            }
        } else {
            for (String part : String.valueOf(v).split("[,;\\s]+")) {
                if (!part.isBlank()) {
                    nums.add(Double.parseDouble(part.trim()));
                }
            }
        }
        if (nums.size() != 3) {
            throw new EffectParseException(where + ": \"" + key + "\" precisa de 3 números [x, y, z]");
        }
        return new Vector(nums.get(0), nums.get(1), nums.get(2));
    }

    public Color color(String key, Color def) {
        String v = str(key, null);
        return v == null ? def : parseColor(v, where + ": cor \"" + key + "\"");
    }

    public List<Color> colors(String key, List<Color> def) {
        Object v = s.get(key);
        if (v == null) {
            return def;
        }
        List<Color> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                out.add(parseColor(String.valueOf(o), where + ": cores \"" + key + "\""));
            }
        } else {
            out.add(parseColor(String.valueOf(v), where + ": cor \"" + key + "\""));
        }
        return out;
    }

    public static Color parseColor(String raw, String where) {
        String h = raw.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        try {
            if (h.length() != 6) {
                throw new NumberFormatException();
            }
            return Color.fromRGB(Integer.parseInt(h, 16));
        } catch (NumberFormatException e) {
            throw new EffectParseException(where + " inválida (use #RRGGBB): " + raw);
        }
    }

    public List<String> strings(String key) {
        Object v = s.get(key);
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(v));
    }

    public List<Map<?, ?>> maps(String key) {
        return s.getMapList(key);
    }
}
