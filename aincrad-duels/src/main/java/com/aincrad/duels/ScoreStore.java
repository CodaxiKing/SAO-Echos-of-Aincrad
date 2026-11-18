package com.aincrad.duels;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.duels.model.DuelScore;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * O placar de duelos e as apostas em andamento, no banco do core: o placar vale para a rede
 * inteira; as apostas ficam anotadas para o Col voltar aos donos se o servidor cair no meio.
 */
public final class ScoreStore {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE duel_scores (player VARCHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL, wins INT NOT NULL, losses INT NOT NULL, "
                    + "draws INT NOT NULL, streak INT NOT NULL, best INT NOT NULL);"
                    + "CREATE TABLE duel_bets (hold_id VARCHAR(64) PRIMARY KEY, server VARCHAR(64) NOT NULL)",
            // Aposta de item: CUSTODIA enquanto o duelo dura, ENTREGA quando o dono não estava online para receber.
            "CREATE TABLE duel_items ({AUTO_ID}, owner VARCHAR(36) NOT NULL, item LONGTEXT NOT NULL, state VARCHAR(8) NOT NULL, server VARCHAR(64) NOT NULL)");

    /** Uma linha do ranking. */
    public record Row(String name, DuelScore score) {
    }

    private final Database db;
    private final Map<UUID, DuelScore> cache = new ConcurrentHashMap<>();

    public ScoreStore(Database db) {
        this.db = db;
    }

    private static DuelScore read(java.sql.ResultSet rs, int from) throws SQLException {
        return new DuelScore(rs.getInt(from), rs.getInt(from + 1), rs.getInt(from + 2), rs.getInt(from + 3), rs.getInt(from + 4));
    }

    /** O placar de um jogador, do banco. {@code then} roda na thread principal. */
    public void load(UUID id, Consumer<DuelScore> then) {
        db.query("SELECT wins, losses, draws, streak, best FROM duel_scores WHERE player = ?", rs -> read(rs, 1), id.toString())
                .thenAccept(rows -> AincradCore.get().runSync(() -> {
                    DuelScore s = rows.isEmpty() ? DuelScore.EMPTY : rows.get(0);
                    cache.put(id, s);
                    then.accept(s);
                }));
    }

    /** O que já se sabe do placar de quem está online (vazio se ainda não carregou). */
    public DuelScore cached(UUID id) {
        return cache.getOrDefault(id, DuelScore.EMPTY);
    }

    public void forget(UUID id) {
        cache.remove(id);
    }

    /** Aplica o resultado de um duelo ao placar do jogador, lendo antes o valor do banco (outro servidor pode ter mexido). */
    public void record(UUID id, String name, UnaryOperator<DuelScore> result) {
        String upsert = db.upsert("duel_scores", List.of("player"), List.of("name", "wins", "losses", "draws", "streak", "best"));
        db.async(c -> {
            List<DuelScore> rows = Database.query(c, "SELECT wins, losses, draws, streak, best FROM duel_scores WHERE player = ?", rs -> read(rs, 1), id.toString());
            DuelScore s = result.apply(rows.isEmpty() ? DuelScore.EMPTY : rows.get(0));
            Database.update(c, upsert, id.toString(), name, s.wins(), s.losses(), s.draws(), s.streak(), s.best());
            return s;
        }).thenAccept(s -> cache.put(id, s));
    }

    /** Os que mais venceram. {@code then} roda na thread principal. */
    public void top(int limit, Consumer<List<Row>> then) {
        db.query("SELECT name, wins, losses, draws, streak, best FROM duel_scores WHERE wins > 0 ORDER BY wins DESC, losses ASC LIMIT " + Math.max(1, Math.min(100, limit)),
                rs -> new Row(rs.getString(1), read(rs, 2))).thenAccept(rows -> AincradCore.get().runSync(() -> then.accept(rows)));
    }

    // ------------------------------------------------------------------ aposta de item

    private static String pack(byte[] item) {
        return java.util.Base64.getEncoder().encodeToString(item);
    }

    /** O item de um duelista entrou em custódia. */
    public void itemHeld(UUID owner, byte[] item, String server) {
        db.update("INSERT INTO duel_items (owner, item, state, server) VALUES (?, ?, 'CUSTODIA', ?)", owner.toString(), pack(item), server);
    }

    /** O duelo acabou: as custódias dos dois saem (os itens já foram entregues ou viraram entrega). */
    public void itemsSettled(UUID a, UUID b, String server) {
        db.update("DELETE FROM duel_items WHERE state = 'CUSTODIA' AND server = ? AND owner IN (?, ?)", server, a.toString(), b.toString());
    }

    /** Guarda um item para alguém que não está online: ele recebe ao entrar, em qualquer servidor. */
    public void itemOwed(UUID to, byte[] item, String server) {
        db.update("INSERT INTO duel_items (owner, item, state, server) VALUES (?, ?, 'ENTREGA', ?)", to.toString(), pack(item), server);
    }

    /** Os itens que esperam o jogador. Cada um é entregue uma vez só. {@code then} roda na thread principal. */
    public void claimItems(UUID owner, Consumer<List<byte[]>> then) {
        db.async(c -> {
            List<String[]> rows = Database.query(c, "SELECT id, item FROM duel_items WHERE owner = ? AND state = 'ENTREGA'",
                    rs -> new String[]{String.valueOf(rs.getLong(1)), rs.getString(2)}, owner.toString());
            List<byte[]> out = new java.util.ArrayList<>();
            for (String[] row : rows) {
                // Só entrega o que conseguiu apagar: dois servidores não entregam o mesmo item.
                if (Database.update(c, "DELETE FROM duel_items WHERE id = ?", Long.parseLong(row[0])) > 0) {
                    out.add(java.util.Base64.getDecoder().decode(row[1]));
                }
            }
            return out;
        }).thenAccept(items -> AincradCore.get().runSync(() -> then.accept(items)));
    }

    /** Duelos interrompidos pelo reinício: os itens em custódia voltam a ser dos donos, para entrega. Roda nesta thread. */
    public int releaseOrphanItems(String server) throws SQLException {
        return db.sync(c -> Database.update(c, "UPDATE duel_items SET state = 'ENTREGA' WHERE state = 'CUSTODIA' AND server = ?", server));
    }

    // ------------------------------------------------------------------ apostas em andamento

    public void betOpened(String hold, String server) {
        db.update(db.upsert("duel_bets", List.of("hold_id"), List.of("server")), hold, server);
    }

    public void betClosed(String hold) {
        if (hold != null) {
            db.update("DELETE FROM duel_bets WHERE hold_id = ?", hold);
        }
    }

    /** As apostas que ficaram abertas quando este servidor desligou no meio de um duelo. Roda nesta thread. */
    public List<String> orphanBets(String server) throws SQLException {
        return db.sync(c -> {
            List<String> holds = Database.query(c, "SELECT hold_id FROM duel_bets WHERE server = ?", rs -> rs.getString(1), server);
            Database.update(c, "DELETE FROM duel_bets WHERE server = ?", server);
            return holds;
        });
    }
}
