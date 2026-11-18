package com.aincrad.reputation.hook;

import com.aincrad.bosses.api.BossDefeatEvent;
import com.aincrad.reputation.ReputationService;
import com.aincrad.reputation.model.Achievements.Type;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/** Conquistas de chefe. Só é carregada quando o AincradBosses está ligado. */
public final class BossesListener implements Listener {

    private final ReputationService reputation;

    public BossesListener(ReputationService reputation) {
        this.reputation = reputation;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDefeat(BossDefeatEvent event) {
        int floor = event.getFloor();
        for (UUID id : event.getParticipants()) {
            reputation.happened(id, Type.CHEFE_DERROTADO, floor, null);
            if (event.isFirstKill()) {
                reputation.happened(id, Type.CHEFE_PRIMEIRA_VITORIA, floor, null);
            }
        }
        if (event.getLastAttack() != null) {
            reputation.happened(event.getLastAttack(), Type.LAST_ATTACK, floor, null);
        }
    }
}
