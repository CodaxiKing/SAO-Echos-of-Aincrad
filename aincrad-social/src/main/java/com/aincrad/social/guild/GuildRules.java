package com.aincrad.social.guild;

import java.util.List;

/**
 * Regras da guilda (guilds.yml), em unidades já prontas para o serviço.
 *
 * @param levels níveis em ordem crescente (o índice 0 é o nível 1)
 */
public record GuildRules(
        int nameMin, int nameMax, int tagMin, int tagMax, List<String> blockedWords, List<String> emblems,
        int minMembers, long minMembersDeadlineMs, double refundFraction,
        long recruitPromoteMs, long leaderInactiveMs, long rejoinCooldownMs, long inviteTtlMs,
        int memberWithdrawalsPerDay, int maxTaxPercent, int maxAllies, int alliesMinLevel,
        int uniformMinLevel, int homeMinLevel, int ledgerMax,
        List<Level> levels) {

    /** Um nível: XP total para chegar nele, vagas e tamanho do cofre (múltiplo de 9). */
    public record Level(int level, long xp, int slots, int vault) {
    }

    /** Regras padrão do plano (as mesmas do guilds.yml que vem no .jar). */
    public static GuildRules defaults() {
        long day = 86_400_000L;
        return new GuildRules(3, 24, 2, 4, List.of(), List.of("⚔", "✦", "☠", "♛", "❖", "✚", "☾", "✿", "⚑", "♞"),
                3, 7 * day, 0.5, 3 * day, 30 * day, day, 120_000L,
                16, 10, 3, 3, 2, 2, 200,
                List.of(new Level(1, 0, 10, 9), new Level(2, 1000, 20, 18), new Level(3, 3000, 30, 27),
                        new Level(4, 7000, 40, 36), new Level(5, 15000, 50, 54)));
    }

    public Level level(int n) {
        int i = Math.max(1, Math.min(levels.size(), n)) - 1;
        return levels.get(i);
    }

    /** Nível correspondente a um XP total. */
    public int levelFor(long xp) {
        int lv = 1;
        for (Level l : levels) {
            if (xp >= l.xp()) {
                lv = l.level();
            }
        }
        return lv;
    }

    public int maxLevel() {
        return levels.get(levels.size() - 1).level();
    }
}
