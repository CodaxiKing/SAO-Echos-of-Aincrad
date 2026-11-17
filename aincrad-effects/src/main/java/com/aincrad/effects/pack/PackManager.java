package com.aincrad.effects.pack;

import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.config.Messages;
import com.aincrad.effects.config.Settings;
import com.aincrad.effects.image.ImageRegistry;
import com.aincrad.effects.image.SpaceFont;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Resource pack único do servidor: junta as imagens do Effects e as pastas que outros plugins
 * registram (registerPackFolder), gera o .zip, calcula o hash e envia aos jogadores.
 */
public final class PackManager {

    public static final String ZIP_NAME = "aincrad-pack.zip";
    private static final String JAR_PREFIX = "resourcepack/";
    /** Data fixa nas entradas do .zip: o hash só muda quando o conteúdo muda. */
    private static final long FIXED_TIME = 1_700_000_000_000L;

    private final AincradEffectsPlugin plugin;
    private final File ownFolder;
    private final File zip;
    private final Map<String, File> registered = new LinkedHashMap<>();
    private final UUID packId = UUID.nameUUIDFromBytes("aincrad-effects-pack".getBytes(StandardCharsets.UTF_8));
    private PackHttpServer http;
    private String hash;
    private String url;
    private boolean buildScheduled;
    private List<String> lastConflicts = List.of();

    public PackManager(AincradEffectsPlugin plugin) {
        this.plugin = plugin;
        this.ownFolder = new File(plugin.getDataFolder(), "resourcepack");
        this.zip = new File(new File(plugin.getDataFolder(), "pack"), ZIP_NAME);
    }

    public File texturesRoot() {
        return new File(ownFolder, "assets/aincrad/textures");
    }

    /** Copia do .jar as imagens provisórias que ainda não existem (nunca sobrescreve as do usuário). */
    public void copyDefaults(File pluginJar) {
        int copied = 0;
        try (JarFile jar = new JarFile(pluginJar)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory() || !e.getName().startsWith(JAR_PREFIX)) {
                    continue;
                }
                File out = new File(ownFolder, e.getName().substring(JAR_PREFIX.length()));
                if (out.exists()) {
                    continue;
                }
                Files.createDirectories(out.getParentFile().toPath());
                try (InputStream in = jar.getInputStream(e)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Falha ao copiar o resource pack padrão: " + e.getMessage());
        }
        if (copied > 0) {
            plugin.getLogger().info("Copiados " + copied + " arquivos provisórios para resourcepack/.");
        }
    }

    /** Outro plugin coloca uma pasta no pack único. Chamar de novo com o mesmo dono troca a pasta. */
    public void register(Plugin owner, File folder) {
        registered.put(owner.getName(), folder);
        plugin.getLogger().info("Pasta de pack registrada por " + owner.getName() + ": " + folder.getPath());
        scheduleBuild();
    }

    /** Agenda uma reconstrução para o próximo tick (várias chamadas viram uma só). */
    public void scheduleBuild() {
        if (buildScheduled || !plugin.isEnabled()) {
            return;
        }
        buildScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            buildScheduled = false;
            build();
        });
    }

    public void build() {
        Settings s = plugin.settings();
        List<PackMerger.Layer> layers = new ArrayList<>();
        try {
            layers.add(new PackMerger.Layer("AincradEffects (gerado)", generated(plugin.images())));
            layers.add(PackMerger.readFolder("AincradEffects", ownFolder.toPath()));
            for (Map.Entry<String, File> e : registered.entrySet()) {
                layers.add(PackMerger.readFolder(e.getKey(), e.getValue().toPath()));
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Falha ao ler as pastas do pack: " + e.getMessage());
            return;
        }
        PackMerger.Result result = PackMerger.merge(layers);
        lastConflicts = result.conflicts();
        for (String c : result.conflicts()) {
            plugin.getLogger().warning("Arquivo repetido no pack (vale o último): " + c);
        }

        String newHash;
        try {
            newHash = writeZip(result.files(), s);
        } catch (IOException e) {
            plugin.getLogger().severe("Falha ao gerar o .zip do pack: " + e.getMessage());
            return;
        }
        boolean changed = !newHash.equals(hash);
        hash = newHash;
        setupDelivery(s);
        plugin.getLogger().info("Pack do servidor: " + result.files().size() + " arquivos, " + layers.size()
                + " camadas, sha1 " + hash + (url == null ? " — sem URL de envio" : " — " + url));
        if (changed) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                sendTo(p);
            }
        }
    }

    /** Fontes geradas a partir de images.yml. */
    private static Map<String, byte[]> generated(ImageRegistry images) {
        Map<String, byte[]> files = new TreeMap<>();
        JsonArray providers = new JsonArray();
        for (ImageRegistry.Image img : images.all()) {
            if (!img.present()) {
                continue; // um bitmap com arquivo faltando derruba a fonte inteira no cliente
            }
            JsonObject p = new JsonObject();
            p.addProperty("type", "bitmap");
            p.addProperty("file", "aincrad:" + img.file());
            p.addProperty("height", img.height());
            p.addProperty("ascent", img.ascent());
            JsonArray chars = new JsonArray();
            chars.add(String.valueOf(img.ch()));
            p.add("chars", chars);
            providers.add(p);
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        files.put("assets/aincrad/font/effects.json", PackMerger.json(font));

        JsonObject advances = new JsonObject();
        SpaceFont.advances().forEach(advances::addProperty);
        JsonObject sp = new JsonObject();
        sp.addProperty("type", "space");
        sp.add("advances", advances);
        JsonArray spList = new JsonArray();
        spList.add(sp);
        JsonObject space = new JsonObject();
        space.add("providers", spList);
        files.put("assets/aincrad/font/space.json", PackMerger.json(space));
        return files;
    }

    private String writeZip(Map<String, byte[]> files, Settings s) throws IOException {
        Files.createDirectories(zip.getParentFile().toPath());
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", s.packFormat);
        pack.addProperty("min_format", s.packFormat);
        pack.addProperty("max_format", s.packFormat);
        pack.addProperty("description", s.packDescription);
        JsonObject meta = new JsonObject();
        meta.add("pack", pack);

        File tmp = new File(zip.getParentFile(), ZIP_NAME + ".tmp");
        try (OutputStream fos = Files.newOutputStream(tmp.toPath()); ZipOutputStream out = new ZipOutputStream(fos)) {
            put(out, "pack.mcmeta", PackMerger.json(meta));
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                put(out, e.getKey(), e.getValue());
            }
        }
        Files.move(tmp.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return sha1(Files.readAllBytes(zip.toPath()));
    }

    private static void put(ZipOutputStream out, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(FIXED_TIME);
        out.putNextEntry(e);
        out.write(data);
        out.closeEntry();
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setupDelivery(Settings s) {
        url = null;
        if (!s.packUrl.isEmpty()) {
            stopHttp();
            url = s.packUrl;
            return;
        }
        if (!s.packServerEnabled) {
            stopHttp();
            return;
        }
        if (http == null || http.port() != s.packServerPort) {
            stopHttp();
            try {
                http = new PackHttpServer(s.packServerPort, zip);
                http.start();
            } catch (IOException e) {
                plugin.getLogger().severe("Não foi possível abrir a porta " + s.packServerPort + " para o pack: " + e.getMessage());
                http = null;
                return;
            }
        }
        String base = s.packServerPublicAddress;
        if (base.isEmpty()) {
            String ip = Bukkit.getIp();
            base = "http://" + (ip == null || ip.isBlank() ? "localhost" : ip) + ":" + s.packServerPort;
            plugin.getLogger().warning("pack.servidor_embutido.endereco_publico vazio: usando " + base
                    + " (funciona só para quem joga na mesma máquina/rede).");
        }
        url = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + ZIP_NAME + "?h=" + hash;
    }

    public boolean sendTo(Player player) {
        if (url == null || hash == null) {
            return false;
        }
        Settings s = plugin.settings();
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId, URI.create(url), hash);
            ResourcePackRequest.Builder req = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(s.packRequired)
                    .replace(false);
            if (!s.packPrompt.isEmpty()) {
                req.prompt(Messages.parse(s.packPrompt));
            }
            player.sendResourcePacks(req.build());
            return true;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("URL do pack inválida: " + url);
            return false;
        }
    }

    public void stopHttp() {
        if (http != null) {
            http.stop();
            http = null;
        }
    }

    public String hash() {
        return hash;
    }

    /** Há um pack pronto e uma URL para enviá-lo? */
    public boolean canSend() {
        return url != null && hash != null;
    }

    public List<String> lastConflicts() {
        return lastConflicts;
    }

    public File zipFile() {
        return zip;
    }
}
