package com.aincrad.bosses.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Um chefe lido de bosses/&lt;arquivo&gt;.yml. Só dados: materiais e mobs ficam como texto e são
 * conferidos quando o chefe nasce.
 *
 * @param equipment espaço (mao, mao_secundaria, cabeca, peito, pernas, pes) → material
 * @param phases    da vida mais alta para a mais baixa; a primeira vale desde o começo
 */
public record BossDef(String id, int floor, String name, int level, String mob, double health, double damage, double armor,
                      double speed, double knockbackResistance, double scale, Map<String, String> equipment,
                      int introSeconds, int timeLimitSeconds, double respawnHours, List<Phase> phases, Rewards rewards) {

    /** Tipos de habilidade. */
    public enum SkillType {
        /** Corre para cima do alvo e acerta quem estiver perto ao chegar. */
        INVESTIDA,
        /** Círculo de aviso no chão e, depois, dano em quem ficou dentro. */
        AREA,
        /** Chama lacaios. */
        LACAIOS,
        /** Atira no alvo. */
        PROJETIL,
        /** Pula no alvo e causa dano em volta ao cair. */
        SALTO,
        /** Mais dano por alguns segundos. */
        FURIA;

        public static SkillType parse(String s) {
            try {
                return valueOf(String.valueOf(s).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Uma habilidade. Campos que o tipo não usa ficam com o padrão.
     *
     * @param cooldown   recarga em segundos
     * @param damage     dano (investida, área, projétil, salto)
     * @param radius     raio em blocos (área, salto, investida)
     * @param warnTicks  ticks de aviso no chão antes do dano (área)
     * @param mob        mob dos lacaios; "flecha" ou "bola_de_fogo" no projétil
     * @param count      quantos lacaios
     * @param level      level dos lacaios
     * @param seconds    duração da fúria
     * @param multiplier multiplicador de dano da fúria
     * @param label      nome dos lacaios, ou a fala do chefe ao usar
     */
    public record Skill(SkillType type, double cooldown, double damage, double radius, int warnTicks, String mob, int count,
                        int level, double seconds, double multiplier, String label) {
    }

    /**
     * Uma fase.
     *
     * @param at          começa quando a vida fica igual ou abaixo deste percentual (100 = início)
     * @param damageTimes multiplica o dano do chefe nesta fase
     * @param speedTimes  multiplica a velocidade
     * @param equipment   troca só os espaços listados
     */
    public record Phase(double at, String name, String message, double damageTimes, double speedTimes,
                        Map<String, String> equipment, List<Skill> skills) {
    }

    /** Um item de recompensa: material ou "rpg:id[:raridade]". */
    public record Drop(String item, int amount) {
    }

    /**
     * @param minDamagePercent dano mínimo (em % da vida do chefe) para contar como participante
     * @param xp               XP do AincradRPG para cada participante
     * @param col              Col para cada participante
     * @param lastAttack       item de quem deu o golpe final (null = nenhum)
     * @param items            itens divididos entre os participantes
     */
    public record Rewards(double minDamagePercent, long xp, long col, String lastAttack, List<Drop> items) {
    }

    /** Lê um chefe. Problemas vão para {@code errors}; devolve null se não dá para usar. */
    public static BossDef parse(String id, ConfigurationSection s, List<String> errors) {
        String where = "chefe " + id + ": ";
        int floor = s.getInt("andar", 0);
        if (floor < 1) {
            errors.add(where + "\"andar\" precisa ser 1 ou mais.");
            return null;
        }
        double health = s.getDouble("vida", 0);
        if (health <= 0) {
            errors.add(where + "\"vida\" precisa ser maior que zero.");
            return null;
        }
        String mob = s.getString("mob", "ZOMBIE").toUpperCase(Locale.ROOT);
        List<Phase> phases = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("fases")) {
            phases.add(phase(raw, where, errors));
        }
        if (phases.isEmpty()) {
            phases.add(new Phase(100, "", "", 1, 1, Map.of(), List.of()));
        }
        phases.sort(Comparator.comparingDouble(Phase::at).reversed());
        if (phases.get(0).at() < 100) {
            // Sem fase de 100%: a mais alta vale desde o começo.
            Phase p = phases.get(0);
            phases.set(0, new Phase(100, p.name(), p.message(), p.damageTimes(), p.speedTimes(), p.equipment(), p.skills()));
        }
        ConfigurationSection r = s.getConfigurationSection("recompensas");
        List<Drop> items = new ArrayList<>();
        if (r != null) {
            for (Map<?, ?> raw : r.getMapList("itens")) {
                Object item = raw.get("item");
                if (item != null) {
                    items.add(new Drop(item.toString(), Math.max(1, (int) num(raw.get("quantidade"), 1))));
                }
            }
        }
        String last = r == null ? null : r.getString("last_attack");
        Rewards rewards = new Rewards(r == null ? 5 : Math.max(0, r.getDouble("dano_minimo_pct", 5)),
                r == null ? 0 : Math.max(0, r.getLong("xp", 0)), r == null ? 0 : Math.max(0, r.getLong("col", 0)),
                last == null || last.isBlank() ? null : last, List.copyOf(items));
        return new BossDef(id, floor, s.getString("nome", id), Math.max(1, s.getInt("level", 1)), mob, health,
                Math.max(0, s.getDouble("dano", 6)), Math.max(0, s.getDouble("armadura", 0)), Math.max(0, s.getDouble("velocidade", 0.25)),
                Math.max(0, Math.min(1, s.getDouble("resistencia_empurrao", 0.5))), Math.max(0.1, Math.min(8, s.getDouble("escala", 1))),
                strings(s.getConfigurationSection("equipamento")), Math.max(0, s.getInt("intro_segundos", 5)),
                Math.max(0, s.getInt("tempo_limite_segundos", 900)), Math.max(0, s.getDouble("reaparece_horas", 6)),
                List.copyOf(phases), rewards);
    }

    private static Phase phase(Map<?, ?> raw, String where, List<String> errors) {
        List<Skill> skills = new ArrayList<>();
        if (raw.get("habilidades") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                SkillType type = SkillType.parse(String.valueOf(m.get("tipo")));
                if (type == null) {
                    errors.add(where + "habilidade \"" + m.get("tipo") + "\" não existe (investida, area, lacaios, projetil, salto, furia).");
                    continue;
                }
                skills.add(new Skill(type, Math.max(1, num(m.get("recarga"), 15)), Math.max(0, num(m.get("dano"), 6)),
                        Math.max(0.5, num(m.get("raio"), 3)), Math.max(0, (int) num(m.get("aviso_ticks"), 30)),
                        m.get("mob") == null ? "ZOMBIE" : m.get("mob").toString(), Math.max(1, (int) num(m.get("quantidade"), 2)),
                        Math.max(1, (int) num(m.get("level"), 1)), Math.max(1, num(m.get("segundos"), 8)),
                        Math.max(1, num(m.get("multiplicador"), 1.5)), m.get("nome") == null ? "" : m.get("nome").toString()));
            }
        }
        Map<String, String> equipment = new LinkedHashMap<>();
        if (raw.get("equipamento") instanceof Map<?, ?> eq) {
            eq.forEach((k, v) -> equipment.put(String.valueOf(k).toLowerCase(Locale.ROOT), String.valueOf(v).toUpperCase(Locale.ROOT)));
        }
        return new Phase(Math.max(0, Math.min(100, num(raw.get("vida"), 100))),
                raw.get("nome") == null ? "" : raw.get("nome").toString(), raw.get("mensagem") == null ? "" : raw.get("mensagem").toString(),
                Math.max(0, num(raw.get("dano_x"), 1)), Math.max(0, num(raw.get("velocidade_x"), 1)), Map.copyOf(equipment), List.copyOf(skills));
    }

    private static double num(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static Map<String, String> strings(ConfigurationSection s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                out.put(k.toLowerCase(Locale.ROOT), String.valueOf(s.get(k)).toUpperCase(Locale.ROOT));
            }
        }
        return Map.copyOf(out);
    }

    /** Índice da fase para a vida atual (em %): a última cujo limite já foi alcançado. */
    public int phaseAt(double healthPercent) {
        int index = 0;
        for (int i = 1; i < phases.size(); i++) {
            if (healthPercent <= phases.get(i).at()) {
                index = i;
            }
        }
        return index;
    }
}
