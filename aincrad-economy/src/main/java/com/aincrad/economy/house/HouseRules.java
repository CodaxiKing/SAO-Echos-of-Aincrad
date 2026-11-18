package com.aincrad.economy.house;

/** Contas das casas que não precisam de servidor: a retomada por inatividade e o que a placa mostra. */
public final class HouseRules {

    private static final long DAY = 86_400_000L;

    /** O que a placa na frente da casa mostra. */
    public enum Sign { VENDA, LEILAO, DONO }

    private HouseRules() {
    }

    /**
     * O dono sumiu? {@code days} 0 ou menos desliga a regra; {@code seen} 0 ou menos quer dizer
     * que ele ainda não foi visto desde que a regra ligou, e não conta.
     */
    public static boolean inactive(long seen, long now, int days) {
        return days > 0 && seen > 0 && now - seen >= days * DAY;
    }

    /** Dias que faltam para a casa ser retomada (0 = já pode). */
    public static long daysLeft(long seen, long now, int days) {
        if (days <= 0 || seen <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, (seen + days * DAY - now + DAY - 1) / DAY);
    }

    /** Como está o imposto de uma casa. */
    public enum Tax { EM_DIA, VENCIDO, PERDIDO }

    /** O imposto de um período: uma fração do preço da casa. 0 = a casa não paga. */
    public static long tax(long price, double fraction) {
        return price <= 0 || fraction <= 0 ? 0 : Math.max(1, (long) Math.floor(price * fraction));
    }

    /** Vencido = passou da data; perdido = passou também a tolerância, e a casa é retomada. */
    public static Tax taxState(long due, long now, int graceDays) {
        return now < due ? Tax.EM_DIA : now < due + Math.max(0, graceDays) * DAY ? Tax.VENCIDO : Tax.PERDIDO;
    }

    /**
     * Quantos períodos ainda dá para pagar: o que venceu, mais o adiantado até {@code maxAhead}
     * períodos à frente de hoje.
     */
    public static int payable(long due, long now, int periodDays, int maxAhead) {
        long period = Math.max(1, periodDays) * DAY;
        long limit = now + Math.max(0, maxAhead) * period;
        return due >= limit ? 0 : (int) Math.min(1000, (limit - due + period - 1) / period);
    }

    /** O vencimento depois de pagar {@code periods} períodos. Pagar atrasado não dá dias de graça. */
    public static long paid(long due, int periods, int periodDays) {
        return due + (long) periods * Math.max(1, periodDays) * DAY;
    }

    /** Dias até uma data, arredondados para cima (0 se já passou). */
    public static long daysUntil(long when, long now) {
        return when <= now ? 0 : (when - now + DAY - 1) / DAY;
    }

    /** Quantas casas da lista estão à venda. */
    public static int free(java.util.List<Boolean> forSale) {
        int n = 0;
        for (boolean b : forSale) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /** A visita do corretor acaba no fim do tempo, ou quando o jogador sai da casa. */
    public static boolean visitOver(long now, long endsAt, boolean insideHouse) {
        return now >= endsAt || !insideHouse;
    }

    public static Sign sign(boolean forSale, boolean auctioned) {
        return !forSale ? Sign.DONO : auctioned ? Sign.LEILAO : Sign.VENDA;
    }
}
