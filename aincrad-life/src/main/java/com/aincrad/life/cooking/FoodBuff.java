package com.aincrad.life.cooking;

import java.util.Locale;

/**
 * O buff de comida ativo de um jogador. Só existe um por vez: comer outro prato troca. Fica
 * gravado no perfil do core (texto), para continuar depois de relogar e de trocar de servidor.
 *
 * @param value   %, pontos de vida ou nível, conforme o tipo
 * @param expires quando acaba (milissegundos)
 * @param dish    nome do prato, para as mensagens
 */
public record FoodBuff(Type type, double value, long expires, String dish) {

    /** Os buffs que um prato pode dar. */
    public enum Type {
        /** +% de dano. */
        FORCA,
        /** Regeneração do nível dado. */
        REGENERACAO,
        /** +% de velocidade de movimento. */
        VELOCIDADE,
        /** + pontos de vida máxima. */
        VIDA_MAXIMA,
        /** +% de XP (AincradRPG). */
        XP,
        /** +% de sorte na pesca. */
        SORTE_PESCA;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type parse(String s) {
            if (s == null) {
                return null;
            }
            String t = s.trim().toUpperCase(Locale.ROOT).replace("Ç", "C").replace("Ã", "A").replace("Á", "A");
            if (t.equals("XP_EXTRA")) {
                return XP;
            }
            try {
                return valueOf(t);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public boolean active(long now) {
        return expires > now;
    }

    /** Texto gravado no perfil: "tipo;valor;expira;prato". */
    public String encode() {
        return type.name() + ";" + value + ";" + expires + ";" + dish;
    }

    /** Lê o texto do perfil; null se estiver vazio ou estragado. */
    public static FoodBuff decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] p = raw.split(";", 4);
        if (p.length < 4) {
            return null;
        }
        Type t = Type.parse(p[0]);
        try {
            return t == null ? null : new FoodBuff(t, Double.parseDouble(p[1]), Long.parseLong(p[2]), p[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Duração do buff de um prato, em segundos: os minutos da receita, vezes o fator da
     * qualidade, mais os bônus do cozinheiro. Queimado não dá buff.
     */
    public static int seconds(int minutes, Quality q, double common, double good, double excellent, double cookBonus) {
        double factor = switch (q) {
            case QUEIMADO -> 0;
            case COMUM -> common;
            case BOM -> good;
            case EXCELENTE -> excellent;
        };
        return (int) Math.round(minutes * 60.0 * factor * (1 + Math.max(0, cookBonus)));
    }
}
