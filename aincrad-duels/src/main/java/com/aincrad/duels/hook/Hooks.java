package com.aincrad.duels.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Pontes para os plugins opcionais. As classes {@code *Bridge} usam as APIs deles e só são
 * tocadas quando o plugin está ligado.
 */
public final class Hooks {

    public boolean has(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    /**
     * Até a hora dada (milissegundos; 0 encerra), os dois podem se ferir dentro de zona segura
     * (AincradFloors) e sem mudar a cor do cursor (AincradReputation).
     */
    public void exemptPvp(UUID a, UUID b, long until) {
        if (has("AincradFloors")) {
            try {
                Bridges.Floors.exempt(a, b, until);
            } catch (LinkageError | RuntimeException ignored) {
                // versão sem a isenção: o duelo só não vale dentro da cidade
            }
        }
        if (has("AincradReputation")) {
            try {
                Bridges.Reputation.exempt(a, b, until);
            } catch (LinkageError | RuntimeException ignored) {
                // versão sem a isenção
            }
        }
    }

    public boolean hasEconomy() {
        return has("AincradEconomy");
    }

    public long balance(Player p) {
        try {
            return hasEconomy() ? Bridges.Eco.balance(p) : 0;
        } catch (LinkageError | RuntimeException e) {
            return 0;
        }
    }

    /** Põe a aposta do jogador em custódia. @return o id da custódia, ou null se ele não tem o Col */
    public String hold(Player p, long amount) {
        try {
            return hasEconomy() ? Bridges.Eco.hold(p, amount) : null;
        } catch (LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Entrega a custódia inteira a {@code to}. */
    public void release(String hold, UUID to, long amount) {
        if (hold != null && hasEconomy()) {
            try {
                Bridges.Eco.release(hold, to, amount);
            } catch (LinkageError | RuntimeException ignored) {
                // sem a economia, não havia custódia
            }
        }
    }

    /** Devolve a custódia ao dono. */
    public void refund(String hold) {
        if (hold != null && hasEconomy()) {
            try {
                Bridges.Eco.refund(hold);
            } catch (LinkageError | RuntimeException ignored) {
                // sem a economia, não havia custódia
            }
        }
    }

    public String format(long col) {
        try {
            return hasEconomy() ? Bridges.Eco.format(col) : col + " Col";
        } catch (LinkageError | RuntimeException e) {
            return col + " Col";
        }
    }
}
