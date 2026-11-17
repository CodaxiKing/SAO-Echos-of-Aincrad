package com.aincrad.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Banco do core: MySQL compartilhado pela rede (padrão do plano) ou SQLite para teste local.
 * Pool HikariCP, consultas fora da thread principal ({@link #async}) e migrações numeradas por
 * plugin ({@link #migrate}).
 *
 * <p>Nas migrações e no SQL, {@code {AUTO_ID}} vira a coluna de id auto-incrementada do banco
 * em uso. Use {@link #upsert} para "insere ou atualiza" sem depender do dialeto.
 */
public final class Database implements AutoCloseable {

    public enum Type { MYSQL, SQLITE }

    /** Uma consulta que usa a conexão e devolve algo. */
    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection c) throws SQLException;
    }

    /** Converte uma linha do resultado. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Type type;
    private final HikariDataSource ds;
    private final ExecutorService exec;
    private final Logger log;

    private Database(Type type, HikariDataSource ds, int threads, Logger log) {
        this.type = type;
        this.ds = ds;
        this.log = log;
        AtomicInteger n = new AtomicInteger();
        this.exec = Executors.newFixedThreadPool(Math.max(1, threads), r -> {
            Thread t = new Thread(r, "Aincrad-DB-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public static Database sqlite(File file, Logger log) {
        HikariConfig c = new HikariConfig();
        c.setPoolName("Aincrad-SQLite");
        c.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        c.setMaximumPoolSize(1); // SQLite: um escritor por vez
        c.setConnectionInitSql("PRAGMA foreign_keys = ON");
        return new Database(Type.SQLITE, new HikariDataSource(c), 1, log);
    }

    public static Database mysql(String host, int port, String database, String user, String password,
                                 String params, int pool, int threads, Logger log) {
        HikariConfig c = new HikariConfig();
        c.setPoolName("Aincrad-MySQL");
        c.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + (params == null || params.isBlank() ? "" : "?" + params));
        c.setUsername(user);
        c.setPassword(password);
        c.setMaximumPoolSize(Math.max(2, pool));
        c.addDataSourceProperty("cachePrepStmts", "true");
        c.addDataSourceProperty("prepStmtCacheSize", "250");
        c.addDataSourceProperty("rewriteBatchedStatements", "true");
        return new Database(Type.MYSQL, new HikariDataSource(c), threads, log);
    }

    public Type type() {
        return type;
    }

    // ------------------------------------------------------------------ execução

    /** Roda fora da thread principal. Use {@code thenAccept} + o agendador do Bukkit para voltar a ela. */
    public <T> CompletableFuture<T> async(SqlFunction<T> f) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sync(f);
            } catch (SQLException e) {
                throw new DatabaseException(e);
            }
        }, exec);
    }

    /** Roda agora, nesta thread (ligar o plugin, migrações, testes). Não use na thread principal em jogo. */
    public <T> T sync(SqlFunction<T> f) throws SQLException {
        try (Connection c = ds.getConnection()) {
            return f.apply(c);
        }
    }

    public CompletableFuture<Integer> update(String sql, Object... params) {
        return async(c -> update(c, sql, params));
    }

    public <T> CompletableFuture<List<T>> query(String sql, RowMapper<T> mapper, Object... params) {
        return async(c -> query(c, sql, mapper, params));
    }

    public static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    public static <T> List<T> query(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    // ------------------------------------------------------------------ dialeto

    /** Troca {@code {AUTO_ID}} pela coluna de id do banco em uso. */
    public String sql(String raw) {
        return raw.replace("{AUTO_ID}", type == Type.SQLITE
                ? "id INTEGER PRIMARY KEY AUTOINCREMENT"
                : "id BIGINT AUTO_INCREMENT PRIMARY KEY");
    }

    /**
     * SQL de "insere ou atualiza" para a tabela, com {@code keys} formando a chave única e
     * {@code cols} as demais colunas. Parâmetros: chaves e depois colunas, nessa ordem.
     */
    public String upsert(String table, List<String> keys, List<String> cols) {
        List<String> all = new ArrayList<>(keys);
        all.addAll(cols);
        StringJoiner names = new StringJoiner(", ");
        StringJoiner marks = new StringJoiner(", ");
        for (String a : all) {
            names.add(a);
            marks.add("?");
        }
        StringJoiner sets = new StringJoiner(", ");
        for (String c : cols) {
            sets.add(type == Type.SQLITE ? c + " = excluded." + c : c + " = VALUES(" + c + ")");
        }
        String base = "INSERT INTO " + table + " (" + names + ") VALUES (" + marks + ")";
        if (cols.isEmpty()) {
            return type == Type.SQLITE ? base + " ON CONFLICT DO NOTHING" : "INSERT IGNORE" + base.substring("INSERT".length());
        }
        return type == Type.SQLITE
                ? base + " ON CONFLICT(" + String.join(", ", keys) + ") DO UPDATE SET " + sets
                : base + " ON DUPLICATE KEY UPDATE " + sets;
    }

    // ------------------------------------------------------------------ migrações

    /**
     * Aplica, em ordem, as migrações de {@code owner} que ainda não rodaram. A migração N é o
     * índice N-1 da lista e pode ter vários comandos separados por ";". Nunca mude uma migração
     * que já foi publicada: acrescente uma nova. Roda nesta thread (use ao ligar o plugin).
     *
     * @return quantas migrações foram aplicadas agora
     */
    public int migrate(String owner, List<String> steps) throws SQLException {
        return sync(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS aincrad_migrations ("
                        + "owner VARCHAR(64) NOT NULL, version INT NOT NULL, applied BIGINT NOT NULL, "
                        + "PRIMARY KEY (owner, version))");
            }
            int current = query(c, "SELECT COALESCE(MAX(version), 0) FROM aincrad_migrations WHERE owner = ?",
                    rs -> rs.getInt(1), owner).get(0);
            int applied = 0;
            for (int v = current + 1; v <= steps.size(); v++) {
                boolean auto = c.getAutoCommit();
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    for (String part : sql(steps.get(v - 1)).split(";")) {
                        if (!part.isBlank()) {
                            st.executeUpdate(part.trim());
                        }
                    }
                    update(c, "INSERT INTO aincrad_migrations (owner, version, applied) VALUES (?, ?, ?)",
                            owner, v, System.currentTimeMillis());
                    c.commit();
                    applied++;
                } catch (SQLException e) {
                    c.rollback();
                    throw new SQLException("Migração " + v + " de " + owner + " falhou: " + e.getMessage(), e);
                } finally {
                    c.setAutoCommit(auto);
                }
            }
            if (applied > 0 && log != null) {
                log.info("Banco: " + applied + " migração(ões) de " + owner + " aplicada(s) (agora na versão " + steps.size() + ").");
            }
            return applied;
        });
    }

    @Override
    public void close() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(10, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ds.close();
    }

    /** Erro de banco dentro de um {@link CompletableFuture}. */
    public static final class DatabaseException extends RuntimeException {
        public DatabaseException(SQLException cause) {
            super(cause.getMessage(), cause);
        }
    }
}
