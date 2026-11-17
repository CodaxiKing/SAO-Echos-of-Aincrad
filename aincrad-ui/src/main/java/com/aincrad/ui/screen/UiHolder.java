package com.aincrad.ui.screen;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marca os inventários do modo baú, para os listeners reconhecerem e cancelarem cliques. */
public final class UiHolder implements InventoryHolder {

    private Inventory inventory;

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
