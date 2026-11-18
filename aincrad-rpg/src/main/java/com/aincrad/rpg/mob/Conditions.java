package com.aincrad.rpg.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * O clima e o período de um lugar, para os mobs que só aparecem com névoa ou de noite, e os itens
 * de outros plugins. O clima vem do AincradWorld e os ingredientes do AincradLife, por reflexão
 * (os dois ligam depois deste plugin).
 */
final class Conditions {

    private Conditions() {
    }

    /** É noite no mundo? (do pôr ao nascer do sol) */
    static boolean night(World w) {
        long t = Math.floorMod(w.getTime(), 24000L);
        return w.getEnvironment() == World.Environment.NORMAL && t >= 13000 && t < 23000;
    }

    private static Method method(String plugin, String className, String name, Class<?>... params) {
        Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
        if (p == null || !p.isEnabled()) {
            return null;
        }
        try {
            return Class.forName(className, true, p.getClass().getClassLoader()).getMethod(name, params);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** "limpo", "chuva", "neve" ou "nevoa". Sem o AincradWorld: "limpo". */
    static String weather(Location l) {
        Method m = method("AincradWorld", "com.aincrad.world.api.AincradWorldAPI", "getWeather", Location.class);
        if (m != null) {
            try {
                Object r = m.invoke(null, l);
                return r == null ? "limpo" : r.toString();
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "limpo";
            }
        }
        return "limpo";
    }

    /** Um ingrediente do AincradLife ({@code ingredients.yml}), ou null. */
    static ItemStack lifeIngredient(String id, int amount) {
        Method m = method("AincradLife", "com.aincrad.life.api.AincradLifeAPI", "createIngredient", String.class, int.class);
        if (m != null) {
            try {
                return m.invoke(null, id, amount) instanceof ItemStack it ? it : null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
