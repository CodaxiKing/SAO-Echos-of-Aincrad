package com.aincrad.ui.command;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.hud.HudCalibration;
import com.aincrad.ui.prefs.PlayerPrefs;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /hud}: cada jogador encaixa a HUD na própria tela.
 *
 * <ul>
 *   <li>{@code /hud tela <largura> <altura> [escala]} — calcula a posição pela janela do jogo;</li>
 *   <li>{@code /hud fora [px]} e {@code /hud dentro [px]} — acerto fino;</li>
 *   <li>{@code /hud padrao} — volta ao padrão do servidor.</li>
 * </ul>
 */
public final class HudCommand implements TabExecutor {

    private final AincradUIPlugin plugin;

    public HudCommand(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            return true;
        }
        PlayerPrefs prefs = plugin.prefs();
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (op) {
            case "tela" -> {
                int w = number(args, 1, -1);
                int h = number(args, 2, -1);
                if (w < 320 || h < 240 || w > 16384 || h > 16384) {
                    plugin.messages().send(p, "hud_cmd.uso");
                    return true;
                }
                // Sem escala, vale a "Auto" do Minecraft (a maior que cabe na janela).
                int scale = HudCalibration.effectiveScale(w, h, number(args, 3, 0));
                int gui = HudCalibration.guiWidth(w, scale);
                prefs.setHudDx(p, HudCalibration.dx(gui, plugin.settings().flatHudX));
                plugin.hud().update(p);
                plugin.messages().send(p, "hud_cmd.tela", Placeholder.unparsed("largura", String.valueOf(w)),
                        Placeholder.unparsed("altura", String.valueOf(h)), Placeholder.unparsed("escala", String.valueOf(scale)));
            }
            case "fora", "dentro" -> {
                int step = Math.max(1, Math.min(400, number(args, 1, 8)));
                prefs.setHudDx(p, prefs.hudDx(p) + (op.equals("fora") ? -step : step));
                plugin.hud().update(p);
                plugin.messages().send(p, "hud_cmd.movida", Placeholder.unparsed("posicao", String.valueOf(-prefs.hudDx(p))));
            }
            case "padrao", "padrão" -> {
                prefs.setHudDx(p, 0);
                plugin.hud().update(p);
                plugin.messages().send(p, "hud_cmd.padrao");
            }
            default -> plugin.messages().send(p, "hud_cmd.uso");
        }
        return true;
    }

    private static int number(String[] args, int index, int def) {
        if (args.length <= index) {
            return def;
        }
        try {
            return Integer.parseInt(args[index].trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("tela", "fora", "dentro", "padrao").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tela")) {
            return List.of("1920", "1366", "1280", "2560");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("tela")) {
            return List.of("1080", "768", "720", "1440");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("tela")) {
            return List.of("1", "2", "3", "4");
        }
        return List.of();
    }
}
