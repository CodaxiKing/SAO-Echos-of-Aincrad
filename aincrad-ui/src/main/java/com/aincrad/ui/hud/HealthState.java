package com.aincrad.ui.hud;

/** Faixa da barra de vida: verde, amarela (abaixo de 50%) e vermelha (abaixo de 25%). */
public enum HealthState {
    NORMAL, WARNING, CRITICAL;

    public static HealthState of(double ratio, double yellowBelow, double redBelow) {
        if (ratio < redBelow) {
            return CRITICAL;
        }
        if (ratio < yellowBelow) {
            return WARNING;
        }
        return NORMAL;
    }

    /** Quantos segmentos ficam cheios: arredonda para cima, para 1 de vida ainda mostrar algo. */
    public static int filledSegments(double ratio, int segments) {
        if (ratio <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(segments, (int) Math.ceil(ratio * segments)));
    }
}
