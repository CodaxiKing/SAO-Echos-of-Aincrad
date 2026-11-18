package com.aincrad.life.smith;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.model.TreeDef;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.smith.ForgeRegistry.Blueprint;
import com.aincrad.life.smith.ForgeRegistry.Mat;
import com.aincrad.life.smith.ForgeRegistry.Named;
import com.aincrad.life.smith.ForgeRegistry.Rarity;
import com.aincrad.life.smith.ForgedItems.Upgrade;
import com.aincrad.life.station.PanelManager;
import com.aincrad.life.station.StationPanel;
import com.aincrad.life.station.StationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O ferreiro: a bigorna de forja abre o reparo, a forja e o aprimoramento. A forja e o
 * aprimoramento acontecem sobre a bigorna, no {@link StationPanel}; a fornalha é o fole e o
 * barril é a têmpera. Também cuida dos materiais e do martelo como itens, do golpe crítico dos
 * itens forjados e do aviso de durabilidade.
 */
public final class SmithService implements Listener {

    /** Tipo dos projetos no "aprender antes de fazer" (a planta). */
    public static final String KIND = "projeto";

    /** Os números do ferreiro (config.yml → ferreiro), lidos no começo de cada trabalho. */
    record Settings(int ringBase, int ringPerRank, int ringMin, int perfect, int good, int perfectPer, int hitGap,
                    double heatStart, double heatLow, double heatHigh, double heatDecay, double heatPerHit, double bellows,
                    int maxCracks, double failLoss, int temperTicks, double temperBonus, double temperPenalty, Rarity temperFrom,
                    double[] thresholds, int triesBase, double triesExtraFrom, int triesExtra,
                    int upSlots, int upHits, int upRing, double upBase, double upAtMaster, double upPerLevel, double upPerMaterial,
                    double upPerHit, double upCap, String upPath) {
    }

    /** Um trabalho aberto sobre a bigorna. */
    interface Session {
        Player player();

        StationService.Placed station();

        /** Clique na bigorna. */
        void click(boolean sneaking);

        void tick();

        /** Clique na fornalha: o fole. */
        void bellows();

        /** Clique no barril: a têmpera. */
        void quench();
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final ProfessionService professions;
    private final StationService stations;
    private final PanelManager panels;
    private final ForgedItems forged;
    private final NamespacedKey keyMat;
    private final NamespacedKey keyHammer;
    private final NamespacedKey keyPowder;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private ForgeRegistry registry = ForgeRegistry.parse(null, null, new ArrayList<>());

    public SmithService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, ProfessionService professions,
                        StationService stations, PanelManager panels) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.professions = professions;
        this.stations = stations;
        this.panels = panels;
        this.forged = new ForgedItems(plugin, msg);
        this.keyMat = new NamespacedKey(plugin, "material_forja");
        this.keyHammer = new NamespacedKey(plugin, "martelo");
        this.keyPowder = new NamespacedKey(plugin, "po_de_tempera");
    }

    /** Lê ingots.yml e forge.yml, registra os projetos como aprendizados e liga as bancadas. */
    public void load() {
        List<String> errors = new ArrayList<>();
        registry = ForgeRegistry.parse(yaml("ingots.yml"), yaml("forge.yml"), errors);
        for (Mat m : registry.mats().values()) {
            if (Material.matchMaterial(m.material()) == null) {
                errors.add("ingots.yml: " + m.id() + ": material desconhecido " + m.material() + ".");
            }
        }
        for (Blueprint b : registry.blueprints().values()) {
            Material m = Material.matchMaterial(b.material());
            if (m == null || m.getMaxDurability() <= 0) {
                errors.add("forge.yml: projeto " + b.id() + ": " + b.material() + " não é um item com durabilidade (arma, armadura ou escudo).");
            }
        }
        errors.forEach(e -> plugin.getLogger().warning(e));
        professions.learnables().removeIf(l -> l.kind().equals(KIND));
        for (Blueprint b : registry.blueprints().values()) {
            professions.register(new Learnable(KIND, b.id(), b.name(), b.type().name(), Profession.FERREIRO, b.study(), b.produce(),
                    b.unique(), b.path(), b.spec()));
        }
        Map<Upgrade, Double> perLevel = new EnumMap<>(Upgrade.class);
        double[] defaults = {1, 2, 2, 0.2, 5};
        for (Upgrade u : Upgrade.values()) {
            perLevel.put(u, config.decimal("ferreiro.aprimoramento.por_nivel." + u.key(), defaults[u.ordinal()], 0, 1000));
        }
        forged.perLevel(perLevel);
        stations.onUse(config.string("ferreiro.estacao_bigorna", "forge_anvil").toLowerCase(Locale.ROOT), this::useAnvil);
        stations.onUse(config.string("ferreiro.estacao_fornalha", "forge_furnace").toLowerCase(Locale.ROOT), (p, st) -> {
            Session s = sessions.get(p.getUniqueId());
            if (s == null) {
                msg.send(p, "forja.fole_sem_forja");
            } else {
                s.bellows();
            }
        });
        stations.onUse(quenchType(), (p, st) -> {
            Session s = sessions.get(p.getUniqueId());
            if (s == null) {
                msg.send(p, "forja.barril_sem_forja");
            } else {
                s.quench();
            }
        });
        plugin.getLogger().info("Ferreiro: " + registry.mats().size() + " materiais, " + registry.blueprints().size() + " projetos e "
                + registry.named().size() + " armas nomeadas.");
    }

    private YamlConfiguration yaml(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    private String quenchType() {
        return config.string("ferreiro.estacao_barril", "quench_barrel").toLowerCase(Locale.ROOT);
    }

    public ForgeRegistry registry() {
        return registry;
    }

    public ForgedItems forged() {
        return forged;
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

    PanelManager panelManager() {
        return panels;
    }

    double mod(Player p, String key) {
        return professions.modifier(p, Profession.FERREIRO, key);
    }

    /** Um número dos efeitos da árvore (config.yml → ferreiro.arvore). */
    double treeValue(String key, double def) {
        return config.decimal("ferreiro.arvore." + key, def, 0, 100);
    }

    Settings settings() {
        String f = "ferreiro.forja.";
        String a = "ferreiro.aprimoramento.";
        Rarity temperFrom = Rarity.parse(config.string(f + "tempera.a_partir_de", "raro"));
        return new Settings(config.integer(f + "anel_ticks", 26, 6, 200), config.integer(f + "anel_por_rank", 3, 0, 40),
                config.integer(f + "anel_minimo", 10, 4, 200), config.integer(f + "perfeita", 2, 0, 40), config.integer(f + "boa", 5, 0, 60),
                config.integer(f + "perfeita_mais_1_a_cada", 400, 50, 100_000), config.integer(f + "intervalo_ticks", 10, 1, 200),
                config.decimal(f + "calor.inicio", 0.75, 0, 1), config.decimal(f + "calor.ideal_de", 0.45, 0, 1),
                config.decimal(f + "calor.ideal_ate", 0.85, 0, 1), config.decimal(f + "calor.queda_por_tick", 0.0015, 0, 0.1),
                config.decimal(f + "calor.queda_por_batida", 0.035, 0, 1), config.decimal(f + "calor.fole", 0.12, 0.01, 1),
                config.integer(f + "trincas", 3, 1, 50), config.decimal(f + "perda_na_falha", 0.5, 0, 1),
                config.integer(f + "tempera.segundos", 3, 1, 60) * 20, config.decimal(f + "tempera.bonus_durabilidade", 15, 0, 1000),
                config.decimal(f + "tempera.perda_qualidade", 10, 0, 100), temperFrom == null ? Rarity.RARO : temperFrom,
                new double[]{config.decimal(f + "raridade.incomum", 40, 0, 100), config.decimal(f + "raridade.raro", 60, 0, 100),
                        config.decimal(f + "raridade.epico", 75, 0, 100), config.decimal(f + "raridade.lendario", 90, 0, 100)},
                config.integer(a + "tentativas", 8, 0, 100), config.decimal(f + "tentativas_extras_a_partir_de", 90, 0, 101),
                config.integer(f + "tentativas_extras", 2, 0, 100),
                config.integer(a + "encaixes_de_material", 3, 0, 5), config.integer(a + "batidas", 5, 1, 30),
                config.integer(a + "anel_ticks", 22, 6, 200), config.decimal(a + "chance_base", 0.5, 0, 1),
                config.decimal(a + "chance_no_mestre", 0.3, 0, 1), config.decimal(a + "queda_por_nivel", 0.06, 0, 1),
                config.decimal(a + "por_material", 0.1, 0, 1), config.decimal(a + "por_batida_perfeita", 0.02, 0, 1),
                config.decimal(a + "teto", 0.95, 0.05, 1), config.string(a + "acima_de_5_exige_caminho", "refinador").toLowerCase(Locale.ROOT));
    }

    /** O maior nível de um atributo que o ferreiro consegue dar, pelo rank (config → aprimoramento.limite). */
    int upgradeLimit(Player p) {
        Profession.Rank r = professions.rank(p, Profession.FERREIRO);
        int[] defaults = {0, 0, 5, 10, 10};
        return config.integer("ferreiro.aprimoramento.limite." + r.key(), defaults[r.ordinal()], 0, 100);
    }

    /** O material extra que aumenta a chance de um atributo. */
    Material upgradeMaterial(Upgrade u) {
        String[] defaults = {"FLINT", "FEATHER", "ENDER_EYE", "IRON_BLOCK", "OBSIDIAN"};
        Material m = Material.matchMaterial(config.string("ferreiro.aprimoramento.material." + u.key(), defaults[u.ordinal()]));
        return m == null ? Material.FLINT : m;
    }

    // ------------------------------------------------------------------ itens

    /** O material de forja que um item representa (null: não é). */
    public Mat identify(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return null;
        }
        if (it.hasItemMeta()) {
            String id = it.getItemMeta().getPersistentDataContainer().get(keyMat, PersistentDataType.STRING);
            if (id != null) {
                return registry.mat(id);
            }
        }
        return registry.common(it.getType().name());
    }

    public ItemStack matItem(Mat m, int amount) {
        Material type = Material.matchMaterial(m.material());
        ItemStack it = new ItemStack(type == null ? Material.IRON_INGOT : type, Math.max(1, amount));
        if (!m.custom()) {
            return it;
        }
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("nome", m.name(), "rank", m.rank(), "classe", msg.raw("forja.classe." + m.clazz()));
        meta.displayName(msg.get("forja.material_nome", vars));
        meta.lore(msg.list("forja.material_lore", vars));
        meta.getPersistentDataContainer().set(keyMat, PersistentDataType.STRING, m.id());
        it.setItemMeta(meta);
        return it;
    }

    /** O martelo de ferreiro: com ele na mão, a bigorna abre a oficina. */
    public ItemStack hammer() {
        Material type = Material.matchMaterial(config.string("ferreiro.martelo_item", "MACE"));
        ItemStack it = new ItemStack(type == null ? Material.IRON_PICKAXE : type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("forja.martelo_nome"));
        meta.lore(msg.list("forja.martelo_lore"));
        meta.getPersistentDataContainer().set(keyHammer, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    /** Pó de têmpera (feito pelo alquimista): com ele no inventário, o ferreiro tem mais tempo na têmpera. */
    public ItemStack temperPowder(int amount) {
        return temperPowder(amount, 0);
    }

    /** @param stronger quanto este pó alarga a mais a janela (0.5 = +50%; a árvore do alquimista) */
    public ItemStack temperPowder(int amount, double stronger) {
        ItemStack it = new ItemStack(Material.SUGAR, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("forja.po_nome"));
        meta.lore(msg.list("forja.po_lore"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyPowder, PersistentDataType.BYTE, (byte) 1);
        if (stronger > 0) {
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "po_forca"), PersistentDataType.DOUBLE, stronger);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Gasta um pó de têmpera do inventário. @return quanto a janela da têmpera aumenta (0 = não tinha) */
    double takeTemperPowder(Player p) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(keyPowder)) {
                double stronger = it.getItemMeta().getPersistentDataContainer().getOrDefault(new org.bukkit.NamespacedKey(plugin, "po_forca"), PersistentDataType.DOUBLE, 0.0);
                it.setAmount(it.getAmount() - 1);
                return config.decimal("ferreiro.forja.tempera.po_de_tempera", 1.0, 0, 10) * (1 + stronger);
            }
        }
        return 0;
    }

    private boolean holdsHammer(Player p) {
        if (!config.bool("ferreiro.exigir_martelo", true)) {
            return true;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        return hand.hasItemMeta() && hand.getItemMeta().getPersistentDataContainer().has(keyHammer);
    }

    static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    /** O item forjado: a base (do AincradRPG, se o projeto pedir e ele estiver ligado) mais os bônus dos materiais. */
    ItemStack build(Blueprint bp, Rarity rarity, Named named, Map<String, Double> stats, int quality, int tries, Player smith) {
        ItemStack it = bp.rpgItem() == null ? null : hooks.rpgItem(bp.rpgItem(), rarity.key());
        if (it == null) {
            Material m = Material.matchMaterial(bp.material());
            it = new ItemStack(m == null ? Material.IRON_SWORD : m);
        }
        forged.stamp(it, bp.id(), named == null ? bp.name() : named.name(), msg.raw("forja.raridade_cor." + rarity.key()),
                stats, quality, tries, smith.getName());
        return it;
    }

    // ------------------------------------------------------------------ bigorna

    private void useAnvil(Player p, StationService.Placed st) {
        Session mine = sessions.get(p.getUniqueId());
        if (mine != null) {
            if (mine.station() == st) {
                mine.click(p.isSneaking());
                return;
            }
            panels.close(p, true);
        }
        for (Session s : sessions.values()) {
            if (s.station() == st) {
                msg.send(p, "forja.ocupada", Messages.vars("jogador", s.player().getName()));
                return;
            }
        }
        if (!holdsHammer(p)) {
            msg.send(p, "forja.sem_martelo");
            return;
        }
        Menu menu = Menu.of(msg.get("forja.menu_titulo")).rows(3);
        menu.item(Menu.Button.of(new ItemStack(Material.ANVIL), msg.get("forja.menu_forjar"), pl -> projectMenu(pl, st)).closing()
                .lore(msg.list("forja.menu_forjar_lore")));
        menu.item(Menu.Button.of(new ItemStack(Material.IRON_INGOT), msg.get("forja.menu_reparar"), this::repair).closing()
                .lore(msg.list("forja.menu_reparar_lore")));
        menu.item(Menu.Button.of(new ItemStack(Material.NETHER_STAR), msg.get("forja.menu_aprimorar"), pl -> upgradeMenu(pl, st)).closing()
                .lore(msg.list("forja.menu_aprimorar_lore")));
        AincradCore.get().menus().open(p, menu);
    }

    /** A lista de projetos: os que o ferreiro conhece e, como "???", os que ainda não aprendeu. */
    private void projectMenu(Player p, StationService.Placed st) {
        Menu menu = Menu.of(msg.get("forja.projetos_titulo")).rows(6);
        for (Blueprint b : registry.blueprints().values()) {
            Learnable l = professions.learnable(KIND, b.id());
            if (l == null) {
                continue;
            }
            if (!professions.knows(p, l)) {
                menu.item(Menu.Button.of(new ItemStack(Material.GRAY_DYE), msg.get("menu.desconhecido", Messages.vars("categoria", b.type().name())),
                        pl -> { }).lore(List.of(msg.get("menu.desconhecido_lore"))));
                continue;
            }
            Material icon = Material.matchMaterial(b.material());
            boolean can = professions.canProduce(p, l);
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("forja.projeto_tipo", Messages.vars("tipo", b.type().name(), "encaixes", slotNames(b))));
            lore.add(can ? msg.get("forja.projeto_clique") : msg.get("menu.proficiencia_insuficiente", Messages.vars("producao", b.produce())));
            menu.item(Menu.Button.of(new ItemStack(icon == null ? Material.IRON_SWORD : icon), msg.get("menu.conhecido", Messages.vars("nome", b.name())), pl -> {
                if (professions.canProduce(pl, l)) {
                    openForge(pl, st, b);
                }
            }).closing().lore(lore));
        }
        AincradCore.get().menus().open(p, menu);
    }

    private String slotNames(Blueprint b) {
        List<String> out = new ArrayList<>();
        b.type().slots().forEach(r -> out.add(msg.raw("forja.encaixe." + r.key())));
        return String.join(", ", out);
    }

    private boolean free(Player p, StationService.Placed st) {
        for (Session s : sessions.values()) {
            if (s.station() == st && s.player() != p) {
                msg.send(p, "forja.ocupada", Messages.vars("jogador", s.player().getName()));
                return false;
            }
        }
        Location base = st.base();
        return base != null && p.getWorld().equals(base.getWorld()) && p.getLocation().distanceSquared(base) <= 36;
    }

    private void openForge(Player p, StationService.Placed st, Blueprint b) {
        if (!free(p, st)) {
            return;
        }
        StationPanel panel = panels.open(p, st.base(), st.yaw, st.type.hologram());
        ForgeSession s = new ForgeSession(this, p, st, panel, b, settings());
        sessions.put(p.getUniqueId(), s);
        s.open();
    }

    /** Os pontos dos encaixes: os da bancada, se batem com a quantidade; senão, um arco sobre ela. */
    static List<double[]> slotPoints(StationService.Placed st, int n) {
        if (st.type.slots().size() == n) {
            return st.type.slots();
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double x = n == 1 ? 0 : -0.95 + 1.9 * i / (n - 1);
            out.add(new double[]{x, 1.6 + 0.35 * (1 - (x / 0.95) * (x / 0.95)), 0});
        }
        return out;
    }

    /** Há uma bancada do tipo por perto? (o barril de têmpera, para a forja) */
    boolean quenchNear(Location at) {
        String type = quenchType();
        for (StationService.Placed p : stations.placed().values()) {
            Location b = p.type.id().equals(type) ? p.base() : null;
            if (b != null && b.getWorld().equals(at.getWorld()) && b.distanceSquared(at) <= 64) {
                return true;
            }
        }
        return false;
    }

    void ended(Session s) {
        sessions.remove(s.player().getUniqueId(), s);
        stations.setActive(s.station(), false);
    }

    /** Roda a cada tick. */
    public void tick() {
        for (Session s : new ArrayList<>(sessions.values())) {
            s.tick();
        }
    }

    // ------------------------------------------------------------------ reparo

    private static int maxDamage(ItemStack it) {
        return it.getItemMeta() instanceof Damageable d && d.hasMaxDamage() ? d.getMaxDamage() : it.getType().getMaxDurability();
    }

    private static int damage(ItemStack it) {
        return it.hasItemMeta() && it.getItemMeta() instanceof Damageable d ? d.getDamage() : 0;
    }

    /** O material que repara um item, pelo começo do nome do item (config → reparo.materiais). */
    Material repairMaterial(Material item) {
        ConfigurationSection s = config.yaml().getConfigurationSection("ferreiro.reparo.materiais");
        if (s != null) {
            for (String prefix : s.getKeys(false)) {
                if (item.name().startsWith(prefix.toUpperCase(Locale.ROOT))) {
                    Material m = Material.matchMaterial(String.valueOf(s.get(prefix)));
                    if (m != null) {
                        return m;
                    }
                }
            }
        }
        Material def = Material.matchMaterial(config.string("ferreiro.reparo.material_padrao", "IRON_INGOT"));
        return def == null ? Material.IRON_INGOT : def;
    }

    /** O custo de reparar o item agora, para este ferreiro. */
    ForgeMath.RepairCost repairCost(Player p, ItemStack it) {
        int max = Math.max(1, maxDamage(it));
        return ForgeMath.repair(damage(it) / (double) max, config.integer("ferreiro.reparo.col", 300, 0, Integer.MAX_VALUE),
                config.integer("ferreiro.reparo.unidades", 4, 0, 64), professions.proficiency(p, Profession.FERREIRO),
                config.decimal("ferreiro.reparo.desconto_no_mestre", 0.5, 0, 0.9), -mod(p, "custo_reparo"), mod(p, "material_reparo"));
    }

    /**
     * O ferreiro NPC das cidades ({@code /reparo}, ou um NPC com esse comando): repara o item da
     * mão só por Col, pelo preço cheio vezes {@code ferreiro.npc.preco_x}, sem pedir material e
     * sem dar proficiência. Itens de raridade alta (config) ele recusa: esses são do Refinador.
     */
    public void npcRepair(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType().isAir() || maxDamage(it) <= 0 || damage(it) <= 0) {
            msg.send(p, "forja.npc_nada");
            return;
        }
        String rarity = hooks.rpgRarity(it);
        if (rarity != null && config.yaml().getStringList("ferreiro.npc.recusa_raridades").stream().anyMatch(rarity::equalsIgnoreCase)) {
            msg.send(p, "forja.npc_recusa");
            return;
        }
        double wear = damage(it) / (double) Math.max(1, maxDamage(it));
        long price = Math.max(1, Math.round(wear * config.integer("ferreiro.reparo.col", 300, 0, Integer.MAX_VALUE)
                * config.decimal("ferreiro.npc.preco_x", 1.5, 1, 100)));
        ItemStack snapshot = it.clone();
        Material coin = Material.matchMaterial(config.string("sem_economia.item", "GOLD_INGOT"));
        Material fallback = coin == null ? Material.GOLD_INGOT : coin;
        long perItem = config.integer("sem_economia.col_por_item", 100, 1, Integer.MAX_VALUE);
        var vars = Messages.vars("preco", hooks.price(price, fallback, perItem));
        AincradCore.get().menus().confirm(p, msg.get("forja.npc_confirmar", vars), yes -> {
            ItemStack now = yes.getInventory().getItemInMainHand();
            if (!now.equals(snapshot)) {
                msg.send(yes, "forja.item_mudou");
                return;
            }
            if (!hooks.charge(yes, price, fallback, perItem, "life:reparo_npc")) {
                msg.send(yes, "forja.reparo_sem_col", vars);
                return;
            }
            ItemMeta meta = now.getItemMeta();
            ((Damageable) meta).setDamage(0);
            now.setItemMeta(meta);
            yes.getInventory().setItemInMainHand(now);
            yes.playSound(yes.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 0.9f);
            msg.send(yes, "forja.npc_reparado", vars);
        }, no -> { });
    }

    /** Reparo: o item vai na outra mão (o martelo está na principal). Mostra o custo e pede confirmação. */
    private void repair(Player p) {
        ItemStack it = p.getInventory().getItemInOffHand();
        if (!ForgedItems.workable(it) || damage(it) <= 0) {
            msg.send(p, ForgedItems.workable(it) ? "forja.reparo_inteiro" : "forja.item_na_outra_mao");
            return;
        }
        // Itens épicos e lendários só o Restaurador sabe reparar.
        String rpgRarity = hooks.rpgRarity(it);
        boolean fine = forged.quality(it) >= config.integer("ferreiro.reparo.qualidade_fina", 75, 0, 100)
                || "epico".equals(rpgRarity) || "lendario".equals(rpgRarity);
        if (fine && mod(p, "desbloqueia_reparo_epico") < 1) {
            msg.send(p, "forja.reparo_so_restaurador");
            return;
        }
        ForgeMath.RepairCost cost = repairCost(p, it);
        Material unit = repairMaterial(it.getType());
        ItemStack snapshot = it.clone();
        Material coin = Material.matchMaterial(config.string("sem_economia.item", "GOLD_INGOT"));
        Material fallback = coin == null ? Material.GOLD_INGOT : coin;
        long perItem = config.integer("sem_economia.col_por_item", 100, 1, Integer.MAX_VALUE);
        var vars = Messages.vars("preco", hooks.price(cost.col(), fallback, perItem), "unidades", cost.units(),
                "material", unit.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        AincradCore.get().menus().confirm(p, msg.get("forja.reparo_confirmar", vars), yes -> {
            ItemStack now = yes.getInventory().getItemInOffHand();
            if (!now.equals(snapshot)) {
                msg.send(yes, "forja.item_mudou");
                return;
            }
            if (!yes.getInventory().containsAtLeast(new ItemStack(unit), cost.units())) {
                msg.send(yes, "forja.reparo_sem_material", vars);
                return;
            }
            if (!hooks.charge(yes, cost.col(), fallback, perItem, "life:reparo")) {
                msg.send(yes, "forja.reparo_sem_col", vars);
                return;
            }
            yes.getInventory().removeItem(new ItemStack(unit, cost.units()));
            ItemMeta meta = now.getItemMeta();
            ((Damageable) meta).setDamage(0);
            // Restaurador: o reparo deixa o item mais resistente do que era (uma vez por item).
            double more = mod(yes, "restaura_maximo");
            if (more > 0 && !forged.marked(now, "restaurado")) {
                Damageable d = (Damageable) meta;
                int current = d.hasMaxDamage() ? d.getMaxDamage() : now.getType().getMaxDurability();
                d.setMaxDamage((int) Math.round(current * (1 + more)));
            }
            now.setItemMeta(meta);
            if (more > 0) {
                forged.mark(now, "restaurado");
            }
            // Renascer: um item reparado por este mestre nunca mais quebra de vez.
            if (mod(yes, "tecnica_renascer") >= 1 && !forged.marked(now, "renascer")) {
                forged.mark(now, "renascer");
                msg.send(yes, "forja.tecnica_renascer");
            }
            yes.getInventory().setItemInOffHand(now);
            professions.practice(yes, Profession.FERREIRO, config.integer("ferreiro.reparo.dificuldade", 100, 0, 1000), true);
            yes.playSound(yes.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
            msg.send(yes, "forja.reparado", vars);
        }, no -> { });
    }

    /** Item com pouca durabilidade avisa; com "quebra_em_zero: false", o item nunca quebra de vez. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack it = event.getItem();
        int max = maxDamage(it);
        if (max <= 0) {
            return;
        }
        int before = max - damage(it);
        int after = before - event.getDamage();
        if (after <= 0 && (!config.bool("ferreiro.reparo.quebra_em_zero", true) || forged.marked(it, "renascer"))) {
            // Fica com 1 de durabilidade em vez de quebrar.
            if (before <= 1) {
                event.setCancelled(true);
            } else {
                event.setDamage(before - 1);
            }
            msg.actionBar(event.getPlayer(), "forja.quase_quebrando");
            return;
        }
        double warn = config.decimal("ferreiro.reparo.aviso_pct", 10, 0, 100) / 100.0;
        if (before > max * warn && after <= max * warn && after > 0) {
            msg.actionBar(event.getPlayer(), "forja.durabilidade_baixa");
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
        }
    }

    /** A esquiva das couraças com a Segunda Pele: a soma das peças vestidas, até o limite da config. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDodge(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        double dodge = 0;
        for (ItemStack piece : victim.getInventory().getArmorContents()) {
            dodge += forged.dodge(piece);
        }
        dodge = Math.min(dodge, config.decimal("ferreiro.arvore.esquiva_maxima", 20, 0, 100));
        if (dodge > 0 && ThreadLocalRandom.current().nextDouble() * 100 < dodge) {
            event.setCancelled(true);
            msg.actionBar(victim, "forja.esquivou");
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
        }
    }

    /** Golpe crítico dos itens forjados e aprimorados (Precisão). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        double crit = forged.crit(p.getInventory().getItemInMainHand());
        if (crit > 0 && ThreadLocalRandom.current().nextDouble() * 100 < crit) {
            event.setDamage(event.getDamage() * config.decimal("ferreiro.critico_multiplicador", 1.5, 1, 10));
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() * 0.6, 0), 12, 0.3, 0.3, 0.3, 0.2);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
        }
    }

    // ------------------------------------------------------------------ aprimoramento

    /**
     * Pode aprimorar este atributo do item agora?
     *
     * @return a chave da mensagem de recusa, ou null se pode
     */
    String upgradeRefusal(Player p, ItemStack it, Upgrade u) {
        if (!ForgedItems.workable(it)) {
            return "forja.item_na_outra_mao";
        }
        if (forged.tries(it) == 0) {
            return "forja.up_sem_tentativas";
        }
        int level = forged.level(it, u);
        if (level >= upgradeLimit(p)) {
            return "forja.up_limite";
        }
        String path = settings().upPath();
        if (level >= 5 && !path.isEmpty() && !path.equalsIgnoreCase(TreeDef.pathOf(professions.owned(p, Profession.FERREIRO)))) {
            return "forja.up_caminho";
        }
        if (level >= 5 && mod(p, "desbloqueia_aprimoramento_alto") < 1) {
            return "forja.up_no";
        }
        return null;
    }

    /** Escolha do atributo. O item a aprimorar vai na outra mão. */
    private void upgradeMenu(Player p, StationService.Placed st) {
        ItemStack it = p.getInventory().getItemInOffHand();
        if (!ForgedItems.workable(it)) {
            msg.send(p, "forja.item_na_outra_mao");
            return;
        }
        Material[] icons = {Material.FLINT, Material.FEATHER, Material.ENDER_EYE, Material.IRON_BLOCK, Material.OBSIDIAN};
        int limit = upgradeLimit(p);
        int tries = forged.tries(it);
        Menu menu = Menu.of(msg.get("forja.up_titulo", Messages.vars("tentativas", tries < 0 ? settings().triesBase() : tries))).rows(3);
        for (Upgrade u : Upgrade.values()) {
            int level = forged.level(it, u);
            String refusal = upgradeRefusal(p, it, u);
            List<Component> lore = new ArrayList<>();
            lore.add(Messages.parse(msg.raw("forja.atributo_descricao." + u.key())));
            lore.add(msg.get("forja.up_nivel", Messages.vars("nivel", level, "limite", limit)));
            lore.add(msg.get("forja.up_material", Messages.vars("material", upgradeMaterial(u).name().toLowerCase(Locale.ROOT).replace('_', ' '))));
            lore.add(refusal == null ? msg.get("forja.up_clique") : msg.get(refusal, Messages.vars("limite", limit)));
            menu.item(Menu.Button.of(new ItemStack(icons[u.ordinal()]), Messages.parse("<aqua>" + msg.raw("forja.atributo." + u.key())), pl -> {
                ItemStack now = pl.getInventory().getItemInOffHand();
                String no = upgradeRefusal(pl, now, u);
                if (no != null) {
                    msg.send(pl, no, Messages.vars("limite", upgradeLimit(pl)));
                    return;
                }
                if (!free(pl, st)) {
                    return;
                }
                StationPanel panel = panels.open(pl, st.base(), st.yaw, st.type.hologram());
                UpgradeSession s = new UpgradeSession(this, pl, st, panel, u, settings());
                sessions.put(pl.getUniqueId(), s);
                s.open();
            }).closing().lore(lore));
        }
        AincradCore.get().menus().open(p, menu);
    }

    /** Ao desligar o módulo. */
    public void shutdown() {
        sessions.clear();
    }
}
