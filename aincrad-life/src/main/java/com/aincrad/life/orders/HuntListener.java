package com.aincrad.life.orders;

import com.aincrad.life.api.LegendaryFishEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Fecha a caçada contratada quando o Senhor do Lago é derrotado. */
public final class HuntListener implements Listener {

    private final OrderService orders;

    public HuntListener(OrderService orders) {
        this.orders = orders;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLegendary(LegendaryFishEvent event) {
        orders.onLegendary(event.getFisher(), event.getFloor(), event.getHunters());
    }
}
