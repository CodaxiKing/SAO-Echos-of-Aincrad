package com.aincrad.admin.hook;

import com.aincrad.admin.DiscordService;
import com.aincrad.reputation.api.AchievementGrantEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/** Conquista rara (as que o servidor anuncia) vai para o Discord. */
public final class ReputationHook implements Listener {

    private final DiscordService discord;

    public ReputationHook(DiscordService discord) {
        this.discord = discord;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAchievement(AchievementGrantEvent event) {
        if (event.isAnnounced()) {
            discord.send(DiscordService.Kind.CONQUISTA, Map.of("jogador", event.getPlayer().getName(),
                    "conquista", event.getName(), "titulo", event.getTitle() == null ? "" : event.getTitle()));
        }
    }
}
