package com.aincrad.ui.pack;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * Hotbar em bolinhas, desenhada em 4× a resolução do Minecraft (o jogo estica o sprite para o
 * tamanho original, então as posições dos 9 espaços continuam as mesmas).
 */
final class HotbarArt {

    private static final int S = 4;
    private static final Color FILL = new Color(10, 14, 18, 120);
    private static final Color RING = new Color(255, 255, 255, 170);
    private static final Color SELECTED = new Color(0xF5, 0xA6, 0x23, 255);

    private HotbarArt() {
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Círculo com centro (cx, cy) e raio r, em pixels do Minecraft. */
    private static void circle(Graphics2D g, double cx, double cy, double r, Color fill, Color ring, double ringWidth) {
        Ellipse2D e = new Ellipse2D.Double((cx - r) * S, (cy - r) * S, 2 * r * S, 2 * r * S);
        if (fill != null) {
            g.setColor(fill);
            g.fill(e);
        }
        g.setColor(ring);
        g.setStroke(new BasicStroke((float) (ringWidth * S)));
        g.draw(e);
    }

    /** 182×22: nove espaços de 20 px, o item fica em (3 + 20i, 3). */
    static BufferedImage hotbar() {
        BufferedImage img = new BufferedImage(182 * S, 22 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        for (int i = 0; i < 9; i++) {
            circle(g, 11 + 20 * i, 11, 9.25, FILL, RING, 0.75);
        }
        g.dispose();
        return img;
    }

    /** 24×23, desenhado 1 px acima e à esquerda do espaço escolhido. */
    static BufferedImage selection() {
        BufferedImage img = new BufferedImage(24 * S, 23 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        circle(g, 12, 12, 10.25, null, SELECTED, 1.5);
        g.dispose();
        return img;
    }

    /** 29×24: o espaço da mão secundária (item em x = 3 à esquerda, x = 10 à direita; y = 4). */
    static BufferedImage offhand(boolean left) {
        BufferedImage img = new BufferedImage(29 * S, 24 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        circle(g, (left ? 3 : 10) + 8, 12, 9.25, FILL, RING, 0.75);
        g.dispose();
        return img;
    }
}
