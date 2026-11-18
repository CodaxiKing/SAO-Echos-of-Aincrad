package com.aincrad.skills;

import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.skills.api.AincradSkillsAPI;
import com.aincrad.skills.hook.Hooks;
import com.aincrad.skills.model.Passive;
import com.aincrad.skills.model.SkillDef;
import com.aincrad.skills.model.SkillRules;
import com.aincrad.skills.model.WeaponType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * AincradSkills: proficiência por tipo de arma, Sword Skills ativas (com recarga e a pausa depois
 * do uso), atalhos, skills passivas e a skill única Duas Espadas. Etapas 34 a 36 do plano.
 */
public final class AincradSkillsPlugin extends JavaPlugin implements Listener {

    private ConfigFile config;
    private Messages msg;
    private Hooks hooks;
    private SkillStore store;
    private SkillEngine engine;
    private PassiveService passives;
    private UniqueService unique;
    private NamespacedKey keyShortcut;
    private Map<Integer, Integer> activeSlots = Map.of();
    private Map<Integer, Integer> passiveSlots = Map.of();

    @Override
    public void onEnable() {
        AincradCore core = AincradCore.get();
        msg = core.messages(this);
        config = core.config(this, "config.yml");
        try {
            core.database().migrate("skills", SkillStore.MIGRATIONS);
        } catch (SQLException e) {
            getLogger().severe("Banco indisponível, o AincradSkills não vai ligar: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        keyShortcut = new NamespacedKey(this, "atalho");
        hooks = new Hooks();
        store = new SkillStore(this, core.database());
        unique = new UniqueService(this, core.database(), msg);
        engine = new SkillEngine(this, msg, store, unique, hooks);
        passives = new PassiveService(this, msg, store, engine);
        settings();
        unique.refresh();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(engine, this);
        getServer().getPluginManager().registerEvents(passives, this);
        getServer().getPluginManager().registerEvents(unique, this);
        getServer().getScheduler().runTaskTimer(this, passives::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, unique::refresh, 1200L, 1200L);

        command("skills", List.of("habilidades"), this::skillsCommand);
        command("skill", List.of(), this::castCommand);
        command("rastrear", List.of(), (s, a) -> {
            if (s instanceof Player p) {
                passives.startTracking(p);
            }
        });
        core.commands().register(this, new AdminCommand());
        AincradSkillsAPI.init(engine, store, unique);
        getServer().getServicesManager().register(AincradSkillsAPI.class, AincradSkillsAPI.instance(), this, ServicePriority.Normal);
        for (Player p : getServer().getOnlinePlayers()) {
            store.load(p.getUniqueId());
        }
    }

    @Override
    public void onDisable() {
        AincradSkillsAPI.init(null, null, null);
        getServer().getServicesManager().unregisterAll(this);
    }

    private void settings() {
        engine.reload(config);
        passives.reload(config);
        activeSlots = table("espacos.ativas", Map.of(1, 2, 10, 3, 25, 4));
        passiveSlots = table("espacos.passivas", Map.of(1, 1, 15, 2, 30, 3));
    }

    private Map<Integer, Integer> table(String path, Map<Integer, Integer> def) {
        Map<Integer, Integer> out = new HashMap<>();
        var sec = config.yaml().getConfigurationSection(path);
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    out.put(Integer.parseInt(k), sec.getInt(k));
                } catch (NumberFormatException e) {
                    getLogger().warning("config.yml: " + path + "." + k + " não é um level.");
                }
            }
        }
        return out.isEmpty() ? def : out;
    }

    private void command(String name, List<String> aliases, BiConsumer<CommandSender, String[]> run) {
        Command c = new Command(name, "", "/" + name, aliases) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                run.accept(sender, args);
                return true;
            }
        };
        getServer().getCommandMap().register("aincradskills", c);
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        config.reload();
        settings();
        event.reloaded(getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        store.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        store.unload(event.getPlayer().getUniqueId());
        engine.forget(event.getPlayer().getUniqueId());
        passives.forget(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ espaços

    private int activeAllowed(Player p) {
        return SkillRules.slots(activeSlots, hooks.level(p), SkillRules.ACTIVE_SLOTS);
    }

    private int passiveAllowed(Player p) {
        return SkillRules.slots(passiveSlots, hooks.level(p), SkillRules.PASSIVE_SLOTS);
    }

    private void toggle(Player p, SkillDef def) {
        SkillStore.Data d = store.data(p);
        if (d == null) {
            return;
        }
        int at = d.slotOf(def.id());
        if (at >= 0) {
            store.setActive(p.getUniqueId(), d, at, null);
            msg.send(p, "menu.desequipou", Messages.vars("skill", def.name()));
            return;
        }
        if (!engine.canLearn(p, d, def)) {
            msg.send(p, def.unique() != null && !unique.has(p, def.unique()) ? "skill.unica" : "menu.bloqueada",
                    Messages.vars("skill", def.name(), "valor", def.minProf(), "arma", msg.raw("armas." + def.weapon().key())));
            return;
        }
        int allowed = activeAllowed(p);
        for (int i = 0; i < allowed; i++) {
            if (d.active(i) == null || engine.skill(d.active(i)) == null) {
                store.setActive(p.getUniqueId(), d, i, def.id());
                msg.send(p, "menu.equipou", Messages.vars("skill", def.name(), "atalho", i + 1));
                return;
            }
        }
        msg.send(p, "menu.sem_espaco", Messages.vars("espacos", allowed));
    }

    private void toggle(Player p, Passive passive) {
        SkillStore.Data d = store.data(p);
        if (d == null) {
            return;
        }
        String name = msg.raw("passivas." + passive.key() + ".nome");
        int at = d.slotOf(passive);
        if (at >= 0) {
            store.setPassive(p.getUniqueId(), d, at, null);
            msg.send(p, "menu.desequipou", Messages.vars("skill", name));
            return;
        }
        int allowed = passiveAllowed(p);
        for (int i = 0; i < allowed; i++) {
            if (d.passive(i) == null) {
                store.setPassive(p.getUniqueId(), d, i, passive);
                msg.send(p, "menu.equipou_passiva", Messages.vars("skill", name));
                return;
            }
        }
        msg.send(p, "menu.sem_espaco", Messages.vars("espacos", allowed));
    }

    // ------------------------------------------------------------------ /skills

    private static ItemStack icon(String material, Material fallback) {
        Material m = Material.matchMaterial(material);
        return new ItemStack(m == null || !m.isItem() ? fallback : m);
    }

    private void skillsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("atalho")) {
            giveShortcut(p);
            return;
        }
        openMenu(p);
    }

    private void openMenu(Player p) {
        SkillStore.Data d = store.data(p);
        if (d == null) {
            msg.send(p, "skill.carregando");
            return;
        }
        Menu menu = Menu.of(msg.get("menu.titulo", Messages.vars("ativas", activeAllowed(p), "passivas", passiveAllowed(p)))).rows(6);
        for (WeaponType t : WeaponType.values()) {
            if (t == WeaponType.DUAS_ESPADAS && !unique.has(p, UniqueService.DUAL)) {
                continue;
            }
            menu.item(Menu.Button.of(new ItemStack(Material.BOOK), msg.get("menu.proficiencia",
                    Messages.vars("arma", msg.raw("armas." + t.key()), "valor", d.prof(t), "maximo", SkillRules.MAX_PROF)), pl -> { }));
        }
        WeaponType held = engine.weapon(p);
        List<SkillDef> list = new ArrayList<>(engine.skills().values());
        // As da arma na mão primeiro; depois por arma e por proficiência exigida.
        list.sort((a, b) -> {
            int ha = a.weapon() == held ? 0 : 1;
            int hb = b.weapon() == held ? 0 : 1;
            if (ha != hb) {
                return Integer.compare(ha, hb);
            }
            int w = a.weapon().compareTo(b.weapon());
            return w != 0 ? w : Integer.compare(a.minProf(), b.minProf());
        });
        for (SkillDef def : list) {
            if (def.unique() != null && !unique.has(p, def.unique())) {
                continue; // Skill única dos outros não aparece.
            }
            int slot = d.slotOf(def.id());
            boolean can = engine.canLearn(p, d, def);
            List<Component> lore = new ArrayList<>();
            if (!def.description().isBlank()) {
                lore.add(msg.get("menu.descricao", Messages.vars("descricao", def.description())));
            }
            lore.add(msg.get("menu.dados", Messages.vars("arma", msg.raw("armas." + def.weapon().key()), "proficiencia", def.minProf(),
                    "dano", String.format(Locale.ROOT, "%.1f", def.damage()), "golpes", def.hits(),
                    "alcance", String.format(Locale.ROOT, "%.1f", def.range()),
                    "recarga", String.format(Locale.ROOT, "%.1f", def.cooldownMs() / 1000.0),
                    "pausa", String.format(Locale.ROOT, "%.1f", def.freezeMs() / 1000.0))));
            lore.add(slot >= 0 ? msg.get("menu.equipada", Messages.vars("atalho", slot + 1))
                    : msg.get(can ? "menu.clique_equipar" : "menu.falta_proficiencia", Messages.vars("valor", def.minProf())));
            ItemStack it = can ? icon(def.icon(), Material.IRON_SWORD) : new ItemStack(Material.GRAY_DYE);
            if (slot >= 0) {
                ItemMeta meta = it.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                it.setItemMeta(meta);
            }
            menu.item(Menu.Button.of(it, msg.get(can ? "menu.skill" : "menu.skill_bloqueada", Messages.vars("skill", def.name())), pl -> {
                toggle(pl, def);
                openMenu(pl);
            }).lore(lore));
        }
        for (Passive passive : Passive.values()) {
            boolean on = d.has(passive);
            ItemStack it = icon(passive.icon(), Material.PAPER);
            if (on) {
                ItemMeta meta = it.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                it.setItemMeta(meta);
            }
            menu.item(Menu.Button.of(it, msg.get("menu.passiva", Messages.vars("skill", msg.raw("passivas." + passive.key() + ".nome"))), pl -> {
                toggle(pl, passive);
                openMenu(pl);
            }).lore(List.of(msg.get("menu.descricao", Messages.vars("descricao", msg.raw("passivas." + passive.key() + ".descricao"))),
                    msg.get(on ? "menu.passiva_ligada" : "menu.clique_equipar"))));
        }
        AincradCore.get().menus().open(p, menu);
    }

    // ------------------------------------------------------------------ usar pelo comando e pelo item

    private void castCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        SkillStore.Data d = store.data(p);
        if (d == null) {
            msg.send(p, "skill.carregando");
            return;
        }
        int slot;
        try {
            slot = args.length == 0 ? -1 : Integer.parseInt(args[0]) - 1;
        } catch (NumberFormatException e) {
            slot = -1;
        }
        SkillDef def = engine.skill(d.active(slot));
        if (def == null) {
            msg.send(p, "skill.uso");
            return;
        }
        engine.cast(p, def);
    }

    private void giveShortcut(Player p) {
        Material m = Material.matchMaterial(config.string("ativacao.item_de_atalho", "NETHER_STAR"));
        ItemStack it = new ItemStack(m == null || !m.isItem() ? Material.NETHER_STAR : m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("atalho.item_nome"));
        meta.lore(msg.list("atalho.item_lore"));
        meta.getPersistentDataContainer().set(keyShortcut, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        msg.send(p, "atalho.recebeu");
    }

    /** O item de atalho (para o Bedrock, que não tem a tecla de troca de mão): abre as skills equipadas. */
    @EventHandler
    public void onShortcut(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null || !event.getItem().hasItemMeta()
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !event.getItem().getItemMeta().getPersistentDataContainer().has(keyShortcut, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        SkillStore.Data d = store.data(p);
        if (d == null) {
            return;
        }
        Menu menu = Menu.of(msg.get("atalho.menu")).rows(1);
        for (int i = 0; i < SkillRules.ACTIVE_SLOTS; i++) {
            SkillDef def = engine.skill(d.active(i));
            if (def != null) {
                menu.item(Menu.Button.of(icon(def.icon(), Material.IRON_SWORD), msg.get("menu.skill", Messages.vars("skill", def.name())),
                        pl -> engine.cast(pl, def)).closing());
            }
        }
        if (menu.items().isEmpty()) {
            msg.send(p, "atalho.vazio");
            return;
        }
        AincradCore.get().menus().open(p, menu);
    }

    // ------------------------------------------------------------------ admin

    /** {@code /aincrad skill dar | unica | unica_limpar | teste | lista}. */
    private final class AdminCommand implements SubCommand {

        @Override
        public String name() {
            return "skill";
        }

        @Override
        public String permission() {
            return "aincrad.admin";
        }

        @Override
        public String usage() {
            return "skill dar|unica|unica_limpar|teste|lista ...";
        }

        @Override
        public Component description() {
            return msg.get("admin.descricao");
        }

        private void log(CommandSender actor, String action, String detail) {
            Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(actor, "skills", action, detail));
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "dar" -> {
                    Player t = args.length < 4 ? null : Bukkit.getPlayerExact(args[1]);
                    WeaponType w = args.length < 4 ? null : WeaponType.parse(args[2]);
                    SkillStore.Data d = t == null ? null : store.data(t);
                    Integer v = null;
                    try {
                        v = args.length < 4 ? null : Integer.parseInt(args[3]);
                    } catch (NumberFormatException ignored) {
                        // Cai no uso.
                    }
                    if (d == null || w == null || v == null || v < 0 || v > SkillRules.MAX_PROF) {
                        msg.send(sender, "admin.uso_dar");
                        return;
                    }
                    store.setProf(t.getUniqueId(), d, w, v);
                    msg.send(sender, "admin.dado", Messages.vars("jogador", t.getName(), "arma", msg.raw("armas." + w.key()), "valor", v));
                    log(sender, "skill.dar", t.getName() + " " + w.key() + " = " + v);
                }
                case "unica" -> {
                    Player t = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
                    if (t == null) {
                        msg.send(sender, "admin.uso_unica");
                        return;
                    }
                    String id = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : UniqueService.DUAL;
                    unique.grant(id, t.getUniqueId(), t.getName()).thenAccept(ok -> {
                        if (!ok) {
                            UniqueService.Holder h = unique.holder(id);
                            msg.send(sender, "admin.unica_tem_dono", Messages.vars("skill", id, "jogador", h == null ? "?" : h.name()));
                            return;
                        }
                        unique.announce(id, t.getName());
                        msg.send(sender, "admin.unica_dada", Messages.vars("skill", id, "jogador", t.getName()));
                        log(sender, "skill.unica", id + " para " + t.getName());
                    });
                }
                case "unica_limpar" -> {
                    String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : UniqueService.DUAL;
                    unique.clear(id).thenAccept(ok -> {
                        msg.send(sender, ok ? "admin.unica_limpa" : "admin.unica_sem_dono", Messages.vars("skill", id));
                        if (ok) {
                            log(sender, "skill.unica_limpar", id);
                        }
                    });
                }
                case "teste" -> {
                    if (unique.startTest()) {
                        msg.send(sender, "admin.teste_comecou");
                        log(sender, "skill.teste", UniqueService.DUAL);
                    } else {
                        msg.send(sender, "admin.teste_nao");
                    }
                }
                case "lista" -> {
                    msg.send(sender, "admin.lista", Messages.vars("qtd", engine.skills().size(),
                            "skills", String.join(", ", engine.skills().keySet())));
                    engine.errors().forEach(e -> msg.send(sender, "admin.erro", Messages.vars("erro", e)));
                }
                default -> msg.send(sender, "admin.uso");
            }
        }

        @Override
        public List<String> complete(CommandSender sender, String[] args) {
            List<String> opts = new ArrayList<>();
            if (args.length == 1) {
                opts.addAll(List.of("dar", "unica", "unica_limpar", "teste", "lista"));
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("dar") || args[0].equalsIgnoreCase("unica"))) {
                Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
                for (WeaponType t : WeaponType.values()) {
                    opts.add(t.key());
                }
            }
            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
            return opts;
        }
    }
}
