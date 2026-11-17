package com.aincrad.life.cooking;

import java.util.Locale;

/** A qualidade de um prato. Queimado não dá buff; excelente dá um buff mais longo. */
public enum Quality {
    QUEIMADO, COMUM, BOM, EXCELENTE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Quality parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Os limites de config.yml → culinaria.qualidade. */
    public record Rules(double burnedBelow, double goodFrom, double excellentFrom, double baseChance,
                        double chancePer100, double platingBonus) {
    }

    /**
     * Chance de um prato bem executado sair excelente: cresce com a proficiência acima da
     * dificuldade da receita, com o empratar perfeito e com os bônus do artesão.
     */
    public static double excellentChance(Rules r, double proficiency, double difficulty, boolean perfectPlating, double bonus) {
        double c = r.baseChance() + (proficiency - difficulty) / 100.0 * r.chancePer100()
                + (perfectPlating ? r.platingBonus() : 0) + bonus;
        return Math.max(0.05, Math.min(1, c));
    }

    /**
     * Decide a qualidade.
     *
     * @param score  fração dos acertos nas etapas, de 0 a 1
     * @param burned o fogo passou do ponto por tempo demais
     * @param chance chance de "excelente" quando a execução foi boa o bastante
     * @param roll   sorteio de 0 a 1
     */
    public static Quality decide(Rules r, double score, boolean burned, double chance, double roll) {
        if (burned || score < r.burnedBelow()) {
            return QUEIMADO;
        }
        if (score < r.goodFrom()) {
            return COMUM;
        }
        if (score < r.excellentFrom()) {
            return BOM;
        }
        return roll < chance ? EXCELENTE : BOM;
    }
}
