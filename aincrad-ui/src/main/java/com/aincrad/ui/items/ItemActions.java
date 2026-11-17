package com.aincrad.ui.items;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * O que o jogador pode fazer com um item pelo menu, sem abrir o inventário padrão.
 * Cada ação devolve o resultado para a tela mostrar a mensagem certa.
 */
public final class ItemActions {

    public enum Result { OK, VAZIO, NAO_EQUIPAVEL, INVENTARIO_CHEIO, MUDOU }

    /** Um item do inventário, com o slot onde está. */
    public record Entry(int slot, ItemStack item, ItemCategory category) {
    }

    /** Slots da mochila e da hotbar (0 a 35). Armadura e mão secundária ficam no perfil. */
    public static final int STORAGE_SLOTS = 36;

    private ItemActions() {
    }

    public static ItemCategory categoryOf(ItemStack item) {
        Material m = item.getType();
        EquipmentSlot slot = m.getEquipmentSlot();
        return ItemCategory.of(m.name(), m.isEdible(), m.isBlock(), slot.name());
    }

    /** Itens da mochila e da hotbar que passam no filtro, na ordem dos slots. */
    public static List<Entry> list(Player p, ItemCategory filter) {
        List<Entry> out = new ArrayList<>();
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                continue;
            }
            ItemCategory c = categoryOf(it);
            if (filter.accepts(c)) {
                out.add(new Entry(i, it.clone(), c));
            }
        }
        return out;
    }

    /** Para onde o item vai se for equipado (null se não for equipável). */
    public static EquipmentSlot equipSlot(ItemStack item) {
        EquipmentSlot s = item.getType().getEquipmentSlot();
        return switch (s) {
            case HEAD, CHEST, LEGS, FEET, OFF_HAND -> s;
            default -> null;
        };
    }

    /** O slot ainda tem o mesmo item que a tela mostrou? (o inventário pode mudar com o menu aberto) */
    private static boolean same(PlayerInventory inv, int slot, ItemStack expected) {
        ItemStack now = inv.getItem(slot);
        return now != null && expected != null && now.isSimilar(expected) && now.getAmount() == expected.getAmount();
    }

    /** Equipa o item do slot, trocando com a peça que estava equipada. */
    public static Result equip(Player p, int slot, ItemStack expected) {
        PlayerInventory inv = p.getInventory();
        if (!same(inv, slot, expected)) {
            return Result.MUDOU;
        }
        ItemStack item = inv.getItem(slot);
        EquipmentSlot target = equipSlot(item);
        if (target == null) {
            return Result.NAO_EQUIPAVEL;
        }
        ItemStack old = inv.getItem(target);
        inv.setItem(target, item);
        inv.setItem(slot, old == null || old.getType().isAir() ? null : old);
        return Result.OK;
    }

    /** Coloca o item na mão (troca com o slot selecionado da hotbar). */
    public static Result hold(Player p, int slot, ItemStack expected) {
        PlayerInventory inv = p.getInventory();
        if (!same(inv, slot, expected)) {
            return Result.MUDOU;
        }
        int held = inv.getHeldItemSlot();
        if (slot == held) {
            return Result.OK;
        }
        ItemStack inHand = inv.getItem(held);
        inv.setItem(held, inv.getItem(slot));
        inv.setItem(slot, inHand);
        return Result.OK;
    }

    /** Joga o item no chão, na frente do jogador. */
    public static Result drop(Player p, int slot, ItemStack expected) {
        PlayerInventory inv = p.getInventory();
        if (!same(inv, slot, expected)) {
            return Result.MUDOU;
        }
        ItemStack item = inv.getItem(slot);
        inv.setItem(slot, null);
        Item entity = p.getWorld().dropItem(p.getEyeLocation().subtract(0, 0.3, 0), item);
        entity.setVelocity(p.getLocation().getDirection().multiply(0.3));
        entity.setThrower(p.getUniqueId());
        entity.setPickupDelay(40);
        return Result.OK;
    }

    /** Tira a peça equipada e guarda na mochila. */
    public static Result unequip(Player p, EquipmentSlot slot) {
        PlayerInventory inv = p.getInventory();
        ItemStack piece = inv.getItem(slot);
        if (piece == null || piece.getType().isAir()) {
            return Result.VAZIO;
        }
        int free = firstFreeStorage(inv);
        if (free < 0) {
            return Result.INVENTARIO_CHEIO;
        }
        inv.setItem(free, piece);
        inv.setItem(slot, null);
        return Result.OK;
    }

    private static int firstFreeStorage(PlayerInventory inv) {
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    /** Itens da mochila que podem ir para um slot de equipamento. */
    public static List<Entry> candidates(Player p, EquipmentSlot slot) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : list(p, ItemCategory.TUDO)) {
            if (slot == EquipmentSlot.HAND || slot.equals(equipSlot(e.item()))) {
                out.add(e);
            }
        }
        return out;
    }
}
