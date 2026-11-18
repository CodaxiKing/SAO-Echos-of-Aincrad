package com.aincrad.ui.hud;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.flat.FlatOverlay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Faixas no meio da tela, por um instante: "WARNING" quando a vida fica crítica, "Immortal
 * Object" quando o golpe é barrado numa zona segura, o losango do duelo quando ele começa. São as
 * artes do anime (ui/banner), desenhadas pela HUD em barras.
 */
public final class BannerService implements Listener {

    private record Shown(String id, long until) {
    }

    private final AincradUIPlugin plugin;
    private final Map<UUID, Shown> shown = new HashMap<>();

    public BannerService(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Mostra a faixa por {@code millis}.
     *
     * @return false se o jogador não vê a HUD em barras, ou a faixa não existe
     */
    public boolean show(Player p, String id, long millis) {
        if (!FlatOverlay.BANNERS.contains(id) || !plugin.settings().bannersEnabled || !plugin.toasts().onScreen(p)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Shown old = shown.get(p.getUniqueId());
        // A mesma faixa pedida de novo enquanto está na tela só estica o tempo (o golpe repetido na cidade).
        shown.put(p.getUniqueId(), new Shown(id, now + millis));
        if (old == null || old.until() < now || !old.id().equals(id)) {
            plugin.hud().update(p);
        }
        return true;
    }

    /** A faixa na tela do jogador agora, ou null. */
    public String active(Player p) {
        Shown s = shown.get(p.getUniqueId());
        if (s == null) {
            return null;
        }
        if (s.until() < System.currentTimeMillis()) {
            shown.remove(p.getUniqueId());
            return null;
        }
        return s.id();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shown.remove(event.getPlayer().getUniqueId());
    }
}
