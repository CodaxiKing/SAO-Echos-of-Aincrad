package com.aincrad.ui.api;

/**
 * Pincel de uma tela plana. Coordenadas em pixels de interface: {@code x} a partir do centro da
 * tela (negativo = esquerda), {@code y} a partir do topo. A área segura é x de -235 a 235 e
 * y de 0 a 255 (tela 1080p com a interface em escala 4). Cores em RGB (0xRRGGBB).
 *
 * <p>Tudo o que a tela pode desenhar precisa ter sido desenhado uma vez em
 * {@link FlatApi#prepare} (é assim que as peças entram no resource pack). O que não entrou é
 * pulado, com um aviso no console.
 */
public interface FlatDraw {

    /** Tamanhos de letra. */
    enum Font {
        /** Pequena (linha de 9 px). */
        PEQUENA("p", 9),
        /** Pequena em negrito. */
        NEGRITO("b", 9),
        /** Média em negrito (linha de 12 px), para títulos. */
        MEDIA("m", 12);

        private final String id;
        private final int height;

        Font(String id, int height) {
            this.id = id;
            this.height = height;
        }

        public String id() {
            return id;
        }

        public int height() {
            return height;
        }
    }

    /** true na passada de preparação (sem jogador): desenhe todas as variações da tela. */
    boolean preparing();

    /** Posição do cursor (0, 0 quando não há cursor). */
    int cursorX();

    int cursorY();

    /** Retângulo liso. {@code alpha} de 10 a 100 (%). */
    void rect(int x, int y, int w, int h, int rgb, int alpha);

    /** Texto com o canto superior esquerdo em (x, y). Devolve a largura. */
    int text(int x, int y, Font font, String text, int rgb);

    /** Texto terminando em {@code right}. */
    int textRight(int right, int y, Font font, String text, int rgb);

    /** Texto centrado em {@code center}. */
    int textCenter(int center, int y, Font font, String text, int rgb);

    /** Largura do texto, sem desenhar. */
    int width(Font font, String text);

    /** Corta o texto com "…" para caber em {@code maxWidth}. */
    String fit(Font font, String text, int maxWidth);

    /**
     * Ícone quadrado de uma textura do jogo ou do pack, ex.: {@code minecraft:item/bread} ou
     * {@code aincrad:flat/cursor} (sem .png).
     */
    void icon(int x, int y, int size, String texture);

    /**
     * O mesmo ícone pintado numa cor (a arte branca vira a cor; as outras são multiplicadas por
     * ela). {@code size} é a altura: uma textura mais larga que alta mantém a proporção.
     */
    void icon(int x, int y, int size, String texture, int rgb);

    /**
     * Área clicável. Devolve true se o cursor está em cima (para pintar o destaque).
     * {@code onClick} roda na thread principal; a tela é redesenhada em seguida.
     */
    boolean button(int x, int y, int w, int h, Runnable onClick);
}
