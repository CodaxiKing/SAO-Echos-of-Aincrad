package com.aincrad.life.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.util.UUID;

/**
 * Pontes para os plugins opcionais. {@link EcoBridge} e {@link FloorsBridge} usam as APIs deles
 * e só são tocadas quando estão ligados; o AincradEffects vai por reflexão.
 */
public final class Hooks {

    private final Plugin plugin;

    public Hooks(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasEconomy() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradEconomy");
    }

    public boolean hasFloors() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradFloors");
    }

    public boolean hasRpg() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradRPG");
    }

    public boolean hasEffects() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradEffects");
    }

    /**
     * Cobra um preço: em Col, com a economia; sem ela, em itens ({@code fallback} × quantidade, um
     * item a cada {@code colPerItem} Col, no mínimo 1).
     *
     * @return true se cobrou
     */
    public boolean charge(Player p, long col, Material fallback, long colPerItem, String reason) {
        if (col <= 0) {
            return true;
        }
        if (hasEconomy()) {
            try {
                return EcoBridge.withdraw(p, col, reason);
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("AincradEconomy incompatível (cobrança): " + e);
                return false;
            }
        }
        int items = (int) Math.max(1, Math.ceil(col / (double) Math.max(1, colPerItem)));
        if (!p.getInventory().containsAtLeast(new ItemStack(fallback), items)) {
            return false;
        }
        p.getInventory().removeItem(new ItemStack(fallback, items));
        return true;
    }

    /** Texto do preço: "500 Col" ou "5x Lingote de ouro" (sem economia). */
    public String price(long col, Material fallback, long colPerItem) {
        if (hasEconomy()) {
            try {
                return EcoBridge.format(col);
            } catch (LinkageError | RuntimeException e) {
                return col + " Col";
            }
        }
        return (int) Math.max(1, Math.ceil(col / (double) Math.max(1, colPerItem))) + "x " + fallback.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    /** Manda um item para a caixa de retirada do AincradEconomy. @return false sem a economia */
    public boolean mail(UUID player, ItemStack item, String reason) {
        if (!hasEconomy()) {
            return false;
        }
        try {
            return EcoBridge.mail(player, item, reason);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (caixa de retirada): " + e);
            return false;
        }
    }

    /** Leva o jogador ao andar 1 (a cidade inicial), neste servidor ou em outro. @return false sem o AincradFloors */
    public boolean toFirstFloor(Player p) {
        if (!hasFloors()) {
            return false;
        }
        try {
            return FloorsBridge.teleport(p, 1);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradFloors incompatível (retorno): " + e);
            return false;
        }
    }

    private Object effects() {
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals("com.aincrad.effects.api.AincradEffectsAPI")) {
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                return r == null ? null : r.getProvider();
            }
        }
        return null;
    }

    /** O jogador carregou o resource pack do servidor? (sem o AincradEffects: false) */
    public boolean hasPack(Player p) {
        Object api = effects();
        if (api == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(api.getClass().getMethod("hasPack", Player.class).invoke(api, p));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Entrega a pasta resourcepack/ ao pack único do AincradEffects. */
    public boolean registerPack(File folder) {
        Object api = effects();
        if (api == null) {
            return false;
        }
        try {
            api.getClass().getMethod("registerPackFolder", Plugin.class, File.class).invoke(api, plugin, folder);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Não foi possível registrar os modelos no AincradEffects: " + e);
            return false;
        }
    }
}
