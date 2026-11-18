package com.aincrad.life.orders;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * O item que um cliente entregou a um artesão num contrato de serviço. Enquanto o contrato vale,
 * o item fica preso ao inventário do artesão: não vai para baú, baú do fim, moldura, suporte,
 * trouxinha nem para o chão; não gasta e não cai quando ele morre. Quando o contrato termina sem
 * entrega (prazo, cancelamento), o dono recebe de volta a cópia guardada na caixa do contrato e o
 * item que estava com o artesão some.
 */
public final class ServiceItems implements Listener {

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final NamespacedKey keyContract;
    private final NamespacedKey keySince;
    private final NamespacedKey keyLines;
    /** Os contratos de serviço em produção, conforme a última leitura do banco. */
    private volatile Set<Long> active = Set.of();
    private volatile long refreshed;

    public ServiceItems(Plugin plugin, Messages msg, ConfigFile config) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.keyContract = new NamespacedKey(plugin, "contrato_servico");
        this.keySince = new NamespacedKey(plugin, "contrato_servico_desde");
        this.keyLines = new NamespacedKey(plugin, "contrato_servico_linhas");
    }

    // ------------------------------------------------------------------ marca

    /** O número do contrato a que o item está preso (0: item comum). */
    public long contract(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) {
            return 0;
        }
        return it.getItemMeta().getPersistentDataContainer().getOrDefault(keyContract, PersistentDataType.LONG, 0L);
    }

    public boolean bound(ItemStack it) {
        return contract(it) > 0;
    }

    /** Prende o item ao contrato. As linhas de aviso vão no começo da descrição, sem mexer no resto. */
    public ItemStack bind(ItemStack item, long id, String owner) {
        ItemStack it = item.clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> mine = msg.list("servico.item_lore", Messages.vars("id", id, "dono", owner));
        List<Component> lore = new ArrayList<>(mine);
        if (meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyContract, PersistentDataType.LONG, id);
        pdc.set(keySince, PersistentDataType.LONG, System.currentTimeMillis());
        pdc.set(keyLines, PersistentDataType.INTEGER, mine.size());
        it.setItemMeta(meta);
        return it;
    }

    /** Solta o item do contrato (na entrega): tira a marca e as linhas de aviso. */
    public ItemStack unbind(ItemStack item) {
        ItemStack it = item.clone();
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int lines = pdc.getOrDefault(keyLines, PersistentDataType.INTEGER, 0);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (int i = 0; i < lines && !lore.isEmpty(); i++) {
            lore.remove(0);
        }
        meta.lore(lore.isEmpty() ? null : lore);
        pdc.remove(keyContract);
        pdc.remove(keySince);
        pdc.remove(keyLines);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ devolução

    /** Chamado a cada minuto com os contratos de serviço que ainda estão em produção. */
    public void refresh(Set<Long> activeContracts) {
        active = Set.copyOf(activeContracts);
        refreshed = System.currentTimeMillis();
        Bukkit.getOnlinePlayers().forEach(this::purge);
    }

    /**
     * Tira do jogador os itens de contratos que não valem mais (o dono já recebeu a cópia de
     * volta). Um item entregue depois da última leitura do banco nunca é tirado por engano.
     */
    public void purge(Player p) {
        if (refreshed == 0) {
            return;
        }
        ItemStack[] contents = p.getInventory().getContents();
        int removed = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            long id = contract(it);
            if (id <= 0 || active.contains(id)) {
                continue;
            }
            long since = it.getItemMeta().getPersistentDataContainer().getOrDefault(keySince, PersistentDataType.LONG, 0L);
            if (OrderRules.purge(false, since, refreshed, 5_000L)) {
                p.getInventory().setItem(i, null);
                removed++;
            }
        }
        if (removed > 0) {
            msg.send(p, "servico.item_recolhido");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                purge(event.getPlayer());
            }
        }, 60L);
    }

    // ------------------------------------------------------------------ travas

    private void deny(Player p) {
        msg.actionBar(p, "servico.preso");
    }

    /** Com um baú, baú do fim, loja, troca ou qualquer outra tela aberta, o item em contrato não se move. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        ItemStack hotbar = event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                ? p.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offhand = event.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : null;
        boolean involved = bound(event.getCurrentItem()) || bound(event.getCursor()) || bound(hotbar) || bound(offhand);
        if (!involved) {
            return;
        }
        InventoryType top = event.getView().getTopInventory().getType();
        boolean ownInventory = top == InventoryType.CRAFTING || top == InventoryType.CREATIVE;
        // Largar pelo inventário (tecla de largar, ou clicar fora da janela).
        boolean drop = event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP || event.getSlot() == -999;
        // Trouxinha: guardar o item dentro dela é guardar.
        boolean bundle = isBundle(event.getCurrentItem()) || isBundle(event.getCursor());
        if (!ownInventory || drop || bundle) {
            event.setCancelled(true);
            deny(p);
        }
    }

    private static boolean isBundle(ItemStack it) {
        return it != null && it.getType().name().endsWith("BUNDLE");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryType top = event.getView().getTopInventory().getType();
        if (bound(event.getOldCursor()) && top != InventoryType.CRAFTING && top != InventoryType.CREATIVE) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                deny(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (bound(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    /** Moldura e allay: pôr o item neles é tirá-lo do inventário. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntity(PlayerInteractEntityEvent event) {
        if ((event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof Allay)
                && bound(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (bound(event.getPlayerItem())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    /** Vaso decorado e estante entalhada guardam o item da mão com um clique. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null || !bound(event.getItem())) {
            return;
        }
        Material m = event.getClickedBlock().getType();
        if (m == Material.DECORATED_POT || m == Material.CHISELED_BOOKSHELF || m == Material.JUKEBOX || m == Material.LECTERN) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    /** O item do cliente não gasta enquanto está com o artesão. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(PlayerItemDamageEvent event) {
        if (bound(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Se o artesão morre, o item em contrato fica com ele. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        List<ItemStack> keep = new ArrayList<>();
        event.getDrops().removeIf(it -> {
            if (bound(it)) {
                keep.add(it);
                return true;
            }
            return false;
        });
        event.getItemsToKeep().addAll(keep);
    }

    /**
     * Comandos que tiram o item da mão para vender, trocar ou guardar (leilão, barraca, caixa do
     * contrato): com um item em contrato na mão, não rodam. A lista está em
     * config.yml → encomendas.servico.comandos_bloqueados.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!bound(p.getInventory().getItemInMainHand()) && !bound(p.getInventory().getItemInOffHand())) {
            return;
        }
        String typed = event.getMessage().substring(1).toLowerCase(Locale.ROOT).trim() + " ";
        for (String blocked : config.yaml().getStringList("encomendas.servico.comandos_bloqueados")) {
            if (typed.startsWith(blocked.toLowerCase(Locale.ROOT).trim() + " ")) {
                event.setCancelled(true);
                msg.send(p, "servico.preso");
                return;
            }
        }
    }
}
