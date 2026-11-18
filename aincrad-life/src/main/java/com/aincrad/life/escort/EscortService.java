package com.aincrad.life.escort;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.core.region.RegionEnterEvent;
import com.aincrad.life.escort.EscortRules.End;
import com.aincrad.life.escort.EscortRules.State;
import com.aincrad.life.hook.Hooks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Contratos de escolta: um jogador paga outro para chegar vivo a um lugar (uma região do core).
 * O Col fica em custódia e é pago quando o cliente entra na região com o escolta por perto; se o
 * cliente morre, volta para ele e o escolta fica com uma falha no histórico.
 */
public final class EscortService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_escorts (hold_id VARCHAR(64) NOT NULL PRIMARY KEY, server VARCHAR(64) NOT NULL, "
                    + "client VARCHAR(36) NOT NULL, escort VARCHAR(36) NOT NULL, amount BIGINT NOT NULL, at_time BIGINT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS life_escort_stats (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, "
                    + "done INT NOT NULL, failed INT NOT NULL)");

    /** Um contrato em andamento neste servidor. */
    private static final class Contract {
        final UUID client;
        final UUID escort;
        final String clientName;
        final String escortName;
        final String region;
        final long amount;
        final String hold;
        State state = State.PROPOSTO;
        long since = System.currentTimeMillis();

        Contract(Player client, Player escort, String region, long amount, String hold) {
            this.client = client.getUniqueId();
            this.escort = escort.getUniqueId();
            this.clientName = client.getName();
            this.escortName = escort.getName();
            this.region = region;
            this.amount = amount;
            this.hold = hold;
        }
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final Database db;
    private final Map<UUID, Contract> byClient = new HashMap<>();
    private final Map<UUID, Contract> byEscort = new HashMap<>();

    public EscortService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.db = AincradCore.get().database();
    }

    /** Ao ligar: um contrato que ficou aberto (o servidor caiu no meio) é desfeito, e o Col volta. */
    public void recover() throws SQLException {
        String server = AincradCore.get().server();
        List<String> holds = db.sync(c -> Database.query(c, "SELECT hold_id FROM life_escorts WHERE server = ?", rs -> rs.getString(1), server));
        for (String hold : holds) {
            hooks.refund(hold, "escolta interrompida");
            plugin.getLogger().info("Contrato de escolta interrompido pelo reinício: o Col voltou ao cliente.");
        }
        if (!holds.isEmpty()) {
            db.sync(c -> Database.update(c, "DELETE FROM life_escorts WHERE server = ?", server));
        }
    }

    private double near() {
        return config.decimal("escolta.distancia_maxima", 32, 4, 256);
    }

    private static double distance(Player a, Player b) {
        return a == null || b == null || !a.getWorld().equals(b.getWorld()) ? -1 : a.getLocation().distance(b.getLocation());
    }

    // ------------------------------------------------------------------ propor e aceitar

    private void propose(Player client, String escortName, String regionName, String amountText) {
        Player escort = Bukkit.getPlayerExact(escortName);
        long amount;
        try {
            amount = Long.parseLong(amountText);
        } catch (NumberFormatException e) {
            msg.send(client, "escolta.uso");
            return;
        }
        if (escort == null) {
            msg.send(client, "escolta.offline", Messages.vars("jogador", escortName));
            return;
        }
        Region region = AincradCore.get().regions().get(regionName.toLowerCase(Locale.ROOT));
        long min = config.integer("escolta.minimo", 50, 1, 1_000_000_000);
        EscortRules.Block block = EscortRules.blocked(client.equals(escort), amount, min,
                byClient.containsKey(client.getUniqueId()) || byEscort.containsKey(client.getUniqueId()),
                byClient.containsKey(escort.getUniqueId()) || byEscort.containsKey(escort.getUniqueId()),
                region != null, region != null && region.contains(client.getLocation()));
        if (block != null) {
            msg.send(client, "escolta.bloqueio." + block.name().toLowerCase(Locale.ROOT),
                    Messages.vars("jogador", escort.getName(), "minimo", min, "destino", regionName));
            return;
        }
        String hold = hooks.hold(client, amount, "escolta: " + escort.getName());
        if (hold == null) {
            msg.send(client, "escolta.sem_col", Messages.vars("valor", amount));
            return;
        }
        Contract c = new Contract(client, escort, region.name(), amount, hold);
        byClient.put(c.client, c);
        byEscort.put(c.escort, c);
        db.update("INSERT INTO life_escorts (hold_id, server, client, escort, amount, at_time) VALUES (?, ?, ?, ?, ?, ?)",
                hold, AincradCore.get().server(), c.client.toString(), c.escort.toString(), amount, c.since).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar o contrato de escolta", ex);
            return 0;
        });
        var vars = Messages.vars("cliente", client.getName(), "escolta", escort.getName(), "destino", region.name(), "valor", amount);
        msg.send(client, "escolta.proposta_enviada", vars);
        msg.send(escort, "escolta.proposta_recebida", vars);
    }

    private void accept(Player escort) {
        Contract c = byEscort.get(escort.getUniqueId());
        Player client = c == null ? null : Bukkit.getPlayer(c.client);
        if (c == null || c.state != State.PROPOSTO || client == null) {
            msg.send(escort, "escolta.nada_a_aceitar");
            return;
        }
        if (distance(client, escort) < 0 || distance(client, escort) > near()) {
            msg.send(escort, "escolta.longe", Messages.vars("jogador", c.clientName, "blocos", (int) near()));
            return;
        }
        c.state = State.ATIVO;
        c.since = System.currentTimeMillis();
        var vars = Messages.vars("cliente", c.clientName, "escolta", c.escortName, "destino", c.region, "valor", c.amount,
                "minutos", config.integer("escolta.tempo_maximo_minutos", 60, 1, 1440));
        msg.send(client, "escolta.comecou_cliente", vars);
        msg.send(escort, "escolta.comecou_escolta", vars);
    }

    // ------------------------------------------------------------------ acabar

    private void stat(UUID id, String name, boolean failed) {
        String u = id.toString();
        db.async(c -> {
            if (Database.update(c, "UPDATE life_escort_stats SET name = ?, done = done + ?, failed = failed + ? WHERE uuid = ?",
                    name, failed ? 0 : 1, failed ? 1 : 0, u) == 0) {
                Database.update(c, "INSERT INTO life_escort_stats (uuid, name, done, failed) VALUES (?, ?, ?, ?)", u, name, failed ? 0 : 1, failed ? 1 : 0);
            }
            return null;
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar o histórico de escolta de " + name, ex);
            return null;
        });
    }

    private void end(Contract c, End how) {
        if (byClient.remove(c.client) == null) {
            return; // Já acabou.
        }
        byEscort.remove(c.escort);
        db.update("DELETE FROM life_escorts WHERE hold_id = ?", c.hold);
        if (how.pays()) {
            hooks.release(c.hold, c.escort, c.amount, "escolta de " + c.clientName);
        } else {
            hooks.refund(c.hold, "escolta: " + how.name().toLowerCase(Locale.ROOT));
        }
        // Só um contrato aceito entra no histórico do escolta.
        if (c.state == State.ATIVO && (how.pays() || how.fails())) {
            stat(c.escort, c.escortName, how.fails());
        }
        var vars = Messages.vars("cliente", c.clientName, "escolta", c.escortName, "destino", c.region, "valor", c.amount);
        String key = "escolta.fim." + how.name().toLowerCase(Locale.ROOT);
        for (UUID id : List.of(c.client, c.escort)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg.send(p, key, vars);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegion(RegionEnterEvent event) {
        Contract c = byClient.get(event.getPlayer().getUniqueId());
        if (c == null || !c.region.equals(event.region().name())) {
            return;
        }
        Player client = event.getPlayer();
        if (EscortRules.arrived(c.state, distance(client, Bukkit.getPlayer(c.escort)), near()) && !client.isDead()) {
            end(c, End.CHEGOU);
        } else if (c.state == State.ATIVO) {
            // Chegou sozinho: o escolta precisa estar junto. Quando ele chegar perto, o tick fecha o contrato.
            msg.send(client, "escolta.espere_o_escolta", Messages.vars("escolta", c.escortName));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        Contract asClient = byClient.get(id);
        if (asClient != null) {
            end(asClient, asClient.state == State.ATIVO ? End.CLIENTE_MORREU : End.CANCELADO);
        }
        Contract asEscort = byEscort.get(id);
        if (asEscort != null) {
            end(asEscort, asEscort.state == State.ATIVO ? End.ESCOLTA_FALHOU : End.RECUSADO);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Contract asClient = byClient.get(id);
        if (asClient != null) {
            end(asClient, End.CANCELADO);
        }
        Contract asEscort = byEscort.get(id);
        if (asEscort != null) {
            end(asEscort, asEscort.state == State.ATIVO ? End.ESCOLTA_FALHOU : End.RECUSADO);
        }
    }

    /** A cada 5 segundos: proposta sem resposta, tempo esgotado, e o cliente que já está no destino esperando o escolta. */
    public void tick() {
        long now = System.currentTimeMillis();
        long limit = config.integer("escolta.tempo_maximo_minutos", 60, 1, 1440) * 60_000L;
        for (Contract c : new ArrayList<>(byClient.values())) {
            if (c.state == State.PROPOSTO) {
                if (now - c.since >= 60_000L) {
                    end(c, End.RECUSADO);
                }
                continue;
            }
            if (EscortRules.timedOut(c.since, now, limit)) {
                end(c, End.TEMPO);
                continue;
            }
            Player client = Bukkit.getPlayer(c.client);
            Region r = AincradCore.get().regions().get(c.region);
            if (client != null && r != null && r.contains(client.getLocation()) && !client.isDead()
                    && EscortRules.arrived(c.state, distance(client, Bukkit.getPlayer(c.escort)), near())) {
                end(c, End.CHEGOU);
            }
        }
    }

    /** Ao desligar: os contratos em andamento são desfeitos, e o Col volta. */
    public void shutdown() {
        for (Contract c : new ArrayList<>(byClient.values())) {
            end(c, End.CANCELADO);
        }
    }

    // ------------------------------------------------------------------ comando

    /** {@code /escolta pedir <jogador> <regiao> <valor> | aceitar | recusar | cancelar | desistir | info [jogador]}. */
    public void command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        if (!hooks.hasEconomy()) {
            msg.send(p, "escolta.sem_economia");
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pedir" -> {
                if (args.length < 4) {
                    msg.send(p, "escolta.uso");
                } else {
                    propose(p, args[1], args[2], args[3]);
                }
            }
            case "aceitar" -> accept(p);
            case "recusar" -> {
                Contract c = byEscort.get(p.getUniqueId());
                if (c != null && c.state == State.PROPOSTO) {
                    end(c, End.RECUSADO);
                } else {
                    msg.send(p, "escolta.nada_a_aceitar");
                }
            }
            case "cancelar" -> {
                Contract c = byClient.get(p.getUniqueId());
                if (c == null) {
                    msg.send(p, "escolta.sem_contrato");
                } else {
                    end(c, End.CANCELADO);
                }
            }
            case "desistir" -> {
                Contract c = byEscort.get(p.getUniqueId());
                if (c == null || c.state != State.ATIVO) {
                    msg.send(p, "escolta.sem_contrato");
                } else {
                    end(c, End.ESCOLTA_FALHOU);
                }
            }
            case "info" -> info(p, args.length > 1 ? args[1] : p.getName());
            default -> msg.send(p, "escolta.uso");
        }
    }

    private void info(Player p, String name) {
        db.query("SELECT name, done, failed FROM life_escort_stats WHERE LOWER(name) = ?",
                rs -> new Object[] {rs.getString(1), rs.getInt(2), rs.getInt(3)}, name.toLowerCase(Locale.ROOT))
                .whenComplete((rows, ex) -> AincradCore.get().runSync(() -> {
                    if (ex != null || rows.isEmpty()) {
                        msg.send(p, "escolta.sem_historico", Messages.vars("jogador", name));
                        return;
                    }
                    Object[] r = rows.get(0);
                    msg.send(p, "escolta.historico", Messages.vars("jogador", String.valueOf(r[0]), "feitas", r[1], "falhas", r[2],
                            "taxa", EscortRules.successRate((Integer) r[1], (Integer) r[2])));
                }));
    }

    public List<String> complete(String[] args) {
        List<String> opts = new ArrayList<>();
        if (args.length == 1) {
            opts.addAll(List.of("pedir", "aceitar", "recusar", "cancelar", "desistir", "info"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("pedir") || args[0].equalsIgnoreCase("info"))) {
            Bukkit.getOnlinePlayers().forEach(pl -> opts.add(pl.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("pedir")) {
            AincradCore.get().regions().all().forEach(r -> opts.add(r.name()));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return opts;
    }
}
