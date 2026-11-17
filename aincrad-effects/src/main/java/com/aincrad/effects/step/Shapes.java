package com.aincrad.effects.step;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pontos (relativos à origem) das formas de partícula. Tudo determinístico, para poder testar. */
public final class Shapes {

    public enum Shape {
        PONTO, CIRCULO, COLUNA, ESPIRAL, EXPLOSAO, LINHA;

        public static Shape parse(String s) {
            return switch (s == null ? "ponto" : s.trim().toLowerCase(Locale.ROOT)) {
                case "circulo", "círculo", "anel", "circle" -> CIRCULO;
                case "coluna", "column" -> COLUNA;
                case "espiral", "spiral" -> ESPIRAL;
                case "explosao", "explosão", "explosion" -> EXPLOSAO;
                case "linha", "line" -> LINHA;
                case "ponto", "point" -> PONTO;
                default -> throw new IllegalArgumentException("forma desconhecida: " + s
                        + " (use ponto, circulo, coluna, espiral, explosao ou linha)");
            };
        }
    }

    private static final double GOLDEN = Math.PI * (3 - Math.sqrt(5));

    private Shapes() {
    }

    /** Círculo horizontal de raio r. */
    public static List<Vector> circle(double r, int n) {
        List<Vector> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            out.add(new Vector(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        return out;
    }

    /** Coluna vertical de altura h; com r &gt; 0 os pontos giram em volta do eixo. */
    public static List<Vector> column(double r, double h, int n) {
        List<Vector> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double y = n == 1 ? 0 : h * i / (n - 1);
            double a = i * GOLDEN;
            out.add(new Vector(Math.cos(a) * r, y, Math.sin(a) * r));
        }
        return out;
    }

    /** Espiral subindo {@code turns} voltas até a altura h. */
    public static List<Vector> spiral(double r, double h, double turns, int n) {
        List<Vector> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : (double) i / (n - 1);
            double a = 2 * Math.PI * turns * t;
            out.add(new Vector(Math.cos(a) * r, h * t, Math.sin(a) * r));
        }
        return out;
    }

    /** Direções unitárias espalhadas por igual numa esfera (para partículas que voam para fora). */
    public static List<Vector> sphereDirections(int n) {
        List<Vector> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double y = n == 1 ? 0 : 1 - 2.0 * i / (n - 1);
            double radius = Math.sqrt(Math.max(0, 1 - y * y));
            double a = i * GOLDEN;
            out.add(new Vector(Math.cos(a) * radius, y, Math.sin(a) * radius));
        }
        return out;
    }

    /** Linha de comprimento {@code length} na direção dada. */
    public static List<Vector> line(Vector direction, double length, int n) {
        Vector d = direction.clone();
        if (d.lengthSquared() < 1e-9) {
            d = new Vector(0, 0, 1);
        }
        d.normalize();
        List<Vector> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : length * i / (n - 1);
            out.add(d.clone().multiply(t));
        }
        return out;
    }
}
