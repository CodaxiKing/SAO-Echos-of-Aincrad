package com.aincrad.ui.command;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.Messages;
import com.aincrad.ui.config.UiSettings.SoundDef;
import com.aincrad.ui.screen.Screen;
import com.aincrad.ui.screen.Screens;
import com.aincrad.ui.sound.UiSound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** /menu, /creditos e /aincradui (/aincrad ui) teste|reload. */
public final class UiCommands implements CommandExecutor, TabCompleter {

    /** Telas de /aincrad ui teste: as de {@link Screens#NAMES} mais os testes de HUD e som. */
    private static final List<String> TEST_SCREENS;

    static {
        List<String> all = new ArrayList<>(Screens.NAMES);
        all.addAll(List.of("morte", "critico", "sons"));
        TEST_SCREENS = List.copyOf(all);
    }

    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");

    private final AincradUIPlugin plugin;

    public UiCommands(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "menu" -> {
                if (!(sender instanceof Player p)) {
                    msg().send(sender, "geral.so_jogador");
                    return true;
                }
                plugin.sessions().openMain(p);
            }
            case "creditos" -> credits(sender);
            case "aincradui" -> admin(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aincrad.admin")) {
            msg().send(sender, "geral.sem_permissao");
            return;
        }
        if (args.length == 0) {
            msg().send(sender, "admin.uso");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                msg().send(sender, "admin.reload_ok",
                        Placeholder.unparsed("glifos", String.valueOf(plugin.glyphs().presentCount())),
                        Placeholder.unparsed("pack", plugin.pack().mode().name().toLowerCase(Locale.ROOT)));
            }
            case "teste" -> test(sender, args);
            default -> msg().send(sender, "admin.uso");
        }
    }

    private void test(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg().send(sender, "geral.so_jogador");
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "admin.uso");
            msg().send(sender, "admin.telas", Placeholder.unparsed("telas", String.join(", ", TEST_SCREENS)));
            return;
        }
        String tela = args[1].toLowerCase(Locale.ROOT);
        switch (tela) {
            case "morte" -> plugin.deathEffects().showDeath(p);
            case "critico" -> {
                plugin.hud().forceCritical(p, 6);
                msg().send(p, "admin.critico", Placeholder.unparsed("segundos", "6"));
            }
            case "sons" -> playAllSounds(p);
            default -> {
                Screen s = plugin.screens().byName(p, tela);
                if (s == null) {
                    msg().send(p, "admin.tela_invalida", Placeholder.unparsed("tela", tela));
                    msg().send(p, "admin.telas", Placeholder.unparsed("telas", String.join(", ", TEST_SCREENS)));
                    return;
                }
                plugin.sessions().close(p, false);
                plugin.sessions().open(p, s);
            }
        }
    }

    private void playAllSounds(Player p) {
        UiSound[] all = UiSound.values();
        for (int i = 0; i < all.length; i++) {
            UiSound sound = all[i];
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                SoundDef def = plugin.settings().sounds.get(sound);
                msg().send(p, "admin.som",
                        Placeholder.unparsed("som", sound.configKey()),
                        Placeholder.unparsed("evento", def == null ? "?" : "aincrad:" + def.event()));
                plugin.sounds().play(p, sound);
            }, 20L * i + 1);
        }
    }

    private void credits(CommandSender sender) {
        try (InputStream in = plugin.getResource("CREDITOS.md")) {
            if (in == null) {
                msg().send(sender, "creditos.nao_encontrado");
                return;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sender.sendMessage(formatMarkdown(line));
                }
            }
        } catch (IOException e) {
            msg().send(sender, "creditos.nao_encontrado");
        }
    }

    /** Markdown simples do CREDITOS.md → texto colorido no chat. */
    static Component formatMarkdown(String line) {
        String text = LINK.matcher(line).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + " (" + m.group(2) + ")"));
        text = text.replace("**", "").replace("`", "");
        String escaped = text.replace("<", "\\<");
        if (escaped.startsWith("# ")) {
            return Messages.parse("<gold><bold>" + escaped.substring(2));
        }
        if (escaped.startsWith("## ")) {
            return Messages.parse("<yellow>" + escaped.substring(3));
        }
        if (escaped.startsWith("- ")) {
            return Messages.parse("<gray> • <white>" + escaped.substring(2));
        }
        return Messages.parse("<gray>" + escaped);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("aincradui") || !sender.hasPermission("aincrad.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("teste", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("teste")) {
            return filter(TEST_SCREENS, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).toList();
    }
}
