package com.aincrad.economy;

import com.aincrad.economy.auction.AuctionMath;
import com.aincrad.economy.house.House;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionMathTest {

    @Test
    void prazoDoAnuncio() {
        assertEquals(24, AuctionMath.duration(0, 24, 72));
        assertEquals(10, AuctionMath.duration(10, 24, 72));
        assertEquals(72, AuctionMath.duration(500, 24, 72));
        assertEquals(1, AuctionMath.duration(-3, 0, 72));
    }

    @Test
    void taxas() {
        assertEquals(950, AuctionMath.proceeds(1000, 0.05));
        assertEquals(1000, AuctionMath.proceeds(1000, 0));
        assertEquals(19, AuctionMath.proceeds(19, 0.05)); // 0,95 de taxa arredonda para 0
        assertEquals(10, AuctionMath.listingFee(1000, 0.01));
    }

    @Test
    void acessoDaCasa() {
        UUID dono = UUID.randomUUID();
        UUID amigo = UUID.randomUUID();
        UUID estranho = UUID.randomUUID();
        House h = new House("a", "casa_a", 5000, 1, dono, "Kirito", amigo + ",lixo, ", null);
        assertTrue(h.canUse(dono));
        assertTrue(h.canUse(amigo));
        assertFalse(h.canUse(estranho));
        assertEquals(amigo.toString(), h.membersText());
        h.owner(null, null);
        assertTrue(h.forSale());
        assertFalse(h.canUse(amigo)); // vender limpa os convidados
    }
}
