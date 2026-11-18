package com.aincrad.skills.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * As skills únicas e os critérios secretos delas (unique-skills.yml). Cada uma vai para o primeiro
 * jogador que cumprir o critério; o critério não aparece em lugar nenhum.
 */
public final class UniqueRules {

    private UniqueRules() {
    }

    /** O tipo de critério. */
    public enum Kind {
        /** Só o admin dá. */
        MANUAL,
        /** O vencedor do teste de reação. */
        REACAO,
        /** Golpes aparados com a passiva Aparar. */
        APARADOS,
        /** Golpes acertados com uma arma. */
        GOLPES,
        /** Sword Skills usadas. */
        SKILLS,
        /** Proficiência numa arma. */
        PROFICIENCIA
    }

    /** Os contadores que o plugin guarda por jogador. */
    public static final String PARRIES = "aparados";
    public static final String HITS = "golpes";
    public static final String CASTS = "skills";

    /**
     * Um critério.
     *
     * @param weapon só para {@link Kind#PROFICIENCIA}
     */
    public record Criterion(Kind kind, WeaponType weapon, long amount) {

        /** O jogador cumpre o critério? (os manuais e o de reação nunca se cumprem sozinhos) */
        public boolean met(ToLongFunction<String> counters, ToIntFunction<WeaponType> proficiency) {
            return switch (kind) {
                case MANUAL, REACAO -> false;
                case APARADOS -> counters.applyAsLong(PARRIES) >= amount;
                case GOLPES -> counters.applyAsLong(HITS) >= amount;
                case SKILLS -> counters.applyAsLong(CASTS) >= amount;
                case PROFICIENCIA -> proficiency.applyAsInt(weapon) >= amount;
            };
        }
    }

    /** Uma skill única. */
    public record Def(String id, String name, Criterion criterion) {
    }

    /**
     * Lê um critério: {@code manual}, {@code reacao}, {@code aparados:300}, {@code golpes:5000},
     * {@code skills:1000} ou {@code proficiencia:florete:1000}. Null se inválido.
     */
    public static Criterion criterion(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Criterion(Kind.MANUAL, null, 0);
        }
        String[] p = raw.trim().toLowerCase(Locale.ROOT).split(":");
        try {
            return switch (p[0]) {
                case "manual" -> p.length == 1 ? new Criterion(Kind.MANUAL, null, 0) : null;
                case "reacao" -> p.length == 1 ? new Criterion(Kind.REACAO, null, 0) : null;
                case "aparados" -> counter(Kind.APARADOS, p);
                case "golpes" -> counter(Kind.GOLPES, p);
                case "skills" -> counter(Kind.SKILLS, p);
                case "proficiencia" -> {
                    WeaponType w = p.length == 3 ? WeaponType.parse(p[1]) : null;
                    long n = w == null ? 0 : Long.parseLong(p[2]);
                    yield w == null || n < 1 || n > SkillRules.MAX_PROF ? null : new Criterion(Kind.PROFICIENCIA, w, n);
                }
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Criterion counter(Kind kind, String[] p) {
        long n = p.length == 2 ? Long.parseLong(p[1]) : 0;
        return n < 1 ? null : new Criterion(kind, null, n);
    }

    /** Lê a seção {@code skills} do unique-skills.yml. As com erro ficam de fora e entram em {@code errors}. */
    public static Map<String, Def> parse(ConfigurationSection root, List<String> errors) {
        Map<String, Def> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            String id = key.toLowerCase(Locale.ROOT);
            Criterion c = s == null ? null : criterion(s.getString("criterio"));
            if (c == null || !id.matches("[a-z0-9_]{1,32}")) {
                errors.add(key + ": 'criterio' inválido (manual, reacao, aparados:N, golpes:N, skills:N, proficiencia:arma:N) ou id com caracteres estranhos");
                continue;
            }
            out.put(id, new Def(id, s.getString("nome", key), c));
        }
        return out;
    }
}
