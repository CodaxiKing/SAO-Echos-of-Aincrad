package com.aincrad.economy.data;

import com.aincrad.core.AincradCore;
import com.aincrad.core.bus.MessageBus;
import com.aincrad.core.db.Database;
import com.aincrad.economy.EcoMath;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Saldos de Col. O banco é a verdade e vale para a rede toda: cada mudança é gravada como
 * "saldo = saldo + delta" (atômica, sem sobrescrever o que outro servidor fez). Os jogadores
 * online ficam em memória para as consultas da thread principal serem instantâneas; quando outro
 * servidor muda o saldo de alguém, avisa pelo MessageBus e quem tem o jogador online relê.
 */
public final class Accounts {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS eco_accounts (uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "name VARCHAR(16) NOT NULL, balance BIGINT NOT NULL, updated BIGINT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS eco_transactions ({AUTO_ID}, uuid VARCHAR(36) NOT NULL, "
                    + "amount BIGINT NOT NULL, balance BIGINT NOT NULL, reason VARCHAR(64) NOT NULL, "
                    + "server VARCHAR(64) NOT NULL, created BIGINT NOT NULL);"
                    + "CREATE INDEX eco_tx_uuid ON eco_transactions (uuid)");

    public static final String BUS_CHANNEL = "economy:balance";

    private final Database db;
    private final Logger log;
    private final String server;
    private final Map<UUID, Long> online = new ConcurrentHashMap<>();
    private final Predicate<UUID> isOnline;
    private long startBalance;

    public Accounts(Database db, Logger log, String server, long startBalance, Predicate<UUID> isOnline) {
        this.db = db;
        this.log = log;
        this.server = server;
        this.startBalance = startBalance;
        this.isOnline = isOnline;
    }

    public void startBalance(long v) {
        this.startBalance = v;
    }

    /** Cria a conta se não existe e lê o saldo, nesta thread (pré-login). */
    public void loadBlocking(UUID id, String name) throws SQLException {
        long now = System.currentTimeMillis();
        String insert = insertIgnore();
        long bal = db.sync(c -> {
            Database.update(c, insert, id.toString(), name, startBalance, now);
            Database.update(c, "UPDATE eco_accounts SET name = ? WHERE uuid = ?", name, id.toString());
            return Database.query(c, "SELECT balance FROM eco_accounts WHERE uuid = ?", rs -> rs.getLong(1), id.toString()).get(0);
        });
        online.put(id, bal);
    }

    private String insertIgnore() {
        return (db.type() == Database.Type.SQLITE ? "INSERT OR IGNORE" : "INSERT IGNORE")
                + " INTO eco_accounts (uuid, name, balance, updated) VALUES (?, ?, ?, ?)";
    }

    public boolean isLoaded(UUID id) {
        return online.containsKey(id);
    }

    /** Saldo de quem está online aqui; -1 se não está carregado. */
    public long cached(UUID id) {
        Long v = online.get(id);
        return v == null ? -1 : v;
    }

    public void forget(UUID id) {
        online.remove(id);
    }

    /** Saldo direto do banco (offline, outro servidor). -1 = conta não existe. */
    public CompletableFuture<Long> fetch(UUID id) {
        return db.query("SELECT balance FROM eco_accounts WHERE uuid = ?", rs -> rs.getLong(1), id.toString())
                .thenApply(l -> l.isEmpty() ? -1L : l.get(0));
    }

    /**
     * Aplica o delta: na memória na hora (se online aqui) e no banco em seguida. Quem chama já
     * conferiu o saldo. Avisa a rede para os outros servidores relerem.
     */
    public void apply(UUID id, long delta, String reason) {
        online.computeIfPresent(id, (k, v) -> Math.max(0, EcoMath.add(v, delta)));
        long now = System.currentTimeMillis();
        String key = id.toString();
        String why = reason == null ? "?" : reason.length() > 64 ? reason.substring(0, 64) : reason;
        String insert = insertIgnore();
        db.async(c -> {
            // Conta nunca criada (offline que nunca entrou): nasce com o saldo inicial.
            Database.update(c, insert, key, "?", startBalance, now);
            Database.update(c, "UPDATE eco_accounts SET balance = CASE WHEN balance + ? < 0 THEN 0 ELSE balance + ? END, "
                    + "updated = ? WHERE uuid = ?", delta, delta, now, key);
            long after = Database.query(c, "SELECT balance FROM eco_accounts WHERE uuid = ?", rs -> rs.getLong(1), key).get(0);
            Database.update(c, "INSERT INTO eco_transactions (uuid, amount, balance, reason, server, created) VALUES (?, ?, ?, ?, ?, ?)",
                    key, delta, after, why, server, now);
            return after;
        }).whenComplete((after, err) -> {
            if (err != null) {
                log.warning("Não foi possível gravar " + delta + " Col de " + id + " (" + why + "): " + err.getMessage());
                return;
            }
            AincradCore.get().publish(BUS_CHANNEL, key);
        });
    }

    /** Outro servidor mexeu no saldo: relê se o jogador está online aqui. */
    public void onBus(MessageBus.Message m) {
        if (server.equals(m.origin())) {
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(m.payload());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!online.containsKey(id)) {
            return;
        }
        fetch(id).thenAccept(bal -> AincradCore.get().runSync(() -> {
            if (bal >= 0 && isOnline.test(id)) {
                online.put(id, bal);
            }
        }));
    }

    /** Ranking (async): nome e saldo dos {@code limit} mais ricos. */
    public CompletableFuture<List<Map.Entry<String, Long>>> top(int limit) {
        return db.query("SELECT name, balance FROM eco_accounts ORDER BY balance DESC LIMIT " + Math.max(1, limit),
                rs -> Map.entry(rs.getString(1), rs.getLong(2)));
    }
}
