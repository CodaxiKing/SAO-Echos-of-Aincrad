package com.aincrad.economy.house;

import com.aincrad.core.AincradCore;
import com.aincrad.core.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * {@code /casa ir}: leva o jogador à casa dele, também quando ela fica em outro servidor da
 * rede. O servidor da casa é o do andar dela (floors.yml do AincradFloors). Ao trocar de
 * servidor, o destino fica anotado no perfil do core e é cumprido na chegada.
 */
public final class HouseTravel implements Listener {

    private static final String PROFILE_KEY = "casa_ir";

    private final Plugin plugin;
    private final Messages msg;
    private final HouseService houses;
    private final String channel;

    public HouseTravel(Plugin plugin, Messages msg, HouseService houses, String channel) {
        this.plugin = plugin;
        this.msg = msg;
        this.houses = houses;
        this.channel = channel;
    }

    /** O servidor onde fica o andar; "" se é este, se não dá para saber ou sem o AincradFloors. */
    private static String serverOf(int floor) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AincradFloors")) {
            return "";
        }
        try {
            Object r = Class.forName("com.aincrad.floors.api.AincradFloorsAPI", true,
                            Bukkit.getPluginManager().getPlugin("AincradFloors").getClass().getClassLoader())
                    .getMethod("getServer", int.class).invoke(null, floor);
            return r == null ? "" : r.toString();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return "";
        }
    }

    public void go(Player p, House h) {
        Location here = HouseService.spawnLocation(h.spawn());
        String server = serverOf(h.floor());
        if (!server.isEmpty()) {
            AincradCore.get().profile(p).set(plugin, PROFILE_KEY, h.name());
            msg.send(p, "casa.indo", Messages.vars("casa", h.name(), "servidor", server));
            // Um instante para o perfil ser gravado antes da troca.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    connect(p, server);
                }
            }, 10L);
            return;
        }
        if (here == null) {
            msg.send(p, "casa.sem_spawn");
            return;
        }
        p.teleportAsync(here);
        msg.send(p, "casa.teleportado", Messages.vars("casa", h.name()));
    }

    private void connect(Player p, String server) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, channel)) {
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
            }
            p.sendPluginMessage(plugin, channel, bytes.toByteArray());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Não foi possível mandar " + p.getName() + " para o servidor " + server + ": " + e);
        }
    }

    /** Chegou de outro servidor a caminho de casa: termina a viagem. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            var profile = AincradCore.get().profile(p);
            String name = profile.get(plugin, PROFILE_KEY, "");
            if (name.isEmpty()) {
                return;
            }
            profile.remove(plugin, PROFILE_KEY);
            House h = houses.byName(name);
            Location at = h == null || !h.canUse(p.getUniqueId()) ? null : HouseService.spawnLocation(h.spawn());
            if (at != null) {
                p.teleportAsync(at);
                msg.send(p, "casa.teleportado", Messages.vars("casa", h.name()));
            }
        }, 40L);
    }
}
