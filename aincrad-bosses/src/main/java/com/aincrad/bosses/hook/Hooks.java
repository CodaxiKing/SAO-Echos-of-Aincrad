package com.aincrad.bosses.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Pontes para os plugins opcionais. As classes {@link RpgBridge} e {@link EcoBridge} usam a API
 * dos outros plugins e só são tocadas quando eles estão ligados; o AincradSocial vai por reflexão.
 */
public final class Hooks {

    private final Logger log;
    private boolean rpg;
    private boolean economy;

    public Hooks(Logger log) {
        this.log = log;
        detect();
    }

    public void detect() {
        rpg = Bukkit.getPluginManager().isPluginEnabled("AincradRPG");
        economy = Bukkit.getPluginManager().isPluginEnabled("AincradEconomy");
    }

    public boolean hasRpg() {
        return rpg;
    }

    /** XP do RPG (sem ele, nada). */
    public long giveXp(Player p, long amount) {
        if (!rpg || amount <= 0) {
            return 0;
        }
        try {
            return RpgBridge.addXp(p, amount);
        } catch (LinkageError | RuntimeException e) {
            log.warning("AincradRPG incompatível (XP): " + e);
            rpg = false;
            return 0;
        }
    }

    /** Col (sem a economia, nada). */
    public long giveCol(Player p, long amount) {
        if (!economy || amount <= 0) {
            return 0;
        }
        try {
            return EcoBridge.deposit(p, amount) ? amount : 0;
        } catch (LinkageError | RuntimeException e) {
            log.warning("AincradEconomy incompatível (Col): " + e);
            economy = false;
            return 0;
        }
    }

    public boolean hasEconomy() {
        return economy;
    }

    /** Cobra Col do jogador. False se ele não tem o bastante ou não há economia. */
    public boolean takeCol(Player p, long amount, String reason) {
        if (!economy || amount <= 0) {
            return amount <= 0;
        }
        try {
            return EcoBridge.withdraw(p, amount, reason);
        } catch (LinkageError | RuntimeException e) {
            log.warning("AincradEconomy incompatível (cobrança): " + e);
            return false;
        }
    }

    /** Dá level a um mob (lacaios, labirinto). Sem o RPG, aumenta a vida 12% por level. */
    public void setMobLevel(LivingEntity e, int level) {
        if (rpg) {
            try {
                RpgBridge.setMobLevel(e, level);
                return;
            } catch (LinkageError | RuntimeException ex) {
                log.warning("AincradRPG incompatível (level de mob): " + ex);
                rpg = false;
            }
        }
        var hp = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (hp != null && level > 1) {
            hp.setBaseValue(hp.getBaseValue() * (1 + 0.12 * (level - 1)));
            e.setHealth(hp.getValue());
        }
    }

    /**
     * Item de recompensa: material do Minecraft, ou "rpg:id[:raridade]" (items.yml do AincradRPG).
     * Null se o material não existe ou o RPG não está ligado.
     */
    public ItemStack item(String spec, int amount) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        if (spec.startsWith("b64:")) {
            try {
                ItemStack it = ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(spec.substring(4)));
                it.setAmount(Math.min(amount, it.getMaxStackSize()));
                return it;
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (spec.toLowerCase(Locale.ROOT).startsWith("rpg:")) {
            if (!rpg) {
                return null;
            }
            String[] p = spec.split(":");
            try {
                ItemStack it = RpgBridge.createItem(p[1], p.length > 2 ? p[2] : null);
                if (it != null) {
                    it.setAmount(Math.min(amount, it.getMaxStackSize()));
                }
                return it;
            } catch (LinkageError | RuntimeException e) {
                log.warning("AincradRPG incompatível (item " + spec + "): " + e);
                return null;
            }
        }
        if (spec.toLowerCase(Locale.ROOT).startsWith("cristal:")) {
            // Cristal do AincradItems (ele liga depois deste plugin: vai por reflexão).
            try {
                Object it = Class.forName("com.aincrad.items.api.AincradItemsAPI", true,
                                org.bukkit.Bukkit.getPluginManager().getPlugin("AincradItems").getClass().getClassLoader())
                        .getMethod("createCrystal", String.class, int.class).invoke(null, spec.substring(8), amount);
                return it instanceof ItemStack stack ? stack : null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.warning("AincradItems ausente ou incompatível (item " + spec + "): " + e);
                return null;
            }
        }
        Material m = Material.matchMaterial(spec);
        return m == null || !m.isItem() ? null : new ItemStack(m, Math.min(amount, m.getMaxStackSize()));
    }

    /**
     * O texto que representa o item nas recompensas: o material (item comum), "rpg:id:raridade"
     * (item do AincradRPG, sorteado de novo a cada entrega) ou "b64:..." (qualquer outro item
     * com nome, lore ou encantos, guardado inteiro).
     */
    public String spec(ItemStack it) {
        if (!it.hasItemMeta()) {
            return it.getType().name();
        }
        var pdc = it.getItemMeta().getPersistentDataContainer();
        org.bukkit.NamespacedKey idKey = org.bukkit.NamespacedKey.fromString("aincradrpg:item");
        org.bukkit.NamespacedKey rarKey = org.bukkit.NamespacedKey.fromString("aincradrpg:raridade");
        String id = idKey == null ? null : pdc.get(idKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (id != null) {
            String rar = rarKey == null ? null : pdc.get(rarKey, org.bukkit.persistence.PersistentDataType.STRING);
            return "rpg:" + id + (rar == null ? "" : ":" + rar);
        }
        ItemStack one = it.clone();
        one.setAmount(1);
        return "b64:" + java.util.Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    /** Avisa o AincradSocial da vitória (XP de guilda, registro). */
    public void recordBossKill(Collection<UUID> participants, int floor, UUID lastAttacker) {
        try {
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (c.getName().equals("com.aincrad.social.api.AincradSocialAPI")) {
                    RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                    if (r != null) {
                        c.getMethod("recordBossKill", Collection.class, int.class, UUID.class).invoke(r.getProvider(), participants, floor, lastAttacker);
                    }
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("AincradSocial incompatível (recordBossKill): " + e);
        }
    }
}
