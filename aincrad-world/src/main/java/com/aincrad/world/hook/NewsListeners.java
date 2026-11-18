package com.aincrad.world.hook;

import com.aincrad.bosses.api.BossDefeatEvent;
import com.aincrad.floors.api.AincradFloorsAPI;
import com.aincrad.floors.api.FloorUnlockEvent;
import com.aincrad.life.api.FishingTournamentEndEvent;
import com.aincrad.life.api.MarriageEvent;
import com.aincrad.reputation.api.AchievementGrantEvent;
import com.aincrad.reputation.api.PlayerJailEvent;
import com.aincrad.rpg.api.XpGainEvent;
import com.aincrad.world.NewsService;
import com.aincrad.world.NightService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Os ouvintes dos outros plugins: cada classe só é carregada quando o plugin dela está ligado.
 * As notícias saem daqui; o XP da noite também.
 */
public final class NewsListeners {

    private NewsListeners() {
    }

    private static String name(UUID id) {
        String n = id == null ? null : Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    public static final class Floors implements Listener {
        private final NewsService news;

        public Floors(NewsService news) {
            this.news = news;
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onUnlock(FloorUnlockEvent event) {
            String n = AincradFloorsAPI.getFloorName(event.getFloor());
            news.record("andar", Map.of("andar", String.valueOf(event.getFloor()), "nome", n == null ? "" : n));
        }
    }

    public static final class Bosses implements Listener {
        private final NewsService news;

        public Bosses(NewsService news) {
            this.news = news;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onDefeat(BossDefeatEvent event) {
            // Só a primeira vitória é notícia: o desafio repetido não enche o jornal.
            if (event.isFirstKill()) {
                news.record("chefe", Map.of("chefe", event.getBossName(), "andar", String.valueOf(event.getFloor()),
                        "jogador", event.getLastAttack() == null ? "ninguém" : name(event.getLastAttack()),
                        "participantes", String.valueOf(event.getParticipants().size())));
            }
        }
    }

    public static final class Life implements Listener {
        private final NewsService news;

        public Life(NewsService news) {
            this.news = news;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onTournament(FishingTournamentEndEvent event) {
            if (event.getWinner() != null) {
                news.record("torneio", Map.of("jogador", String.valueOf(event.getWinnerName()),
                        "tamanho", String.format(Locale.ROOT, "%.1f", event.getSizeCm()), "andar", String.valueOf(event.getFloor())));
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onMarriage(MarriageEvent event) {
            if (event.isMarried()) {
                news.record("casamento", Map.of("a", name(event.getFirst()), "b", name(event.getSecond())));
            }
        }
    }

    public static final class Reputation implements Listener {
        private final NewsService news;

        public Reputation(NewsService news) {
            this.news = news;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onAchievement(AchievementGrantEvent event) {
            if (event.isAnnounced()) {
                news.record("conquista", Map.of("jogador", event.getPlayer().getName(), "conquista", event.getName()));
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onJail(PlayerJailEvent event) {
            news.record("preso", Map.of("jogador", event.getPlayer().getName(), "captor", event.getCaptor().getName(),
                    "mortes", String.valueOf(event.getKills())));
        }
    }

    /** O XP de um abate feito à noite, fora das cidades, rende mais. */
    public static final class Rpg implements Listener {
        private final NightService night;

        public Rpg(NightService night) {
            this.night = night;
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onXp(XpGainEvent event) {
            if ("mob".equals(event.getReason())) {
                double times = night.xpTimes(event.getPlayer());
                if (times > 1) {
                    event.setAmount(Math.round(event.getAmount() * times));
                }
            }
        }
    }
}
