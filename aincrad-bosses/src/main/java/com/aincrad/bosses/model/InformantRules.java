package com.aincrad.bosses.model;

/** Regras dos informantes: o preço de uma página do guia e quem pode lê-la. */
public final class InformantRules {

    private InformantRules() {
    }

    /** O que o jogador vê na página de uma fase. */
    public enum View {
        /** A fase inteira (as habilidades ainda não vistas pelo servidor seguem ocultas). */
        ABERTA,
        /** O servidor já descobriu, mas o jogador não tem a página: dá para comprar. */
        A_VENDA,
        /** Ninguém chegou nessa fase: nem o informante sabe. */
        DESCONHECIDA
    }

    /**
     * @param sold       os informantes estão ligados (senão o guia é aberto a todos, como antes)
     * @param serverSeen o servidor já viu a fase
     * @param owned      o jogador tem a página (comprou, ganhou ou estava na luta)
     */
    public static View view(boolean sold, boolean serverSeen, boolean owned) {
        if (!serverSeen) {
            return View.DESCONHECIDA;
        }
        return !sold || owned ? View.ABERTA : View.A_VENDA;
    }

    /** Preço de uma página: base, mais por andar do chefe, mais por fase (as últimas valem mais). */
    public static long price(long base, long perFloor, long perPhase, int floor, int phaseIndex) {
        return Math.max(0, base + perFloor * Math.max(0, floor - 1) + perPhase * Math.max(0, phaseIndex));
    }

    /** A taxa para copiar uma página que o jogador já tem (para vender a outro). */
    public static long copyFee(long price, double fraction) {
        return Math.max(0, Math.round(price * Math.max(0, Math.min(1, fraction))));
    }

    /** A chave de uma página: {@code "illfang:2"} (fase de índice 2). */
    public static String key(String boss, int phaseIndex) {
        return boss + ":" + phaseIndex;
    }

    /** Lê o número da fase digitado pelo jogador (a partir de 1) e devolve o índice, ou -1. */
    public static int phaseIndex(String typed, int phases) {
        try {
            int n = Integer.parseInt(typed.trim());
            return n >= 1 && n <= phases ? n - 1 : -1;
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }
}
