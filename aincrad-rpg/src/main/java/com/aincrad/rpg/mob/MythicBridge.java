package com.aincrad.rpg.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;

/**
 * Ponte para o MythicMobs 5, por reflexão (o plugin é opcional e não entra na compilação).
 * Usa o {@code BukkitAPIHelper}: {@code MythicBukkit.inst().getAPIHelper()}.
 */
final class MythicBridge {

    private Object helper;
    private Method spawn;
    private Method lookup;

    boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    private boolean ready() {
        if (!available()) {
            helper = null;
            return false;
        }
        if (helper != null) {
            return true;
        }
        try {
            Class<?> c = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = c.getMethod("inst").invoke(null);
            Object h = c.getMethod("getAPIHelper").invoke(inst);
            spawn = h.getClass().getMethod("spawnMythicMob", String.class, Location.class, int.class);
            try {
                lookup = h.getClass().getMethod("getMythicMob", String.class);
            } catch (NoSuchMethodException e) {
                lookup = null;
            }
            helper = h;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** O mob existe no MythicMobs? (true também quando não dá para saber.) */
    boolean exists(String id) {
        if (!ready()) {
            return false;
        }
        if (lookup == null) {
            return true;
        }
        try {
            return lookup.invoke(helper, id) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    /** Cria o mob. Null se o MythicMobs não está ligado ou não conhece o mob. */
    Entity spawn(String id, Location at, int level) {
        if (!ready()) {
            return null;
        }
        try {
            return spawn.invoke(helper, id, at, level) instanceof Entity e ? e : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
