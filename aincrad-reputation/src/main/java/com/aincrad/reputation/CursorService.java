package com.aincrad.reputation;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.core.region.RegionManager;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.reputation.ReputationService.Rep;
import com.aincrad.reputation.hook.Hooks;
import com.aincrad.reputation.model.CursorRules;
import com.aincrad.reputation.model.CursorRules.Color;
import com.aincrad.reputation.model.CursorRules.Cursor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cor do cursor na prática: quem ataca um jogador verde fica laranja, quem mata fica vermelho;
 * laranja e vermelho não entram nas cidades; um vermelho morto por outro jogador vai preso; e
 * há andares e regiões onde o PvP é desligado.
 */
public final class CursorService implements Listener {

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final ReputationService reputation;
    /** Pares de jogadores que podem se ferir sem mudar de cor (duelos): "a|b" → até quando. */
    private final Map<String, Long> exempt = new ConcurrentHashMap<>();
    /** Teleportes que este plugin mesmo fez (para a prisão e para fora dela). */
    private final Set<UUID> moving = new HashSet<>();
    private Location prisonSpawn;

    public CursorService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, ReputationService reputation) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.reputation = reputation;
        loadPrison();
    }

    private static boolean bypass(Player p) {
        return p.hasPermission("aincrad.reputation.bypass");
    }

    // ------------------------------------------------------------------ PvP

    private static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** Um duelo (ou outro combate combinado): os dois podem se ferir sem mudar de cor até a hora dada. */
    public void exempt(UUID a, UUID b, long until) {
        exempt.put(pair(a, b), until);
    }

    private boolean exempted(Player a, Player b) {
        Long until = exempt.get(pair(a.getUniqueId(), b.getUniqueId()));
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            exempt.remove(pair(a.getUniqueId(), b.getUniqueId()));
            return false;
        }
        return true;
    }

    private static Player attacker(Entity by) {
        if (by instanceof Player p) {
            return p;
        }
        return by instanceof Projectile pr && pr.getShooter() instanceof Player shooter ? shooter : null;
    }

    /** O PvP está desligado neste lugar? (andares e regiões da config) */
    public boolean pvpOff(Location l) {
        if (config.yaml().getIntegerList("pvp.andares_sem_pvp").contains(hooks.floorAt(l))) {
            return true;
        }
        RegionManager regions = AincradCore.get().regions();
        for (String name : config.yaml().getStringList("pvp.regioes_sem_pvp")) {
            Region r = regions.get(name);
            if (r != null && r.contains(l)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvpZone(EntityDamageByEntityEvent event) {
        Player by = attacker(event.getDamager());
        if (by != null && event.getEntity() instanceof Player victim && by != victim && !exempted(by, victim)
                && (pvpOff(victim.getLocation()) || pvpOff(by.getLocation()))) {
            event.setCancelled(true);
            msg.actionBar(by, "pvp.desligado");
        }
    }

    /** Um golpe que passou por todas as proteções (zona segura, duelo, PvP desligado): conta para a cor. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player by = attacker(event.getDamager());
        if (by != null && event.getEntity() instanceof Player victim && event.getFinalDamage() > 0) {
            attacked(by, victim);
        }
    }

    /** Um jogador atacou outro (golpe, flecha ou veneno). */
    public void attacked(Player by, Player victim) {
        if (by == victim || bypass(by) || exempted(by, victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        Cursor before = reputation.rep(by).cursor();
        Cursor after = CursorRules.attacked(before, reputation.color(victim), now, hours("cursor.laranja_horas", 24));
        if (!after.equals(before)) {
            reputation.setCursor(by, after);
        }
    }

    private long hours(String path, double def) {
        return Math.round(config.decimal(path, def, 0, 100_000) * 3_600_000L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer == victim) {
            return;
        }
        long now = System.currentTimeMillis();
        Color victimColor = reputation.color(victim);
        if (!bypass(killer) && !exempted(killer, victim)) {
            Cursor before = reputation.rep(killer).cursor();
            Cursor after = CursorRules.killed(before, victimColor, now, hours("cursor.vermelho_horas", 48),
                    config.decimal("cursor.vermelho_aumento_por_morte", 0.5, 0, 100));
            if (!after.equals(before)) {
                reputation.setCursor(killer, after);
                msg.send(killer, "cursor.matou", Messages.vars("jogador", victim.getName(),
                        "tempo", TimeFormat.duration(after.redUntil() - now, 2), "mortes", after.kills()));
            }
        }
        // Um vermelho derrotado por outro jogador vai preso, por um tempo proporcional às mortes que causou.
        if (CursorRules.goesToJail(victimColor, true) && config.bool("prisao.ativa", true)) {
            long time = CursorRules.jailMs(reputation.rep(victim).cursor().kills(), Math.round(config.decimal("prisao.minutos_por_morte", 30, 0, 100_000) * 60_000L),
                    Math.round(config.decimal("prisao.maximo_horas", 24, 0, 100_000) * 3_600_000L));
            reputation.setJail(victim, now + time);
            msg.send(victim, "prisao.preso", Messages.vars("tempo", TimeFormat.duration(time, 2), "jogador", killer.getName()));
            msg.send(killer, "prisao.prendeu", Messages.vars("jogador", victim.getName()));
        }
    }

    // ------------------------------------------------------------------ cidades

    /** Laranja e vermelho não entram nas cidades: os guardas barram na entrada. Quem já está dentro pode sair. */
    private boolean barred(Player p, Location from, Location to) {
        return config.bool("cidades.barrar", true) && !bypass(p) && CursorRules.barredFromCity(reputation.color(p))
                && hooks.safeZone(to) && !hooks.safeZone(from);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player p = event.getPlayer();
        if (jailed(p)) {
            Region prison = prison();
            if (prison != null && prison.contains(from) && !prison.contains(to)) {
                event.setCancelled(true);
                msg.actionBar(p, "prisao.nao_sai");
            }
            return;
        }
        if (barred(p, from, to)) {
            event.setCancelled(true);
            msg.actionBar(p, "cidades.barrado");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (moving.contains(p.getUniqueId())) {
            return;
        }
        if (jailed(p)) {
            // Preso não se teleporta (comando, cristal, portal, pérola).
            event.setCancelled(true);
            msg.send(p, "prisao.sem_teleporte");
            return;
        }
        if (barred(p, event.getFrom(), event.getTo())) {
            event.setCancelled(true);
            msg.send(p, "cidades.barrado");
        }
    }

    // ------------------------------------------------------------------ prisão

    private File prisonFile() {
        return new File(plugin.getDataFolder(), "prisao.yml");
    }

    private void loadPrison() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(prisonFile());
        World w = y.getString("mundo") == null ? null : Bukkit.getWorld(y.getString("mundo", ""));
        prisonSpawn = w == null ? null : new Location(w, y.getDouble("x"), y.getDouble("y"), y.getDouble("z"), (float) y.getDouble("yaw"), 0);
    }

    private Region prison() {
        return AincradCore.get().regions().get(config.string("prisao.regiao", "prisao"));
    }

    /** A prisão está neste servidor? (precisa da região e do ponto de chegada) */
    public boolean hasPrison() {
        return prisonSpawn != null && prison() != null;
    }

    /**
     * Admin: define a prisão com a seleção da varinha do core; o ponto de chegada dos presos é
     * onde o admin está.
     *
     * @return false se a seleção não está completa ou o admin está fora dela
     */
    public boolean definePrison(Player admin) {
        RegionManager regions = AincradCore.get().regions();
        RegionManager.Selection s = regions.selection(admin);
        if (!s.complete() || !s.a().getWorld().equals(s.b().getWorld())) {
            return false;
        }
        Location a = s.a();
        Location b = s.b();
        Region r = Region.of(config.string("prisao.regiao", "prisao"), a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ(), null);
        if (!r.contains(admin.getLocation())) {
            return false;
        }
        regions.save(r);
        prisonSpawn = admin.getLocation();
        YamlConfiguration y = new YamlConfiguration();
        y.set("mundo", prisonSpawn.getWorld().getName());
        y.set("x", prisonSpawn.getX());
        y.set("y", prisonSpawn.getY());
        y.set("z", prisonSpawn.getZ());
        y.set("yaw", prisonSpawn.getYaw());
        try {
            y.save(prisonFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível gravar prisao.yml: " + e.getMessage());
        }
        return true;
    }

    public boolean jailed(Player p) {
        return reputation.rep(p).jailUntil() > System.currentTimeMillis();
    }

    private void send(Player p, Location to) {
        moving.add(p.getUniqueId());
        try {
            p.teleport(to);
        } finally {
            moving.remove(p.getUniqueId());
        }
    }

    /** Quem está preso renasce na prisão. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (jailed(event.getPlayer()) && hasPrison()) {
            event.setRespawnLocation(prisonSpawn);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> enforce(event.getPlayer()), 20L);
    }

    /** Preso só usa os comandos da lista (config → prisao.comandos_permitidos). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!jailed(p) || p.hasPermission("aincrad.admin")) {
            return;
        }
        String typed = event.getMessage().substring(1).toLowerCase(Locale.ROOT).split(" ")[0];
        for (String ok : config.yaml().getStringList("prisao.comandos_permitidos")) {
            if (typed.equals(ok.toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        event.setCancelled(true);
        msg.send(p, "prisao.sem_comando");
    }

    /** Põe o preso na prisão (se ele está fora) ou solta quem já cumpriu o tempo. */
    private void enforce(Player p) {
        if (!p.isOnline() || p.isDead()) {
            return;
        }
        Rep r = reputation.rep(p);
        long now = System.currentTimeMillis();
        if (r.jailUntil() <= 0) {
            return;
        }
        if (r.jailUntil() > now) {
            Region prison = prison();
            if (hasPrison() && prison != null && !prison.contains(p.getLocation())) {
                send(p, prisonSpawn);
                msg.send(p, "prisao.info", Messages.vars("tempo", TimeFormat.duration(r.jailUntil() - now, 2)));
            }
            return;
        }
        // Cumpriu o tempo: deixa de ser vermelho e volta laranja.
        reputation.setJail(p, 0);
        reputation.setCursor(p, CursorRules.released(r.cursor(), now, hours("cursor.laranja_horas", 24)));
        Region prison = prison();
        if (prison != null && prison.contains(p.getLocation())) {
            Location out = hooks.floorSpawn(1);
            send(p, out != null ? out : p.getWorld().getSpawnLocation());
        }
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1f);
        msg.send(p, "prisao.solto");
    }

    /** Roda a cada 5 s. */
    public void tick() {
        Bukkit.getOnlinePlayers().forEach(this::enforce);
    }
}
