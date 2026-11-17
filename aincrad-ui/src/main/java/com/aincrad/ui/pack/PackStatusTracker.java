package com.aincrad.ui.pack;

import com.aincrad.ui.AincradUIPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sabe quem está com o resource pack carregado, para escolher entre arte e texto reserva. */
public final class PackStatusTracker implements Listener {

    private final AincradUIPlugin plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public PackStatusTracker(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasPack(Player player) {
        return plugin.settings().packAssumeLoaded || loaded.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player p = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(p.getUniqueId());
            case DECLINED -> {
                loaded.remove(p.getUniqueId());
                plugin.messages().send(p, "pack.recusado");
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> loaded.remove(p.getUniqueId());
            default -> {
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.pack().sendTo(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }
}
