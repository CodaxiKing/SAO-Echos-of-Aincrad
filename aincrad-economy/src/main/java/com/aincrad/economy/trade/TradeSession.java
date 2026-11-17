package com.aincrad.economy.trade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma janela de troca entre A e B (baú de 6 linhas aberto pelos dois). Colunas 0–3 das linhas
 * 0–4 = oferta de A; colunas 5–8 = oferta de B; coluna 4 = divisória; linha 5 = botões
 * (Col e confirmar de cada um, cancelar no meio).
 */
public final class TradeSession implements InventoryHolder {

    public static final int A_COL = 45;
    public static final int A_CONFIRM = 46;
    public static final int CANCEL = 49;
    public static final int B_CONFIRM = 52;
    public static final int B_COL = 53;

    private final Player a;
    private final Player b;
    private final Inventory inv;
    private long colA;
    private long colB;
    private boolean okA;
    private boolean okB;
    private long lastChange;
    private boolean finished;

    public TradeSession(Player a, Player b, Component title) {
        this.a = a;
        this.b = b;
        this.inv = Bukkit.createInventory(this, 54, title);
        this.lastChange = System.currentTimeMillis();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public Player a() {
        return a;
    }

    public Player b() {
        return b;
    }

    public boolean isA(Player p) {
        return p.getUniqueId().equals(a.getUniqueId());
    }

    public Player other(Player p) {
        return isA(p) ? b : a;
    }

    /** Slot de oferta de A (colunas 0–3, linhas 0–4)? */
    public static boolean areaA(int slot) {
        return slot >= 0 && slot < 45 && slot % 9 < 4;
    }

    public static boolean areaB(int slot) {
        return slot >= 0 && slot < 45 && slot % 9 > 4;
    }

    public static boolean ownArea(boolean isA, int slot) {
        return isA ? areaA(slot) : areaB(slot);
    }

    public long col(boolean ofA) {
        return ofA ? colA : colB;
    }

    public void col(boolean ofA, long v) {
        if (ofA) {
            colA = Math.max(0, v);
        } else {
            colB = Math.max(0, v);
        }
        changed();
    }

    public boolean confirmed(boolean ofA) {
        return ofA ? okA : okB;
    }

    public void confirm(boolean ofA, boolean v) {
        if (ofA) {
            okA = v;
        } else {
            okB = v;
        }
    }

    /** Alguém mexeu na oferta: as confirmações caem e o relógio de espera recomeça. */
    public void changed() {
        okA = false;
        okB = false;
        lastChange = System.currentTimeMillis();
    }

    public long sinceChange() {
        return System.currentTimeMillis() - lastChange;
    }

    public boolean finished() {
        return finished;
    }

    public void finish() {
        finished = true;
    }

    /** Itens oferecidos por A ou B (cópias não vazias). */
    public List<ItemStack> offer(boolean ofA) {
        List<ItemStack> out = new ArrayList<>();
        for (int s = 0; s < 45; s++) {
            if (ofA ? areaA(s) : areaB(s)) {
                ItemStack it = inv.getItem(s);
                if (it != null && !it.getType().isAir()) {
                    out.add(it.clone());
                }
            }
        }
        return out;
    }

    /** Esvazia as áreas de oferta (depois de entregar ou devolver). */
    public void clearOffers() {
        for (int s = 0; s < 45; s++) {
            if (areaA(s) || areaB(s)) {
                inv.setItem(s, null);
            }
        }
    }
}
