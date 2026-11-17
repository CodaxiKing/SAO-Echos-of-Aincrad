package com.aincrad.life.cooking;

import java.util.List;
import java.util.Map;

/**
 * Uma receita de recipes.yml: o que vai em cada encaixe, as etapas de preparo e o buff do prato.
 *
 * @param slots      ingrediente de cada encaixe (os quatro obrigatórios e, se a receita pedir, o Especial)
 * @param studyMin   proficiência para aprender a receita
 * @param produceMin proficiência para cozinhar
 * @param difficulty dificuldade, para o ganho de proficiência e a chance de "excelente"
 * @param secret     não existe como livro: é descoberta ao acertar a combinação
 * @param portions   quantos pratos saem (1 = prato individual; mais = prato de panela)
 * @param path       caminho da árvore que a receita exige (null = qualquer um)
 * @param item       material do prato pronto (precisa ser comestível)
 */
public record Recipe(String id, String name, Map<SlotKind, String> slots, int studyMin, int produceMin, int difficulty,
                     List<Step> steps, Buff buff, boolean secret, int portions, String path, String spec, String item) {

    /** O buff do prato: tipo, valor (%, pontos de vida ou nível, conforme o tipo) e duração. */
    public record Buff(FoodBuff.Type type, double value, int minutes) {
    }

    /** Prato de panela (rende várias porções)? */
    public boolean pot() {
        return portions > 1;
    }

    /** Chave da combinação de ingredientes, na ordem dos encaixes. */
    public static String combo(Map<SlotKind, String> slots, boolean withSpecial) {
        StringBuilder sb = new StringBuilder();
        for (SlotKind k : SlotKind.values()) {
            if (k == SlotKind.ESPECIAL && !withSpecial) {
                continue;
            }
            String v = slots.get(k);
            sb.append(v == null ? "-" : v).append('|');
        }
        return sb.toString();
    }
}
