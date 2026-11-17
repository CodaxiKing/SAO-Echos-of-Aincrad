package com.aincrad.life.cooking;

/**
 * A etapa do fogo: a chama cai sozinha com o tempo; pôr lenha sobe, abafar desce. O cozinheiro
 * mantém a chama dentro da faixa certa pelo tempo pedido. Quente demais por muito tempo, queima.
 * Só regras: quem desenha e ouve os cliques é a {@link CookingSession}.
 */
public final class FireStep {

    private final double low;
    private final double high;
    private final int duration;
    private final double decay;
    private final int burnTicks;
    private double heat;
    private int elapsed;
    private int inBand;
    private int tooHot;

    /**
     * @param low       início da faixa certa (0 a 1)
     * @param high      fim da faixa certa
     * @param duration  quanto tempo a etapa dura, em ticks
     * @param decay     quanto a chama cai por tick
     * @param burnTicks ticks seguidos acima da faixa que queimam o prato
     */
    public FireStep(double low, double high, int duration, double decay, int burnTicks) {
        this.low = Math.max(0, Math.min(low, high));
        this.high = Math.min(1, Math.max(low, high));
        this.duration = Math.max(1, duration);
        this.decay = Math.max(0, decay);
        this.burnTicks = Math.max(1, burnTicks);
        // Começa um pouco abaixo da faixa: o cozinheiro acende o fogo.
        this.heat = Math.max(0, this.low - 0.1);
    }

    /** Um tick: a chama cai e o tempo conta. */
    public void tick() {
        if (done()) {
            return;
        }
        heat = Math.max(0, heat - decay);
        elapsed++;
        if (heat >= low && heat <= high) {
            inBand++;
        }
        tooHot = heat > high ? tooHot + 1 : 0;
    }

    /** Lenha (valor positivo) ou abafar (negativo). */
    public void add(double amount) {
        heat = Math.max(0, Math.min(1, heat + amount));
    }

    public double heat() {
        return heat;
    }

    public double low() {
        return low;
    }

    public double high() {
        return high;
    }

    public boolean inBand() {
        return heat >= low && heat <= high;
    }

    /** Ficou quente demais por tempo demais. */
    public boolean burned() {
        return tooHot >= burnTicks;
    }

    public boolean done() {
        return elapsed >= duration || burned();
    }

    /** Fração do tempo que já passou. */
    public double progress() {
        return elapsed / (double) duration;
    }

    /**
     * Nota da etapa, de 0 a 1: a fração do tempo dentro da faixa. Ficar 90% do tempo na faixa
     * já vale a nota cheia (os primeiros instantes são para acender).
     */
    public double score() {
        return Math.min(1, inBand / (duration * 0.9));
    }
}
