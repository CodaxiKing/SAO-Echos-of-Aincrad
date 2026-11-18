package com.aincrad.social;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** Avisos ao entrar e sair, fogo amigo e chat da party. */
public final class SocialListener implements Listener {

    private final AincradSocialPlugin plugin;

    public SocialListener(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.store().rememberName(p.getUniqueId(), p.getName());
        plugin.saveSoon();
        // Espera um pouco para não se misturar com as mensagens de entrada do servidor.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            int requests = plugin.friends().incoming(p.getUniqueId()).size();
            if (requests > 0) {
                plugin.messages().send(p, "amigo.pendentes", Placeholder.unparsed("total", String.valueOf(requests)));
            }
            int unread = plugin.inbox().unread(p.getUniqueId());
            if (unread > 0) {
                plugin.messages().send(p, "mensagem.nao_lidas", Placeholder.unparsed("total", String.valueOf(unread)));
            }
        }, 60L);
        announce(p, "amigo.entrou");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        announce(p, "amigo.saiu");
        plugin.actions().leave(p.getUniqueId(), p.getName(), false);
    }

    private void announce(Player p, String key) {
        if (!plugin.settings().announceFriends) {
            return;
        }
        for (UUID id : plugin.friends().friends(p.getUniqueId())) {
            Player f = Names.online(id);
            if (f != null && !f.equals(p)) {
                plugin.messages().send(f, key, Placeholder.unparsed("jogador", p.getName()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (plugin.settings().friendlyFire || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker != null && !attacker.equals(victim)
                && !plugin.duels().service().inDuel(attacker.getUniqueId(), victim.getUniqueId())
                && plugin.parties().sameParty(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** "@texto" no chat vai só para a party. Roda depois do ChatInput (LOWEST). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String prefix = plugin.settings().partyChatPrefix;
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!text.startsWith(prefix) || text.length() <= prefix.length()) {
            return;
        }
        Player p = event.getPlayer();
        if (plugin.parties().partyOf(p.getUniqueId()) == null) {
            return; // sem party, a mensagem vai para o chat normal
        }
        event.setCancelled(true);
        String body = text.substring(prefix.length()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.actions().partyChat(p, body));
    }
}
