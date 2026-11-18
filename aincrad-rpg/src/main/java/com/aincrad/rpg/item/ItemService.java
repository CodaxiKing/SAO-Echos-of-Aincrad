package com.aincrad.rpg.item;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.rpg.RpgSettings;
import com.aincrad.rpg.model.ItemCombat;
import io.papermc.paper.persistence.PersistentDataContainerView;
import com.google.common.collect.Multimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Modelos de items.yml → itens com raridade, bônus sorteados e level mínimo. */
public final class ItemService {

    /** Um modelo de items.yml. */
    public record Template(String id, Material material, String name, int minLevel, EquipmentSlot slot,
                           Map<String, ItemRoll.Range> bonus, double dropChance, int dropMin, int dropMax, String model) {
    }

    /** Bônus aceitos, com o atributo, a operação e se o valor é em %. Sem atributo: bônus de combate, guardado no item. */
    private record BonusType(Attribute attribute, AttributeModifier.Operation op, boolean percent) {
    }

    private static final Map<String, BonusType> BONUS = new LinkedHashMap<>();

    static {
        BONUS.put("dano", new BonusType(Attribute.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, false));
        BONUS.put("vida", new BonusType(Attribute.MAX_HEALTH, AttributeModifier.Operation.ADD_NUMBER, false));
        BONUS.put("armadura", new BonusType(Attribute.ARMOR, AttributeModifier.Operation.ADD_NUMBER, false));
        BONUS.put("resistencia", new BonusType(Attribute.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_NUMBER, false));
        BONUS.put("velocidade_ataque", new BonusType(Attribute.ATTACK_SPEED, AttributeModifier.Operation.ADD_SCALAR, true));
        BONUS.put("velocidade", new BonusType(Attribute.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_SCALAR, true));
        for (String stat : ItemCombat.STATS) {
            BONUS.put(stat, new BonusType(null, null, true));
        }
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile file;
    private final NamespacedKey keyId;
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyLevel;
    private final NamespacedKey keySlot;
    private final Map<String, NamespacedKey> keyCombat = new LinkedHashMap<>();
    private Map<String, Template> templates = Map.of();
    private Set<String> unique = Set.of();
    private UniqueItemService uniques;
    private RpgSettings settings;

    public ItemService(Plugin plugin, Messages msg, ConfigFile file, RpgSettings settings) {
        this.plugin = plugin;
        this.msg = msg;
        this.file = file;
        this.settings = settings;
        keyId = new NamespacedKey(plugin, "item");
        keyRarity = new NamespacedKey(plugin, "raridade");
        keyLevel = new NamespacedKey(plugin, "level_minimo");
        keySlot = new NamespacedKey(plugin, "slot");
        for (String stat : ItemCombat.STATS) {
            keyCombat.put(stat, new NamespacedKey(plugin, "c_" + stat));
        }
        load();
    }

    public void reload(RpgSettings s) {
        this.settings = s;
        file.reload();
        load();
    }

    private void load() {
        Map<String, Template> out = new LinkedHashMap<>();
        ConfigurationSection root = file.yaml().getConfigurationSection("itens");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                Template t = s == null ? null : parse(id.toLowerCase(Locale.ROOT), s);
                if (t != null) {
                    out.put(t.id(), t);
                }
            }
        }
        templates = Collections.unmodifiableMap(out);
        Set<String> only = new java.util.LinkedHashSet<>();
        for (String id : out.keySet()) {
            if (root.getBoolean(id + ".unico", false)) {
                only.add(id);
            }
        }
        unique = Collections.unmodifiableSet(only);
        plugin.getLogger().info(templates.size() + " modelo(s) de item carregado(s).");
    }

    private Template parse(String id, ConfigurationSection s) {
        Material m = Material.matchMaterial(s.getString("material", ""));
        if (m == null || !m.isItem()) {
            plugin.getLogger().warning("items.yml: " + id + " tem material inválido.");
            return null;
        }
        EquipmentSlot slot;
        try {
            slot = EquipmentSlot.valueOf(s.getString("slot", "MAINHAND").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("items.yml: " + id + " tem slot inválido.");
            return null;
        }
        Map<String, ItemRoll.Range> bonus = new LinkedHashMap<>();
        ConfigurationSection b = s.getConfigurationSection("bonus");
        if (b != null) {
            for (String k : b.getKeys(false)) {
                String key = k.toLowerCase(Locale.ROOT);
                List<?> range = b.getList(k, List.of());
                if (!BONUS.containsKey(key) || range.size() != 2
                        || !(range.get(0) instanceof Number a) || !(range.get(1) instanceof Number c)) {
                    plugin.getLogger().warning("items.yml: bônus " + k + " de " + id + " ignorado (use [mín, máx]).");
                    continue;
                }
                bonus.put(key, new ItemRoll.Range(a.doubleValue(), c.doubleValue()));
            }
        }
        List<Integer> lv = s.getIntegerList("drop.level_mob");
        return new Template(id, m, s.getString("nome", id), Math.max(1, s.getInt("level_minimo", 1)), slot, bonus,
                s.getDouble("drop.chance", 0), lv.isEmpty() ? 1 : lv.get(0), lv.size() < 2 ? Integer.MAX_VALUE : lv.get(1),
                s.getString("modelo", "").trim());
    }

    public Set<String> ids() {
        return templates.keySet();
    }

    public Template template(String id) {
        return id == null ? null : templates.get(id.toLowerCase(Locale.ROOT));
    }

    public Rarity rollRarity(Random rnd) {
        return ItemRoll.rarity(settings.weights(), rnd);
    }

    /** Os modelos com {@code unico: true}: existem uma vez só na rede. */
    public Set<String> uniqueIds() {
        return unique;
    }

    public void uniques(UniqueItemService service) {
        uniques = service;
    }

    /** O id do modelo de um item do RPG, ou null. */
    public String id(ItemStack it) {
        return it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
    }

    /**
     * Cria o item; {@code rarity} null = sorteada pelos pesos. Modelo desconhecido: null.
     * Um item único que já existe na rede também devolve null: não há um segundo.
     */
    public ItemStack create(String id, Rarity rarity) {
        Template t = template(id);
        if (t == null) {
            return null;
        }
        String serial = null;
        if (unique.contains(t.id())) {
            serial = uniques == null ? null : uniques.reserve(t.id());
            if (serial == null) {
                return null;
            }
        }
        Random rnd = ThreadLocalRandom.current();
        Rarity r = rarity == null ? rollRarity(rnd) : rarity;
        RpgSettings.RarityInfo info = settings.rarities.get(r);
        Map<String, Double> rolled = ItemRoll.roll(t.bonus(), info.multiplier(), rnd);

        ItemStack item = new ItemStack(t.material());
        ItemMeta meta = item.getItemMeta();
        applyModel(meta, t);
        meta.displayName(Messages.parse(info.color() + "<name>", Messages.vars("name", t.name())));
        List<Component> lore = new ArrayList<>();
        lore.add(msg.get("item.lore_raridade", Placeholder.component("raridade",
                Messages.parse(info.color() + "<n>", Messages.vars("n", info.name())))));
        lore.add(msg.get("item.lore_level", Messages.vars("level", t.minLevel())));
        EquipmentSlotGroup group = t.slot().getGroup();

        // Ao pôr modificadores, o item perde os padrões do material (dano da espada etc.): copia antes.
        Multimap<Attribute, AttributeModifier> defaults = t.material().getDefaultAttributeModifiers(t.slot());
        defaults.forEach(meta::addAttributeModifier);
        rolled.forEach((stat, value) -> {
            if (value == 0) {
                return;
            }
            BonusType bt = BONUS.get(stat);
            if (bt.attribute() == null) {
                meta.getPersistentDataContainer().set(keyCombat.get(stat), PersistentDataType.DOUBLE, value);
                lore.add(msg.get("item.lore_bonus", Messages.vars("valor", fmt(value), "unidade", "%"),
                        Placeholder.component("nome", msg.get("item.bonus_" + stat))));
                return;
            }
            double amount = bt.percent() ? value / 100.0 : value;
            NamespacedKey key = new NamespacedKey(plugin, "item_" + stat + "_" + t.slot().name().toLowerCase(Locale.ROOT));
            meta.addAttributeModifier(bt.attribute(), new AttributeModifier(key, amount, bt.op(), group));
            lore.add(msg.get("item.lore_bonus", Messages.vars("valor", fmt(value), "unidade", bt.percent() ? "%" : ""),
                    Placeholder.component("nome", msg.get("item.bonus_" + stat))));
        });
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyId, PersistentDataType.STRING, t.id());
        pdc.set(keyRarity, PersistentDataType.STRING, r.key());
        pdc.set(keyLevel, PersistentDataType.INTEGER, t.minLevel());
        pdc.set(keySlot, PersistentDataType.STRING, t.slot().name());
        item.setItemMeta(meta);
        if (serial != null) {
            uniques.tag(item, serial);
        }
        return item;
    }

    /**
     * O visual do item no resource pack. {@code modelo: "aincrad:arma/elucidator"} aponta um
     * modelo de item (assets/aincrad/items/arma/elucidator.json); {@code modelo: 10005} é o
     * custom model data, para packs antigos ou do ItemsAdder.
     */
    private void applyModel(ItemMeta meta, Template t) {
        String model = t.model();
        if (model == null || model.isEmpty()) {
            return;
        }
        if (model.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(model.toLowerCase(Locale.ROOT));
            if (key == null) {
                plugin.getLogger().warning("items.yml: modelo inválido em " + t.id() + ": " + model);
            } else {
                meta.setItemModel(key);
            }
            return;
        }
        try {
            CustomModelDataComponent data = meta.getCustomModelDataComponent();
            data.setFloats(List.of((float) Integer.parseInt(model)));
            meta.setCustomModelDataComponent(data);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("items.yml: modelo inválido em " + t.id() + ": " + model);
        }
    }

    /**
     * A soma dos bônus de combate (crítico, esquiva...) dos itens que o jogador está usando. Um
     * item só conta no slot para o qual foi feito, e não conta se pede um level acima do dele.
     */
    public Map<String, Double> combat(Player p, int level) {
        Map<String, Double> total = new HashMap<>();
        for (EquipmentSlot slot : COMBAT_SLOTS) {
            ItemStack it = p.getInventory().getItem(slot);
            if (it == null || it.isEmpty()) {
                continue;
            }
            PersistentDataContainerView pdc = it.getPersistentDataContainer();
            if (!slot.name().equals(pdc.get(keySlot, PersistentDataType.STRING))) {
                continue;
            }
            Integer need = pdc.get(keyLevel, PersistentDataType.INTEGER);
            if (need != null && need > level) {
                continue;
            }
            keyCombat.forEach((stat, key) -> {
                Double v = pdc.get(key, PersistentDataType.DOUBLE);
                if (v != null) {
                    total.merge(stat, v, Double::sum);
                }
            });
        }
        return total;
    }

    private static final EquipmentSlot[] COMBAT_SLOTS = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Level mínimo gravado no item (0 = item comum, sem exigência). */
    public int requiredLevel(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return 0;
        }
        Integer v = it.getItemMeta().getPersistentDataContainer().get(keyLevel, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public Rarity rarity(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return Rarity.parse(it.getItemMeta().getPersistentDataContainer().get(keyRarity, PersistentDataType.STRING));
    }

    /** Modelos que podem cair de um mob desse level. */
    public List<Template> drops(int mobLevel) {
        List<Template> out = new ArrayList<>();
        for (Template t : templates.values()) {
            if (t.dropChance() > 0 && mobLevel >= t.dropMin() && mobLevel <= t.dropMax()) {
                out.add(t);
            }
        }
        return out;
    }
}
