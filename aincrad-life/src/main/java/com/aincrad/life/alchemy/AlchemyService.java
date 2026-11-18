package com.aincrad.life.alchemy;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.life.alchemy.AlchemyMath.Purity;
import com.aincrad.life.alchemy.AlchemyRegistry.Formula;
import com.aincrad.life.alchemy.AlchemyRegistry.Infusion;
import com.aincrad.life.alchemy.AlchemyRegistry.Kind;
import com.aincrad.life.alchemy.AlchemyRegistry.Product;
import com.aincrad.life.alchemy.AlchemyRegistry.Reagent;
import com.aincrad.life.cooking.CookingService;
import com.aincrad.life.cooking.Ingredient;
import com.aincrad.life.fishing.FishingRegistry;
import com.aincrad.life.fishing.FishingService;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.smith.SmithService;
import com.aincrad.life.station.PanelManager;
import com.aincrad.life.station.StationPanel;
import com.aincrad.life.station.StationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * A alquimia: lê os reagentes e as fórmulas, abre o preparo na mesa de alquimia, entrega o
 * produto (poções pelo {@link PotionService}, insumos pelas outras profissões, cristais pelo
 * AincradItems) e mostra o livro do alquimista.
 */
public final class AlchemyService {

    /** Tipo das fórmulas no "aprender antes de fazer". */
    public static final String KIND = "formula";

    /** Os números do preparo (config.yml → alquimia), lidos no começo de cada sessão. */
    record Settings(int grindMarks, int grindRing, int grindGap, int grindPerfect, int grindGood, int colorTicks,
                    int distillTicks, double band, double decay, double heat, double cool, int explodeTicks,
                    double explodeDamage, double explodeRadius, int bottleRing, int bottlePerfect, int bottleGood,
                    AlchemyMath.Rules rules, boolean wrongConsumes) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final ProfessionService professions;
    private final StationService stations;
    private final PanelManager panels;
    private final PotionService potions;
    private final GatherService gather;
    private final Supplier<CookingService> cooking;
    private final Supplier<FishingService> fishing;
    private final Supplier<SmithService> smith;
    private final NamespacedKey keyReagent;
    private final Map<UUID, AlchemySession> sessions = new HashMap<>();
    private AlchemyRegistry registry = AlchemyRegistry.parse(null, null, new ArrayList<>());

    /**
     * @param cooking,fishing,smith as outras profissões, para os insumos (cada uma pode devolver null se o módulo está desligado)
     */
    public AlchemyService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, ProfessionService professions,
                          StationService stations, PanelManager panels, Supplier<CookingService> cooking,
                          Supplier<FishingService> fishing, Supplier<SmithService> smith) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.professions = professions;
        this.stations = stations;
        this.panels = panels;
        this.cooking = cooking;
        this.fishing = fishing;
        this.smith = smith;
        this.potions = new PotionService(plugin, msg, config);
        this.gather = new GatherService(plugin, msg, config, AincradCore.get().database(), professions, this);
        this.keyReagent = new NamespacedKey(plugin, "reagente");
    }

    /** Lê reagents.yml e formulas.yml, registra as fórmulas como aprendizados e liga a mesa. */
    public void load() {
        List<String> errors = new ArrayList<>();
        registry = AlchemyRegistry.parse(yaml("reagents.yml"), yaml("formulas.yml"), errors);
        for (Reagent r : registry.reagents().values()) {
            if (Material.matchMaterial(r.material()) == null) {
                errors.add("reagents.yml: " + r.id() + ": material desconhecido " + r.material() + ".");
            }
        }
        for (Formula f : registry.formulas().values()) {
            Product p = f.product();
            if (p.kind() == Kind.ANTIDOTO || p.kind() == Kind.BUFF || p.kind() == Kind.VENENO || p.kind() == Kind.ARREMESSAVEL) {
                for (String e : p.effect().split(",")) {
                    if (PotionService.effect(e) == null) {
                        errors.add("formulas.yml: " + f.id() + ": o efeito \"" + e + "\" não existe no Minecraft.");
                    }
                }
            }
        }
        errors.forEach(e -> plugin.getLogger().warning(e));
        professions.learnables().removeIf(l -> l.kind().equals(KIND));
        for (Formula f : registry.formulas().values()) {
            professions.register(new Learnable(KIND, f.id(), f.name(), msg.raw("alquimia.categoria." + f.product().kind().key()),
                    Profession.ALQUIMIA, f.study(), f.produce(), f.unique(), f.path(), f.spec()));
        }
        try {
            gather.loadBlocking();
        } catch (SQLException e) {
            plugin.getLogger().warning("Não foi possível ler os pontos de coleta: " + e.getMessage());
        }
        stations.onUse(stationType(), this::use);
        plugin.getLogger().info("Alquimia: " + registry.reagents().size() + " reagentes, " + registry.formulas().size() + " fórmulas e "
                + gather.nodes().size() + " pontos de coleta.");
    }

    private YamlConfiguration yaml(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    /** O tipo de bancada (stations.yml) que serve de mesa de alquimia. */
    public String stationType() {
        return config.string("alquimia.estacao", "alchemy_table").toLowerCase(Locale.ROOT);
    }

    public AlchemyRegistry registry() {
        return registry;
    }

    public PotionService potions() {
        return potions;
    }

    public GatherService gather() {
        return gather;
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

    Learnable learnable(Formula f) {
        return professions.learnable(KIND, f.id());
    }

    double mod(Player p, String key) {
        return professions.modifier(p, Profession.ALQUIMIA, key);
    }

    Settings settings() {
        String c = "alquimia.";
        String pu = c + "pureza.";
        return new Settings(config.integer(c + "moer.marcas", 5, 1, 20), config.integer(c + "moer.anel_ticks", 20, 6, 200),
                config.integer(c + "moer.intervalo_ticks", 6, 1, 200), config.integer(c + "moer.perfeita", 2, 0, 40),
                config.integer(c + "moer.boa", 5, 0, 60), config.integer(c + "infusao.cor_ticks", 24, 5, 400),
                config.integer(c + "destilar.segundos", 8, 2, 120) * 20, config.decimal(c + "destilar.faixa", 0.22, 0.05, 0.9),
                config.decimal(c + "destilar.queda_por_tick", 0.006, 0, 0.2), config.decimal(c + "destilar.aquecer", 0.12, 0.01, 1),
                config.decimal(c + "destilar.esfriar", 0.10, 0.01, 1), config.integer(c + "destilar.explode_segundos", 3, 1, 60) * 20,
                config.decimal(c + "destilar.explosao_dano", 4, 0, 100), config.decimal(c + "destilar.explosao_raio", 4, 0, 32),
                config.integer(c + "engarrafar.anel_ticks", 30, 6, 200), config.integer(c + "engarrafar.perfeita", 1, 0, 40),
                config.integer(c + "engarrafar.boa", 4, 0, 60),
                new AlchemyMath.Rules(config.decimal(pu + "moer_minimo", 35, 0, 100), config.decimal(pu + "moer_maximo", 60, 0, 100),
                        config.decimal(pu + "infusao_cor_certa", 8, 0, 100), config.decimal(pu + "infusao_cor_errada", 15, 0, 100),
                        config.decimal(pu + "destilar", 20, 0, 100), config.decimal(pu + "engarrafar_perfeito", 5, 0, 100),
                        config.decimal(pu + "engarrafar_bom", 2, 0, 100), config.decimal(pu + "engarrafar_ruim", -5, -100, 0),
                        config.decimal(pu + "por_100_de_proficiencia", 3, 0, 100), config.decimal(pu + "maximo_da_proficiencia", 10, 0, 100),
                        config.decimal(pu + "comum", 40, 0, 100), config.decimal(pu + "pura", 70, 0, 100), config.decimal(pu + "perfeita", 90, 0, 100)),
                config.bool(c + "mistura_errada_consome", true));
    }

    // ------------------------------------------------------------------ itens

    /** O reagente que um item representa (null: não é reagente). */
    public Reagent identify(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return null;
        }
        if (it.hasItemMeta()) {
            String id = it.getItemMeta().getPersistentDataContainer().get(keyReagent, PersistentDataType.STRING);
            if (id != null) {
                return registry.reagent(id);
            }
        }
        // Uma poção feita na mesa não serve de "água" para outra.
        return potions.isProduct(it) ? null : registry.common(it.getType().name());
    }

    public ItemStack reagentItem(Reagent r, int amount) {
        Material m = Material.matchMaterial(r.material());
        ItemStack it = new ItemStack(m == null ? Material.PAPER : m, Math.max(1, amount));
        if (!r.custom()) {
            return it;
        }
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("nome", r.name(), "encaixe", msg.raw("alquimia.encaixe." + r.slot().key()), "doses", r.doses(),
                "teto", msg.get("alquimia.pureza." + r.cap().key()));
        meta.displayName(msg.get("alquimia.reagente_nome", vars));
        meta.lore(msg.list(r.slot() == AlchemyRegistry.Slot.FRASCO ? "alquimia.frasco_lore" : "alquimia.reagente_lore", vars));
        meta.getPersistentDataContainer().set(keyReagent, PersistentDataType.STRING, r.id());
        it.setItemMeta(meta);
        return it;
    }

    static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    // ------------------------------------------------------------------ produto

    /**
     * Dá para entregar o produto desta fórmula neste servidor? (os insumos dependem do módulo da
     * outra profissão; os cristais, do AincradItems)
     *
     * @return a chave da mensagem de recusa, ou null se dá
     */
    String deliveryRefusal(Formula f) {
        Product p = f.product();
        if (p.kind() == Kind.CRISTAL) {
            return hooks.hasItems() ? null : "alquimia.sem_cristais";
        }
        if (p.kind() != Kind.INSUMO) {
            return null;
        }
        boolean ok = switch (p.effect()) {
            case "isca" -> fishing.get() != null && fishing.get().registry().bait(p.ref()) != null;
            case "ingrediente" -> cooking.get() != null && cooking.get().registry().ingredient(p.ref()) != null;
            default -> smith.get() != null;
        };
        return ok ? null : "alquimia.sem_modulo";
    }

    /**
     * Entrega o produto de um preparo.
     *
     * @param doses poções que saem do frasco (para insumos e cristais vale a quantidade da fórmula)
     * @return quantas unidades foram entregues
     */
    int deliver(Player p, Formula f, Purity purity, int doses) {
        Product pr = f.product();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        switch (pr.kind()) {
            case INSUMO -> {
                int qty = (int) Math.max(1, pr.value()) + (int) Math.max(0, mod(p, "insumos_extras")) + (purity == Purity.PERFEITA ? 1 : 0);
                ItemStack it = null;
                if (pr.effect().equals("isca") && fishing.get() != null) {
                    FishingRegistry.Bait bait = fishing.get().registry().bait(pr.ref());
                    it = bait == null ? null : fishing.get().baitItem(bait, qty);
                } else if (pr.effect().equals("ingrediente") && cooking.get() != null) {
                    Ingredient ing = cooking.get().registry().ingredient(pr.ref());
                    it = ing == null ? null : cooking.get().ingredientItem(ing, qty);
                } else if (pr.effect().equals("po_de_tempera") && smith.get() != null) {
                    it = smith.get().temperPowder(qty);
                }
                if (it == null) {
                    return 0;
                }
                give(p, it);
                return qty;
            }
            case CRISTAL -> {
                int qty = (int) Math.max(1, pr.value()) + (rnd.nextDouble() < mod(p, "cristal_duplo") ? 1 : 0);
                return hooks.giveCrystal(p, pr.ref(), qty) ? qty : 0;
            }
            default -> {
                double strength = switch (pr.kind()) {
                    case CURA -> mod(p, "forca_cura");
                    case OLEO -> mod(p, "forca_oleo");
                    default -> 0;
                };
                double duration = switch (pr.kind()) {
                    case VENENO -> mod(p, "duracao_veneno");
                    case ARREMESSAVEL -> mod(p, "duracao_frasco");
                    default -> 0;
                };
                give(p, potions.item(f, purity, doses, strength, duration, (int) mod(p, "golpes_extras"),
                        mod(p, "area_frasco"), mod(p, "sem_colateral") >= 1));
                return doses;
            }
        }
    }

    // ------------------------------------------------------------------ mesa

    /** Clique na mesa de alquimia: abre o preparo; com o preparo aberto, começa. */
    private void use(Player p, StationService.Placed st) {
        AlchemySession mine = sessions.get(p.getUniqueId());
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
        for (AlchemySession s : sessions.values()) {
            if (s.station() == st) {
                msg.send(p, "alquimia.ocupada", Messages.vars("jogador", s.player().getName()));
                return;
            }
        }
        StationPanel panel = panels.open(p, st.base(), st.yaw, st.type.hologram());
        AlchemySession session = new AlchemySession(this, p, st, panel, settings());
        sessions.put(p.getUniqueId(), session);
        session.open();
    }

    void ended(AlchemySession s) {
        sessions.remove(s.player().getUniqueId(), s);
        stations.setActive(s.station(), false);
    }

    /** Roda a cada tick: as sessões abertas e as curas em andamento. */
    public void tick() {
        for (AlchemySession s : new ArrayList<>(sessions.values())) {
            s.tick();
        }
        potions.tick();
    }

    // ------------------------------------------------------------------ livro

    /** O livro do alquimista: só as fórmulas aprendidas, com os reagentes, a ordem e as cores. */
    public void book(Player p) {
        Menu menu = Menu.of(msg.get("alquimia.livro_titulo")).rows(6);
        int known = 0;
        for (Formula f : registry.formulas().values()) {
            Learnable l = learnable(f);
            if (l == null || !professions.knows(p, l)) {
                continue;
            }
            known++;
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("alquimia.livro_reagentes", Messages.vars("base", name(f.base()), "principal", name(f.principal()),
                    "secundario", name(f.secondary()))));
            int n = 0;
            for (Infusion step : f.infusion()) {
                lore.add(msg.get("alquimia.livro_passo", Messages.vars("n", ++n,
                        "reagente", name(step.reagent() == AlchemyRegistry.Slot.PRINCIPAL ? f.principal() : f.secondary()),
                        "cor", msg.get("alquimia.cor." + step.color()))));
            }
            lore.add(msg.get("alquimia.livro_catalisador", Messages.vars("cor", msg.get("alquimia.cor." + f.catalystColor()))));
            lore.add(professions.canProduce(p, l) ? msg.get("menu.sabe_fazer")
                    : msg.get("menu.proficiencia_insuficiente", Messages.vars("producao", f.produce())));
            menu.item(Menu.Button.of(new ItemStack(icon(f.product().kind())), msg.get("menu.conhecido", Messages.vars("nome", f.name())),
                    pl -> { }).lore(lore));
        }
        if (known == 0) {
            msg.send(p, "alquimia.livro_vazio");
            return;
        }
        AincradCore.get().menus().open(p, menu);
    }

    private String name(String reagent) {
        Reagent r = registry.reagent(reagent);
        return r == null ? reagent : r.name();
    }

    private static Material icon(Kind k) {
        return switch (k) {
            case ARREMESSAVEL -> Material.SPLASH_POTION;
            case OLEO, VENENO -> Material.HONEY_BOTTLE;
            case INSUMO -> Material.BUNDLE;
            case CRISTAL -> Material.AMETHYST_SHARD;
            default -> Material.POTION;
        };
    }
}
