package com.aincrad.effects.api;

import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.engine.Audience;
import com.aincrad.effects.engine.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Implementação registrada no ServicesManager. */
public final class AincradEffectsAPIImpl implements AincradEffectsAPI {

    private final AincradEffectsPlugin plugin;

    public AincradEffectsAPIImpl(AincradEffectsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean play(String efeito, Player alvo, Map<String, String> variaveis) {
        Effect e = plugin.engine().get(efeito);
        if (e == null || alvo == null) {
            return false;
        }
        plugin.engine().play(e, alvo.getLocation(), alvo, null, null, variaveis);
        return true;
    }

    @Override
    public boolean play(String efeito, Location local, double raio, Map<String, String> variaveis) {
        Effect e = plugin.engine().get(efeito);
        if (e == null || local == null || local.getWorld() == null) {
            return false;
        }
        plugin.engine().play(e, local, null, raio > 0 ? Audience.radius(raio) : null, null, variaveis);
        return true;
    }

    @Override
    public boolean play(String efeito, Location local, Collection<? extends Player> espectadores, Map<String, String> variaveis) {
        Effect e = plugin.engine().get(efeito);
        if (e == null || local == null || local.getWorld() == null) {
            return false;
        }
        plugin.engine().play(e, local, null, null, Objects.requireNonNull(espectadores, "espectadores"), variaveis);
        return true;
    }

    @Override
    public int stop(Player jogador) {
        return plugin.engine().stop(jogador);
    }

    @Override
    public int stopAll() {
        return plugin.engine().stopAll();
    }

    @Override
    public boolean exists(String efeito) {
        return plugin.engine().get(efeito) != null;
    }

    @Override
    public Set<String> effects() {
        return plugin.engine().ids();
    }

    @Override
    public void registerPackFolder(Plugin dono, File pasta) {
        plugin.pack().register(Objects.requireNonNull(dono, "dono"), Objects.requireNonNull(pasta, "pasta"));
    }

    @Override
    public boolean hasPack(Player jogador) {
        return plugin.packStatus().hasPack(jogador);
    }
}
