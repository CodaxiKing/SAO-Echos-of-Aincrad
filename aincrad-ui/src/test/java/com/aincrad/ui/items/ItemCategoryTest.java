package com.aincrad.ui.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCategoryTest {

    @Test
    void categorias() {
        assertEquals(ItemCategory.ARMAS, ItemCategory.of("DIAMOND_SWORD", false, false, "HAND"));
        assertEquals(ItemCategory.ARMAS, ItemCategory.of("BOW", false, false, "HAND"));
        assertEquals(ItemCategory.ARMADURAS, ItemCategory.of("IRON_CHESTPLATE", false, false, "CHEST"));
        assertEquals(ItemCategory.ARMADURAS, ItemCategory.of("SHIELD", false, false, "OFF_HAND"));
        assertEquals(ItemCategory.ARMADURAS, ItemCategory.of("PLAYER_HEAD", false, true, "HEAD"));
        assertEquals(ItemCategory.CONSUMIVEIS, ItemCategory.of("BREAD", true, false, "HAND"));
        assertEquals(ItemCategory.CONSUMIVEIS, ItemCategory.of("SPLASH_POTION", false, false, "HAND"));
        assertEquals(ItemCategory.BLOCOS, ItemCategory.of("STONE", false, true, "HAND"));
        assertEquals(ItemCategory.OUTROS, ItemCategory.of("STICK", false, false, "HAND"));
        // Bloco que vai na cabeça, mas não é capacete (ex.: abóbora entalhada conta, terra não usa HEAD).
        assertEquals(ItemCategory.ARMADURAS, ItemCategory.of("CARVED_PUMPKIN", false, true, "HEAD"));
    }

    @Test
    void filtroPassaPorTodos() {
        ItemCategory c = ItemCategory.TUDO;
        for (int i = 0; i < ItemCategory.values().length; i++) {
            c = c.next();
        }
        assertEquals(ItemCategory.TUDO, c);
        assertTrue(ItemCategory.TUDO.accepts(ItemCategory.BLOCOS));
        assertFalse(ItemCategory.ARMAS.accepts(ItemCategory.BLOCOS));
    }
}
