package com.aincrad.ui.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Map;

/**
 * As mensagens usam cores pensadas para fundo escuro (chat, tooltip). Sobre os painéis brancos
 * do SAO elas viram tons escuros equivalentes, para continuarem legíveis.
 */
public final class Contrast {

    static final TextColor DEFAULT = TextColor.color(0x3C3C3C);

    private static final Map<TextColor, TextColor> ON_LIGHT = Map.of(
            NamedTextColor.WHITE, DEFAULT,
            NamedTextColor.GRAY, TextColor.color(0x707070),
            NamedTextColor.DARK_GRAY, TextColor.color(0x555555),
            NamedTextColor.AQUA, TextColor.color(0x1B7FB5),
            NamedTextColor.YELLOW, TextColor.color(0xB07D00),
            NamedTextColor.GREEN, TextColor.color(0x2E8B3A),
            NamedTextColor.LIGHT_PURPLE, TextColor.color(0x8E3FB0),
            NamedTextColor.GOLD, TextColor.color(0xC07A00));

    private Contrast() {
    }

    static TextColor onLight(TextColor c) {
        if (c == null) {
            return null;
        }
        return ON_LIGHT.getOrDefault(c, c);
    }

    /** Troca as cores claras por escuras em todo o componente; sem cor vira cinza-escuro. */
    public static Component forLight(Component c) {
        Component root = map(c);
        return root.color() == null ? root.color(DEFAULT) : root;
    }

    private static Component map(Component c) {
        Component out = c.color(onLight(c.color()));
        return out.children(out.children().stream().map(Contrast::map).toList());
    }
}
