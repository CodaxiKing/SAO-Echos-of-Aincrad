package com.aincrad.quests;

import com.aincrad.core.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * NPCs simples do próprio plugin: uma entidade parada, que não se fere, com nome. Ficam em
 * npcs.yml e não são salvos com o mapa — o plugin os põe de volta quando o pedaço carrega, então
 * nunca duplicam. Sobre a cabeça, um "!" (quest disponível) ou "?" (algo a entregar), que cada
 * jogador vê conforme as quests dele.
 */
public final class NpcService implements Listener {

    private static final Pattern ID = Pattern.compile("[a-z0-9_\\-]{1,32}");
    private static final double SEE = 32;

    /** Um NPC do arquivo. */
    public record Npc(String id, String name, String world, double x, double y, double z, float yaw, String type, String greeting) {
    }

    /** As entidades de um NPC que está no mundo agora. */
    private record Body(Entity npc, TextDisplay available, TextDisplay ready) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final QuestService quests;
    private final NamespacedKey key;
    private final File file;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final Map<String, Body> bodies = new HashMap<>();

    public NpcService(Plugin plugin, Messages msg, QuestService quests) {
        this.plugin = plugin;
        this.msg = msg;
        this.quests = quests;
        key = new NamespacedKey(plugin, "npc");
        file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public Map<String, Npc> all() {
        return npcs;
    }

    // ------------------------------------------------------------------ arquivo

    public void load() {
        removeBodies();
        npcs.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("npcs");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null || !validId(id) || s.getString("mundo") == null) {
                    plugin.getLogger().warning("npcs.yml: NPC '" + id + "' inválido, ignorado.");
                    continue;
                }
                npcs.put(id, new Npc(id, s.getString("nome", id), s.getString("mundo"), s.getDouble("x"), s.getDouble("y"),
                        s.getDouble("z"), (float) s.getDouble("yaw"), s.getString("tipo", "VILLAGER"), s.getString("fala", "")));
            }
        }
        for (Npc n : npcs.values()) {
            World w = Bukkit.getWorld(n.world());
            if (w != null && w.isChunkLoaded((int) Math.floor(n.x()) >> 4, (int) Math.floor(n.z()) >> 4)) {
                spawn(n);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Npc n : npcs.values()) {
            String p = "npcs." + n.id() + ".";
            y.set(p + "nome", n.name());
            y.set(p + "mundo", n.world());
            y.set(p + "x", n.x());
            y.set(p + "y", n.y());
            y.set(p + "z", n.z());
            y.set(p + "yaw", (double) n.yaw());
            y.set(p + "tipo", n.type());
            y.set(p + "fala", n.greeting());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível gravar o npcs.yml", e);
        }
    }

    /** Cria ou muda de lugar. O nome e a fala antigos ficam quando os novos vêm null. */
    public Npc put(String id, String name, Location at, String type) {
        Npc old = npcs.get(id);
        Npc n = new Npc(id, name != null ? name : old != null ? old.name() : id, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                at.getYaw(), type != null ? type.toUpperCase(Locale.ROOT) : old != null ? old.type() : "VILLAGER",
                old != null ? old.greeting() : "");
        npcs.put(id, n);
        save();
        remove(bodies.remove(id));
        spawn(n);
        return n;
    }

    public boolean setGreeting(String id, String greeting) {
        Npc n = npcs.get(id);
        if (n == null) {
            return false;
        }
        npcs.put(id, new Npc(n.id(), n.name(), n.world(), n.x(), n.y(), n.z(), n.yaw(), n.type(), greeting));
        save();
        return true;
    }

    public boolean delete(String id) {
        if (npcs.remove(id) == null) {
            return false;
        }
        save();
        remove(bodies.remove(id));
        return true;
    }

    // ------------------------------------------------------------------ no mundo

    private static EntityType type(String name) {
        try {
            EntityType t = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            return t.isSpawnable() && t.isAlive() ? t : EntityType.VILLAGER;
        } catch (IllegalArgumentException e) {
            return EntityType.VILLAGER;
        }
    }

    private TextDisplay marker(Location at, String path) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.text(msg.get(path));
            t.getPersistentDataContainer().set(key, PersistentDataType.STRING, "");
        });
        return d;
    }

    private void spawn(Npc n) {
        World w = Bukkit.getWorld(n.world());
        if (w == null || bodies.containsKey(n.id())) {
            return;
        }
        Location at = new Location(w, n.x(), n.y(), n.z(), n.yaw(), 0);
        Entity e = w.spawnEntity(at, type(n.type()), CreatureSpawnEvent.SpawnReason.CUSTOM, ent -> {
            ent.setPersistent(false);
            ent.setInvulnerable(true);
            ent.setSilent(true);
            ent.setGravity(false);
            ent.customName(Messages.parse(n.name()));
            ent.setCustomNameVisible(true);
            ent.getPersistentDataContainer().set(key, PersistentDataType.STRING, n.id());
            if (ent instanceof LivingEntity le) {
                le.setAI(false);
                le.setCollidable(false);
                le.setRemoveWhenFarAway(false);
            }
            if (ent instanceof Mob m) {
                m.setAware(false);
            }
        });
        Location top = at.clone().add(0, e.getHeight() + 0.55, 0);
        bodies.put(n.id(), new Body(e, marker(top, "npc.icone_disponivel"), marker(top, "npc.icone_pronta")));
    }

    private static void remove(Body b) {
        if (b != null) {
            b.npc().remove();
            b.available().remove();
            b.ready().remove();
        }
    }

    public void removeBodies() {
        bodies.values().forEach(NpcService::remove);
        bodies.clear();
    }

    private static boolean in(Npc n, Chunk c) {
        return n.world().equals(c.getWorld().getName())
                && ((int) Math.floor(n.x()) >> 4) == c.getX() && ((int) Math.floor(n.z()) >> 4) == c.getZ();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Npc n : npcs.values()) {
            if (in(n, event.getChunk())) {
                // No tick seguinte: criar entidade dentro do evento de carga não é seguro.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (npcs.get(n.id()) == n && event.getChunk().isLoaded()) {
                        spawn(n);
                    }
                });
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Npc n : npcs.values()) {
            if (in(n, event.getChunk())) {
                remove(bodies.remove(n.id()));
            }
        }
    }

    /** A cada 2 segundos: cada jogador por perto vê o ícone que vale para as quests dele. */
    public void tick() {
        for (var e : bodies.entrySet()) {
            Body b = e.getValue();
            if (!b.npc().isValid()) {
                continue;
            }
            for (Player p : b.npc().getWorld().getPlayers()) {
                boolean near = p.getLocation().distanceSquared(b.npc().getLocation()) <= SEE * SEE;
                QuestService.NpcStatus s = near ? quests.status(p, e.getKey()) : QuestService.NpcStatus.NADA;
                show(p, b.available(), s == QuestService.NpcStatus.DISPONIVEL);
                show(p, b.ready(), s == QuestService.NpcStatus.PRONTA);
            }
        }
    }

    private void show(Player p, Entity marker, boolean visible) {
        if (!marker.isValid()) {
            return;
        }
        if (visible) {
            p.showEntity(plugin, marker);
        } else {
            p.hideEntity(plugin, marker);
        }
    }

    private String npcOf(Entity e) {
        String id = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null || id.isEmpty() ? null : id;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        String id = npcOf(event.getRightClicked());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        Npc n = npcs.get(id);
        if (event.getHand() == EquipmentSlot.HAND && n != null) {
            Component name = Messages.parse(n.name());
            quests.talk(event.getPlayer(), id, name, n.greeting());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}
