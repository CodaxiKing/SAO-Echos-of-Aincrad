package com.aincrad.life.marriage;

import java.util.UUID;

/** As regras do casamento: quem pode pedir, a espera depois do divórcio, a trava e os autores do baú. Só regras, sem servidor. */
public final class MarriageRules {

    private MarriageRules() {
    }

    /** O que impede (ou não) um pedido de casamento. */
    public enum Refusal {
        OK,
        /** Pediu a si mesmo. */
        SI_MESMO,
        /** Quem pede já é casado. */
        JA_CASADO,
        /** A outra pessoa já é casada. */
        PAR_CASADO,
        /** Sem o anel na mão. */
        SEM_ANEL,
        /** Os dois precisam estar no mesmo lugar. */
        LONGE,
        /** Os dois precisam estar em zona segura. */
        FORA_DE_ZONA_SEGURA
    }

    /**
     * Pode pedir? As condições são conferidas nesta ordem, e de novo na hora do aceite.
     *
     * @param distance distância entre os dois, em blocos (um valor negativo = mundos diferentes)
     */
    public static Refusal canPropose(boolean self, boolean married, boolean targetMarried, boolean ring, double distance,
                                     double maxDistance, boolean bothSafe) {
        if (self) {
            return Refusal.SI_MESMO;
        }
        if (married) {
            return Refusal.JA_CASADO;
        }
        if (targetMarried) {
            return Refusal.PAR_CASADO;
        }
        if (!ring) {
            return Refusal.SEM_ANEL;
        }
        if (distance < 0 || distance > maxDistance) {
            return Refusal.LONGE;
        }
        return bothSafe ? Refusal.OK : Refusal.FORA_DE_ZONA_SEGURA;
    }

    /**
     * Quanto falta da espera para casar de novo depois de um divórcio.
     *
     * @param divorcedAt quando se divorciou (0 = nunca)
     * @return milissegundos que faltam (0 = já pode)
     */
    public static long waitLeft(long divorcedAt, long now, int days) {
        if (divorcedAt <= 0) {
            return 0;
        }
        return Math.max(0, divorcedAt + days * 86_400_000L - now);
    }

    /** O teleporte até o par vale uma vez por dia. {@code lastDay} e {@code today} são datas como "2026-11-17". */
    public static boolean canTeleport(String lastDay, String today) {
        return lastDay == null || !lastDay.equals(today);
    }

    // ------------------------------------------------------------------ baú do casal

    /**
     * A trava do baú está livre para este jogador? Livre se ninguém a tem, se quem tinha deixou
     * vencer (servidor caiu com o baú aberto) ou se é dele mesmo.
     */
    public static boolean lockFree(String lockOwner, long lockUntil, long now, String me) {
        return lockOwner == null || lockOwner.isEmpty() || lockUntil < now || lockOwner.equals(me);
    }

    /**
     * De quem é o que está num espaço do baú depois de alguém mexer nele. O baú guarda o autor de
     * cada depósito, para devolver cada item a quem o colocou no divórcio.
     *
     * @param beforeKey    o item que estava no espaço (null = vazio); a chave identifica o item, sem a quantidade
     * @param beforeAuthor quem tinha posto
     * @param afterKey     o item que está agora (null = vazio)
     * @param closer       quem mexeu no baú
     * @return o autor do que está no espaço agora (null se ficou vazio)
     */
    public static UUID author(String beforeKey, int beforeAmount, UUID beforeAuthor, String afterKey, int afterAmount, UUID closer) {
        if (afterKey == null || afterAmount <= 0) {
            return null;
        }
        // O mesmo item, sem ter crescido: continua de quem pôs. Qualquer outra coisa foi posta agora.
        if (afterKey.equals(beforeKey) && afterAmount <= beforeAmount && beforeAuthor != null) {
            return beforeAuthor;
        }
        return closer;
    }
}
