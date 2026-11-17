package com.aincrad.effects.image;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Lê largura e altura do cabeçalho de um PNG sem carregar a imagem. */
public final class PngSize {

    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int IHDR = 0x49484452;

    private PngSize() {
    }

    /** @return {largura, altura} ou null se não for um PNG válido */
    public static int[] read(File file) {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readLong() != PNG_SIGNATURE) {
                return null;
            }
            in.readInt();
            if (in.readInt() != IHDR) {
                return null;
            }
            int w = in.readInt();
            int h = in.readInt();
            return (w > 0 && h > 0) ? new int[]{w, h} : null;
        } catch (IOException e) {
            return null;
        }
    }
}
