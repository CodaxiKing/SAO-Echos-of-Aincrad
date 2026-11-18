package com.aincrad.rpg;

import com.aincrad.rpg.item.UniqueItemService;
import com.aincrad.rpg.item.UniqueItemService.Entry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras dos itens únicos do servidor (etapa 51), sem servidor. */
class UniqueItemRulesTest {

    private static final long DAY = 86_400_000L;

    @Test
    void soOExemplarComASerieDoBancoVale() {
        Entry now = new Entry("serie-nova", "Kirito", 0, 0);
        assertTrue(UniqueItemService.genuine("serie-nova", now));
        // O item voltou a cair e ganhou outra série: a cópia antiga, guardada num baú, se desfaz.
        assertFalse(UniqueItemService.genuine("serie-velha", now));
        // Um item único sem registro no banco não vale.
        assertFalse(UniqueItemService.genuine("serie-nova", null));
    }

    @Test
    void seODonoSomeOItemVoltaAPoderCair() {
        long seen = 100 * DAY;
        assertFalse(UniqueItemService.stale(seen, seen + 29 * DAY, 30));
        assertTrue(UniqueItemService.stale(seen, seen + 30 * DAY, 30));
        // 0 dias = o item nunca volta a cair.
        assertFalse(UniqueItemService.stale(seen, seen + 9_999 * DAY, 0));
    }
}
