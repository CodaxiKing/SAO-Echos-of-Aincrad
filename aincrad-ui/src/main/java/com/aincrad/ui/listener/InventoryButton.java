package com.aincrad.ui.listener;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.glyph.Glyph;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * O botão do menu SAO dentro do inventário do Minecraft.
 *
 * <p>A tecla E abre o inventário no próprio jogo do jogador, sem avisar o servidor: não existe
 * evento para cancelar nem para trocar por outra tela. O que o servidor vê são os cliques lá
 * dentro. Por isso o ícone do menu fica nos espaços de fabricação do inventário: o jogador aperta
 * E, clica no ícone, o inventário fecha e o menu SAO abre.</p>
 *
 * <p>O ícone é um item de verdade, guardado na grade de fabricação, e nunca pode sair dela: todo
 * clique, arraste ou atalho que o tocaria é cancelado; ao fechar o inventário ele é tirado antes
 * de o Minecraft devolver a grade à mochila; e uma varredura apaga o que escapar.</p>
 */
public final class InventoryButton implements Listener {

    /** Posições da grade 2×2 na visão do inventário (0 é o resultado). */
    private static final int FIRST = 1;
    private static final int LAST = 4;

    private final AincradUIPlugin plugin;
    private final NamespacedKey key;

    public InventoryButton(AincradUIPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "botao_menu");
    }

    private boolean enabled() {
        return plugin.settings().inventoryButton;
    }

    public boolean isButton(ItemStack it) {
        return it != null && !it.isEmpty() && it.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private ItemStack button(Player p) {
        Glyph g = plugin.glyphs().get("icons/profile_on");
        boolean art = plugin.packStatus().hasPack(p) && g != null && g.present();
        ItemStack it = new ItemStack(art ? Material.PAPER : Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        if (art) {
            meta.setItemModel(new NamespacedKey("aincrad", g.modelPath()));
        }
        meta.displayName(plugin.messages().get("menu.botao_inventario").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(plugin.messages().get("menu.botao_inventario_lore").decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    /** A grade de fabricação do inventário do jogador, ou null se ele está com outra janela aberta. */
    private static CraftingInventory grid(Player p) {
        InventoryView view = p.getOpenInventory();
        return view.getType() == InventoryType.CRAFTING && view.getTopInventory() instanceof CraftingInventory c ? c : null;
    }

    /** Põe o ícone nos espaços vazios da grade (ou tira, se a função está desligada). */
    public void place(Player p) {
        CraftingInventory grid = grid(p);
        if (grid == null) {
            return;
        }
        boolean want = enabled() && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
        ItemStack[] matrix = grid.getMatrix();
        boolean changed = false;
        for (int i = 0; i < matrix.length; i++) {
            if (!want && isButton(matrix[i])) {
                matrix[i] = null;
                changed = true;
            } else if (want && (matrix[i] == null || matrix[i].isEmpty())) {
                matrix[i] = button(p);
                changed = true;
            }
        }
        if (changed) {
            grid.setMatrix(matrix);
        }
    }

    private void clear(Player p) {
        CraftingInventory grid = grid(p);
        if (grid == null) {
            return;
        }
        ItemStack[] matrix = grid.getMatrix();
        boolean changed = false;
        for (int i = 0; i < matrix.length; i++) {
            if (isButton(matrix[i])) {
                matrix[i] = null;
                changed = true;
            }
        }
        if (changed) {
            grid.setMatrix(matrix);
        }
    }

    /** A cada 2 s: repõe o ícone e apaga qualquer cópia que tenha ido parar na mochila. */
    public void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                if (isButton(contents[i])) {
                    p.getInventory().setItem(i, null);
                }
            }
            if (isButton(p.getItemOnCursor())) {
                p.setItemOnCursor(null);
            }
            place(p);
        }
    }

    public void shutdown() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            clear(p);
        }
    }

    private void openMenu(Player p) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            p.closeInventory();
            plugin.sessions().openMain(p);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || event.getView().getType() != InventoryType.CRAFTING) {
            return;
        }
        // Tecla numérica sobre um espaço: o item da hotbar trocaria de lugar com o ícone.
        ItemStack swapped = event.getHotbarButton() >= 0 ? p.getInventory().getItem(event.getHotbarButton()) : null;
        boolean onGrid = event.getRawSlot() >= FIRST && event.getRawSlot() <= LAST;
        if (isButton(event.getCurrentItem()) || isButton(event.getCursor()) || isButton(swapped)) {
            event.setCancelled(true);
            if (onGrid) {
                openMenu(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getType() != InventoryType.CRAFTING || isButton(event.getOldCursor())) {
            if (isButton(event.getOldCursor())) {
                event.setCancelled(true);
            }
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw >= FIRST && raw <= LAST && isButton(event.getView().getItem(raw))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Ao fechar, o Minecraft devolve a grade de fabricação à mochila: o ícone sai antes. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p && event.getView().getType() == InventoryType.CRAFTING) {
            clear(p);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    place(p);
                }
            });
        }
    }

    /** O livro de receitas esvazia a grade para montar a receita: o ícone não pode ir junto. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRecipeBook(com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isButton(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isButton);
        clear(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }
}
