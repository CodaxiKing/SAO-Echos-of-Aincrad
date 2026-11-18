package com.aincrad.duels;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.duels.api.AincradDuelsAPI;
import com.aincrad.duels.hook.Hooks;
import com.aincrad.duels.model.DuelMode;
import com.aincrad.duels.model.DuelScore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * AincradDuels: duelos entre jogadores, como no anime — primeiro golpe, metade da vida ou
 * total —, com aposta em Col, placar e ranking. Era parte do AincradSocial.
 */
public final class AincradDuelsPlugin extends JavaPlugin implements Listener {

    private static final List<String> MODES = List.of("primeiro_golpe", "metade", "total");

    private Messages msg;
    private ConfigFile config;
    private Hooks hooks;
    private ScoreStore scores;
    private DuelManager duels;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        hooks = new Hooks();
        scores = new ScoreStore(core.database());
        try {
            core.database().migrate("duels", ScoreStore.MIGRATIONS);
            // Duelos que estavam valendo quando o servidor caiu: as apostas voltam aos donos.
            List<String> orphans = scores.orphanBets(core.server());
            if (!orphans.isEmpty()) {
                getServer().getScheduler().runTask(this, () -> {
                    orphans.forEach(hooks::refund);
                    getLogger().info(orphans.size() + " aposta(s) de duelos interrompidos pelo reinício voltaram aos donos.");
                });
            }
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradDuels não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        duels = new DuelManager(this, msg, config, hooks, scores);
        getServer().getPluginManager().registerEvents(duels, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, duels::tick, 10L, 10L);
        for (Player p : getServer().getOnlinePlayers()) {
            scores.load(p.getUniqueId(), s -> { });
        }
        Command cmd = new Command("duelo", "", "/duelo", List.of("duel", "duelar", "duelos")) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                command(sender, args);
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return complete(sender, args);
            }
        };
        getServer().getCommandMap().register("aincradduels", cmd);
        AincradDuelsAPI.init(duels, scores);
        getServer().getServicesManager().register(AincradDuelsAPI.class, AincradDuelsAPI.instance(), this, ServicePriority.Normal);
        getLogger().info("AincradDuels ligado" + (hooks.hasEconomy() ? ", com aposta em Col." : " (sem o AincradEconomy: sem apostas)."));
    }

    @Override
    public void onDisable() {
        if (duels != null) {
            duels.shutdown();
        }
        var known = getServer().getCommandMap().getKnownCommands();
        for (String name : List.of("duelo", "duel", "duelar", "duelos")) {
            known.remove(name);
            known.remove("aincradduels:" + name);
        }
        AincradDuelsAPI.init(null, null);
        getServer().getServicesManager().unregisterAll(this);
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        event.reloaded(getName());
    }

    // ------------------------------------------------------------------ /duelo

    private void command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "so_jogador");
            return;
        }
        if (args.length == 0) {
            openPlayers(p);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "aceitar", "recusar" -> {
                UUID from = null;
                if (args.length > 1) {
                    Player other = Bukkit.getPlayerExact(args[1]);
                    if (other == null) {
                        msg.send(p, "jogador_nao_encontrado", Messages.vars("jogador", args[1]));
                        return;
                    }
                    from = other.getUniqueId();
                }
                if (args[0].equalsIgnoreCase("aceitar")) {
                    duels.accept(p, from);
                } else {
                    duels.decline(p, from);
                }
            }
            case "desistir", "render" -> duels.surrender(p);
            case "placar" -> {
                Player who = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : p;
                if (who == null) {
                    msg.send(p, "jogador_nao_encontrado", Messages.vars("jogador", args[1]));
                    return;
                }
                scores.load(who.getUniqueId(), s -> msg.send(p, "placar.linha", scoreVars(who.getName(), s)));
            }
            case "ranking", "top" -> scores.top(config.integer("ranking.tamanho", 10, 1, 100), rows -> {
                if (rows.isEmpty()) {
                    msg.send(p, "placar.ranking_vazio");
                    return;
                }
                msg.send(p, "placar.ranking_titulo");
                int pos = 1;
                for (ScoreStore.Row row : rows) {
                    msg.sendRaw(p, "placar.ranking_linha", Messages.vars("pos", pos++), scoreVars(row.name(), row.score()));
                }
            });
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    msg.send(p, "jogador_nao_encontrado", Messages.vars("jogador", args[0]));
                    return;
                }
                DuelMode mode = null;
                long stake = 0;
                for (int i = 1; i < args.length; i++) {
                    DuelMode m = DuelMode.parse(args[i]);
                    if (m != null && i == 1) {
                        mode = m;
                        continue;
                    }
                    try {
                        stake = Long.parseLong(args[i]);
                    } catch (NumberFormatException e) {
                        msg.send(p, "uso");
                        return;
                    }
                }
                duels.challenge(p, target, mode, stake);
            }
        }
    }

    private static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver scoreVars(String name, DuelScore s) {
        return Messages.vars("jogador", name, "vitorias", s.wins(), "derrotas", s.losses(), "empates", s.draws(),
                "sequencia", s.streak(), "melhor", s.best(), "aproveitamento", s.winRate());
    }

    private List<String> complete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length <= 1) {
            out.addAll(List.of("aceitar", "recusar", "desistir", "placar", "ranking"));
            names(sender, out);
        } else if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (List.of("aceitar", "recusar", "placar").contains(first)) {
                names(sender, out);
            } else if (!List.of("desistir", "ranking").contains(first)) {
                out.addAll(MODES);
            }
        } else if (args.length == 3) {
            config.yaml().getLongList("aposta.valores").forEach(v -> out.add(String.valueOf(v)));
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private static void names(CommandSender sender, List<String> out) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(sender)) {
                out.add(o.getName());
            }
        }
    }

    // ------------------------------------------------------------------ menus: quem, modo, aposta

    private static ItemStack head(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(p);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack icon(Material m) {
        return ItemBuilder.of(m).hideAll().build();
    }

    /** /duelo sem nada: quem está por perto, o placar e o ranking. */
    private void openPlayers(Player p) {
        Menu menu = Menu.of(msg.get("menu.titulo")).rows(6).floating();
        double reach = config.decimal("distancia_desafio", 16, 2, 256);
        List<Player> near = new ArrayList<>();
        for (Player o : p.getWorld().getPlayers()) {
            if (!o.equals(p) && p.canSee(o) && o.getLocation().distanceSquared(p.getLocation()) <= reach * reach) {
                near.add(o);
            }
        }
        near.sort(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(p.getLocation())));
        for (Player o : near) {
            DuelScore s = scores.cached(o.getUniqueId());
            menu.item(Menu.Button.of(head(o), Component.text(o.getName()), pl -> openModes(pl, o.getUniqueId()))
                    .lore(msg.list("menu.jogador_lore", scoreVars(o.getName(), s))));
        }
        if (near.isEmpty()) {
            menu.item(Menu.Button.of(icon(Material.BARRIER), msg.get("menu.ninguem"), pl -> { }));
        }
        menu.button(Menu.Button.of(icon(Material.BOOK), msg.get("menu.meu_placar"), pl ->
                scores.load(pl.getUniqueId(), s -> msg.send(pl, "placar.linha", scoreVars(pl.getName(), s)))).closing());
        menu.button(Menu.Button.of(icon(Material.GOLD_INGOT), msg.get("menu.ranking"), pl -> command(pl, new String[]{"ranking"})).closing());
        AincradCore.get().menus().open(p, menu);
    }

    private void openModes(Player p, UUID target) {
        Menu menu = Menu.of(msg.get("menu.titulo_modo")).rows(3).floating();
        Material[] icons = {Material.WOODEN_SWORD, Material.IRON_SWORD, Material.NETHERITE_SWORD};
        for (DuelMode m : DuelMode.values()) {
            if (m.lethal() && !duels.allowTotal()) {
                continue;
            }
            menu.item(Menu.Button.of(icon(icons[m.ordinal()]), msg.get("modo." + m.key()), pl -> openStakes(pl, target, m))
                    .lore(msg.list("menu.modo_lore." + m.key())));
        }
        menu.button(Menu.Button.of(icon(Material.ARROW), msg.get("menu.voltar"), this::openPlayers));
        AincradCore.get().menus().open(p, menu);
    }

    /** A aposta: os valores da config que cabem no saldo de quem desafia. */
    private void openStakes(Player p, UUID target, DuelMode mode) {
        long max = duels.maxStake();
        List<Long> values = new ArrayList<>();
        for (long v : config.yaml().getLongList("aposta.valores")) {
            if (v > 0 && v <= max && v <= hooks.balance(p)) {
                values.add(v);
            }
        }
        if (values.isEmpty()) {
            send(p, target, mode, 0);
            return;
        }
        Menu menu = Menu.of(msg.get("menu.titulo_aposta")).rows(3).floating();
        menu.item(Menu.Button.of(icon(Material.PAPER), msg.get("menu.sem_aposta"), pl -> send(pl, target, mode, 0)).closing());
        for (long v : values) {
            menu.item(Menu.Button.of(icon(Material.GOLD_NUGGET), msg.get("menu.aposta", Messages.vars("valor", hooks.format(v))),
                    pl -> send(pl, target, mode, v)).closing());
        }
        menu.button(Menu.Button.of(icon(Material.ARROW), msg.get("menu.voltar"), pl -> openModes(pl, target)));
        AincradCore.get().menus().open(p, menu);
    }

    private void send(Player p, UUID target, DuelMode mode, long stake) {
        Player to = Bukkit.getPlayer(target);
        if (to == null) {
            msg.send(p, "jogador_nao_encontrado", Messages.vars("jogador", "?"));
            return;
        }
        duels.challenge(p, to, mode, stake);
    }
}
