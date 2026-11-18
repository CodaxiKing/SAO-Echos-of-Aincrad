package com.aincrad.reputation.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Pontes para os plugins opcionais. As classes {@code *Bridge} e {@code *Listener} deste pacote
 * usam as APIs deles e só são tocadas quando o plugin está ligado.
 */
public final class Hooks {

    private final Plugin plugin;

    public Hooks(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean has(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    /** O lugar é zona segura (cidade) do AincradFloors? Sem o plugin: false. */
    public boolean safeZone(Location l) {
        if (!has("AincradFloors") || l == null) {
            return false;
        }
        try {
            return FloorsBridge.safeZone(l);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** O id do chefe contra quem o jogador está lutando (null sem o AincradBosses, ou fora de luta). */
    public String bossFight(org.bukkit.entity.Player p) {
        if (!has("AincradBosses")) {
            return null;
        }
        try {
            return BossesBridge.fight(p);
        } catch (LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Põe Col do jogador em custódia no AincradEconomy. Null se ele não tem o valor, ou sem o plugin. */
    public String hold(org.bukkit.entity.Player p, long amount, String reason) {
        if (!has("AincradEconomy")) {
            return null;
        }
        try {
            return EcoBridge.hold(p, amount, reason);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (custódia): " + e);
            return null;
        }
    }

    /** Libera a custódia para alguém, online ou não. */
    public void release(String hold, java.util.UUID to, long amount, String reason) {
        try {
            EcoBridge.release(hold, to, amount, reason).exceptionally(ex -> {
                plugin.getLogger().warning("Não foi possível liberar a custódia " + hold + ": " + ex);
                return false;
            });
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (liberar custódia): " + e);
        }
    }

    /** Devolve a custódia ao dono. */
    public void refund(String hold, String reason) {
        try {
            EcoBridge.refund(hold, reason).exceptionally(ex -> {
                plugin.getLogger().warning("Não foi possível devolver a custódia " + hold + ": " + ex);
                return 0L;
            });
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (devolver custódia): " + e);
        }
    }

    /** O andar do lugar (0 sem o AincradFloors, ou fora de qualquer andar). */
    public int floorAt(Location l) {
        if (!has("AincradFloors") || l == null) {
            return 0;
        }
        try {
            return FloorsBridge.floorAt(l);
        } catch (LinkageError | RuntimeException e) {
            return 0;
        }
    }

    /** O ponto de chegada de um andar neste servidor (null sem o AincradFloors, ou se o andar fica em outro servidor). */
    public Location floorSpawn(int floor) {
        if (!has("AincradFloors")) {
            return null;
        }
        try {
            return FloorsBridge.spawn(floor);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradFloors incompatível (ponto de chegada): " + e);
            return null;
        }
    }
}
