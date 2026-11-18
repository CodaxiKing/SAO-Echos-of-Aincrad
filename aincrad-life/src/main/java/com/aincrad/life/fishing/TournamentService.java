package com.aincrad.life.fishing;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.life.api.FishingTournamentEndEvent;
import com.aincrad.life.hook.Hooks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * O torneio semanal de pesca: inscrição paga em Col, por andar; vence o maior peixe da semana
 * naquele andar; o prêmio é o total das inscrições menos a taxa. Fecha sozinho na virada da
 * semana (segunda-feira), uma vez só na rede inteira. Precisa do AincradEconomy.
 */
public final class TournamentService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_fish_tournament (week VARCHAR(10) NOT NULL, floor INT NOT NULL, uuid VARCHAR(36) NOT NULL, "
                    + "name VARCHAR(32) NOT NULL, paid BIGINT NOT NULL, best_mm INT NOT NULL, fish VARCHAR(64) NOT NULL, at BIGINT NOT NULL, "
                    + "PRIMARY KEY (week, floor, uuid));"
                    + "CREATE TABLE IF NOT EXISTS life_fish_tournament_closed (week VARCHAR(10) NOT NULL, floor INT NOT NULL, "
                    + "PRIMARY KEY (week, floor))");

    /** Uma inscrição. {@code bestMm}: o maior peixe da semana, em milímetros (0 = nenhum). */
    public record Entry(UUID player, String name, long paid, int bestMm, String fish, long at) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final Database db;
    private final FishingService fishing;

    TournamentService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, Database db, FishingService fishing) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.db = db;
        this.fishing = fishing;
    }

    // ------------------------------------------------------------------ regras

    /** A semana de uma data, como "2026-W47" (semana ISO: começa na segunda-feira). */
    public static String week(LocalDate date) {
        return String.format("%04d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** O prêmio: o total das inscrições menos a taxa. */
    public static long prize(long pot, double taxPercent) {
        return (long) Math.floor(pot * (1 - Math.max(0, Math.min(100, taxPercent)) / 100.0));
    }

    /** O vencedor: o maior peixe; no empate, quem pescou primeiro. Null se ninguém pescou nada. */
    public static Entry winner(List<Entry> entries) {
        Entry best = null;
        for (Entry e : entries) {
            if (e.bestMm() <= 0) {
                continue;
            }
            if (best == null || e.bestMm() > best.bestMm() || (e.bestMm() == best.bestMm() && e.at() < best.at())) {
                best = e;
            }
        }
        return best;
    }

    public boolean active() {
        return config.bool("pesca.torneio.ativo", true) && hooks.hasEconomy();
    }

    private static String now() {
        return week(LocalDate.now());
    }

    private long fee() {
        return config.integer("pesca.torneio.inscricao_col", 200, 0, Integer.MAX_VALUE);
    }

    private static String cm(int mm) {
        return FishingService.cm(mm);
    }

    private static Entry entry(java.sql.ResultSet rs) throws SQLException {
        return new Entry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getString(5), rs.getLong(6));
    }

    private List<Entry> entries(java.sql.Connection c, String week, int floor) throws SQLException {
        return Database.query(c, "SELECT uuid, name, paid, best_mm, fish, at FROM life_fish_tournament WHERE week = ? AND floor = ? "
                + "ORDER BY best_mm DESC, at ASC", TournamentService::entry, week, floor);
    }

    private void fail(CommandSender to, Throwable err) {
        plugin.getLogger().log(Level.WARNING, "Torneio de pesca: erro no banco", err);
        msg.send(to, "geral.erro");
    }

    // ------------------------------------------------------------------ jogador

    /** Inscreve o jogador no torneio da semana, no andar dado. */
    public void enter(Player p, int floor) {
        if (!active()) {
            msg.send(p, "pesca.torneio.desligado");
            return;
        }
        if (!fishing.hasFloor(floor)) {
            msg.send(p, "pesca.torneio.sem_andar", Messages.vars("andares", fishing.floors()));
            return;
        }
        String week = now();
        String id = p.getUniqueId().toString();
        db.async(c -> {
            if (!Database.query(c, "SELECT 1 FROM life_fish_tournament_closed WHERE week = ? AND floor = ?", rs -> 1, week, floor).isEmpty()) {
                return "pesca.torneio.encerrado";
            }
            return Database.query(c, "SELECT 1 FROM life_fish_tournament WHERE week = ? AND floor = ? AND uuid = ?", rs -> 1, week, floor, id)
                    .isEmpty() ? null : "pesca.torneio.ja_inscrito";
        }).whenComplete((refusal, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                fail(p, err);
                return;
            }
            if (refusal != null) {
                msg.send(p, refusal, Messages.vars("andar", floor));
                return;
            }
            long fee = fee();
            if (!p.isOnline() || !hooks.charge(p, fee, Material.GOLD_INGOT, 100, "life:torneio_pesca")) {
                msg.send(p, "pesca.torneio.sem_col", Messages.vars("preco", hooks.price(fee, Material.GOLD_INGOT, 100)));
                return;
            }
            db.update("INSERT INTO life_fish_tournament (week, floor, uuid, name, paid, best_mm, fish, at) VALUES (?, ?, ?, ?, ?, 0, '', 0)",
                    week, floor, id, p.getName(), fee).whenComplete((n, e) -> AincradCore.get().runSync(() -> {
                if (e != null) {
                    // Dois cliques ao mesmo tempo, ou o banco caiu: devolve a inscrição.
                    hooks.pay(p.getUniqueId(), fee, "life:torneio_pesca_devolucao");
                    fail(p, e);
                    return;
                }
                msg.send(p, "pesca.torneio.inscrito", Messages.vars("andar", floor, "preco", hooks.price(fee, Material.GOLD_INGOT, 100)));
            }));
        }));
    }

    /** Um peixe pescado: se o jogador está inscrito no andar e este é o maior dele na semana, grava. */
    public void record(Player p, int floor, FishingRegistry.Fish fish, int mm) {
        if (!active()) {
            return;
        }
        db.update("UPDATE life_fish_tournament SET best_mm = ?, fish = ?, at = ? WHERE week = ? AND floor = ? AND uuid = ? AND best_mm < ?",
                mm, fish.id(), System.currentTimeMillis(), now(), floor, p.getUniqueId().toString(), mm).whenComplete((n, err) -> {
            if (err == null && n != null && n > 0) {
                AincradCore.get().runSync(() -> msg.send(p, "pesca.torneio.novo_melhor",
                        Messages.vars("peixe", fish.name(), "tamanho", cm(mm), "andar", floor)));
            }
        });
    }

    /** Mostra o ranking da semana no andar. */
    public void ranking(CommandSender to, int floor) {
        if (!active()) {
            msg.send(to, "pesca.torneio.desligado");
            return;
        }
        String week = now();
        db.async(c -> entries(c, week, floor)).whenComplete((list, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                fail(to, err);
                return;
            }
            long pot = list.stream().mapToLong(Entry::paid).sum();
            msg.send(to, "pesca.torneio.titulo", Messages.vars("andar", floor, "semana", week, "inscritos", list.size(),
                    "premio", hooks.price(prize(pot, tax()), Material.GOLD_INGOT, 100)));
            int pos = 0;
            for (Entry e : list) {
                if (e.bestMm() <= 0 || pos >= 10) {
                    break;
                }
                FishingRegistry.Fish f = fishing.registry().fish(e.fish());
                msg.sendRaw(to, "pesca.torneio.linha", Messages.vars("pos", ++pos, "jogador", e.name(), "tamanho", cm(e.bestMm()),
                        "peixe", f == null ? e.fish() : f.name()));
            }
            if (pos == 0) {
                msg.sendRaw(to, "pesca.torneio.vazio");
            }
        }));
    }

    private double tax() {
        return config.decimal("pesca.torneio.taxa_pct", 10, 0, 100);
    }

    // ------------------------------------------------------------------ fechamento

    /** Roda a cada minuto: fecha os torneios das semanas que já passaram. */
    public void minute() {
        if (!active()) {
            return;
        }
        String week = now();
        db.query("SELECT DISTINCT t.week, t.floor FROM life_fish_tournament t WHERE t.week < ? AND NOT EXISTS "
                        + "(SELECT 1 FROM life_fish_tournament_closed c WHERE c.week = t.week AND c.floor = t.floor)",
                rs -> new Object[]{rs.getString(1), rs.getInt(2)}, week).whenComplete((open, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Torneio de pesca: não foi possível conferir as semanas abertas", err);
                return;
            }
            for (Object[] o : open) {
                close((String) o[0], (Integer) o[1]);
            }
        });
    }

    /** Admin: fecha agora o torneio desta semana em todos os andares com inscritos. */
    public void closeNow() {
        String week = now();
        db.query("SELECT DISTINCT floor FROM life_fish_tournament WHERE week = ?", rs -> rs.getInt(1), week)
                .whenComplete((floors, err) -> {
                    if (err == null) {
                        floors.forEach(f -> close(week, f));
                    }
                });
    }

    /**
     * Fecha um torneio e paga. O registro em life_fish_tournament_closed é a trava: só o servidor
     * que conseguir gravá-lo paga, então o prêmio sai uma vez só na rede.
     */
    private void close(String week, int floor) {
        db.async(c -> {
            try {
                Database.update(c, "INSERT INTO life_fish_tournament_closed (week, floor) VALUES (?, ?)", week, floor);
            } catch (SQLException alreadyClosed) {
                return null;
            }
            return entries(c, week, floor);
        }).whenComplete((list, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Torneio de pesca: erro ao fechar " + week + " andar " + floor, err);
                return;
            }
            if (list == null || list.isEmpty()) {
                return;
            }
            Entry w = winner(list);
            if (w == null) {
                // Ninguém pescou nada: cada um recebe a inscrição de volta.
                for (Entry e : list) {
                    hooks.pay(e.player(), e.paid(), "life:torneio_pesca_devolucao");
                    Player on = Bukkit.getPlayer(e.player());
                    if (on != null) {
                        msg.send(on, "pesca.torneio.devolvido", Messages.vars("andar", floor));
                    }
                }
                return;
            }
            long prize = prize(list.stream().mapToLong(Entry::paid).sum(), tax());
            hooks.pay(w.player(), prize, "life:torneio_pesca_premio");
            FishingRegistry.Fish f = fishing.registry().fish(w.fish());
            Bukkit.broadcast(msg.prefix().append(msg.get("pesca.torneio.vencedor", Messages.vars("jogador", w.name(), "andar", floor,
                    "tamanho", cm(w.bestMm()), "peixe", f == null ? w.fish() : f.name(), "premio", hooks.price(prize, Material.GOLD_INGOT, 100)))));
            Bukkit.getPluginManager().callEvent(new FishingTournamentEndEvent(week, floor, w.player(), w.name(), w.bestMm() / 10.0, prize));
        }));
    }
}
