package com.aincrad.economy.house;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.economy.EconomyService;
import com.aincrad.economy.HoldService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Casas com vista: casas raras que não têm preço fixo. Vão a leilão com lance mínimo e prazo, o
 * maior lance leva, e quando o dono vende ou é despejado a casa volta a leilão. O lance fica em
 * custódia; cada mudança é uma linha do banco ("UPDATE ... WHERE ..."), então dois servidores
 * nunca aceitam o mesmo lance nem fecham o mesmo leilão.
 */
public final class HouseAuctionService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS eco_house_auctions (house VARCHAR(64) NOT NULL PRIMARY KEY, min_bid BIGINT NOT NULL, "
                    + "hours INT NOT NULL, ends_at BIGINT NOT NULL, bidder VARCHAR(36), bidder_name VARCHAR(16), bid BIGINT NOT NULL, "
                    + "hold_id VARCHAR(36), state VARCHAR(8) NOT NULL)");

    /** O leilão de uma casa. {@code open} false = a casa tem dono (o leilão está fechado até ele sair). */
    public record Auction(String house, long minBid, int hours, long endsAt, String bidder, String bidderName, long bid, String hold, boolean open) {
    }

    /** Por que o lance não vale (null = vale). */
    public enum Block { SEM_LEILAO, FECHADO, BAIXO, JA_E_SEU, LIMITE_DE_CASAS }

    /** O menor lance aceito agora: o mínimo, ou o lance atual mais o incremento. */
    public static long minNext(long minBid, long current, double increment) {
        return current <= 0 ? minBid : Math.max(current + 1, (long) Math.ceil(current * (1 + Math.max(0, increment))));
    }

    public static Block blocked(Auction a, long now, long amount, double increment, boolean ownBid, boolean atHouseLimit) {
        if (a == null) {
            return Block.SEM_LEILAO;
        }
        if (!a.open() || a.endsAt() <= now) {
            return Block.FECHADO;
        }
        if (ownBid) {
            return Block.JA_E_SEU;
        }
        if (atHouseLimit) {
            return Block.LIMITE_DE_CASAS;
        }
        return amount < minNext(a.minBid(), a.bid(), increment) ? Block.BAIXO : null;
    }

    /** Um lance nos últimos minutos estica o prazo, para ninguém ganhar no último segundo. */
    public static long extended(long endsAt, long now, long windowMs) {
        return endsAt - now < windowMs ? now + windowMs : endsAt;
    }

    private final Plugin plugin;
    private final EconomyService eco;
    private final HoldService holds;
    private final HouseService houses;
    private final Messages msg;
    private final Database db;
    private volatile Map<String, Auction> auctions = Map.of();

    public HouseAuctionService(Plugin plugin, EconomyService eco, HoldService holds, HouseService houses, Database db) {
        this.plugin = plugin;
        this.eco = eco;
        this.holds = holds;
        this.houses = houses;
        this.msg = eco.messages();
        this.db = db;
    }

    private static Map<String, Auction> read(java.sql.Connection c) throws SQLException {
        Map<String, Auction> m = new ConcurrentHashMap<>();
        Database.query(c, "SELECT house, min_bid, hours, ends_at, bidder, bidder_name, bid, hold_id, state FROM eco_house_auctions",
                rs -> m.put(rs.getString(1), new Auction(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getLong(4), rs.getString(5),
                        rs.getString(6), rs.getLong(7), rs.getString(8), "ABERTO".equals(rs.getString(9)))));
        return m;
    }

    public void loadBlocking() throws SQLException {
        auctions = db.sync(HouseAuctionService::read);
    }

    public void refresh() {
        db.async(HouseAuctionService::read).thenAccept(m -> auctions = m).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler os leilões de casas", ex);
            return null;
        });
    }

    /** A casa é de leilão? Então não tem preço fixo: {@code /casa comprar} não vale para ela. */
    public boolean auctioned(String house) {
        return auctions.containsKey(house);
    }

    public Auction of(String house) {
        return auctions.get(house);
    }

    private double increment() {
        return eco.config().decimal("casas.leilao.incremento", 0.05, 0, 10);
    }

    // ------------------------------------------------------------------ admin

    /** Põe uma casa sem dono em leilão (e a marca como casa de leilão dali em diante). */
    public void start(CommandSender sender, House h, long minBid, int hours) {
        if (!h.forSale()) {
            msg.send(sender, "leilao_casa.tem_dono", Messages.vars("casa", h.name(), "dono", String.valueOf(h.ownerName())));
            return;
        }
        long ends = System.currentTimeMillis() + hours * 3_600_000L;
        Auction before = auctions.get(h.name());
        if (before != null && before.hold() != null) {
            holds.refund(before.hold(), "leilão de casa refeito");
        }
        db.update(db.upsert("eco_house_auctions", List.of("house"), List.of("min_bid", "hours", "ends_at", "bidder", "bidder_name", "bid", "hold_id", "state")),
                h.name(), minBid, hours, ends, null, null, 0L, null, "ABERTO").whenComplete((n, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível abrir o leilão da casa " + h.name(), ex);
                return;
            }
            refresh();
            msg.send(sender, "leilao_casa.aberto", Messages.vars("casa", h.name(), "horas", hours), eco.col(minBid));
            announce("leilao_casa.anuncio_aberto", h.name(), minBid, null);
        }));
    }

    /** A casa deixa de ser de leilão: volta a ter o preço fixo dela. */
    public void remove(CommandSender sender, String house) {
        Auction a = auctions.get(house);
        if (a == null) {
            msg.send(sender, "leilao_casa.sem_leilao");
            return;
        }
        if (a.hold() != null && a.open()) {
            holds.refund(a.hold(), "leilão de casa cancelado");
        }
        db.update("DELETE FROM eco_house_auctions WHERE house = ?", house).thenRun(this::refresh);
        msg.send(sender, "leilao_casa.removido", Messages.vars("casa", house));
    }

    private void announce(String key, String house, long amount, String player) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, key, Messages.vars("casa", house, "jogador", player == null ? "" : player), eco.col(amount));
        }
    }

    // ------------------------------------------------------------------ lance

    /** {@code /casa lance <valor>}, dentro da casa. */
    public void bid(Player p, House h, long amount) {
        long now = System.currentTimeMillis();
        Auction a = auctions.get(h.name());
        int max = eco.config().integer("casas.max_por_jogador", 1, 0, 100);
        Block b = blocked(a, now, amount, increment(), a != null && p.getUniqueId().toString().equals(a.bidder()),
                houses.ownedBy(p.getUniqueId()).size() >= max);
        if (b != null) {
            msg.send(p, "leilao_casa.bloqueio." + b.name().toLowerCase(java.util.Locale.ROOT),
                    Messages.vars("casa", h.name()), eco.col(a == null ? 0 : minNext(a.minBid(), a.bid(), increment())));
            return;
        }
        String hold = holds.hold(p.getUniqueId(), amount, "lance: " + h.name());
        if (hold == null) {
            msg.send(p, "casa.sem_saldo", eco.col(amount));
            return;
        }
        long ends = extended(a.endsAt(), now, eco.config().integer("casas.leilao.prorrogar_minutos", 5, 0, 600) * 60_000L);
        String oldHold = a.hold();
        String oldBidder = a.bidder();
        UUID id = p.getUniqueId();
        String name = p.getName();
        // Só vale se o lance ainda é maior que o que está no banco (outro servidor pode ter dado um lance agora).
        db.update("UPDATE eco_house_auctions SET bidder = ?, bidder_name = ?, bid = ?, hold_id = ?, ends_at = ? "
                        + "WHERE house = ? AND state = 'ABERTO' AND bid < ? AND ends_at > ?",
                id.toString(), name, amount, hold, ends, h.name(), amount, now).whenComplete((n, ex) -> AincradCore.get().runSync(() -> {
            Player online = Bukkit.getPlayer(id);
            if (ex != null || n == null || n == 0) {
                holds.refund(hold, "lance não aceito");
                if (online != null) {
                    msg.send(online, "leilao_casa.perdeu_a_vez");
                }
                refresh();
                return;
            }
            if (oldHold != null) {
                holds.refund(oldHold, "lance superado");
                Player before = oldBidder == null ? null : Bukkit.getPlayer(UUID.fromString(oldBidder));
                if (before != null) {
                    msg.send(before, "leilao_casa.superado", Messages.vars("casa", h.name(), "jogador", name), eco.col(amount));
                }
            }
            refresh();
            if (online != null) {
                msg.send(online, "leilao_casa.lance_dado", Messages.vars("casa", h.name()), eco.col(amount));
            }
            announce("leilao_casa.anuncio_lance", h.name(), amount, name);
        }));
    }

    /** {@code /casa leilao}: como está o leilão da casa onde o jogador está. */
    public void info(Player p, House h) {
        Auction a = auctions.get(h.name());
        if (a == null) {
            msg.send(p, "leilao_casa.sem_leilao");
            return;
        }
        if (!a.open()) {
            msg.send(p, "leilao_casa.fechado", Messages.vars("casa", h.name(), "dono", String.valueOf(h.ownerName())));
            return;
        }
        long left = Math.max(0, a.endsAt() - System.currentTimeMillis());
        msg.send(p, a.bid() <= 0 ? "leilao_casa.info_sem_lance" : "leilao_casa.info", Messages.vars("casa", h.name(),
                "jogador", String.valueOf(a.bidderName()), "horas", left / 3_600_000L, "minutos", (left / 60_000L) % 60,
                "proximo", eco.format(minNext(a.minBid(), a.bid(), increment()))), eco.col(a.bid() <= 0 ? a.minBid() : a.bid()));
    }

    // ------------------------------------------------------------------ fechar e reabrir

    /** A cada 30 segundos: os leilões vencidos são fechados (o maior lance leva) ou, sem lance, recomeçam. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Auction a : auctions.values()) {
            if (!a.open() || a.endsAt() > now) {
                continue;
            }
            if (a.bidder() == null) {
                db.update("UPDATE eco_house_auctions SET ends_at = ? WHERE house = ? AND state = 'ABERTO' AND bidder IS NULL AND ends_at <= ?",
                        now + a.hours() * 3_600_000L, a.house(), now).thenRun(this::refresh);
                continue;
            }
            db.async(c -> {
                // Só o servidor que conseguir marcar o leilão como fechado entrega a casa.
                if (Database.update(c, "UPDATE eco_house_auctions SET state = 'FECHADO' WHERE house = ? AND state = 'ABERTO' AND ends_at <= ?",
                        a.house(), now) == 0) {
                    return false;
                }
                return Database.update(c, "UPDATE eco_houses SET owner = ?, owner_name = ?, members = ?, bought = ? WHERE name = ? AND owner IS NULL",
                        a.bidder(), a.bidderName(), "", now, a.house()) > 0;
            }).whenComplete((won, ex) -> AincradCore.get().runSync(() -> {
                if (ex != null) {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível fechar o leilão da casa " + a.house(), ex);
                    return;
                }
                refresh();
                if (!Boolean.TRUE.equals(won)) {
                    return;
                }
                // O lance sai de circulação: a casa não foi vendida por ninguém.
                holds.burn(a.hold());
                houses.reloadAsync();
                AincradCore.get().publish(HouseService.BUS_CHANNEL, a.house());
                announce("leilao_casa.anuncio_fim", a.house(), a.bid(), a.bidderName());
            }));
        }
    }

    /** O dono vendeu a casa, ou foi despejado: se ela é de leilão, volta a leilão. */
    public void reopen(String house) {
        Auction a = auctions.get(house);
        if (a == null) {
            return;
        }
        long ends = System.currentTimeMillis() + a.hours() * 3_600_000L;
        db.update("UPDATE eco_house_auctions SET state = 'ABERTO', bidder = NULL, bidder_name = NULL, bid = 0, hold_id = NULL, ends_at = ? WHERE house = ?",
                ends, house).whenComplete((n, ex) -> AincradCore.get().runSync(() -> {
            refresh();
            if (ex == null) {
                announce("leilao_casa.anuncio_aberto", house, a.minBid(), null);
            }
        }));
    }
}
