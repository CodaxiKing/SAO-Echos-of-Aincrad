package com.aincrad.core;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Disparado por {@code /aincrad reload}. Cada plugin Aincrad escuta, recarrega as próprias
 * configs e chama {@link #reloaded(String)} com o nome, que aparece na resposta do comando.
 */
public final class AincradReloadEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final List<String> reloaded = new ArrayList<>();

    public void reloaded(String pluginName) {
        reloaded.add(pluginName);
    }

    public List<String> reloadedPlugins() {
        return List.copyOf(reloaded);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
