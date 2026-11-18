package com.aincrad.life.hook;

import com.aincrad.rpg.api.XpGainEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.function.ObjLongConsumer;

/**
 * Repassa ao familiar o XP que o dono ganha no AincradRPG. Só é carregada quando o RPG está
 * ligado. {@code gained} recebe o jogador e o XP já com os bônus aplicados.
 */
public final class RpgPetXpListener implements Listener {

    private final ObjLongConsumer<Player> gained;

    public RpgPetXpListener(ObjLongConsumer<Player> gained) {
        this.gained = gained;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onXp(XpGainEvent event) {
        if (event.getAmount() > 0) {
            gained.accept(event.getPlayer(), event.getAmount());
        }
    }
}
