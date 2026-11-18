package com.aincrad.admin;

import com.aincrad.admin.model.BackupRules;
import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** {@code /aincrad backup agora | listar | restaurar <data> [confirmar]}. */
final class BackupCommand implements SubCommand {

    private static final long CONFIRM_MS = 60_000L;

    private record Pending(String name, long until) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final BackupService backups;
    private final Map<String, Pending> pending = new HashMap<>();

    BackupCommand(Plugin plugin, Messages msg, BackupService backups) {
        this.plugin = plugin;
        this.msg = msg;
        this.backups = backups;
    }

    @Override
    public String name() {
        return "backup";
    }

    @Override
    public String permission() {
        return "aincrad.admin";
    }

    @Override
    public String usage() {
        return "backup agora|listar|restaurar <data>";
    }

    @Override
    public Component description() {
        return msg.get("backup.descricao");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "agora" -> now(sender);
            case "listar" -> list(sender);
            case "restaurar" -> restore(sender, args);
            default -> msg.send(sender, "backup.uso");
        }
    }

    private void now(CommandSender sender) {
        msg.send(sender, "backup.comecou");
        backups.run("manual: " + sender.getName()).whenComplete((r, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null) {
                msg.send(sender, "backup.falhou", Messages.vars("erro", reason(ex)));
                return;
            }
            msg.send(sender, r.database() ? "backup.feito" : "backup.feito_sem_banco",
                    Messages.vars("nome", r.name(), "arquivos", r.files(), "tamanho", BackupRules.size(r.bytes())));
            Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(sender, "admin", "backup.agora", r.name()));
        }));
    }

    private void list(CommandSender sender) {
        List<String> names = backups.names();
        if (names.isEmpty()) {
            msg.send(sender, "backup.nenhum");
            return;
        }
        msg.send(sender, "backup.lista_titulo", Messages.vars("qtd", names.size()));
        for (int i = names.size() - 1; i >= 0; i--) {
            YamlConfiguration m = backups.manifest(names.get(i));
            msg.send(sender, "backup.lista_linha", Messages.vars("nome", names.get(i), "arquivos", m.getInt("arquivos"),
                    "tamanho", BackupRules.size(m.getLong("bytes")), "banco", m.getString("banco", "?"),
                    "motivo", m.getString("motivo", "?")));
        }
    }

    private void restore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg.send(sender, "backup.uso");
            return;
        }
        String name = BackupRules.resolve(backups.names(), args[1]);
        if (name == null) {
            msg.send(sender, "backup.desconhecido", Messages.vars("data", args[1]));
            return;
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            msg.send(sender, "backup.com_jogadores", Messages.vars("qtd", Bukkit.getOnlinePlayers().size()));
            return;
        }
        boolean confirm = args.length > 2 && args[2].equalsIgnoreCase("confirmar");
        Pending p = pending.get(sender.getName());
        if (!confirm || p == null || !p.name().equals(name) || p.until() < System.currentTimeMillis()) {
            pending.put(sender.getName(), new Pending(name, System.currentTimeMillis() + CONFIRM_MS));
            YamlConfiguration m = backups.manifest(name);
            msg.send(sender, "backup.confirme", Messages.vars("nome", name, "arquivos", m.getInt("arquivos"),
                    "banco", m.getString("banco", "?")));
            if (AincradCore.get().database().type() == Database.Type.MYSQL) {
                msg.send(sender, "backup.aviso_mysql");
            }
            return;
        }
        pending.remove(sender.getName());
        msg.send(sender, "backup.antes");
        // O estado de agora é guardado antes: restaurar o backup errado também tem volta.
        backups.run("antes de restaurar " + name).whenComplete((r, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null) {
                msg.send(sender, "backup.falhou", Messages.vars("erro", reason(ex)));
                return;
            }
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                msg.send(sender, "backup.com_jogadores", Messages.vars("qtd", Bukkit.getOnlinePlayers().size()));
                return;
            }
            try {
                backups.markRestore(name);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Não foi possível marcar a restauração", e);
                msg.send(sender, "backup.falhou", Messages.vars("erro", String.valueOf(e.getMessage())));
                return;
            }
            Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(sender, "admin", "backup.restaurar", name));
            plugin.getLogger().warning(sender.getName() + " pediu a restauração do backup " + name
                    + " (estado atual guardado em " + r.name() + "). Desligando: a restauração roda ao ligar.");
            msg.send(sender, "backup.desligando", Messages.vars("nome", name, "atual", r.name()));
            Bukkit.shutdown();
        }));
    }

    private static String reason(Throwable ex) {
        Throwable t = ex.getCause() != null && ex instanceof java.util.concurrent.CompletionException ? ex.getCause() : ex;
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> opts = new ArrayList<>();
        if (args.length == 1) {
            opts.addAll(List.of("agora", "listar", "restaurar"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("restaurar")) {
            opts.addAll(backups.names());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("restaurar")) {
            opts.add("confirmar");
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return opts;
    }
}
