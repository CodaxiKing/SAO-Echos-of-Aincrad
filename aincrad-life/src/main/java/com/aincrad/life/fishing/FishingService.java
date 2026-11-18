package com.aincrad.life.fishing;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.life.api.LegendaryFishEvent;
import com.aincrad.life.cooking.FoodBuffService;
import com.aincrad.life.fishing.FishingRegistry.Bait;
import com.aincrad.life.fishing.FishingRegistry.Fish;
import com.aincrad.life.fishing.FishingRegistry.Legendary;
import com.aincrad.life.fishing.FishingRegistry.Point;
import com.aincrad.life.fishing.FishingRegistry.Rarity;
import com.aincrad.life.fishing.FishingRegistry.Rod;
import com.aincrad.life.fishing.FishingRegistry.Zone;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * A pesca do Aincrad: dentro de um ponto de pesca (região do core), a vara abre uma
 * {@link FishingSession} — lançar com força, fisgar na hora e cansar o peixe. Fora dos pontos,
 * a pesca é a normal do Minecraft. Também cuida das varas, iscas e peixes como itens, do registro
 * do maior peixe e do Senhor do Lago.
 */
public final class FishingService implements Listener {

    /** Os números de uma raridade (config.yml → pesca.raridade). */
    record RarityRules(int window, int stamina, double burstPerSecond) {
    }

    /** Os números da pescaria (config.yml → pesca), lidos no começo de cada lançamento. */
    record Settings(int chargePeriod, int chargeTimeout, int biteMin, int biteMax, double band, double bandAtMaster,
                    double redFrom, double looseBelow, int looseTicks, double decay, double pull, double burst, int burstTicks,
                    double burstDegrees, double luckAtMaster, double bedrockWindow, Map<Rarity, RarityRules> rarities) {
    }

    /** Um lançamento sendo carregado (a barra de força). */
    private record Charge(long since, Point point) {
    }

    /** Um Senhor do Lago fora da água. */
    private record Lord(LivingEntity entity, UUID owner, String ownerName, Point point, long leaves, java.util.Set<UUID> hunters) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final ProfessionService professions;
    private final Supplier<FoodBuffService> food;
    private final TournamentService tournament;
    private final NamespacedKey keyRod;
    private final NamespacedKey keyBait;
    private final NamespacedKey keyFish;
    private final NamespacedKey keyFishMm;
    private final NamespacedKey keyFishAt;
    private final NamespacedKey keyLord;
    private final Map<UUID, Charge> charging = new HashMap<>();
    private final Map<UUID, FishingSession> sessions = new HashMap<>();
    private final Map<UUID, Lord> lords = new HashMap<>();
    private FishingRegistry registry = FishingRegistry.parse(null, new ArrayList<>());
    private long tick;

    /**
     * @param food o módulo de culinária, para a "sorte na pesca" da comida (pode devolver null)
     */
    public FishingService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, ProfessionService professions,
                          Supplier<FoodBuffService> food) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.professions = professions;
        this.food = food;
        this.tournament = new TournamentService(plugin, msg, config, hooks, AincradCore.get().database(), this);
        this.keyRod = new NamespacedKey(plugin, "vara");
        this.keyBait = new NamespacedKey(plugin, "isca");
        this.keyFish = new NamespacedKey(plugin, "peixe");
        this.keyFishMm = new NamespacedKey(plugin, "peixe_mm");
        this.keyFishAt = new NamespacedKey(plugin, "peixe_hora");
        this.keyLord = new NamespacedKey(plugin, "senhor_do_lago");
    }

    /** Lê fishing.yml. */
    public void load() {
        File f = new File(plugin.getDataFolder(), "fishing.yml");
        if (!f.exists()) {
            plugin.saveResource("fishing.yml", false);
        }
        List<String> errors = new ArrayList<>();
        registry = FishingRegistry.parse(YamlConfiguration.loadConfiguration(f), errors);
        for (Fish fish : registry.fishes().values()) {
            if (Material.matchMaterial(fish.material()) == null) {
                errors.add("fishing.yml: peixe " + fish.id() + ": material desconhecido " + fish.material() + ".");
            }
        }
        for (Bait b : registry.baits().values()) {
            if (Material.matchMaterial(b.material()) == null) {
                errors.add("fishing.yml: isca " + b.id() + ": material desconhecido " + b.material() + ".");
            }
        }
        for (Point p : registry.points().values()) {
            if (AincradCore.get().regions().get(p.region()) == null) {
                errors.add("fishing.yml: ponto " + p.id() + ": a região \"" + p.region() + "\" ainda não existe (/aincrad regiao criar " + p.region() + ").");
            }
            if (p.legendary() != null && lordType(p.legendary()) == null) {
                errors.add("fishing.yml: ponto " + p.id() + ": o mob " + p.legendary().mob() + " não existe ou não é um ser vivo (será um guardião).");
            }
        }
        errors.forEach(e -> plugin.getLogger().warning(e));
        plugin.getLogger().info("Pesca: " + registry.points().size() + " pontos, " + registry.fishes().size() + " peixes, "
                + registry.rods().size() + " varas e " + registry.baits().size() + " iscas.");
    }

    public FishingRegistry registry() {
        return registry;
    }

    public TournamentService tournament() {
        return tournament;
    }

    Messages messages() {
        return msg;
    }

    ProfessionService professions() {
        return professions;
    }

    public boolean hasFloor(int floor) {
        return registry.points().values().stream().anyMatch(p -> p.floor() == floor);
    }

    /** O peixe lendário de um andar (null: nenhum ponto do andar tem lendário). */
    public Fish legendaryOf(int floor) {
        for (Point p : registry.points().values()) {
            if (p.floor() == floor && p.legendary() != null) {
                return registry.fish(p.legendary().fish());
            }
        }
        return null;
    }

    /** Os andares que têm ponto de pesca, como "1, 2, 5". */
    String floors() {
        TreeSet<Integer> out = new TreeSet<>();
        registry.points().values().forEach(p -> out.add(p.floor()));
        return out.isEmpty() ? "—" : out.toString().replace("[", "").replace("]", "");
    }

    Settings settings() {
        String c = "pesca.";
        Map<Rarity, RarityRules> rar = new EnumMap<>(Rarity.class);
        int[] windows = {30, 24, 16, 12};
        int[] staminas = {60, 90, 130, 200};
        double[] bursts = {0, 0.05, 0.2, 0.3};
        for (Rarity r : Rarity.values()) {
            String k = c + "raridade." + r.key() + ".";
            rar.put(r, new RarityRules(config.integer(k + "janela_ticks", windows[r.ordinal()], 2, 600),
                    config.integer(k + "cansar_ticks", staminas[r.ordinal()], 5, 6000),
                    config.decimal(k + "arrancada_por_segundo", bursts[r.ordinal()], 0, 1)));
        }
        return new Settings(config.integer(c + "lancar.periodo_ticks", 40, 10, 400), config.integer(c + "lancar.espera_segundos", 10, 2, 120) * 20,
                config.integer(c + "mordida.min_segundos", 4, 1, 600) * 20, config.integer(c + "mordida.max_segundos", 10, 1, 600) * 20,
                config.decimal(c + "tensao.faixa", 0.3, 0.05, 0.9), config.decimal(c + "tensao.faixa_no_mestre", 0.1, 0, 0.5),
                config.decimal(c + "tensao.vermelho_a_partir", 0.9, 0.5, 1), config.decimal(c + "tensao.frouxa_abaixo", 0.1, 0, 0.5),
                config.integer(c + "tensao.frouxa_ticks", 40, 1, 2400), config.decimal(c + "tensao.queda_por_tick", 0.012, 0, 0.5),
                config.decimal(c + "tensao.puxada", 0.07, 0.005, 1), config.decimal(c + "tensao.arrancada_por_tick", 0.02, 0, 0.5),
                config.integer(c + "tensao.arrancada_ticks", 40, 5, 600), config.decimal(c + "tensao.arrancada_graus", 20, 1, 170),
                config.decimal(c + "sorte_no_mestre", 0.3, 0, 5), config.decimal(c + "bedrock_janela", 1.5, 1, 10), rar);
    }

    // ------------------------------------------------------------------ itens

    private static String tag(ItemStack it, NamespacedKey key) {
        return it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** A vara que o item é: a própria (com marca) ou, para uma vara comum, a "vara_padrao". Null: não é vara. */
    public Rod rodOf(ItemStack it) {
        if (it == null || it.getType() != Material.FISHING_ROD) {
            return null;
        }
        Rod tagged = registry.rod(tag(it, keyRod));
        return tagged != null ? tagged : registry.defaultRod();
    }

    /** A isca que o item é (só os itens próprios). */
    public Bait baitOf(ItemStack it) {
        return registry.bait(tag(it, keyBait));
    }

    public ItemStack rodItem(Rod rod) {
        ItemStack it = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("nome", rod.name(), "rank", rod.rank(), "zona", zoneName(registry.deepest(rod)),
                "sorte", Math.round(rod.luck() * 100));
        meta.displayName(msg.get("pesca.vara_nome", vars));
        meta.lore(msg.list("pesca.vara_lore", vars));
        meta.getPersistentDataContainer().set(keyRod, PersistentDataType.STRING, rod.id());
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack baitItem(Bait bait, int amount) {
        Material m = Material.matchMaterial(bait.material());
        ItemStack it = new ItemStack(m == null ? Material.WHEAT_SEEDS : m, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("nome", bait.name(), "sorte", Math.round(bait.luck() * 100));
        meta.displayName(msg.get("pesca.isca_nome", vars));
        meta.lore(msg.list(bait.legendary() ? "pesca.isca_lore_lendaria" : "pesca.isca_lore", vars));
        meta.getPersistentDataContainer().set(keyBait, PersistentDataType.STRING, bait.id());
        it.setItemMeta(meta);
        return it;
    }

    /** O peixe pescado: guarda a espécie, o tamanho e a hora (para o frescor, na banca de peixe). */
    public ItemStack fishItem(Fish fish, int mm, String fisher) {
        Material m = Material.matchMaterial(fish.material());
        ItemStack it = new ItemStack(m == null ? Material.COD : m);
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("nome", fish.name(), "tamanho", cm(mm), "raridade", msg.get("pesca.raridade." + fish.rarity().key()),
                "pescador", fisher);
        meta.displayName(msg.get("pesca.peixe_nome." + fish.rarity().key(), vars));
        meta.lore(msg.list("pesca.peixe_lore", vars));
        if (fish.rarity() == Rarity.LENDARIO) {
            meta.setEnchantmentGlintOverride(true);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyFish, PersistentDataType.STRING, fish.id());
        pdc.set(keyFishMm, PersistentDataType.INTEGER, mm);
        pdc.set(keyFishAt, PersistentDataType.LONG, System.currentTimeMillis());
        it.setItemMeta(meta);
        return it;
    }

    /** Id da espécie de um peixe pescado num ponto de pesca (null: não é). */
    public String fishId(ItemStack it) {
        return tag(it, keyFish);
    }

    /** Tamanho de um peixe pescado, em milímetros (0: não é). */
    public int fishMm(ItemStack it) {
        return it == null || !it.hasItemMeta() ? 0 : it.getItemMeta().getPersistentDataContainer().getOrDefault(keyFishMm, PersistentDataType.INTEGER, 0);
    }

    /** Quando o peixe foi pescado, em milissegundos (0: não é peixe de ponto de pesca). Usado no frescor da banca. */
    public long fishCaughtAt(ItemStack it) {
        return it == null || !it.hasItemMeta() ? 0 : it.getItemMeta().getPersistentDataContainer().getOrDefault(keyFishAt, PersistentDataType.LONG, 0L);
    }

    /** "23,5" para 235 mm. */
    public static String cm(int mm) {
        return mm % 10 == 0 ? String.valueOf(mm / 10) : (mm / 10) + "," + (mm % 10);
    }

    String zoneName(Zone z) {
        return z == null ? "—" : msg.raw("pesca.zona." + z.key());
    }

    static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    // ------------------------------------------------------------------ árvore: tesouros, iscas, mergulho

    /** Um número dos efeitos da árvore (config.yml → pesca.arvore). */
    double treeValue(String key, double def) {
        return config.decimal("pesca.arvore." + key, def, 0, 100_000);
    }

    private double mod(Player p, String key) {
        return professions.modifier(p, Profession.PESCA, key);
    }

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.BASIC_ISO_DATE;

    /** Marca que o jogador usou hoje uma coisa que vale uma vez por dia. @return true se ainda não tinha usado */
    public boolean oncePerDay(Player p, String what) {
        String today = java.time.LocalDate.now().format(DAY);
        var profile = AincradCore.get().profile(p);
        if (today.equals(profile.get(plugin, "dia_" + what, ""))) {
            return false;
        }
        profile.set(plugin, "dia_" + what, today);
        return true;
    }

    private final Map<UUID, Long> harpoonAt = new HashMap<>();
    private final Map<UUID, Long> diveAt = new HashMap<>();

    /** Arpão (Mergulhador): pega o peixe sem a fase de tensão, uma vez a cada recarga. */
    boolean useHarpoon(Player p) {
        if (mod(p, "tecnica_arpao") < 1) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < harpoonAt.getOrDefault(p.getUniqueId(), 0L)) {
            return false;
        }
        harpoonAt.put(p.getUniqueId(), now + (long) (treeValue("arpao_recarga_segundos", 300) * 1000));
        msg.send(p, "pesca.tecnica_arpao");
        return true;
    }

    /**
     * O que vem na linha no lugar de um peixe, ou null. O Naufrágio garante um baú por dia, na
     * primeira mordida do dia em que um tesouro era possível.
     */
    FishingPerks.Treasure rollTreasure(Player p, Zone zone) {
        java.util.function.ToDoubleFunction<String> m = key -> mod(p, key);
        double chance = FishingPerks.treasureChance(zone, treeValue("tesouro_chance", 0.05), m);
        if (chance <= 0) {
            return null;
        }
        if (mod(p, "tecnica_naufragio") >= 1 && oncePerDay(p, "naufragio")) {
            msg.send(p, "pesca.tecnica_naufragio");
            return FishingPerks.Treasure.BAU;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() >= chance) {
            return null;
        }
        return rnd.nextDouble() < treeValue("tesouro_chance_bau", 0.4) ? FishingPerks.Treasure.BAU : FishingPerks.Treasure.MATERIAL;
    }

    private ItemStack pick(String list, ThreadLocalRandom rnd) {
        List<String> names = config.yaml().getStringList("pesca.arvore." + list);
        if (names.isEmpty()) {
            return null;
        }
        Material m = Material.matchMaterial(names.get(rnd.nextInt(names.size())));
        return m == null || !m.isItem() ? null : new ItemStack(m);
    }

    /** Entrega o tesouro ao pescador. */
    void giveTreasure(Player p, FishingPerks.Treasure t) {
        java.util.function.ToDoubleFunction<String> m = key -> mod(p, key);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int given = 0;
        if (t == FishingPerks.Treasure.BAU) {
            int n = FishingPerks.chestItems((int) treeValue("bau_itens", 2), m);
            for (int i = 0; i < n; i++) {
                // Baús de rank A (árvore): metade dos itens vem da lista melhor.
                ItemStack it = pick(mod(p, "desbloqueia_baus_a") >= 1 && rnd.nextBoolean() ? "bau_a" : "bau", rnd);
                if (it != null) {
                    give(p, it);
                    given++;
                }
            }
            msg.send(p, "pesca.pegou_bau", Messages.vars("qtd", given));
        } else {
            boolean vein = rnd.nextDouble() < treeValue("veio_chance", 0.2);
            int n = FishingPerks.materialAmount(m, vein);
            // Materiais raros: a lista melhor, e com "rank_materiais" ela vem mais vezes.
            double better = mod(p, "desbloqueia_materiais_a") >= 1 ? 0.25 + 0.25 * mod(p, "rank_materiais") : 0;
            ItemStack it = pick(rnd.nextDouble() < better ? "materiais_a" : "materiais", rnd);
            if (it != null) {
                it.setAmount(Math.min(it.getMaxStackSize(), n));
                give(p, it);
                given = it.getAmount();
            }
            msg.send(p, vein && mod(p, "tecnica_veio") >= 1 ? "pesca.pegou_veio" : "pesca.pegou_material", Messages.vars("qtd", given));
        }
        professions.practice(p, Profession.PESCA, (int) treeValue("tesouro_dificuldade", 400), true);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    /** /pesca isca [id]: faz iscas do peixe que o pescador tem na mão. Sem id, lista as que ele sabe fazer. */
    public void makeBait(Player p, String id) {
        java.util.function.ToDoubleFunction<String> m = key -> mod(p, key);
        ItemStack hand = p.getInventory().getItemInMainHand();
        Fish fish = registry.fish(fishId(hand));
        if (id == null) {
            List<String> can = new ArrayList<>();
            for (Bait b : registry.baits().values()) {
                String why = FishingPerks.baitRefusal(b, null, m);
                if (!"no".equals(why)) {
                    can.add(b.id() + " (" + msg.raw("pesca.raridade." + FishingPerks.fishFor(FishingPerks.baitTier(b)).key()) + ")");
                }
            }
            msg.send(p, "pesca.iscas_lista", Messages.vars("iscas", can.isEmpty() ? "—" : String.join(", ", can)));
            return;
        }
        Bait bait = registry.bait(id.toLowerCase(Locale.ROOT));
        if (bait == null) {
            msg.send(p, "pesca.isca_desconhecida", Messages.vars("isca", id));
            return;
        }
        String why = FishingPerks.baitRefusal(bait, fish, m);
        if (why != null) {
            msg.send(p, "pesca.isca_recusa_" + why, Messages.vars("isca", bait.name(),
                    "raridade", msg.raw("pesca.raridade." + FishingPerks.fishFor(FishingPerks.baitTier(bait)).key())));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        int n = bait.id().equals(FishingPerks.GOLDEN_BAIT) ? 1 : FishingPerks.baitsPerFish((int) treeValue("iscas_por_peixe", 2), m);
        give(p, baitItem(bait, n));
        professions.practice(p, Profession.PESCA, 100 + 200 * FishingPerks.baitTier(bait), true);
        msg.send(p, "pesca.isca_feita", Messages.vars("isca", bait.name(), "qtd", n));
    }

    /**
     * Mergulhador: debaixo d'água, num ponto de pesca, o clique com a mão vazia pega um peixe do
     * fundo na mão, sem vara. Tem recarga.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDive(org.bukkit.event.player.PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !event.getAction().isRightClick()
                || !p.getInventory().getItemInMainHand().getType().isAir() || !p.isInWater()
                || p.getEyeLocation().getBlock().getType() != Material.WATER || mod(p, "desbloqueia_mergulho") < 1) {
            return;
        }
        Point point = pointAt(p.getLocation());
        if (point == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < diveAt.getOrDefault(p.getUniqueId(), 0L)) {
            msg.actionBar(p, "pesca.mergulho_espera");
            return;
        }
        diveAt.put(p.getUniqueId(), now + (long) (treeValue("mergulho_recarga_segundos", 20) * 1000));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Fish fish = registry.roll(point, Zone.FUNDO, 0, rnd.nextDouble());
        if (fish == null || fish.rarity() == Rarity.LENDARIO) {
            msg.actionBar(p, "pesca.mergulho_nada");
            return;
        }
        int mm = FishingRegistry.sizeMm(fish, rnd.nextDouble());
        give(p, fishItem(fish, mm, p.getName()));
        register(p, point, fish, mm);
        professions.practice(p, Profession.PESCA, fish.difficulty(), true);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.6f, 1.4f);
        msg.send(p, "pesca.mergulho_pegou", Messages.vars("peixe", fish.name(), "tamanho", cm(mm)));
    }

    // ------------------------------------------------------------------ sorte e registro

    /** A sorte do lançamento: vara, isca, proficiência, comida e nós da árvore. */
    double luck(Player p, Rod rod, Bait bait, Settings cfg) {
        FoodBuffService f = food.get();
        return rod.luck() + (bait == null ? 0 : bait.luck())
                + professions.proficiency(p, Profession.PESCA) / Profession.MAX * cfg.luckAtMaster()
                + (f == null ? 0 : f.fishingLuck(p))
                + professions.modifier(p, Profession.PESCA, "chance_incomum") + professions.modifier(p, Profession.PESCA, "chance_raro");
    }

    /** O maior peixe da espécie que o jogador já pescou, em milímetros (0 = nenhum). */
    public int record(Player p, Fish fish) {
        return AincradCore.get().profile(p).getInt(plugin, "peixe." + fish.id(), 0);
    }

    /** Guarda o peixe no registro do jogador e no torneio. @return true se é o maior dele da espécie */
    boolean register(Player p, Point point, Fish fish, int mm) {
        tournament.record(p, point.floor(), fish, mm);
        if (mm <= record(p, fish)) {
            return false;
        }
        AincradCore.get().profile(p).set(plugin, "peixe." + fish.id(), mm);
        return true;
    }

    // ------------------------------------------------------------------ a vara

    /** O ponto de pesca onde o jogador está (null: fora de qualquer um). */
    public Point pointAt(Location l) {
        for (Region r : AincradCore.get().regions().at(l)) {
            Point p = registry.pointOfRegion(r.name());
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        FishingSession session = sessions.get(p.getUniqueId());
        // Pelo nome: os estados mudam entre versões do Paper (ex.: LURED).
        switch (event.getState().name()) {
            case "FISHING" -> cast(event, p);
            case "REEL_IN" -> {
                // Com a linha na água do ponto de pesca, o clique é a fisgada ou uma puxada.
                if (session != null && session.onReel()) {
                    event.setCancelled(true);
                }
            }
            case "BITE", "CAUGHT_FISH", "LURED", "FAILED_ATTEMPT" -> {
                // Dentro do ponto, quem decide a mordida é a sessão, não o Minecraft.
                if (session != null) {
                    event.setCancelled(true);
                }
            }
            default -> {
                // Boia no chão ou presa num mob: a pescaria acabou.
                if (session != null) {
                    end(session);
                }
            }
        }
    }

    private void cast(PlayerFishEvent event, Player p) {
        Point point = pointAt(p.getLocation());
        if (point == null) {
            charging.remove(p.getUniqueId());
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            msg.actionBar(p, "pesca.mao_principal");
            return;
        }
        Rod rod = rodOf(p.getInventory().getItemInMainHand());
        if (rod == null) {
            return;
        }
        Settings cfg = settings();
        Zone zone;
        double power;
        if (AincradCore.get().isBedrock(p)) {
            // No Bedrock não há barra de força: a linha vai para a zona mais funda que a vara alcança.
            zone = registry.deepest(rod);
            power = 0.7;
        } else {
            Charge c = charging.remove(p.getUniqueId());
            if (c == null || c.point() != point) {
                // Primeiro clique: começa a barra de força. O segundo lança.
                event.setCancelled(true);
                charging.put(p.getUniqueId(), new Charge(tick, point));
                p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.5f, 1.4f);
                return;
            }
            power = power(c, cfg, p);
            zone = registry.zone(power);
        }
        if (zone != null) {
            // Águas profundas (árvore): sem o nó, o lançamento mais forte cai na zona do meio.
            zone = FishingPerks.allowed(zone, key -> professions.modifier(p, Profession.PESCA, key));
        }
        if (zone == null || !registry.reaches(rod, zone)) {
            event.setCancelled(true);
            msg.send(p, "pesca.vara_fraca", Messages.vars("zona", zoneName(zone), "vara", rod.name()));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return;
        }
        FishHook hook = event.getHook();
        hook.setVelocity(hook.getVelocity().multiply(0.55 + power * 0.75));
        // A mordida do Minecraft nunca chega: a sessão cuida disso.
        hook.setWaitTime(72_000, 72_000);
        FishingSession old = sessions.remove(p.getUniqueId());
        if (old != null) {
            old.dispose();
        }
        sessions.put(p.getUniqueId(), new FishingSession(this, p, hook, point, zone, rod, baitOf(p.getInventory().getItemInOffHand()), cfg));
        msg.actionBar(p, "pesca.lancou", Messages.vars("zona", zoneName(zone)));
    }

    /** A força da barra agora: sobe e desce, de 0 a 1. */
    private double power(Charge c, Settings cfg, Player p) {
        double phase = ((tick - c.since()) % cfg.chargePeriod()) / (double) cfg.chargePeriod();
        double wave = 1 - Math.abs(2 * phase - 1);
        return Math.min(1, wave + wave * Math.max(0, professions.modifier(p, Profession.PESCA, "forca_lancamento")));
    }

    /** Roda a cada tick: a barra de força de quem está carregando e as sessões abertas. */
    public void tick() {
        tick++;
        if (!charging.isEmpty()) {
            Settings cfg = settings();
            charging.entrySet().removeIf(e -> {
                Player p = Bukkit.getPlayer(e.getKey());
                Charge c = e.getValue();
                if (p == null || tick - c.since() > cfg.chargeTimeout() || rodOf(p.getInventory().getItemInMainHand()) == null) {
                    return true;
                }
                if (tick % 2 == 0) {
                    double power = power(c, cfg, p);
                    p.sendActionBar(Messages.parse(msg.raw("pesca.forca") + " " + powerBar(power) + " <white>" + zoneName(registry.zone(power))));
                }
                return false;
            });
        }
        for (FishingSession s : new ArrayList<>(sessions.values())) {
            s.tick();
        }
        if (tick % 20 == 0 && !lords.isEmpty()) {
            for (Lord l : new ArrayList<>(lords.values())) {
                if (!l.entity().isValid() || tick >= l.leaves()) {
                    lords.remove(l.entity().getUniqueId());
                    if (l.entity().isValid()) {
                        l.entity().remove();
                        Player owner = Bukkit.getPlayer(l.owner());
                        if (owner != null) {
                            msg.send(owner, "pesca.senhor_foi_embora", Messages.vars("nome", l.point().legendary().bossName()));
                        }
                    }
                }
            }
        }
    }

    /** A barra de força, com as três zonas em cores. */
    private String powerBar(double power) {
        int width = 24;
        int pointer = (int) Math.min(width - 1, Math.floor(power * width));
        StringBuilder sb = new StringBuilder("<dark_gray>[");
        for (int i = 0; i < width; i++) {
            Zone z = registry.zone((i + 0.5) / width);
            sb.append(i == pointer ? "<white>█" : z == Zone.RASO ? "<aqua>-" : z == Zone.MEIO ? "<blue>-" : "<dark_blue>-");
        }
        return sb.append("<dark_gray>]").toString();
    }

    /** Encerra a sessão (a boia fica por conta do Minecraft). */
    void end(FishingSession s) {
        sessions.remove(s.player().getUniqueId(), s);
        s.dispose();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        charging.remove(event.getPlayer().getUniqueId());
        FishingSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null) {
            end(s);
        }
    }

    /** Ao desligar o módulo: fecha as sessões e manda os chefes de volta para o fundo. */
    public void shutdown() {
        new ArrayList<>(sessions.values()).forEach(this::end);
        charging.clear();
        lords.values().forEach(l -> l.entity().remove());
        lords.clear();
    }

    // ------------------------------------------------------------------ Senhor do Lago

    private static EntityType lordType(Legendary l) {
        try {
            EntityType t = EntityType.valueOf(l.mob());
            return t.isAlive() && t.isSpawnable() ? t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * O lendário foi fisgado: em vez do peixe, o Senhor do Lago sai da água. Quem fisgou recebe o
     * peixe quando o chefe morrer, não importa quem dê o último golpe.
     */
    void spawnLord(Player fisher, Point point, Location at) {
        Legendary l = point.legendary();
        EntityType type = lordType(l);
        Entity e = at.getWorld().spawnEntity(at, type == null ? EntityType.GUARDIAN : type);
        if (!(e instanceof LivingEntity boss)) {
            e.remove();
            return;
        }
        boss.setPersistent(false);
        boss.setRemoveWhenFarAway(false);
        boss.getPersistentDataContainer().set(keyLord, PersistentDataType.STRING, point.id());
        if (hooks.hasRpg()) {
            hooks.mobLevel(boss, l.level());
        }
        AttributeInstance hp = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(l.health());
            // Caçador de lendários (técnica): o Senhor do Lago já sai da água ferido.
            boolean worn = professions.modifier(fisher, Profession.PESCA, "tecnica_senhor_do_lago") >= 1;
            boss.setHealth(hp.getValue() * (worn ? 1 - Math.min(0.9, treeValue("senhor_do_lago_menos_vida", 0.2)) : 1));
        }
        AttributeInstance dmg = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null && l.damage() > 0) {
            dmg.setBaseValue(l.damage());
        }
        boss.customName(msg.get("pesca.senhor_nome", Messages.vars("nome", l.bossName(), "level", l.level())));
        boss.setCustomNameVisible(true);
        if (boss instanceof Mob mob) {
            mob.setTarget(fisher);
        }
        lords.put(boss.getUniqueId(), new Lord(boss, fisher.getUniqueId(), fisher.getName(), point, tick + l.minutes() * 1200L, new java.util.HashSet<>()));
        at.getWorld().playSound(at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f);
        var vars = Messages.vars("nome", l.bossName(), "jogador", fisher.getName(), "minutos", l.minutes());
        for (Player near : at.getWorld().getPlayers()) {
            if (near.getLocation().distanceSquared(at) <= 48 * 48) {
                msg.send(near, "pesca.senhor_apareceu", vars);
            }
        }
    }

    /** Guarda quem feriu o chefe: são eles que recebem numa caçada contratada. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLordHurt(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Lord l = lords.get(event.getEntity().getUniqueId());
        if (l == null) {
            return;
        }
        Entity by = event.getDamager();
        if (by instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player shooter) {
            l.hunters().add(shooter.getUniqueId());
        } else if (by instanceof Player player) {
            l.hunters().add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLordDeath(EntityDeathEvent event) {
        Lord l = lords.remove(event.getEntity().getUniqueId());
        if (l == null) {
            return;
        }
        event.getDrops().clear();
        Fish fish = registry.fish(l.point().legendary().fish());
        if (fish == null) {
            return;
        }
        int mm = FishingRegistry.sizeMm(fish, ThreadLocalRandom.current().nextDouble());
        ItemStack item = fishItem(fish, mm, l.ownerName());
        Player owner = Bukkit.getPlayer(l.owner());
        if (owner != null) {
            give(owner, item);
            register(owner, l.point(), fish, mm);
            professions.practice(owner, Profession.PESCA, fish.difficulty(), true);
            owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else if (!hooks.mail(l.owner(), item, "life:peixe_lendario")) {
            // Quem fisgou saiu e não há caixa de retirada: o peixe fica onde o chefe caiu.
            event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), item);
        }
        Bukkit.broadcast(msg.prefix().append(msg.get("pesca.senhor_derrotado", Messages.vars("nome", l.point().legendary().bossName(),
                "jogador", l.ownerName(), "peixe", fish.name(), "tamanho", cm(mm), "andar", l.point().floor()))));
        Bukkit.getPluginManager().callEvent(new LegendaryFishEvent(l.owner(), l.ownerName(), fish.id(), l.point().floor(), mm / 10.0, l.hunters()));
    }

    // ------------------------------------------------------------------ /pesca

    /** O registro do jogador: o maior peixe de cada espécie (as que ele nunca pescou aparecem como "???"). */
    public void showRecords(Player p) {
        msg.send(p, "pesca.registro_titulo", Messages.vars("proficiencia", (int) professions.proficiency(p, Profession.PESCA)));
        for (Fish f : registry.fishes().values()) {
            int mm = record(p, f);
            if (mm > 0) {
                msg.sendRaw(p, "pesca.registro_linha", Messages.vars("peixe", f.name(), "tamanho", cm(mm),
                        "raridade", msg.get("pesca.raridade." + f.rarity().key())));
            } else {
                msg.sendRaw(p, "pesca.registro_desconhecido", Messages.vars("raridade", msg.get("pesca.raridade." + f.rarity().key())));
            }
        }
        Point here = pointAt(p.getLocation());
        if (here != null) {
            msg.sendRaw(p, "pesca.registro_ponto", Messages.vars("andar", here.floor(),
                    "zona", zoneName(registry.deepest(rodOf(p.getInventory().getItemInMainHand())))));
        }
    }

    /** Texto dos ids, para os comandos de admin. */
    public String list(String what) {
        return switch (what.toLowerCase(Locale.ROOT)) {
            case "varas" -> String.join(", ", registry.rods().keySet());
            case "iscas" -> String.join(", ", registry.baits().keySet());
            case "peixes" -> String.join(", ", registry.fishes().keySet());
            default -> String.join(", ", registry.points().keySet());
        };
    }
}
