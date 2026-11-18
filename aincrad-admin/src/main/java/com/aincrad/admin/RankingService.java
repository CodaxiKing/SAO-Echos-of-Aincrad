package com.aincrad.admin;

import com.aincrad.admin.model.RankingRules;
import com.aincrad.admin.model.RankingRules.DamageRow;
import com.aincrad.admin.model.RankingRules.Row;
import com.aincrad.admin.model.RankingRules.Snapshot;
import com.aincrad.core.db.Database;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.logging.Level;

/**
 * Os dados do ranking (vitórias, danos, Last Attacks e a hora de cada andar, gravados quando
 * acontecem) e o site pequeno que os mostra: uma página e {@code /api/ranking}, só leitura.
 */
public final class RankingService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS admin_kills (kill_key VARCHAR(96) NOT NULL PRIMARY KEY, boss VARCHAR(64) NOT NULL, "
                    + "boss_name VARCHAR(64) NOT NULL, floor INT NOT NULL, at_time BIGINT NOT NULL, first_kill INT NOT NULL, "
                    + "la_uuid VARCHAR(36), la_name VARCHAR(16))",
            "CREATE TABLE IF NOT EXISTS admin_kill_damage (kill_key VARCHAR(96) NOT NULL, uuid VARCHAR(36) NOT NULL, "
                    + "name VARCHAR(16) NOT NULL, damage DOUBLE NOT NULL, PRIMARY KEY (kill_key, uuid))",
            "CREATE TABLE IF NOT EXISTS admin_floor_times (floor INT NOT NULL PRIMARY KEY, at_time BIGINT NOT NULL)");

    /** Um participante de uma vitória. */
    public record Hit(String uuid, String name, double damage) {
    }

    private final Plugin plugin;
    private final Database db;
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile String json = RankingRules.json(Snapshot.EMPTY);
    private volatile String page = "";
    private volatile String title = "Aincrad — Ranking";
    private IntSupplier frontline = () -> 0;
    private HttpServer server;

    public RankingService(Plugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        render();
    }

    /** De onde vem o andar da linha de frente (o AincradFloors, quando está ligado). */
    public void frontline(IntSupplier supplier) {
        frontline = supplier;
    }

    // ------------------------------------------------------------------ gravar

    public void recordKill(String boss, String bossName, int floor, boolean first, String laUuid, String laName, List<Hit> hits) {
        long now = System.currentTimeMillis();
        String key = clip(boss, 64) + ":" + now;
        db.async(c -> {
            Database.update(c, "INSERT INTO admin_kills (kill_key, boss, boss_name, floor, at_time, first_kill, la_uuid, la_name) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", key, clip(boss, 64), clip(bossName, 64), floor, now, first ? 1 : 0, laUuid,
                    laName == null ? null : clip(laName, 16));
            for (Hit h : hits) {
                Database.update(c, "INSERT INTO admin_kill_damage (kill_key, uuid, name, damage) VALUES (?, ?, ?, ?)",
                        key, h.uuid(), clip(h.name(), 16), h.damage());
            }
            return null;
        }).exceptionally(ex -> warn("gravar a vitória sobre " + boss, ex));
    }

    public void recordFloor(int floor) {
        long now = System.currentTimeMillis();
        db.async(c -> {
            // Só a primeira liberação conta: bloquear e liberar de novo não muda a história.
            if (Database.query(c, "SELECT floor FROM admin_floor_times WHERE floor = ?", rs -> rs.getInt(1), floor).isEmpty()) {
                Database.update(c, "INSERT INTO admin_floor_times (floor, at_time) VALUES (?, ?)", floor, now);
            }
            return null;
        }).exceptionally(ex -> warn("gravar a liberação do andar " + floor, ex));
    }

    private <T> T warn(String what, Throwable ex) {
        plugin.getLogger().log(Level.WARNING, "Ranking: não foi possível " + what, ex);
        return null;
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s;
        return t.length() <= max ? t : t.substring(0, max);
    }

    // ------------------------------------------------------------------ ler

    /** Relê o ranking do banco (fora da thread principal). {@code floor} é a linha de frente de agora. */
    public void refresh() {
        int floor = frontline.getAsInt();
        db.async(c -> load(c, floor)).thenAccept(s -> {
            snapshot = s;
            render();
        }).exceptionally(ex -> warn("ler o ranking", ex));
    }

    private static Snapshot load(Connection c, int frontline) throws SQLException {
        List<Row> front = Database.query(c, "SELECT d.name, COUNT(*) FROM admin_kill_damage d JOIN admin_kills k ON k.kill_key = d.kill_key "
                + "WHERE k.first_kill = 1 GROUP BY d.uuid, d.name ORDER BY 2 DESC, 1 LIMIT 20", rs -> new Row(rs.getString(1), rs.getLong(2)));
        List<Row> last = Database.query(c, "SELECT la_name, COUNT(*) FROM admin_kills WHERE la_uuid IS NOT NULL "
                + "GROUP BY la_uuid, la_name ORDER BY 2 DESC, 1 LIMIT 20", rs -> new Row(rs.getString(1), rs.getLong(2)));
        List<DamageRow> damage = Database.query(c, "SELECT k.boss_name, k.floor, d.name, d.damage FROM admin_kill_damage d "
                        + "JOIN admin_kills k ON k.kill_key = d.kill_key ORDER BY k.floor, d.damage DESC",
                rs -> new DamageRow(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getDouble(4)));
        Map<Integer, Long> floors = new HashMap<>();
        Database.query(c, "SELECT floor, at_time FROM admin_floor_times", rs -> floors.put(rs.getInt(1), rs.getLong(2)));
        return new Snapshot(frontline, front, last, RankingRules.topPerBoss(damage, 3), RankingRules.floorTimes(floors),
                System.currentTimeMillis());
    }

    private void render() {
        json = RankingRules.json(snapshot);
        page = RankingRules.page(snapshot, title, ZoneId.systemDefault());
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    // ------------------------------------------------------------------ site

    /** Liga, religa ou desliga o site. A porta fica só na config: não vai para o console. */
    public void site(boolean on, String address, int port, String pageTitle) {
        stop();
        title = pageTitle;
        render();
        if (!on) {
            return;
        }
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress(address, port), 16);
            s.createContext("/", ex -> {
                String path = ex.getRequestURI().getPath();
                if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
                    reply(ex, 405, "text/plain; charset=utf-8", "Só leitura.");
                } else if (path.equals("/api/ranking")) {
                    reply(ex, 200, "application/json; charset=utf-8", json);
                } else if (path.equals("/") || path.equals("/index.html")) {
                    reply(ex, 200, "text/html; charset=utf-8", page);
                } else {
                    reply(ex, 404, "text/plain; charset=utf-8", "Não encontrado.");
                }
            });
            s.start();
            server = s;
            plugin.getLogger().info("Site de ranking ligado.");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("O site de ranking não ligou (" + e.getClass().getSimpleName()
                    + "): confira site.endereco e site.porta no config.yml.");
        }
    }

    private static void reply(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        try (ex) {
            if ("HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(status, -1);
            } else {
                ex.sendResponseHeaders(status, bytes.length);
                ex.getResponseBody().write(bytes);
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
