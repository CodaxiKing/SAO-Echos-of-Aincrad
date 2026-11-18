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
    private final java.util.function.BiPredicate<org.bukkit.entity.Player, String> ambushed;

    /** @param ambushed o cristal deste tipo falha para o jogador (pego numa emboscada)? */
    public ItemsListener(CursorService cursor, Messages msg, java.util.function.BiPredicate<org.bukkit.entity.Player, String> ambushed) {
        this.cursor = cursor;
        this.msg = msg;
        this.ambushed = ambushed;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystal(CrystalUseEvent event) {
        if (cursor.jailed(event.getPlayer())) {
            event.setCancelled(true);
            msg.send(event.getPlayer(), "prisao.sem_cristal");
        } else if (ambushed.test(event.getPlayer(), event.getType())) {
            event.setCancelled(true);
            msg.send(event.getPlayer(), "crime.cristal_falhou");
        }
    }
}
