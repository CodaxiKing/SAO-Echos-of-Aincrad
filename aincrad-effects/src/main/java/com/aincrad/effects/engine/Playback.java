package com.aincrad.effects.engine;

import com.aincrad.effects.AincradEffectsPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/** Um efeito tocando: origem, alvo, espectadores, variáveis e tudo que precisa ser limpo no fim. */
public final class Playback {

    private final AincradEffectsPlugin plugin;
    private final EffectEngine engine;
    private final Effect effect;
    private final Location origin;
    private final Player target;
    private final List<Player> viewers;
    private final Map<String, String> vars;
    private final long startedTick;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Runnable> cleanups = new ArrayList<>();
    private boolean stopped;

    Playback(AincradEffectsPlugin plugin, EffectEngine engine, Effect effect, Location origin, Player target,
             List<Player> viewers, Map<String, String> vars) {
        this.plugin = plugin;
        this.engine = engine;
        this.effect = effect;
        this.origin = origin.clone();
        this.target = target;
        this.viewers = List.copyOf(viewers);
        this.vars = Map.copyOf(vars);
        this.startedTick = Bukkit.getCurrentTick();
    }

    public AincradEffectsPlugin plugin() {
        return plugin;
    }

    public Effect effect() {
        return effect;
    }

    public Location origin() {
        return origin.clone();
    }

    public Player target() {
        return target;
    }

    public Map<String, String> vars() {
        return vars;
    }

    public long startedTick() {
        return startedTick;
    }

    public boolean stopped() {
        return stopped;
    }

    /** Espectadores ainda online. Para passos na tela (título, actionbar, barra, menu). */
    public List<Player> viewers() {
        List<Player> out = new ArrayList<>();
        for (Player p : viewers) {
            if (p.isOnline()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Espectadores no mesmo mundo e dentro de distancia_visao de {@code at}. Para passos no mundo. */
    public List<Player> viewersNear(Location at) {
        double max = plugin.settings().viewDistance;
        double max2 = max * max;
        World w = at.getWorld();
        List<Player> out = new ArrayList<>();
        for (Player p : viewers) {
            if (p.isOnline() && p.getWorld().equals(w) && p.getLocation().distanceSquared(at) <= max2) {
                out.add(p);
            }
        }
        return out;
    }

    public boolean involves(Player p) {
        return p.equals(target) || viewers.contains(p);
    }

    /** Texto do YAML pronto para um espectador (variáveis, PlaceholderAPI e &lt;img:id&gt;). */
    public Component text(String raw, Player viewer) {
        Map<String, String> v = vars;
        if (viewer != null) {
            v = new HashMap<>(vars);
            v.put("espectador", viewer.getName());
        }
        return engine.text().render(raw, v, viewer);
    }

    public String substitute(String raw) {
        return TextRenderer.substitute(raw, vars);
    }

    public boolean hasPack(Player p) {
        return engine.text().hasPack(p);
    }

    public Limits limits() {
        return engine.limits();
    }

    /** Roda {@code r} daqui a {@code delay} ticks (0 = agora), se o efeito não tiver parado. */
    public void later(int delay, Runnable r) {
        if (stopped) {
            return;
        }
        if (delay <= 0) {
            safely(r);
            return;
        }
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!stopped) {
                safely(r);
            }
        }, delay));
    }

    /**
     * Roda {@code tick} a cada {@code period} ticks durante {@code total} ticks, recebendo os ticks
     * passados desde o começo (0, period, 2·period...). A última chamada recebe {@code total}.
     */
    public void animate(int period, int total, IntConsumer tick) {
        if (stopped) {
            return;
        }
        int p = Math.max(1, period);
        safely(() -> tick.accept(0));
        if (total <= 0) {
            return;
        }
        int[] elapsed = {0};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (stopped) {
                self[0].cancel();
                return;
            }
            elapsed[0] = Math.min(total, elapsed[0] + p);
            safely(() -> tick.accept(elapsed[0]));
            if (elapsed[0] >= total) {
                self[0].cancel();
            }
        }, p, p);
        tasks.add(self[0]);
    }

    /** Registra uma limpeza que roda no fim do efeito (ou quando ele é parado). */
    public void onStop(Runnable r) {
        cleanups.add(r);
    }

    /**
     * Cria uma entidade que só os jogadores dados enxergam, respeitando o limite de entidades por
     * jogador. A entidade some depois de {@code lifetime} ticks ou quando o efeito para.
     *
     * @return a entidade, ou null se ninguém pôde recebê-la
     */
    public <T extends Entity> T spawnFor(Location at, Class<T> type, Consumer<T> init, List<Player> to, int lifetime) {
        if (stopped || at.getWorld() == null) {
            return null;
        }
        List<Player> allowed = new ArrayList<>();
        for (Player p : to) {
            if (limits().takeEntity(p)) {
                allowed.add(p);
            }
        }
        if (allowed.isEmpty()) {
            return null;
        }
        T entity = at.getWorld().spawn(at, type, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            init.accept(e);
        });
        List<UUID> shown = new ArrayList<>();
        for (Player p : allowed) {
            p.showEntity(plugin, entity);
            shown.add(p.getUniqueId());
        }
        AtomicBoolean done = new AtomicBoolean();
        Runnable remove = () -> {
            if (done.compareAndSet(false, true)) {
                if (entity.isValid()) {
                    entity.remove();
                }
                shown.forEach(limits()::releaseEntity);
            }
        };
        cleanups.add(remove);
        if (lifetime > 0) {
            later(lifetime, remove);
        }
        return entity;
    }

    private void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Erro no efeito " + effect.id() + " (" + effect.source() + ")", e);
        }
    }

    /** Para o efeito: cancela o que falta e limpa entidades, barras e menus. */
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        for (BukkitTask t : tasks) {
            t.cancel();
        }
        tasks.clear();
        List<Runnable> list = new ArrayList<>(cleanups);
        Collections.reverse(list);
        cleanups.clear();
        for (Runnable r : list) {
            try {
                r.run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "Falha ao limpar efeito " + effect.id(), e);
            }
        }
        engine.finished(this);
    }
}
