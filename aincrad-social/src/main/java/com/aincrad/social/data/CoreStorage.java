package com.aincrad.social.data;

import com.aincrad.core.db.Database;
import com.aincrad.social.data.TrackedSocialStore.Changes;
import com.aincrad.social.data.TrackedSocialStore.Op;
import com.aincrad.social.guild.Guild;
import com.aincrad.social.guild.GuildService;
import com.aincrad.social.guild.GuildStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dados no banco do AincradCore: os mesmos em todos os servidores da rede.
 *
 * <ul>
 *   <li>Amigos, pedidos, caixa de entrada e nomes: uma linha por item; só o que mudou é gravado.</li>
 *   <li>Guildas: um documento por guilda, com versão. Se dois servidores mudam a mesma guilda no
 *       mesmo instante, vale a primeira gravação; o outro servidor relê a guilda do banco — menos o
 *       tesouro: o Col que entrou ou saiu ali é somado de novo depois de reler.</li>
 *   <li>Cofre: os itens ficam fora do documento, e só um servidor por vez abre o cofre de uma
 *       guilda (trava com prazo, renovada enquanto o cofre está aberto).</li>
 * </ul>
 *
 * Cada mudança é avisada pelo MessageBus; os outros servidores releem só o que mudou. Toda a
 * escrita passa por uma thread só, para as gravações saírem na ordem em que aconteceram.
 */
public final class CoreStorage implements Storage {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE social_names (player VARCHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL);"
                    + "CREATE TABLE social_friends (a VARCHAR(36) NOT NULL, b VARCHAR(36) NOT NULL, PRIMARY KEY (a, b));"
                    + "CREATE INDEX social_friends_b ON social_friends (b);"
                    + "CREATE TABLE social_requests (sender VARCHAR(36) NOT NULL, target VARCHAR(36) NOT NULL, PRIMARY KEY (sender, target));"
                    + "CREATE INDEX social_requests_target ON social_requests (target);"
                    + "CREATE TABLE social_inbox (id VARCHAR(36) PRIMARY KEY, owner VARCHAR(36) NOT NULL, sender VARCHAR(36) NOT NULL, "
                    + "sender_name VARCHAR(32) NOT NULL, body TEXT NOT NULL, sent BIGINT NOT NULL, seen INT NOT NULL, pos INT NOT NULL);"
                    + "CREATE INDEX social_inbox_owner ON social_inbox (owner);"
                    + "CREATE TABLE social_guilds (id VARCHAR(36) PRIMARY KEY, body LONGTEXT NOT NULL, version BIGINT NOT NULL);"
                    + "CREATE TABLE social_guild_players (player VARCHAR(36) PRIMARY KEY, refund BIGINT NOT NULL, left_at BIGINT NOT NULL);"
                    + "CREATE TABLE social_vaults (guild VARCHAR(36) PRIMARY KEY, data LONGTEXT NOT NULL);"
                    + "CREATE TABLE social_locks (name VARCHAR(64) PRIMARY KEY, holder VARCHAR(64), lease BIGINT NOT NULL DEFAULT 0)");

    public static final String CH_PLAYERS = "social:players";
    public static final String CH_GUILD = "social:guild";
    public static final String CH_GUILD_PLAYERS = "social:gplayers";
    public static final String CH_VAULT = "social:vault";
    public static final String CH_CHAT = "social:chat";

    /** Trava do cofre: vale este tempo sem renovar (servidor que caiu libera sozinho). */
    static final long VAULT_LEASE = 60_000L;
    /** Trava da manutenção: um servidor por janela. */
    static final long EXCLUSIVE_LEASE = 9 * 60_000L;
    /** Acima disto, a lista de jogadores não vai no aviso (os outros servidores releem ao reiniciar). */
    private static final int MAX_NOTICE = 300;

    /** O que o armazenamento precisa do servidor e da rede. */
    public interface Net {
        String server();

        /** Avisa os outros servidores. */
        void publish(String channel, String payload);

        /** Recebe avisos dos OUTROS servidores, na thread principal. */
        void subscribe(String channel, Consumer<String> handler);

        /** Roda na thread principal. */
        void main(Runnable r);

        /** Roda na thread principal daqui a {@code ticks}. */
        void later(long ticks, Runnable r);

        /** Roda na thread principal a cada {@code ticks}. */
        void every(long ticks, Runnable r);
    }

    /** O que o plugin faz quando os dados mudam por causa de outro servidor. */
    public interface Host {
        /** Guildas destes jogadores mudaram: atualizar a tag no nome de quem está online. */
        void refreshNames(Collection<UUID> players);

        /** A guilda acabou em outro servidor: fechar o cofre de quem o tem aberto aqui. */
        void guildRemoved(UUID guild);

        /** Chegou mensagem nova na caixa do jogador. */
        void inboxGrew(UUID player, int unread);

        /** Uma linha de chat de guilda vinda de outro servidor. */
        void chat(Collection<UUID> guilds, Component line);
    }

    @FunctionalInterface
    private interface Sql {
        void run(Connection c) throws SQLException;
    }

    private final Database db;
    private final Net net;
    private final Host host;
    private final Logger log;
    private final File folder;
    private final TrackedSocialStore store = new TrackedSocialStore();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AincradSocial-DB");
        t.setDaemon(true);
        return t;
    });
    private GuildService guilds;
    /** O que já está no banco, para gravar só o que mudou (thread principal). */
    private final Map<UUID, String> savedGuilds = new HashMap<>();
    private final Map<UUID, Long> versions = new HashMap<>();
    private final Map<UUID, long[]> savedPlayers = new HashMap<>();
    /** O tesouro de cada guilda na última versão confirmada no banco. */
    private final Map<UUID, Long> confirmedTreasury = new HashMap<>();
    /** Cofres que este servidor tem abertos. */
    private final Set<UUID> heldVaults = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> pendingVaults = new LinkedHashMap<>();
    private boolean storeScheduled;
    private boolean guildsScheduled;
    private boolean vaultsScheduled;
    private volatile boolean closed;

    public CoreStorage(Database db, Net net, Host host, Logger log, File folder) {
        this.db = db;
        this.net = net;
        this.host = host;
        this.log = log;
        this.folder = folder;
    }

    @Override
    public SocialStore store() {
        return store;
    }

    @Override
    public boolean network() {
        return true;
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<UUID> uuids(String csv) {
        List<UUID> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            UUID id = uuid(s.trim());
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private static String csv(Collection<UUID> ids) {
        StringBuilder sb = new StringBuilder();
        for (UUID id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    /** Grava (ou lê) fora da thread principal, uma coisa de cada vez. */
    private void io(Sql work) {
        if (closed) {
            return;
        }
        io.execute(() -> {
            try {
                db.sync(c -> {
                    work.run(c);
                    return null;
                });
            } catch (SQLException | RuntimeException e) {
                log.log(Level.WARNING, "AincradSocial: falha no banco", e);
            }
        });
    }

    /** Espera o que está na fila ser gravado (testes e desligamento). */
    public void await() {
        try {
            io.submit(() -> { }).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.log(Level.WARNING, "AincradSocial: o banco demorou demais", e);
        }
    }

    // ------------------------------------------------------------------ ligar

    @Override
    public void load(GuildService guilds) throws SQLException {
        this.guilds = guilds;
        db.migrate("AincradSocial", MIGRATIONS);
        boolean empty = db.sync(c -> {
            // Travas que ficaram de quando este servidor caiu.
            Database.update(c, "UPDATE social_locks SET holder = NULL, lease = 0 WHERE holder = ?", net.server());
            Database.query(c, "SELECT player, name FROM social_names", rs -> {
                UUID p = uuid(rs.getString(1));
                if (p != null) {
                    store.loadName(p, rs.getString(2));
                }
                return null;
            });
            Database.query(c, "SELECT a, b FROM social_friends", rs -> {
                UUID a = uuid(rs.getString(1));
                UUID b = uuid(rs.getString(2));
                if (a != null && b != null) {
                    store.loadFriend(a, b);
                }
                return null;
            });
            Database.query(c, "SELECT sender, target FROM social_requests", rs -> {
                UUID a = uuid(rs.getString(1));
                UUID b = uuid(rs.getString(2));
                if (a != null && b != null) {
                    store.loadRequest(a, b);
                }
                return null;
            });
            Database.query(c, "SELECT owner, id, sender, sender_name, body, sent, seen FROM social_inbox ORDER BY owner, pos", rs -> {
                UUID owner = uuid(rs.getString(1));
                UUID id = uuid(rs.getString(2));
                UUID from = uuid(rs.getString(3));
                if (owner != null && id != null && from != null) {
                    store.loadInbox(owner, new InboxMessage(id, from, rs.getString(4), rs.getString(5), rs.getLong(6), rs.getInt(7) != 0));
                }
                return null;
            });
            int count = 0;
            for (String[] row : Database.query(c, "SELECT id, body, version FROM social_guilds",
                    rs -> new String[]{rs.getString(1), rs.getString(2), String.valueOf(rs.getLong(3))})) {
                count++;
                UUID id = uuid(row[0]);
                try {
                    Guild g = GuildStore.decode(id, row[1]);
                    GuildStore.replace(guilds, g);
                    savedGuilds.put(id, GuildStore.encode(g, false));
                    versions.put(id, Long.parseLong(row[2]));
                    confirmedTreasury.put(id, g.treasury());
                } catch (RuntimeException e) {
                    log.warning("Guilda " + row[0] + " ignorada (dados inválidos): " + e.getMessage());
                }
            }
            for (String[] row : Database.query(c, "SELECT guild, data FROM social_vaults", rs -> new String[]{rs.getString(1), rs.getString(2)})) {
                UUID id = uuid(row[0]);
                Guild g = id == null ? null : guilds.get(id);
                if (g != null) {
                    guilds.setVaultData(g, row[1]);
                }
            }
            for (long[] row : Database.query(c, "SELECT player, refund, left_at FROM social_guild_players", rs -> {
                UUID p = uuid(rs.getString(1));
                return p == null ? null : new long[]{p.getMostSignificantBits(), p.getLeastSignificantBits(), rs.getLong(2), rs.getLong(3)};
            })) {
                if (row != null) {
                    UUID p = new UUID(row[0], row[1]);
                    GuildStore.setPlayerState(guilds, p, row[2], row[3]);
                    savedPlayers.put(p, new long[]{row[2], row[3]});
                }
            }
            return count == 0 && store.names.isEmpty();
        });
        if (empty) {
            importYaml();
        }
        net.subscribe(CH_PLAYERS, payload -> reloadPlayers(uuids(payload)));
        net.subscribe(CH_GUILD, payload -> {
            UUID id = uuid(payload);
            if (id != null) {
                reloadGuild(id);
            }
        });
        net.subscribe(CH_GUILD_PLAYERS, payload -> reloadGuildPlayers(uuids(payload)));
        net.subscribe(CH_VAULT, payload -> {
            UUID id = uuid(payload);
            if (id != null) {
                reloadVault(id);
            }
        });
        net.subscribe(CH_CHAT, payload -> {
            int cut = payload.indexOf('\n');
            if (cut > 0) {
                host.chat(uuids(payload.substring(0, cut)), GsonComponentSerializer.gson().deserialize(payload.substring(cut + 1)));
            }
        });
        // Renova as travas dos cofres abertos aqui.
        net.every(400L, () -> {
            for (UUID id : heldVaults) {
                io(c -> Database.update(c, "UPDATE social_locks SET lease = ? WHERE name = ? AND holder = ?",
                        System.currentTimeMillis() + VAULT_LEASE, "vault:" + id, net.server()));
            }
        });
    }

    /** Banco vazio e arquivos antigos na pasta: leva dados.yml e guildas.yml para o banco, uma vez. */
    private void importYaml() {
        File data = new File(folder, "dados.yml");
        File guildFile = new File(folder, "guildas.yml");
        if (data.isFile()) {
            YamlSocialStore old = new YamlSocialStore(data, log);
            old.load();
            store.importFrom(old);
            flushStore(false);
            log.info("dados.yml levado para o banco (amigos, pedidos, caixas e nomes).");
            done(data);
        }
        if (guildFile.isFile()) {
            new GuildStore(guildFile, log).load(guilds);
            for (Guild g : guilds.all()) {
                String vault = g.vaultData();
                if (!vault.isEmpty()) {
                    UUID id = g.id();
                    io(c -> Database.update(c, db.upsert("social_vaults", List.of("guild"), List.of("data")), id.toString(), vault));
                }
            }
            flushGuilds(false);
            log.info("guildas.yml levado para o banco (" + guilds.all().size() + " guilda(s)).");
            done(guildFile);
        }
        await();
    }

    private void done(File f) {
        File to = new File(f.getParentFile(), f.getName() + ".importado");
        if (!f.renameTo(to)) {
            log.warning("Não foi possível renomear " + f.getName() + " para " + to.getName()
                    + ": apague-o à mão para ele não ser confundido com os dados em uso.");
        }
    }

    // ------------------------------------------------------------------ amigos, pedidos, caixa, nomes

    @Override
    public void saveSoon() {
        if (storeScheduled || closed) {
            return;
        }
        storeScheduled = true;
        net.later(10L, () -> {
            storeScheduled = false;
            flushStore(true);
        });
    }

    private static String[] ordered(UUID a, UUID b) {
        String x = a.toString();
        String y = b.toString();
        return x.compareTo(y) < 0 ? new String[]{x, y} : new String[]{y, x};
    }

    /** Grava as mudanças anotadas. Público para os testes. */
    public void flushStore(boolean notify) {
        Changes ch = store.drain();
        if (ch.isEmpty()) {
            return;
        }
        io(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (Op op : ch.ops()) {
                    write(c, op);
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            if (notify && ch.players().size() <= MAX_NOTICE) {
                net.publish(CH_PLAYERS, csv(ch.players()));
            }
        });
    }

    private void write(Connection c, Op op) throws SQLException {
        switch (op.kind()) {
            case FRIEND_ADD -> {
                String[] p = ordered(op.a(), op.b());
                Database.update(c, db.upsert("social_friends", List.of("a", "b"), List.of()), p[0], p[1]);
            }
            case FRIEND_DEL -> {
                String[] p = ordered(op.a(), op.b());
                Database.update(c, "DELETE FROM social_friends WHERE a = ? AND b = ?", p[0], p[1]);
            }
            case REQUEST_ADD -> Database.update(c, db.upsert("social_requests", List.of("sender", "target"), List.of()),
                    op.a().toString(), op.b().toString());
            case REQUEST_DEL -> Database.update(c, "DELETE FROM social_requests WHERE sender = ? AND target = ?",
                    op.a().toString(), op.b().toString());
            case NAME -> Database.update(c, db.upsert("social_names", List.of("player"), List.of("name")), op.a().toString(), clip(op.text(), 32));
            case INBOX_SET -> {
                Database.update(c, "DELETE FROM social_inbox WHERE owner = ?", op.a().toString());
                int pos = 0;
                for (InboxMessage m : op.inbox()) {
                    Database.update(c, "INSERT INTO social_inbox (id, owner, sender, sender_name, body, sent, seen, pos) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            m.id().toString(), op.a().toString(), m.from().toString(), clip(m.fromName(), 32), m.text(), m.time(), m.read() ? 1 : 0, pos++);
                }
            }
        }
    }

    private static String clip(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max);
    }

    private record PlayerRows(UUID player, Set<UUID> friends, Set<UUID> requests, List<InboxMessage> inbox, String name) {
    }

    private void reloadPlayers(List<UUID> players) {
        if (players.isEmpty()) {
            return;
        }
        io(c -> {
            List<PlayerRows> rows = new ArrayList<>();
            for (UUID p : players) {
                String id = p.toString();
                Set<UUID> friends = new LinkedHashSet<>();
                Database.query(c, "SELECT a, b FROM social_friends WHERE a = ? OR b = ?", rs -> {
                    UUID other = uuid(id.equals(rs.getString(1)) ? rs.getString(2) : rs.getString(1));
                    if (other != null) {
                        friends.add(other);
                    }
                    return null;
                }, id, id);
                Set<UUID> requests = new LinkedHashSet<>();
                Database.query(c, "SELECT sender FROM social_requests WHERE target = ?", rs -> {
                    UUID from = uuid(rs.getString(1));
                    if (from != null) {
                        requests.add(from);
                    }
                    return null;
                }, id);
                List<InboxMessage> inbox = new ArrayList<>();
                Database.query(c, "SELECT id, sender, sender_name, body, sent, seen FROM social_inbox WHERE owner = ? ORDER BY pos", rs -> {
                    UUID mid = uuid(rs.getString(1));
                    UUID from = uuid(rs.getString(2));
                    if (mid != null && from != null) {
                        inbox.add(new InboxMessage(mid, from, rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6) != 0));
                    }
                    return null;
                }, id);
                List<String> name = Database.query(c, "SELECT name FROM social_names WHERE player = ?", rs -> rs.getString(1), id);
                rows.add(new PlayerRows(p, friends, requests, inbox, name.isEmpty() ? null : name.get(0)));
            }
            net.main(() -> {
                for (PlayerRows r : rows) {
                    int before = store.unread(r.player());
                    store.apply(r.player(), r.friends(), r.requests(), r.inbox(), r.name());
                    int after = store.unread(r.player());
                    if (after > before) {
                        host.inboxGrew(r.player(), after);
                    }
                }
            });
        });
    }

    // ------------------------------------------------------------------ guildas

    @Override
    public void saveGuildsSoon() {
        if (guildsScheduled || closed || guilds == null) {
            return;
        }
        guildsScheduled = true;
        net.later(10L, () -> {
            guildsScheduled = false;
            flushGuilds(true);
        });
    }

    /** Grava as guildas que mudaram desde a última vez. Público para os testes. */
    public void flushGuilds(boolean notify) {
        if (guilds == null) {
            return;
        }
        String server = net.server();
        Set<UUID> alive = new HashSet<>();
        for (Guild g : guilds.all()) {
            UUID id = g.id();
            alive.add(id);
            String body = GuildStore.encode(g, false);
            if (body.equals(savedGuilds.get(id))) {
                continue;
            }
            long version = versions.getOrDefault(id, 0L);
            long treasury = g.treasury();
            savedGuilds.put(id, body);
            versions.put(id, version + 1);
            io(c -> {
                boolean ok;
                if (version == 0) {
                    try {
                        ok = Database.update(c, "INSERT INTO social_guilds (id, body, version) VALUES (?, ?, 1)", id.toString(), body) > 0;
                    } catch (SQLException e) {
                        ok = false; // outro servidor criou primeiro
                    }
                } else {
                    ok = Database.update(c, "UPDATE social_guilds SET body = ?, version = ? WHERE id = ? AND version = ?",
                            body, version + 1, id.toString(), version) > 0;
                }
                if (!ok) {
                    log.warning("A guilda " + id + " foi mudada em outro servidor ao mesmo tempo: a mudança feita em \""
                            + server + "\" foi descartada e a guilda relida do banco.");
                    net.main(() -> reloadGuild(id));
                } else {
                    net.main(() -> confirmedTreasury.put(id, treasury));
                    if (notify) {
                        net.publish(CH_GUILD, id.toString());
                    }
                }
            });
        }
        for (UUID id : new ArrayList<>(savedGuilds.keySet())) {
            if (!alive.contains(id)) {
                savedGuilds.remove(id);
                versions.remove(id);
                confirmedTreasury.remove(id);
                io(c -> {
                    Database.update(c, "DELETE FROM social_guilds WHERE id = ?", id.toString());
                    Database.update(c, "DELETE FROM social_vaults WHERE guild = ?", id.toString());
                    if (notify) {
                        net.publish(CH_GUILD, id.toString());
                    }
                });
            }
        }
        // Reembolsos pendentes e a data em que cada jogador saiu da guilda.
        Map<UUID, long[]> now = GuildStore.playerStates(guilds);
        List<UUID> changed = new ArrayList<>();
        for (Map.Entry<UUID, long[]> e : now.entrySet()) {
            long[] old = savedPlayers.get(e.getKey());
            long[] v = e.getValue();
            if (old == null || old[0] != v[0] || old[1] != v[1]) {
                changed.add(e.getKey());
            }
        }
        List<UUID> gone = new ArrayList<>();
        for (UUID p : savedPlayers.keySet()) {
            if (!now.containsKey(p)) {
                gone.add(p);
            }
        }
        if (changed.isEmpty() && gone.isEmpty()) {
            return;
        }
        Map<UUID, long[]> values = new HashMap<>();
        for (UUID p : changed) {
            long[] v = now.get(p).clone();
            values.put(p, v);
            savedPlayers.put(p, v);
        }
        gone.forEach(savedPlayers::remove);
        io(c -> {
            for (Map.Entry<UUID, long[]> e : values.entrySet()) {
                Database.update(c, db.upsert("social_guild_players", List.of("player"), List.of("refund", "left_at")),
                        e.getKey().toString(), e.getValue()[0], e.getValue()[1]);
            }
            for (UUID p : gone) {
                Database.update(c, "DELETE FROM social_guild_players WHERE player = ?", p.toString());
            }
            List<UUID> all = new ArrayList<>(changed);
            all.addAll(gone);
            if (notify && all.size() <= MAX_NOTICE) {
                net.publish(CH_GUILD_PLAYERS, csv(all));
            }
        });
    }

    /** Relê uma guilda do banco (outro servidor mudou, ou a gravação daqui perdeu a vez). */
    private void reloadGuild(UUID id) {
        io(c -> {
            List<String[]> rows = Database.query(c, "SELECT body, version FROM social_guilds WHERE id = ?",
                    rs -> new String[]{rs.getString(1), String.valueOf(rs.getLong(2))}, id.toString());
            net.main(() -> {
                Guild old = guilds.get(id);
                Set<UUID> affected = new LinkedHashSet<>();
                if (old != null) {
                    affected.addAll(old.members().keySet());
                }
                // Col que entrou ou saiu do tesouro aqui e ainda não chegou ao banco: não se perde.
                long pending = old == null ? 0 : old.treasury() - confirmedTreasury.getOrDefault(id, old.treasury());
                if (rows.isEmpty()) {
                    savedGuilds.remove(id);
                    versions.remove(id);
                    confirmedTreasury.remove(id);
                    if (old != null) {
                        GuildStore.drop(guilds, id);
                        host.guildRemoved(id);
                    }
                } else {
                    try {
                        Guild g = GuildStore.decode(id, rows.get(0)[0]);
                        GuildStore.replace(guilds, g);
                        savedGuilds.put(id, GuildStore.encode(g, false));
                        versions.put(id, Long.parseLong(rows.get(0)[1]));
                        confirmedTreasury.put(id, g.treasury());
                        affected.addAll(g.members().keySet());
                        if (pending != 0) {
                            GuildStore.addTreasury(g, pending);
                            saveGuildsSoon();
                        }
                    } catch (RuntimeException e) {
                        log.warning("Guilda " + id + " veio inválida do banco: " + e.getMessage());
                    }
                }
                host.refreshNames(affected);
            });
        });
    }

    private void reloadGuildPlayers(List<UUID> players) {
        if (players.isEmpty()) {
            return;
        }
        io(c -> {
            Map<UUID, long[]> found = new HashMap<>();
            for (UUID p : players) {
                List<long[]> rows = Database.query(c, "SELECT refund, left_at FROM social_guild_players WHERE player = ?",
                        rs -> new long[]{rs.getLong(1), rs.getLong(2)}, p.toString());
                found.put(p, rows.isEmpty() ? new long[]{0, 0} : rows.get(0));
            }
            net.main(() -> found.forEach((p, v) -> {
                GuildStore.setPlayerState(guilds, p, v[0], v[1]);
                if (v[0] == 0 && v[1] == 0) {
                    savedPlayers.remove(p);
                } else {
                    savedPlayers.put(p, v);
                }
            }));
        });
    }

    // ------------------------------------------------------------------ travas

    private boolean acquire(Connection c, String name, long leaseMillis) throws SQLException {
        Database.update(c, db.upsert("social_locks", List.of("name"), List.of()), name);
        long now = System.currentTimeMillis();
        return Database.update(c, "UPDATE social_locks SET holder = ?, lease = ? WHERE name = ? AND (holder IS NULL OR holder = ? OR lease < ?)",
                net.server(), now + leaseMillis, name, net.server(), now) > 0;
    }

    @Override
    public void exclusive(String name, Consumer<Boolean> run) {
        io(c -> {
            boolean mine;
            try {
                mine = acquire(c, "x:" + name, EXCLUSIVE_LEASE);
            } catch (SQLException e) {
                mine = false;
            }
            boolean ok = mine;
            net.main(() -> run.accept(ok));
        });
    }

    // ------------------------------------------------------------------ cofre

    @Override
    public void lockVault(UUID guild, BiConsumer<Boolean, String> ready) {
        io(c -> {
            boolean mine = acquire(c, "vault:" + guild, VAULT_LEASE);
            String data = "";
            if (mine) {
                heldVaults.add(guild);
                List<String> rows = Database.query(c, "SELECT data FROM social_vaults WHERE guild = ?", rs -> rs.getString(1), guild.toString());
                data = rows.isEmpty() ? "" : rows.get(0);
            }
            String items = data;
            net.main(() -> ready.accept(mine, items));
        });
    }

    @Override
    public void saveVault(UUID guild, String data) {
        pendingVaults.put(guild, data);
        if (vaultsScheduled || closed) {
            return;
        }
        vaultsScheduled = true;
        net.later(20L, () -> {
            vaultsScheduled = false;
            Map<UUID, String> batch = new LinkedHashMap<>(pendingVaults);
            pendingVaults.clear();
            batch.forEach(this::writeVault);
        });
    }

    private void writeVault(UUID guild, String data) {
        io(c -> {
            // Só grava quem tem a trava: um servidor que perdeu a vez não passa por cima do outro.
            List<String> holder = Database.query(c, "SELECT holder FROM social_locks WHERE name = ?", rs -> rs.getString(1), "vault:" + guild);
            if (holder.isEmpty() || !net.server().equals(holder.get(0))) {
                log.warning("O cofre da guilda " + guild + " não está mais com este servidor: a mudança não foi gravada.");
                return;
            }
            Database.update(c, db.upsert("social_vaults", List.of("guild"), List.of("data")), guild.toString(), data);
        });
    }

    @Override
    public void unlockVault(UUID guild, String data) {
        pendingVaults.remove(guild);
        writeVault(guild, data);
        io(c -> {
            Database.update(c, "UPDATE social_locks SET holder = NULL, lease = 0 WHERE name = ? AND holder = ?", "vault:" + guild, net.server());
            heldVaults.remove(guild);
            net.publish(CH_VAULT, guild.toString());
        });
    }

    private void reloadVault(UUID id) {
        io(c -> {
            List<String> rows = Database.query(c, "SELECT data FROM social_vaults WHERE guild = ?", rs -> rs.getString(1), id.toString());
            String data = rows.isEmpty() ? "" : rows.get(0);
            net.main(() -> {
                Guild g = guilds.get(id);
                if (g != null && !heldVaults.contains(id)) {
                    guilds.setVaultData(g, data);
                }
            });
        });
    }

    // ------------------------------------------------------------------ chat e desligar

    @Override
    public void publish(String channel, String payload) {
        if (!closed) {
            net.publish(channel, payload);
        }
    }

    @Override
    public void subscribe(String channel, Consumer<String> handler) {
        net.subscribe(channel, handler);
    }

    @Override
    public void publishChat(Collection<UUID> guildIds, Component line) {
        if (!guildIds.isEmpty() && !closed) {
            net.publish(CH_CHAT, csv(guildIds) + "\n" + GsonComponentSerializer.gson().serialize(line));
        }
    }

    @Override
    public void shutdown() {
        flushStore(true);
        flushGuilds(true);
        new LinkedHashMap<>(pendingVaults).forEach(this::writeVault);
        pendingVaults.clear();
        io(c -> Database.update(c, "UPDATE social_locks SET holder = NULL, lease = 0 WHERE holder = ?", net.server()));
        await();
        closed = true;
        io.shutdown();
    }
}
