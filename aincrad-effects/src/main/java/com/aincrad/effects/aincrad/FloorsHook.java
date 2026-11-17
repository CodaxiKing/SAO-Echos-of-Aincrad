package com.aincrad.effects.aincrad;

import com.aincrad.floors.api.AincradFloorsAPI;
import com.aincrad.floors.api.FloorAnnounceEvent;
import com.aincrad.floors.api.GateTeleportEvent;
import com.aincrad.floors.api.PlayerEnterFloorEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/** Efeitos dos andares: Floor Cleared, chegada no andar e Teleport Gate. Só carrega com o AincradFloors. */
final class FloorsHook implements Listener {

    private final AincradIntegration fx;

    FloorsHook(AincradIntegration fx) {
        this.fx = fx;
    }

    /** Um andar foi liberado na rede: todos veem o "Floor Cleared" e o título simples do Floors não aparece. */
    @EventHandler
    public void onAnnounce(FloorAnnounceEvent event) {
        if (fx.effect("floor_cleared") == null) {
            return;
        }
        event.setTitleHandled(true);
        Map<String, String> vars = Map.of("andar", String.valueOf(event.getFloor()), "nome", event.getFloorName(),
                "jogadores", event.getParticipants(), "guilda", event.getGuild());
        for (Player p : Bukkit.getOnlinePlayers()) {
            fx.play("floor_cleared", p, vars);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnter(PlayerEnterFloorEvent event) {
        // Ao entrar no servidor o jogador "chega" ao andar onde já estava: sem título, a não ser na primeira vez.
        if (event.getFrom() == 0 && !event.isFirstVisit()) {
            return;
        }
        fx.play("chegada_andar", event.getPlayer(), Map.of("andar", String.valueOf(event.getTo()),
                "nome", AincradFloorsAPI.getFloorName(event.getTo())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGate(GateTeleportEvent event) {
        fx.play("gate", event.getPlayer(), Map.of("andar", String.valueOf(event.getFloor()),
                "nome", AincradFloorsAPI.getFloorName(event.getFloor())));
    }
}
