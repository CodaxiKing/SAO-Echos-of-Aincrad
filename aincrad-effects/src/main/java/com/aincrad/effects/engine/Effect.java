package com.aincrad.effects.engine;

import com.aincrad.effects.step.Step;

import java.util.List;

/**
 * Um efeito: sequência de passos com tempo.
 *
 * @param audience quem vê por padrão (null = publico_padrao da config)
 * @param source   arquivo de onde veio, para mensagens
 */
public record Effect(String id, Audience audience, List<Step> steps, String source) {

    /** Ticks até o último passo terminar. */
    public int length() {
        int max = 0;
        for (Step s : steps) {
            max = Math.max(max, s.at() + s.repeat() * s.interval() + s.duration());
        }
        return max;
    }
}
