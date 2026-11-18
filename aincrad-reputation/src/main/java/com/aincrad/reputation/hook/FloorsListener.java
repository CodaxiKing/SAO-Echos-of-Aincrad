package com.aincrad.reputation.hook;

import com.aincrad.floors.api.PlayerEnterFloorEvent;
import com.aincrad.reputation.ReputationService;
import com.aincrad.reputation.model.Achievements.Type;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Conquistas de andar. Só é carregada quando o AincradFloors está ligado. */
public final class FloorsListener implements Listener {

    private final ReputationService reputation;

    public FloorsListener(ReputationService reputation) {
        this.reputation = reputation;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnter(PlayerEnterFloorEvent event) {
        if (!event.isFirstVisit() || event.getTo() <= 0) {
            return;
        }
        reputation.happened(event.getPlayer(), Type.ANDAR_VISITADO, event.getTo(), null);
        // O andar 1 é onde todo mundo começa: não há "primeiro" nele.
        if (event.getTo() > 1) {
            reputation.happened(event.getPlayer(), Type.PRIMEIRO_NO_ANDAR, event.getTo(), null);
        }
    }
}
