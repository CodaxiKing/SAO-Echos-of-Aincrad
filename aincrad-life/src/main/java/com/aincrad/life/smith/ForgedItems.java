package com.aincrad.life.smith;

import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O que a forja e o aprimoramento gravam no próprio item: os bônus dos materiais, os níveis de
 * cada atributo, as tentativas que restam, a qualidade e o nome do ferreiro. {@link #refresh}
 * refaz os atributos, a descrição e o nome a partir desses dados.
 */
public final class ForgedItems {

    /** Os cinco atributos do aprimoramento e o bônus que cada nível dá. */
    public enum Upgrade {
        AFIACAO("dano"), RAPIDEZ("velocidade_ataque"), PRECISAO("critico"), PESO("empurrao"), DURABILIDADE("durabilidade");

        private final String stat;

        Upgrade(String stat) {
            this.stat = stat;
        }

        public String stat() {
            return stat;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record StatType(Attribute attribute, AttributeModifier.Operation op, boolean percent) {
    }

    private static final Map<String, StatType> ATTRIBUTES = new LinkedHashMap<>();

    static {
        ATTRIBUTES.put("dano", new StatType(Attribute.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, false));
        ATTRIBUTES.put("velocidade_ataque", new StatType(Attribute.ATTACK_SPEED, AttributeModifier.Operation.ADD_SCALAR, true));
        ATTRIBUTES.put("armadura", new StatType(Attribute.ARMOR, AttributeModifier.Operation.ADD_NUMBER, false));
        ATTRIBUTES.put("resistencia", new StatType(Attribute.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_NUMBER, false));
        ATTRIBUTES.put("vida", new StatType(Attribute.MAX_HEALTH, AttributeModifier.Operation.ADD_NUMBER, false));
        ATTRIBUTES.put("empurrao", new StatType(Attribute.ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_NUMBER, false));
    }

    private final Plugin plugin;
    private final Messages msg;
    private final NamespacedKey keyBlueprint;
    private final NamespacedKey keyName;
    private final NamespacedKey keyColor;
    private final NamespacedKey keyStats;
    private final NamespacedKey keyUpgrades;
    private final NamespacedKey keyTries;
    private final NamespacedKey keyQuality;
    private final NamespacedKey keySmith;
    private final NamespacedKey keyCrit;
    private final NamespacedKey keyLore;
    private Map<Upgrade, Double> perLevel = Map.of();

    ForgedItems(Plugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
        keyBlueprint = new NamespacedKey(plugin, "forja");
        keyName = new NamespacedKey(plugin, "forja_nome");
        keyColor = new NamespacedKey(plugin, "forja_cor");
        keyStats = new NamespacedKey(plugin, "forja_bonus");
        keyUpgrades = new NamespacedKey(plugin, "forja_niveis");
        keyTries = new NamespacedKey(plugin, "forja_tentativas");
        keyQuality = new NamespacedKey(plugin, "forja_qualidade");
        keySmith = new NamespacedKey(plugin, "forja_ferreiro");
        keyCrit = new NamespacedKey(plugin, "forja_critico");
        keyLore = new NamespacedKey(plugin, "forja_linhas");
    }

    /** Quanto cada nível de cada atributo dá (config.yml → ferreiro.aprimoramento.por_nivel). */
    void perLevel(Map<Upgrade, Double> values) {
        this.perLevel = Map.copyOf(values);
    }

    // ------------------------------------------------------------------ leitura

    private static PersistentDataContainer pdc(ItemStack it) {
        return it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer();
    }

    /** O item pode ser reparado e aprimorado? (arma, ferramenta, armadura ou escudo: tem durabilidade) */
    public static boolean workable(ItemStack it) {
        return it != null && it.getType().getMaxDurability() > 0 && it.getAmount() == 1;
    }

    /** Tentativas de aprimoramento que restam (-1: o item nunca passou pela forja nem pelo aprimoramento). */
    public int tries(ItemStack it) {
        PersistentDataContainer p = pdc(it);
        Integer v = p == null ? null : p.get(keyTries, PersistentDataType.INTEGER);
        return v == null ? -1 : v;
    }

    /** Nível de um atributo no item. */
    public int level(ItemStack it, Upgrade u) {
        PersistentDataContainer p = pdc(it);
        return p == null ? 0 : (int) Math.round(ForgeMath.decode(p.get(keyUpgrades, PersistentDataType.STRING)).getOrDefault(u.key(), 0.0));
    }

    /** Chance de golpe crítico do item, em %. */
    public double crit(ItemStack it) {
        PersistentDataContainer p = pdc(it);
        return p == null ? 0 : p.getOrDefault(keyCrit, PersistentDataType.DOUBLE, 0.0);
    }

    /** Qualidade da forja, de 0 a 100 (0: não é item forjado). */
    public int quality(ItemStack it) {
        PersistentDataContainer p = pdc(it);
        return p == null ? 0 : p.getOrDefault(keyQuality, PersistentDataType.INTEGER, 0);
    }

    /** Id do projeto com que o item foi forjado (null: não é item forjado). */
    public String blueprint(ItemStack it) {
        PersistentDataContainer p = pdc(it);
        return p == null ? null : p.get(keyBlueprint, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ escrita

    /**
     * Marca um item recém-forjado.
     *
     * @param color cor do nome (tag do MiniMessage, pela raridade)
     */
    void stamp(ItemStack it, String blueprint, String name, String color, Map<String, Double> stats, int quality, int tries, String smith) {
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer p = meta.getPersistentDataContainer();
        p.set(keyBlueprint, PersistentDataType.STRING, blueprint);
        p.set(keyName, PersistentDataType.STRING, name);
        p.set(keyColor, PersistentDataType.STRING, color);
        p.set(keyStats, PersistentDataType.STRING, ForgeMath.encode(stats));
        p.set(keyQuality, PersistentDataType.INTEGER, quality);
        p.set(keyTries, PersistentDataType.INTEGER, tries);
        p.set(keySmith, PersistentDataType.STRING, smith);
        it.setItemMeta(meta);
        refresh(it);
    }

    /** Define as tentativas (um item que não veio da forja ganha as dele no primeiro aprimoramento). */
    void setTries(ItemStack it, int tries) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(keyTries, PersistentDataType.INTEGER, Math.max(0, tries));
        it.setItemMeta(meta);
        refresh(it);
    }

    /** Soma (ou tira) níveis de um atributo; nunca fica abaixo de zero. */
    void addLevel(ItemStack it, Upgrade u, int delta) {
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer p = meta.getPersistentDataContainer();
        Map<String, Double> levels = ForgeMath.decode(p.get(keyUpgrades, PersistentDataType.STRING));
        double now = Math.max(0, levels.getOrDefault(u.key(), 0.0) + delta);
        if (now <= 0) {
            levels.remove(u.key());
        } else {
            levels.put(u.key(), now);
        }
        p.set(keyUpgrades, PersistentDataType.STRING, ForgeMath.encode(levels));
        it.setItemMeta(meta);
        refresh(it);
    }

    /** Refaz os atributos, a durabilidade máxima, a descrição e o nome do item a partir do que está gravado nele. */
    void refresh(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer p = meta.getPersistentDataContainer();
        Map<String, Double> base = ForgeMath.decode(p.get(keyStats, PersistentDataType.STRING));
        Map<String, Double> levels = ForgeMath.decode(p.get(keyUpgrades, PersistentDataType.STRING));
        Map<String, Double> total = new LinkedHashMap<>(base);
        int plus = 0;
        for (Upgrade u : Upgrade.values()) {
            int level = (int) Math.round(levels.getOrDefault(u.key(), 0.0));
            if (level > 0) {
                plus += level;
                total.merge(u.stat(), level * perLevel.getOrDefault(u, 0.0), Double::sum);
            }
        }

        EquipmentSlot slot = it.getType().getEquipmentSlot();
        if (!meta.hasAttributeModifiers()) {
            // Ao pôr modificadores, o item perde os padrões do material (dano da espada etc.): copia antes.
            it.getType().getDefaultAttributeModifiers(slot).forEach(meta::addAttributeModifier);
        }
        for (Map.Entry<String, StatType> e : ATTRIBUTES.entrySet()) {
            StatType t = e.getValue();
            NamespacedKey key = new NamespacedKey(plugin, "forja_" + e.getKey());
            Collection<AttributeModifier> current = meta.getAttributeModifiers(t.attribute());
            if (current != null) {
                for (AttributeModifier m : new ArrayList<>(current)) {
                    if (m.getKey().equals(key)) {
                        meta.removeAttributeModifier(t.attribute(), m);
                    }
                }
            }
            double v = total.getOrDefault(e.getKey(), 0.0);
            if (v != 0) {
                meta.addAttributeModifier(t.attribute(), new AttributeModifier(key, t.percent() ? v / 100.0 : v, t.op(), slot.getGroup()));
            }
        }
        double crit = total.getOrDefault("critico", 0.0);
        if (crit > 0) {
            p.set(keyCrit, PersistentDataType.DOUBLE, crit);
        } else {
            p.remove(keyCrit);
        }
        if (meta instanceof Damageable d && it.getType().getMaxDurability() > 0) {
            double more = total.getOrDefault("durabilidade", 0.0);
            if (more > 0) {
                d.setMaxDamage((int) Math.round(it.getType().getMaxDurability() * (1 + more / 100.0)));
            } else if (d.hasMaxDamage()) {
                d.setMaxDamage(null);
            }
        }

        // Descrição: tira as linhas que este plugin pôs da última vez e põe as novas no fim.
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int mine = Math.min(lore.size(), p.getOrDefault(keyLore, PersistentDataType.INTEGER, 0));
        lore = new ArrayList<>(lore.subList(0, lore.size() - mine));
        List<Component> add = new ArrayList<>();
        total.forEach((stat, v) -> {
            if (v != 0 && msg.has("forja.bonus." + stat)) {
                add.add(Messages.parse(msg.raw("forja.bonus." + stat).replace("<valor>", number(v))));
            }
        });
        for (Upgrade u : Upgrade.values()) {
            int level = (int) Math.round(levels.getOrDefault(u.key(), 0.0));
            if (level > 0) {
                add.add(msg.get("forja.lore_nivel", Messages.vars("atributo", msg.raw("forja.atributo." + u.key()), "nivel", level)));
            }
        }
        Integer quality = p.get(keyQuality, PersistentDataType.INTEGER);
        if (quality != null) {
            add.add(msg.get("forja.lore_qualidade", Messages.vars("qualidade", quality)));
        }
        Integer tries = p.get(keyTries, PersistentDataType.INTEGER);
        if (tries != null) {
            add.add(msg.get("forja.lore_tentativas", Messages.vars("tentativas", tries)));
        }
        String smith = p.get(keySmith, PersistentDataType.STRING);
        if (smith != null) {
            add.add(msg.get("forja.lore_ferreiro", Messages.vars("ferreiro", smith)));
        }
        lore.addAll(add);
        meta.lore(lore);
        p.set(keyLore, PersistentDataType.INTEGER, add.size());

        String name = p.get(keyName, PersistentDataType.STRING);
        if (name != null) {
            String color = p.getOrDefault(keyColor, PersistentDataType.STRING, "<white>");
            meta.displayName(Messages.parse(color + "<nome>" + (plus > 0 ? " <yellow>+" + plus : ""), Messages.vars("nome", name)));
        }
        it.setItemMeta(meta);
    }

    static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
