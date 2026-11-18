package com.aincrad.admin;

import com.aincrad.admin.model.BackupRules;
import com.aincrad.core.AincradCore;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** {@code /aincrad status}: versões dos plugins Aincrad, integrações, banco, backup e resource pack. */
final class StatusCommand implements SubCommand {

    /** Os plugins que já têm código, na ordem do plano. */
    private static final List<String> KNOWN = List.of("AincradCore", "AincradRPG", "AincradEconomy", "AincradFloors",
            "AincradBosses", "AincradEffects", "AincradUI", "AincradItems", "AincradLife", "AincradReputation",
            "AincradSocial", "AincradAdmin");

    private final Messages msg;
    private final BackupService backups;

    StatusCommand(Messages msg, BackupService backups) {
        this.msg = msg;
        this.backups = backups;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String permission() {
        return "aincrad.admin";
    }

    @Override
    public String usage() {
        return "status";
    }

    @Override
    public Component description() {
        return msg.get("status.descricao");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        AincradCore core = AincradCore.get();
        msg.send(sender, "status.titulo", Messages.vars("servidor", core.server(),
                "banco", core.database().type().name().toLowerCase(Locale.ROOT)));

        List<String> names = new ArrayList<>(KNOWN);
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().startsWith("Aincrad") && !names.contains(p.getName())) {
                names.add(p.getName());
            }
        }
        // Integração -> os plugins Aincrad que a usam.
        TreeMap<String, List<String>> integrations = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String n : names) {
            Plugin p = Bukkit.getPluginManager().getPlugin(n);
            if (p == null) {
                msg.send(sender, "status.plugin_falta", Messages.vars("plugin", n));
                continue;
            }
            msg.send(sender, p.isEnabled() ? "status.plugin" : "status.plugin_desligado",
                    Messages.vars("plugin", n, "versao", p.getPluginMeta().getVersion()));
            for (String dep : p.getPluginMeta().getPluginSoftDependencies()) {
                if (!dep.startsWith("Aincrad")) {
                    integrations.computeIfAbsent(dep, k -> new ArrayList<>()).add(n.substring("Aincrad".length()));
                }
            }
        }
        for (var e : integrations.entrySet()) {
            msg.send(sender, Bukkit.getPluginManager().isPluginEnabled(e.getKey()) ? "status.integracao" : "status.integracao_falta",
                    Messages.vars("nome", e.getKey(), "usam", String.join(", ", e.getValue())));
        }

        List<String> list = backups.names();
        if (list.isEmpty()) {
            msg.send(sender, "status.backup_nenhum");
        } else {
            msg.send(sender, "status.backup", Messages.vars("nome", list.get(list.size() - 1), "qtd", list.size()));
        }
        File pack = pack();
        if (pack == null) {
            msg.send(sender, "status.pack_nenhum");
        } else {
            msg.send(sender, "status.pack", Messages.vars("arquivo", pack.getName(), "tamanho", BackupRules.size(pack.length())));
        }
    }

    /** O .zip do resource pack: o do AincradEffects (o único da rede) ou, sem ele, o do AincradUI. */
    private static File pack() {
        Plugin effects = Bukkit.getPluginManager().getPlugin("AincradEffects");
        if (effects != null) {
            File f = new File(new File(effects.getDataFolder(), "pack"), "aincrad-pack.zip");
            if (f.isFile()) {
                return f;
            }
        }
        Plugin ui = Bukkit.getPluginManager().getPlugin("AincradUI");
        if (ui != null) {
            File f = new File(ui.getDataFolder(), "aincrad-ui-pack.zip");
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }
}
