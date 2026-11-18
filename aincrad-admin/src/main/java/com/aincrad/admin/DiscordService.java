package com.aincrad.admin;

import com.aincrad.admin.model.DiscordRules;
import com.aincrad.core.config.ConfigFile;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Discord por webhook, sem bot: um endereço e um texto por tipo de mensagem, em discord.yml.
 * O endereço do webhook é um segredo: nunca aparece em mensagem nem no console.
 */
public final class DiscordService {

    /** Os tipos de mensagem. */
    public enum Kind { ANDAR, CHEFE, RAID, CONQUISTA }

    private final Plugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<Kind, String> urls = new EnumMap<>(Kind.class);
    private final Map<Kind, String> texts = new EnumMap<>(Kind.class);
    private boolean enabled;

    public DiscordService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigFile file) {
        urls.clear();
        texts.clear();
        enabled = file.bool("ativo", false);
        String fallback = file.string("webhooks.padrao", "");
        for (Kind k : Kind.values()) {
            String key = k.name().toLowerCase(Locale.ROOT);
            String url = file.string("webhooks." + key, "");
            if (url.isBlank()) {
                url = fallback;
            }
            if (!url.isBlank()) {
                if (DiscordRules.validUrl(url)) {
                    urls.put(k, url.trim());
                } else if (enabled) {
                    plugin.getLogger().warning("discord.yml: o webhook de '" + key + "' não é um endereço de webhook do Discord. Ignorado.");
                }
            }
            texts.put(k, file.string("mensagens." + key, ""));
        }
        if (enabled) {
            plugin.getLogger().info("Discord: " + urls.size() + " de " + Kind.values().length + " tipos de mensagem com webhook.");
        }
    }

    public boolean enabled(Kind kind) {
        return enabled && urls.containsKey(kind) && !texts.getOrDefault(kind, "").isBlank();
    }

    /** Publica a mensagem do tipo, se ele tem webhook e texto. Não bloqueia. */
    public void send(Kind kind, Map<String, String> vars) {
        if (!enabled(kind)) {
            return;
        }
        String body = DiscordRules.payload(DiscordRules.fill(texts.get(kind), vars));
        HttpRequest req = HttpRequest.newBuilder(URI.create(urls.get(kind)))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, ex) -> {
            String what = kind.name().toLowerCase(Locale.ROOT);
            if (ex != null) {
                // Só a classe do erro: a mensagem de uma falha de rede pode trazer o endereço.
                plugin.getLogger().warning("Discord: a mensagem de '" + what + "' não saiu (" + ex.getClass().getSimpleName() + ").");
            } else if (res.statusCode() >= 300) {
                plugin.getLogger().warning("Discord: a mensagem de '" + what + "' foi recusada (HTTP " + res.statusCode() + ").");
            }
        });
    }
}
