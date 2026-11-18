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

    /** O jogador está num duelo do AincradSocial? Sem o plugin: false. */
    public boolean dueling(Player p) {
        if (!has("AincradSocial")) {
            return false;
        }
        try {
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (c.getName().equals("com.aincrad.social.api.AincradSocialAPI")) {
                    Object api = Bukkit.getServicesManager().load(c);
                    return api != null && Boolean.TRUE.equals(c.getMethod("isDueling", java.util.UUID.class).invoke(api, p.getUniqueId()));
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
        return false;
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
