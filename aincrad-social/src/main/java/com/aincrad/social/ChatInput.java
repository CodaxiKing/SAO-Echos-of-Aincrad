package com.aincrad.social;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Texto digitado no chat como resposta (nome de jogador, texto de mensagem): o menu do SAO não
 * tem teclado, então o jogador digita no chat. A próxima linha dele vai para o handler e não
 * aparece para os outros. "cancelar" desiste.
 */
public final class ChatInput implements Listener {

    private record Pending(Consumer<String> handler, long expires) {
    }

    private final AincradSocialPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    /** Espera a próxima linha do jogador. O handler roda na thread principal. */
    public void ask(Player p, Consumer<String> handler) {
        long expires = System.currentTimeMillis() + plugin.settings().typeSeconds * 1000L;
        pending.put(p.getUniqueId(), new Pending(handler, expires));
        plugin.messages().send(p, "geral.digite_cancelar");
    }

    public boolean waiting(Player p) {
        return pending.containsKey(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        Pending pend = pending.remove(p.getUniqueId());
        if (pend == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (System.currentTimeMillis() > pend.expires()) {
                plugin.messages().send(p, "geral.tempo_esgotado");
                return;
            }
            if (text.isEmpty() || text.equalsIgnoreCase("cancelar") || text.equalsIgnoreCase("cancel")) {
                plugin.messages().send(p, "geral.cancelado");
                return;
            }
            pend.handler().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
