package com.aincrad.bosses.data;

import com.aincrad.bosses.model.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * As salas dos chefes, marcadas pelos comandos e guardadas em salas.yml (um arquivo de dados
 * deste servidor: a sala fica no mundo dele).
 */
public final class RoomStore {

    /** A sala de um chefe. Campos null = ainda não marcados. */
    public static final class Room {
        public Cuboid box;
        /** Onde o chefe nasce: "mundo,x,y,z,yaw,pitch". */
        public String spawn;
        /** Para onde vai quem é tirado da sala. */
        public String entry;
        /** Blocos do vão da porta: "x,y,z" (no mundo da sala). Abertos = ar. */
        public final List<int[]> door = new ArrayList<>();
        /** Cantos marcados antes de a caixa existir. */
        public int[] corner1;
        public int[] corner2;
        public String cornerWorld;

        public boolean ready() {
            return box != null;
        }
    }

    private final File file;
    private final Map<String, Room> rooms = new HashMap<>();

    public RoomStore(File file) {
        this.file = file;
        load();
    }

    public void load() {
        rooms.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("salas");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Room r = new Room();
            r.box = Cuboid.parse(s.getString("sala"));
            r.spawn = s.getString("spawn");
            r.entry = s.getString("entrada");
            for (String b : s.getStringList("porta")) {
                String[] p = b.split(",");
                try {
                    r.door.add(new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())});
                } catch (RuntimeException e) {
                    // linha estragada: ignora o bloco
                }
            }
            rooms.put(id.toLowerCase(Locale.ROOT), r);
        }
    }

    public Room get(String id) {
        return rooms.get(id.toLowerCase(Locale.ROOT));
    }

    public Room getOrCreate(String id) {
        return rooms.computeIfAbsent(id.toLowerCase(Locale.ROOT), k -> new Room());
    }

    public Map<String, Room> all() {
        return rooms;
    }

    public void save() throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Salas dos chefes. Gerado pelos comandos /aincrad chefe sala e /aincrad chefe porta."));
        rooms.forEach((id, r) -> {
            String base = "salas." + id + ".";
            if (r.box != null) {
                y.set(base + "sala", r.box.text());
            }
            y.set(base + "spawn", r.spawn);
            y.set(base + "entrada", r.entry);
            List<String> door = new ArrayList<>();
            r.door.forEach(b -> door.add(b[0] + "," + b[1] + "," + b[2]));
            y.set(base + "porta", door);
        });
        y.save(file);
    }

    public static String text(Location l) {
        return String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.1f,%.1f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public static Location location(String s) {
        if (s == null) {
            return null;
        }
        String[] p = s.split(",");
        World w = p.length >= 4 ? Bukkit.getWorld(p[0].trim()) : null;
        if (w == null) {
            return null;
        }
        try {
            return new Location(w, Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim()),
                    p.length >= 6 ? Float.parseFloat(p[4].trim()) : 0f, p.length >= 6 ? Float.parseFloat(p[5].trim()) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
