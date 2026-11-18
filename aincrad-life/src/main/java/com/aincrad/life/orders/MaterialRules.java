package com.aincrad.life.orders;

/** As contas dos pedidos de material: o que ainda está em aberto, quanto alguém pode entregar e quanto recebe. Só regras. */
public final class MaterialRules {

    private MaterialRules() {
    }

    /** O que ainda falta entregar. */
    public static int open(int quantity, int delivered) {
        return Math.max(0, quantity - delivered);
    }

    /**
     * Quanto ainda dá para reservar: o que falta, menos o que os outros já reservaram (reservas
     * vencidas não contam: a quantidade delas volta a ficar livre).
     */
    public static int reservable(int quantity, int delivered, int reservedByOthers) {
        return Math.max(0, open(quantity, delivered) - Math.max(0, reservedByOthers));
    }

    /**
     * Quantas unidades este fornecedor pode entregar agora: o que ele reservou, mais o que está
     * livre, limitado ao que tem na mão.
     *
     * @param inHand           unidades na mão
     * @param ownReserved      o que resta da reserva dele (0 se não reservou)
     * @param reservedByOthers reservas ativas dos outros
     */
    public static int deliverable(int quantity, int delivered, int inHand, int ownReserved, int reservedByOthers) {
        int free = reservable(quantity, delivered, Math.max(0, reservedByOthers) + Math.max(0, ownReserved));
        return Math.max(0, Math.min(inHand, Math.max(0, ownReserved) + free));
    }

    /** O pagamento de uma entrega: por unidade, na hora. */
    public static long payment(int units, long unitPrice) {
        return Math.max(0, units) * Math.max(0, unitPrice);
    }

    /** O Col que o pedido inteiro deixa em custódia. */
    public static long total(int quantity, long unitPrice) {
        return payment(quantity, unitPrice);
    }

    /** A taxa de entrega de um fornecedor: o que entregou sobre o que reservou (1 = cumpriu tudo; sem reservas, 1). */
    public static double rate(int reserved, int delivered) {
        return reserved <= 0 ? 1 : Math.max(0, Math.min(1, delivered / (double) reserved));
    }
}
