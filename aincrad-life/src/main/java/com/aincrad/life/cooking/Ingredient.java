package com.aincrad.life.cooking;

/**
 * Um ingrediente de ingredients.yml.
 *
 * @param rank     de E a S
 * @param slot     em que encaixe do prato ele entra
 * @param material item do Minecraft que o representa
 * @param custom   true: só vale o item próprio (com a marca do plugin, vindo de drop, loja ou
 *                 comando); false: qualquer item comum desse material serve
 * @param origin   de onde vem (texto para o jogador)
 */
public record Ingredient(String id, String name, String rank, SlotKind slot, String material, boolean custom, String origin) {

    private static final String RANKS = "EDCBAS";

    /** Posição do rank, de 0 (E) a 5 (S). */
    public int rankIndex() {
        return Math.max(0, RANKS.indexOf(rank));
    }

    public static boolean validRank(String r) {
        return r != null && r.length() == 1 && RANKS.contains(r);
    }
}
