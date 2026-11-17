package com.aincrad.floors.listener;

import com.aincrad.floors.FloorService;
import com.aincrad.floors.model.Floor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;

/**
 * Barra a entrada em andar bloqueado pelos três caminhos — teleporte (inclui portais e troca de
 * mundo), caminhada para dentro de uma região de andar e entrar no servidor já lá dentro — e
 * acompanha em que andar cada jogador está.
 */
public final class EntryGuard implements Listener {

    private final Plugin plugin;
    private final FloorService floors;

    public EntryGuard(Plugin plugin, FloorService floors) {
        this.plugin = plugin;
        this.floors = floors;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            floors.store().loadPlayerBlocking(event.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("Não foi possível carregar os andares de " + event.getName() + ": " + e.getMessage());
        }
    }

    /** true se o jogador não pode ir de {@code from} para {@code to}. */
    private boolean blocked(Player p, Location from, Location to) {
        Floor target = floors.floorAt(to);
        if (target == null || floors.canEnter(p, target.number())) {
            return false;
        }
        // Já está dentro (o andar foi bloqueado de novo com ele lá): deixa andar, para poder sair.
        Floor here = floors.floorAt(from);
        if (here != null && here.number() == target.number()) {
            return false;
        }
        floors.deny(p, target.number());
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (blocked(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (blocked(event.getPlayer(), from, to)) {
            event.setCancelled(true);
            return;
        }
        floors.track(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleported(PlayerTeleportEvent event) {
        floors.track(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        floors.track(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        floors.track(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // Um tick depois: o perfil do core e os outros plugins já carregaram.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            floors.arrive(p);
            Floor here = floors.floorAt(p.getLocation());
            if (here != null && !floors.canEnter(p, here.number()) && floors.config().bool("expulsar_ao_entrar", true)) {
                Location out = floors.fallbackSpawn();
                if (out != null) {
                    p.teleportAsync(out);
                    floors.messages().send(p, "andar.expulso");
                    return;
                }
            }
            floors.track(p, p.getLocation());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        floors.untrack(event.getPlayer().getUniqueId());
        floors.store().forget(event.getPlayer().getUniqueId());
    }
}
