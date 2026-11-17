package com.aincrad.economy;

/** Contas puras da economia (testáveis sem servidor). */
public final class EcoMath {

    private EcoMath() {
    }

    /** "1.234.567" (separador de milhar com ponto, como no Brasil). */
    public static String format(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append('.');
            }
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    /** Taxa arredondada para baixo (quem paga nunca perde mais que a fração). */
    public static long tax(long amount, double rate) {
        if (amount <= 0 || rate <= 0) {
            return 0;
        }
        return (long) Math.floor(amount * Math.min(1, rate));
    }

    /**
     * Col de um abate: {@code level × porLevel}, com variação de ±{@code variance} pelo
     * {@code roll} (0 a 1; 0.5 = sem variação). Nunca negativo.
     */
    public static long mobCol(int level, double perLevel, double variance, double roll) {
        if (level <= 0 || perLevel <= 0) {
            return 0;
        }
        double base = level * perLevel;
        double factor = 1 + Math.max(0, variance) * (roll * 2 - 1);
        return Math.max(0, Math.round(base * factor));
    }

    /** Soma sem estourar o long (saldo gigante vira o máximo, não negativo). */
    public static long add(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) {
            return b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return r;
    }

    /** Lê uma quantidade digitada: "1500", "1.500", "1,5k", "2m". Inválido ou negativo: -1. */
    public static long parse(String s) {
        if (s == null || s.isBlank()) {
            return -1;
        }
        String t = s.trim().toLowerCase(java.util.Locale.ROOT);
        long mult = 1;
        if (t.endsWith("k")) {
            mult = 1_000;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m")) {
            mult = 1_000_000;
            t = t.substring(0, t.length() - 1);
        }
        try {
            if (mult == 1) {
                long v = Long.parseLong(t.replace(".", ""));
                return v < 0 ? -1 : v;
            }
            double d = Double.parseDouble(t.replace(',', '.'));
            if (d < 0 || Double.isNaN(d) || d * mult > Long.MAX_VALUE) {
                return -1;
            }
            return Math.round(d * mult);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
