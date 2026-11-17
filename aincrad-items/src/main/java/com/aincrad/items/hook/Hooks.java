package com.aincrad.items.hook;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pontes para os plugins opcionais: áreas anti-cristal do AincradBosses e o pack do AincradEffects. */
public final class Hooks {

    private final Plugin plugin;
    private Method crystalBlocked;
    private boolean lookedBosses;
    private Set<String> blockedRegions = Set.of();

    public Hooks(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigFile config) {
        Set<String> s = new HashSet<>();
        config.yaml().getStringList("anti_cristal.regioes").forEach(r -> s.add(r.toLowerCase(Locale.ROOT)));
        blockedRegions = s;
        lookedBosses = false;
        crystalBlocked = null;
    }

    public boolean hasFloors() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradFloors");
    }

    public boolean hasEffects() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradEffects");
    }

    /** Nenhum cristal funciona aqui? (sala de chefe anti-cristal ou região da config) */
    public boolean blocked(Location l) {
        if (!blockedRegions.isEmpty()) {
            for (Region r : AincradCore.get().regions().at(l)) {
                if (blockedRegions.contains(r.name().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        if (!lookedBosses) {
            lookedBosses = true;
            Plugin bosses = Bukkit.getPluginManager().getPlugin("AincradBosses");
            if (bosses != null && bosses.isEnabled()) {
                try {
                    crystalBlocked = Class.forName("com.aincrad.bosses.api.AincradBossesAPI", true, bosses.getClass().getClassLoader())
                            .getMethod("isCrystalBlocked", Location.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    plugin.getLogger().warning("AincradBosses sem isCrystalBlocked: as salas de chefe não bloqueiam cristais.");
                }
            }
        }
        if (crystalBlocked != null) {
            try {
                return Boolean.TRUE.equals(crystalBlocked.invoke(null, l));
            } catch (ReflectiveOperationException | RuntimeException e) {
                crystalBlocked = null;
            }
        }
        return false;
    }

    /** Entrega a pasta resourcepack/ ao pack único do AincradEffects. @return true se registrou */
    public boolean registerPack(File folder) {
        try {
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (c.getName().equals("com.aincrad.effects.api.AincradEffectsAPI")) {
                    RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                    if (r == null) {
                        return false;
                    }
                    c.getMethod("registerPackFolder", Plugin.class, File.class).invoke(r.getProvider(), plugin, folder);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Não foi possível registrar os modelos no AincradEffects: " + e);
        }
        return false;
    }
}
