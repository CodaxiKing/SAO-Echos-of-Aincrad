package com.aincrad.reputation.model;

import java.util.Locale;

/**
 * A cor do cursor, como no anime: verde (normal), laranja para quem atacou um jogador verde e
 * vermelho para quem matou um. Só regras, sem servidor.
 */
public final class CursorRules {

    private CursorRules() {
    }

    /** A cor do cursor de um jogador. */
    public enum Color {
        VERDE, LARANJA, VERMELHO;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * O que se guarda de um jogador.
     *
     * @param orangeUntil até quando está laranja (milissegundos; 0 = não está)
     * @param redUntil    até quando está vermelho
     * @param kills       jogadores verdes que ele já matou
     */
    public record Cursor(long orangeUntil, long redUntil, int kills) {

        public static final Cursor CLEAN = new Cursor(0, 0, 0);

        /** A cor agora: vermelho vence laranja. */
        public Color color(long now) {
            if (redUntil > now) {
                return Color.VERMELHO;
            }
            return orangeUntil > now ? Color.LARANJA : Color.VERDE;
        }
    }

    /**
     * Um jogador atacou outro. Atacar um verde deixa laranja por um tempo (e renova, se já estava).
     * Atacar um laranja ou um vermelho não muda nada: é legítima defesa ou caça a criminoso.
     */
    public static Cursor attacked(Cursor attacker, Color victim, long now, long orangeMs) {
        if (victim != Color.VERDE) {
            return attacker;
        }
        return new Cursor(Math.max(attacker.orangeUntil(), now + orangeMs), attacker.redUntil(), attacker.kills());
    }

    /**
     * Um jogador matou outro. Matar um verde deixa vermelho; cada morte causada aumenta o tempo:
     * a primeira vale {@code redMs}, a segunda {@code redMs × (1 + growth)}, e assim por diante.
     * O tempo novo soma ao que ainda restava. Matar um laranja ou um vermelho não muda nada.
     */
    public static Cursor killed(Cursor attacker, Color victim, long now, long redMs, double growth) {
        if (victim != Color.VERDE) {
            return attacker;
        }
        long add = Math.round(redMs * (1 + Math.max(0, growth) * attacker.kills()));
        return new Cursor(attacker.orangeUntil(), Math.max(attacker.redUntil(), now) + add, attacker.kills() + 1);
    }

    /** O tempo de prisão de um vermelho derrotado por outro jogador: proporcional às mortes que causou, até um teto. */
    public static long jailMs(int kills, long perKillMs, long maxMs) {
        return Math.min(Math.max(0, maxMs), Math.max(1, kills) * Math.max(0, perKillMs));
    }

    /** Cumprida a prisão, o jogador deixa de ser vermelho e volta laranja por um tempo. */
    public static Cursor released(Cursor c, long now, long orangeMs) {
        return new Cursor(now + orangeMs, 0, c.kills());
    }

    /** Laranja e vermelho não entram nas cidades. */
    public static boolean barredFromCity(Color c) {
        return c != Color.VERDE;
    }

    /** Um vermelho morto por outro jogador vai preso. */
    public static boolean goesToJail(Color victim, boolean killedByPlayer) {
        return victim == Color.VERMELHO && killedByPlayer;
    }
}
