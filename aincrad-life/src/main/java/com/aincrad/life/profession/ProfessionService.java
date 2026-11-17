package com.aincrad.life.profession;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.life.api.ProfessionRankUpEvent;
import com.aincrad.life.data.LifeStore;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.model.TreeDef;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * O núcleo das profissões: proficiência (0 a 1000), "aprender antes de fazer" e a árvore de
 * especialização. Os módulos de cada profissão (culinária, pesca, ferreiro, alquimia) usam
 * {@link #practice}, {@link #canProduce} e {@link #modifier}.
 */
public final class ProfessionService implements Listener {

    private final Plugin plugin;
    private final Messages msg;
    private final LifeStore store;
    private final Hooks hooks;
    private final NamespacedKey keyLearn;
    private final NamespacedKey keyBlank;
    private final Map<Profession, TreeDef> trees = new EnumMap<>(Profession.class);
    private final Map<String, Learnable> learnables = new LinkedHashMap<>();
    private ConfigFile config;

    public ProfessionService(Plugin plugin, Messages msg, LifeStore store, Hooks hooks, ConfigFile config) {
        this.plugin = plugin;
        this.msg = msg;
        this.store = store;
        this.hooks = hooks;
        this.keyLearn = new NamespacedKey(plugin, "aprender");
        this.keyBlank = new NamespacedKey(plugin, "pergaminho_branco");
        reload(config);
    }

    /** Relê professions.yml, as árvores e os aprendizados de exemplo. */
    public void reload(ConfigFile c) {
        this.config = c;
        trees.clear();
        for (Profession p : Profession.values()) {
            File f = new File(plugin.getDataFolder(), "trees/" + p.key() + ".yml");
            if (!f.exists()) {
                plugin.saveResource("trees/" + p.key() + ".yml", false);
            }
            List<String> errors = new ArrayList<>();
            trees.put(p, TreeDef.parse(p, YamlConfiguration.loadConfiguration(f), errors));
            errors.forEach(e -> plugin.getLogger().warning(e));
        }
        learnables.values().removeIf(l -> l.kind().equals("exemplo"));
        ConfigurationSection ex = c.yaml().getConfigurationSection("exemplos_de_aprendizado");
        if (ex != null) {
            for (String id : ex.getKeys(false)) {
                ConfigurationSection s = ex.getConfigurationSection(id);
                Profession p = s == null ? null : Profession.parse(s.getString("profissao"));
                if (p != null) {
                    register(new Learnable("exemplo", id, s.getString("nome", id), s.getString("categoria", "Exemplo"), p,
                            s.getInt("estudo", 0), s.getInt("producao", 0), s.getBoolean("unico", false),
                            s.getString("caminho"), s.getString("especializacao")));
                }
            }
        }
    }

    public Messages messages() {
        return msg;
    }

    public ConfigFile config() {
        return config;
    }

    public TreeDef tree(Profession p) {
        return trees.get(p);
    }

    public LifeStore.PlayerLife data(Player p) {
        return store.get(p.getUniqueId());
    }

    public String name(Profession p) {
        return msg.raw("profissao." + p.key());
    }

    // ------------------------------------------------------------------ proficiência

    public double proficiency(Player p, Profession prof) {
        return data(p).proficiency.get(prof);
    }

    public Profession.Rank rank(Player p, Profession prof) {
        return Profession.Rank.of(proficiency(p, prof));
    }

    /**
     * O jogador fez uma ação da profissão (cozinhou, pescou, forjou...). Soma os pontos conforme
     * a dificuldade; falha dá a metade.
     *
     * @return pontos ganhos
     */
    public double practice(Player p, Profession prof, double difficulty, boolean success) {
        double now = proficiency(p, prof);
        double gain = Profession.gain(now, difficulty, success, config.decimal("ganho.maximo", 8, 0, 1000),
                config.decimal("ganho.faixa_facil", 200, 0, 1000), config.decimal("ganho.fator_falha", 0.5, 0, 1));
        if (gain <= 0) {
            return 0;
        }
        setProficiency(p, prof, now + gain, true);
        return gain;
    }

    /** Define a proficiência (admin e {@link #practice}). */
    public void setProficiency(Player p, Profession prof, double value, boolean announce) {
        double before = proficiency(p, prof);
        double after = Math.max(0, Math.min(Profession.MAX, value));
        data(p).proficiency.put(prof, after);
        store.saveProficiency(p.getUniqueId(), prof, after);
        Profession.Rank was = Profession.Rank.of(before);
        Profession.Rank is = Profession.Rank.of(after);
        if (announce && after > before) {
            msg.actionBar(p, "profissao.ganho", Messages.vars("profissao", name(prof), "pontos", fmt(after - before), "total", (int) after));
        }
        if (is.ordinal() > was.ordinal()) {
            var vars = Messages.vars("profissao", name(prof), "rank", msg.raw("rank." + is.key()));
            msg.title(p, "profissao.rank_titulo", Duration.ofSeconds(3), vars);
            msg.send(p, "profissao.rank", vars);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            plugin.getServer().getPluginManager().callEvent(new ProfessionRankUpEvent(p, prof.key(), is.key(), after));
        }
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }

    // ------------------------------------------------------------------ aprender antes de fazer

    /** Um módulo registra o que pode ser aprendido (receitas, projetos, fórmulas). */
    public void register(Learnable l) {
        learnables.put(l.key(), l);
    }

    public Learnable learnable(String kind, String id) {
        return learnables.get(kind.toLowerCase(Locale.ROOT) + ":" + id.toLowerCase(Locale.ROOT));
    }

    public Collection<Learnable> learnables() {
        return learnables.values();
    }

    public boolean knows(Player p, Learnable l) {
        return data(p).known.contains(l.key());
    }

    /** Conhece o item e tem a proficiência de produção? */
    public boolean canProduce(Player p, Learnable l) {
        return l.canProduce(proficiency(p, l.profession()), data(p).known);
    }

    public Learnable.Check canLearn(Player p, Learnable l) {
        LifeStore.PlayerLife d = data(p);
        return l.canLearn(d.proficiency.get(l.profession()), d.known, d.tree.get(l.profession()));
    }

    /** Ensina sem conferir nada (admin, quests, descoberta por experimento). */
    public void teach(Player p, Learnable l) {
        if (data(p).known.add(l.key())) {
            store.saveKnown(p.getUniqueId(), l.kind(), l.id());
        }
    }

    public void forget(Player p, Learnable l) {
        if (data(p).known.remove(l.key())) {
            store.deleteKnown(p.getUniqueId(), l.kind(), l.id());
        }
    }

    /** O item que ensina (pergaminho, livro ou planta, conforme o tipo). */
    public ItemStack scroll(Learnable l) {
        Material m = switch (l.kind()) {
            case "receita" -> Material.BOOK;
            case "projeto" -> Material.MAP;
            default -> Material.PAPER;
        };
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("nome", l.name(), "profissao", name(l.profession()), "categoria", l.category(),
                "estudo", l.studyMin(), "producao", l.produceMin());
        meta.displayName(msg.get("saber.item_nome", vars));
        meta.lore(msg.list("saber.item_lore", vars));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyLearn, PersistentDataType.STRING, l.key());
        it.setItemMeta(meta);
        return it;
    }

    /** Pergaminho em branco, usado para transcrever. */
    public ItemStack blankScroll(int amount) {
        ItemStack it = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("saber.branco_nome"));
        meta.lore(msg.list("saber.branco_lore"));
        meta.getPersistentDataContainer().set(keyBlank, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isBlank(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(keyBlank);
    }

    /** Usar o item que ensina: confere a proficiência de estudo e o caminho; só some se ensinou. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUseScroll(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String key = hand.hasItemMeta() ? hand.getItemMeta().getPersistentDataContainer().get(keyLearn, PersistentDataType.STRING) : null;
        if (key == null) {
            return;
        }
        event.setCancelled(true);
        Learnable l = learnables.get(key);
        if (l == null) {
            msg.send(p, "saber.desconhecido");
            return;
        }
        var vars = Messages.vars("nome", l.name(), "profissao", name(l.profession()), "estudo", l.studyMin(), "producao", l.produceMin());
        switch (canLearn(p, l)) {
            case JA_SABE -> msg.send(p, "saber.ja_sabe", vars);
            case PROFICIENCIA -> msg.send(p, "saber.falta_estudo", vars);
            case CAMINHO_FECHADO -> msg.send(p, "saber.caminho_fechado", vars);
            case OK -> {
                hand.setAmount(hand.getAmount() - 1);
                teach(p, l);
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                msg.send(p, canProduce(p, l) ? "saber.aprendeu" : "saber.aprendeu_sem_producao", vars);
            }
        }
    }

    /**
     * Transcreve algo que o jogador conhece para um pergaminho em branco (rank Mestre, custo em Col).
     *
     * @return o item, ou null (com o motivo já avisado)
     */
    public ItemStack transcribe(Player p, Learnable l) {
        var vars = Messages.vars("nome", l.name(), "profissao", name(l.profession()));
        if (!knows(p, l)) {
            msg.send(p, "saber.nao_sabe", vars);
            return null;
        }
        if (l.unique()) {
            msg.send(p, "saber.unico", vars);
            return null;
        }
        if (rank(p, l.profession()).ordinal() < Profession.Rank.MESTRE.ordinal()) {
            msg.send(p, "saber.precisa_mestre", vars);
            return null;
        }
        ItemStack blank = null;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (isBlank(it)) {
                blank = it;
                break;
            }
        }
        if (blank == null) {
            msg.send(p, "saber.sem_pergaminho");
            return null;
        }
        long cost = config.integer("transcricao.custo_col", 2000, 0, Integer.MAX_VALUE);
        Material fallback = Material.matchMaterial(config.string("sem_economia.item", "GOLD_INGOT"));
        long per = config.integer("sem_economia.col_por_item", 100, 1, Integer.MAX_VALUE);
        if (!hooks.charge(p, cost, fallback == null ? Material.GOLD_INGOT : fallback, per, "life:transcrever")) {
            msg.send(p, "saber.sem_col", Messages.vars("preco", hooks.price(cost, fallback == null ? Material.GOLD_INGOT : fallback, per)));
            return null;
        }
        blank.setAmount(blank.getAmount() - 1);
        ItemStack copy = scroll(l);
        p.getInventory().addItem(copy).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        msg.send(p, "saber.transcreveu", vars);
        return copy;
    }

    // ------------------------------------------------------------------ árvore

    public Set<String> owned(Player p, Profession prof) {
        return data(p).tree.get(prof);
    }

    public int freePoints(Player p, Profession prof) {
        return tree(prof).free(proficiency(p, prof), owned(p, prof));
    }

    private void take(Player p, Profession prof, String entry) {
        if (owned(p, prof).add(entry)) {
            store.saveNode(p.getUniqueId(), prof, entry);
        }
    }

    public TreeDef.Deny choosePath(Player p, Profession prof, String path) {
        TreeDef.Deny d = tree(prof).canChoosePath(path, proficiency(p, prof), owned(p, prof));
        if (d == TreeDef.Deny.OK) {
            take(p, prof, "caminho:" + path.toLowerCase(Locale.ROOT));
        }
        return d;
    }

    public TreeDef.Deny chooseSpec(Player p, Profession prof, String spec) {
        TreeDef.Deny d = tree(prof).canChooseSpec(spec, proficiency(p, prof), owned(p, prof));
        if (d == TreeDef.Deny.OK) {
            take(p, prof, "espec:" + spec.toLowerCase(Locale.ROOT));
        }
        return d;
    }

    public TreeDef.Deny buyNode(Player p, Profession prof, String node) {
        TreeDef.Deny d = tree(prof).canBuy(node, proficiency(p, prof), owned(p, prof));
        if (d == TreeDef.Deny.OK) {
            take(p, prof, node.toLowerCase(Locale.ROOT));
        }
        return d;
    }

    /** Admin: desfaz as escolhas e devolve os pontos. */
    public void resetTree(Player p, Profession prof) {
        owned(p, prof).clear();
        store.clearTree(p.getUniqueId(), prof);
    }

    /**
     * Soma do efeito {@code key} nos nós que o jogador comprou (ex.: "janela_perfeita"). As sessões
     * de forja, preparo, pesca e alquimia consultam isto.
     */
    public double modifier(Player p, Profession prof, String key) {
        return tree(prof).modifier(key, owned(p, prof));
    }

    /** Texto do motivo de uma recusa da árvore. */
    public Component deny(TreeDef.Deny d) {
        return msg.get("arvore.recusa." + d.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ entrada e saída

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            store.loadBlocking(event.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("Não foi possível carregar as profissões de " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        store.forget(event.getPlayer().getUniqueId());
    }
}
