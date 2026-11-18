package com.aincrad.life.orders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** As regras das encomendas: estados, taxa, conferência da entrega e reputação. Só regras, sem servidor. */
public final class OrderRules {

    private OrderRules() {
    }

    /** Em que pé está uma encomenda. */
    public enum State {
        /** Pedido aberto no quadro, recebendo propostas. */
        ABERTO,
        /** Contrato direto esperando o artesão aceitar ou recusar. */
        AGUARDANDO,
        /** Proposta aceita: o artesão está trabalhando. */
        CONTRATADO,
        /** O artesão ofereceu um item abaixo do pedido, com desconto; o cliente decide. */
        OFERTA,
        /** Item entregue e artesão pago. */
        ENTREGUE,
        /** Cancelada pelo cliente ou pelo artesão. */
        CANCELADO,
        /** O pedido passou do prazo sem contrato. */
        VENCIDO,
        /** O artesão passou do prazo do contrato. */
        ATRASADO;

        public boolean finished() {
            return this == ENTREGUE || this == CANCELADO || this == VENCIDO || this == ATRASADO;
        }

        public static State parse(String s) {
            try {
                return s == null ? null : valueOf(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** O tipo de contrato. */
    public enum Kind {
        /** Encomenda comum: uma entrega. */
        PEDIDO,
        /** Fornecimento recorrente: várias entregas, uma a cada dia ou semana, pagas uma a uma. */
        RECORRENTE,
        /** Banquete de raid: um prato para cada inscrito, até a hora da raid. */
        BANQUETE,
        /** Caçada ao Senhor do Lago: o pescador paga os aventureiros que ajudarem a derrotá-lo. */
        CACADA,
        /** Serviço: o cliente entrega um item dele para o artesão trabalhar (aprimorar, reparar) e devolver. */
        SERVICO;

        public static Kind parse(String s) {
            try {
                return s == null ? PEDIDO : valueOf(s);
            } catch (IllegalArgumentException e) {
                return PEDIDO;
            }
        }
    }

    /** O Col que um contrato deixa em custódia: o orçamento de cada entrega, vezes as entregas. */
    public static long total(long budget, int rounds) {
        return Math.max(0, budget) * Math.max(1, rounds);
    }

    /** O que volta ao cliente ao aceitar uma proposta de um contrato com várias entregas. */
    public static long change(long budget, long price, int rounds) {
        return change(budget, price) * Math.max(1, rounds);
    }

    /** Quando vence a próxima entrega de um fornecimento recorrente. */
    public static long nextDue(long due, long period) {
        return due + Math.max(0, period);
    }

    /**
     * Banquete: cada inscrito recebe um prato. Com menos inscritos do que pratos, o resto vai
     * para quem encomendou.
     *
     * @return quantos pratos sobram para o cliente
     */
    public static int banquetLeftover(int dishes, int enrolled) {
        return banquetLeftover(dishes, enrolled, 1);
    }

    /** Quantos inscritos dá para servir, com {@code each} unidades para cada um (kit de raid). */
    public static int banquetServed(int units, int enrolled, int each) {
        return Math.max(0, Math.min(Math.max(0, enrolled), units / Math.max(1, each)));
    }

    /** O que sobra para o cliente depois de servir {@code each} unidades a cada inscrito. */
    public static int banquetLeftover(int units, int enrolled, int each) {
        return Math.max(0, units - banquetServed(units, enrolled, each) * Math.max(1, each));
    }

    /** Caçada: recebe quem se inscreveu e de fato feriu o chefe, na ordem de inscrição. */
    public static <T> List<T> paidHunters(List<T> enrolled, Collection<T> fought) {
        List<T> out = new ArrayList<>();
        for (T h : enrolled) {
            if (fought.contains(h) && !out.contains(h)) {
                out.add(h);
            }
        }
        return out;
    }

    /**
     * O que acontece com o item de um contrato de serviço quando o contrato muda de estado.
     */
    public enum ItemFate {
        /** Fica guardado na caixa do contrato, à espera do aceite. */
        GUARDADO,
        /** Está com o artesão, preso ao inventário dele. */
        COM_O_ARTESAO,
        /** O item trabalhado vai para o dono. */
        ENTREGUE_AO_DONO,
        /** O item volta para o dono como ele o entregou. */
        DEVOLVIDO_AO_DONO
    }

    /** Onde o item de um contrato de serviço está (ou para onde vai) em cada estado. */
    public static ItemFate itemFate(State state) {
        return switch (state) {
            case ABERTO, AGUARDANDO -> ItemFate.GUARDADO;
            case CONTRATADO, OFERTA -> ItemFate.COM_O_ARTESAO;
            case ENTREGUE -> ItemFate.ENTREGUE_AO_DONO;
            case CANCELADO, VENCIDO, ATRASADO -> ItemFate.DEVOLVIDO_AO_DONO;
        };
    }

    /**
     * O item que o artesão tem na mão é o do contrato?
     *
     * @param boundTo o contrato a que o item da mão está preso (0 = item comum)
     */
    public static boolean serviceItem(long contract, long boundTo) {
        return contract > 0 && contract == boundTo;
    }

    /** Um item entregue ao artesão só é recolhido se o banco foi lido depois da entrega (com folga): nunca por engano. */
    public static boolean purge(boolean contractActive, long handedAt, long refreshedAt, long slackMs) {
        return !contractActive && refreshedAt > 0 && handedAt + slackMs < refreshedAt;
    }

    /** O resultado de conferir um item com as exigências do pedido. */
    public enum Check {
        OK,
        /** Não é o item pedido. */
        PRODUTO,
        /** É o item, mas abaixo da nota mínima (qualidade, pureza, tamanho). */
        NOTA,
        /** É o item, na nota, mas em quantidade menor. */
        QUANTIDADE
    }

    /** A taxa do quadro sobre a mão de obra (sai de circulação). */
    public static long fee(long price, double percent) {
        return (long) Math.floor(Math.max(0, price) * Math.max(0, Math.min(100, percent)) / 100.0);
    }

    /** O que o artesão recebe: a mão de obra menos a taxa do quadro. */
    public static long pay(long price, double percent) {
        return Math.max(0, price) - fee(price, percent);
    }

    /** O que volta ao cliente quando a proposta é aceita: a diferença entre o orçamento e o preço. */
    public static long change(long budget, long price) {
        return Math.max(0, budget - price);
    }

    /**
     * Confere a entrega com o pedido.
     *
     * @param got     id do produto que o item é (null: não é produto de profissão)
     * @param grade   nota do item (qualidade, pureza, tamanho...)
     * @param amount  quantos o artesão tem na mão
     */
    public static Check check(String wanted, int minGrade, int quantity, String got, int grade, int amount) {
        if (got == null || !got.equalsIgnoreCase(wanted)) {
            return Check.PRODUTO;
        }
        if (grade < minGrade) {
            return Check.NOTA;
        }
        return amount < quantity ? Check.QUANTIDADE : Check.OK;
    }

    /**
     * O histórico de um artesão.
     *
     * @param reviews   avaliações recebidas
     * @param stars     soma das estrelas
     * @param done      contratos concluídos
     * @param cancelled contratos que ele cancelou
     * @param late      contratos que passaram do prazo
     */
    public record Stats(UUID artisan, String name, int reviews, int stars, int done, int cancelled, int late) {

        /** Média de estrelas (0 sem avaliação). */
        public double average() {
            return reviews <= 0 ? 0 : stars / (double) reviews;
        }

        public Stats plusReview(int s) {
            return new Stats(artisan, name, reviews + 1, stars + s, done, cancelled, late);
        }
    }

    /** O ranking de artesãos: pela média de estrelas e, no empate, por quem concluiu mais contratos. */
    public static final Comparator<Stats> RANKING = Comparator.comparingDouble(Stats::average).reversed()
            .thenComparing(Comparator.comparingInt(Stats::done).reversed())
            .thenComparing(Stats::name, String.CASE_INSENSITIVE_ORDER);
}
