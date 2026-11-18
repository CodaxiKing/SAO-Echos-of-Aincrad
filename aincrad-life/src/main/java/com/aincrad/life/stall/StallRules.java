package com.aincrad.life.stall;

/** As contas da barraca: o preço com o frescor do peixe e o que chega ao dono. Só regras, sem servidor. */
public final class StallRules {

    private StallRules() {
    }

    /**
     * Até quando um peixe vale o preço cheio: as horas de frescor a partir da hora em que foi
     * pescado, alongadas pelos nós da árvore do pescador que o pôs à venda.
     *
     * @param caughtAt  quando o peixe foi pescado (milissegundos); 0 = o item não é peixe
     * @param hours     horas de frescor da config
     * @param treeBonus fração a mais (0.5 = 50% mais tempo)
     * @return a hora em que ele passa, ou 0 se o item não tem frescor
     */
    public static long freshUntil(long caughtAt, double hours, double treeBonus) {
        if (caughtAt <= 0) {
            return 0;
        }
        return caughtAt + Math.round(Math.max(0, hours) * 3_600_000L * (1 + Math.max(0, treeBonus)));
    }

    /** O item ainda está fresco? (um item sem frescor nunca passa) */
    public static boolean fresh(long freshUntil, long now) {
        return freshUntil <= 0 || now < freshUntil;
    }

    /**
     * O preço de agora: o cheio enquanto o item está fresco; depois, a fração da config (metade),
     * nunca menos que 1.
     */
    public static long price(long price, long freshUntil, long now, double staleFactor) {
        if (fresh(freshUntil, now)) {
            return Math.max(0, price);
        }
        return Math.max(1, (long) Math.floor(Math.max(0, price) * Math.max(0, Math.min(1, staleFactor))));
    }

    /** A taxa da barraca sobre uma venda. */
    public static long tax(long price, double percent) {
        return (long) Math.floor(Math.max(0, price) * Math.max(0, Math.min(100, percent)) / 100.0);
    }

    /** O que chega ao dono: o que o cliente pagou menos a taxa. */
    public static long ownerTake(long price, double percent) {
        return Math.max(0, price) - tax(price, percent);
    }
}
