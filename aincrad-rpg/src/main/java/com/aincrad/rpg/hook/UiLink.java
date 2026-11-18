package com.aincrad.rpg.hook;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Ligação com o menu e a HUD do AincradUI, por reflexão (ele é opcional e não entra na
 * compilação): o botão "Status" no menu de ícones.
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

    /**
     * Cria (ou liga) a entrada {@code id} do menu, com ícone, nome e grupo.
     *
     * @param icon  glifo do AincradUI, ex.: under_icons/player
     * @param group grupo do menu.yml: perfil, party, mensagens, mapa ou config
     * @return false sem o AincradUI
     */
    public static boolean menuEntry(String id, String icon, Component name, String group, Consumer<Player> action) {
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
            Class.forName(API, true, loader).getMethod("registerMenuEntry", String.class, String.class, Component.class, String.class, actionType).invoke(ui, id, icon, name, group, proxy);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

}
