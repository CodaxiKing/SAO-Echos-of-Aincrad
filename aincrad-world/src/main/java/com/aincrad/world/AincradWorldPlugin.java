package com.aincrad.world;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.world.api.AincradWorldAPI;
import com.aincrad.world.hook.Hooks;
import com.aincrad.world.hook.NewsListeners;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * AincradWorld: o mundo reage aos jogadores. O jornal semanal (etapa 44), o calendário com
 * estações e datas marcadas (57), o dia e a noite que importam (58) e o clima por andar (59).
 */
public final class AincradWorldPlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private ConfigFile calendarFile;
    private ConfigFile weatherFile;
    private Messages msg;
    private NewsService news;
    private SeasonsService seasons;
    private NightService night;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "world.yml");
        calendarFile = core.config(this, "calendar.yml");
        weatherFile = core.config(this, "weather.yml");
        try {
            core.database().migrate("world", NewsService.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradWorld não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Hooks hooks = new Hooks();
        news = new NewsService(this, core.database(), msg, hooks);
        core.ui().menuEntry("jornal", "under_icons/help", msg.get("jornal.entrada"), "mensagens", pl -> news.list(pl));
        seasons = new SeasonsService(this, core.database(), msg, hooks, news);
        night = new NightService(this, msg, hooks);
        settings();
        try {
            seasons.loadBlocking();
        } catch (SQLException e) {
            getLogger().warning("Não foi possível ler o estado do calendário: " + e.getMessage());
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(seasons, this);
        getServer().getPluginManager().registerEvents(night, this);
        // As notícias vêm dos eventos dos outros plugins: cada ouvinte só entra se o plugin dele está ligado.
        List<String> on = new ArrayList<>();
        hook(hooks, "AincradFloors", on, () -> new NewsListeners.Floors(news));
        hook(hooks, "AincradBosses", on, () -> new NewsListeners.Bosses(news));
        hook(hooks, "AincradLife", on, () -> new NewsListeners.Life(news));
        hook(hooks, "AincradReputation", on, () -> new NewsListeners.Reputation(news));
        hook(hooks, "AincradRPG", on, () -> new NewsListeners.Rpg(night));

        getServer().getScheduler().runTaskTimer(this, seasons::tickCalendar, 100L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            seasons.tickWeather();
            night.tick();
        }, 100L, 100L);
        getServer().getScheduler().runTaskTimer(this, news::purge, 600L, 6L * 60 * 60 * 20);

        command("jornal", List.of("noticias"), (sender, args) -> {
            if (!(sender instanceof Player p)) {
                return;
            }
            String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
            switch (sub) {
                case "" -> news.read(p, null);
                case "lista", "edicoes" -> news.list(p);
                case "comprar" -> news.buy(p);
                default -> news.read(p, args[0]);
            }
        });
        command("calendario", List.of("estacao"), (sender, args) -> {
            if (sender instanceof Player p) {
                seasons.show(p);
            }
        });
        AincradWorldAPI.init(seasons, night);
        getServer().getServicesManager().register(AincradWorldAPI.class, AincradWorldAPI.instance(), this, ServicePriority.Normal);
        getLogger().info("Ligado. Estação: " + seasons.season() + ". Notícias de: " + (on.isEmpty() ? "nenhum outro plugin" : String.join(", ", on)));
    }

    private void settings() {
        news.reload(config);
        night.reload(config);
        seasons.reload(calendarFile, weatherFile);
    }

    private void hook(Hooks hooks, String name, List<String> on, Supplier<Listener> listener) {
        if (!hooks.has(name)) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(listener.get(), this);
            on.add(name);
        } catch (LinkageError e) {
            getLogger().warning(name + " incompatível: as notícias dele não vão entrar no jornal (" + e + ").");
        }
    }

    private void command(String name, List<String> aliases, BiConsumer<CommandSender, String[]> run) {
        Command c = new Command(name, "", "/" + name, aliases) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                run.accept(sender, args);
                return true;
            }
        };
        getServer().getCommandMap().register("aincradworld", c);
    }

    @Override
    public void onDisable() {
        AincradWorldAPI.init(null, null);
        getServer().getServicesManager().unregisterAll(this);
        if (seasons != null) {
            seasons.restore();
        }
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        calendarFile.reload();
        weatherFile.reload();
        seasons.restore();
        settings();
        event.reloaded(getName());
    }
}
