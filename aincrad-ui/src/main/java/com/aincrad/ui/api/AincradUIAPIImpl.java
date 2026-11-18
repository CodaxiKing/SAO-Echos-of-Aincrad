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
    public void openAmount(Player player, Component titulo, long min, long max, long inicial, long passo,
                           java.util.function.LongConsumer escolhido) {
        long lo = Math.min(min, max);
        long hi = Math.max(min, max);
        long step = Math.max(1, passo);
        boolean wasOpen = plugin.sessions().isOpen(player);
        plugin.sessions().open(player, amountScreen(titulo, lo, hi, Math.max(lo, Math.min(hi, inicial)), step, wasOpen, escolhido));
    }

    private com.aincrad.ui.screen.Screen amountScreen(Component titulo, long lo, long hi, long value, long step, boolean wasOpen,
                                                      java.util.function.LongConsumer escolhido) {
        return plugin.screens().amount(titulo, lo, hi, value, step, (p, next) -> {
            MenuSession session = plugin.sessions().get(p);
            if (session != null) {
                // Troca a janela pela mesma com o valor novo, sem empilhar.
                session.replace(amountScreen(titulo, lo, hi, next, step, wasOpen, escolhido));
            }
        }, (p, s) -> {
            leaveConfirm(p, wasOpen);
            escolhido.accept(value);
        });
    }

    @Override
    public boolean banner(Player player, String faixa) {
        return plugin.banners().show(player, faixa, plugin.settings().bannerSeconds * 1000L);
    }

    @Override
    public void toast(Player player, Component titulo, Component detalhe) {
        plugin.toasts().show(player, titulo, detalhe);
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

    @Override
    public void setTrackerProvider(java.util.function.Function<Player, Component> provider) {
        plugin.hud().setTrackerProvider(provider);
    }

    @Override
    public void setHudBadgeProvider(java.util.function.Function<Player, Component> provider) {
        plugin.hud().setBadgeProvider(provider);
    }

    @Override
    public FlatApi flat() {
        return plugin.flat();
    }

    @Override
    public void setDeathGame(boolean ativo) {
        plugin.deathGame(ativo);
    }

    @Override
    public boolean isDeathGame() {
        return plugin.deathGame();
    }
}
