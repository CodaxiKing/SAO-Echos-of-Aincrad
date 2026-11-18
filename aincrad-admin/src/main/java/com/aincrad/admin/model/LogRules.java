package com.aincrad.admin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Regras do registro de admin: o filtro de {@code /aincrad log}, a consulta e os cortes de texto. */
public final class LogRules {

    public static final int ACTOR_MAX = 32;
    public static final int PLUGIN_MAX = 32;
    public static final int ACTION_MAX = 64;
    public static final int DETAIL_MAX = 512;

    private LogRules() {
    }

    /** Uma linha do registro. */
    public record Entry(long id, long at, String actor, String plugin, String action, String detail, String server) {
    }

    /**
     * O filtro de {@code /aincrad log [jogador|*] [tipo|*] [página]}.
     *
     * @param player null = qualquer um
     * @param type   null = qualquer tipo; senão o plugin ("floors") ou a ação ("andar", "andar.liberar")
     */
    public record Filter(String player, String type, int page) {

        /** Null se a página não é um número a partir de 1. Um número sozinho é a página. */
        public static Filter parse(String[] args) {
            String player = null;
            String type = null;
            int page = 1;
            int n = args.length;
            if (n > 3) {
                return null;
            }
            // O último argumento, se for número, é a página: "/aincrad log 2", "/aincrad log Kirito 2".
            if (n > 0 && args[n - 1].matches("\\d{1,6}")) {
                page = Integer.parseInt(args[n - 1]);
                n--;
            } else if (n == 3) {
                return null;
            }
            if (page < 1) {
                return null;
            }
            if (n > 0) {
                player = any(args[0]);
            }
            if (n > 1) {
                type = any(args[1]);
                if (type != null) {
                    type = type.toLowerCase(Locale.ROOT);
                }
            }
            return new Filter(player, type, page);
        }

        private static String any(String s) {
            return s == null || s.isBlank() || s.equals("*") ? null : s.trim();
        }
    }

    /** O WHERE da consulta e os parâmetros dele. */
    public record Where(String sql, List<Object> params) {
    }

    /**
     * Jogador: quem fez a ação ou quem aparece no detalhe dela ("500 Col para Kirito").
     * Tipo: o plugin, a ação inteira ou o começo dela até um ponto.
     */
    public static Where where(Filter f) {
        List<String> parts = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (f.player() != null) {
            String p = f.player().toLowerCase(Locale.ROOT);
            parts.add("(LOWER(actor) = ? OR LOWER(detail) LIKE ? ESCAPE '!')");
            params.add(p);
            params.add("%" + escapeLike(p) + "%");
        }
        if (f.type() != null) {
            parts.add("(plugin = ? OR action = ? OR action LIKE ? ESCAPE '!')");
            params.add(f.type());
            params.add(f.type());
            params.add(escapeLike(f.type()) + ".%");
        }
        return new Where(parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts), List.copyOf(params));
    }

    /** Tira o sentido especial de %, _ e ! num LIKE com ESCAPE '!'. */
    public static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** Texto sem quebras de linha e cortado no tamanho da coluna. */
    public static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    public static int offset(int page, int perPage) {
        return (Math.max(1, page) - 1) * perPage;
    }

    /** Antes deste instante, as linhas são apagadas. 0 = nunca apaga. */
    public static long cutoff(long now, int keepDays) {
        return keepDays <= 0 ? 0 : now - keepDays * 86_400_000L;
    }
}
