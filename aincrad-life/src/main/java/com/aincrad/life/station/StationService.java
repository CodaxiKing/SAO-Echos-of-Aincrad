package com.aincrad.life.station;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.life.hook.Hooks;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * As bancadas das profissões no mundo. Cada uma é um Item Display com o modelo do pack, uma
 * entidade Interaction para o clique e uma barreira para o jogador não atravessar. Quem está sem
 * o pack (e o Bedrock) vê o bloco provisório no lugar. Sem o AincradEffects no servidor, a
 * bancada é o próprio bloco provisório.
 *
 * <p>As posições ficam no banco do core; as entidades não são salvas com o mundo e são recriadas
 * quando o pedaço do mapa carrega.
 */
public final class StationService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_stations ({AUTO_ID}, server VARCHAR(64) NOT NULL, type VARCHAR(64) NOT NULL, "
                    + "world VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, yaw FLOAT NOT NULL)");

    /** Uma bancada posta no mundo. */
    public static final class Placed {
        public final long id;
        public final StationType type;
        public final String world;
        public final int x;
        public final int y;
        public final int z;
        public final float yaw;
        ItemDisplay display;
        Interaction click;

        Placed(long id, StationType type, String world, int x, int y, int z, float yaw) {
            this.id = id;
            this.type = type;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        /** Centro da base da bancada. */
        public Location base() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
        }

        /** Um ponto de ancoragem no mundo. */
        public Location anchor(double[] point) {
            Location b = base();
            if (b == null) {
                return null;
            }
            double[] r = StationType.rotate(point, yaw);
            return b.add(r[0], r[1], r[2]);
        }
    }

    private final Plugin plugin;
    private final Messages msg;
    private final Hooks hooks;
    private final Database db;
    private final NamespacedKey key;
    private final Map<String, StationType> types = new LinkedHashMap<>();
    private final Map<Long, Placed> placed = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<Player, Placed>> handlers = new HashMap<>();
    private boolean models;

    public StationService(Plugin plugin, Messages msg, Hooks hooks, Database db) {
        this.plugin = plugin;
        this.msg = msg;
        this.hooks = hooks;
        this.db = db;
        this.key = new NamespacedKey(plugin, "estacao");
    }

    public NamespacedKey key() {
        return key;
    }

    /** Lê stations.yml. {@code mode}: "auto" (modelos só com o AincradEffects), "sim" ou "nao". */
    public void loadTypes(String mode) {
        File f = new File(plugin.getDataFolder(), "stations.yml");
        if (!f.exists()) {
            plugin.saveResource("stations.yml", false);
        }
        types.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(f).getConfigurationSection("estacoes");
        List<String> errors = new ArrayList<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s != null) {
                    StationType t = StationType.parse(id, s, errors);
                    types.put(t.id(), t);
                }
            }
        }
        errors.forEach(e -> plugin.getLogger().warning("stations.yml: " + e));
        models = mode.equalsIgnoreCase("sim") || (mode.equalsIgnoreCase("auto") && hooks.hasEffects());
    }

    public Map<String, StationType> types() {
        return types;
    }

    public Map<Long, Placed> placed() {
        return placed;
    }

    /** O tipo de bancada com este id (stations.yml), ou null. */
    public StationType type(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Uma bancada que não está no mapa nem no banco, só para abrir o painel sobre um bloco
     * qualquer (a fogueira do cozinheiro de campanha).
     */
    public Placed virtual(StationType type, org.bukkit.block.Block block, float yaw) {
        return new Placed(-1, type, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), StationType.snapYaw(yaw));
    }

    /** Roda antes de qualquer bancada abrir (as encomendas trazem os materiais do contrato). Null tira. */
    public void beforeUse(java.util.function.Consumer<Player> hook) {
        this.beforeUse = hook;
    }

    private java.util.function.Consumer<Player> beforeUse;

    /** Um módulo de profissão diz o que acontece ao clicar numa bancada do tipo. */
    public void onUse(String type, BiConsumer<Player, Placed> handler) {
        handlers.put(type.toLowerCase(Locale.ROOT), handler);
    }

    /** Esquece o que os módulos registraram (ao religar os módulos). */
    public void clearHandlers() {
        handlers.clear();
    }

    // ------------------------------------------------------------------ banco

    /** Lê as bancadas deste servidor e monta as que estão em pedaços carregados. */
    public void loadPlacedBlocking() throws java.sql.SQLException {
        removeAllEntities();
        placed.clear();
        String server = AincradCore.get().server();
        for (Object[] r : db.sync(c -> Database.query(c, "SELECT id, type, world, x, y, z, yaw FROM life_stations WHERE server = ?",
                rs -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getFloat(7)}, server))) {
            StationType t = types.get(((String) r[1]).toLowerCase(Locale.ROOT));
            if (t == null) {
                plugin.getLogger().warning("Bancada #" + r[0] + " é do tipo \"" + r[1] + "\", que não existe mais em stations.yml.");
                continue;
            }
            Placed p = new Placed((Long) r[0], t, (String) r[2], (Integer) r[3], (Integer) r[4], (Integer) r[5], (Float) r[6]);
            placed.put(p.id, p);
            World w = Bukkit.getWorld(p.world);
            if (w != null && w.isChunkLoaded(p.x >> 4, p.z >> 4)) {
                spawn(p);
            }
        }
    }

    /** Põe uma bancada no bloco, virada para o jogador. */
    public void create(Player by, StationType type, Block at) {
        float yaw = StationType.snapYaw(by.getLocation().getYaw());
        String server = AincradCore.get().server();
        String world = at.getWorld().getName();
        db.async(c -> {
            Database.update(c, "INSERT INTO life_stations (server, type, world, x, y, z, yaw) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    server, type.id(), world, at.getX(), at.getY(), at.getZ(), yaw);
            return Database.query(c, "SELECT MAX(id) FROM life_stations WHERE server = ? AND world = ? AND x = ? AND y = ? AND z = ?",
                    rs -> rs.getLong(1), server, world, at.getX(), at.getY(), at.getZ()).get(0);
        }).whenComplete((id, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível gravar a bancada", err);
                msg.send(by, "geral.erro");
                return;
            }
            Placed p = new Placed(id, type, world, at.getX(), at.getY(), at.getZ(), yaw);
            placed.put(id, p);
            spawn(p);
            msg.send(by, "estacao.criada", Messages.vars("tipo", type.name(), "id", id));
        }));
    }

    public void remove(Placed p) {
        placed.remove(p.id);
        despawn(p);
        Location b = p.base();
        if (b != null) {
            b.getBlock().setType(Material.AIR);
        }
        db.update("DELETE FROM life_stations WHERE id = ?", p.id);
    }

    /** A bancada que o jogador está olhando (até 5 blocos), ou null. */
    public Placed lookingAt(Player p) {
        Entity e = p.getTargetEntity(5);
        Placed viaEntity = e == null ? null : of(e);
        if (viaEntity != null) {
            return viaEntity;
        }
        Block b = p.getTargetBlockExact(5);
        return b == null ? null : at(b);
    }

    public Placed at(Block b) {
        for (Placed p : placed.values()) {
            if (p.x == b.getX() && p.y == b.getY() && p.z == b.getZ() && p.world.equals(b.getWorld().getName())) {
                return p;
            }
        }
        return null;
    }

    public Placed of(Entity e) {
        Long id = e.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        return id == null ? null : placed.get(id);
    }

    // ------------------------------------------------------------------ entidades

    private BlockData provisional(Placed p) {
        Material m = Material.matchMaterial(p.type.block());
        if (m == null || !m.isBlock()) {
            m = Material.CRAFTING_TABLE;
        }
        BlockData data = m.createBlockData();
        if (data instanceof Directional d) {
            BlockFace face = switch ((int) p.yaw) {
                case 90 -> BlockFace.WEST;
                case 180 -> BlockFace.NORTH;
                case 270 -> BlockFace.EAST;
                default -> BlockFace.SOUTH;
            };
            if (d.getFaces().contains(face)) {
                d.setFacing(face);
            }
        }
        if (data instanceof Levelled l) {
            l.setLevel(l.getMaximumLevel());
        }
        return data;
    }

    private void spawn(Placed p) {
        Location base = p.base();
        if (base == null) {
            return;
        }
        despawn(p);
        // Sem modelos: o bloco provisório de verdade. Com modelos: barreira + Item Display.
        base.getBlock().setBlockData(models ? Material.BARRIER.createBlockData() : provisional(p), false);
        if (models) {
            NamespacedKey model = NamespacedKey.fromString(p.type.model());
            p.display = base.getWorld().spawn(base.clone().add(0, 0.5, 0), ItemDisplay.class, d -> {
                d.setPersistent(false);
                ItemStack it = new ItemStack(Material.PAPER);
                ItemMeta meta = it.getItemMeta();
                if (model != null) {
                    meta.setItemModel(model);
                }
                it.setItemMeta(meta);
                d.setItemStack(it);
                float s = (float) p.type.scale();
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
                d.setRotation(p.yaw, 0);
                d.getPersistentDataContainer().set(key, PersistentDataType.LONG, p.id);
            });
        }
        p.click = base.getWorld().spawn(base, Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth((float) p.type.clickWidth());
            i.setInteractionHeight((float) p.type.clickHeight());
            i.setResponsive(true);
            i.getPersistentDataContainer().set(key, PersistentDataType.LONG, p.id);
        });
    }

    private void despawn(Placed p) {
        if (p.display != null) {
            p.display.remove();
            p.display = null;
        }
        if (p.click != null) {
            p.click.remove();
            p.click = null;
        }
    }

    /**
     * Troca o modelo da bancada para a versão "_active" (ex.: fornalha acesa) enquanto ela está em
     * uso, se o arquivo existir em resourcepack/assets/&lt;ns&gt;/items/&lt;modelo&gt;_active.json.
     */
    public void setActive(Placed p, boolean active) {
        if (p.display == null || !p.display.isValid()) {
            return;
        }
        String model = p.type.model();
        if (active) {
            int colon = model.indexOf(':');
            File f = new File(plugin.getDataFolder(), "resourcepack/assets/" + model.substring(0, colon) + "/items/"
                    + model.substring(colon + 1) + "_active.json");
            if (!f.isFile()) {
                return;
            }
            model = model + "_active";
        }
        NamespacedKey key = NamespacedKey.fromString(model);
        ItemStack it = p.display.getItemStack();
        ItemMeta meta = it.getItemMeta();
        if (key != null && meta != null) {
            meta.setItemModel(key);
            it.setItemMeta(meta);
            p.display.setItemStack(it);
        }
    }

    public void removeAllEntities() {
        placed.values().forEach(this::despawn);
    }

    /** Refaz todas as bancadas carregadas (depois de mudar stations.yml: ancoragem, escala, modelo). */
    public void respawnAll() {
        for (Placed p : placed.values()) {
            World w = Bukkit.getWorld(p.world);
            if (w != null && w.isChunkLoaded(p.x >> 4, p.z >> 4)) {
                spawn(p);
            }
        }
    }

    /**
     * Roda a cada 2 s, só com modelos ligados: quem está perto e sem o pack vê o bloco provisório
     * no lugar da barreira e não vê o modelo (que para ele seria um papel).
     */
    public void syncViewers() {
        if (!models) {
            return;
        }
        for (Placed p : placed.values()) {
            Location base = p.base();
            if (base == null || p.display == null || !p.display.isValid()) {
                continue;
            }
            BlockData fake = null;
            for (Player pl : base.getWorld().getPlayers()) {
                if (pl.getLocation().distanceSquared(base) > 64 * 64) {
                    continue;
                }
                if (hooks.hasPack(pl)) {
                    pl.showEntity(plugin, p.display);
                } else {
                    pl.hideEntity(plugin, p.display);
                    if (fake == null) {
                        fake = provisional(p);
                    }
                    pl.sendBlockChange(base, fake);
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk c = event.getChunk();
        for (Placed p : placed.values()) {
            if (p.x >> 4 == c.getX() && p.z >> 4 == c.getZ() && p.world.equals(c.getWorld().getName())) {
                // No próximo tick: entidades não podem nascer durante o carregamento do pedaço.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (placed.containsKey(p.id) && c.isLoaded()) {
                        spawn(p);
                    }
                });
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk c = event.getChunk();
        for (Placed p : placed.values()) {
            if (p.x >> 4 == c.getX() && p.z >> 4 == c.getZ() && p.world.equals(c.getWorld().getName())) {
                despawn(p);
            }
        }
    }

    // ------------------------------------------------------------------ clique

    private void use(Player p, Placed st) {
        BiConsumer<Player, Placed> h = handlers.get(st.type.id());
        if (h == null) {
            msg.send(p, "estacao.em_breve", Messages.vars("tipo", st.type.name()));
            return;
        }
        if (beforeUse != null) {
            try {
                beforeUse.accept(p);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Erro antes de usar a bancada " + st.type.id(), e);
            }
        }
        try {
            h.accept(p, st);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao usar a bancada " + st.type.id(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent event) {
        Placed st = event.getHand() == EquipmentSlot.HAND ? of(event.getRightClicked()) : null;
        if (st != null) {
            event.setCancelled(true);
            use(event.getPlayer(), st);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(PrePlayerAttackEntityEvent event) {
        Placed st = of(event.getAttacked());
        if (st != null) {
            event.setCancelled(true);
            use(event.getPlayer(), st);
        }
    }

    /** O bloco da bancada (barreira ou provisório) só sai pelo comando de remover. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }
}
