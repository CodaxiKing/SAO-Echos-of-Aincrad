package com.aincrad.core.bus;

import com.aincrad.core.db.Database;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Avisos entre os servidores da rede (andar liberado, anúncios, convites de party): uma tabela de
 * eventos no banco, lida a cada segundo. Quem publica recebe na hora, no próprio servidor; os
 * outros recebem na próxima leitura.
 *
 * <pre>
 * bus.subscribe("floors:unlock", payload -&gt; ...);
 * bus.publish("floors:unlock", "5");
 * </pre>
 * Os handlers rodam na thread que o {@code deliver} indicar (o core usa a thread principal).
 */
public final class MessageBus {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS aincrad_bus ({AUTO_ID}, channel VARCHAR(64) NOT NULL, payload TEXT NOT NULL, "
                    + "origin VARCHAR(64) NOT NULL, created BIGINT NOT NULL)");

    /** Uma mensagem recebida. */
    public record Message(long id, String channel, String payload, String origin, long created) {
    }

    private final Database db;
    private final String server;
    private final Logger log;
    private final Map<String, List<Consumer<Message>>> handlers = new ConcurrentHashMap<>();
    private volatile long lastId = -1;
    private volatile boolean polling;

    public MessageBus(Database db, String server, Logger log) {
        this.db = db;
        this.server = server;
        this.log = log;
    }

    public String server() {
        return server;
    }

    public void subscribe(String channel, Consumer<Message> handler) {
        handlers.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void unsubscribeAll(String channelPrefix) {
        handlers.keySet().removeIf(k -> k.startsWith(channelPrefix));
    }

    /** Publica para a rede; neste servidor os handlers rodam já (pelo {@code localDeliver}). */
    public void publish(String channel, String payload, Consumer<Runnable> localDeliver) {
        long now = System.currentTimeMillis();
        localDeliver.accept(() -> dispatch(new Message(0, channel, payload, server, now)));
        db.update("INSERT INTO aincrad_bus (channel, payload, origin, created) VALUES (?, ?, ?, ?)",
                channel, payload, server, now).exceptionally(ex -> {
            log.log(Level.WARNING, "MessageBus: falha ao publicar em " + channel, ex);
            return 0;
        });
    }

    void dispatch(Message m) {
        List<Consumer<Message>> list = handlers.get(m.channel());
        if (list == null) {
            return;
        }
        for (Consumer<Message> h : list) {
            try {
                h.accept(m);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "MessageBus: erro no handler de " + m.channel(), e);
            }
        }
    }

    /** Começa do fim da tabela (mensagens antigas não são reentregues). */
    public void start() throws SQLException {
        lastId = db.sync(c -> Database.query(c, "SELECT COALESCE(MAX(id), 0) FROM aincrad_bus", rs -> rs.getLong(1)).get(0));
    }

    /**
     * Lê as mensagens novas de outros servidores (fora da thread principal) e entrega pelo
     * {@code deliver}. Chamada pela tarefa periódica.
     */
    public void poll(Consumer<Runnable> deliver) {
        if (polling || lastId < 0) {
            return;
        }
        polling = true;
        db.async(c -> Database.query(c,
                        "SELECT id, channel, payload, origin, created FROM aincrad_bus WHERE id > ? ORDER BY id LIMIT 500",
                        rs -> new Message(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)), lastId))
                .whenComplete((list, ex) -> {
                    polling = false;
                    if (ex != null) {
                        log.log(Level.FINE, "MessageBus: falha ao ler", ex);
                        return;
                    }
                    if (list.isEmpty()) {
                        return;
                    }
                    lastId = list.get(list.size() - 1).id();
                    deliver.accept(() -> {
                        for (Message m : list) {
                            if (!server.equals(m.origin())) {
                                dispatch(m);
                            }
                        }
                    });
                });
    }

    /** Apaga mensagens velhas. */
    public void cleanup(long keepMillis) {
        db.update("DELETE FROM aincrad_bus WHERE created < ?", System.currentTimeMillis() - keepMillis);
    }
}
