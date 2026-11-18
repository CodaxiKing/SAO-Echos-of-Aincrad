package com.aincrad.admin;

import com.aincrad.admin.model.LogRules.Entry;
import com.aincrad.admin.model.LogRules.Filter;
import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * AincradAdmin: ferramentas do dono. Não muda o jogo — registra as ações de administração dos
 * outros plugins Aincrad (etapa 28). Discord, ranking em site e backup vêm nas etapas 29 e 30.
 */
public final class AincradAdminPlugin extends JavaPlugin implements Listener {

    private static final String DEFAULT_DATE = "dd/MM/yyyy HH:mm";
    /** Os nomes curtos que os plugins usam no evento, para o Tab. */
    private static final List<String> PLUGINS = List.of("floors", "rpg", "economy", "bosses", "items", "life", "reputation");

    private ConfigFile config;
    private Messages msg;
    private LogService log;
    private int perPage;
    private int keepDays;
    private DateTimeFormatter date;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        try {
            core.database().migrate("admin", LogService.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradAdmin não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        log = new LogService(this, core.database());
        settings();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(log, this);
        core.commands().register(this, new LogCommand());

        // Limpa o que passou do prazo ao ligar e a cada seis horas.
        long sixHours = 6L * 60 * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, () -> log.purge(keepDays), 200L, sixHours);
    }

    private void settings() {
        perPage = config.integer("registro.por_pagina", 10, 1, 50);
        keepDays = config.integer("registro.guardar_dias", 90, 0, 36_500);
        log.console(config.bool("registro.console", true));
        String pattern = config.string("registro.formato_data", DEFAULT_DATE);
        try {
            date = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException e) {
            getLogger().warning("config.yml: registro.formato_data inválido ('" + pattern + "'), usando " + DEFAULT_DATE);
            date = DateTimeFormatter.ofPattern(DEFAULT_DATE).withZone(ZoneId.systemDefault());
        }
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        settings();
        event.reloaded(getName());
    }

    /** {@code /aincrad log [jogador|*] [tipo|*] [página]}. */
    private final class LogCommand implements SubCommand {

        @Override
        public String name() {
            return "log";
        }

        @Override
        public String permission() {
            return "aincrad.admin";
        }

        @Override
        public String usage() {
            return "log [jogador|*] [tipo|*] [página]";
        }

        @Override
        public Component description() {
            return msg.get("log.descricao");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            Filter f = Filter.parse(args);
            if (f == null) {
                msg.send(sender, "log.uso");
                return;
            }
            log.page(f, perPage).whenComplete((rows, ex) -> AincradCore.get().runSync(() -> {
                if (ex != null) {
                    getLogger().log(Level.WARNING, "Falha ao ler o registro de admin", ex);
                    msg.send(sender, "log.erro");
                    return;
                }
                show(sender, f, rows);
            }));
        }

        private void show(CommandSender sender, Filter f, List<Entry> rows) {
            if (rows.isEmpty()) {
                msg.send(sender, "log.vazio");
                return;
            }
            List<String> filter = new ArrayList<>();
            if (f.player() != null) {
                filter.add(msg.raw("log.filtro_jogador").replace("<jogador>", f.player()));
            }
            if (f.type() != null) {
                filter.add(msg.raw("log.filtro_tipo").replace("<tipo>", f.type()));
            }
            msg.send(sender, "log.titulo", Messages.vars("pagina", f.page(),
                    "filtro", filter.isEmpty() ? msg.raw("log.filtro_tudo") : String.join(", ", filter)));
            for (Entry e : rows.subList(0, Math.min(perPage, rows.size()))) {
                msg.send(sender, "log.linha", Messages.vars("data", date.format(Instant.ofEpochMilli(e.at())),
                        "autor", e.actor(), "plugin", e.plugin(), "acao", e.action(), "detalhe", e.detail()));
            }
            if (rows.size() > perPage) {
                msg.send(sender, "log.proxima", Messages.vars("jogador", f.player() == null ? "*" : f.player(),
                        "tipo", f.type() == null ? "*" : f.type(), "pagina", f.page() + 1));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            List<String> opts = new ArrayList<>();
            if (args.length == 1) {
                opts.add("*");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    opts.add(p.getName());
                }
            } else if (args.length == 2) {
                opts.add("*");
                opts.addAll(PLUGINS);
            }
            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
            return opts;
        }
    }
}
