package com.aincrad.items.crystal;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/**
 * Um cristal de crystals.yml. Só dados (materiais e efeitos ficam como texto e são conferidos
 * quando o item é criado ou usado).
 *
 * @param cooldownGroup cristais do mesmo grupo dividem a recarga
 * @param amount        cura e ressurreição: fração da vida máxima (0 a 1)
 * @param remove        antídoto: efeitos de poção removidos
 * @param waitSeconds        teleporte: segundos parado antes de ir
 * @param maxLength     gravação: tamanho máximo da mensagem
 * @param dropChance    chance por abate (0 = não cai)
 * @param dropMobLevel  level mínimo do mob para cair
 */
public record CrystalDef(String id, Type type, String name, List<String> description, String material, String model,
                         String recordedModel, int cooldown, String cooldownGroup, boolean consumed, double amount,
                         List<String> remove, int waitSeconds, int maxLength, double dropChance, int dropMobLevel) {

    /** O que o cristal faz. */
    public enum Type {
        CURA, ANTIDOTO, TELEPORTE, RESSURREICAO, GRAVACAO;

        public static Type parse(String s) {
            try {
                return valueOf(String.valueOf(s).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Lê um cristal. Erros vão para {@code errors}; devolve null se não dá para usar. */
    public static CrystalDef parse(String id, ConfigurationSection s, List<String> errors) {
        Type type = Type.parse(s.getString("tipo"));
        if (type == null) {
            errors.add("cristal " + id + ": tipo \"" + s.getString("tipo") + "\" não existe (cura, antidoto, teleporte, ressurreicao, gravacao).");
            return null;
        }
        String fraction = type == Type.RESSURREICAO ? "vida" : "cura";
        double def = type == Type.RESSURREICAO ? 0.5 : 1.0;
        List<String> remove = s.getStringList("remove").stream().map(e -> e.toUpperCase(Locale.ROOT)).toList();
        return new CrystalDef(id, type, s.getString("nome", id), List.copyOf(s.getStringList("descricao")),
                s.getString("material", "AMETHYST_SHARD").toUpperCase(Locale.ROOT), blank(s.getString("modelo")),
                blank(s.getString("modelo_gravado")), Math.max(0, s.getInt("recarga", 0)),
                s.getString("grupo_recarga", id).toLowerCase(Locale.ROOT), s.getBoolean("consumido", type != Type.GRAVACAO),
                Math.max(0.01, Math.min(1, s.getDouble(fraction, def))),
                type == Type.ANTIDOTO && remove.isEmpty() ? List.of("POISON", "WITHER", "SLOWNESS") : remove,
                Math.max(0, s.getInt("espera", 3)), Math.max(1, Math.min(1024, s.getInt("maximo", 256))),
                Math.max(0, Math.min(1, s.getDouble("drop.chance", 0))), Math.max(0, s.getInt("drop.level_mob", 0)));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Corta a mensagem no tamanho máximo e tira quebras de linha e espaços das pontas. */
    public String clip(String message) {
        String m = message.replace('\n', ' ').replace('\r', ' ').strip();
        return m.length() <= maxLength ? m : m.substring(0, maxLength);
    }
}
