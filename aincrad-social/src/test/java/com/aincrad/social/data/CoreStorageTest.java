package com.aincrad.social.data;

import com.aincrad.core.db.Database;
import com.aincrad.social.guild.Guild;
import com.aincrad.social.guild.GuildRules;
import com.aincrad.social.guild.GuildService;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os dados no banco, com dois "servidores" (lobby e andar1) sobre o mesmo SQLite e um MessageBus
 * de mentira que entrega os avisos de um ao outro.
 */
class CoreStorageTest {

    /** O MessageBus: o que um servidor publica chega aos handlers dos outros. */
    private static final class Bus {
        final Map<String, List<Consumer<String>>> handlers = new ConcurrentHashMap<>();

        void publish(String from, String channel, String payload) {
            handlers.forEach((key, list) -> {
                if (key.endsWith("|" + channel) && !key.startsWith(from + "|")) {
                    list.forEach(h -> h.accept(payload));
                }
            });
        }
    }

    /** Um servidor da rede. */
    private final class Server implements CoreStorage.Net, CoreStorage.Host {
        final String name;
        final Database db;
        final File folder;
        final GuildService guilds = new GuildService(GuildRules.defaults(), System::currentTimeMillis);
        final CoreStorage storage;
        final List<UUID> removed = new CopyOnWriteArrayList<>();
        final List<UUID> newMail = new CopyOnWriteArrayList<>();
        final List<String> chat = new CopyOnWriteArrayList<>();

        Server(String name) throws Exception {
            this.name = name;
            this.folder = new File(tmp, name);
            assertTrue(folder.isDirectory() || folder.mkdirs());
            this.db = Database.sqlite(new File(tmp, "rede.db"), Logger.getAnonymousLogger());
            this.storage = new CoreStorage(db, this, this, Logger.getAnonymousLogger(), folder);
            servers.add(this);
        }

        Server load() throws Exception {
            storage.load(guilds);
            return this;
        }

        TrackedSocialStore store() {
            return (TrackedSocialStore) storage.store();
        }

        @Override
        public String server() {
            return name;
        }

        @Override
        public void publish(String channel, String payload) {
            bus.publish(name, channel, payload);
        }

        @Override
        public void subscribe(String channel, Consumer<String> handler) {
            bus.handlers.computeIfAbsent(name + "|" + channel, k -> new CopyOnWriteArrayList<>()).add(handler);
        }

        @Override
        public void main(Runnable r) {
            r.run();
        }

        @Override
        public void later(long ticks, Runnable r) {
            r.run();
        }

        @Override
        public void every(long ticks, Runnable r) {
            // sem relógio nos testes
        }

        @Override
        public void refreshNames(Collection<UUID> players) {
        }

        @Override
        public void guildRemoved(UUID guild) {
            removed.add(guild);
        }

        @Override
        public void inboxGrew(UUID player, int unread) {
            newMail.add(player);
        }

        @Override
        public void chat(Collection<UUID> guildIds, Component line) {
            chat.add(guildIds.size() + ":" + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line));
        }
    }

    @TempDir
    File tmp;

    private final Bus bus = new Bus();
    private final List<Server> servers = new ArrayList<>();
    private final UUID kirito = UUID.randomUUID();
    private final UUID asuna = UUID.randomUUID();
    private final UUID klein = UUID.randomUUID();

    /** Espera as gravações e os avisos irem e voltarem entre os servidores. */
    private void settle() {
        for (int round = 0; round < 4; round++) {
            servers.forEach(s -> s.storage.await());
        }
    }

    @AfterEach
    void close() {
        for (Server s : servers) {
            s.storage.shutdown();
            s.db.close();
        }
    }

    @Test
    void amigosPedidosECaixaChegamAoOutroServidor() throws Exception {
        Server lobby = new Server("lobby").load();
        Server andar = new Server("andar1").load();

        lobby.store().rememberName(kirito, "Kirito");
        lobby.store().rememberName(asuna, "Asuna");
        lobby.store().addFriend(kirito, asuna);
        lobby.store().addRequest(klein, kirito);
        InboxMessage m = new InboxMessage(UUID.randomUUID(), asuna, "Asuna", "Jantar hoje?", 123L, false);
        lobby.store().setInbox(kirito, List.of(m));
        lobby.storage.saveSoon();
        settle();

        assertTrue(andar.store().friends(kirito).contains(asuna));
        assertTrue(andar.store().friends(asuna).contains(kirito));
        assertTrue(andar.store().requests(kirito).contains(klein));
        assertEquals("Kirito", andar.store().name(kirito));
        assertEquals(asuna, andar.store().byName("asuna"));
        assertEquals(List.of(m), andar.store().inbox(kirito));
        // O Kirito, se estiver no outro servidor, é avisado da mensagem nova.
        assertEquals(List.of(kirito), andar.newMail);

        // E na volta: desfeita a amizade no andar 1, some no lobby.
        andar.store().removeFriend(asuna, kirito);
        andar.store().removeRequest(klein, kirito);
        andar.store().setInbox(kirito, List.of(m.asRead()));
        andar.storage.saveSoon();
        settle();
        assertTrue(lobby.store().friends(kirito).isEmpty());
        assertTrue(lobby.store().friends(asuna).isEmpty());
        assertTrue(lobby.store().requests(kirito).isEmpty());
        assertTrue(lobby.store().inbox(kirito).get(0).read());
        assertTrue(lobby.newMail.isEmpty()); // marcar como lida não é mensagem nova

        // Um servidor que liga depois lê tudo do banco.
        Server novo = new Server("andar2").load();
        assertEquals("Asuna", novo.store().name(asuna));
        assertEquals(1, novo.store().inbox(kirito).size());
        assertTrue(novo.store().friends(kirito).isEmpty());
    }

    @Test
    void guildaCriadaMudadaEDesfeitaValeParaARede() throws Exception {
        Server lobby = new Server("lobby").load();
        Server andar = new Server("andar1").load();

        lobby.guilds.create(asuna, "Cavaleiros do Juramento", "KoB", null, 5000);
        Guild g = lobby.guilds.guildOf(asuna);
        lobby.guilds.invite(asuna, kirito);
        lobby.guilds.acceptInvite(kirito, g.id());
        lobby.storage.saveGuildsSoon();
        settle();

        Guild there = andar.guilds.guildOf(kirito);
        assertNotNull(there);
        assertEquals("KOB", there.tag());
        assertEquals(2, there.size());

        // O depósito feito no andar 1 aparece no lobby.
        andar.guilds.deposit(asuna, "Asuna", 700);
        andar.storage.saveGuildsSoon();
        settle();
        assertEquals(700, lobby.guilds.get(g.id()).treasury());

        // Quem sai da guilda fica com a espera gravada para a rede inteira.
        andar.guilds.leave(kirito);
        andar.storage.saveGuildsSoon();
        settle();
        assertNull(lobby.guilds.guildOf(kirito));
        assertTrue(lobby.guilds.cooldownLeft(kirito) > 0);

        // Desfeita no lobby: some no andar 1, que fecha o cofre de quem o tinha aberto.
        assertNotNull(lobby.guilds.disband(asuna));
        lobby.storage.saveGuildsSoon();
        settle();
        assertNull(andar.guilds.get(g.id()));
        assertNull(andar.guilds.guildOf(asuna));
        assertEquals(List.of(g.id()), andar.removed);

        Server novo = new Server("andar2").load();
        assertTrue(novo.guilds.all().isEmpty());
    }

    @Test
    void doisServidoresMudandoAMesmaGuildaValeAPrimeiraGravacao() throws Exception {
        Server lobby = new Server("lobby").load();
        Server andar = new Server("andar1").load();
        lobby.guilds.create(asuna, "Cavaleiros do Juramento", "KoB", null, 5000);
        UUID id = lobby.guilds.guildOf(asuna).id();
        lobby.storage.saveGuildsSoon();
        settle();

        // Os dois mexem antes de saber um do outro.
        lobby.guilds.deposit(asuna, "Asuna", 100);
        andar.guilds.deposit(asuna, "Asuna", 999);
        lobby.storage.flushGuilds(false); // grava, mas o aviso "se perde"
        lobby.storage.await();
        andar.storage.flushGuilds(true);
        settle();

        // O andar 1 chegou depois: a mudança dele foi descartada e ele releu a do lobby.
        assertEquals(100, lobby.guilds.get(id).treasury());
        assertEquals(100, andar.guilds.get(id).treasury());

        // Depois de reler, o andar 1 volta a gravar normalmente.
        andar.guilds.deposit(asuna, "Asuna", 50);
        andar.storage.saveGuildsSoon();
        settle();
        assertEquals(150, lobby.guilds.get(id).treasury());
    }

    @Test
    void cofreSoAbreEmUmServidorPorVez() throws Exception {
        Server lobby = new Server("lobby").load();
        Server andar = new Server("andar1").load();
        lobby.guilds.create(asuna, "Cavaleiros do Juramento", "KoB", null, 5000);
        UUID id = lobby.guilds.guildOf(asuna).id();
        lobby.storage.saveGuildsSoon();
        settle();

        AtomicReference<Boolean> a = new AtomicReference<>();
        AtomicReference<Boolean> b = new AtomicReference<>();
        AtomicReference<String> data = new AtomicReference<>();
        lobby.storage.lockVault(id, (mine, items) -> a.set(mine));
        settle();
        andar.storage.lockVault(id, (mine, items) -> b.set(mine));
        settle();
        assertTrue(a.get());
        assertFalse(b.get());

        // O lobby guarda itens e fecha: o andar 1 passa a conseguir abrir e recebe os itens novos.
        lobby.storage.saveVault(id, "espada;;");
        lobby.storage.unlockVault(id, "espada;escudo;");
        settle();
        assertEquals("espada;escudo;", andar.guilds.get(id).vaultData());
        andar.storage.lockVault(id, (mine, items) -> {
            b.set(mine);
            data.set(items);
        });
        settle();
        assertTrue(b.get());
        assertEquals("espada;escudo;", data.get());

        // Quem não tem a vez não grava por cima.
        lobby.storage.saveVault(id, "nada");
        settle();
        andar.storage.unlockVault(id, "espada;escudo;");
        settle();
        Server novo = new Server("andar2").load();
        assertEquals("espada;escudo;", novo.guilds.get(id).vaultData());
    }

    @Test
    void manutencaoSoEmUmServidorEChatParaOsOutros() throws Exception {
        Server lobby = new Server("lobby").load();
        Server andar = new Server("andar1").load();
        AtomicReference<Boolean> a = new AtomicReference<>();
        AtomicReference<Boolean> b = new AtomicReference<>();
        lobby.storage.exclusive("manutencao", a::set);
        settle();
        andar.storage.exclusive("manutencao", b::set);
        settle();
        assertTrue(a.get());
        assertFalse(b.get());
        // O mesmo servidor continua com a vez.
        lobby.storage.exclusive("manutencao", a::set);
        settle();
        assertTrue(a.get());

        UUID guild = UUID.randomUUID();
        lobby.storage.publishChat(List.of(guild), Component.text("[KoB] Asuna: reunião às 20h"));
        settle();
        assertEquals(List.of("1:[KoB] Asuna: reunião às 20h"), andar.chat);
        assertTrue(lobby.chat.isEmpty()); // quem falou já mostrou a linha aos seus
    }

    @Test
    void arquivosAntigosVaoParaOBancoUmaVez() throws Exception {
        Server lobby = new Server("lobby");
        // O que havia em arquivo antes do banco.
        File data = new File(lobby.folder, "dados.yml");
        YamlSocialStore old = new YamlSocialStore(data, Logger.getAnonymousLogger());
        old.rememberName(kirito, "Kirito");
        old.addFriend(kirito, asuna);
        old.addRequest(klein, asuna);
        old.setInbox(asuna, List.of(new InboxMessage(UUID.randomUUID(), kirito, "Kirito", "oi", 5L, false)));
        old.save();
        GuildService before = new GuildService(GuildRules.defaults(), System::currentTimeMillis);
        before.create(asuna, "Cavaleiros do Juramento", "KoB", null, 5000);
        before.setVaultData(before.guildOf(asuna), "espada;;");
        before.addPendingRefund(klein, 250);
        com.aincrad.social.guild.GuildStore oldGuilds =
                new com.aincrad.social.guild.GuildStore(new File(lobby.folder, "guildas.yml"), Logger.getAnonymousLogger());
        oldGuilds.write(oldGuilds.snapshot(before));

        lobby.load();
        assertTrue(lobby.store().friends(asuna).contains(kirito));
        assertEquals("KOB", lobby.guilds.guildOf(asuna).tag());
        assertFalse(data.exists());
        assertTrue(new File(lobby.folder, "dados.yml.importado").isFile());
        assertTrue(new File(lobby.folder, "guildas.yml.importado").isFile());

        // Outro servidor, sem arquivo nenhum, já encontra tudo no banco.
        Server andar = new Server("andar1").load();
        assertTrue(andar.store().friends(kirito).contains(asuna));
        assertTrue(andar.store().requests(asuna).contains(klein));
        assertEquals(1, andar.store().inbox(asuna).size());
        Guild g = andar.guilds.guildOf(asuna);
        assertNotNull(g);
        assertEquals("espada;;", g.vaultData());
        assertEquals(250, andar.guilds.takePendingRefund(klein));
    }
}
