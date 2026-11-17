package com.aincrad.items.crystal;

import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Os cristais de crystals.yml e os itens deles (criar, reconhecer, gravar mensagem). */
public final class CrystalRegistry {

    private final Plugin plugin;
    private final Messages msg;
    private final NamespacedKey keyId;
    private final NamespacedKey keyText;
    private final NamespacedKey keyAuthor;
    private final NamespacedKey keyDate;
    private Map<String, CrystalDef> crystals = Map.of();
    private boolean models;

    public CrystalRegistry(Plugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
        keyId = new NamespacedKey(plugin, "cristal");
        keyText = new NamespacedKey(plugin, "gravacao");
        keyAuthor = new NamespacedKey(plugin, "gravacao_autor");
        keyDate = new NamespacedKey(plugin, "gravacao_data");
    }

    /** Lê a seção "cristais". {@code models}: usar os modelos do resource pack nos itens. */
    public List<String> load(ConfigurationSection root, boolean models) {
        this.models = models;
        return load(root);
    }

    List<String> load(ConfigurationSection root) {
        List<String> errors = new ArrayList<>();
        Map<String, CrystalDef> out = new LinkedHashMap<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                CrystalDef def = s == null ? null : CrystalDef.parse(id.toLowerCase(Locale.ROOT), s, errors);
                if (def != null) {
                    out.put(def.id(), def);
                }
            }
        }
        crystals = Collections.unmodifiableMap(out);
        return errors;
    }

    public CrystalDef get(String id) {
        return id == null ? null : crystals.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<CrystalDef> all() {
        return crystals.values();
    }

    /** O primeiro cristal do tipo (para a API aceitar "cura", "teleporte"... mesmo com ids diferentes). */
    public CrystalDef firstOf(CrystalDef.Type type) {
        for (CrystalDef d : crystals.values()) {
            if (d.type() == type) {
                return d;
            }
        }
        return null;
    }

    /** O cristal que o item é, ou null. */
    public CrystalDef of(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return get(it.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING));
    }

    private void look(ItemMeta meta, String model) {
        NamespacedKey key = models && model != null ? NamespacedKey.fromString(model) : null;
        if (key != null) {
            meta.setItemModel(key);
        } else {
            // Sem o modelo do pack: o material base, brilhando, para não parecer um item comum.
            meta.setEnchantmentGlintOverride(true);
        }
    }

    public ItemStack create(CrystalDef def, int amount) {
        Material m = Material.matchMaterial(def.material());
        if (m == null || !m.isItem()) {
            plugin.getLogger().warning("crystals.yml: material \"" + def.material() + "\" de " + def.id() + " não existe; usando AMETHYST_SHARD.");
            m = Material.AMETHYST_SHARD;
        }
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Messages.parse(def.name()));
        List<Component> lore = new ArrayList<>();
        def.description().forEach(l -> lore.add(Messages.parse(l)));
        if (def.cooldown() > 0) {
            lore.add(msg.get("cristal.lore_recarga", Messages.vars("segundos", def.cooldown())));
        }
        if (def.consumed()) {
            lore.add(msg.get("cristal.lore_consumido"));
        }
        meta.lore(lore);
        look(meta, def.model());
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, def.id());
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ gravação

    /** Uma mensagem gravada num cristal. */
    public record Recording(String text, String author, String date) {
    }

    public Recording recording(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        String text = pdc.get(keyText, PersistentDataType.STRING);
        return text == null ? null : new Recording(text, pdc.getOrDefault(keyAuthor, PersistentDataType.STRING, "?"),
                pdc.getOrDefault(keyDate, PersistentDataType.STRING, "?"));
    }

    /** Devolve uma cópia (de 1 unidade) do cristal com a mensagem gravada. */
    public ItemStack record(ItemStack blank, CrystalDef def, Recording r) {
        ItemStack it = blank.clone();
        it.setAmount(1);
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("autor", r.author(), "data", r.date());
        meta.displayName(msg.get("gravacao.nome_gravado", vars));
        meta.lore(msg.list("gravacao.lore_gravado", vars));
        look(meta, def.recordedModel() != null ? def.recordedModel() : def.model());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyText, PersistentDataType.STRING, r.text());
        pdc.set(keyAuthor, PersistentDataType.STRING, r.author());
        pdc.set(keyDate, PersistentDataType.STRING, r.date());
        it.setItemMeta(meta);
        return it;
    }
}
