package com.aincrad.reputation;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.reputation.api.PlayerJailEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Guildas de crime: o que a Laughing Coffin fazia. Uma guilda vermelha do AincradSocial (o líder
 * tem cursor vermelho) pode ter uma base escondida — que não aparece em lista nenhuma até alguém a
 * encontrar a pé — e armar emboscadas, onde a vítima fica sem cristal de teleporte por alguns
 * segundos depois do primeiro golpe. Derrotar o líder dentro da base rende a recompensa de todos
 * os membros.
 */
public final class CrimeService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS rep_bases (guild VARCHAR(64) NOT NULL PRIMARY KEY, guild_name VARCHAR(64) NOT NULL, "
                    + "server VARCHAR(64) NOT NULL, world VARCHAR(64) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, at_time BIGINT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS rep_base_found (uuid VARCHAR(36) NOT NULL, guild VARCHAR(64) NOT NULL, PRIMARY KEY (uuid, guild))");

    /** A guilda de um jogador, como o AincradSocial a descreve. */
    public record Guild(String id, String name, String rank, boolean red) {

        public boolean leader() {
            return "LIDER".equals(rank);
        }
    }

    /** A base escondida de uma guilda. */
    public record Base(String guild, String guildName, String server, String world, int x, int y, int z) {

        public boolean contains(String serverNow, Location l, double radius) {
            return server.equals(serverNow) && l.getWorld() != null && world.equals(l.getWorld().getName())
                    && inRadius(x + 0.5, y, z + 0.5, l.getX(), l.getY(), l.getZ(), radius);
        }
    }

    /** O ponto está a até {@code radius} blocos do centro? */
    public static boolean inRadius(double cx, double cy, double cz, double x, double y, double z, double radius) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** Na emboscada, só o cristal de teleporte falha, e só enquanto o bloqueio dura. */
    public static boolean crystalBlocked(String crystalType, long blockedUntil, long now) {
        return blockedUntil > now && crystalType != null && crystalType.toLowerCase(Locale.ROOT).contains("teleport");
    }

    /** Uma emboscada armada. */
    private record Ambush(Location at, long until) {
    }

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private final ConfigFile config;
    private final BountyService bounties;
    private volatile Map<String, Base> bases = Map.of();
    private final Map<UUID, Set<String>> found = new ConcurrentHashMap<>();
    private final Map<String, Ambush> ambushes = new HashMap<>();
    private final Map<String, Long> ambushCooldown = new HashMap<>();
    private final Map<UUID, Long> crystalBlock = new HashMap<>();

    public CrimeService(Plugin plugin, Database db, Messages msg, ConfigFile config, BountyService bounties) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.config = config;
        this.bounties = bounties;
    }

    private boolean enabled() {
        return config.bool("crime.ativo", true);
    }

    // ------------------------------------------------------------------ a guilda (AincradSocial, por reflexão)

    /** A guilda do jogador, ou null (sem guilda, ou sem o AincradSocial). */
    public Guild guild(UUID player) {
        try {
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (!c.getName().equals("com.aincrad.social.api.AincradSocialAPI")) {
                    continue;
                }
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                Object info = r == null ? null : c.getMethod("getGuild", UUID.class).invoke(r.getProvider(), player);
                if (info == null) {
                    return null;
                }
                Class<?> k = info.getClass();
                return new Guild(String.valueOf(k.getMethod("id").invoke(info)), String.valueOf(k.getMethod("name").invoke(info)),
                        String.valueOf(k.getMethod("rank").invoke(info)), Boolean.TRUE.equals(k.getMethod("red").invoke(info)));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "AincradSocial incompatível (guilda)", e);
        }
        return null;
    }

    // ------------------------------------------------------------------ dados

    private static Map<String, Base> read(java.sql.Connection c) throws SQLException {
        Map<String, Base> m = new ConcurrentHashMap<>();
        Database.query(c, "SELECT guild, guild_name, server, world, x, y, z FROM rep_bases",
                rs -> m.put(rs.getString(1), new Base(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7))));
        return m;
    }

    public void loadBlocking() throws SQLException {
        bases = db.sync(CrimeService::read);
    }

    public void refresh() {
        db.async(CrimeService::read).thenAccept(m -> bases = m).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler as bases escondidas", ex);
            return null;
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        db.query("SELECT guild FROM rep_base_found WHERE uuid = ?", rs -> rs.getString(1), id.toString())
                .thenAccept(list -> found.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).addAll(list))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível ler as bases que " + id + " encontrou", ex);
                    return null;
                });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        found.remove(event.getPlayer().getUniqueId());
        crystalBlock.remove(event.getPlayer().getUniqueId());
    }

    private double baseRadius() {
        return config.decimal("crime.base_raio", 12, 2, 64);
    }

    // ------------------------------------------------------------------ comando /crime

    public void command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (!enabled()) {
            msg.send(p, "crime.desligado");
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "bases" -> listFound(p);
            case "base" -> base(p, args.length > 1 && args[1].equalsIgnoreCase("remover"));
            case "emboscada" -> ambush(p);
            default -> msg.send(p, "crime.uso");
        }
    }

    private void base(Player p, boolean remove) {
        Guild g = guild(p.getUniqueId());
        if (g == null || !g.leader() || !g.red()) {
            msg.send(p, "crime.so_lider_vermelho");
            return;
        }
        if (remove) {
            db.update("DELETE FROM rep_bases WHERE guild = ?", g.id()).thenRun(this::refresh);
            db.update("DELETE FROM rep_base_found WHERE guild = ?", g.id());
            msg.send(p, "crime.base_removida");
            return;
        }
        Location l = p.getLocation();
        db.update(db.upsert("rep_bases", List.of("guild"), List.of("guild_name", "server", "world", "x", "y", "z", "at_time")),
                g.id(), g.name(), AincradCore.get().server(), l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                System.currentTimeMillis()).thenRun(this::refresh);
        // A base muda de lugar: quem tinha achado a antiga precisa achar de novo.
        db.update("DELETE FROM rep_base_found WHERE guild = ?", g.id());
        found.values().forEach(s -> s.remove(g.id()));
        msg.send(p, "crime.base_definida", Messages.vars("raio", (int) baseRadius()));
    }

    private void listFound(Player p) {
        Set<String> mine = found.getOrDefault(p.getUniqueId(), Set.of());
        boolean any = false;
        for (Base b : bases.values()) {
            if (mine.contains(b.guild())) {
                any = true;
                msg.sendRaw(p, "crime.base_linha", Messages.vars("guilda", b.guildName(), "mundo", b.world(), "x", b.x(), "y", b.y(), "z", b.z()));
            }
        }
        if (!any) {
            msg.send(p, "crime.nenhuma_base");
        }
    }

    private void ambush(Player p) {
        Guild g = guild(p.getUniqueId());
        if (g == null || !g.red()) {
            msg.send(p, "crime.so_guilda_vermelha");
            return;
        }
        long now = System.currentTimeMillis();
        if (ambushCooldown.getOrDefault(g.id(), 0L) > now) {
            msg.send(p, "crime.emboscada_recarga", Messages.vars("minutos", (ambushCooldown.get(g.id()) - now) / 60_000 + 1));
            return;
        }
        long minutes = config.integer("crime.emboscada_minutos", 10, 1, 240);
        ambushes.put(g.id(), new Ambush(p.getLocation().clone(), now + minutes * 60_000L));
        ambushCooldown.put(g.id(), now + config.integer("crime.emboscada_recarga_minutos", 30, 0, 1440) * 60_000L);
        msg.send(p, "crime.emboscada_armada", Messages.vars("minutos", minutes, "raio", (int) config.decimal("crime.emboscada_raio", 16, 2, 64)));
    }

    // ------------------------------------------------------------------ achar a base a pé

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || bases.isEmpty() || !enabled()) {
            return;
        }
        Player p = event.getPlayer();
        Set<String> mine = found.get(p.getUniqueId());
        if (mine == null) {
            return;
        }
        String server = AincradCore.get().server();
        for (Base b : bases.values()) {
            if (mine.contains(b.guild()) || !b.contains(server, event.getTo(), baseRadius())) {
                continue;
            }
            mine.add(b.guild());
            Guild own = guild(p.getUniqueId());
            if (own != null && own.id().equals(b.guild())) {
                continue; // Os membros conhecem a própria base.
            }
            db.update("INSERT INTO rep_base_found (uuid, guild) VALUES (?, ?)", p.getUniqueId().toString(), b.guild()).exceptionally(ex -> 0);
            msg.send(p, "crime.base_encontrada", Messages.vars("guilda", b.guildName()));
        }
    }

    // ------------------------------------------------------------------ emboscada

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent event) {
        if (ambushes.isEmpty() || !(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player a ? a
                : event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player a ? a : null;
        Guild g = attacker == null || attacker.equals(victim) ? null : guild(attacker.getUniqueId());
        Ambush amb = g == null ? null : ambushes.get(g.id());
        long now = System.currentTimeMillis();
        if (amb == null || amb.until() <= now) {
            if (amb != null) {
                ambushes.remove(g.id());
            }
            return;
        }
        double radius = config.decimal("crime.emboscada_raio", 16, 2, 64);
        World w = amb.at().getWorld();
        if (w == null || !w.equals(victim.getWorld()) || victim.getLocation().distanceSquared(amb.at()) > radius * radius) {
            return;
        }
        Guild vg = guild(victim.getUniqueId());
        if (vg != null && vg.id().equals(g.id())) {
            return;
        }
        // Só o primeiro golpe conta: o bloqueio não se renova a cada golpe.
        if (crystalBlock.getOrDefault(victim.getUniqueId(), 0L) <= now) {
            int seconds = config.integer("crime.emboscada_segundos", 15, 1, 300);
            crystalBlock.put(victim.getUniqueId(), now + seconds * 1000L);
            msg.send(victim, "crime.emboscado", Messages.vars("segundos", seconds));
        }
    }

    /** O cristal deste tipo falha agora para o jogador? (pego numa emboscada) */
    public boolean crystalBlocked(Player p, String crystalType) {
        return crystalBlocked(crystalType, crystalBlock.getOrDefault(p.getUniqueId(), 0L), System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ o líder derrotado na base

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJail(PlayerJailEvent event) {
        Player leader = event.getPlayer();
        Guild g = guild(leader.getUniqueId());
        Base b = g == null ? null : bases.get(g.id());
        Location where = leader.getLastDeathLocation() != null ? leader.getLastDeathLocation() : leader.getLocation();
        if (b == null || !g.leader() || !b.contains(AincradCore.get().server(), where, baseRadius())) {
            return;
        }
        // O líder caiu dentro da própria base: a recompensa de todos os membros vai para quem o derrotou.
        Map<UUID, Boolean> member = new HashMap<>();
        bounties.payMatching(event.getCaptor(), g.name(), target -> member.computeIfAbsent(target, t -> {
            Guild tg = guild(t);
            return tg != null && tg.id().equals(g.id());
        }));
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, "crime.base_caiu", Messages.vars("guilda", g.name(), "captor", event.getCaptor().getName(), "lider", leader.getName()));
        }
    }
}
