package com.aincrad.ui.listener;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.config.UiSettings.DrawMode;
import com.aincrad.ui.screen.ChestRenderer;
import com.aincrad.ui.screen.MenuSession;
import com.aincrad.ui.screen.UiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

/**
 * Gesto de abrir (agachar + clique esquerdo), cliques e "voltar" do modo cursor, travamento do
 * jogador enquanto o menu flutua na frente dele, e cliques do modo baú.
 */
public final class MenuListener implements Listener {

    private final AincradUIPlugin plugin;

    public MenuListener(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ abrir e clicar

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (plugin.sessions().isOpen(p)) {
            event.setCancelled(true);
            return;
        }
        UiSettings st = plugin.settings();
        if (!st.gestureEnabled || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if ((a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) || !p.isSneaking()) {
            return;
        }
        if (st.gestureEmptyHand && !p.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        if (!p.hasPermission("aincrad.ui.menu")) {
            return;
        }
        event.setCancelled(true);
        plugin.sessions().openMain(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.sessions().isOpen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            plugin.sessions().click(event.getPlayer());
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        MenuSession s = plugin.sessions().get(p);
        if (event.isSneaking() && s != null && s.mode() == DrawMode.CURSOR) {
            plugin.sessions().back(p);
        }
    }

    // ------------------------------------------------------------------ proteção enquanto o menu está aberto
    // (o jogador fica parado por modificadores de atributo; ver SessionManager#freeze)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.sessions().isOpen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        MenuSession s = plugin.sessions().get(event.getPlayer());
        if (s != null && s.mode() == DrawMode.CURSOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && plugin.sessions().isOpen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && plugin.settings().closeOnDamage && plugin.sessions().isOpen(p)) {
            plugin.sessions().close(p, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        plugin.sessions().close(event.getPlayer(), false);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        plugin.sessions().close(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.sessions().close(event.getPlayer(), false);
    }

    // ------------------------------------------------------------------ modo baú

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof UiHolder) || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        // Trocar ou fechar inventário dentro do evento de clique é instável: faz no próximo tick.
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.sessions().clickSlot(p, slot));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof UiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) {
            return;
        }
        MenuSession s = plugin.sessions().get(p);
        if (s == null || !(s.renderer() instanceof ChestRenderer chest)) {
            return;
        }
        if (chest.switching() || !chest.owns(event.getInventory().getHolder(false))) {
            return;
        }
        plugin.sessions().close(p, true);
    }
}
