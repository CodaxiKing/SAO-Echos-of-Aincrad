package com.aincrad.quests.hook;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ligação com o menu e a HUD do AincradUI, por reflexão (ele é opcional e não entra na
 * compilação): uma entrada no menu de ícones e a linha de rastreio embaixo da barra de vida.
 */
public final class UiLink {

    private static final String API = "com.aincrad.ui.api.AincradUIAPI";

    private UiLink() {
    }

    private static Object api() {
        if (!Bukkit.getPluginManager().isPluginEnabled("AincradUI")) {
            return null;
        }
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals(API)) {
                return Bukkit.getServicesManager().load(c);
            }
        }
        return null;
    }

    /** Liga a entrada {@code id} do menu (já existente em menu.yml) à ação. @return false sem o AincradUI */
    public static boolean menuEntry(String id, Consumer<Player> action) {
        Object ui = api();
        if (ui == null) {
            return false;
        }
        try {
            ClassLoader loader = ui.getClass().getClassLoader();
            Class<?> actionType = Class.forName("com.aincrad.ui.api.MenuAction", true, loader);
            Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{actionType}, (self, method, args) -> {
                switch (method.getName()) {
                    case "run" -> {
                        action.accept((Player) args[0]);
                        return null;
                    }
                    case "hashCode" -> {
                        return System.identityHashCode(self);
                    }
                    case "equals" -> {
                        return self == args[0];
                    }
                    default -> {
                        return "MenuAction(" + id + ")";
                    }
                }
            });
            Class.forName(API, true, loader).getMethod("registerMenuEntry", String.class, String.class, actionType).invoke(ui, id, null, proxy);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** A linha de rastreio da HUD. @return false sem o AincradUI (ou numa versão sem a linha) */
    public static boolean tracker(Function<Player, Component> provider) {
        Object ui = api();
        if (ui == null) {
            return false;
        }
        try {
            Class.forName(API, true, ui.getClass().getClassLoader()).getMethod("setTrackerProvider", Function.class).invoke(ui, provider);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }
}
