package com.aincrad.life.model;

import java.util.Locale;
import java.util.Set;

/**
 * Algo que o artesão precisa aprender antes de produzir: uma receita, um projeto de forja ou uma
 * fórmula. Existe como item (pergaminho, livro, planta); usá-lo ensina e consome o item.
 *
 * @param kind       "receita", "projeto" ou "formula" (ou outro, registrado por um módulo)
 * @param category   categoria mostrada mesmo para quem não conhece ("???" + categoria)
 * @param studyMin   proficiência mínima para aprender
 * @param produceMin proficiência mínima para produzir (igual ou maior que a de estudo)
 * @param unique     não pode ser transcrito
 * @param path       caminho da árvore que o item exige (null = qualquer um)
 * @param spec       especialização que o item exige (null = qualquer uma do caminho)
 */
public record Learnable(String kind, String id, String name, String category, Profession profession, int studyMin,
                        int produceMin, boolean unique, String path, String spec) {

    /** Resultado de tentar aprender. */
    public enum Check {
        OK, JA_SABE, PROFICIENCIA, CAMINHO_FECHADO,
        /** O caminho é o certo, mas falta comprar na árvore o nó que libera estes itens. */
        FALTA_NO
    }

    public Learnable {
        kind = kind.toLowerCase(Locale.ROOT);
        id = id.toLowerCase(Locale.ROOT);
        produceMin = Math.max(studyMin, produceMin);
    }

    /** Chave única: "tipo:id". */
    public String key() {
        return kind + ":" + id;
    }

    /**
     * Pode aprender agora? Item de outro caminho nunca; com o caminho ainda não escolhido, um item
     * exclusivo também não (o jogador precisa escolher antes).
     */
    public Check canLearn(double proficiency, Set<String> known, Set<String> treeOwned) {
        if (known.contains(key())) {
            return Check.JA_SABE;
        }
        if (!TreeDef.allows(path, spec, treeOwned)) {
            return Check.CAMINHO_FECHADO;
        }
        return proficiency < studyMin ? Check.PROFICIENCIA : Check.OK;
    }

    /** Conhece e tem a proficiência de produção? */
    public boolean canProduce(double proficiency, Set<String> known) {
        return known.contains(key()) && proficiency >= produceMin;
    }
}
