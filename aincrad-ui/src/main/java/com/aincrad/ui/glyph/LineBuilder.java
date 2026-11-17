package com.aincrad.ui.glyph;

import net.kyori.adventure.text.Component;

/**
 * Monta uma linha de texto sabendo em que pixel está, para colocar partes em posições absolutas
 * (com a fonte de espaços) — várias imagens e textos sobrepostos numa linha só.
 */
public final class LineBuilder {

    private Component out = Component.empty();
    private int x;

    /** Acrescenta um componente que avança {@code advance} pixels. */
    public LineBuilder add(Component c, int advance) {
        out = out.append(c);
        x += advance;
        return this;
    }

    public LineBuilder add(Glyph g, Component c) {
        return add(c, g.advance());
    }

    public LineBuilder shift(int px) {
        if (px != 0) {
            out = out.append(SpaceFont.component(px));
            x += px;
        }
        return this;
    }

    /** Anda até o pixel {@code target} contado do começo da linha. */
    public LineBuilder moveTo(int target) {
        return shift(target - x);
    }

    public int x() {
        return x;
    }

    public Component build() {
        return out;
    }
}
