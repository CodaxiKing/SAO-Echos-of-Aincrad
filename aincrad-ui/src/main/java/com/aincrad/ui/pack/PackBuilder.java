package com.aincrad.ui.pack;

import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.config.UiSettings.SoundDef;
import com.aincrad.ui.glyph.Glyph;
import com.aincrad.ui.glyph.GlyphRegistry;
import com.aincrad.ui.glyph.SpaceFont;
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
 * Monta a pasta resourcepack/ do AincradUI: copia as imagens provisórias do .jar (sem
 * sobrescrever as artes do usuário) e gera fontes, modelos de item e sounds.json.
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

    /** Copia do .jar os arquivos que ainda não existem na pasta. */
    public int copyDefaults(File pluginJar) throws IOException {
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
        return copied;
    }

    /** Gera os arquivos derivados da config. Sempre sobrescreve: não edite estes à mão. */
    public void generate(GlyphRegistry glyphs, UiSettings settings) throws IOException {
        writeUiFont(glyphs);
        writeSpaceFont();
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
                if (name.equals("pack.mcmeta")) {
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
