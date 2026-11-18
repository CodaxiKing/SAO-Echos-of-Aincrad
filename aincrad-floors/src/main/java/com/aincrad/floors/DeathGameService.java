package com.aincrad.floors;

import com.aincrad.core.AincradCore;
import com.aincrad.core.bus.MessageBus;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.floors.model.DeathGameRules;
import com.aincrad.floors.model.DeathGameRules.Line;
import com.aincrad.floors.model.DeathGameRules.Outcome;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * O jogo da morte: o dia do anúncio (todos na praça, o discurso, o logout some) e o fim de jogo
 * (o chefe do andar final cai: anúncio, créditos e o que fazer com o mundo). O estado fica no
 * banco do core e vale para a rede.
 */
public final class DeathGameService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS floor_flags (k VARCHAR(32) NOT NULL PRIMARY KEY, v TEXT NOT NULL)");

    public static final String BUS = "floors:deathgame";
    private static final String F_ANNOUNCED = "anuncio";
    private static final String F_ENDED = "fim";
    private static final String F_CREDITS = "fim_participantes";
    private static final String SEEN = "anuncio_visto";

    private final Plugin plugin;
    private final Messages msg;
    private final FloorService floors;
    private final ConfigFile file;
    private final Database db;
    private volatile Map<String, String> flags = Map.of();
    private boolean running;

    public DeathGameService(Plugin plugin, Messages msg, FloorService floors, ConfigFile file) {
        this.plugin = plugin;
        this.msg = msg;
        this.floors = floors;
        this.file = file;
        this.db = AincradCore.get().database();
    }

    // ------------------------------------------------------------------ estado

    public void loadBlocking() throws SQLException {
        flags = read();
    }

    private Map<String, String> read() throws SQLException {
        Map<String, String> m = new HashMap<>();
        db.sync(c -> Database.query(c, "SELECT k, v FROM floor_flags", rs -> m.put(rs.getString(1), rs.getString(2))));
        return Map.copyOf(m);
    }

    /** Relê o estado (outro servidor pode ter mudado) e ajusta o logout do AincradUI. */
    public void refresh() {
        db.async(c -> {
            Map<String, String> m = new HashMap<>();
            Database.query(c, "SELECT k, v FROM floor_flags", rs -> m.put(rs.getString(1), rs.getString(2)));
            return Map.copyOf(m);
        }).thenAccept(m -> AincradCore.get().runSync(() -> {
            flags = m;
            applyUi();
        })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler o estado do jogo da morte", ex);
            return null;
        });
    }

    private void flag(String key, String value) {
        Map<String, String> m = new HashMap<>(flags);
        if (value == null) {
            m.remove(key);
            db.update("DELETE FROM floor_flags WHERE k = ?", key);
        } else {
            m.put(key, value);
            db.update(db.upsert("floor_flags", List.of("k"), List.of("v")), key, value);
        }
        flags = Map.copyOf(m);
    }

    public boolean announced() {
        return flags.containsKey(F_ANNOUNCED);
    }

    public boolean ended() {
        return flags.containsKey(F_ENDED);
    }

    /** O logout some entre o anúncio e o fim de jogo. */
    public boolean deathGame() {
        return announced() && !ended();
    }

    private Outcome outcome() {
        return Outcome.parse(file.string("fim.ao_vencer", "continuar"));
    }

    // ------------------------------------------------------------------ AincradUI e AincradEffects (por reflexão)

    private static Object service(String className) {
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals(className)) {
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                return r == null ? null : r.getProvider();
            }
        }
        return null;
    }

    /** Diz ao AincradUI se o logout aparece no menu. */
    public void applyUi() {
        Object ui = service("com.aincrad.ui.api.AincradUIAPI");
        if (ui == null) {
            return;
        }
        try {
            Class.forName("com.aincrad.ui.api.AincradUIAPI", false, ui.getClass().getClassLoader())
                    .getMethod("setDeathGame", boolean.class).invoke(ui, deathGame());
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("AincradUI incompatível (logout do jogo da morte): " + e);
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("AincradUI")) {
            Bukkit.getScheduler().runTask(plugin, this::applyUi);
        }
    }

    /** Toca um efeito do AincradEffects no jogador, se o efeito existe. */
    private void effect(String id, Player p) {
        Object fx = service("com.aincrad.effects.api.AincradEffectsAPI");
        if (fx == null) {
            return;
        }
        try {
            Class<?> api = Class.forName("com.aincrad.effects.api.AincradEffectsAPI", false, fx.getClass().getClassLoader());
            if (Boolean.TRUE.equals(api.getMethod("exists", String.class).invoke(fx, id))) {
                api.getMethod("play", String.class, Player.class, Map.class).invoke(fx, id, p, Map.of("jogador", p.getName()));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("AincradEffects incompatível (efeito " + id + "): " + e);
        }
    }

    // ------------------------------------------------------------------ o dia do anúncio

    public enum Start { OK, JA_FEITO, EM_ANDAMENTO }

    /** Dispara o anúncio na rede inteira. {@code again} repete mesmo já tendo acontecido. */
    public Start start(boolean again) {
        if (running) {
            return Start.EM_ANDAMENTO;
        }
        if (announced() && !again) {
            return Start.JA_FEITO;
        }
        flag(F_ANNOUNCED, String.valueOf(System.currentTimeMillis()));
        flag(F_ENDED, null);
        flag(F_CREDITS, null);
        AincradCore.get().publish(BUS, "anuncio");
        return Start.OK;
    }

    /** Desfaz o anúncio: o logout volta e ele pode ser feito de novo. */
    public void undo() {
        flag(F_ANNOUNCED, null);
        AincradCore.get().publish(BUS, "estado");
    }

    public void onBus(MessageBus.Message m) {
        switch (m.payload()) {
            case "anuncio" -> {
                refreshThen(this::runAnnouncement);
            }
            case "fim" -> refreshThen(this::runVictory);
            default -> refresh();
        }
    }

    private void refreshThen(Runnable then) {
        db.async(c -> {
            Map<String, String> m = new HashMap<>();
            Database.query(c, "SELECT k, v FROM floor_flags", rs -> m.put(rs.getString(1), rs.getString(2)));
            return Map.copyOf(m);
        }).whenComplete((m, ex) -> AincradCore.get().runSync(() -> {
            if (m != null) {
                flags = m;
            }
            then.run();
        }));
    }

    private void show(Player p, Line l) {
        if (l.title() != null || l.subtitle() != null) {
            p.showTitle(Title.title(l.title() == null ? Component.empty() : Messages.parse(l.title()),
                    l.subtitle() == null ? Component.empty() : Messages.parse(l.subtitle()),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(800))));
        }
        if (l.chat() != null) {
            p.sendMessage(Messages.parse(l.chat()));
        }
        if (l.sound() != null) {
            p.playSound(p.getLocation(), l.sound(), 1f, 1f);
        }
    }

    private void play(Collection<? extends Player> players, List<Line> lines, Runnable end, double tail) {
        List<UUID> ids = new ArrayList<>();
        players.forEach(p -> ids.add(p.getUniqueId()));
        for (Line l : lines) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID id : ids) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        show(p, l);
                    }
                }
            }, l.atTicks());
        }
        Bukkit.getScheduler().runTaskLater(plugin, end, DeathGameRules.durationTicks(lines, tail) + 1);
    }

    private void runAnnouncement() {
        if (running) {
            return;
        }
        running = true;
        List<Line> lines = DeathGameRules.lines(file.yaml().getMapList("anuncio.falas"));
        Location plaza = file.bool("anuncio.levar_para_a_praca", true) ? floors.fallbackSpawn() : null;
        long sky = file.integer("anuncio.ceu", 13000, -1, 24000);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player p : players) {
            if (plaza != null) {
                p.teleportAsync(plaza);
            }
            if (sky >= 0) {
                // O céu do fim da tarde, só para o jogador: o mais perto do céu vermelho do anime.
                p.setPlayerTime(sky, false);
            }
            effect("aincrad_anuncio", p);
        }
        play(players, lines, () -> {
            running = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.resetPlayerTime();
                AincradCore.get().profile(p).set(plugin, SEEN, true);
                msg.send(p, "jogo.logout_sumiu");
            }
            applyUi();
        }, file.decimal("anuncio.pausa_final", 4, 0, 600));
    }

    /** Quem entra depois do anúncio vê a versão curta, uma vez. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (closed() && !p.hasPermission("aincrad.admin")) {
                p.kick(msg.get("jogo.fechado"));
                return;
            }
            if (!deathGame() || running || AincradCore.get().profile(p).getBool(plugin, SEEN, false)) {
                return;
            }
            AincradCore.get().profile(p).set(plugin, SEEN, true);
            effect("aincrad_anuncio_curto", p);
            play(List.of(p), DeathGameRules.lines(file.yaml().getMapList("anuncio.falas_curtas")), () -> { }, 0);
        }, 60L);
    }

    // ------------------------------------------------------------------ fim de jogo

    /**
     * O chefe de um andar caiu (chamado quando o AincradBosses pede o andar seguinte).
     *
     * @return true se era o andar final: o jogo acabou e o andar seguinte não é liberado
     */
    public boolean cleared(int floor, Collection<UUID> participants) {
        if (ended() || !DeathGameRules.isFinal(floor, file.integer("fim.andar_final", 0, 0, 1000), floors.registry().lastFloor())) {
            return false;
        }
        List<String> names = new ArrayList<>();
        for (UUID id : participants) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            if (n != null) {
                names.add(n);
            }
        }
        declare(names);
        return true;
    }

    /** Declara a vitória (o chefe final caiu, ou o admin mandou). */
    public void declare(List<String> lastFight) {
        flag(F_ENDED, String.valueOf(System.currentTimeMillis()));
        flag(F_CREDITS, String.join(",", lastFight));
        AincradCore.get().publish(BUS, "fim");
    }

    /** Volta atrás na vitória (comando de admin). */
    public void undoVictory() {
        flag(F_ENDED, null);
        flag(F_CREDITS, null);
        AincradCore.get().publish(BUS, "estado");
    }

    private boolean closed() {
        if (!ended() || outcome() != Outcome.FECHAR) {
            return false;
        }
        try {
            long at = Long.parseLong(flags.getOrDefault(F_ENDED, "0"));
            return System.currentTimeMillis() >= at + file.integer("fim.fechar_depois_minutos", 30, 0, 100_000) * 60_000L;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void runVictory() {
        applyUi();
        String last = flags.getOrDefault(F_CREDITS, "");
        List<String> fighters = last.isBlank() ? List.of() : List.of(last.split(","));
        // Os sobreviventes vêm do Monumento da Vida (AincradReputation), quando ele existe.
        db.async(c -> {
            try {
                return Database.query(c, "SELECT name FROM rep_monument WHERE died_at = 0 ORDER BY joined_at", rs -> rs.getString(1));
            } catch (SQLException noTable) {
                return List.<String>of();
            }
        }).whenComplete((survivors, ex) -> AincradCore.get().runSync(() -> credits(fighters, survivors == null ? List.of() : survivors)));
    }

    private void credits(List<String> fighters, List<String> survivors) {
        int max = file.integer("fim.creditos_maximo", 60, 1, 500);
        String more = msg.raw("jogo.e_mais");
        List<Line> lines = DeathGameRules.lines(file.yaml().getMapList("fim.falas"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            effect("aincrad_vitoria", p);
        }
        play(Bukkit.getOnlinePlayers(), lines, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!fighters.isEmpty()) {
                    msg.sendRaw(p, "jogo.creditos_luta", Messages.vars("nomes", DeathGameRules.credits(fighters, max, more)));
                }
                if (!survivors.isEmpty()) {
                    msg.sendRaw(p, "jogo.creditos_vivos", Messages.vars("qtd", survivors.size(),
                            "nomes", DeathGameRules.credits(survivors, max, more)));
                }
                msg.sendRaw(p, "jogo.depois." + outcome().name().toLowerCase(java.util.Locale.ROOT),
                        Messages.vars("minutos", file.integer("fim.fechar_depois_minutos", 30, 0, 100_000)));
            }
            applyOutcome();
        }, file.decimal("fim.pausa_final", 3, 0, 600));
    }

    private void applyOutcome() {
        if (outcome() != Outcome.REINICIAR) {
            return;
        }
        // Nova temporada: os andares acima do primeiro voltam a ser bloqueados e o anúncio pode
        // ser feito de novo. Level, itens e Col ficam: cada plugin tem os seus comandos de admin.
        for (int n = floors.registry().lastFloor(); n >= 2; n--) {
            if (floors.isUnlocked(n)) {
                floors.lock(n);
            }
        }
        flag(F_ANNOUNCED, null);
        flag(F_ENDED, null);
        flag(F_CREDITS, null);
        Location start = floors.fallbackSpawn();
        for (Player p : Bukkit.getOnlinePlayers()) {
            AincradCore.get().profile(p).remove(plugin, SEEN);
            if (start != null) {
                p.teleportAsync(start);
            }
        }
        AincradCore.get().publish(BUS, "estado");
    }

    /** A cada minuto: com a entrada fechada, quem não é admin sai. */
    public void tick() {
        if (closed()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("aincrad.admin")) {
                    p.kick(msg.get("jogo.fechado"));
                }
            }
        }
    }
}
