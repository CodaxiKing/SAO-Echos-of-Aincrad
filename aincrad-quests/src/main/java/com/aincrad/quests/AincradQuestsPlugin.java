package com.aincrad.quests;

import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.quests.api.AincradQuestsAPI;
import com.aincrad.quests.hook.BossesListener;
import com.aincrad.quests.hook.Hooks;
import com.aincrad.quests.hook.LifeListener;
import com.aincrad.quests.model.QuestDef;
import com.aincrad.quests.model.QuestRules;
import com.aincrad.quests.model.QuestRules.Progress;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AincradQuests: as missões de NPC — objetivos em etapas, recompensas pelas APIs dos outros
 * plugins, diárias e semanais, quest de party, NPCs com diálogo e o diário. Etapas 37 e 38.
 */
public final class AincradQuestsPlugin extends JavaPlugin implements Listener {

    private Messages msg;
    private QuestService quests;
    private NpcService npcs;

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        try {
            core.database().migrate("quests", QuestService.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradQuests não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Hooks hooks = new Hooks(this);
        quests = new QuestService(this, core.database(), msg, hooks);
        quests.reload();
        npcs = new NpcService(this, msg, quests);
        npcs.load();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(quests, this);
        getServer().getPluginManager().registerEvents(npcs, this);
        hook(hooks, "AincradBosses", () -> new BossesListener(quests));
        hook(hooks, "AincradLife", () -> new LifeListener(quests));
        getServer().getScheduler().runTaskTimer(this, () -> {
            quests.tick();
            npcs.tick();
        }, 40L, 40L);

        Command journal = new Command("quests", "", "/quests", List.of("missoes", "diario")) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (sender instanceof Player p) {
                    journal(p, args);
                }
                return true;
            }
        };
        getServer().getCommandMap().register("aincradquests", journal);
        core.commands().register(this, new QuestAdmin());
        core.commands().register(this, new NpcAdmin());
        AincradQuestsAPI.init(quests);
        getServer().getServicesManager().register(AincradQuestsAPI.class, AincradQuestsAPI.instance(), this, ServicePriority.Normal);
        for (Player p : getServer().getOnlinePlayers()) {
            quests.load(p.getUniqueId());
        }
    }

    private void hook(Hooks hooks, String name, Supplier<Listener> listener) {
        if (!hooks.has(name)) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(listener.get(), this);
        } catch (LinkageError e) {
            getLogger().warning(name + " incompatível: os objetivos dele não vão contar (" + e + ").");
        }
    }

    @Override
    public void onDisable() {
        AincradQuestsAPI.init(null);
        getServer().getServicesManager().unregisterAll(this);
        if (npcs != null) {
            npcs.removeBodies();
        }
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        quests.reload();
        npcs.load();
        event.reloaded(getName());
    }

    // ------------------------------------------------------------------ /quests (o diário)

    private void journal(Player p, String[] args) {
        Map<String, Progress> m = quests.data(p);
        if (m == null) {
            msg.send(p, "quest.carregando");
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("abandonar")) {
            QuestDef def = quests.quest(args[1]);
            msg.send(p, def != null && quests.abandon(p, def) ? "diario.abandonou" : "diario.nao_ativa",
                    Messages.vars("quest", def == null ? args[1] : def.name()));
            return;
        }
        Menu menu = Menu.of(msg.get("diario.titulo")).rows(6);
        String tracked = quests.tracked(p);
        List<QuestDef> done = new ArrayList<>();
        for (QuestDef def : quests.quests().values()) {
            Progress pr = m.get(def.id());
            if (pr == null) {
                continue;
            }
            if (pr.state() != QuestRules.State.ATIVA) {
                done.add(def);
                continue;
            }
            boolean ready = QuestRules.ready(def, pr);
            List<Component> lore = new ArrayList<>();
            if (!def.description().isBlank()) {
                lore.add(msg.get("dialogo.descricao", Messages.vars("descricao", def.description())));
            }
            lore.add(msg.get("quest.objetivo", Messages.vars("texto", quests.text(def, pr.stage()), "feito", ready ? 1 : pr.count(),
                    "total", ready ? 1 : def.stages().get(pr.stage()).amount())));
            lore.add(msg.get("diario.etapa", Messages.vars("etapa", Math.min(pr.stage() + 1, def.stages().size()), "total", def.stages().size())));
            lore.add(msg.get("dialogo.recompensa", Messages.vars("xp", def.reward().xp(), "col", def.reward().col())));
            lore.add(msg.get(def.id().equals(tracked) ? "diario.rastreada" : "diario.clique_rastrear"));
            lore.add(msg.get("diario.como_abandonar", Messages.vars("id", def.id())));
            menu.item(Menu.Button.of(new ItemStack(ready ? Material.EMERALD : def.id().equals(tracked) ? Material.COMPASS : Material.BOOK),
                    msg.get("diario.ativa", Messages.vars("quest", def.name(), "tipo", msg.raw("tipo." + def.kind().name().toLowerCase(Locale.ROOT)))),
                    pl -> quests.track(pl, def)).closing().lore(lore));
        }
        for (QuestDef def : done) {
            Progress pr = m.get(def.id());
            menu.item(Menu.Button.of(new ItemStack(Material.PAPER), msg.get("diario.feita", Messages.vars("quest", def.name())), pl -> { })
                    .lore(List.of(msg.get("diario.vezes", Messages.vars("vezes", pr.times())))));
        }
        if (menu.items().isEmpty()) {
            msg.send(p, "diario.vazio");
            return;
        }
        AincradCore.get().menus().open(p, menu);
    }

    // ------------------------------------------------------------------ admin

    private static List<String> filter(List<String> opts, String[] args) {
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(opts);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private static List<String> players() {
        List<String> out = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        return out;
    }

    /** {@code /aincrad quest dar|concluir|resetar <jogador> <id>} e {@code lista}. */
    private final class QuestAdmin implements SubCommand {

        @Override
        public String name() {
            return "quest";
        }

        @Override
        public String permission() {
            return "aincrad.admin";
        }

        @Override
        public String usage() {
            return "quest dar|concluir|resetar <jogador> <id>";
        }

        @Override
        public Component description() {
            return msg.get("admin.descricao_quest");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("lista")) {
                msg.send(sender, "admin.lista", Messages.vars("qtd", quests.quests().size(), "quests", String.join(", ", quests.quests().keySet())));
                quests.errors().forEach(e -> msg.send(sender, "admin.erro", Messages.vars("erro", e)));
                return;
            }
            Player t = args.length < 3 ? null : Bukkit.getPlayerExact(args[1]);
            QuestDef def = args.length < 3 ? null : quests.quest(args[2]);
            if (t == null || def == null || !List.of("dar", "concluir", "resetar").contains(sub)) {
                msg.send(sender, "admin.uso_quest");
                return;
            }
            boolean ok = switch (sub) {
                case "dar" -> quests.give(t, def);
                case "concluir" -> quests.forceComplete(t, def);
                default -> quests.reset(t, def);
            };
            msg.send(sender, ok ? "admin.feito_" + sub : "admin.falhou", Messages.vars("jogador", t.getName(), "quest", def.name()));
            if (ok) {
                Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(sender, "quests", "quest." + sub, def.id() + " para " + t.getName()));
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            return filter(switch (args.length) {
                case 1 -> List.of("dar", "concluir", "resetar", "lista");
                case 2 -> players();
                case 3 -> new ArrayList<>(quests.quests().keySet());
                default -> List.of();
            }, args);
        }
    }

    /** {@code /aincrad npc criar|mover|fala|remover|listar}. */
    private final class NpcAdmin implements SubCommand {

        @Override
        public String name() {
            return "npc";
        }

        @Override
        public String permission() {
            return "aincrad.admin";
        }

        @Override
        public String usage() {
            return "npc criar <id> <nome>|mover <id>|fala <id> <texto>|remover <id>|listar";
        }

        @Override
        public Component description() {
            return msg.get("admin.descricao_npc");
        }

        private void log(CommandSender actor, String action, String detail) {
            Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(actor, "quests", action, detail));
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
            String rest = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
            switch (sub) {
                case "listar" -> {
                    if (npcs.all().isEmpty()) {
                        msg.send(sender, "admin.npc_nenhum");
                    }
                    for (NpcService.Npc n : npcs.all().values()) {
                        msg.send(sender, "admin.npc_linha", Messages.vars("id", n.id(), "mundo", n.world(),
                                "x", (int) n.x(), "y", (int) n.y(), "z", (int) n.z()), net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
                                .component("nome", Messages.parse(n.name())));
                    }
                }
                case "criar", "mover" -> {
                    if (!(sender instanceof Player p) || !NpcService.validId(id) || (sub.equals("criar") && rest == null)
                            || (sub.equals("mover") && !npcs.all().containsKey(id))) {
                        msg.send(sender, "admin.uso_npc");
                        return;
                    }
                    npcs.put(id, sub.equals("criar") ? rest : null, p.getLocation(), null);
                    msg.send(sender, "admin.npc_" + (sub.equals("criar") ? "criado" : "movido"), Messages.vars("id", id));
                    log(sender, "npc." + sub, id);
                }
                case "fala" -> {
                    if (id == null || rest == null || !npcs.setGreeting(id, rest)) {
                        msg.send(sender, "admin.uso_npc");
                        return;
                    }
                    msg.send(sender, "admin.npc_fala", Messages.vars("id", id));
                    log(sender, "npc.fala", id);
                }
                case "remover" -> {
                    if (id == null || !npcs.delete(id)) {
                        msg.send(sender, "admin.uso_npc");
                        return;
                    }
                    msg.send(sender, "admin.npc_removido", Messages.vars("id", id));
                    log(sender, "npc.remover", id);
                }
                default -> msg.send(sender, "admin.uso_npc");
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            return filter(switch (args.length) {
                case 1 -> List.of("criar", "mover", "fala", "remover", "listar");
                case 2 -> args[0].equalsIgnoreCase("criar") ? List.of() : new ArrayList<>(npcs.all().keySet());
                default -> List.of();
            }, args);
        }
    }
}
