package com.aincrad.ui.api;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.menu.MenuRegistry;
import com.aincrad.ui.screen.MenuSession;
import com.aincrad.ui.screen.Screen;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Implementação registrada no ServicesManager. */
public final class AincradUIAPIImpl implements AincradUIAPI {

    private final AincradUIPlugin plugin;

    public AincradUIAPIImpl(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerMenuEntry(String id, String icone, MenuAction acao) {
        registerMenuEntry(id, icone, null, MenuRegistry.DEFAULT_API_GROUP, acao);
    }

    @Override
    public void registerMenuEntry(String id, String icone, Component nome, String grupo, MenuAction acao) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(acao, "acao");
        String group = grupo == null || plugin.menu().group(grupo) == null ? MenuRegistry.DEFAULT_API_GROUP : grupo;
        plugin.menu().register(new MenuRegistry.ApiEntry(id, icone, nome, group, acao));
    }

    @Override
    public void unregisterMenuEntry(String id) {
        plugin.menu().unregister(id);
    }

    @Override
    public boolean openScreen(Player player, String tela) {
        Screen s = plugin.screens().byName(player, tela);
        if (s == null) {
            return false;
        }
        plugin.sessions().open(player, s);
        return true;
    }

    @Override
    public void openConfirm(Player player, Component pergunta, MenuAction sim, MenuAction nao) {
        boolean wasOpen = plugin.sessions().isOpen(player);
        plugin.sessions().open(player, plugin.screens().confirm(pergunta,
                (p, s) -> {
                    leaveConfirm(p, wasOpen);
                    if (sim != null) {
                        sim.run(p);
                    }
                },
                (p, s) -> {
                    leaveConfirm(p, wasOpen);
                    if (nao != null) {
                        nao.run(p);
                    }
                }));
    }

    private void leaveConfirm(Player p, boolean wasOpen) {
        if (wasOpen) {
            plugin.sessions().back(p);
        } else {
            plugin.sessions().close(p, false);
        }
    }

    @Override
    public void openList(Player player, UiList lista) {
        plugin.sessions().open(player, plugin.screens().list(player, Objects.requireNonNull(lista, "lista"), 0));
    }

    @Override
    public void replaceList(Player player, UiList lista) {
        MenuSession s = plugin.sessions().get(player);
        Screen screen = plugin.screens().list(player, Objects.requireNonNull(lista, "lista"), 0);
        if (s == null) {
            plugin.sessions().open(player, screen);
        } else {
            s.replace(screen);
        }
    }

    @Override
    public void openWindow(Player player, Component titulo, java.util.List<Component> linhas, java.util.List<UiList.Button> botoes) {
        plugin.sessions().open(player, plugin.screens().textWindow(titulo, linhas, botoes));
    }

    @Override
    public void back(Player player) {
        plugin.sessions().back(player);
    }

    @Override
    public boolean isMenuOpen(Player player) {
        return plugin.sessions().isOpen(player);
    }

    @Override
    public void closeMenu(Player player) {
        plugin.sessions().close(player, true);
    }

    @Override
    public void setPartyHudProvider(PartyHudProvider provider) {
        plugin.hud().setPartyProvider(provider);
    }
}
