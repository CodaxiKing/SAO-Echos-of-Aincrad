package com.aincrad.social.party;

import com.aincrad.social.AincradSocialPlugin;
import com.aincrad.social.service.PartyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A party na rede: quem está em qual party vale para todos os servidores. Cada servidor guarda
 * as parties na memória e avisa os outros do que mudou; convites e chat atravessam servidores,
 * e trocar de servidor não tira ninguém da party.
 *
 * <p>Continua local: o XP dividido, os drops e as barras de vida, que só fazem sentido entre
 * jogadores que estão no mesmo lugar.
 */
public final class PartyNet {

    private static final String CH_PARTY = "social:party";
    private static final String CH_INVITE = "social:pinvite";
    private static final String CH_CHAT = "social:pchat";
    private static final String CH_ONLINE = "social:online";
    /** Sem notícia de um jogador por este tempo, ele não conta mais como online em outro servidor. */
    private static final long PRESENCE_MILLIS = 150_000L;

    private final AincradSocialPlugin plugin;
    /** O que os outros servidores já sabem de cada party (id → "líder|membros"). */
    private Map<UUID, String> known = new HashMap<>();
    /** Jogadores vistos em outros servidores (id → quando). */
    private final Map<UUID, Long> elsewhere = new ConcurrentHashMap<>();

    public PartyNet(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void start() {
        var storage = plugin.storage();
        storage.subscribe(CH_PARTY, this::onParty);
        storage.subscribe(CH_INVITE, this::onInvite);
        storage.subscribe(CH_CHAT, this::onChat);
        storage.subscribe(CH_ONLINE, payload -> {
            String[] p = payload.split("\\|");
            UUID id = uuid(p[0]);
            if (id != null) {
                if (p.length > 1 && p[1].equals("1")) {
                    elsewhere.put(id, System.currentTimeMillis());
                } else {
                    elsewhere.remove(id);
                }
            }
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::sync, 10L, 10L);
        // De minuto em minuto cada servidor repete quem está nele (um aviso perdido se corrige sozinho).
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                storage.publish(CH_ONLINE, p.getUniqueId() + "|1");
            }
        }, 40L, 1200L);
    }

    // ------------------------------------------------------------------ quem está online

    public boolean onlineElsewhere(UUID id) {
        Long seen = elsewhere.get(id);
        return seen != null && System.currentTimeMillis() - seen < PRESENCE_MILLIS;
    }

    public void joined(Player p) {
        elsewhere.remove(p.getUniqueId());
        plugin.storage().publish(CH_ONLINE, p.getUniqueId() + "|1");
    }

    /**
     * O jogador saiu deste servidor. Se em 10 segundos ele não apareceu em outro, saiu da rede:
     * aí sim deixa a party.
     */
    public void quit(Player p) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        plugin.storage().publish(CH_ONLINE, id + "|0");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(id) == null && !onlineElsewhere(id)) {
                plugin.actions().leave(id, name, false);
            }
        }, 200L);
    }

    // ------------------------------------------------------------------ parties

    /** Avisa os outros servidores das parties que mudaram aqui desde a última vez. */
    private void sync() {
        Map<UUID, String> now = plugin.parties().snapshot();
        for (Map.Entry<UUID, String> e : now.entrySet()) {
            if (!e.getValue().equals(known.get(e.getKey()))) {
                plugin.storage().publish(CH_PARTY, e.getKey() + "|" + e.getValue());
            }
        }
        for (UUID id : known.keySet()) {
            if (!now.containsKey(id)) {
                plugin.storage().publish(CH_PARTY, id + "|");
            }
        }
        known = now;
    }

    /** "id|líder|m1,m2" (ou "id|" quando a party acabou). */
    private void onParty(String payload) {
        String[] p = payload.split("\\|", -1);
        UUID id = uuid(p[0]);
        if (id == null) {
            return;
        }
        UUID leader = p.length > 2 ? uuid(p[1]) : null;
        List<UUID> members = new ArrayList<>();
        if (p.length > 2) {
            for (String s : p[2].split(",")) {
                UUID m = uuid(s);
                if (m != null) {
                    members.add(m);
                }
            }
        }
        if (leader == null || members.size() < 2) {
            plugin.parties().remove(id);
            known.remove(id);
        } else {
            plugin.parties().apply(id, leader, members);
            known.put(id, PartyService.encode(leader, members));
        }
    }

    // ------------------------------------------------------------------ convites e chat

    /** Convite para alguém que está em outro servidor. */
    public void sendInvite(Player from, UUID to) {
        plugin.storage().publish(CH_INVITE, from.getUniqueId() + "|" + to + "|" + from.getName());
    }

    private void onInvite(String payload) {
        String[] p = payload.split("\\|", 3);
        UUID from = p.length == 3 ? uuid(p[0]) : null;
        UUID to = p.length == 3 ? uuid(p[1]) : null;
        Player target = to == null ? null : Bukkit.getPlayer(to);
        if (from == null || target == null) {
            return;
        }
        if (plugin.parties().invite(from, to) == PartyService.InviteResult.SENT) {
            plugin.actions().showInvite(target, p[2]);
        }
    }

    /** Repassa uma linha do chat da party aos membros que estão em outros servidores. */
    public void chat(UUID party, Component line) {
        plugin.storage().publish(CH_CHAT, party + "\n" + GsonComponentSerializer.gson().serialize(line));
    }

    private void onChat(String payload) {
        int cut = payload.indexOf('\n');
        UUID id = cut > 0 ? uuid(payload.substring(0, cut)) : null;
        if (id == null) {
            return;
        }
        Component line = GsonComponentSerializer.gson().deserialize(payload.substring(cut + 1));
        for (PartyService.Party party : plugin.parties().parties()) {
            if (party.id().equals(id)) {
                for (UUID m : party.members()) {
                    Player o = Bukkit.getPlayer(m);
                    if (o != null) {
                        o.sendMessage(line);
                    }
                }
            }
        }
    }
}
