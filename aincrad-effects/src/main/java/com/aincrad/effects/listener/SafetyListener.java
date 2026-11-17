package com.aincrad.effects.listener;

import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.step.MenuStep;
import com.aincrad.effects.step.WorldSteps;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

/** Menus de efeito sem roubo de itens, fogos de efeito sem dano e limpeza ao sair. */
public final class SafetyListener implements Listener {

    private final AincradEffectsPlugin plugin;
    private final NamespacedKey fireworkTag;

    public SafetyListener(AincradEffectsPlugin plugin) {
        this.plugin = plugin;
        this.fireworkTag = new NamespacedKey(plugin, WorldSteps.FIREWORK_TAG);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuStep.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (holder.click(p, slot) && p.getOpenInventory().getTopInventory().getHolder(false) == holder) {
                p.closeInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof MenuStep.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework fw && fw.getPersistentDataContainer().has(fireworkTag, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.engine().stop(event.getPlayer());
        plugin.limits().forget(event.getPlayer().getUniqueId());
    }
}
