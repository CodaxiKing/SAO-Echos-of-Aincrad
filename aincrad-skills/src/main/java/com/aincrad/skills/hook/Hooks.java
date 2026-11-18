package com.aincrad.skills.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Pontes para os plugins opcionais. As classes {@code *Bridge} usam as APIs deles e só são
 * tocadas quando o plugin está ligado.
 */
public final class Hooks {

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

    /** O level do jogador no AincradRPG. Sem o plugin, todos os espaços ficam liberados. */
    public int level(Player p) {
        if (!has("AincradRPG")) {
            return Integer.MAX_VALUE;
        }
        try {
            return RpgBridge.level(p);
        } catch (LinkageError | RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }
}
