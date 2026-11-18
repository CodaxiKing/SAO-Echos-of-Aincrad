package com.aincrad.reputation.model;

import java.util.ArrayList;
import java.util.List;

/** Regras da recompensa por cabeça e dos túmulos, sem servidor. */
public final class WorldMarkRules {

    private WorldMarkRules() {
    }

    // ------------------------------------------------------------------ recompensa por cabeça

    /** Por que a recompensa não pode ser posta (null = pode). */
    public enum BountyBlock { VOCE_MESMO, VALOR, NAO_E_CRIMINOSO }

    /**
     * @param onlyRed só jogador vermelho pode ter a cabeça a prêmio (senão, laranja também)
     */
    public static BountyBlock bountyBlocked(boolean self, long amount, long min, CursorRules.Color target, boolean onlyRed) {
        if (self) {
            return BountyBlock.VOCE_MESMO;
        }
        if (amount < Math.max(1, min)) {
            return BountyBlock.VALOR;
        }
        boolean criminal = target == CursorRules.Color.VERMELHO || (!onlyRed && target == CursorRules.Color.LARANJA);
        return criminal ? null : BountyBlock.NAO_E_CRIMINOSO;
    }

    /**
     * A recompensa volta a quem pôs quando o alvo deixou de ser vermelho sem ser preso: o
     * vermelho venceu e ele não está na prisão.
     */
    public static boolean bountyExpired(long redUntil, long jailUntil, long now) {
        return redUntil <= now && jailUntil <= now;
    }

    // ------------------------------------------------------------------ túmulos

    /**
     * Um túmulo.
     *
     * @param expiresAt 0 = nunca some
     */
    public record Grave(long id, String uuid, String name, String world, int x, int y, int z, long at, long expiresAt, int flowers) {

        public boolean expired(long now) {
            return expiresAt > 0 && expiresAt <= now;
        }

        public int chunkX() {
            return x >> 4;
        }

        public int chunkZ() {
            return z >> 4;
        }
    }

    /** Quando o túmulo some. {@code days} 0 = nunca. */
    public static long expiresAt(long at, int days) {
        return days <= 0 ? 0 : at + days * 86_400_000L;
    }

    /**
     * Com um túmulo novo no pedaço de mapa, quais dos antigos saem para caber no limite: os mais
     * velhos primeiro.
     *
     * @param inChunk os túmulos que já estão no pedaço (sem o novo)
     */
    public static List<Grave> overflow(List<Grave> inChunk, int max) {
        List<Grave> sorted = new ArrayList<>(inChunk);
        sorted.sort((a, b) -> Long.compare(a.at(), b.at()));
        int remove = sorted.size() + 1 - Math.max(1, max);
        return remove <= 0 ? List.of() : List.copyOf(sorted.subList(0, Math.min(remove, sorted.size())));
    }
}
