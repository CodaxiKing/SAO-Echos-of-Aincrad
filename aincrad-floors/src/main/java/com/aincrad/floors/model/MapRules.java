package com.aincrad.floors.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regras do mapa por exploração: o andar é dividido em setores quadrados, e cada setor que o
 * jogador pisou fica gravado. O mapa mostra só os setores vistos.
 */
public final class MapRules {

    private MapRules() {
    }

    /** O setor de uma coordenada (x ou z) para o tamanho de setor dado. */
    public static int sector(int block, int size) {
        return Math.floorDiv(block, Math.max(1, size));
    }

    /** Os dois números do setor num só, para guardar em conjuntos. */
    public static long key(int sx, int sz) {
        return ((long) sx << 32) | (sz & 0xFFFFFFFFL);
    }

    public static int sx(long key) {
        return (int) (key >> 32);
    }

    public static int sz(long key) {
        return (int) key;
    }

    /** Quantos setores cabem num andar de {@code minX..maxX} por {@code minZ..maxZ}. */
    public static long total(int minX, int maxX, int minZ, int maxZ, int size) {
        return (long) (sector(maxX, size) - sector(minX, size) + 1) * (sector(maxZ, size) - sector(minZ, size) + 1);
    }

    /** Quanto do andar o jogador já viu, em % (0 a 100). */
    public static int percent(int seen, long total) {
        return total <= 0 ? 0 : (int) Math.min(100, Math.round(seen * 100.0 / total));
    }

    /**
     * O mapa em volta do jogador, uma linha de texto por fileira de setores (o norte em cima):
     * {@code you} onde ele está, {@code known} nos setores vistos e {@code unknown} no resto.
     *
     * @param radius quantos setores para cada lado
     */
    public static List<String> grid(Set<Long> seen, int sx, int sz, int radius, String you, String known, String unknown) {
        List<String> out = new ArrayList<>();
        for (int z = sz - radius; z <= sz + radius; z++) {
            StringBuilder line = new StringBuilder();
            for (int x = sx - radius; x <= sx + radius; x++) {
                line.append(x == sx && z == sz ? you : seen.contains(key(x, z)) ? known : unknown);
            }
            out.add(line.toString());
        }
        return out;
    }

    /** Os setores num texto curto, para o item "dados de mapa": {@code "3,-2;4,-2"}. */
    public static String encode(Set<Long> seen, int max) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (long k : seen) {
            if (n++ >= max) {
                break;
            }
            b.append(b.length() > 0 ? ";" : "").append(sx(k)).append(',').append(sz(k));
        }
        return b.toString();
    }

    /** Lê o texto de {@link #encode}. Pedaços ilegíveis são ignorados. */
    public static Set<Long> decode(String text) {
        Set<Long> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String part : text.split(";")) {
            String[] p = part.split(",");
            try {
                if (p.length == 2) {
                    out.add(key(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())));
                }
            } catch (NumberFormatException ignored) {
                // Pedaço ilegível: fica de fora.
            }
        }
        return out;
    }
}
