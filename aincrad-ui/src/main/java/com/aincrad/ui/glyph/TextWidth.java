package com.aincrad.ui.glyph;

/**
 * Largura aproximada (em pixels de interface) de um texto na fonte padrão do Minecraft,
 * já com o espaço de 1 px entre letras. Usada para alinhar a HUD à esquerda.
 */
public final class TextWidth {

    private TextWidth() {
    }

    public static int of(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += of(text.charAt(i));
        }
        return w;
    }

    public static int of(char c) {
        return switch (c) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
