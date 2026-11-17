package com.aincrad.ui.pack;

import java.util.List;

/** Abre para os testes de outros pacotes o que no PackBuilder é visível só no pacote. */
public final class PackBuilderAccess {

    private PackBuilderAccess() {
    }

    public static List<String> vanillaHudSprites() {
        return PackBuilder.vanillaHudSprites();
    }
}
