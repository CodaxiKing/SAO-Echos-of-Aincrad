package com.aincrad.ui.screen;

import org.bukkit.util.Vector;

/**
 * Plano vertical na frente do jogador onde o menu do modo cursor é desenhado.
 * Coordenadas (x, y) em blocos: x para a direita do jogador, y para cima, (0, 0) no centro.
 */
public final class PlaneFrame {

    private final Vector center;
    private final Vector forward;
    private final Vector right;
    private final Vector up;

    private PlaneFrame(Vector center, Vector forward, Vector right, Vector up) {
        this.center = center;
        this.forward = forward;
        this.right = right;
        this.up = up;
    }

    /**
     * @param eye      posição dos olhos
     * @param yaw      yaw do jogador em graus (só a direção horizontal conta, o menu fica de pé)
     * @param distance distância do plano até os olhos
     * @param offsetY  deslocamento vertical do centro do plano
     */
    public static PlaneFrame facing(Vector eye, float yaw, double distance, double offsetY) {
        double rad = Math.toRadians(yaw);
        Vector forward = new Vector(-Math.sin(rad), 0, Math.cos(rad)).normalize();
        Vector up = new Vector(0, 1, 0);
        Vector right = forward.clone().crossProduct(up).normalize();
        Vector center = eye.clone().add(forward.clone().multiply(distance)).add(new Vector(0, offsetY, 0));
        return new PlaneFrame(center, forward, right, up);
    }

    /** Ponto do mundo para a coordenada (x, y) do plano, recuado {@code depth} blocos para trás. */
    public Vector toWorld(double x, double y, double depth) {
        return center.clone()
                .add(right.clone().multiply(x))
                .add(up.clone().multiply(y))
                .add(forward.clone().multiply(depth));
    }

    public Vector toWorld(double x, double y) {
        return toWorld(x, y, 0);
    }

    /**
     * Onde o olhar encosta no plano.
     *
     * @return {x, y} no plano, ou null se o jogador estiver olhando para longe do plano
     */
    public double[] project(Vector eye, Vector look) {
        double denom = look.dot(forward);
        if (denom <= 1e-4) {
            return null;
        }
        double t = center.clone().subtract(eye).dot(forward) / denom;
        if (t <= 0) {
            return null;
        }
        Vector hit = eye.clone().add(look.clone().multiply(t)).subtract(center);
        return new double[]{hit.dot(right), hit.dot(up)};
    }

    /** O ponto (u, v) cai dentro do quadrado de lado {@code size} centrado em (x, y)? */
    public static boolean inside(double u, double v, double x, double y, double size) {
        double half = size / 2.0;
        return Math.abs(u - x) <= half && Math.abs(v - y) <= half;
    }

    public Vector forward() {
        return forward.clone();
    }

    public Vector center() {
        return center.clone();
    }
}
