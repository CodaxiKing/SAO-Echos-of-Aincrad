package com.aincrad.ui.api;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.menu.MenuRegistry;
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
        plugin.sessions().open(player, plugin.screens().confirm(pergunta,
                (p, s) -> {
                    plugin.sessions().close(p, false);
                    if (sim != null) {
                        sim.run(p);
                    }
                },
                (p, s) -> {
                    plugin.sessions().close(p, true);
                    if (nao != null) {
                        nao.run(p);
                    }
                }));
    }

    @Override
    public boolean isMenuOpen(Player player) {
        return plugin.sessions().isOpen(player);
    }

    @Override
    public void closeMenu(Player player) {
        plugin.sessions().close(player, true);
    }
}
