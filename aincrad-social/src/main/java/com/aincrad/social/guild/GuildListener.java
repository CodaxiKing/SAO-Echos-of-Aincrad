package com.aincrad.social.guild;

import com.aincrad.social.AincradSocialPlugin;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/** Tag no chat e na lista, chat da guilda, fogo amigo, XP por mob e avisos ao entrar. */
public final class GuildListener implements Listener {

    private final AincradSocialPlugin plugin;

    public GuildListener(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    private GuildService svc() {
        return plugin.guilds();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        svc().markSeen(p.getUniqueId());
        plugin.guildManager().refreshName(p);
        plugin.guildManager().payRefund(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            for (Guild g : svc().invitesFor(p.getUniqueId())) {
                plugin.messages().send(p, "guilda.convite_pendente",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("guilda", g.name()),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("tag", g.tag()));
            }
        }, 80L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        svc().markSeen(event.getPlayer().getUniqueId());
        plugin.guildManager().cancelHome(event.getPlayer().getUniqueId());
        plugin.saveGuildsSoon();
    }

    /** "#texto" vai só para a guilda. Roda depois do ChatInput (LOWEST) e junto do "@" da party. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGuildChat(AsyncChatEvent event) {
        String prefix = plugin.guildConfig().chatPrefix;
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!text.startsWith(prefix) || text.length() <= prefix.length() || svc().guildOf(event.getPlayer().getUniqueId()) == null) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        String body = text.substring(prefix.length()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.guildManager().chat(p, body));
    }

    /** Tag da guilda antes do nome no chat, com a cor de cada espectador (verde, azul, vermelha). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChatTag(AsyncChatEvent event) {
        Guild g = svc().guildOf(event.getPlayer().getUniqueId());
        if (g == null) {
            return;
        }
        ChatRenderer original = event.renderer();
        event.renderer((source, displayName, message, viewer) -> {
            var line = original.render(source, displayName, message, viewer);
            return plugin.guildManager().tagFor(g, viewer instanceof Player vp ? vp : null).append(line);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (plugin.guildConfig().friendlyFire || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (svc().sameGuild(attacker.getUniqueId(), victim.getUniqueId()) || svc().areAllies(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            plugin.guildManager().cancelHome(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && event.getEntity() instanceof Enemy && !(event.getEntity() instanceof Player)) {
            plugin.guildManager().mobKilled(killer);
        }
    }

    // "/aincrad guilda ..." (padrão do plano) vira "/guilda admin ...".
    static String rewrite(String line) {
        String t = line.stripLeading();
        String l = t.toLowerCase(Locale.ROOT);
        if (l.equals("aincrad guilda") || l.startsWith("aincrad guilda ")) {
            return "guilda admin" + t.substring("aincrad guilda".length());
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String out = event.getMessage().startsWith("/") ? rewrite(event.getMessage().substring(1)) : null;
        if (out != null) {
            event.setMessage("/" + out);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String c = event.getCommand();
        String out = rewrite(c.startsWith("/") ? c.substring(1) : c);
        if (out != null) {
            event.setCommand(out);
        }
    }
}
