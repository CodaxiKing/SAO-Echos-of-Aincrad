package com.aincrad.items.api;

import com.aincrad.items.crystal.CrystalDef;
import com.aincrad.items.crystal.CrystalRegistry;
import com.aincrad.items.crystal.Resurrection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * API do AincradItems. Métodos estáticos e a {@link #instance()} registrada no ServicesManager.
 * Use na thread principal. Evento: {@link CrystalUseEvent} (cancelável).
 */
public final class AincradItemsAPI {

    private static final AincradItemsAPI INSTANCE = new AincradItemsAPI();
    private static CrystalRegistry registry;
    private static Resurrection resurrection;

    private AincradItemsAPI() {
    }

    public static void init(CrystalRegistry r, Resurrection res) {
        registry = r;
        resurrection = res;
    }

    public static AincradItemsAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return registry != null;
    }

    private static CrystalDef find(String type) {
        if (registry == null || type == null) {
            return null;
        }
        CrystalDef def = registry.get(type);
        if (def == null) {
            CrystalDef.Type t = CrystalDef.Type.parse(type);
            def = t == null ? null : registry.firstOf(t);
        }
        return def;
    }

    /** Ids dos cristais de crystals.yml. */
    public static List<String> getCrystals() {
        List<String> out = new ArrayList<>();
        if (registry != null) {
            registry.all().forEach(d -> out.add(d.id()));
        }
        return out;
    }

    /**
     * Cria o item de um cristal (para recompensas e lojas de outros plugins).
     *
     * @param type id de crystals.yml, ou o tipo ("cura", "antidoto", "teleporte", "ressurreicao", "gravacao")
     * @return o item, ou null se não existe
     */
    public static ItemStack createCrystal(String type, int amount) {
        CrystalDef def = find(type);
        return def == null ? null : registry.create(def, amount);
    }

    /**
     * Faz do cristal um cristal puro: a recarga dele é multiplicada por {@code factor} (0.5 =
     * metade). Para o alquimista do AincradLife.
     */
    public static void setCooldownFactor(ItemStack crystal, double factor) {
        com.aincrad.items.crystal.CrystalService.setCooldownFactor(crystal, factor);
    }

    /** Dá cristais ao jogador (o que não couber cai no chão). @return false se o cristal não existe */
    public static boolean giveCrystal(Player player, String type, int amount) {
        CrystalDef def = find(type);
        if (def == null || amount <= 0) {
            return false;
        }
        int left = amount;
        while (left > 0) {
            ItemStack it = registry.create(def, left);
            left -= it.getAmount();
            player.getInventory().addItem(it).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        return true;
    }

    /** Id do cristal que o item é, ou null. */
    public static String getCrystalId(ItemStack item) {
        CrystalDef def = registry == null ? null : registry.of(item);
        return def == null ? null : def.id();
    }

    /** O jogador levou um golpe fatal e está na janela de ressurreição? */
    public static boolean isDying(Player player) {
        return resurrection != null && resurrection.isDying(player);
    }
}
