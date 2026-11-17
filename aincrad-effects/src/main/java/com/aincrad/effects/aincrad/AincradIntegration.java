package com.aincrad.effects.aincrad;

import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.config.ConfigFiles;
import com.aincrad.effects.engine.Audience;
import com.aincrad.effects.engine.Effect;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Integração com o Aincrad: liga os eventos do AincradFloors e do AincradBosses aos efeitos de
 * effects/aincrad.yml, e cuida da placa da porta e da janela de resultado. Este plugin só
 * desenha: não dá itens, não libera andares e não mexe nas lutas.
 *
 * <p>Esta classe não usa as APIs dos outros plugins; as que usam ({@link FloorsHook},
 * {@link BossesHook}, {@link DoorPlaques}) só são carregadas quando eles estão ligados.
 */
public final class AincradIntegration {

    private final AincradEffectsPlugin plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private YamlConfiguration config = new YamlConfiguration();
    private DoorPlaques plaques;
    private BukkitTask plaqueTask;
    private boolean floors;
    private boolean bosses;

    public AincradIntegration(AincradEffectsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Liga (ou religa, depois de um reload) o que estiver disponível. */
    public void start() {
        stop();
        config = ConfigFiles.load(plugin, "aincrad.yml");
        var pm = plugin.getServer().getPluginManager();
        floors = pm.isPluginEnabled("AincradFloors");
        bosses = pm.isPluginEnabled("AincradBosses");
        try {
            if (floors) {
                register(new FloorsHook(this));
            }
            if (bosses) {
                ResultWindow window = new ResultWindow(plugin, this);
                register(window);
                register(new BossesHook(plugin, this, window));
                if (config.getBoolean("placa.ativo", true)) {
                    plaques = new DoorPlaques(plugin, this);
                    register(plaques);
                    plaqueTask = plugin.getServer().getScheduler().runTaskTimer(plugin, plaques::tick, 20L, 10L);
                }
            }
        } catch (LinkageError e) {
            plugin.getLogger().warning("Integração com o Aincrad desligada: versão incompatível do Floors ou do Bosses (" + e + ").");
            stop();
            return;
        }
        if (floors || bosses) {
            plugin.getLogger().info("Integração com o Aincrad ligada:" + (floors ? " andares" : "") + (bosses ? " chefes" : "") + ".");
        }
    }

    private void register(Listener l) {
        listeners.add(l);
        plugin.getServer().getPluginManager().registerEvents(l, plugin);
    }

    public void stop() {
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        if (plaqueTask != null) {
            plaqueTask.cancel();
            plaqueTask = null;
        }
        if (plaques != null) {
            plaques.clear();
            plaques = null;
        }
    }

    public YamlConfiguration config() {
        return config;
    }

    public boolean active() {
        return floors || bosses;
    }

    /** O efeito configurado para o momento (aincrad.yml → efeitos.&lt;chave&gt;), ou null se desligado ou inexistente. */
    public Effect effect(String key) {
        String id = config.getString("efeitos." + key, "");
        return id == null || id.isBlank() ? null : plugin.engine().get(id);
    }

    /** Toca o efeito do momento para um jogador. @return false se está desligado */
    public boolean play(String key, Player target, Map<String, String> vars) {
        Effect e = effect(key);
        if (e == null || target == null || !target.isOnline()) {
            return false;
        }
        plugin.engine().play(e, target.getLocation(), target, null, null, vars);
        return true;
    }

    /** Toca o efeito num lugar, só para os espectadores dados. */
    public boolean playAt(String key, Location at, Collection<? extends Player> viewers, Map<String, String> vars) {
        Effect e = effect(key);
        if (e == null || viewers.isEmpty()) {
            return false;
        }
        plugin.engine().play(e, at, null, Audience.player(), viewers, vars);
        return true;
    }
}
