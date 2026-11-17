package com.aincrad.ui.flat;

import com.aincrad.ui.api.FlatDraw;
import com.aincrad.ui.glyph.SpaceFont;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * As peças das telas planas no resource pack. Cada peça (retângulo, ícone, linha de texto) numa
 * altura {@code y} da tela é um caractere próprio: a posição vertical de um caractere é fixa na
 * fonte, então "o mesmo retângulo 10 px mais abaixo" é outro caractere.
 *
 * <p>As peças entram aqui quando são desenhadas na preparação ({@link FlatCanvas} em modo
 * preparar). Uma peça pedida depois que o pack foi gerado é anotada e marca o atlas como
 * {@link #dirty()}: ela só aparece depois de o pack ser refeito.
 *
 * <p>Contas de posição: o título da BossBar fica com o topo em y = 3 e o jogo desenha um glifo
 * com o topo em {@code y + 7 - subida}; logo, para o topo cair em Y, a subida é {@code 10 - Y}.
 */
public final class FlatAtlas {

    public static final Key FONT = Key.key("aincrad", "flat");
    /** Topo do título da BossBar (3) + linha de base da fonte padrão (7). */
    static final int BASE = 10;
    /** Larguras dos pedaços de retângulo: qualquer largura é uma soma destas. */
    static final int[] WIDTHS = {256, 128, 64, 32, 16, 8, 4, 2, 1};
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Um pedaço pronto para desenhar. */
    public record Piece(String text, int advance) {
    }

    private record RectKey(int h, int alpha, int y) {
    }

    private record IconKey(String texture, int size, int y) {
    }

    private record TextKey(FlatDraw.Font font, int y) {
    }

    /** Tamanho de uma textura e até que coluna ela tem tinta. */
    record IconInfo(int w, int h, int ink) {
    }

    private final Map<RectKey, int[]> rects = new LinkedHashMap<>();
    private final Map<IconKey, int[]> icons = new LinkedHashMap<>();
    private final Map<TextKey, Integer> texts = new LinkedHashMap<>();
    private final Map<String, IconInfo> vanilla = new HashMap<>();
    private final Map<String, IconInfo> own = new HashMap<>();
    private int nextCode = 0xE000;
    /** Peças com índice de registro abaixo destes já estão no pack gerado. */
    private int builtRects;
    private int builtIcons;
    private int builtTexts;
    private boolean dirty;
    private File texturesRoot;

    public FlatAtlas() {
        try (InputStream in = FlatAtlas.class.getResourceAsStream("/flat/vanilla_icons.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        vanilla.put("minecraft:" + line.substring(0, eq), new IconInfo(16, 16, Integer.parseInt(line.substring(eq + 1).trim())));
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("flat/vanilla_icons.txt ilegível", e);
        }
    }

    /** Pasta assets/aincrad/textures do pack (para medir as texturas "aincrad:..."). */
    public void texturesRoot(File root) {
        this.texturesRoot = root;
        own.clear();
    }

    public boolean dirty() {
        return dirty;
    }

    private int allocate(int count) {
        int start = nextCode;
        nextCode += count;
        // De U+F800 em diante ficam os caracteres de espaço: continua no plano 15.
        if (start < 0xF800 && nextCode > 0xF800) {
            start = 0xF0000;
            nextCode = start + count;
        }
        return start;
    }

    // ------------------------------------------------------------------ retângulos

    /** Peças (da maior para a menor largura) do retângulo; null se ainda não está no pack. */
    public String[] rect(int h, int alpha, int y, boolean preparing) {
        RectKey k = new RectKey(h, alpha, y);
        int[] e = rects.get(k);
        if (e == null) {
            e = new int[]{allocate(WIDTHS.length), rects.size()};
            rects.put(k, e);
            dirty = true;
        }
        if (!preparing && e[1] >= builtRects) {
            return null;
        }
        String[] out = new String[WIDTHS.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = Character.toString(e[0] + i);
        }
        return out;
    }

    // ------------------------------------------------------------------ ícones

    /** Existe a textura (do jogo, 16×16 sem animação, ou do pack)? */
    public boolean hasTexture(String texture) {
        return info(texture) != null;
    }

    /**
     * Textura que serve de ícone para um item do jogo (chave do material, ex.: "bread"): a do
     * item; para blocos, a do bloco (frente, lado ou topo). Sem nenhuma, a barreira.
     */
    public String itemTexture(String key) {
        for (String t : new String[]{"item/" + key, "block/" + key, "block/" + key + "_front", "block/" + key + "_side",
                "block/" + key + "_top", "block/" + key + "_0"}) {
            if (vanilla.containsKey("minecraft:" + t)) {
                return "minecraft:" + t;
            }
        }
        return "minecraft:item/barrier";
    }

    IconInfo info(String texture) {
        IconInfo v = vanilla.get(texture);
        if (v != null || !texture.startsWith("aincrad:") || texturesRoot == null) {
            return v;
        }
        return own.computeIfAbsent(texture, t -> {
            File f = new File(texturesRoot, t.substring("aincrad:".length()) + ".png");
            try {
                BufferedImage img = f.isFile() ? ImageIO.read(f) : null;
                if (img == null || img.getWidth() > 256 || img.getHeight() > 256) {
                    return null;
                }
                int ink = 0;
                for (int x = img.getWidth() - 1; x >= 0 && ink == 0; x--) {
                    for (int yy = 0; yy < img.getHeight(); yy++) {
                        if ((img.getRGB(x, yy) >>> 24) != 0) {
                            ink = x + 1;
                            break;
                        }
                    }
                }
                return new IconInfo(img.getWidth(), img.getHeight(), ink);
            } catch (IOException e) {
                return null;
            }
        });
    }

    /** Avanço que o jogo dá a um glifo bitmap: int(0.5 + tinta × escala) + 1. */
    static int advance(IconInfo i, int size) {
        return (int) (0.5f + i.ink() * ((float) size / i.h())) + 1;
    }

    /** O ícone naquela altura; null se a textura não existe ou ainda não está no pack. */
    public Piece icon(String texture, int size, int y, boolean preparing) {
        IconInfo info = info(texture);
        if (info == null) {
            return null;
        }
        IconKey k = new IconKey(texture, size, y);
        int[] e = icons.get(k);
        if (e == null) {
            e = new int[]{allocate(1), icons.size()};
            icons.put(k, e);
            dirty = true;
        }
        if (!preparing && e[1] >= builtIcons) {
            return null;
        }
        return new Piece(Character.toString(e[0]), advance(info, size));
    }

    // ------------------------------------------------------------------ texto

    /** A fonte para escrever com o topo da linha em {@code y}; null se ainda não está no pack. */
    public Key text(FlatDraw.Font font, int y, boolean preparing) {
        TextKey k = new TextKey(font, y);
        Integer idx = texts.get(k);
        if (idx == null) {
            idx = texts.size();
            texts.put(k, idx);
            dirty = true;
        }
        if (!preparing && idx >= builtTexts) {
            return null;
        }
        return Key.key("aincrad", "flat_t/" + font.id() + "_" + (y < 0 ? "n" + -y : String.valueOf(y)));
    }

    public int size() {
        return rects.size() * WIDTHS.length + icons.size() + texts.size();
    }

    // ------------------------------------------------------------------ pack

    /** Gera as fontes e as texturas dos retângulos. Depois disto, tudo o que foi registrado desenha. */
    public void write(File packRoot) throws IOException {
        File genDir = new File(packRoot, "assets/aincrad/textures/flat_gen");
        deleteTree(genDir.toPath());
        deleteTree(new File(packRoot, "assets/aincrad/font/flat_t").toPath());
        Files.createDirectories(genDir.toPath());

        JsonArray providers = new JsonArray();
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        SpaceFont.advances().forEach(advances::addProperty);
        space.add("advances", advances);
        providers.add(space);

        for (Map.Entry<RectKey, int[]> e : rects.entrySet()) {
            RectKey k = e.getKey();
            int ascent = BASE - k.y();
            // A subida não pode passar da altura do glifo: retângulo baixo perto do topo ganha
            // linhas transparentes embaixo.
            int texH = Math.max(k.h(), ascent);
            for (int i = 0; i < WIDTHS.length; i++) {
                String name = "r" + WIDTHS[i] + "_" + texH + "_" + k.h() + "_" + k.alpha();
                File png = new File(genDir, name + ".png");
                if (!png.isFile()) {
                    BufferedImage img = new BufferedImage(WIDTHS[i], texH, BufferedImage.TYPE_INT_ARGB);
                    int argb = (Math.round(k.alpha() * 255 / 100f) << 24) | 0xFFFFFF;
                    for (int x = 0; x < WIDTHS[i]; x++) {
                        for (int yy = 0; yy < k.h(); yy++) {
                            img.setRGB(x, yy, argb);
                        }
                    }
                    ImageIO.write(img, "png", png);
                }
                providers.add(bitmap("aincrad:flat_gen/" + name + ".png", texH, ascent, List.of(Character.toString(e.getValue()[0] + i))));
            }
        }
        for (Map.Entry<IconKey, int[]> e : icons.entrySet()) {
            IconKey k = e.getKey();
            providers.add(bitmap(k.texture() + ".png", k.size(), Math.min(k.size(), BASE - k.y()),
                    List.of(Character.toString(e.getValue()[0]))));
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        writeJson(new File(packRoot, "assets/aincrad/font/flat.json"), font);

        for (TextKey k : texts.keySet()) {
            FlatFont f = FlatFont.of(k.font());
            JsonArray p = new JsonArray();
            p.add(bitmap("aincrad:flat/inter_" + k.font().id() + ".png", f.height(), Math.min(f.height(), BASE - k.y()), f.grid()));
            JsonObject tf = new JsonObject();
            tf.add("providers", p);
            writeJson(new File(packRoot, "assets/" + text(k.font(), k.y(), true).namespace() + "/font/"
                    + text(k.font(), k.y(), true).value() + ".json"), tf);
        }
        builtRects = rects.size();
        builtIcons = icons.size();
        builtTexts = texts.size();
        dirty = false;
    }

    private static JsonObject bitmap(String file, int height, int ascent, List<String> chars) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", file);
        p.addProperty("height", height);
        p.addProperty("ascent", ascent);
        JsonArray arr = new JsonArray();
        chars.forEach(arr::add);
        p.add("chars", arr);
        return p;
    }

    private static void writeJson(File f, JsonObject obj) throws IOException {
        Files.createDirectories(f.getParentFile().toPath());
        Files.writeString(f.toPath(), GSON.toJson(obj), StandardCharsets.UTF_8);
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
