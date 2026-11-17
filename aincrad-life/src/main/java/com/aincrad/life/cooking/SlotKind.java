package com.aincrad.life.cooking;

import java.util.Locale;

/** Os cinco encaixes do prato. Os quatro primeiros são obrigatórios; o Especial é opcional. */
public enum SlotKind {
    BASE, PROTEINA, VEGETAL, TEMPERO, ESPECIAL;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean required() {
        return this != ESPECIAL;
    }

    public static SlotKind parse(String s) {
        if (s == null) {
            return null;
        }
        return switch (s.trim().toLowerCase(Locale.ROOT).replace("í", "i")) {
            case "base" -> BASE;
            case "proteina" -> PROTEINA;
            case "vegetal" -> VEGETAL;
            case "tempero" -> TEMPERO;
            case "especial" -> ESPECIAL;
            default -> null;
        };
    }
}
