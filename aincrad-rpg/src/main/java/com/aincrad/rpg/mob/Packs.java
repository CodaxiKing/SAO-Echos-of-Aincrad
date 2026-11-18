package com.aincrad.rpg.mob;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * O que os plugins de modelo 3D do servidor oferecem, para os menus escolherem sem digitar:
 * os mobs do MythicMobs, os modelos do ModelEngine e os itens do ItemsAdder. Tudo por
 * reflexão (nenhum deles entra na compilação); sem o plugin, as listas vêm vazias.
 */
public final class Packs {

    private Packs() {
    }

    private static boolean on(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    private static List<String> sorted(Object names) {
        List<String> out = new ArrayList<>();
        if (names instanceof Collection<?> c) {
            for (Object o : c) {
                out.add(String.valueOf(o));
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Os nomes internos dos mobs do MythicMobs 5. */
    public static List<String> mythicMobs() {
        if (!on("MythicMobs")) {
            return List.of();
        }
        try {
            Class<?> c = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object manager = c.getMethod("getMobManager").invoke(c.getMethod("inst").invoke(null));
            return sorted(manager.getClass().getMethod("getMobNames").invoke(manager));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return List.of();
        }
    }

    /** Os modelos carregados no ModelEngine 4. */
    public static List<String> models() {
        if (!on("ModelEngine")) {
            return List.of();
        }
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object registry = api.getMethod("getModelRegistry").invoke(api.getMethod("getAPI").invoke(null));
            for (String name : new String[]{"getKeys", "getOrderedId", "keySet"}) {
                try {
                    Method m = registry.getClass().getMethod(name);
                    List<String> out = sorted(m.invoke(registry));
                    if (!out.isEmpty()) {
                        return out;
                    }
                } catch (NoSuchMethodException ignored) {
                    // outra versão do ModelEngine: tenta o próximo nome
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return List.of();
        }
        return List.of();
    }

    /** O id do ItemsAdder ("namespace:id") do item, ou null se não é um item dele. */
    public static String itemsAdderId(ItemStack it) {
        if (it == null || !on("ItemsAdder")) {
            return null;
        }
        try {
            Class<?> c = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = c.getMethod("byItemStack", ItemStack.class).invoke(null, it);
            return stack == null ? null : String.valueOf(c.getMethod("getNamespacedID").invoke(stack));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** O item do ItemsAdder com este id ("namespace:id"), ou null. */
    public static ItemStack itemsAdderItem(String id, int amount) {
        if (id == null || !on("ItemsAdder")) {
            return null;
        }
        try {
            Class<?> c = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = c.getMethod("getInstance", String.class).invoke(null, id);
            if (stack == null || !(c.getMethod("getItemStack").invoke(stack) instanceof ItemStack it)) {
                return null;
            }
            ItemStack copy = it.clone();
            copy.setAmount(Math.max(1, Math.min(copy.getMaxStackSize(), amount)));
            return copy;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }
}
