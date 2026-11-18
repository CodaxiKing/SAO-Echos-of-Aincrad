package com.aincrad.quests;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.RegionEnterEvent;
import com.aincrad.quests.api.QuestCompleteEvent;
import com.aincrad.quests.hook.Hooks;
import com.aincrad.quests.model.QuestDef;
import com.aincrad.quests.model.QuestDef.Goal;
import com.aincrad.quests.model.QuestDef.Stage;
import com.aincrad.quests.model.QuestRules;
import com.aincrad.quests.model.QuestRules.Advance;
import com.aincrad.quests.model.QuestRules.Progress;
import com.aincrad.quests.model.QuestRules.State;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * O motor das quests: lê {@code quests/*.yml}, guarda o progresso de cada jogador no banco do
 * core, conta os objetivos (matar, coletar, entregar, falar, chegar, chefe, pescar, cozinhar) e
 * paga a recompensa na entrega.
 */
public final class QuestService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS quest_progress (uuid VARCHAR(36) NOT NULL, quest VARCHAR(64) NOT NULL, "
                    + "state VARCHAR(8) NOT NULL, stage INT NOT NULL, amount INT NOT NULL, done_at BIGINT NOT NULL, "
                    + "times INT NOT NULL, PRIMARY KEY (uuid, quest))");

    /** Arquivos de quest que vêm no jar. */
    public static final List<String> BUNDLED = List.of("exemplo");

    /** O que o NPC tem para o jogador. */
    public enum NpcStatus { NADA, DISPONIVEL, PRONTA }

    private static final double PARTY_RANGE = 64;

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private final Hooks hooks;
    private final Map<UUID, Map<String, Progress>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, String> tracked = new HashMap<>();
    private Map<String, QuestDef> quests = Map.of();
    private List<String> errors = List.of();

    public QuestService(Plugin plugin, Database db, Messages msg, Hooks hooks) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.hooks = hooks;
    }

    // ------------------------------------------------------------------ arquivos e dados

    public void reload() {
        File dir = new File(plugin.getDataFolder(), "quests");
        for (String name : BUNDLED) {
            if (!new File(dir, name + ".yml").exists()) {
                plugin.saveResource("quests/" + name + ".yml", false);
            }
        }
        Map<String, QuestDef> all = new LinkedHashMap<>();
        List<String> errs = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                all.putAll(QuestDef.parse(YamlConfiguration.loadConfiguration(f), f.getName(), errs));
            }
        }
        for (QuestDef d : all.values()) {
            if (d.after() != null && !all.containsKey(d.after())) {
                errs.add(d.id() + ": a quest anterior '" + d.after() + "' não existe");
            }
        }
        quests = all;
        errors = List.copyOf(errs);
        errs.forEach(e -> plugin.getLogger().warning("quests/" + e));
        plugin.getLogger().info(quests.size() + " quests carregadas.");
    }

    public Map<String, QuestDef> quests() {
        return quests;
    }

    public List<String> errors() {
        return errors;
    }

    public QuestDef quest(String id) {
        return id == null ? null : quests.get(id.toLowerCase(Locale.ROOT));
    }

    /** O progresso do jogador (quest → progresso), ou null enquanto carrega. */
    public Map<String, Progress> data(Player p) {
        return cache.get(p.getUniqueId());
    }

    public void load(UUID id) {
        db.query("SELECT quest, state, stage, amount, done_at, times FROM quest_progress WHERE uuid = ?",
                        rs -> Map.entry(rs.getString(1), new Progress("FEITA".equals(rs.getString(2)) ? State.FEITA : State.ATIVA,
                                rs.getInt(3), rs.getInt(4), rs.getLong(5), rs.getInt(6))), id.toString())
                .thenAccept(list -> AincradCore.get().runSync(() -> {
                    if (Bukkit.getPlayer(id) != null) {
                        Map<String, Progress> m = new HashMap<>();
                        list.forEach(e -> m.put(e.getKey(), e.getValue()));
                        cache.put(id, m);
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível carregar as quests de " + id, ex);
                    return null;
                });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
        tracked.remove(event.getPlayer().getUniqueId());
    }

    private void put(Player p, String quest, Progress pr) {
        Map<String, Progress> m = data(p);
        if (m == null) {
            return;
        }
        m.put(quest, pr);
        String sql = db.upsert("quest_progress", List.of("uuid", "quest"), List.of("state", "stage", "amount", "done_at", "times"));
        db.update(sql, p.getUniqueId().toString(), quest, pr.state().name(), pr.stage(), pr.count(), pr.doneAt(), pr.times())
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível gravar a quest " + quest + " de " + p.getName(), ex);
                    return 0;
                });
    }

    private void remove(Player p, String quest) {
        Map<String, Progress> m = data(p);
        if (m != null) {
            m.remove(quest);
        }
        db.update("DELETE FROM quest_progress WHERE uuid = ? AND quest = ?", p.getUniqueId().toString(), quest);
    }

    private Set<String> done(Map<String, Progress> m) {
        Set<String> out = new HashSet<>();
        m.forEach((k, v) -> {
            if (v.times() > 0) {
                out.add(k);
            }
        });
        return out;
    }

    public QuestRules.Block blocked(Player p, QuestDef def) {
        Map<String, Progress> m = data(p);
        if (m == null) {
            return QuestRules.Block.ATIVA;
        }
        return QuestRules.blocked(def, m.get(def.id()), System.currentTimeMillis(), ZoneId.systemDefault(),
                hooks.level(p), hooks.frontline(), done(m));
    }

    // ------------------------------------------------------------------ aceitar, avançar, entregar

    public boolean accept(Player p, QuestDef def) {
        Map<String, Progress> m = data(p);
        QuestRules.Block b = blocked(p, def);
        if (m == null || b != null) {
            if (b != null) {
                msg.send(p, "quest.bloqueio." + b.name().toLowerCase(Locale.ROOT), Messages.vars("level", def.minLevel(),
                        "andar", def.floor(), "quest", def.after() == null || quest(def.after()) == null ? "" : quest(def.after()).name()));
            }
            return false;
        }
        put(p, def.id(), Progress.started(m.get(def.id())));
        tracked.putIfAbsent(p.getUniqueId(), def.id());
        msg.send(p, "quest.aceita", Messages.vars("quest", def.name()));
        msg.sendRaw(p, "quest.objetivo", Messages.vars("texto", text(def, 0), "feito", 0, "total", def.stages().get(0).amount()));
        return true;
    }

    /** Com a HUD do AincradUI mostrando o rastreio, a barra de ação fica livre. */
    private boolean hudTracker;

    public void useHudTracker(boolean on) {
        hudTracker = on;
    }

    /** A linha da quest rastreada, para a HUD. Null se o jogador não rastreia nada em andamento. */
    public net.kyori.adventure.text.Component trackerLine(Player p) {
        Map<String, Progress> m = data(p);
        QuestDef t = m == null ? null : quests.get(tracked.getOrDefault(p.getUniqueId(), ""));
        Progress pr = t == null ? null : m.get(t.id());
        if (pr == null || pr.state() != State.ATIVA) {
            return null;
        }
        boolean ready = QuestRules.ready(t, pr);
        return msg.get("quest.rastreio", Messages.vars("quest", t.name(), "texto", text(t, pr.stage()),
                "feito", ready ? 1 : pr.count(), "total", ready ? 1 : t.stages().get(pr.stage()).amount()));
    }

    /** O texto da etapa (o do arquivo, ou um montado a partir do objetivo). */
    public String text(QuestDef def, int stage) {
        if (stage >= def.stages().size()) {
            return msg.raw("quest.voltar").replace("<npc>", def.npc());
        }
        Stage s = def.stages().get(stage);
        if (!s.text().isBlank()) {
            return s.text();
        }
        return msg.raw("objetivo." + s.goal().name().toLowerCase(Locale.ROOT))
                .replace("<alvo>", s.target() == null ? msg.raw("objetivo.qualquer") : s.target())
                .replace("<quantidade>", String.valueOf(s.amount()));
    }

    private void applied(Player p, QuestDef def, Advance a) {
        if (a.step() == QuestRules.Step.NADA) {
            return;
        }
        Progress pr = a.progress();
        put(p, def.id(), pr);
        switch (a.step()) {
            case AVANCOU -> msg.actionBar(p, "quest.progresso", Messages.vars("quest", def.name(), "texto", text(def, pr.stage()),
                    "feito", pr.count(), "total", def.stages().get(pr.stage()).amount()));
            case ETAPA_CONCLUIDA -> {
                msg.send(p, "quest.etapa", Messages.vars("quest", def.name()));
                msg.sendRaw(p, "quest.objetivo", Messages.vars("texto", text(def, pr.stage()), "feito", 0,
                        "total", def.stages().get(pr.stage()).amount()));
            }
            case PRONTA -> msg.send(p, "quest.pronta", Messages.vars("quest", def.name(), "npc", def.npc()));
            default -> {
            }
        }
    }

    /**
     * Algo aconteceu com o jogador. Conta para as quests ativas dele cuja etapa atual pede isso;
     * nas quests de party, matar e chefe contam também para os membros por perto.
     */
    public void happened(Player source, Goal goal, int amount, String... targets) {
        count(source, goal, amount, false, targets);
        if (goal != Goal.MATAR && goal != Goal.CHEFE) {
            return;
        }
        for (UUID id : hooks.party(source.getUniqueId())) {
            Player member = id.equals(source.getUniqueId()) ? null : Bukkit.getPlayer(id);
            if (member == null) {
                continue;
            }
            boolean near = goal == Goal.CHEFE || (member.getWorld().equals(source.getWorld())
                    && member.getLocation().distanceSquared(source.getLocation()) <= PARTY_RANGE * PARTY_RANGE);
            if (near) {
                count(member, goal, amount, true, targets);
            }
        }
    }

    /** Como {@link #happened}, mas só para o próprio jogador (quando todos os envolvidos já são avisados um a um). */
    public void happenedSolo(Player p, Goal goal, int amount, String... targets) {
        count(p, goal, amount, false, targets);
    }

    private void count(Player p, Goal goal, int amount, boolean partyOnly, String... targets) {
        Map<String, Progress> m = data(p);
        if (m == null) {
            return;
        }
        for (var e : new ArrayList<>(m.entrySet())) {
            QuestDef def = quests.get(e.getKey());
            Progress pr = e.getValue();
            if (def == null || pr.state() != State.ATIVA || pr.stage() >= def.stages().size() || (partyOnly && !def.party())) {
                continue;
            }
            if (def.stages().get(pr.stage()).matches(goal, targets)) {
                applied(p, def, QuestRules.advance(def, pr, amount));
            }
        }
    }

    private static int has(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** A cada 2 segundos: as etapas de "coletar" olham o inventário; a quest rastreada vai para a tela. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Progress> m = data(p);
            if (m == null) {
                continue;
            }
            for (var e : new ArrayList<>(m.entrySet())) {
                QuestDef def = quests.get(e.getKey());
                Progress pr = e.getValue();
                if (def == null || pr.state() != State.ATIVA || pr.stage() >= def.stages().size()) {
                    continue;
                }
                Stage s = def.stages().get(pr.stage());
                Material mat = s.goal() == Goal.COLETAR ? Material.matchMaterial(s.target()) : null;
                if (mat != null) {
                    applied(p, def, QuestRules.setCount(def, pr, has(p, mat)));
                }
            }
            QuestDef t = quests.get(tracked.getOrDefault(p.getUniqueId(), ""));
            Progress pr = t == null ? null : m.get(t.id());
            if (pr != null && pr.state() == State.ATIVA) {
                boolean ready = QuestRules.ready(t, pr);
                if (!hudTracker) {
                    msg.actionBar(p, "quest.rastreio", Messages.vars("quest", t.name(), "texto", text(t, pr.stage()),
                            "feito", ready ? 1 : pr.count(), "total", ready ? 1 : t.stages().get(pr.stage()).amount()));
                }
            }
        }
    }

    /** O jogador falou com o NPC: conta "falar" e "entregar", e abre o diálogo. */
    public void talk(Player p, String npc, Component npcName, String greeting) {
        Map<String, Progress> m = data(p);
        if (m == null) {
            msg.send(p, "quest.carregando");
            return;
        }
        happened(p, Goal.FALAR, 1, npc);
        // Entregar: os itens saem do inventário quando o jogador tem todos.
        for (var e : new ArrayList<>(m.entrySet())) {
            QuestDef def = quests.get(e.getKey());
            Progress pr = e.getValue();
            if (def == null || !def.npc().equals(npc) || pr.state() != State.ATIVA || pr.stage() >= def.stages().size()) {
                continue;
            }
            Stage s = def.stages().get(pr.stage());
            Material mat = s.goal() == Goal.ENTREGAR ? Material.matchMaterial(s.target()) : null;
            if (mat == null) {
                continue;
            }
            if (has(p, mat) < s.amount()) {
                msg.send(p, "quest.faltam_itens", Messages.vars("quest", def.name(), "item", mat.name(), "quantidade", s.amount()));
                continue;
            }
            p.getInventory().removeItem(new ItemStack(mat, s.amount()));
            applied(p, def, QuestRules.advance(def, pr, s.amount()));
        }

        Menu menu = Menu.of(npcName).rows(3);
        long now = System.currentTimeMillis();
        for (QuestDef def : quests.values()) {
            if (!def.npc().equals(npc)) {
                continue;
            }
            Progress pr = m.get(def.id());
            List<Component> lore = new ArrayList<>();
            if (!def.description().isBlank()) {
                lore.add(msg.get("dialogo.descricao", Messages.vars("descricao", def.description())));
            }
            lore.add(msg.get("dialogo.recompensa", Messages.vars("xp", def.reward().xp(), "col", def.reward().col())));
            if (QuestRules.ready(def, pr)) {
                lore.add(msg.get("dialogo.clique_entregar"));
                menu.item(Menu.Button.of(new ItemStack(Material.EMERALD), msg.get("dialogo.pronta", Messages.vars("quest", def.name())),
                        pl -> complete(pl, def)).closing().lore(lore));
            } else if (pr != null && pr.state() == State.ATIVA) {
                lore.add(msg.get("quest.objetivo", Messages.vars("texto", text(def, pr.stage()), "feito", pr.count(),
                        "total", def.stages().get(pr.stage()).amount())));
                menu.item(Menu.Button.of(new ItemStack(Material.BOOK), msg.get("dialogo.em_andamento", Messages.vars("quest", def.name())),
                        pl -> { }).lore(lore));
            } else if (QuestRules.blocked(def, pr, now, ZoneId.systemDefault(), hooks.level(p), hooks.frontline(), done(m)) == null) {
                lore.add(msg.get("dialogo.clique_aceitar"));
                menu.item(Menu.Button.of(new ItemStack(Material.WRITABLE_BOOK), msg.get("dialogo.disponivel", Messages.vars("quest", def.name())),
                        pl -> accept(pl, def)).closing().lore(lore));
            }
        }
        if (greeting != null && !greeting.isBlank()) {
            p.sendMessage(npcName.append(Messages.parse("<gray>: </gray>")).append(Messages.parse(greeting)));
        }
        if (!menu.items().isEmpty()) {
            AincradCore.get().menus().open(p, menu);
        }
    }

    /** O que o NPC tem para o jogador (o ícone sobre a cabeça dele). */
    public NpcStatus status(Player p, String npc) {
        Map<String, Progress> m = data(p);
        if (m == null) {
            return NpcStatus.NADA;
        }
        NpcStatus best = NpcStatus.NADA;
        long now = System.currentTimeMillis();
        Set<String> done = null;
        for (QuestDef def : quests.values()) {
            Progress pr = m.get(def.id());
            if (pr != null && pr.state() == State.ATIVA && pr.stage() < def.stages().size()) {
                Stage s = def.stages().get(pr.stage());
                // Alguém com quem falar ou a quem entregar também chama a atenção.
                if ((s.goal() == Goal.FALAR && npc.equalsIgnoreCase(s.target())) || (s.goal() == Goal.ENTREGAR && def.npc().equals(npc))) {
                    return NpcStatus.PRONTA;
                }
            }
            if (!def.npc().equals(npc)) {
                continue;
            }
            if (QuestRules.ready(def, pr)) {
                return NpcStatus.PRONTA;
            }
            if (best == NpcStatus.NADA) {
                if (done == null) {
                    done = done(m);
                }
                if (QuestRules.blocked(def, pr, now, ZoneId.systemDefault(), hooks.level(p), hooks.frontline(), done) == null) {
                    best = NpcStatus.DISPONIVEL;
                }
            }
        }
        return best;
    }

    /** Entrega a quest: paga a recompensa e marca como feita. */
    public boolean complete(Player p, QuestDef def) {
        Map<String, Progress> m = data(p);
        Progress pr = m == null ? null : m.get(def.id());
        if (!QuestRules.ready(def, pr)) {
            return false;
        }
        finish(p, def, pr);
        return true;
    }

    private void finish(Player p, QuestDef def, Progress pr) {
        Progress doneNow = QuestRules.completed(pr, System.currentTimeMillis());
        put(p, def.id(), doneNow);
        if (def.id().equals(tracked.get(p.getUniqueId()))) {
            tracked.remove(p.getUniqueId());
        }
        QuestDef.Reward r = def.reward();
        if (r.xp() > 0 && !hooks.xp(p, r.xp(), "quest")) {
            p.giveExp((int) Math.min(Integer.MAX_VALUE, r.xp()));
        }
        boolean colPaid = r.col() <= 0 || hooks.col(p, r.col(), "quest: " + def.id());
        for (String spec : r.items()) {
            String[] parts = QuestRules.item(spec);
            ItemStack it = null;
            if (parts != null && parts[0].toLowerCase(Locale.ROOT).startsWith("rpg:")) {
                it = hooks.rpgItem(parts[0].substring(4));
            } else if (parts != null) {
                Material mat = Material.matchMaterial(parts[0]);
                it = mat == null || !mat.isItem() ? null : new ItemStack(mat);
            }
            if (it == null) {
                plugin.getLogger().warning("Quest " + def.id() + ": item de recompensa inválido: " + spec);
                continue;
            }
            int left = Integer.parseInt(parts[1]);
            while (left > 0) {
                ItemStack stack = it.clone();
                stack.setAmount(Math.min(left, Math.max(1, it.getMaxStackSize())));
                left -= stack.getAmount();
                p.getInventory().addItem(stack).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            }
        }
        if (r.title() != null && hooks.has("AincradReputation")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "aincrad titulo dar " + p.getName() + " " + r.title());
        }
        for (String cmd : r.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("<jogador>", p.getName()));
        }
        hooks.socialQuestDone(p.getUniqueId());
        msg.send(p, "quest.concluida", Messages.vars("quest", def.name(), "xp", r.xp(), "col", colPaid ? r.col() : 0));
        p.playSound(p.getLocation(), "entity.player.levelup", 0.8f, 1.2f);
        Bukkit.getPluginManager().callEvent(new QuestCompleteEvent(p, def.id(), def.name(),
                def.kind().name().toLowerCase(Locale.ROOT), doneNow.times()));
    }

    // ------------------------------------------------------------------ diário e admin

    public void track(Player p, QuestDef def) {
        tracked.put(p.getUniqueId(), def.id());
        msg.send(p, "quest.rastreando", Messages.vars("quest", def.name()));
    }

    public String tracked(Player p) {
        return tracked.get(p.getUniqueId());
    }

    public boolean abandon(Player p, QuestDef def) {
        Map<String, Progress> m = data(p);
        Progress pr = m == null ? null : m.get(def.id());
        if (pr == null || pr.state() != State.ATIVA) {
            return false;
        }
        if (pr.times() > 0) {
            put(p, def.id(), new Progress(State.FEITA, 0, 0, pr.doneAt(), pr.times()));
        } else {
            remove(p, def.id());
        }
        tracked.remove(p.getUniqueId(), def.id());
        return true;
    }

    /** Admin: dá a quest sem olhar os requisitos. */
    public boolean give(Player p, QuestDef def) {
        Map<String, Progress> m = data(p);
        if (m == null) {
            return false;
        }
        put(p, def.id(), Progress.started(m.get(def.id())));
        return true;
    }

    /** Admin: conclui a quest e paga a recompensa, esteja onde estiver. */
    public boolean forceComplete(Player p, QuestDef def) {
        Map<String, Progress> m = data(p);
        if (m == null) {
            return false;
        }
        finish(p, def, m.get(def.id()));
        return true;
    }

    /** Admin: apaga o progresso (a quest volta a poder ser pega do zero). */
    public boolean reset(Player p, QuestDef def) {
        if (data(p) == null) {
            return false;
        }
        remove(p, def.id());
        tracked.remove(p.getUniqueId(), def.id());
        return true;
    }

    // ------------------------------------------------------------------ objetivos que vêm do core e do Minecraft

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        Player killer = e.getKiller();
        if (killer == null || e instanceof Player) {
            return;
        }
        happened(killer, Goal.MATAR, 1, e.getType().name(), hooks.mobId(e));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegion(RegionEnterEvent event) {
        happened(event.getPlayer(), Goal.CHEGAR, 1, event.region().name());
    }
}
