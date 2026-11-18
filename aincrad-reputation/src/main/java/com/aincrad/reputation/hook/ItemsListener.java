package com.aincrad.reputation.hook;

import com.aincrad.core.message.Messages;
import com.aincrad.items.api.CrystalUseEvent;
import com.aincrad.reputation.CursorService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Preso não usa cristais. Só é carregada quando o AincradItems está ligado. */
public final class ItemsListener implements Listener {

    private final CursorService cursor;
    private final Messages msg;

    public ItemsListener(CursorService cursor, Messages msg) {
        this.cursor = cursor;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystal(CrystalUseEvent event) {
        if (cursor.jailed(event.getPlayer())) {
            event.setCancelled(true);
            msg.send(event.getPlayer(), "prisao.sem_cristal");
        }
    }
}
