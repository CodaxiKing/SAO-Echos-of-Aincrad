package com.aincrad.ui.items;

import java.util.Locale;

/** Filtro da tela de Itens. A ordem é a ordem em que o botão de filtro passa por eles. */
public enum ItemCategory {
    TUDO("tudo"),
    ARMAS("armas"),
    ARMADURAS("armaduras"),
    CONSUMIVEIS("consumiveis"),
    BLOCOS("blocos"),
    OUTROS("outros");

    private final String key;

    ItemCategory(String key) {
        this.key = key;
    }

    /** Chave em messages.yml (itens.categoria.&lt;chave&gt;). */
    public String key() {
        return key;
    }

    public ItemCategory next() {
        ItemCategory[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * Categoria de um material, a partir do nome e de propriedades simples (sem depender do
     * registro do servidor, para poder ser testada).
     *
     * @param equipSlot nome do EquipmentSlot do material (HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET, BODY...)
     */
    public static ItemCategory of(String material, boolean edible, boolean block, String equipSlot) {
        String m = material.toUpperCase(Locale.ROOT);
        if (m.endsWith("_SWORD") || m.endsWith("_AXE") || m.endsWith("_SPEAR") || m.equals("BOW")
                || m.equals("CROSSBOW") || m.equals("TRIDENT") || m.equals("MACE")) {
            return ARMAS;
        }
        if (equipSlot != null && switch (equipSlot) {
            case "HEAD", "CHEST", "LEGS", "FEET" -> !block || m.endsWith("_HEAD") || m.endsWith("_SKULL") || m.equals("CARVED_PUMPKIN");
            case "OFF_HAND" -> m.equals("SHIELD");
            default -> false;
        }) {
            return ARMADURAS;
        }
        if (edible || m.endsWith("POTION") || m.equals("MILK_BUCKET") || m.equals("HONEY_BOTTLE")
                || m.equals("ENCHANTED_GOLDEN_APPLE") || m.equals("TOTEM_OF_UNDYING")) {
            return CONSUMIVEIS;
        }
        if (block) {
            return BLOCOS;
        }
        return OUTROS;
    }

    public boolean accepts(ItemCategory c) {
        return this == TUDO || this == c;
    }
}
