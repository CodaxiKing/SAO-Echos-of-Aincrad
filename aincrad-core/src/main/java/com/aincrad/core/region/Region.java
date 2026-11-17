package com.aincrad.core.region;

import org.bukkit.Location;

import java.util.regex.Pattern;

/**
 * Uma caixa nomeada entre dois cantos de bloco (inclusive), num mundo.
 *
 * @param owner plugin que criou (ou "admin" quando veio de /aincrad regiao criar)
 */
public record Region(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String owner) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_\\-.]{1,64}");

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** Monta a partir de dois cantos em qualquer ordem. */
    public static Region of(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2, String owner) {
        return new Region(name, world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), owner);
    }

    public boolean contains(String w, double x, double y, double z) {
        return world.equals(w)
                && x >= minX && x < maxX + 1
                && y >= minY && y < maxY + 1
                && z >= minZ && z < maxZ + 1;
    }

    public boolean contains(Location l) {
        return l.getWorld() != null && contains(l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
