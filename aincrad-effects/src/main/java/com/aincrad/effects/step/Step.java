package com.aincrad.effects.step;

import com.aincrad.effects.engine.Playback;
import com.aincrad.effects.engine.Spec;

/**
 * Um passo de um efeito. Campos comuns a todos os tipos:
 * <ul>
 *   <li>{@code em}: quando começa, em segundos depois do início do efeito;</li>
 *   <li>{@code repetir}: quantas vezes repete depois da primeira (0 = só uma vez);</li>
 *   <li>{@code intervalo}: segundos entre as repetições.</li>
 * </ul>
 */
public abstract class Step {

    private final int at;
    private final int repeat;
    private final int interval;

    protected Step(Spec s) {
        this.at = s.ticks("em", 0);
        this.repeat = Math.max(0, s.integer("repetir", 0));
        this.interval = Math.max(1, s.ticks("intervalo", 0.5));
    }

    public final int at() {
        return at;
    }

    public final int repeat() {
        return repeat;
    }

    public final int interval() {
        return interval;
    }

    /** Ticks que o passo continua ativo depois de começar (textos, barras, fades). */
    public int duration() {
        return 0;
    }

    public abstract void run(Playback pb);
}
