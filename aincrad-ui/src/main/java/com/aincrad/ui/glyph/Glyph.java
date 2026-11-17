package com.aincrad.ui.glyph;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Uma imagem do pack transformada num caractere da fonte aincrad:ui.
 *
 * @param id       id usado na config (ex.: icons/profile ou icons/profile_on)
 * @param texture  caminho relativo a assets/aincrad/textures/ (com .png)
 * @param height   altura em pixels de fonte
 * @param ascent   subida em pixels de fonte
 * @param ch       caractere da fonte
 * @param advance  largura em pixels de fonte (já com o espaço de 1 px que o jogo soma)
 * @param present  false quando o PNG não existe: o glifo fica fora da fonte e usa texto reserva
 */
public record Glyph(String id, String texture, int height, int ascent, char ch, int advance, boolean present) {

    public Component component() {
        return component(NamedTextColor.WHITE);
    }

    /** O jogo multiplica a cor do texto pela cor da imagem: branco mantém a arte original. */
    public Component component(TextColor color) {
        if (!present) {
            return Component.text("[" + shortName() + "]", color);
        }
        return Component.text(String.valueOf(ch)).font(GlyphRegistry.FONT).color(color);
    }

    /** Nome do modelo de item gerado para o glifo (aincrad:ui/&lt;id&gt;). */
    public String modelPath() {
        return "ui/" + id;
    }

    private String shortName() {
        int i = id.lastIndexOf('/');
        return i >= 0 ? id.substring(i + 1) : id;
    }
}
