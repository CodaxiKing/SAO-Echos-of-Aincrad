package com.aincrad.life.alchemy;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.life.alchemy.AlchemyRegistry.Gather;
import com.aincrad.life.alchemy.AlchemyRegistry.Reagent;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Os pontos de coleta de reagentes: blocos marcados por um admin. Qualquer jogador colhe clicando
 * no bloco; depois de colhido, o ponto leva um tempo para renascer. Quem tem proficiência em
 * alquimia colhe mais e reconhece os reagentes raros.
 */
public final class GatherService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_gather_nodes ({AUTO_ID}, server VARCHAR(64) NOT NULL, kind VARCHAR(64) NOT NULL, "
                    + "world VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, ready BIGINT NOT NULL)");

    /** Um ponto de coleta. {@code ready}: quando volta a dar reagente (milissegundos). */
    public static final class Node {
        public final long id;
        public final String kind;
        public final String world;
        public final int x;
        public final int y;
        public final int z;
        volatile long ready;

        Node(long id, String kind, String world, int x, int y, int z, long ready) {
            this.id = id;
            this.kind = kind;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ready = ready;
        }

        boolean at(Block b) {
            return x == b.getX() && y == b.getY() && z == b.getZ() && world.equals(b.getWorld().getName());
        }
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Database db;
    private final ProfessionService professions;
    private final AlchemyService alchemy;
    private final Map<Long, Node> nodes = new ConcurrentHashMap<>();

    GatherService(Plugin plugin, Messages msg, ConfigFile config, Database db, ProfessionService professions, AlchemyService alchemy) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.db = db;
        this.professions = professions;
        this.alchemy = alchemy;
    }

    public Map<Long, Node> nodes() {
        return nodes;
    }

    /** Lê os pontos deste servidor. */
    public void loadBlocking() throws SQLException {
        nodes.clear();
        for (Node n : db.sync(c -> Database.query(c, "SELECT id, kind, world, x, y, z, ready FROM life_gather_nodes WHERE server = ?",
                rs -> new Node(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getLong(7)),
                AincradCore.get().server()))) {
            nodes.put(n.id, n);
        }
    }

    public Node at(Block b) {
        for (Node n : nodes.values()) {
            if (n.at(b)) {
                return n;
            }
        }
        return null;
    }

    /** Marca o bloco como ponto de coleta do tipo dado. */
    public void create(Player by, Gather kind, Block at) {
        String server = AincradCore.get().server();
        String world = at.getWorld().getName();
        db.async(c -> {
            Database.update(c, "INSERT INTO life_gather_nodes (server, kind, world, x, y, z, ready) VALUES (?, ?, ?, ?, ?, ?, 0)",
                    server, kind.id(), world, at.getX(), at.getY(), at.getZ());
            return Database.query(c, "SELECT MAX(id) FROM life_gather_nodes WHERE server = ? AND world = ? AND x = ? AND y = ? AND z = ?",
                    rs -> rs.getLong(1), server, world, at.getX(), at.getY(), at.getZ()).get(0);
        }).whenComplete((id, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível gravar o ponto de coleta", err);
                msg.send(by, "geral.erro");
                return;
            }
            nodes.put(id, new Node(id, kind.id(), world, at.getX(), at.getY(), at.getZ(), 0));
            msg.send(by, "alquimia.ponto_criado", Messages.vars("tipo", kind.id(), "id", id));
        }));
    }

    public void remove(Node n) {
        nodes.remove(n.id);
        db.update("DELETE FROM life_gather_nodes WHERE id = ?", n.id);
    }

    /** Clique num ponto de coleta: colhe, se já renasceu. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Node n = at(event.getClickedBlock());
        if (n == null) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        Gather kind = alchemy.registry().gather(n.kind);
        if (kind == null) {
            msg.send(p, "alquimia.ponto_sem_tipo");
            return;
        }
        long now = System.currentTimeMillis();
        if (n.ready > now) {
            msg.actionBar(p, "alquimia.ponto_vazio", Messages.vars("tempo", TimeFormat.duration(n.ready - now)));
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double proficiency = professions.proficiency(p, Profession.ALQUIMIA);
        // Quem conhece alquimia reconhece o reagente raro e colhe mais.
        boolean rare = kind.rare() != null && proficiency >= kind.rareProficiency() && rnd.nextDouble() < kind.rareChance();
        Reagent r = alchemy.registry().reagent(rare ? kind.rare() : kind.reagent());
        if (r == null) {
            return;
        }
        double extra = proficiency / Profession.MAX * config.decimal("alquimia.colheita.bonus_no_mestre", 1.0, 0, 10)
                + professions.modifier(p, Profession.ALQUIMIA, "colheita");
        int amount = 1 + (int) extra + (rnd.nextDouble() < extra - Math.floor(extra) ? 1 : 0);
        n.ready = now + kind.respawnSeconds() * 1000L;
        db.update("UPDATE life_gather_nodes SET ready = ? WHERE id = ?", n.ready, n.id);
        AlchemyService.give(p, alchemy.reagentItem(r, amount));
        Location at = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.3, 0.2, 0.3, 0);
        p.playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 0.8f, 1f);
        msg.actionBar(p, rare ? "alquimia.colheu_raro" : "alquimia.colheu", Messages.vars("qtd", amount, "reagente", r.name()));
    }

    /** O bloco de um ponto de coleta só sai pelo comando de remover. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Roda a cada 2 s: um brilho sobre os pontos prontos, para quem está por perto ver. */
    public void sparkle() {
        long now = System.currentTimeMillis();
        for (Node n : nodes.values()) {
            World w = n.ready > now ? null : Bukkit.getWorld(n.world);
            if (w == null || !w.isChunkLoaded(n.x >> 4, n.z >> 4)) {
                continue;
            }
            Location at = new Location(w, n.x + 0.5, n.y + 1.1, n.z + 0.5);
            if (!at.getNearbyPlayers(24).isEmpty()) {
                w.spawnParticle(Particle.END_ROD, at, 2, 0.25, 0.15, 0.25, 0.005);
            }
        }
    }
}
