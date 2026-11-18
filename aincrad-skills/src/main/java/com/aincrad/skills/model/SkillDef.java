package com.aincrad.skills.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Uma Sword Skill ativa, lida de {@code skills/*.yml}.
 *
 * @param minProf    proficiência mínima na arma
 * @param damage     multiplicador do dano de ataque do jogador, por golpe
 * @param hits       número de golpes
 * @param range      alcance em blocos
 * @param shape      quem é atingido
 * @param cooldownMs recarga
 * @param freezeMs   a pausa depois do uso (o jogador fica travado)
 * @param hitTicks   ticks entre um golpe e outro
 * @param dash       avanço para a frente ao usar, em blocos
 * @param unique     id da skill única exigida, ou null
 */
public record SkillDef(String id, String name, String description, WeaponType weapon, int minProf, double damage, int hits,
                       double range, Shape shape, long cooldownMs, long freezeMs, int hitTicks, double dash,
                       String icon, String particle, String sound, String unique) {

    /** Quem é atingido: o alvo à frente, todos numa linha à frente, ou todos em volta. */
    public enum Shape { ALVO, LINHA, AREA }

    /** Lê as skills de um arquivo. As com erro ficam de fora e entram em {@code errors}. */
    public static Map<String, SkillDef> parse(ConfigurationSection root, String file, List<String> errors) {
        Map<String, SkillDef> out = new LinkedHashMap<>();
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
            WeaponType weapon = WeaponType.parse(s.getString("arma"));
            if (weapon == null) {
                errors.add(where + ": 'arma' faltando ou desconhecida");
                continue;
            }
            Shape shape;
            try {
                shape = Shape.valueOf(s.getString("forma", "alvo").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(where + ": 'forma' precisa ser alvo, linha ou area");
                continue;
            }
            double damage = s.getDouble("dano", 1.0);
            int hits = s.getInt("golpes", 1);
            double range = s.getDouble("alcance", 3.5);
            if (damage <= 0 || hits < 1 || hits > 50 || range <= 0 || range > 32) {
                errors.add(where + ": 'dano' (> 0), 'golpes' (1 a 50) ou 'alcance' (até 32) fora do limite");
                continue;
            }
            String unique = s.getString("unica");
            out.put(key.toLowerCase(Locale.ROOT), new SkillDef(key.toLowerCase(Locale.ROOT), s.getString("nome", key),
                    s.getString("descricao", ""), weapon, clamp(s.getInt("proficiencia", 0), 0, 1000), damage, hits, range, shape,
                    Math.max(0, Math.round(s.getDouble("recarga", 8) * 1000)), Math.max(0, Math.round(s.getDouble("pausa", 0.5) * 1000)),
                    clamp(s.getInt("intervalo_golpes", 4), 1, 40), Math.max(0, Math.min(8, s.getDouble("avanco", 0))),
                    s.getString("icone", "IRON_SWORD"), s.getString("particula", "SWEEP_ATTACK"),
                    s.getString("som", "entity.player.attack.sweep"),
                    unique == null || unique.isBlank() ? null : unique.toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
