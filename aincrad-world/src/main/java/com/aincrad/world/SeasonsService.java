package com.aincrad.world;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.world.api.CalendarDateEvent;
import com.aincrad.world.api.SeasonChangeEvent;
import com.aincrad.world.hook.Hooks;
import com.aincrad.world.model.WorldRules;
import com.aincrad.world.model.WorldRules.Calendar;
import com.aincrad.world.model.WorldRules.DateDef;
import com.aincrad.world.model.WorldRules.Weather;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * O calendário de Aincrad (estações e datas marcadas, em calendar.yml) e o clima de cada andar
 * (weather.yml). A estação e as datas valem para a rede; o clima é sorteado em cada servidor.
 */
public final class SeasonsService implements Listener {

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private final Hooks hooks;
    private final NewsService news;
    private Calendar calendar;
    private ConfigFile weatherFile;
    private boolean weatherOn;
    private long weatherMs;
    private int fogView;
    private final Map<String, String> state = new HashMap<>();
    private final Map<Integer, Weather> weather = new HashMap<>();
    private final Map<Integer, Long> weatherUntil = new HashMap<>();
    private final Set<UUID> fogged = new HashSet<>();
    private final Set<UUID> rained = new HashSet<>();

    public SeasonsService(Plugin plugin, Database db, Messages msg, Hooks hooks, NewsService news) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.hooks = hooks;
        this.news = news;
    }

    public void reload(ConfigFile calendarFile, ConfigFile weather) {
        List<String> errors = new ArrayList<>();
        calendar = WorldRules.calendar(calendarFile.yaml(), errors);
        errors.forEach(e -> plugin.getLogger().warning("calendar.yml: " + e));
        weatherFile = weather;
        weatherOn = weather.bool("ativo", true);
        weatherMs = weather.integer("duracao_minutos", 20, 1, 1440) * 60_000L;
        fogView = weather.integer("nevoa.distancia_de_visao", 3, 2, 32);
        this.weather.clear();
        weatherUntil.clear();
    }

    public void loadBlocking() throws SQLException {
        Map<String, String> m = new HashMap<>();
        db.sync(c -> Database.query(c, "SELECT k, v FROM world_state", rs -> m.put(rs.getString(1), rs.getString(2))));
        state.clear();
        state.putAll(m);
    }

    private void remember(String key, String value) {
        state.put(key, value);
        db.update(db.upsert("world_state", List.of("k"), List.of("v")), key, value).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar o estado do calendário", ex);
            return 0;
        });
    }

    // ------------------------------------------------------------------ calendário

    public Calendar calendar() {
        return calendar;
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    public String season() {
        return calendar.season(today());
    }

    public String seasonName(String season) {
        return msg.has("estacao." + season) ? msg.raw("estacao." + season) : season;
    }

    /** A cada minuto: a estação mudou? Alguma data marcada começou hoje? */
    public void tickCalendar() {
        LocalDate today = today();
        String now = calendar.season(today);
        String before = state.get("estacao");
        if (!now.equals(before)) {
            // Gravado antes de avisar: num reinício, a mudança não é anunciada duas vezes.
            remember("estacao", now);
            weather.clear();
            weatherUntil.clear();
            Bukkit.getPluginManager().callEvent(new SeasonChangeEvent(before, now));
            if (before != null) {
                news.record("estacao", Map.of("estacao", seasonName(now)));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    msg.send(p, "calendario.estacao_mudou", Messages.vars("estacao", seasonName(now)));
                }
            }
        }
        for (DateDef d : calendar.today(today)) {
            String key = "data:" + d.id();
            if (today.toString().equals(state.get(key))) {
                continue;
            }
            remember(key, today.toString());
            Bukkit.getPluginManager().callEvent(new CalendarDateEvent(d.id(), d.name()));
            news.record("data", Map.of("nome", d.name()));
            for (Player p : Bukkit.getOnlinePlayers()) {
                msg.send(p, "calendario.data_hoje", Messages.vars("nome", d.name()));
            }
        }
    }

    /** {@code /calendario}: a estação, o dia e as próximas datas. */
    public void show(Player p) {
        LocalDate today = today();
        msg.send(p, "calendario.hoje", Messages.vars("estacao", seasonName(calendar.season(today)), "dia", calendar.seasonDay(today),
                "dias", calendar.daysPerSeason(), "ano", calendar.year(today)));
        List<Object[]> next = new ArrayList<>();
        for (DateDef d : calendar.dates().values()) {
            LocalDate when = calendar.next(d.id(), today);
            if (when != null) {
                next.add(new Object[] {when, d});
            }
        }
        next.sort((a, b) -> ((LocalDate) a[0]).compareTo((LocalDate) b[0]));
        for (Object[] n : next) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, (LocalDate) n[0]);
            msg.sendRaw(p, days == 0 ? "calendario.linha_hoje" : "calendario.linha", Messages.vars("nome", ((DateDef) n[1]).name(), "dias", days));
        }
        Weather w = weatherAt(p.getLocation());
        if (weatherOn) {
            msg.sendRaw(p, "calendario.clima", Messages.vars("clima", msg.raw("clima." + w.name().toLowerCase(java.util.Locale.ROOT))));
        }
    }

    // ------------------------------------------------------------------ clima

    /** O clima do andar onde o lugar fica (sorteado na hora, se ainda não foi). */
    public Weather weatherAt(Location l) {
        if (!weatherOn || l == null) {
            return Weather.LIMPO;
        }
        int floor = hooks.floorAt(l);
        long now = System.currentTimeMillis();
        Weather w = weather.get(floor);
        if (w == null || weatherUntil.getOrDefault(floor, 0L) <= now) {
            w = WorldRules.chances(weatherFile.yaml(), floor, season()).roll(ThreadLocalRandom.current().nextDouble());
            weather.put(floor, w);
            weatherUntil.put(floor, now + weatherMs);
        }
        return w;
    }

    /** A cada 5 segundos: cada jogador vê o clima do andar onde está. */
    public void tickWeather() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Weather w = hooks.safeZone(p.getLocation()) && !weatherFile.bool("nas_cidades", true) ? Weather.LIMPO : weatherAt(p.getLocation());
            boolean wet = w == Weather.CHUVA || w == Weather.NEVE;
            if (wet && rained.add(p.getUniqueId())) {
                // Chuva só para este jogador. Vira neve onde o bioma é frio: o Minecraft decide pelo bioma.
                p.setPlayerWeather(WeatherType.DOWNFALL);
            } else if (!wet && rained.remove(p.getUniqueId())) {
                p.resetPlayerWeather();
            }
            boolean fog = w == Weather.NEVOA;
            if (fog && fogged.add(p.getUniqueId())) {
                p.setSendViewDistance(fogView);
                msg.actionBar(p, "clima.nevoa_chegou");
            } else if (!fog && fogged.remove(p.getUniqueId())) {
                p.setSendViewDistance(p.getWorld().getSendViewDistance());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fogged.remove(event.getPlayer().getUniqueId());
        rained.remove(event.getPlayer().getUniqueId());
    }

    /** Ao desligar: ninguém fica com a névoa ou a chuva presa. */
    public void restore() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (rained.remove(p.getUniqueId())) {
                p.resetPlayerWeather();
            }
            if (fogged.remove(p.getUniqueId())) {
                p.setSendViewDistance(p.getWorld().getSendViewDistance());
            }
        }
    }
}
