package com.aincrad.economy.auction;

import com.aincrad.economy.EcoMath;

/** Contas do leilão (testáveis sem servidor). */
public final class AuctionMath {

    private AuctionMath() {
    }

    /** Prazo em horas: 0 ou negativo = padrão; acima do máximo = máximo. */
    public static int duration(int asked, int def, int max) {
        int d = asked <= 0 ? def : asked;
        return Math.max(1, Math.min(max, d));
    }

    /** Taxa paga ao anunciar (fração do preço), arredondada para baixo. */
    public static long listingFee(long price, double rate) {
        return EcoMath.tax(price, rate);
    }

    /** Quanto o vendedor recebe depois da taxa de venda. */
    public static long proceeds(long price, double saleRate) {
        return price - EcoMath.tax(price, saleRate);
    }
}
