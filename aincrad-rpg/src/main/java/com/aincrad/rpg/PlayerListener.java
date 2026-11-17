package com.aincrad.rpg;

import com.aincrad.core.message.Messages;
import com.aincrad.rpg.item.ItemService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.HashMap;

/** Entrada e saída, XP vanilla desligado, morte, encantamento e level mínimo das armaduras. */
public final class PlayerListener implements Listener {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Plugin plugin;
    private final RpgService rpg;
    private final ItemService items;

    public PlayerListener(Plugin plugin, RpgService rpg, ItemService items) {
        this.plugin = plugin;
        this.rpg = rpg;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            rpg.store().loadBlocking(event.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("Não foi possível carregar o RPG de " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        rpg.applyAttributes(p);
        rpg.refresh(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rpg.store().unload(event.getPlayer().getUniqueId());
    }

    /** XP vanilla (orbes) não entra: a barra é do RPG. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onExp(PlayerExpChangeEvent event) {
        if (rpg.settings().blockVanillaXp) {
            event.setAmount(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        event.setDroppedExp(0);
        event.setKeepLevel(false);
        event.setNewLevel(0);
        event.setNewExp(0);
        long lost = rpg.deathPenalty(p);
        if (lost > 0) {
            rpg.messages().send(p, "xp.morte", Messages.vars("qtd", lost));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                rpg.applyAttributes(p);
                rpg.refresh(p);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (rpg.settings().blockEnchanting) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (rpg.settings().blockEnchanting && event.getInventory().getType() == InventoryType.ENCHANTING) {
            event.setCancelled(true);
            if (event.getPlayer() instanceof Player p) {
                rpg.messages().send(p, "encantamento.bloqueado");
            }
        }
    }

    /** Roda a cada 2 s: armadura acima do level volta para o inventário (ou cai no chão). */
    public void checkArmor(Player p) {
        int level = rpg.progress(p).level();
        PlayerInventory inv = p.getInventory();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack it = inv.getItem(slot);
            int need = items.requiredLevel(it);
            if (need <= level) {
                continue;
            }
            inv.setItem(slot, null);
            HashMap<Integer, ItemStack> left = inv.addItem(it);
            left.values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            Component name = it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                    ? it.getItemMeta().displayName() : Component.translatable(it.translationKey());
            rpg.messages().send(p, "item.armadura_level", Messages.vars("level", need),
                    Placeholder.component("item", name == null ? Component.empty() : name));
        }
    }
}
