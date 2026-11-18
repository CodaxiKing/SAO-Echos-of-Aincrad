package com.aincrad.quests.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Uma quest, lida de {@code quests/*.yml}.
 *
 * @param npc      quem dá a quest e a quem ela é entregue
 * @param party    o progresso de matar e de chefe vale para a party inteira
 * @param minLevel level mínimo (0 = qualquer)
 * @param floor    andar que precisa estar liberado (0 = qualquer)
 * @param after    quest que precisa estar concluída antes (ou null)
 */
public record QuestDef(String id, String name, String description, Kind kind, boolean party, String npc,
                       int minLevel, int floor, String after, List<Stage> stages, Reward reward) {

    /** História (uma vez), diária e semanal (renovam). */
    public enum Kind { HISTORIA, DIARIA, SEMANAL }

    /** O que uma etapa pede. */
    public enum Goal {
        /** Matar mobs: {@code alvo} é o tipo ({@code WOLF}) ou o mob do spawns.yml ({@code mythic:Lobo}). */
        MATAR,
        /** Ter os itens no inventário (não são tirados). */
        COLETAR,
        /** Levar os itens ao NPC (são tirados). */
        ENTREGAR,
        /** Falar com um NPC. */
        FALAR,
        /** Entrar numa região do core. */
        CHEGAR,
        /** Participar da vitória sobre um chefe. */
        CHEFE,
        /** Pescar (AincradLife). */
        PESCAR,
        /** Cozinhar (AincradLife). */
        COZINHAR
    }

    /**
     * Uma etapa.
     *
     * @param target mob, item, NPC, região ou chefe, conforme o objetivo; null = qualquer
     */
    public record Stage(Goal goal, String target, int amount, String text) {

        /** O acontecimento conta para esta etapa? */
        public boolean matches(Goal what, String... targets) {
            if (goal != what) {
                return false;
            }
            if (target == null) {
                return true;
            }
            for (String t : targets) {
                if (t != null && target.equalsIgnoreCase(t)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A recompensa.
     *
     * @param items    {@code "BREAD 3"} ou {@code "rpg:espada_de_ferro"}
     * @param commands comandos do console, com {@code <jogador>}
     */
    public record Reward(long xp, long col, List<String> items, String title, List<String> commands) {
    }

    private static final Map<String, Goal> GOALS = Map.of("matar", Goal.MATAR, "coletar", Goal.COLETAR, "entregar", Goal.ENTREGAR,
            "falar", Goal.FALAR, "chegar", Goal.CHEGAR, "chefe", Goal.CHEFE, "pescar", Goal.PESCAR, "cozinhar", Goal.COZINHAR);

    /** Lê as quests de um arquivo. As com erro ficam de fora e entram em {@code errors}. */
    public static Map<String, QuestDef> parse(ConfigurationSection root, String file, List<String> errors) {
        Map<String, QuestDef> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            String where = file + ": " + key;
            if (s == null) {
                errors.add(where + ": não é uma seção");
                continue;
            }
            Kind kind;
            try {
                kind = Kind.valueOf(s.getString("tipo", "historia").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(where + ": 'tipo' precisa ser historia, diaria ou semanal");
                continue;
            }
            String npc = s.getString("npc");
            if (npc == null || npc.isBlank()) {
                errors.add(where + ": 'npc' faltando (quem dá a quest)");
                continue;
            }
            List<Stage> stages = new ArrayList<>();
            boolean bad = false;
            int n = 0;
            for (Map<?, ?> m : s.getMapList("etapas")) {
                n++;
                Object rawGoal = m.get("tipo");
                Goal goal = rawGoal == null ? null : GOALS.get(rawGoal.toString().toLowerCase(Locale.ROOT));
                if (goal == null) {
                    errors.add(where + ", etapa " + n + ": 'tipo' desconhecido (" + rawGoal + ")");
                    bad = true;
                    break;
                }
                String target = first(m, "alvo", "item", "npc", "regiao");
                if (target == null && (goal == Goal.COLETAR || goal == Goal.ENTREGAR || goal == Goal.FALAR || goal == Goal.CHEGAR)) {
                    errors.add(where + ", etapa " + n + ": falta dizer o item, o npc ou a regiao");
                    bad = true;
                    break;
                }
                int amount = m.get("quantidade") instanceof Number q ? q.intValue() : 1;
                if (amount < 1 || amount > 100_000) {
                    errors.add(where + ", etapa " + n + ": 'quantidade' fora do limite");
                    bad = true;
                    break;
                }
                if (goal == Goal.FALAR || goal == Goal.CHEGAR) {
                    amount = 1;
                }
                Object text = m.get("texto");
                stages.add(new Stage(goal, target, amount, text == null ? "" : text.toString()));
            }
            if (bad) {
                continue;
            }
            if (stages.isEmpty()) {
                errors.add(where + ": nenhuma etapa em 'etapas'");
                continue;
            }
            String after = s.getString("requisitos.quest");
            String title = s.getString("recompensas.titulo");
            out.put(key.toLowerCase(Locale.ROOT), new QuestDef(key.toLowerCase(Locale.ROOT), s.getString("nome", key),
                    s.getString("descricao", ""), kind, s.getBoolean("party", false), npc.toLowerCase(Locale.ROOT),
                    Math.max(0, s.getInt("requisitos.level", 0)), Math.max(0, s.getInt("requisitos.andar", 0)),
                    after == null || after.isBlank() ? null : after.toLowerCase(Locale.ROOT), List.copyOf(stages),
                    new Reward(Math.max(0, s.getLong("recompensas.xp", 0)), Math.max(0, s.getLong("recompensas.col", 0)),
                            List.copyOf(s.getStringList("recompensas.itens")), title == null || title.isBlank() ? null : title,
                            List.copyOf(s.getStringList("recompensas.comandos")))));
        }
        return out;
    }

    private static String first(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return null;
    }
}
