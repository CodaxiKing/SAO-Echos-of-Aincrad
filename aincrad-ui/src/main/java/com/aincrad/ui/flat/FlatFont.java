package com.aincrad.ui.flat;

import com.aincrad.ui.api.FlatDraw;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Larguras das letras das folhas inter_*.png (geradas por tools/gerar_tela_plana.py). Como cada
 * letra é uma imagem com largura conhecida, dá para posicionar textos no pixel.
 */
public final class FlatFont {

    private static final Map<FlatDraw.Font, FlatFont> LOADED = new EnumMap<>(FlatDraw.Font.class);

    private final int height;
    private final int columns;
    private final List<Character> order = new ArrayList<>();
    private final Map<Character, Integer> advance = new HashMap<>();

    private FlatFont(int height, int columns) {
        this.height = height;
        this.columns = columns;
    }

    public static synchronized FlatFont of(FlatDraw.Font font) {
        return LOADED.computeIfAbsent(font, f -> {
            try (InputStream in = FlatFont.class.getResourceAsStream("/flat/inter_" + f.id() + ".widths")) {
                if (in == null) {
                    throw new IllegalStateException("flat/inter_" + f.id() + ".widths não está no jar");
                }
                return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    static FlatFont parse(BufferedReader reader) throws IOException {
        int height = 9;
        int columns = 16;
        List<String[]> rows = new ArrayList<>();
        for (String line; (line = reader.readLine()) != null; ) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = line.substring(0, eq);
            String v = line.substring(eq + 1).trim();
            switch (k) {
                case "altura" -> height = Integer.parseInt(v);
                case "colunas" -> columns = Integer.parseInt(v);
                default -> rows.add(new String[]{k, v});
            }
        }
        FlatFont f = new FlatFont(height, columns);
        for (String[] r : rows) {
            char c = (char) Integer.parseInt(r[0], 16);
            f.order.add(c);
            f.advance.put(c, Integer.parseInt(r[1]));
        }
        return f;
    }

    public int height() {
        return height;
    }

    public boolean has(char c) {
        return advance.containsKey(c);
    }

    /** Troca letras que a folha não tem por "?". */
    public String clean(String text) {
        StringBuilder b = null;
        for (int i = 0; i < text.length(); i++) {
            if (!has(text.charAt(i))) {
                if (b == null) {
                    b = new StringBuilder(text);
                }
                b.setCharAt(i, '?');
            }
        }
        return b == null ? text : b.toString();
    }

    /** Largura em pixels de interface (texto já limpo por {@link #clean}). */
    public int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += advance.getOrDefault(text.charAt(i), 0);
        }
        return w;
    }

    public String fit(String text, int maxWidth) {
        String t = clean(text);
        if (width(t) <= maxWidth) {
            return t;
        }
        int dots = advance.getOrDefault('…', 6);
        int w = 0;
        int i = 0;
        while (i < t.length() && w + advance.getOrDefault(t.charAt(i), 0) + dots <= maxWidth) {
            w += advance.getOrDefault(t.charAt(i), 0);
            i++;
        }
        return t.substring(0, i).stripTrailing() + "…";
    }

    /** Linhas da grade "chars" do provider bitmap (células vazias com \u0000). */
    public List<String> grid() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < order.size(); i += columns) {
            StringBuilder b = new StringBuilder();
            for (int c = 0; c < columns; c++) {
                b.append(i + c < order.size() ? order.get(i + c) : '\u0000');
            }
            rows.add(b.toString());
        }
        return rows;
    }
}
