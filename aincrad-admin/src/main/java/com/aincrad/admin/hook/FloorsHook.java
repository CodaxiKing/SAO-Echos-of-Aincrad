package com.aincrad.admin.hook;

import com.aincrad.admin.DiscordService;
import com.aincrad.admin.RankingService;
import com.aincrad.floors.api.AincradFloorsAPI;
import com.aincrad.floors.api.FloorUnlockEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/** Andar liberado: entra no ranking (a hora) e vai para o Discord. */
public final class FloorsHook implements Listener {

    private final RankingService ranking;
    private final DiscordService discord;

    public FloorsHook(RankingService ranking, DiscordService discord) {
        this.ranking = ranking;
        this.discord = discord;
        ranking.frontline(AincradFloorsAPI::getFrontline);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnlock(FloorUnlockEvent event) {
        int floor = event.getFloor();
        ranking.recordFloor(floor);
        String name = AincradFloorsAPI.getFloorName(floor);
        discord.send(DiscordService.Kind.ANDAR, Map.of("andar", String.valueOf(floor),
                "nome", name == null ? "" : name, "participantes", String.valueOf(event.getParticipants().size())));
    }
}
