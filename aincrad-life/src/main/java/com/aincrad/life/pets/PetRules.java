package com.aincrad.life.pets;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** As espécies de pets.yml e as contas dos familiares: domar, level, vínculo e o prazo do Coração. Só regras, sem servidor. */
public final class PetRules {

    private PetRules() {
    }

    /** O que um familiar faz pelo dono. */
    public enum Ability {
        /** Cura pequena de tempos em tempos. */
        CURA,
        /** Uma bolha que absorve um golpe. */
        BOLHA,
        /** Avisa quando há mob hostil por perto. */
        ALERTA,
        /** Traz para o dono os itens caídos por perto. */
        BUSCA;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Ability parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** O que o familiar está fazendo. */
    public enum Mode {
        /** Segue o dono. */
        SEGUIR,
        /** Fica onde está. */
        FICAR,
        /** Guardado: não aparece no mundo. */
        GUARDADO;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Mode parse(String s) {
            try {
                return s == null ? SEGUIR : valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return SEGUIR;
            }
        }
    }

    /**
     * Uma espécie que pode ser domada.
     *
     * @param mob    tipo de mob do Minecraft
     * @param food   a comida favorita (material)
     * @param chance chance de domar a cada tentativa (0.03 = 3%)
     * @param health vida no level 1
     */
    public record Species(String id, String name, String mob, String food, double chance, Ability ability, double health) {
    }

    /** Lê pets.yml. Os erros (com o id) vão para {@code errors} e a espécie é pulada. */
    public static Map<String, Species> parse(ConfigurationSection root, List<String> errors) {
        Map<String, Species> out = new LinkedHashMap<>();
        ConfigurationSection s = root == null ? null : root.getConfigurationSection("familiares");
        if (s == null) {
            return out;
        }
        for (String raw : s.getKeys(false)) {
            ConfigurationSection e = s.getConfigurationSection(raw);
            if (e == null) {
                continue;
            }
            String id = raw.toLowerCase(Locale.ROOT);
            Ability ability = Ability.parse(e.getString("habilidade"));
            String mob = e.getString("mob", "").toUpperCase(Locale.ROOT);
            String food = e.getString("comida", "").toUpperCase(Locale.ROOT);
            if (ability == null || mob.isEmpty() || food.isEmpty()) {
                errors.add("pets.yml: " + id + ": confira \"mob\", \"comida\" e \"habilidade\" (cura, bolha, alerta, busca).");
                continue;
            }
            boolean repeated = out.values().stream().anyMatch(o -> o.mob().equals(mob));
            if (repeated) {
                errors.add("pets.yml: " + id + ": o mob " + mob + " já é outra espécie.");
                continue;
            }
            out.put(id, new Species(id, e.getString("nome", id), mob, food, Math.max(0, Math.min(1, e.getDouble("chance", 0.03))), ability,
                    Math.max(1, e.getDouble("vida", 10))));
        }
        return out;
    }

    // ------------------------------------------------------------------ domar

    /** O que impede (ou não) uma tentativa de domar. */
    public enum Tame {
        /** Pode tentar. */
        OK,
        /** O jogador já tem um familiar (vivo, guardado ou esperando ser revivido). */
        JA_TEM,
        /** O jogador atacou este mob: ele não confia mais. */
        ATACOU,
        /** Não é a comida favorita da espécie. */
        COMIDA
    }

    public static Tame canTame(boolean hasPet, boolean attacked, boolean rightFood) {
        if (!rightFood) {
            return Tame.COMIDA;
        }
        if (hasPet) {
            return Tame.JA_TEM;
        }
        return attacked ? Tame.ATACOU : Tame.OK;
    }

    /** A tentativa deu certo? {@code multiplier} é o ajuste da config (para testar: um valor alto). */
    public static boolean tamed(double chance, double multiplier, double roll) {
        return roll < Math.max(0, Math.min(1, chance * Math.max(0, multiplier)));
    }

    // ------------------------------------------------------------------ level

    /** XP para passar do level dado para o seguinte. */
    public static long xpToNext(int level, double base, double exponent) {
        return Math.max(1, Math.round(base * Math.pow(Math.max(1, level), exponent)));
    }

    /** O level e o XP depois de ganhar {@code gain}: {level, xp}. Sobe vários levels de uma vez, se der. */
    public static long[] addXp(int level, long xp, long gain, double base, double exponent, int maxLevel) {
        int lv = Math.max(1, level);
        long have = Math.max(0, xp) + Math.max(0, gain);
        while (lv < maxLevel && have >= xpToNext(lv, base, exponent)) {
            have -= xpToNext(lv, base, exponent);
            lv++;
        }
        if (lv >= maxLevel) {
            have = 0;
        }
        return new long[]{lv, have};
    }

    /** A vida máxima do familiar: a da espécie, mais um tanto por level acima do primeiro. */
    public static double maxHealth(double base, int level, double perLevel) {
        return base + Math.max(0, level - 1) * Math.max(0, perLevel);
    }

    /** A parte do XP do dono que vai para o familiar. */
    public static long share(long ownerXp, double fraction) {
        return (long) Math.floor(Math.max(0, ownerXp) * Math.max(0, fraction));
    }

    // ------------------------------------------------------------------ vínculo

    /** O vínculo vai de 0 a 100. */
    public static int bond(int now, int delta) {
        return Math.max(0, Math.min(100, now + delta));
    }

    /** Com vínculo abaixo do mínimo, a habilidade para. */
    public static boolean abilityWorks(int bond, int minimum) {
        return bond >= minimum;
    }

    /** A cura do familiar: cresce um pouco com o level. */
    public static double heal(double base, int level, double perLevel) {
        return base + Math.max(0, level - 1) * Math.max(0, perLevel);
    }

    // ------------------------------------------------------------------ morte

    /** Ainda dá para reviver? O Coração vale por um número de dias reais a partir da morte. */
    public static boolean canRevive(long died, long now, int days) {
        return died > 0 && now - died <= days * 86_400_000L;
    }
}
