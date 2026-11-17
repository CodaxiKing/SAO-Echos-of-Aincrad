package com.aincrad.effects.aincrad;

import com.aincrad.bosses.api.AincradBossesAPI;
import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.engine.TextRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Placa do chefe em cima da porta da sala: um Text Display por jogador, que aparece (com fade e
 * crescendo) quando ele chega perto e some quando se afasta ou a luta começa. Depois da primeira
 * vitória o texto passa a ser o de "Derrotado por …".
 */
final class DoorPlaques implements Listener {

    private record Shown(TextDisplay display, boolean defeated) {
    }

    /** Folga para a placa não piscar na borda do raio. */
    private static final double HYSTERESIS = 2;

    private final AincradEffectsPlugin plugin;
    private final AincradIntegration fx;
    /** Chefe → jogador → a placa que ele vê. */
    private final Map<String, Map<UUID, Shown>> shown = new HashMap<>();

    DoorPlaques(AincradEffectsPlugin plugin, AincradIntegration fx) {
        this.plugin = plugin;
        this.fx = fx;
    }

    void tick() {
        double radius = Math.max(1, fx.config().getDouble("placa.distancia", 8));
        double height = fx.config().getDouble("placa.altura", 1.2);
        for (AincradBossesAPI.BossInfo boss : AincradBossesAPI.getBosses()) {
            Map<UUID, Shown> map = shown.computeIfAbsent(boss.id(), k -> new HashMap<>());
            if (boss.door() == null) {
                removeAll(map);
                continue;
            }
            Location at = boss.door().clone().add(0, height, 0);
            // Quem saiu de perto, mudou de mundo, deslogou — ou a luta começou, ou o estado mudou.
            for (Iterator<Map.Entry<UUID, Shown>> it = map.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Shown> e = it.next();
                Player p = Bukkit.getPlayer(e.getKey());
                boolean keep = p != null && !boss.fighting() && e.getValue().display().isValid()
                        && e.getValue().defeated() == boss.defeated() && p.getWorld().equals(at.getWorld())
                        && p.getLocation().distanceSquared(at) <= (radius + HYSTERESIS) * (radius + HYSTERESIS);
                if (!keep) {
                    remove(e.getKey(), e.getValue());
                    it.remove();
                }
            }
            if (boss.fighting()) {
                continue;
            }
            for (Player p : at.getWorld().getPlayers()) {
                if (!map.containsKey(p.getUniqueId()) && p.getLocation().distanceSquared(at) <= radius * radius) {
                    Shown s = spawn(p, boss, at);
                    if (s != null) {
                        map.put(p.getUniqueId(), s);
                    }
                }
            }
        }
    }

    private Shown spawn(Player p, AincradBossesAPI.BossInfo boss, Location at) {
        if (!plugin.limits().takeEntity(p)) {
            return null;
        }
        TextRenderer renderer = new TextRenderer(plugin.images(), plugin.packStatus()::hasPack);
        String raw = fx.config().getString(boss.defeated() ? "placa.texto_derrotado" : "placa.texto", "{chefe}");
        Component text = renderer.render(raw, Map.of("chefe", boss.name(), "level", String.valueOf(boss.level()),
                "andar", String.valueOf(boss.floor()), "jogador", boss.defeatedBy() == null ? "?" : boss.defeatedBy()), p);
        float scale = (float) Math.max(0.1, fx.config().getDouble("placa.escala", 1.0));
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.text(text);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setLineWidth(400);
            e.setViewRange(1.5f);
            // Começa pequena e quase transparente; no próximo tick cresce e aparece.
            e.setTextOpacity((byte) 30);
            e.setTransformation(scaled(scale * 0.6f));
        });
        p.showEntity(plugin, d);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(10);
                d.setTextOpacity((byte) 255);
                d.setTransformation(scaled(scale));
            }
        }, 2L);
        return new Shown(d, boss.defeated());
    }

    private static Transformation scaled(float s) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    private void remove(UUID player, Shown s) {
        s.display().remove();
        plugin.limits().releaseEntity(player);
    }

    private void removeAll(Map<UUID, Shown> map) {
        map.forEach(this::remove);
        map.clear();
    }

    void clear() {
        shown.values().forEach(this::removeAll);
        shown.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (Map<UUID, Shown> map : shown.values()) {
            Shown s = map.remove(id);
            if (s != null) {
                s.display().remove();
            }
        }
    }
}
