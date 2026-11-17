package com.aincrad.effects.pack;

import com.aincrad.effects.AincradEffectsPlugin;
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
import java.util.function.Consumer;

/** Envia o pack ao entrar e sabe quem está com ele, para trocar imagens por texto em quem não está. */
public final class PackStatus implements Listener {

    private final AincradEffectsPlugin plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    /** Chamado quando o jogador termina de responder ao pack (carregou, recusou ou falhou). */
    private Consumer<Player> onSettled = p -> { };

    public PackStatus(AincradEffectsPlugin plugin) {
        this.plugin = plugin;
    }

    public void onSettled(Consumer<Player> listener) {
        this.onSettled = listener;
    }

    public boolean hasPack(Player player) {
        return plugin.settings().packAssumeLoaded || loaded.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.pack().sendTo(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player p = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                loaded.add(p.getUniqueId());
                onSettled.accept(p);
            }
            case DECLINED -> {
                loaded.remove(p.getUniqueId());
                plugin.messages().send(p, "pack_recusado");
                onSettled.accept(p);
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                loaded.remove(p.getUniqueId());
                onSettled.accept(p);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }
}
