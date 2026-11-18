package com.aincrad.world.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Pontes para os plugins opcionais. Sem o plugin, cada método tem um resultado neutro. */
public final class Hooks {

    public boolean has(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    /** O andar do lugar (0 sem o AincradFloors, ou fora de qualquer andar). */
    public int floorAt(Location l) {
        if (has("AincradFloors") && l != null) {
            try {
                return Bridges.Floors.floorAt(l);
            } catch (LinkageError | RuntimeException e) {
                return 0;
            }
        }
        return 0;
    }

    public boolean safeZone(Location l) {
        if (has("AincradFloors") && l != null) {
            try {
                return Bridges.Floors.safeZone(l);
            } catch (LinkageError | RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    public String floorName(int floor) {
        if (has("AincradFloors")) {
            try {
                String n = Bridges.Floors.name(floor);
                return n == null ? "" : n;
            } catch (LinkageError | RuntimeException e) {
                return "";
            }
        }
        return "";
    }

    /** Level do mob no AincradRPG (0 sem o plugin, ou se o mob não tem level). */
    public int mobLevel(Entity e) {
        if (has("AincradRPG")) {
            try {
                return Bridges.Rpg.mobLevel(e);
            } catch (LinkageError | RuntimeException ex) {
                return 0;
            }
        }
        return 0;
    }

    public void setMobLevel(LivingEntity e, int level) {
        if (has("AincradRPG")) {
            try {
                Bridges.Rpg.setMobLevel(e, level);
            } catch (LinkageError | RuntimeException ignored) {
                // Sem o RPG compatível o mob fica como nasceu.
            }
        }
    }

    public boolean hasEconomy() {
        return has("AincradEconomy");
    }

    /** Cobra Col. Sem o AincradEconomy, nada é cobrado (true). */
    public boolean charge(Player p, long amount, String reason) {
        if (amount <= 0 || !hasEconomy()) {
            return true;
        }
        try {
            return Bridges.Eco.withdraw(p, amount, reason);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }
}
