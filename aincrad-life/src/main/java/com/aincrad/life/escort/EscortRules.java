package com.aincrad.life.escort;

/** Regras do contrato de escolta, sem servidor. */
public final class EscortRules {

    private EscortRules() {
    }

    /** Em que pé o contrato está. */
    public enum State {
        /** O cliente propôs e o Col já está em custódia; falta o escolta aceitar. */
        PROPOSTO,
        /** A caminho. */
        ATIVO
    }

    /** Como o contrato acabou. */
    public enum End {
        /** O cliente chegou vivo, com o escolta por perto: o escolta recebe. */
        CHEGOU,
        /** O cliente morreu no caminho: o Col volta e o escolta fica com uma falha. */
        CLIENTE_MORREU,
        /** O escolta desistiu, saiu do servidor ou morreu: o Col volta e ele fica com uma falha. */
        ESCOLTA_FALHOU,
        /** O tempo acabou: o Col volta e o escolta fica com uma falha. */
        TEMPO,
        /** O cliente cancelou, ou saiu do servidor: o Col volta, sem falha para o escolta. */
        CANCELADO,
        /** O escolta não aceitou a tempo: o Col volta. */
        RECUSADO;

        /** O escolta recebe o pagamento? */
        public boolean pays() {
            return this == CHEGOU;
        }

        /** Conta como falha no histórico do escolta? */
        public boolean fails() {
            return this == CLIENTE_MORREU || this == ESCOLTA_FALHOU || this == TEMPO;
        }
    }

    /**
     * O cliente entrou na região de destino. Só vale se o escolta está junto.
     *
     * @param escortDistance distância do escolta ao cliente (negativa = outro mundo ou offline)
     */
    public static boolean arrived(State state, double escortDistance, double maxDistance) {
        return state == State.ATIVO && escortDistance >= 0 && escortDistance <= maxDistance;
    }

    public static boolean timedOut(long startedAt, long now, long limitMs) {
        return limitMs > 0 && now - startedAt >= limitMs;
    }

    /** Por que a proposta não pode ser feita (null = pode). */
    public enum Block { VOCE_MESMO, VALOR, JA_TEM_CONTRATO, ESCOLTA_OCUPADO, DESTINO, JA_ESTA_LA }

    public static Block blocked(boolean self, long amount, long min, boolean clientBusy, boolean escortBusy,
                                boolean destinationExists, boolean alreadyThere) {
        if (self) {
            return Block.VOCE_MESMO;
        }
        if (amount < Math.max(1, min)) {
            return Block.VALOR;
        }
        if (clientBusy) {
            return Block.JA_TEM_CONTRATO;
        }
        if (escortBusy) {
            return Block.ESCOLTA_OCUPADO;
        }
        if (!destinationExists) {
            return Block.DESTINO;
        }
        return alreadyThere ? Block.JA_ESTA_LA : null;
    }

    /** A taxa de sucesso de um escolta, em % (100 para quem ainda não tem histórico). */
    public static int successRate(int done, int failed) {
        int total = done + failed;
        return total <= 0 ? 100 : (int) Math.round(done * 100.0 / total);
    }
}
