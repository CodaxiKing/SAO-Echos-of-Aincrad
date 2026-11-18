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
import org.bukkit.Bukkit;
import com.aincrad.core.AincradCore;
import com.aincrad.life.station.StationType;
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

    /** Um número dos efeitos da árvore (config.yml → culinaria.arvore). */
    double treeValue(String key, double def) {
        return config.decimal("culinaria.arvore." + key, def, 0, 100_000);
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
        return dish(recipe, q, cook, durationBonus, strengthBonus, amount, DishPerks.NONE);
    }

    /** O prato pronto, com o que a árvore do cozinheiro pôs a mais nele. */
    public ItemStack dish(Recipe recipe, Quality q, String cook, double durationBonus, double strengthBonus, int amount, DishPerks perks) {
        Material m = recipe == null ? Material.ROTTEN_FLESH : Material.matchMaterial(recipe.item());
        if (m == null || !m.isEdible()) {
            m = Material.BREAD;
        }
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        String name = recipe == null ? msg.raw("cozinha.gororoba") : recipe.name();
        int base = recipe == null ? 0 : FoodBuff.seconds(recipe.buff().minutes(), q, config.decimal("culinaria.duracao.comum", 0.8, 0, 10),
                config.decimal("culinaria.duracao.bom", 1.0, 0, 10), config.decimal("culinaria.duracao.excelente", 1.5, 0, 10), durationBonus);
        int seconds = base <= 0 ? 0 : Math.max(base, perks.minSeconds());
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
        List<Component> lore = new ArrayList<>(msg.list("cozinha.prato_lore", vars));
        for (String perk : perks.labels()) {
            lore.add(msg.get("cozinha.perk." + perk));
        }
        meta.lore(lore);
        int stack = perks.stack64() ? 64 : 16;
        meta.setMaxStackSize(stack);
        // O que a árvore do cozinheiro pôs no prato: vale para quem comer.
        if (perks.regen()) {
            pdc.set(perk("regeneracao"), PersistentDataType.INTEGER, 1);
        }
        if (perks.second() != null) {
            pdc.set(perk("segundo_tipo"), PersistentDataType.STRING, perks.second().name());
            pdc.set(perk("segundo_valor"), PersistentDataType.DOUBLE, perks.secondValue());
        }
        if (perks.shareRadius() > 0) {
            pdc.set(perk("compartilha"), PersistentDataType.INTEGER, perks.shareRadius());
        }
        if (perks.instant()) {
            pdc.set(perk("rancho"), PersistentDataType.INTEGER, 1);
        }
        if (perks.twoBites()) {
            pdc.set(perk("despensa"), PersistentDataType.INTEGER, 1);
        }
        if (perks.extraSlot()) {
            pdc.set(perk("nectar"), PersistentDataType.INTEGER, 1);
        }
        if (perks.dayFood()) {
            pdc.set(perk("pao_do_viajante"), PersistentDataType.INTEGER, 1);
        }
        pdc.set(keyDish, PersistentDataType.STRING, recipe == null ? "gororoba" : recipe.id());
        pdc.set(keyDishName, PersistentDataType.STRING, name);
        pdc.set(keyQuality, PersistentDataType.STRING, q.name());
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, Math.min(stack, amount)));
        return it;
    }

    private NamespacedKey perk(String name) {
        return new NamespacedKey(plugin, "prato_" + name);
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
        eat(p, pdc, buff, name);
        // Despensa: a conserva excelente rende duas refeições (a segunda já sem a marca).
        if (pdc.has(perk("despensa"))) {
            ItemStack again = it.clone();
            again.setAmount(1);
            ItemMeta meta = again.getItemMeta();
            meta.getPersistentDataContainer().remove(perk("despensa"));
            again.setItemMeta(meta);
            Bukkit.getScheduler().runTask(plugin, () -> give(p, again));
            msg.send(p, "cozinha.perk_despensa_sobrou");
        }
    }

    /** O efeito de comer o prato: o buff e o que a árvore do cozinheiro pôs a mais. */
    private void eat(Player p, PersistentDataContainer pdc, FoodBuff buff, String name) {
        boolean nectar = pdc.has(perk("nectar"));
        FoodBuff before = nectar ? buffs.applyExtra(p, buff) : buffs.apply(p, buff);
        var vars = Messages.vars("prato", name, "efeito", Messages.parse(buffs.describe(buff)), "anterior", before == null ? "" : before.dish());
        msg.send(p, nectar ? "cozinha.buff_nectar" : before == null ? "cozinha.buff_ganhou" : "cozinha.buff_trocou", vars);
        long millis = Math.max(0, buff.expires() - System.currentTimeMillis());
        // Banquete Real: o segundo buff fica no lugar extra, pelo mesmo tempo.
        FoodBuff.Type second = FoodBuff.Type.parse(pdc.get(perk("segundo_tipo"), PersistentDataType.STRING));
        if (second != null && !nectar) {
            buffs.applyExtra(p, new FoodBuff(second, pdc.getOrDefault(perk("segundo_valor"), PersistentDataType.DOUBLE, 0.0), buff.expires(), name));
        }
        if (pdc.has(perk("regeneracao"))) {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION,
                    (int) (treeValue("regeneracao_segundos", 30) * 20), 0, true, false, true));
        }
        // Pão do Viajante: sem fome por um dia do Minecraft.
        if (pdc.has(perk("pao_do_viajante"))) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
            AincradCore.get().profile(p).set(plugin, "sem_fome_ate", System.currentTimeMillis() + (long) (treeValue("sem_fome_minutos", 20) * 60_000L));
        }
        // O festim de um Chef de Banquete alcança quem está por perto, por parte do tempo.
        int radius = pdc.getOrDefault(perk("compartilha"), PersistentDataType.INTEGER, 0);
        if (radius > 0) {
            long shared = (long) (millis * Math.min(1, treeValue("compartilhar_fracao", 0.5)));
            for (Player o : p.getWorld().getPlayers()) {
                if (!o.equals(p) && o.getLocation().distanceSquared(p.getLocation()) <= (double) radius * radius && buffs.current(o) == null) {
                    buffs.apply(o, new FoodBuff(buff.type(), buff.value(), System.currentTimeMillis() + shared, name));
                    msg.send(o, "cozinha.buff_compartilhado", Messages.vars("prato", name, "jogador", p.getName()));
                }
            }
        }
    }

    /** Rancho: o prato do cozinheiro de campanha é comido na hora, com um clique, mesmo em combate. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onQuickEat(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        ItemStack it = event.getItem();
        if (it == null || !it.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(keyDish) || !pdc.has(perk("rancho"))) {
            return;
        }
        FoodBuff.Type type = FoodBuff.Type.parse(pdc.get(keyBuffType, PersistentDataType.STRING));
        int seconds = pdc.getOrDefault(keyBuffSeconds, PersistentDataType.INTEGER, 0);
        if (type == null || seconds <= 0) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        String name = pdc.getOrDefault(keyDishName, PersistentDataType.STRING, "?");
        eat(p, pdc, new FoodBuff(type, pdc.getOrDefault(keyBuffValue, PersistentDataType.DOUBLE, 0.0),
                System.currentTimeMillis() + seconds * 1000L, name), name);
        p.setFoodLevel(Math.min(20, p.getFoodLevel() + 6));
        it.setAmount(it.getAmount() - 1);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EAT, 1f, 1f);
    }

    /** Pão do Viajante: enquanto vale, a fome não desce. */
    @EventHandler(ignoreCancelled = true)
    public void onHunger(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && event.getFoodLevel() < p.getFoodLevel()
                && AincradCore.get().profile(p).getLong(plugin, "sem_fome_ate", 0) > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ fogueira (cozinha de campanha)

    /** Bancadas de mentira sobre fogueiras, enquanto alguém cozinha nelas. */
    private final Map<String, StationService.Placed> campfires = new HashMap<>();

    /**
     * Cozinha de campanha: quem tem o nó cozinha em qualquer fogueira, com a mão vazia. O primeiro
     * clique abre o preparo, o seguinte começa a cozinhar; agachado, desiste.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCampfire(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Material m = event.getClickedBlock().getType();
        if (m != Material.CAMPFIRE && m != Material.SOUL_CAMPFIRE) {
            return;
        }
        Player p = event.getPlayer();
        CookingSession mine = sessions.get(p.getUniqueId());
        boolean here = mine != null && campfires.containsValue(mine.station());
        boolean emptyHand = p.getInventory().getItemInMainHand().getType().isAir();
        if (!here && (!emptyHand || professions.modifier(p, Profession.CULINARIA, "desbloqueia_fogueira") < 1)) {
            return;
        }
        StationType type = stations.type(stationType());
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        org.bukkit.block.Block b = event.getClickedBlock();
        String key = b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
        StationService.Placed spot = campfires.computeIfAbsent(key, k -> stations.virtual(type, b, p.getLocation().getYaw()));
        use(p, spot);
        campfires.values().removeIf(v -> sessions.values().stream().noneMatch(sess -> sess.station() == v));
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
