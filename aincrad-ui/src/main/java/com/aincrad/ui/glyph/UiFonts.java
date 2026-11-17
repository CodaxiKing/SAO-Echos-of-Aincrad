package com.aincrad.ui.glyph;

import net.kyori.adventure.key.Key;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Fontes de texto do pack e a medição de largura dos textos, necessária para alinhar as linhas
 * do submenu (todas com a mesma largura de fundo) e posicionar a HUD.
 */
public final class UiFonts {

    /** Fonte SAO UI (TTF), com reserva na fonte padrão para letras que ela não tem. */
    public static final Key SAO = Key.key("aincrad", "sao");
    /** Fonte padrão rebaixada: a linha de números embaixo da barra de vida. */
    public static final Key HUD_LOW = Key.key("aincrad", "hud_low");

    public static final String SAO_TTF = "sao_ui.ttf";
    /** Altura da fonte SAO em pixels de fonte (o texto padrão tem 8 de letra + 1 de espaço). */
    public static final float SAO_SIZE = 11.0f;
    public static final float SAO_SHIFT_Y = 0.0f;
    /** Subida da fonte rebaixada: 0 desce o texto 7 px em relação ao normal. */
    public static final int HUD_LOW_ASCENT = 0;

    private static volatile Font sao;
    /** Tamanho AWT que dá a mesma escala do jogo (o jogo usa altura de linha = SAO_SIZE). */
    private static volatile float saoAwtSize;

    private UiFonts() {
    }

    /** Lê a TTF do pack para medir os textos. Sem ela, mede pela fonte padrão. */
    public static void load(File ttf, Logger log) {
        sao = null;
        if (ttf == null || !ttf.isFile()) {
            return;
        }
        try {
            Font f = Font.createFont(Font.TRUETYPE_FONT, ttf);
            LineMetrics lm = f.deriveFont(100f).getLineMetrics("Ag", frc());
            float unitHeight = (lm.getAscent() + lm.getDescent()) / 100f;
            saoAwtSize = unitHeight > 0 ? SAO_SIZE / unitHeight : SAO_SIZE;
            sao = f.deriveFont(saoAwtSize);
        } catch (Exception e) {
            if (log != null) {
                log.warning("Não foi possível ler " + ttf.getName() + " para medir textos: " + e.getMessage());
            }
        }
    }

    public static boolean saoAvailable() {
        return sao != null;
    }

    private static FontRenderContext frc() {
        return new FontRenderContext(null, true, true);
    }

    /** Largura (pixels de fonte, arredondada para cima) de um texto na fonte SAO. */
    public static int saoWidth(String text) {
        Font f = sao;
        if (f == null || text.isEmpty()) {
            return TextWidth.of(text);
        }
        // Letras que a TTF não tem caem na fonte padrão (provider "reference" do pack).
        double total = 0;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (f.canDisplay(c)) {
                run.append(c);
            } else {
                total += f.getStringBounds(run.toString(), frc()).getWidth();
                run.setLength(0);
                total += TextWidth.of(c);
            }
        }
        total += f.getStringBounds(run.toString(), frc()).getWidth();
        return (int) Math.ceil(total);
    }

    /**
     * As 16 linhas da textura minecraft:font/ascii.png; as linhas 2 a 7 são o ASCII imprimível.
     * As outras ficam com \u0000 (ignorado pelo jogo).
     */
    public static List<String> asciiRows() {
        List<String> rows = new ArrayList<>(16);
        String empty = "\u0000".repeat(16);
        for (int r = 0; r < 16; r++) {
            if (r < 2 || r > 7) {
                rows.add(empty);
                continue;
            }
            StringBuilder sb = new StringBuilder(16);
            for (int c = 0; c < 16; c++) {
                int code = r * 16 + c;
                sb.append(code == 0x7F ? '\u0000' : (char) code);
            }
            rows.add(sb.toString());
        }
        return rows;
    }
}
