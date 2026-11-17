package com.aincrad.bosses.model;

import org.bukkit.Location;

/** Uma caixa de blocos num mundo (sala do chefe, zona do labirinto). */
public record Cuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Cuboid of(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Cuboid(world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public boolean contains(String w, double x, double y, double z) {
        return world.equals(w) && x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    public boolean contains(Location l) {
        return l != null && l.getWorld() != null && contains(l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    /** "mundo,x1,y1,z1,x2,y2,z2". */
    public String text() {
        return world + "," + minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
    }

    public static Cuboid parse(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(",");
        if (p.length != 7) {
            return null;
        }
        try {
            return of(p[0].trim(), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()),
                    Integer.parseInt(p[4].trim()), Integer.parseInt(p[5].trim()), Integer.parseInt(p[6].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
