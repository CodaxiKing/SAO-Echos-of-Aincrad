package com.aincrad.ui.pack;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Ponte opcional com o AincradEffects. O AincradUI não compila contra o Effects: procura a API
 * dele no ServicesManager pelo nome da classe e chama os métodos por reflexão. Sem o Effects,
 * {@link #available()} é false e o AincradUI usa o pack e as partículas próprios.
 *
 * <p>Contrato esperado (a ser implementado no aincrad-effects):
 * <pre>
 * package com.aincrad.effects.api;
 * interface AincradEffectsAPI {
 *     void registerPackFolder(Plugin dono, File pasta);
 *     boolean play(String efeito, Player alvo, Map&lt;String,String&gt; variaveis);
 *     boolean play(String efeito, Location local, double raio, Map&lt;String,String&gt; variaveis);
 * }
 * </pre>
 */
public final class EffectsBridge {

    public static final String API_CLASS = "com.aincrad.effects.api.AincradEffectsAPI";

    private final Object provider;
    private final Class<?> type;
    private final Logger log;

    private EffectsBridge(Object provider, Class<?> type, Logger log) {
        this.provider = provider;
        this.type = type;
        this.log = log;
    }

    public static EffectsBridge detect(Logger log) {
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals(API_CLASS)) {
                RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(c);
                if (reg != null) {
                    return new EffectsBridge(reg.getProvider(), c, log);
                }
            }
        }
        return new EffectsBridge(null, null, log);
    }

    public boolean available() {
        return provider != null;
    }

    public boolean registerPackFolder(Plugin owner, File folder) {
        Object result = invoke("registerPackFolder", new Class<?>[]{Plugin.class, File.class}, owner, folder);
        return result != null && !Boolean.FALSE.equals(result);
    }

    public boolean play(String effect, Player target, Map<String, String> vars) {
        return Boolean.TRUE.equals(invoke("play", new Class<?>[]{String.class, Player.class, Map.class}, effect, target, vars));
    }

    public boolean play(String effect, Location where, double radius, Map<String, String> vars) {
        return Boolean.TRUE.equals(invoke("play", new Class<?>[]{String.class, Location.class, double.class, Map.class},
                effect, where, radius, vars));
    }

    private Object invoke(String name, Class<?>[] params, Object... args) {
        if (provider == null) {
            return null;
        }
        try {
            Method m = type.getMethod(name, params);
            Object result = m.invoke(provider, args);
            return m.getReturnType() == void.class ? Boolean.TRUE : result;
        } catch (NoSuchMethodException e) {
            log.warning("AincradEffects não tem o método " + name + " esperado pelo AincradUI.");
        } catch (Exception e) {
            log.warning("Falha ao chamar AincradEffects." + name + ": " + e);
        }
        return null;
    }
}
