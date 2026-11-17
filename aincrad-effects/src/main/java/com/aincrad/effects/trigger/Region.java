package com.aincrad.effects.trigger;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Caixa entre dois cantos (inclusive), num mundo. */
public record Region(String world, Vector min, Vector max) {

    public static Region of(String world, Vector a, Vector b) {
        return new Region(world, Vector.getMinimum(a, b), Vector.getMaximum(a, b));
    }

    public boolean contains(Location loc) {
        return loc.getWorld() != null && loc.getWorld().getName().equals(world)
                && contains(loc.getX(), loc.getY(), loc.getZ());
    }

    /** Coordenadas de bloco: o bloco do canto máximo também conta. */
    public boolean contains(double x, double y, double z) {
        return x >= min.getX() && x < max.getX() + 1
                && y >= min.getY() && y < max.getY() + 1
                && z >= min.getZ() && z < max.getZ() + 1;
    }
}
