package com.aincrad.ui.pack;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.glyph.GlyphRegistry;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Decide quem entrega o pack. Com o AincradEffects, a pasta é registrada no pack único dele.
 * Sem ele, o AincradUI gera o próprio .zip e, se houver URL, envia ao jogador quando ele entra.
 */
public final class PackService {

    public enum Mode { EFFECTS, STANDALONE }

    private static final String ZIP_NAME = "aincrad-ui-pack.zip";

    private final AincradUIPlugin plugin;
    private final PackBuilder builder;
    private Mode mode = Mode.STANDALONE;
    private PackHttpServer http;
    private String url;
    private String hash;

    public PackService(AincradUIPlugin plugin) {
        this.plugin = plugin;
        this.builder = new PackBuilder(new File(plugin.getDataFolder(), "resourcepack"), plugin.getLogger());
    }

    public File texturesRoot() {
        return builder.texturesRoot();
    }

    /** Copia as imagens provisórias que faltam. Chamado antes de ler os glifos. */
    public void prepare(File pluginJar) {
        try {
            int copied = builder.copyDefaults(pluginJar);
            if (copied > 0) {
                plugin.getLogger().info("Copiados " + copied + " arquivos provisórios para resourcepack/.");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Falha ao copiar o resource pack padrão: " + e.getMessage());
        }
    }

    /** Gera os arquivos do pack e entrega ao Effects ou monta o .zip próprio. */
    public void build(GlyphRegistry glyphs, UiSettings settings, EffectsBridge effects) {
        stopHttp();
        url = null;
        hash = null;
        try {
            builder.generate(glyphs, settings);
        } catch (IOException e) {
            plugin.getLogger().severe("Falha ao gerar o resource pack: " + e.getMessage());
            return;
        }

        if (effects.available() && effects.registerPackFolder(plugin, builder.root())) {
            mode = Mode.EFFECTS;
            plugin.getLogger().info("Pasta resourcepack/ registrada no pack único do AincradEffects.");
            return;
        }

        mode = Mode.STANDALONE;
        File zip = new File(plugin.getDataFolder(), ZIP_NAME);
        try {
            hash = builder.zip(zip, settings.packFormat, settings.packDescription);
        } catch (IOException e) {
            plugin.getLogger().severe("Falha ao compactar o resource pack: " + e.getMessage());
            return;
        }

        if (!settings.packUrl.isEmpty()) {
            url = settings.packUrl;
        } else if (settings.packServerEnabled) {
            try {
                http = new PackHttpServer(settings.packServerPort, zip);
                http.start();
                if (settings.packServerPublicAddress.isEmpty()) {
                    plugin.getLogger().warning("pack.servidor_embutido.endereco_publico está vazio: o pack não será enviado.");
                } else {
                    url = trimSlash(settings.packServerPublicAddress) + "/" + ZIP_NAME + "?h=" + hash;
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Não foi possível abrir a porta " + settings.packServerPort + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Pack gerado em " + zip.getPath() + " (sha1 " + hash + ")"
                + (url == null ? " — sem URL configurada, instale-o no cliente para testar." : " — URL: " + url));
    }

    public void sendTo(Player player) {
        if (mode != Mode.STANDALONE || url == null || hash == null) {
            return;
        }
        UiSettings s = plugin.settings();
        try {
            UUID id = UUID.nameUUIDFromBytes(("aincrad-ui:" + hash).getBytes(StandardCharsets.UTF_8));
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(id, URI.create(url), hash);
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(s.packRequired)
                    .replace(false)
                    .build());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("URL do pack inválida: " + url);
        }
    }

    public Mode mode() {
        return mode;
    }

    public void stopHttp() {
        if (http != null) {
            http.stop();
            http = null;
        }
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
