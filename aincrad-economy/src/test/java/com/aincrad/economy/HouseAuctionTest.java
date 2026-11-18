package com.aincrad.economy;

import com.aincrad.economy.house.HouseAuctionService;
import com.aincrad.economy.house.HouseAuctionService.Auction;
import com.aincrad.economy.house.HouseAuctionService.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** As regras do leilão de casas (etapa 50), sem servidor. */
class HouseAuctionTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_000 * HOUR;

    private static Auction open(long bid, String bidder) {
        return new Auction("cabana", 10_000, 48, NOW + 10 * HOUR, bidder, bidder == null ? null : "Kirito", bid, bidder == null ? null : "hold", true);
    }

    @Test
    void oMaiorLanceLevaECadaLancePrecisaSuperarOAnterior() {
        // Sem lances, vale o mínimo.
        assertEquals(10_000, HouseAuctionService.minNext(10_000, 0, 0.05));
        // Com lance de 10.000 e incremento de 5%, o próximo é 10.500.
        assertEquals(10_500, HouseAuctionService.minNext(10_000, 10_000, 0.05));
        // Sem incremento, basta 1 Col a mais.
        assertEquals(10_001, HouseAuctionService.minNext(10_000, 10_000, 0));
        assertNull(HouseAuctionService.blocked(open(0, null), NOW, 10_000, 0.05, false, false));
        assertEquals(Block.BAIXO, HouseAuctionService.blocked(open(0, null), NOW, 9_999, 0.05, false, false));
        assertEquals(Block.BAIXO, HouseAuctionService.blocked(open(10_000, "a"), NOW, 10_499, 0.05, false, false));
        assertNull(HouseAuctionService.blocked(open(10_000, "a"), NOW, 10_500, 0.05, false, false));
    }

    @Test
    void quandoOLanceNaoVale() {
        assertEquals(Block.SEM_LEILAO, HouseAuctionService.blocked(null, NOW, 99_999, 0.05, false, false));
        assertEquals(Block.JA_E_SEU, HouseAuctionService.blocked(open(10_000, "a"), NOW, 99_999, 0.05, true, false));
        assertEquals(Block.LIMITE_DE_CASAS, HouseAuctionService.blocked(open(10_000, "a"), NOW, 99_999, 0.05, false, true));
        // O prazo acabou, ou a casa tem dono (o leilão só reabre quando ele sair).
        assertEquals(Block.FECHADO, HouseAuctionService.blocked(open(0, null), NOW + 10 * HOUR, 99_999, 0.05, false, false));
        Auction owned = new Auction("cabana", 10_000, 48, NOW + 10 * HOUR, "a", "Kirito", 12_000, "hold", false);
        assertEquals(Block.FECHADO, HouseAuctionService.blocked(owned, NOW, 99_999, 0.05, false, false));
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                HouseAuctionTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (Block b : Block.values()) {
            assertNotNull(msg.getString("leilao_casa.bloqueio." + b.name().toLowerCase(Locale.ROOT)), b.name());
        }
    }

    @Test
    void umLanceNoFimEsticaOPrazo() {
        long window = 5 * 60_000L;
        long ends = NOW + HOUR;
        // Falta uma hora: o prazo não muda.
        assertEquals(ends, HouseAuctionService.extended(ends, NOW, window));
        // Faltam 2 minutos: passa a faltar 5.
        assertEquals(ends - 2 * 60_000L + window, HouseAuctionService.extended(ends, ends - 2 * 60_000L, window));
        assertEquals(ends, HouseAuctionService.extended(ends, ends - 2 * 60_000L, 0));
    }
}
