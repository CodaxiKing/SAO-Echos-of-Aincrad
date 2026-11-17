package com.aincrad.effects.engine;

import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.step.Step;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Guarda os efeitos carregados e toca/para as execuções. */
public final class EffectEngine {

    private final AincradEffectsPlugin plugin;
    private final Limits limits;
    private final List<Playback> active = new ArrayList<>();
    private Map<String, Effect> effects = new LinkedHashMap<>();
    private TextRenderer text;

    public EffectEngine(AincradEffectsPlugin plugin, Limits limits) {
        this.plugin = plugin;
        this.limits = limits;
    }

    public void setEffects(Map<String, Effect> loaded) {
        stopAll();
        this.effects = new LinkedHashMap<>(loaded);
    }

    public void setText(TextRenderer text) {
        this.text = text;
    }

    TextRenderer text() {
        return text;
    }

    Limits limits() {
        return limits;
    }

    public Effect get(String id) {
        return id == null ? null : effects.get(id);
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(new TreeSet<>(effects.keySet()));
    }

    /**
     * Toca um efeito.
     *
     * @param origin   onde os passos no mundo acontecem
     * @param target   jogador alvo (pode ser null): preenche {jogador} e é o público "jogador"
     * @param audience quem vê; null usa o do efeito ou o padrão da config
     * @param viewers  espectadores explícitos; se não for null, ignora {@code audience}
     */
    public Playback play(Effect effect, Location origin, Player target, Audience audience,
                         Collection<? extends Player> viewers, Map<String, String> vars) {
        Audience aud = audience != null ? audience
                : effect.audience() != null ? effect.audience() : plugin.settings().defaultAudience;
        List<Player> who = viewers != null ? new ArrayList<>(viewers) : aud.resolve(origin, target);

        Map<String, String> all = new HashMap<>();
        all.put("jogador", target != null ? target.getName() : "");
        all.put("nome", target != null ? target.getName() : "");
        all.put("level", target != null ? String.valueOf(target.getLevel()) : "");
        all.put("mundo", origin.getWorld() != null ? origin.getWorld().getName() : "");
        all.put("x", String.valueOf(origin.getBlockX()));
        all.put("y", String.valueOf(origin.getBlockY()));
        all.put("z", String.valueOf(origin.getBlockZ()));
        all.put("efeito", effect.id());
        if (vars != null) {
            vars.forEach((k, v) -> {
                if (k != null && v != null) {
                    all.put(k, v);
                }
            });
        }

        if (target != null) {
            enforceConcurrency(target);
        }
        Playback pb = new Playback(plugin, this, effect, origin, target, who, all);
        active.add(pb);
        for (Step step : effect.steps()) {
            for (int r = 0; r <= step.repeat(); r++) {
                pb.later(step.at() + r * step.interval(), () -> step.run(pb));
            }
        }
        pb.later(effect.length() + 1, pb::stop);
        return pb;
    }

    private void enforceConcurrency(Player target) {
        int max = plugin.settings().maxConcurrentPerPlayer;
        List<Playback> mine = new ArrayList<>();
        for (Playback pb : active) {
            if (target.equals(pb.target())) {
                mine.add(pb);
            }
        }
        for (int i = 0; i <= mine.size() - max; i++) {
            mine.get(i).stop();
        }
    }

    void finished(Playback pb) {
        active.remove(pb);
    }

    /** Para os efeitos em que o jogador é alvo ou espectador. */
    public int stop(Player p) {
        int n = 0;
        for (Playback pb : new ArrayList<>(active)) {
            if (pb.involves(p)) {
                pb.stop();
                n++;
            }
        }
        return n;
    }

    public int stopAll() {
        List<Playback> copy = new ArrayList<>(active);
        copy.forEach(Playback::stop);
        active.clear();
        return copy.size();
    }

    public int activeCount() {
        return active.size();
    }
}
