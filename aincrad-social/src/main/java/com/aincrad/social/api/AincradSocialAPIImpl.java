package com.aincrad.social.api;

import com.aincrad.social.AincradSocialPlugin;
import com.aincrad.social.guild.Guild;
import com.aincrad.social.service.PartyService;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Implementação registrada no ServicesManager. */
public final class AincradSocialAPIImpl implements AincradSocialAPI {

    private final AincradSocialPlugin plugin;

    public AincradSocialAPIImpl(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<UUID> getParty(UUID player) {
        PartyService.Party p = plugin.parties().partyOf(player);
        return p == null ? List.of() : p.members();
    }

    @Override
    public GuildInfo getGuild(UUID player) {
        Guild g = plugin.guilds().guildOf(player);
        if (g == null) {
            return null;
        }
        return new GuildInfo(g.id(), g.name(), g.tag(), plugin.hooks().emblem(g.emblem()), g.level(), g.rankOf(player).name(),
                plugin.guildManager().isRed(g));
    }

    @Override
    public boolean areAllies(UUID a, UUID b) {
        return plugin.parties().sameParty(a, b) || plugin.guilds().sameGuild(a, b) || plugin.guilds().areAllies(a, b);
    }

    @Override
    public boolean isDueling(UUID player) {
        return plugin.duels().service().duelOf(player) != null;
    }

    @Override
    public boolean inDuel(UUID a, UUID b) {
        return plugin.duels().service().inDuel(a, b);
    }

    @Override
    public void addGuildXp(UUID player, long amount, String reason) {
        Guild g = plugin.guilds().guildOf(player);
        if (g != null) {
            plugin.guildManager().addXp(g, amount);
        }
    }

    @Override
    public void recordBossKill(Collection<UUID> participants, int floor, UUID lastAttacker) {
        Set<Guild> guilds = new LinkedHashSet<>();
        for (UUID p : participants) {
            Guild g = plugin.guilds().guildOf(p);
            if (g != null) {
                guilds.add(g);
            }
        }
        Guild la = lastAttacker == null ? null : plugin.guilds().guildOf(lastAttacker);
        if (la != null) {
            guilds.add(la);
        }
        for (Guild g : guilds) {
            boolean isLa = g == la;
            plugin.guilds().recordBossKill(g, floor, isLa);
            plugin.guildManager().addXp(g, plugin.guildConfig().xpBoss + (isLa ? plugin.guildConfig().xpLastAttack : 0));
        }
    }

    @Override
    public void questCompleted(UUID player) {
        addGuildXp(player, plugin.guildConfig().xpQuest, "quest");
    }

    @Override
    public long taxIncome(Player player, long income) {
        long t = plugin.guilds().tax(player.getUniqueId(), player.getName(), income);
        if (t > 0) {
            plugin.saveGuildsSoon();
        }
        return t;
    }
}
