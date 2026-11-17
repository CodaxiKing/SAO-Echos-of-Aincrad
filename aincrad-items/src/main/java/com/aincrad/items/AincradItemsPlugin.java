package com.aincrad.items;

import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.items.api.AincradItemsAPI;
import com.aincrad.items.crystal.CrystalDef;
import com.aincrad.items.crystal.CrystalRegistry;
import com.aincrad.items.crystal.CrystalService;
import com.aincrad.items.crystal.Resurrection;
import com.aincrad.items.hook.Hooks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** AincradItems: os cristais consumíveis do anime (etapas 20–21). */
public final class AincradItemsPlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private Messages msg;
    private Hooks hooks;
    private CrystalRegistry registry;
    private Resurrection resurrection;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        hooks = new Hooks(this);
        hooks.reload(config);
        registry = new CrystalRegistry(this, msg);
        loadCrystals();
        CrystalService crystals = new CrystalService(this, msg, config, registry, hooks);
        resurrection = new Resurrection(this, msg, config, crystals);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(crystals, this);
        getServer().getPluginManager().registerEvents(resurrection, this);
        getServer().getScheduler().runTaskTimer(this, resurrection::tick, 5L, 5L);
        core.commands().register(this, command());

        AincradItemsAPI.init(registry, resurrection);
        getServer().getServicesManager().register(AincradItemsAPI.class, AincradItemsAPI.instance(), this, ServicePriority.Normal);

        // Os modelos vão para o pack único do AincradEffects (se ele estiver no servidor).
        copyPack();
        if (hooks.hasEffects() && hooks.registerPack(new File(getDataFolder(), "resourcepack"))) {
            getLogger().info("Modelos dos cristais registrados no pack do AincradEffects.");
        }
    }

    /** crystals.yml é lido direto (um cristal apagado não volta sozinho). */
    private void loadCrystals() {
        File file = new File(getDataFolder(), "crystals.yml");
        if (!file.exists()) {
            saveResource("crystals.yml", false);
        }
        String mode = config.string("modelos", "auto").toLowerCase(Locale.ROOT);
        boolean models = mode.equals("sim") || (mode.equals("auto") && hooks.hasEffects());
        List<String> errors = registry.load(YamlConfiguration.loadConfiguration(file).getConfigurationSection("cristais"), models);
        errors.forEach(e -> getLogger().warning("crystals.yml: " + e));
        getLogger().info(registry.all().size() + " cristal(is) carregado(s)" + (models ? ", com os modelos do pack." : ", sem modelos do pack."));
    }

    /** Copia do .jar os arquivos de resourcepack/ que ainda não existem (as artes que você trocou ficam). */
    private void copyPack() {
        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("resourcepack/")) {
                    continue;
                }
                File out = new File(getDataFolder(), e.getName());
                if (out.exists()) {
                    continue;
                }
                Files.createDirectories(out.getParentFile().toPath());
                try (InputStream in = jar.getInputStream(e)) {
                    Files.copy(in, out.toPath());
                }
            }
        } catch (IOException e) {
            getLogger().warning("Não foi possível copiar os modelos dos cristais: " + e.getMessage());
        }
    }

    private SubCommand command() {
        return new SubCommand() {
            @Override public String name() { return "cristal"; }
            @Override public String permission() { return "aincrad.admin"; }
            @Override public String usage() { return "cristal dar <jogador> <tipo> [qtd] | lista"; }
            @Override public Component description() { return msg.get("admin.descricao"); }

            private String ids() {
                return String.join(", ", AincradItemsAPI.getCrystals());
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("lista")) {
                    msg.send(sender, "admin.lista", Messages.vars("lista", ids()));
                    return;
                }
                if (args.length < 3 || !args[0].equalsIgnoreCase("dar")) {
                    msg.send(sender, "admin.uso");
                    return;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    msg.send(sender, "admin.offline");
                    return;
                }
                int amount = 1;
                if (args.length > 3) {
                    try {
                        amount = Math.max(1, Math.min(2304, Integer.parseInt(args[3])));
                    } catch (NumberFormatException e) {
                        msg.send(sender, "admin.uso");
                        return;
                    }
                }
                CrystalDef def = registry.get(args[2]);
                if (def == null || !AincradItemsAPI.giveCrystal(t, def.id(), amount)) {
                    msg.send(sender, "cristal.desconhecido", Messages.vars("id", args[2], "lista", ids()));
                    return;
                }
                msg.send(sender, "admin.dado", Messages.vars("qtd", amount, "jogador", t.getName()),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("cristal", Messages.parse(def.name())));
                Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(sender, "items", "cristal.dar",
                        amount + "x " + def.id() + " para " + t.getName()));
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                List<String> opts = new ArrayList<>();
                switch (args.length) {
                    case 1 -> opts.addAll(List.of("dar", "lista"));
                    case 2 -> Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                    case 3 -> opts.addAll(AincradItemsAPI.getCrystals());
                    default -> {
                    }
                }
                String last = args[args.length - 1].toLowerCase(Locale.ROOT);
                opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
                return opts;
            }
        };
    }

    @Override
    public void onDisable() {
        if (resurrection != null) {
            resurrection.shutdown();
        }
        AincradItemsAPI.init(null, null);
        getServer().getServicesManager().unregisterAll(this);
    }

    /** /aincrad reload recarrega config, mensagens e cristais (o plano chama de /aincrad items reload). */
    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        hooks.reload(config);
        loadCrystals();
        event.reloaded(getName());
    }
}
