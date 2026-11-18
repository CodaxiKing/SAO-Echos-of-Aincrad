package com.aincrad.life.fishing;

/**
 * A fase de cansar o peixe: puxar sobe a tensão da linha, soltar desce. Enquanto a tensão fica
 * na faixa verde, a força do peixe esvazia. Tensão no vermelho por tempo demais arrebenta a
 * linha; tensão baixa demais por tempo demais solta o peixe. Só regras: quem desenha e ouve os
 * cliques é a {@link FishingSession}.
 */
public final class TensionFight {

    /** Como a luta está. */
    public enum State {
        LUTANDO, PEGOU, ARREBENTOU, ESCAPOU
    }

    /**
     * Os números da luta.
     *
     * @param low        início da faixa verde (0 a 1)
     * @param high       fim da faixa verde
     * @param redFrom    a partir daqui a linha está no vermelho
     * @param looseBelow abaixo daqui a linha está frouxa
     * @param decay      quanto a tensão cai por tick
     * @param pull       quanto cada puxada sobe
     * @param burst      quanto a tensão sobe por tick numa arrancada não compensada
     * @param stamina    ticks na faixa verde para cansar o peixe
     * @param snapTicks  ticks seguidos no vermelho que arrebentam a linha
     * @param looseTicks ticks seguidos com a linha frouxa que soltam o peixe
     */
    public record Rules(double low, double high, double redFrom, double looseBelow, double decay, double pull, double burst,
                        int stamina, int snapTicks, int looseTicks) {
    }

    private final Rules rules;
    private double tension;
    private int stamina;
    private int red;
    private int loose;
    private State state = State.LUTANDO;

    public TensionFight(Rules rules) {
        this.rules = rules;
        this.tension = (rules.low() + rules.high()) / 2;
        this.stamina = Math.max(1, rules.stamina());
    }

    /** Uma puxada (clique). */
    public void pull() {
        if (state == State.LUTANDO) {
            tension = Math.min(1, tension + rules.pull());
        }
    }

    /**
     * Um tick.
     *
     * @param burst o peixe está numa arrancada que o pescador não compensou
     */
    public void tick(boolean burst) {
        if (state != State.LUTANDO) {
            return;
        }
        tension = Math.max(0, Math.min(1, tension - rules.decay() + (burst ? rules.burst() : 0)));
        if (inGreen()) {
            stamina--;
        }
        red = tension >= rules.redFrom() ? red + 1 : 0;
        loose = tension <= rules.looseBelow() ? loose + 1 : 0;
        if (red >= Math.max(1, rules.snapTicks())) {
            state = State.ARREBENTOU;
        } else if (loose >= Math.max(1, rules.looseTicks())) {
            state = State.ESCAPOU;
        } else if (stamina <= 0) {
            state = State.PEGOU;
        }
    }

    public boolean inGreen() {
        return tension >= rules.low() && tension <= rules.high();
    }

    public boolean inRed() {
        return tension >= rules.redFrom();
    }

    public double tension() {
        return tension;
    }

    /** Fração da força que ainda resta ao peixe (1 = inteiro, 0 = cansado). */
    public double staminaLeft() {
        return Math.max(0, stamina) / (double) Math.max(1, rules.stamina());
    }

    public Rules rules() {
        return rules;
    }

    public State state() {
        return state;
    }
}
