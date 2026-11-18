package com.aincrad.admin.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Regras do Discord por webhook: o endereço aceito, o texto e o corpo da mensagem. */
public final class DiscordRules {

    /** Limite de caracteres de uma mensagem do Discord. */
    public static final int MAX = 2000;

    private DiscordRules() {
    }

    /** Só webhooks do próprio Discord, por HTTPS. */
    public static boolean validUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(u.getScheme())
                    && (host.equals("discord.com") || host.equals("discordapp.com")
                    || host.endsWith(".discord.com") || host.endsWith(".discordapp.com"))
                    && u.getPath() != null && u.getPath().startsWith("/api/webhooks/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Troca {@code <chave>} pelo valor. Os valores vêm do jogo: o Markdown deles é neutralizado. */
    public static String fill(String template, Map<String, String> vars) {
        String out = template == null ? "" : template;
        for (var e : vars.entrySet()) {
            out = out.replace("<" + e.getKey() + ">", escape(e.getValue()));
        }
        return out.length() <= MAX ? out : out.substring(0, MAX - 1) + "…";
    }

    /** Nome de jogador ou de chefe não vira negrito, link nem menção. */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ("\\*_~`|>@#[]()".indexOf(c) >= 0) {
                b.append('\\');
            }
            b.append(c);
        }
        return b.toString();
    }

    /** {@code "Kirito (1.234), Asuna (980)"}: os {@code n} maiores danos. */
    public static String top(Map<String, Double> damage, int n) {
        List<Map.Entry<String, Double>> list = new ArrayList<>(damage.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            parts.add(list.get(i).getKey() + " (" + String.format(Locale.GERMANY, "%,d", Math.round(list.get(i).getValue())) + ")");
        }
        return String.join(", ", parts);
    }

    /** O corpo do POST. As menções ficam desligadas: um nome com @everyone não avisa ninguém. */
    public static String payload(String content) {
        return "{\"content\":" + RankingRules.jsonString(content) + ",\"allowed_mentions\":{\"parse\":[]}}";
    }
}
