package com.aincrad.skills.model;

import java.util.Map;

/** As contas das skills, sem servidor: proficiência, espaços, dano, atalhos e geometria. */
public final class SkillRules {

    public static final int MAX_PROF = 1000;
    public static final int ACTIVE_SLOTS = 4;
    public static final int PASSIVE_SLOTS = 3;

    private SkillRules() {
    }

    /**
     * Chance de um golpe render 1 ponto de proficiência: cheia no começo, caindo até o mínimo
     * perto do máximo. No máximo, zero.
     */
    public static double gainChance(int prof, double base, double min) {
        if (prof >= MAX_PROF) {
            return 0;
        }
        double t = Math.max(0, prof) / (double) MAX_PROF;
        return Math.max(min, base * (1 - t));
    }

    /** Espaços liberados no level: a maior entrada da tabela com level menor ou igual. */
    public static int slots(Map<Integer, Integer> table, int level, int cap) {
        int best = 0;
        int bestLevel = Integer.MIN_VALUE;
        for (var e : table.entrySet()) {
            if (e.getKey() <= level && e.getKey() > bestLevel) {
                bestLevel = e.getKey();
                best = e.getValue();
            }
        }
        return Math.max(0, Math.min(cap, best));
    }

    /** Dano de um golpe: dano de ataque × multiplicador da skill × bônus da proficiência. */
    public static double hitDamage(double attack, double multiplier, int prof, double bonusPerPoint) {
        return Math.max(0, attack) * multiplier * (1 + Math.max(0, prof) * Math.max(0, bonusPerPoint));
    }

    /** Quanto falta da recarga, em milissegundos (0 = pronta). */
    public static long remaining(long until, long now) {
        return Math.max(0, until - now);
    }

    /** Quando a skill termina: o último golpe sai em {@code (golpes - 1) × intervalo} ticks. */
    public static long castTicks(int hits, int hitTicks) {
        return (long) (Math.max(1, hits) - 1) * hitTicks;
    }

    /**
     * As teclas da hotbar (0 a 8) dos quatro atalhos. A tecla do item na mão não serve — apertá-la
     * não muda nada —, então são as quatro primeiras das outras.
     */
    public static int[] keys(int heldSlot) {
        int[] out = new int[ACTIVE_SLOTS];
        int n = 0;
        for (int i = 0; i < 9 && n < ACTIVE_SLOTS; i++) {
            if (i != heldSlot) {
                out[n++] = i;
            }
        }
        return out;
    }

    /** Qual atalho (0 a 3) a tecla da hotbar aciona, ou -1. */
    public static int slotOfKey(int heldSlot, int key) {
        int[] keys = keys(heldSlot);
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    /**
     * O ponto está na linha à frente? {@code (dx, dy, dz)} é a direção do olhar, já com tamanho 1.
     *
     * @param width meia largura da linha
     */
    public static boolean inLine(double ox, double oy, double oz, double dx, double dy, double dz,
                                 double px, double py, double pz, double range, double width) {
        double vx = px - ox;
        double vy = py - oy;
        double vz = pz - oz;
        double along = vx * dx + vy * dy + vz * dz;
        if (along < 0 || along > range) {
            return false;
        }
        double cx = vx - dx * along;
        double cy = vy - dy * along;
        double cz = vz - dz * along;
        return cx * cx + cy * cy + cz * cz <= width * width;
    }

    /** A seta (8 direções) de onde o alvo está, em relação a para onde o jogador olha. */
    public static String arrow(double yaw, double fromX, double fromZ, double toX, double toZ) {
        // No Minecraft, yaw 0 olha para +Z e cresce no sentido horário visto de cima.
        double target = Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
        double diff = ((target - yaw) % 360 + 360) % 360;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[(int) Math.round(diff / 45) % 8];
    }
}
