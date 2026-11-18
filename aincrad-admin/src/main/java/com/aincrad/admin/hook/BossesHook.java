package com.aincrad.admin.hook;

import com.aincrad.admin.DiscordService;
import com.aincrad.admin.RankingService;
import com.aincrad.admin.model.DiscordRules;
import com.aincrad.bosses.api.BossDefeatEvent;
import com.aincrad.bosses.api.RaidScheduledEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chefe derrotado (ranking e Discord) e raid marcada (Discord). */
public final class BossesHook implements Listener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm").withZone(ZoneId.systemDefault());

    private final RankingService ranking;
    private final DiscordService discord;

    public BossesHook(RankingService ranking, DiscordService discord) {
        this.ranking = ranking;
        this.discord = discord;
    }

    private static String name(UUID id) {
        String n = id == null ? null : Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDefeat(BossDefeatEvent event) {
        List<RankingService.Hit> hits = new ArrayList<>();
        Map<String, Double> byName = new LinkedHashMap<>();
        for (UUID id : event.getParticipants()) {
            double damage = event.getDamage().getOrDefault(id, 0.0);
            String n = name(id);
            hits.add(new RankingService.Hit(id.toString(), n, damage));
            byName.put(n, damage);
        }
        UUID la = event.getLastAttack();
        ranking.recordKill(event.getBossId(), event.getBossName(), event.getFloor(), event.isFirstKill(),
                la == null ? null : la.toString(), la == null ? null : name(la), hits);
        discord.send(DiscordService.Kind.CHEFE, Map.of("chefe", event.getBossName(), "andar", String.valueOf(event.getFloor()),
                "last_attack", la == null ? "ninguém" : name(la), "top", DiscordRules.top(byName, 3),
                "participantes", String.valueOf(hits.size()),
                "primeira", event.isFirstKill() ? "primeira vitória" : "vitória"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRaid(RaidScheduledEvent event) {
        discord.send(DiscordService.Kind.RAID, Map.of("chefe", event.getBossName(), "andar", String.valueOf(event.getFloor()),
                "lider", name(event.getLeader()), "data", DATE.format(Instant.ofEpochMilli(event.getAt()))));
    }
}
