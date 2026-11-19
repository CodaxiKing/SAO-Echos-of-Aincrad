package com.aincrad.effects.pack;

import java.util.Locale;

/**
 * O endereço de onde o jogador baixa o pack do servidor embutido.
 *
 * <p>Com {@code endereco_publico} na config, vale ele. Sem, vale o endereço que o próprio jogador
 * digitou para entrar no servidor: se ele chegou até aqui por esse nome, é por esse nome que
 * consegue baixar (desde que a porta do pack esteja aberta no host).</p>
 */
public final class PackAddress {

    private PackAddress() {
    }

    /** Limpa o host do handshake: sem o lixo depois do {@code \0} (Forge, proxies) e sem ponto no fim. */
    static String host(String virtualHost) {
        if (virtualHost == null) {
            return "";
        }
        String h = virtualHost;
        int cut = h.indexOf('\0');
        if (cut >= 0) {
            h = h.substring(0, cut);
        }
        h = h.trim().toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        // Só letras, números, ponto, hífen e dois-pontos (IPv6): nada que estrague a URL.
        return h.matches("[a-z0-9.:\\-]+") ? h : "";
    }

    /**
     * A base da URL, sem barra no fim.
     *
     * @param configured  {@code pack.servidor_embutido.endereco_publico} ("" = automático)
     * @param virtualHost o host que o jogador usou para entrar (pode ser null)
     * @param serverIp    o {@code server-ip} do server.properties (pode ser vazio)
     */
    public static String base(String configured, String virtualHost, String serverIp, int port) {
        if (configured != null && !configured.isBlank()) {
            String c = configured.trim();
            return c.endsWith("/") ? c.substring(0, c.length() - 1) : c;
        }
        String h = host(virtualHost);
        if (h.isEmpty()) {
            h = host(serverIp);
        }
        if (h.isEmpty()) {
            h = "localhost";
        }
        // IPv6 vai entre colchetes na URL.
        return "http://" + (h.contains(":") ? "[" + h + "]" : h) + ":" + port;
    }

    public static String url(String base, String zipName, String hash) {
        return base + "/" + zipName + "?h=" + hash;
    }
}
