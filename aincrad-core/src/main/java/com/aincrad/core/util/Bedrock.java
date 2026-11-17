package com.aincrad.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Jogador de Bedrock (Geyser + Floodgate)? Usa a API do Floodgate por reflexão; sem o Floodgate,
 * ninguém é Bedrock.
 */
public final class Bedrock {

    private static Object api;
    private static Method isFloodgate;
    private static boolean checked;

    private Bedrock() {
    }

    private static synchronized void init() {
        if (checked) {
            return;
        }
        checked = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return;
        }
        try {
            Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            api = c.getMethod("getInstance").invoke(null);
            isFloodgate = c.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            api = null;
        }
    }

    public static boolean isBedrock(Player p) {
        init();
        if (api == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isFloodgate.invoke(api, p.getUniqueId()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Para testes e reload: procura o Floodgate de novo na próxima chamada. */
    public static synchronized void reset() {
        checked = false;
        api = null;
    }
}
