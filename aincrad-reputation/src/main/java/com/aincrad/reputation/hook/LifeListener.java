package com.aincrad.reputation.hook;

import com.aincrad.life.api.FishingTournamentEndEvent;
import com.aincrad.life.api.LegendaryFishEvent;
import com.aincrad.life.api.MarriageEvent;
import com.aincrad.life.api.PetTameEvent;
import com.aincrad.life.api.PoisonAttackEvent;
import com.aincrad.life.api.ProfessionRankUpEvent;
import com.aincrad.reputation.CursorService;
import com.aincrad.reputation.ReputationService;
import com.aincrad.reputation.model.Achievements.Type;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Conquistas da vida no Aincrad e o veneno em jogador. Só é carregada quando o AincradLife está ligado. */
public final class LifeListener implements Listener {

    private final ReputationService reputation;
    private final CursorService cursor;

    public LifeListener(ReputationService reputation, CursorService cursor) {
        this.reputation = reputation;
        this.cursor = cursor;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLegendary(LegendaryFishEvent event) {
        reputation.happened(event.getFisher(), Type.PEIXE_LENDARIO, event.getFloor(), "pesca");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTournament(FishingTournamentEndEvent event) {
        reputation.happened(event.getWinner(), Type.TORNEIO_DE_PESCA, event.getFloor(), "pesca");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTame(PetTameEvent event) {
        reputation.happened(event.getPlayer(), Type.FAMILIAR_DOMADO, 0, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRank(ProfessionRankUpEvent event) {
        if ("completo".equalsIgnoreCase(event.getRank())) {
            reputation.happened(event.getPlayer(), Type.PROFISSAO_COMPLETA, 0, event.getProfession());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMarriage(MarriageEvent event) {
        if (event.isMarried()) {
            reputation.happened(event.getFirst(), Type.CASAMENTO, 0, null);
            reputation.happened(event.getSecond(), Type.CASAMENTO, 0, null);
        }
    }

    /** Veneno de alquimia em jogador conta como ataque. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPoison(PoisonAttackEvent event) {
        cursor.attacked(event.getAttacker(), event.getVictim());
    }
}
