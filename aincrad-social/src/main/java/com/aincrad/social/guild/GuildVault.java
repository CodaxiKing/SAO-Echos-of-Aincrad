package com.aincrad.social.guild;

import com.aincrad.social.AincradSocialPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cofre da guilda: um baú compartilhado por guilda (todos que abrem veem o mesmo inventário ao
 * vivo). Recrutas não abrem; membros têm limite de retiradas por dia; tudo vai para o histórico.
 */
public final class GuildVault implements Listener {

    /** Marca o inventário do cofre de uma guilda. */
    public static final class Holder implements InventoryHolder {
        private final UUID guild;
        private Inventory inventory;

        Holder(UUID guild) {
            this.guild = guild;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final AincradSocialPlugin plugin;
    private final Map<UUID, Inventory> open = new HashMap<>();

    public GuildVault(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    private GuildService svc() {
        return plugin.guilds();
    }

    // ------------------------------------------------------------------ serialização

    static String encode(ItemStack[] items) {
        List<String> parts = new ArrayList<>(items.length);
        for (ItemStack it : items) {
            parts.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        return String.join(";", parts);
    }

    static ItemStack[] decode(String data, int size) {
        ItemStack[] out = new ItemStack[size];
        if (data == null || data.isEmpty()) {
            return out;
        }
        String[] parts = data.split(";", -1);
        int extra = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            ItemStack it;
            try {
                it = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[i]));
            } catch (RuntimeException e) {
                continue;
            }
            if (i < size) {
                out[i] = it;
            } else {
                // Cofre encolheu (não deveria): põe nos primeiros espaços vazios.
                while (extra < size && out[extra] != null) {
                    extra++;
                }
                if (extra < size) {
                    out[extra] = it;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ abrir e salvar

    public boolean canOpen(Player p) {
        return svc().has(p.getUniqueId(), GuildPerm.DEPOSITAR) || svc().has(p.getUniqueId(), GuildPerm.RETIRAR_COFRE);
    }

    public void open(Player p) {
        Guild g = svc().guildOf(p.getUniqueId());
        if (g == null) {
            plugin.messages().send(p, "guilda.sem_guilda");
            return;
        }
        if (!canOpen(p)) {
            plugin.messages().send(p, "guilda.sem_permissao");
            return;
        }
        int size = svc().vaultSize(g);
        Inventory inv = open.get(g.id());
        if (inv == null || inv.getSize() != size) {
            ItemStack[] items = inv != null ? inv.getContents() : decode(g.vaultData(), size);
            if (inv != null) {
                for (var v : new ArrayList<>(inv.getViewers())) {
                    v.closeInventory();
                }
            }
            Holder h = new Holder(g.id());
            inv = Bukkit.createInventory(h, size, plugin.messages().get("guilda.cofre_titulo",
                    Placeholder.unparsed("guilda", g.name())));
            h.inventory = inv;
            ItemStack[] fit = new ItemStack[size];
            System.arraycopy(items, 0, fit, 0, Math.min(size, items.length));
            inv.setContents(fit);
            open.put(g.id(), inv);
        }
        p.openInventory(inv);
    }

    private void save(UUID guildId) {
        Inventory inv = open.get(guildId);
        Guild g = svc().get(guildId);
        if (inv == null || g == null) {
            return;
        }
        svc().setVaultData(g, encode(inv.getContents()));
        plugin.saveGuildsSoon();
    }

    /** Ao desligar: grava todos os cofres abertos. */
    public void saveAll() {
        for (UUID id : new ArrayList<>(open.keySet())) {
            Inventory inv = open.get(id);
            for (var v : new ArrayList<>(inv.getViewers())) {
                v.closeInventory();
            }
            Guild g = svc().get(id);
            if (g != null) {
                svc().setVaultData(g, encode(inv.getContents()));
            }
        }
        open.clear();
    }

    /** A guilda acabou: os itens vão para a caixa do líder (como texto) e para o chão dele se online. */
    public ItemStack[] drain(Guild g) {
        Inventory inv = open.remove(g.id());
        ItemStack[] items = inv != null ? inv.getContents() : decode(g.vaultData(), 54);
        if (inv != null) {
            for (var v : new ArrayList<>(inv.getViewers())) {
                v.closeInventory();
            }
        }
        return items;
    }

    // ------------------------------------------------------------------ cliques

    private static String describe(ItemStack it) {
        return it.getType().name().toLowerCase() + (it.getAmount() > 1 ? " x" + it.getAmount() : "");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder) || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        UUID id = p.getUniqueId();
        boolean top = event.getClickedInventory() == event.getView().getTopInventory();
        InventoryAction a = event.getAction();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean hasCurrent = current != null && !current.getType().isAir();
        boolean hasCursor = !cursor.getType().isAir();

        if (a == InventoryAction.COLLECT_TO_CURSOR || a == InventoryAction.UNKNOWN) {
            event.setCancelled(true); // poderia puxar itens do cofre sem registro
            return;
        }
        if (top) {
            boolean taking = hasCurrent && switch (a) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP,
                     SWAP_WITH_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
                default -> false;
            };
            boolean placing = (hasCursor && switch (a) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> true;
                default -> false;
            }) || a == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() >= 0
                    && p.getInventory().getItem(event.getHotbarButton()) != null;
            if (taking && !svc().canWithdrawItem(id)) {
                event.setCancelled(true);
                plugin.messages().send(p, "guilda.cofre_limite");
                return;
            }
            if (placing && !svc().has(id, GuildPerm.DEPOSITAR)) {
                event.setCancelled(true);
                plugin.messages().send(p, "guilda.sem_permissao");
                return;
            }
            if (taking) {
                svc().recordVault(id, p.getName(), true, describe(current), current.getAmount());
            }
            if (placing && hasCursor) {
                svc().recordVault(id, p.getName(), false, describe(cursor), cursor.getAmount());
            }
        } else if (a == InventoryAction.MOVE_TO_OTHER_INVENTORY && hasCurrent) {
            if (!svc().has(id, GuildPerm.DEPOSITAR)) {
                event.setCancelled(true);
                plugin.messages().send(p, "guilda.sem_permissao");
                return;
            }
            svc().recordVault(id, p.getName(), false, describe(current), current.getAmount());
        }
        Holder h = (Holder) event.getView().getTopInventory().getHolder(false);
        Bukkit.getScheduler().runTask(plugin, () -> save(h.guild));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder h) || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean intoTop = event.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (!intoTop) {
            return;
        }
        if (!svc().has(p.getUniqueId(), GuildPerm.DEPOSITAR)) {
            event.setCancelled(true);
            plugin.messages().send(p, "guilda.sem_permissao");
            return;
        }
        svc().recordVault(p.getUniqueId(), p.getName(), false, describe(event.getOldCursor()), event.getOldCursor().getAmount());
        Bukkit.getScheduler().runTask(plugin, () -> save(h.guild));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Holder h) {
            save(h.guild);
            if (event.getInventory().getViewers().size() <= 1) {
                open.remove(h.guild);
            }
        }
    }
}
