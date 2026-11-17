package com.aincrad.life.hook;

import com.aincrad.rpg.api.XpGainEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.function.ToDoubleFunction;

/**
 * Bônus de XP do AincradRPG (ex.: "Descansado" da estalagem). Só é carregada quando o RPG está
 * ligado. {@code bonus} devolve a fração a mais para o jogador (0.05 = +5%).
 */
public final class RpgXpListener implements Listener {

    private final ToDoubleFunction<Player> bonus;

    public RpgXpListener(ToDoubleFunction<Player> bonus) {
        this.bonus = bonus;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onXp(XpGainEvent event) {
        double extra = bonus.applyAsDouble(event.getPlayer());
        if (extra > 0) {
            event.setAmount(Math.round(event.getAmount() * (1 + extra)));
        }
    }
}
