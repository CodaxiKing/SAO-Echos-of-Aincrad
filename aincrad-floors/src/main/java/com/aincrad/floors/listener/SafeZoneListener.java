package com.aincrad.floors.listener;

import com.aincrad.core.region.RegionEnterEvent;
import com.aincrad.core.region.RegionLeaveEvent;
import com.aincrad.floors.FloorService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * Zona segura (a região "cidade" de cada andar): nenhum dano de nenhuma fonte, sem PvP, sem
 * fome, mobs hostis não nascem nem ficam, blocos não quebram. Quem tem aincrad.floors.bypass
 * constrói e ataca normalmente (para montar a cidade).
 */
public final class SafeZoneListener implements Listener {

    /** Nascimentos que um admin fez de propósito continuam valendo. */
    private static final Set<CreatureSpawnEvent.SpawnReason> ALLOWED = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.CUSTOM, CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);

    private final FloorService floors;

    public SafeZoneListener(FloorService floors) {
        this.floors = floors;
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        return damager instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!floors.isSafeZone(event.getEntity().getLocation())) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        // Outras entidades (NPCs, molduras, animais): só um admin com bypass machuca.
        Player by = event instanceof EntityDamageByEntityEvent e ? attacker(e.getDamager()) : null;
        if (by == null || !by.hasPermission(FloorService.BYPASS)) {
            event.setCancelled(true);
        }
    }

    /** Quem está fora não acerta quem está dentro, e quem está dentro não acerta quem está fora. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttackFromInside(EntityDamageByEntityEvent event) {
        Player by = attacker(event.getDamager());
        if (by != null && event.getEntity() instanceof Player && floors.isSafeZone(by.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && event.getFoodLevel() < p.getFoodLevel() && floors.isSafeZone(p.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player p && floors.isSafeZone(p.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster && !ALLOWED.contains(event.getSpawnReason())
                && floors.isSafeZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Roda de tempos em tempos: mob hostil sem nome que entrou numa cidade some. */
    public void sweep() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!floors.isSafeZone(p.getLocation())) {
                continue;
            }
            for (Entity e : p.getNearbyEntities(32, 16, 32)) {
                if (e instanceof Monster && e.customName() == null && floors.isSafeZone(e.getLocation())) {
                    e.remove();
                }
            }
        }
    }

    private void build(Cancellable event, Player p, org.bukkit.Location l) {
        if (!p.hasPermission(FloorService.BYPASS) && floors.isSafeZone(l)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        build(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        build(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        build(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        build(event, event.getPlayer(), event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> floors.isSafeZone(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> floors.isSafeZone(b.getLocation()));
    }

    // ------------------------------------------------------------------ avisos

    private void warn(Player p, String key) {
        String how = floors.config().string("zona_segura.aviso", "actionbar");
        if (how.equalsIgnoreCase("titulo")) {
            p.showTitle(net.kyori.adventure.title.Title.title(net.kyori.adventure.text.Component.empty(), floors.messages().get(key),
                    net.kyori.adventure.title.Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
        } else if (how.equalsIgnoreCase("actionbar")) {
            floors.messages().actionBar(p, key);
        }
    }

    @EventHandler
    public void onEnter(RegionEnterEvent event) {
        if (floors.isCity(event.region().name())) {
            warn(event.getPlayer(), "zona.entrou");
        }
    }

    @EventHandler
    public void onLeave(RegionLeaveEvent event) {
        if (floors.isCity(event.region().name()) && !floors.isSafeZone(event.getPlayer().getLocation())) {
            warn(event.getPlayer(), "zona.saiu");
        }
    }
}
