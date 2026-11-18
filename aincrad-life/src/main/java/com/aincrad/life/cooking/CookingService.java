package com.aincrad.life.cooking;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.station.PanelManager;
import com.aincrad.life.station.StationPanel;
import com.aincrad.life.station.StationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A culinária: lê os ingredientes e as receitas, abre a sessão de preparo na bancada de cozinha,
 * cria os pratos e dá o buff de quem come.
 */
public final class CookingService implements Listener {

    /** Tipo das receitas no "aprender antes de fazer" (o livro de receitas). */
    public static final String KIND = "receita";

    /** Os números do preparo (config.yml → culinaria), lidos no começo de cada sessão. */
    record Settings(int cutMarks, int cutRing, int cutPerfect, int cutGood,
                    int stirMarks, int stirRing, int stirPerfect, int stirGood,
                    int seasonMin, int seasonMax, int seasonLit, int seasonPerfect, int seasonGood,
                    int plateRing, int platePerfect, int plateGood,
                    int fireTicks, double fireBand, double fireDecay, double fireWood, double fireDamp, int fireBurn,
                    Quality.Rules rules, int maxPortions, boolean wrongConsumes, double specialBonus) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final ProfessionService professions;
    private final StationService stations;
    private final PanelManager panels;
    private final FoodBuffService buffs;
    private final NamespacedKey keyIngredient;
    private final NamespacedKey keyDish;
    private final NamespacedKey keyDishName;
    private final NamespacedKey keyQuality;
    private final NamespacedKey keyBuffType;
    private final NamespacedKey keyBuffValue;
    private final NamespacedKey keyBuffSeconds;
    private final Map<UUID, CookingSession> sessions = new HashMap<>();
    private RecipeRegistry registry = new RecipeRegistry();

    public CookingService(Plugin plugin, Messages msg, ConfigFile config, ProfessionService professions,
                          StationService stations, PanelManager panels, FoodBuffService buffs) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.professions = professions;
        this.stations = stations;
        this.panels = panels;
        this.buffs = buffs;
        this.keyIngredient = new NamespacedKey(plugin, "ingrediente");
        this.keyDish = new NamespacedKey(plugin, "prato");
        this.keyDishName = new NamespacedKey(plugin, "prato_nome");
        this.keyQuality = new NamespacedKey(plugin, "prato_qualidade");
        this.keyBuffType = new NamespacedKey(plugin, "prato_buff");
        this.keyBuffValue = new NamespacedKey(plugin, "prato_buff_valor");
        this.keyBuffSeconds = new NamespacedKey(plugin, "prato_buff_segundos");
    }

    /** Lê ingredients.yml e recipes.yml, registra as receitas como aprendizados e liga a bancada. */
    public void load() {
        List<String> errors = new ArrayList<>();
        registry = RecipeRegistry.parse(yaml("ingredients.yml"), yaml("recipes.yml"), errors);
        for (Recipe r : registry.recipes().values()) {
            Material m = Material.matchMaterial(r.item());
            if (m == null || !m.isEdible()) {
                errors.add("recipes.yml: " + r.id() + ": o item " + r.item() + " não é comestível (o prato sai como pão).");
            }
        }
        for (Ingredient i : registry.ingredients().values()) {
            if (Material.matchMaterial(i.material()) == null) {
                errors.add("ingredients.yml: " + i.id() + ": material desconhecido " + i.material() + ".");
            }
        }
        errors.forEach(e -> plugin.getLogger().warning(e));
        professions.learnables().removeIf(l -> l.kind().equals(KIND));
        for (Recipe r : registry.recipes().values()) {
            professions.register(new Learnable(KIND, r.id(), r.name(), msg.raw(r.pot() ? "cozinha.categoria_panela" : "cozinha.categoria"),
                    Profession.CULINARIA, r.studyMin(), r.produceMin(), false, r.path(), r.spec()));
        }
        stations.onUse(stationType(), this::use);
        plugin.getLogger().info("Culinária: " + registry.ingredients().size() + " ingredientes e " + registry.recipes().size() + " receitas.");
    }

    private YamlConfiguration yaml(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    /** O tipo de bancada (stations.yml) que serve de cozinha. */
    public String stationType() {
        return config.string("culinaria.estacao", "kitchen").toLowerCase(Locale.ROOT);
    }

    public RecipeRegistry registry() {
        return registry;
    }

    public FoodBuffService buffs() {
        return buffs;
    }

    Messages messages() {
        return msg;
    }

    ProfessionService professions() {
        return professions;
    }

    StationService stations() {
        return stations;
    }

    PanelManager panels() {
        return panels;
    }

    Learnable learnable(Recipe r) {
        return professions.learnable(KIND, r.id());
    }

    Settings settings() {
        String c = "culinaria.";
        return new Settings(
                config.integer(c + "cortar.marcas", 4, 1, 20), config.integer(c + "cortar.anel_ticks", 24, 6, 200),
                config.integer(c + "cortar.perfeita", 2, 0, 40), config.integer(c + "cortar.boa", 5, 0, 60),
                config.integer(c + "mexer.marcas", 5, 1, 20), config.integer(c + "mexer.anel_ticks", 18, 6, 200),
                config.integer(c + "mexer.perfeita", 2, 0, 40), config.integer(c + "mexer.boa", 5, 0, 60),
                config.integer(c + "temperar.espera_min_ticks", 20, 1, 600), config.integer(c + "temperar.espera_max_ticks", 70, 1, 600),
                config.integer(c + "temperar.aceso_ticks", 14, 6, 200), config.integer(c + "temperar.perfeita", 4, 0, 40),
                config.integer(c + "temperar.boa", 7, 0, 60),
                config.integer(c + "empratar.anel_ticks", 40, 6, 200), config.integer(c + "empratar.perfeita", 1, 0, 40),
                config.integer(c + "empratar.boa", 4, 0, 60),
                config.integer(c + "fogo.segundos", 8, 2, 120) * 20, config.decimal(c + "fogo.faixa", 0.24, 0.05, 0.9),
                config.decimal(c + "fogo.queda_por_tick", 0.006, 0, 0.2), config.decimal(c + "fogo.lenha", 0.12, 0.01, 1),
                config.decimal(c + "fogo.abafar", 0.10, 0.01, 1), config.integer(c + "fogo.queima_ticks", 60, 5, 2400),
                new Quality.Rules(config.decimal(c + "qualidade.queimado_abaixo", 0.25, 0, 1), config.decimal(c + "qualidade.bom_a_partir", 0.55, 0, 1),
                        config.decimal(c + "qualidade.excelente_a_partir", 0.85, 0, 1), config.decimal(c + "qualidade.chance_excelente_base", 0.5, 0, 1),
                        config.decimal(c + "qualidade.chance_por_100_de_folga", 0.15, 0, 1), config.decimal(c + "qualidade.bonus_empratar_perfeito", 0.2, 0, 1)),
                config.integer(c + "porcoes_maximo", 6, 1, 16), config.bool(c + "mistura_errada_consome", true),
                config.decimal(c + "bonus_especial", 0.1, 0, 1));
    }

    // ------------------------------------------------------------------ itens

    /** O ingrediente que um item representa (null: não é ingrediente). */
    public Ingredient identify(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return null;
        }
        if (it.hasItemMeta()) {
            PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(keyIngredient, PersistentDataType.STRING);
            if (id != null) {
                return registry.ingredient(id);
            }
            if (pdc.has(keyDish)) {
                return null;
            }
        }
        return registry.common(it.getType().name());
    }

    /** O item de um ingrediente: o próprio (com marca) ou o item comum do Minecraft. */
    public ItemStack ingredientItem(Ingredient i, int amount) {
        Material m = Material.matchMaterial(i.material());
        ItemStack it = new ItemStack(m == null ? Material.PAPER : m, Math.max(1, amount));
        if (!i.custom()) {
            return it;
        }
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("nome", i.name(), "rank", i.rank(), "encaixe", msg.raw("cozinha.encaixe." + i.slot().key()), "origem", i.origin());
        meta.displayName(msg.get("cozinha.ingrediente_nome", vars));
        meta.lore(msg.list(i.origin().isEmpty() ? "cozinha.ingrediente_lore_sem_origem" : "cozinha.ingrediente_lore", vars));
        meta.getPersistentDataContainer().set(keyIngredient, PersistentDataType.STRING, i.id());
        it.setItemMeta(meta);
        return it;
    }

    /**
     * O prato pronto. O buff fica gravado no item (tipo, valor e duração), já com a qualidade e
     * os bônus de quem cozinhou.
     *
     * @param recipe        null = "gororoba" (mistura que não deu certo)
     * @param durationBonus fração a mais na duração do buff (nós da árvore)
     * @param strengthBonus fração a mais no valor do buff
     */
    public ItemStack dish(Recipe recipe, Quality q, String cook, double durationBonus, double strengthBonus, int amount) {
        Material m = recipe == null ? Material.ROTTEN_FLESH : Material.matchMaterial(recipe.item());
        if (m == null || !m.isEdible()) {
            m = Material.BREAD;
        }
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        String name = recipe == null ? msg.raw("cozinha.gororoba") : recipe.name();
        int seconds = recipe == null ? 0 : FoodBuff.seconds(recipe.buff().minutes(), q, config.decimal("culinaria.duracao.comum", 0.8, 0, 10),
                config.decimal("culinaria.duracao.bom", 1.0, 0, 10), config.decimal("culinaria.duracao.excelente", 1.5, 0, 10), durationBonus);
        Component quality = msg.get("cozinha.qualidade." + q.key());
        Component effect;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (recipe != null && seconds > 0) {
            // Regeneração é em níveis: o bônus de força do cozinheiro não vira fração de nível.
            double value = recipe.buff().type() == FoodBuff.Type.REGENERACAO ? recipe.buff().value()
                    : Math.round(recipe.buff().value() * (1 + Math.max(0, strengthBonus)) * 10) / 10.0;
            pdc.set(keyBuffType, PersistentDataType.STRING, recipe.buff().type().name());
            pdc.set(keyBuffValue, PersistentDataType.DOUBLE, value);
            pdc.set(keyBuffSeconds, PersistentDataType.INTEGER, seconds);
            effect = Messages.parse(msg.raw("cozinha.buff_valor." + recipe.buff().type().key()).replace("<valor>", FoodBuffService.number(value))
                    + " <gray>" + msg.raw("cozinha.por_minutos").replace("<minutos>", String.valueOf(Math.max(1, Math.round(seconds / 60.0)))));
        } else {
            effect = msg.get("cozinha.sem_efeito");
        }
        var vars = Messages.vars("nome", name, "qualidade", quality, "efeito", effect, "cozinheiro", cook);
        meta.displayName(msg.get("cozinha.prato_nome", vars));
        meta.lore(msg.list("cozinha.prato_lore", vars));
        meta.setMaxStackSize(16);
        pdc.set(keyDish, PersistentDataType.STRING, recipe == null ? "gororoba" : recipe.id());
        pdc.set(keyDishName, PersistentDataType.STRING, name);
        pdc.set(keyQuality, PersistentDataType.STRING, q.name());
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, Math.min(16, amount)));
        return it;
    }

    /** Id da receita de um prato (null: não é prato, ou é gororoba). */
    public String dishId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String id = it.getItemMeta().getPersistentDataContainer().get(keyDish, PersistentDataType.STRING);
        return id == null || id.equals("gororoba") ? null : id;
    }

    /** A qualidade de um prato (null: não é prato). */
    public Quality dishQuality(ItemStack it) {
        return it == null || !it.hasItemMeta() ? null
                : Quality.parse(it.getItemMeta().getPersistentDataContainer().get(keyQuality, PersistentDataType.STRING));
    }

    static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    /** Comer um prato: dá o buff gravado nele (troca o que estava ativo). Queimado não dá nada. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        ItemStack it = event.getItem();
        if (!it.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(keyDish)) {
            return;
        }
        Player p = event.getPlayer();
        String name = pdc.getOrDefault(keyDishName, PersistentDataType.STRING, "?");
        FoodBuff.Type type = FoodBuff.Type.parse(pdc.get(keyBuffType, PersistentDataType.STRING));
        int seconds = pdc.getOrDefault(keyBuffSeconds, PersistentDataType.INTEGER, 0);
        if (type == null || seconds <= 0 || Quality.parse(pdc.get(keyQuality, PersistentDataType.STRING)) == Quality.QUEIMADO) {
            msg.send(p, "cozinha.comeu_sem_efeito", Messages.vars("prato", name));
            return;
        }
        FoodBuff buff = new FoodBuff(type, pdc.getOrDefault(keyBuffValue, PersistentDataType.DOUBLE, 0.0),
                System.currentTimeMillis() + seconds * 1000L, name);
        FoodBuff before = buffs.apply(p, buff);
        var vars = Messages.vars("prato", name, "efeito", Messages.parse(buffs.describe(buff)), "anterior", before == null ? "" : before.dish());
        msg.send(p, before == null ? "cozinha.buff_ganhou" : "cozinha.buff_trocou", vars);
    }

    // ------------------------------------------------------------------ bancada

    /** Clique na bancada de cozinha: abre o preparo; com o preparo aberto, começa a cozinhar. */
    private void use(Player p, StationService.Placed st) {
        CookingSession mine = sessions.get(p.getUniqueId());
        if (mine != null) {
            if (mine.station() == st) {
                if (p.isSneaking()) {
                    mine.cancel();
                } else {
                    mine.start();
                }
                return;
            }
            mine.cancel();
        }
        for (CookingSession s : sessions.values()) {
            if (s.station() == st) {
                msg.send(p, "cozinha.ocupada", Messages.vars("jogador", s.player().getName()));
                return;
            }
        }
        StationPanel panel = panels.open(p, st.base(), st.yaw, st.type.hologram());
        CookingSession session = new CookingSession(this, p, st, panel, settings());
        sessions.put(p.getUniqueId(), session);
        session.open();
    }

    void ended(CookingSession s) {
        sessions.remove(s.player().getUniqueId(), s);
        stations.setActive(s.station(), false);
    }

    /** Roda a cada tick: anda as etapas das sessões abertas. */
    public void tick() {
        for (CookingSession s : new ArrayList<>(sessions.values())) {
            s.tick();
        }
    }
}
