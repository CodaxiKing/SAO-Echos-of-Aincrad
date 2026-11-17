package com.aincrad.ui.pack;

import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.config.UiSettings.SoundDef;
import com.aincrad.ui.flat.FlatAtlas;
import com.aincrad.ui.glyph.Glyph;
import com.aincrad.ui.glyph.GlyphRegistry;
import com.aincrad.ui.glyph.SpaceFont;
import com.aincrad.ui.glyph.UiFonts;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Monta a pasta resourcepack/ do AincradUI: copia as artes do .jar (sem sobrescrever as do
 * usuário) e gera fontes, modelos de item e sounds.json.
 */
public final class PackBuilder {

    private static final String JAR_PREFIX = "resourcepack/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final File root;
    private final Logger log;

    public PackBuilder(File root, Logger log) {
        this.root = root;
        this.log = log;
    }

    public File root() {
        return root;
    }

    public File texturesRoot() {
        return new File(root, "assets/aincrad/textures");
    }

    /**
     * Versão das artes que vêm no .jar. Quando muda, a pasta resourcepack/ antiga é guardada em
     * resourcepack_antigo_&lt;versão&gt;/ e as artes novas são copiadas por cima.
     */
    public static final String ART_VERSION = "sao-assets-1";
    private static final String ART_MARKER = ".versao_artes";

    /** Copia do .jar os arquivos que ainda não existem na pasta (ou todos, se as artes mudaram). */
    public int copyDefaults(File pluginJar) throws IOException {
        File marker = new File(root, ART_MARKER);
        String current = marker.isFile() ? Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim() : "";
        if (root.isDirectory() && !current.equals(ART_VERSION)) {
            File backup = new File(root.getParentFile(), "resourcepack_antigo_" + (current.isEmpty() ? "provisorio" : current));
            if (!backup.exists() && root.renameTo(backup)) {
                log.info("Artes novas no plugin: a pasta resourcepack/ antiga foi guardada em " + backup.getName()
                        + ". Copie de volta o que você tinha personalizado.");
            }
        }
        int copied = 0;
        try (JarFile jar = new JarFile(pluginJar)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory() || !e.getName().startsWith(JAR_PREFIX)) {
                    continue;
                }
                File out = new File(root, e.getName().substring(JAR_PREFIX.length()));
                if (out.exists()) {
                    continue;
                }
                Files.createDirectories(out.getParentFile().toPath());
                try (InputStream in = jar.getInputStream(e)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        }
        Files.createDirectories(root.toPath());
        Files.writeString(marker.toPath(), ART_VERSION, StandardCharsets.UTF_8);
        return copied;
    }

    /** Gera os arquivos derivados da config. Sempre sobrescreve: não edite estes à mão. */
    public void generate(GlyphRegistry glyphs, UiSettings settings) throws IOException {
        writeUiFont(glyphs);
        writeSpaceFont();
        writeTextFonts();
        writeItemModels(glyphs);
        writeSounds(settings);
        File barDir = new File(root, "assets/minecraft/textures/gui/sprites/boss_bar");
        File bg = new File(barDir, "white_background.png");
        File progress = new File(barDir, "white_progress.png");
        if (settings.packHideWhiteBar) {
            writeTransparent(bg, 182, 5);
            writeTransparent(progress, 182, 5);
        } else {
            Files.deleteIfExists(bg.toPath());
            Files.deleteIfExists(progress.toPath());
        }
    }

    /** Fontes e texturas das telas planas, e a troca da HUD do Minecraft (estilo "barras"). */
    public void generateFlat(FlatAtlas atlas, UiSettings settings) throws IOException {
        atlas.write(root);
        File hud = new File(root, "assets/minecraft/textures/gui/sprites/hud");
        File list = new File(hud, GENERATED_LIST);
        // Apaga só o que este plugin gerou da outra vez (artes suas na mesma pasta ficam).
        if (list.isFile()) {
            for (String name : Files.readAllLines(list.toPath(), StandardCharsets.UTF_8)) {
                if (!name.isBlank()) {
                    Files.deleteIfExists(new File(hud, name.trim()).toPath());
                }
            }
            Files.delete(list.toPath());
        }
        if (!settings.hudFlat) {
            return;
        }
        List<String> made = new ArrayList<>();
        if (settings.flatHideVanilla) {
            for (String name : vanillaHudSprites()) {
                boolean bar = name.startsWith("experience_bar");
                made.add(writeGenerated(hud, name, new BufferedImage(bar ? 182 : 9, bar ? 5 : 9, BufferedImage.TYPE_INT_ARGB)));
            }
        }
        if (settings.flatHotbar) {
            made.add(writeGenerated(hud, "hotbar.png", HotbarArt.hotbar()));
            made.add(writeGenerated(hud, "hotbar_selection.png", HotbarArt.selection()));
            made.add(writeGenerated(hud, "hotbar_offhand_left.png", HotbarArt.offhand(true)));
            made.add(writeGenerated(hud, "hotbar_offhand_right.png", HotbarArt.offhand(false)));
        }
        made.removeIf(String::isEmpty);
        if (!made.isEmpty()) {
            Files.write(list.toPath(), made, StandardCharsets.UTF_8);
        }
    }

    private static final String GENERATED_LIST = ".aincrad_gerado";

    /** Grava a imagem se o arquivo não existe (arte do usuário tem preferência). Devolve o nome gravado. */
    private String writeGenerated(File dir, String name, BufferedImage img) throws IOException {
        File f = new File(dir, name);
        if (f.isFile()) {
            return "";
        }
        Files.createDirectories(f.getParentFile().toPath());
        ImageIO.write(img, "png", f);
        return name;
    }

    /** Corações, fome, armadura e barra de XP do Minecraft (a HUD em barras mostra tudo isso). */
    static List<String> vanillaHudSprites() {
        List<String> out = new ArrayList<>();
        for (String c : new String[]{"container", "container_hardcore"}) {
            out.add("heart/" + c + ".png");
            out.add("heart/" + c + "_blinking.png");
        }
        for (String effect : new String[]{"", "absorbing_", "frozen_", "poisoned_", "withered_"}) {
            for (String hardcore : new String[]{"", "hardcore_"}) {
                for (String fill : new String[]{"full", "half"}) {
                    out.add("heart/" + effect + hardcore + fill + ".png");
                    out.add("heart/" + effect + hardcore + fill + "_blinking.png");
                }
            }
        }
        for (String v : new String[]{"vehicle_container", "vehicle_full", "vehicle_half"}) {
            out.add("heart/" + v + ".png");
        }
        for (String kind : new String[]{"food", "armor"}) {
            for (String fill : new String[]{"empty", "half", "full"}) {
                out.add(kind + "_" + fill + ".png");
                if (kind.equals("food")) {
                    out.add(kind + "_" + fill + "_hunger.png");
                }
            }
        }
        out.add("experience_bar_background.png");
        out.add("experience_bar_progress.png");
        return out;
    }

    private void writeUiFont(GlyphRegistry glyphs) throws IOException {
        JsonArray providers = new JsonArray();
        for (Glyph g : glyphs.all()) {
            if (!g.present()) {
                continue; // um bitmap com arquivo faltando derruba a fonte inteira no cliente
            }
            JsonObject p = new JsonObject();
            p.addProperty("type", "bitmap");
            p.addProperty("file", "aincrad:" + g.texture());
            p.addProperty("height", g.height());
            p.addProperty("ascent", g.ascent());
            JsonArray chars = new JsonArray();
            chars.add(String.valueOf(g.ch()));
            p.add("chars", chars);
            providers.add(p);
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        writeJson(new File(root, "assets/aincrad/font/ui.json"), font);
    }

    /**
     * aincrad:sao — fonte SAO UI (TTF do pack) para os textos do menu; letras que ela não tem
     * caem na fonte padrão. aincrad:hud_low — fonte padrão rebaixada, para a linha de números
     * embaixo da barra de vida.
     */
    private void writeTextFonts() throws IOException {
        JsonArray sao = new JsonArray();
        if (new File(root, "assets/aincrad/font/" + UiFonts.SAO_TTF).isFile()) {
            JsonObject ttf = new JsonObject();
            ttf.addProperty("type", "ttf");
            ttf.addProperty("file", "aincrad:" + UiFonts.SAO_TTF);
            ttf.addProperty("size", UiFonts.SAO_SIZE);
            ttf.addProperty("oversample", 6.0);
            JsonArray shift = new JsonArray();
            shift.add(0.0);
            shift.add(UiFonts.SAO_SHIFT_Y);
            ttf.add("shift", shift);
            sao.add(ttf);
        }
        JsonObject fallback = new JsonObject();
        fallback.addProperty("type", "reference");
        fallback.addProperty("id", "minecraft:default");
        sao.add(fallback);
        JsonObject saoFont = new JsonObject();
        saoFont.add("providers", sao);
        writeJson(new File(root, "assets/aincrad/font/sao.json"), saoFont);

        JsonObject low = new JsonObject();
        low.addProperty("type", "bitmap");
        low.addProperty("file", "minecraft:font/ascii.png");
        low.addProperty("height", 8);
        low.addProperty("ascent", UiFonts.HUD_LOW_ASCENT);
        JsonArray rows = new JsonArray();
        for (String row : UiFonts.asciiRows()) {
            rows.add(row);
        }
        low.add("chars", rows);
        JsonArray lowProviders = new JsonArray();
        lowProviders.add(low);
        JsonObject lowFont = new JsonObject();
        lowFont.add("providers", lowProviders);
        writeJson(new File(root, "assets/aincrad/font/hud_low.json"), lowFont);
    }

    private void writeSpaceFont() throws IOException {
        JsonObject advances = new JsonObject();
        SpaceFont.advances().forEach(advances::addProperty);
        JsonObject p = new JsonObject();
        p.addProperty("type", "space");
        p.add("advances", advances);
        JsonArray providers = new JsonArray();
        providers.add(p);
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        writeJson(new File(root, "assets/aincrad/font/space.json"), font);
    }

    /** Um modelo e uma definição de item por glifo, para o menu de baú (item_model aincrad:ui/...). */
    private void writeItemModels(GlyphRegistry glyphs) throws IOException {
        deleteTree(new File(root, "assets/aincrad/models/ui").toPath());
        deleteTree(new File(root, "assets/aincrad/items/ui").toPath());
        for (Glyph g : glyphs.all()) {
            if (!g.present()) {
                continue;
            }
            String tex = g.texture().endsWith(".png") ? g.texture().substring(0, g.texture().length() - 4) : g.texture();
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "aincrad:" + tex);
            model.add("textures", textures);
            writeJson(new File(root, "assets/aincrad/models/" + g.modelPath() + ".json"), model);

            JsonObject inner = new JsonObject();
            inner.addProperty("type", "minecraft:model");
            inner.addProperty("model", "aincrad:" + g.modelPath());
            JsonObject item = new JsonObject();
            item.add("model", inner);
            writeJson(new File(root, "assets/aincrad/items/" + g.modelPath() + ".json"), item);
        }
    }

    private void writeSounds(UiSettings settings) throws IOException {
        JsonObject sounds = new JsonObject();
        File soundsDir = new File(root, "assets/aincrad/sounds");
        for (SoundDef def : settings.sounds.values()) {
            JsonArray list = new JsonArray();
            File ogg = new File(soundsDir, def.file() + ".ogg");
            if (ogg.isFile()) {
                list.add("aincrad:" + def.file());
            } else {
                JsonObject ref = new JsonObject();
                ref.addProperty("name", def.fallback());
                ref.addProperty("type", "event");
                list.add(ref);
            }
            JsonObject entry = new JsonObject();
            entry.add("sounds", list);
            sounds.add(def.event(), entry);
        }
        writeJson(new File(root, "assets/aincrad/sounds.json"), sounds);
    }

    /** Compacta a pasta num .zip com pack.mcmeta e devolve o SHA-1 em hexadecimal. */
    public String zip(File out, int format, String description) throws IOException {
        Files.createDirectories(out.getParentFile().toPath());
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", format);
        pack.addProperty("min_format", format);
        pack.addProperty("max_format", format);
        pack.addProperty("description", description);
        JsonObject meta = new JsonObject();
        meta.add("pack", pack);

        List<Path> files = new ArrayList<>();
        Path base = root.toPath();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile).sorted().forEach(files::add);
        }
        try (OutputStream fos = Files.newOutputStream(out.toPath()); ZipOutputStream zip = new ZipOutputStream(fos)) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Path p : files) {
                String name = base.relativize(p).toString().replace('\\', '/');
                if (name.equals("pack.mcmeta") || name.equals(ART_MARKER)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(p, zip);
                zip.closeEntry();
            }
        }
        return sha1(out);
    }

    public static String sha1(File f) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(Files.readAllBytes(f.toPath()));
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void writeJson(File f, JsonObject obj) throws IOException {
        Files.createDirectories(f.getParentFile().toPath());
        Files.writeString(f.toPath(), GSON.toJson(obj), StandardCharsets.UTF_8);
    }

    private void writeTransparent(File f, int w, int h) throws IOException {
        if (f.isFile()) {
            return;
        }
        Files.createDirectories(f.getParentFile().toPath());
        if (!ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", f)) {
            log.warning("Não foi possível gerar " + f.getName());
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> all = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
            for (Path p : all) {
                Files.deleteIfExists(p);
            }
        }
    }
}
