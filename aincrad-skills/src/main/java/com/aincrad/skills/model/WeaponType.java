package com.aincrad.skills.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Os tipos de arma, cada um com proficiência própria. */
public enum WeaponType {
    ESPADA, FLORETE, ADAGA, MACHADO, LANCA, DUAS_MAOS,
    /** Só para quem tem a skill única: uma espada em cada mão. */
    DUAS_ESPADAS;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WeaponType parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Como reconhecer as armas de um tipo: pelo nome do item e pelo material. */
    public record Match(List<String> names, List<String> materials) {
    }

    /**
     * O tipo de arma de um item. O nome vale mais que o material: uma IRON_SWORD chamada
     * "Florete de Ferro" é um florete. Null se não é arma.
     *
     * @param material    nome do material ({@code IRON_SWORD})
     * @param displayName nome do item sem cores, ou null
     */
    public static WeaponType detect(String material, String displayName, Map<WeaponType, Match> rules) {
        if (material == null) {
            return null;
        }
        if (displayName != null && !displayName.isBlank()) {
            String n = displayName.toLowerCase(Locale.ROOT);
            WeaponType best = null;
            int bestLen = 0;
            for (var e : rules.entrySet()) {
                for (String word : e.getValue().names()) {
                    // A palavra mais comprida ganha: "espada de duas mãos" antes de "espada".
                    if (!word.isBlank() && n.contains(word.toLowerCase(Locale.ROOT)) && word.length() > bestLen) {
                        best = e.getKey();
                        bestLen = word.length();
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        String m = material.toUpperCase(Locale.ROOT);
        for (var e : rules.entrySet()) {
            for (String pattern : e.getValue().materials()) {
                String p = pattern.toUpperCase(Locale.ROOT);
                if (p.startsWith("*") ? m.endsWith(p.substring(1)) : m.equals(p)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }
}
