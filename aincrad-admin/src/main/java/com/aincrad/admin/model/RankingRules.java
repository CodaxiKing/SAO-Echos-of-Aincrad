package com.aincrad.admin.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** O ranking do site: os dados, as contas e a saída em JSON e em HTML (só leitura). */
public final class RankingRules {

    private RankingRules() {
    }

    /** Um jogador e um número (vitórias, Last Attacks, dano). */
    public record Row(String name, double value) {
    }

    /** Os maiores danos num chefe. */
    public record BossDamage(String boss, int floor, List<Row> top) {
    }

    /**
     * Um andar liberado.
     *
     * @param tookMs tempo desde a liberação do andar anterior; -1 se não se sabe
     */
    public record FloorTime(int floor, long at, long tookMs) {
    }

    /** Tudo o que a página mostra. */
    public record Snapshot(int frontline, List<Row> frontlinePlayers, List<Row> lastAttacks, List<BossDamage> bosses,
                           List<FloorTime> floors, long generatedAt) {

        public static final Snapshot EMPTY = new Snapshot(0, List.of(), List.of(), List.of(), List.of(), 0);
    }

    /** Uma linha de dano lida do banco, já ordenada por andar e dano. */
    public record DamageRow(String boss, int floor, String player, double damage) {
    }

    /** Os {@code n} maiores danos de cada chefe (o melhor de cada jogador), na ordem dos andares. */
    public static List<BossDamage> topPerBoss(List<DamageRow> rows, int n) {
        Map<String, Map<String, Double>> best = new LinkedHashMap<>();
        Map<String, Integer> floors = new LinkedHashMap<>();
        for (DamageRow r : rows) {
            floors.putIfAbsent(r.boss(), r.floor());
            best.computeIfAbsent(r.boss(), k -> new LinkedHashMap<>()).merge(r.player(), r.damage(), Math::max);
        }
        List<BossDamage> out = new ArrayList<>();
        for (var e : best.entrySet()) {
            List<Row> top = new ArrayList<>();
            e.getValue().forEach((p, d) -> top.add(new Row(p, d)));
            top.sort((a, b) -> Double.compare(b.value(), a.value()));
            out.add(new BossDamage(e.getKey(), floors.get(e.getKey()), List.copyOf(top.subList(0, Math.min(n, top.size())))));
        }
        out.sort((a, b) -> Integer.compare(a.floor(), b.floor()));
        return out;
    }

    /** O tempo de cada andar: quanto passou desde a liberação do andar anterior. */
    public static List<FloorTime> floorTimes(Map<Integer, Long> unlockedAt) {
        List<FloorTime> out = new ArrayList<>();
        unlockedAt.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            Long before = unlockedAt.get(e.getKey() - 1);
            out.add(new FloorTime(e.getKey(), e.getValue(), before == null ? -1 : Math.max(0, e.getValue() - before)));
        });
        return out;
    }

    /** {@code 93_600_000} → {@code "1d 2h"}; menos de um minuto → {@code "menos de 1min"}. */
    public static String duration(long ms) {
        if (ms < 0) {
            return "—";
        }
        long min = ms / 60_000;
        if (min < 1) {
            return "menos de 1min";
        }
        long d = min / 1440;
        long h = (min % 1440) / 60;
        long m = min % 60;
        if (d > 0) {
            return d + "d" + (h > 0 ? " " + h + "h" : "");
        }
        return h > 0 ? h + "h" + (m > 0 ? " " + m + "min" : "") : m + "min";
    }

    // ------------------------------------------------------------------ JSON

    public static String jsonString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    private static String number(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String rows(List<Row> rows, String valueKey) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            b.append(i > 0 ? "," : "").append("{\"jogador\":").append(jsonString(r.name()))
                    .append(",\"").append(valueKey).append("\":").append(number(r.value())).append('}');
        }
        return b.append(']').toString();
    }

    /** O que {@code /api/ranking} devolve. */
    public static String json(Snapshot s) {
        StringBuilder b = new StringBuilder("{\"gerado_em\":").append(s.generatedAt())
                .append(",\"linha_de_frente\":{\"andar\":").append(s.frontline())
                .append(",\"jogadores\":").append(rows(s.frontlinePlayers(), "primeiras_vitorias")).append('}')
                .append(",\"last_attacks\":").append(rows(s.lastAttacks(), "quantidade"))
                .append(",\"chefes\":[");
        for (int i = 0; i < s.bosses().size(); i++) {
            BossDamage d = s.bosses().get(i);
            b.append(i > 0 ? "," : "").append("{\"chefe\":").append(jsonString(d.boss())).append(",\"andar\":").append(d.floor())
                    .append(",\"maiores_danos\":").append(rows(d.top(), "dano")).append('}');
        }
        b.append("],\"andares\":[");
        for (int i = 0; i < s.floors().size(); i++) {
            FloorTime f = s.floors().get(i);
            b.append(i > 0 ? "," : "").append("{\"andar\":").append(f.floor()).append(",\"liberado_em\":").append(f.at())
                    .append(",\"tempo_ms\":").append(f.tookMs() < 0 ? "null" : Long.toString(f.tookMs())).append('}');
        }
        return b.append("]}").toString();
    }

    // ------------------------------------------------------------------ HTML

    public static String html(String s) {
        return (s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void table(StringBuilder b, String title, String col, List<Row> rows) {
        b.append("<section><h2>").append(html(title)).append("</h2>");
        if (rows.isEmpty()) {
            b.append("<p class=\"vazio\">Ainda sem dados.</p></section>");
            return;
        }
        b.append("<table><tr><th>#</th><th>Jogador</th><th>").append(html(col)).append("</th></tr>");
        for (int i = 0; i < rows.size(); i++) {
            b.append("<tr><td>").append(i + 1).append("</td><td>").append(html(rows.get(i).name())).append("</td><td>")
                    .append(number(rows.get(i).value())).append("</td></tr>");
        }
        b.append("</table></section>");
    }

    /** A página de rankings. Tudo o que vem do jogo passa por {@link #html(String)}. */
    public static String page(Snapshot s, String title, ZoneId zone) {
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone);
        StringBuilder b = new StringBuilder("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>").append(html(title))
                .append("</title><style>")
                .append("body{font-family:system-ui,sans-serif;background:#0d1117;color:#e6edf3;margin:0;padding:16px}")
                .append("main{max-width:900px;margin:0 auto}h1{color:#f0b429}h2{color:#58a6ff;margin-top:32px}")
                .append("table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:6px 8px;border-bottom:1px solid #30363d}")
                .append("th{color:#8b949e;font-weight:600}.vazio,footer{color:#8b949e}.frente{font-size:1.2rem}")
                .append("</style></head><body><main><h1>").append(html(title)).append("</h1>")
                .append("<p class=\"frente\">Linha de frente: <strong>andar ").append(s.frontline()).append("</strong></p>");
        table(b, "Linha de frente", "Primeiras vitórias", s.frontlinePlayers());
        table(b, "Last Attacks", "Quantidade", s.lastAttacks());
        b.append("<section><h2>Maiores danos por chefe</h2>");
        if (s.bosses().isEmpty()) {
            b.append("<p class=\"vazio\">Ainda sem dados.</p>");
        }
        for (BossDamage d : s.bosses()) {
            b.append("<h3>Andar ").append(d.floor()).append(" — ").append(html(d.boss())).append("</h3><table>");
            for (int i = 0; i < d.top().size(); i++) {
                b.append("<tr><td>").append(i + 1).append("</td><td>").append(html(d.top().get(i).name())).append("</td><td>")
                        .append(number(d.top().get(i).value())).append("</td></tr>");
            }
            b.append("</table>");
        }
        b.append("</section><section><h2>Tempo de cada andar</h2>");
        if (s.floors().isEmpty()) {
            b.append("<p class=\"vazio\">Ainda sem dados.</p>");
        } else {
            b.append("<table><tr><th>Andar</th><th>Liberado em</th><th>Tempo desde o anterior</th></tr>");
            for (FloorTime f : s.floors()) {
                b.append("<tr><td>").append(f.floor()).append("</td><td>").append(date.format(Instant.ofEpochMilli(f.at())))
                        .append("</td><td>").append(duration(f.tookMs())).append("</td></tr>");
            }
            b.append("</table>");
        }
        b.append("</section><footer><p>Atualizado em ")
                .append(s.generatedAt() <= 0 ? "—" : date.format(Instant.ofEpochMilli(s.generatedAt())))
                .append(". Os mesmos dados em <a href=\"/api/ranking\" style=\"color:#58a6ff\">/api/ranking</a>.</p></footer></main></body></html>");
        return b.toString();
    }
}
