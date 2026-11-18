package com.aincrad.social.party;

import com.aincrad.social.AincradSocialPlugin;
import com.aincrad.social.config.Settings;
import com.aincrad.social.party.PartyRules.DropRule;
import com.aincrad.social.service.PartyService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * O que a party divide: o XP dos mobs (do AincradRPG) entre os membros próximos e os drops
 * conforme a regra da config (livre, por vez ou do líder).
 */
public final class PartyRewards implements Listener {

    private static final String XP_EVENT = "com.aincrad.rpg.api.XpGainEvent";

    private final AincradSocialPlugin plugin;
    /** party → quem recebeu o último drop (rodízio). */
    private final Map<UUID, UUID> lastTurn = new HashMap<>();
    private Method xpAmount;
    private Method xpSetAmount;
    private Method xpReason;
    private Method xpPlayer;

    public PartyRewards(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    /** Membros da party de {@code p} que estão por perto, vivos (com ele). Vazio se não tem party. */
    private List<Player> nearby(Player p, double radius) {
        PartyService.Party party = plugin.parties().partyOf(p.getUniqueId());
        if (party == null) {
            return List.of();
        }
        List<Player> out = new ArrayList<>();
        Location at = p.getLocation();
        for (UUID id : party.members()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && !m.isDead() && m.getWorld().equals(p.getWorld())
                    && (m.equals(p) || m.getLocation().distanceSquared(at) <= radius * radius)) {
                out.add(m);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ XP

    /**
     * Ouve o XpGainEvent do AincradRPG sem depender dele para compilar. Chamar depois que os
     * plugins ligaram. @return true se o RPG existe e a divisão está ligada
     */
    public boolean hookXp() {
        Plugin rpg = Bukkit.getPluginManager().getPlugin("AincradRPG");
        if (rpg == null || !rpg.isEnabled()) {
            return false;
        }
        try {
            Class<? extends Event> type = Class.forName(XP_EVENT, true, rpg.getClass().getClassLoader()).asSubclass(Event.class);
            xpAmount = type.getMethod("getAmount");
            xpSetAmount = type.getMethod("setAmount", long.class);
            xpReason = type.getMethod("getReason");
            xpPlayer = type.getMethod("getPlayer");
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.NORMAL, (listener, event) -> {
                if (type.isInstance(event)) {
                    onXp(event);
                }
            }, plugin, true);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "AincradRPG incompatível: o XP não é dividido na party", e);
            return false;
        }
    }

    private void onXp(Event event) {
        Settings s = plugin.settings();
        if (!s.partyShareXp) {
            return;
        }
        try {
            // Só o XP de mob é dividido; o que este plugin repassa vem com o motivo "party".
            if (!"mob".equals(xpReason.invoke(event)) || !(xpPlayer.invoke(event) instanceof Player killer)) {
                return;
            }
            List<Player> near = nearby(killer, s.partyXpRadius);
            if (near.size() < 2) {
                return;
            }
            long share = PartyRules.share((long) xpAmount.invoke(event), near.size(), s.partyXpBonus);
            xpSetAmount.invoke(event, share);
            for (Player m : near) {
                if (!m.equals(killer)) {
                    plugin.hooks().addXp(m, share, "party");
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível dividir o XP da party", e);
        }
    }

    // ------------------------------------------------------------------ drops

    /** Por último, quando os outros plugins já puseram os drops deles. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        DropRule rule = plugin.settings().partyDrops;
        Player killer = event.getEntity().getKiller();
        if (rule == DropRule.LIVRE || killer == null || event.getEntity() instanceof Player || event.getDrops().isEmpty()) {
            return;
        }
        PartyService.Party party = plugin.parties().partyOf(killer.getUniqueId());
        if (party == null) {
            return;
        }
        Set<UUID> eligible = new LinkedHashSet<>();
        for (Player m : nearby(killer, plugin.settings().partyXpRadius)) {
            eligible.add(m.getUniqueId());
        }
        UUID owner = PartyRules.dropOwner(rule, party.members(), party.leader(), eligible, killer.getUniqueId(), lastTurn.get(party.id()));
        if (owner == null) {
            return;
        }
        if (rule == DropRule.POR_VEZ) {
            if (lastTurn.size() > 500) {
                lastTurn.clear(); // parties antigas; o rodízio só recomeça
            }
            lastTurn.put(party.id(), owner);
        }
        // O item cai no chão como sempre, mas só o dono consegue pegar.
        Location at = event.getEntity().getLocation();
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && !drop.getType().isAir()) {
                at.getWorld().dropItemNaturally(at, drop, item -> item.setOwner(owner));
            }
        }
        event.getDrops().clear();
        Player to = Bukkit.getPlayer(owner);
        if (to != null) {
            to.sendActionBar(plugin.messages().get(to.equals(killer) ? "party.drop_seu" : "party.drop_vez",
                    Placeholder.unparsed("jogador", killer.getName())));
        }
    }
}
