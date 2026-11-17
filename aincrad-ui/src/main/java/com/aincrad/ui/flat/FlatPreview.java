package com.aincrad.ui.flat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Desenha num PNG o que o jogo mostraria para uma linha de tela plana, lendo as fontes do pack
 * gerado e seguindo as regras do Minecraft (título de BossBar centralizado com o topo em y = 3,
 * topo do glifo em {@code y + 7 - subida}, avanço {@code int(0.5 + tinta × escala) + 1}).
 * Serve para conferir layouts nos testes, sem abrir o jogo. Texturas do próprio Minecraft não
 * estão no pack: aparecem como um quadrado cinza do tamanho certo.
 */
public final class FlatPreview {

    private record Glyph(BufferedImage img, int sx, int sy, int sw, int sh, int height, int ascent, int advance, boolean placeholder) {
    }

    private final File packRoot;
    private final FlatAtlas atlas;
    private final Map<String, Map<Integer, Object>> fonts = new HashMap<>();
    private final Map<String, BufferedImage> images = new HashMap<>();

    public FlatPreview(File packRoot, FlatAtlas atlas) {
        this.packRoot = packRoot;
        this.atlas = atlas;
    }

    private record Run(Key font, int rgb, String text) {
    }

    private static void collect(Component c, Key font, TextColor color, List<Run> out) {
        Key f = c.font() != null ? c.font() : font;
        TextColor col = c.color() != null ? c.color() : color;
        if (c instanceof TextComponent t && !t.content().isEmpty()) {
            out.add(new Run(f, col == null ? 0xFFFFFF : col.value(), t.content()));
        }
        for (Component child : c.children()) {
            collect(child, f, col, out);
        }
    }

    /** Glifos (Glyph) e espaços (Integer) de uma fonte do pack, por código. */
    private Map<Integer, Object> font(Key key) throws IOException {
        String id = key.asString();
        Map<Integer, Object> cached = fonts.get(id);
        if (cached != null) {
            return cached;
        }
        Map<Integer, Object> map = new HashMap<>();
        File json = new File(packRoot, "assets/" + key.namespace() + "/font/" + key.value() + ".json");
        JsonObject root = JsonParser.parseString(Files.readString(json.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement el : root.getAsJsonArray("providers")) {
            JsonObject p = el.getAsJsonObject();
            String type = p.get("type").getAsString();
            if (type.equals("space")) {
                for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("advances").entrySet()) {
                    map.put(e.getKey().codePointAt(0), e.getValue().getAsInt());
                }
            } else if (type.equals("bitmap")) {
                bitmap(p, map);
            }
        }
        fonts.put(id, map);
        return map;
    }

    private void bitmap(JsonObject p, Map<Integer, Object> map) throws IOException {
        String file = p.get("file").getAsString();
        int height = p.get("height").getAsInt();
        int ascent = p.get("ascent").getAsInt();
        if (ascent > height) {
            throw new IOException("subida maior que a altura em " + file + " (o jogo recusa a fonte)");
        }
        JsonArray rows = p.getAsJsonArray("chars");
        int[][] grid = new int[rows.size()][];
        for (int r = 0; r < grid.length; r++) {
            grid[r] = rows.get(r).getAsString().codePoints().toArray();
        }
        BufferedImage img = image(file);
        if (img == null) {
            // Textura do jogo: só o tamanho e a tinta são conhecidos.
            FlatAtlas.IconInfo info = atlas.info(file.substring(0, file.length() - 4));
            if (info == null) {
                throw new IOException("textura desconhecida: " + file);
            }
            map.put(grid[0][0], new Glyph(null, 0, 0, info.ink(), info.h(), height, ascent, FlatAtlas.advance(info, height), true));
            return;
        }
        int cw = img.getWidth() / grid[0].length;
        int ch = img.getHeight() / grid.length;
        if (cw > 256 || ch > 256) {
            throw new IOException("glifo maior que 256 px em " + file);
        }
        float scale = (float) height / ch;
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                if (grid[r][c] == 0) {
                    continue;
                }
                int ink = 0;
                for (int x = cw - 1; x >= 0 && ink == 0; x--) {
                    for (int y = 0; y < ch; y++) {
                        if ((img.getRGB(c * cw + x, r * ch + y) >>> 24) != 0) {
                            ink = x + 1;
                            break;
                        }
                    }
                }
                map.put(grid[r][c], new Glyph(img, c * cw, r * ch, cw, ch, height, ascent, (int) (0.5f + ink * scale) + 1, false));
            }
        }
    }

    private BufferedImage image(String file) throws IOException {
        if (images.containsKey(file)) {
            return images.get(file);
        }
        int colon = file.indexOf(':');
        String ns = colon < 0 ? "minecraft" : file.substring(0, colon);
        File f = new File(packRoot, "assets/" + ns + "/textures/" + file.substring(colon + 1));
        BufferedImage img = f.isFile() ? ImageIO.read(f) : null;
        images.put(file, img);
        return img;
    }

    /** Largura total da linha, como o jogo mediria (tem de ser 0 para as telas planas). */
    public int advance(Component line) throws IOException {
        List<Run> runs = new ArrayList<>();
        collect(line, Key.key("minecraft", "default"), null, runs);
        int total = 0;
        for (Run r : runs) {
            Map<Integer, Object> font = font(r.font());
            for (int cp : r.text().codePoints().toArray()) {
                Object g = font.get(cp);
                if (g == null) {
                    throw new IOException("caractere U+" + Integer.toHexString(cp) + " não existe na fonte " + r.font().asString());
                }
                total += g instanceof Integer i ? i : ((Glyph) g).advance();
            }
        }
        return total;
    }

    /** Desenha a linha numa tela de {@code width}×{@code height} px de interface, ampliada {@code zoom}×. */
    public BufferedImage render(Component line, int width, int height, int zoom, int background) throws IOException {
        BufferedImage out = new BufferedImage(width * zoom, height * zoom, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                // Fundo quadriculado, para a transparência aparecer.
                boolean dark = ((x / (16 * zoom)) + (y / (16 * zoom))) % 2 == 0;
                out.setRGB(x, y, dark ? background : background + 0x101010);
            }
        }
        List<Run> runs = new ArrayList<>();
        collect(line, Key.key("minecraft", "default"), null, runs);
        int pen = width / 2 - advance(line) / 2;
        for (Run r : runs) {
            Map<Integer, Object> font = font(r.font());
            for (int cp : r.text().codePoints().toArray()) {
                Object g = font.get(cp);
                if (g instanceof Integer i) {
                    pen += i;
                    continue;
                }
                Glyph gl = (Glyph) g;
                draw(out, gl, pen, FlatAtlas.BASE - gl.ascent(), r.rgb(), zoom);
                pen += gl.advance();
            }
        }
        return out;
    }

    private static void draw(BufferedImage out, Glyph g, int gx, int gy, int rgb, int zoom) {
        float scale = (float) g.height() / g.sh();
        int w = Math.round((g.placeholder() ? g.sw() : g.sw()) * scale * zoom);
        int h = g.height() * zoom;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = gx * zoom + x;
                int py = gy * zoom + y;
                if (px < 0 || py < 0 || px >= out.getWidth() || py >= out.getHeight()) {
                    continue;
                }
                int argb;
                if (g.placeholder()) {
                    argb = 0xFF9AA0A6;
                } else {
                    int sx = g.sx() + Math.min(g.sw() - 1, (int) (x / (scale * zoom)));
                    int sy = g.sy() + Math.min(g.sh() - 1, (int) (y / (scale * zoom)));
                    argb = g.img().getRGB(sx, sy);
                }
                int a = argb >>> 24;
                if (a < 26) {
                    continue; // o shader de texto descarta alfa abaixo de 0.1
                }
                int dst = out.getRGB(px, py);
                int red = blend((argb >> 16 & 0xFF) * (rgb >> 16 & 0xFF) / 255, dst >> 16 & 0xFF, a);
                int green = blend((argb >> 8 & 0xFF) * (rgb >> 8 & 0xFF) / 255, dst >> 8 & 0xFF, a);
                int blue = blend((argb & 0xFF) * (rgb & 0xFF) / 255, dst & 0xFF, a);
                out.setRGB(px, py, red << 16 | green << 8 | blue);
            }
        }
    }

    private static int blend(int src, int dst, int alpha) {
        return (src * alpha + dst * (255 - alpha)) / 255;
    }
}
