package com.aincrad.floors;

import com.aincrad.core.AincradCore;
import com.aincrad.core.bus.MessageBus;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.floors.api.FloorUnlockEvent;
import com.aincrad.floors.api.GateTeleportEvent;
import com.aincrad.floors.api.PlayerEnterFloorEvent;
import com.aincrad.floors.data.FloorStore;
import com.aincrad.floors.model.Floor;
import com.aincrad.floors.model.FloorRegistry;
import com.aincrad.floors.model.FloorRules;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** As regras dos andares: onde cada um fica, quem entra, liberar, anunciar e teleportar. */
public final class FloorService {

    public static final String BUS_STATE = "floors:state";
    public static final String BUS_UNLOCK = "floors:unlock";
    public static final String BYPASS = "aincrad.floors.bypass";
    private static final String PROFILE_DESTINATION = "destino";
    private static final char SEP = '\u001f';

    /** Resultado de liberar um andar. */
    public enum UnlockResult { OK, ALREADY, CANCELLED, UNKNOWN }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final FloorStore store;
    private final File floorsFile;
    private FloorRegistry registry = FloorRegistry.load(null);
    private FloorRules.Mode mode = FloorRules.Mode.GLOBAL;
    private final Map<UUID, Integer> current = new HashMap<>();

    public FloorService(Plugin plugin, Messages msg, ConfigFile config, FloorStore store) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.store = store;
        this.floorsFile = new File(plugin.getDataFolder(), "floors.yml");
        reload();
    }

    /** Relê floors.yml e o modo. O arquivo não é completado com os andares de exemplo. */
    public void reload() {
        if (!floorsFile.exists()) {
            plugin.saveResource("floors.yml", false);
        }
        registry = FloorRegistry.load(YamlConfiguration.loadConfiguration(floorsFile).getConfigurationSection("andares"));
        for (String e : registry.errors()) {
            plugin.getLogger().warning("floors.yml: " + e);
        }
        mode = FloorRules.Mode.parse(config.string("modo", "global"));
        plugin.getLogger().info(registry.size() + " andar(es) carregado(s), modo " + mode.name().toLowerCase(Locale.ROOT) + ".");
    }

    public Messages messages() {
        return msg;
    }

    public ConfigFile config() {
        return config;
    }

    public FloorStore store() {
        return store;
    }

    public FloorRegistry registry() {
        return registry;
    }

    public FloorRules.Mode mode() {
        return mode;
    }

    public String server() {
        return AincradCore.get().server();
    }

    public Floor floor(int n) {
        return registry.get(n);
    }

    public String name(int n) {
        Floor f = registry.get(n);
        return f == null ? "Andar " + n : f.name();
    }

    public boolean isUnlocked(int n) {
        return registry.get(n) != null && FloorRules.unlocked(n, store.unlocked());
    }

    public int frontline() {
        return FloorRules.frontline(store.unlocked(), registry.size());
    }

    // ------------------------------------------------------------------ onde

    /** O andar deste servidor que contém o lugar (região tem preferência sobre mundo); null = nenhum. */
    public Floor floorAt(Location l) {
        if (l == null || l.getWorld() == null) {
            return null;
        }
        String server = server();
        Floor best = null;
        long volume = Long.MAX_VALUE;
        List<Region> regions = AincradCore.get().regions().at(l);
        if (!regions.isEmpty()) {
            for (Floor f : registry.all()) {
                if (!f.byRegion() || !f.isOn(server)) {
                    continue;
                }
                for (Region r : regions) {
                    if (r.name().equalsIgnoreCase(f.region()) && r.volume() < volume) {
                        best = f;
                        volume = r.volume();
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        String world = l.getWorld().getName();
        for (Floor f : registry.all()) {
            if (!f.byRegion() && f.isOn(server) && world.equalsIgnoreCase(f.world())) {
                return f;
            }
        }
        return null;
    }

    /** O lugar é uma cidade (zona segura) de algum andar? */
    public boolean isSafeZone(Location l) {
        if (l == null || l.getWorld() == null || registry.cities().isEmpty()) {
            return false;
        }
        for (Region r : AincradCore.get().regions().at(l)) {
            if (registry.cities().contains(r.name().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public boolean isCity(String regionName) {
        return registry.cities().contains(regionName.toLowerCase(Locale.ROOT));
    }

    public static Location parseSpawn(String s) {
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

    public static String formatSpawn(Location l) {
        return String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.1f,%.1f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** Define o ponto de chegada e grava em floors.yml. */
    public void setSpawn(int n, Location l) throws IOException {
        Floor f = registry.get(n);
        String spawn = formatSpawn(l);
        registry.put(f.withSpawn(spawn));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(floorsFile);
        yaml.set("andares." + n + ".chegada", spawn);
        yaml.save(floorsFile);
    }

    // ------------------------------------------------------------------ quem entra

    public boolean canEnter(Player p, int floor) {
        FloorStore.PlayerFloors pf = store.player(p.getUniqueId());
        return FloorRules.canEnter(mode, floor, store.unlocked(), pf.granted, p.hasPermission(BYPASS));
    }

    public boolean gateOffers(Player p, int floor) {
        FloorStore.PlayerFloors pf = store.player(p.getUniqueId());
        return FloorRules.gateOffers(mode, floor, store.unlocked(), pf.granted, pf.visited, p.hasPermission(BYPASS));
    }

    /** Mensagem certa para quem foi barrado. */
    public void deny(Player p, int floor) {
        msg.actionBar(p, isUnlocked(floor) ? "andar.sem_acesso" : "andar.bloqueado", Messages.vars("andar", name(floor)));
    }

    /** Andar em que o jogador está (0 = nenhum). */
    public int currentFloor(Player p) {
        return current.getOrDefault(p.getUniqueId(), 0);
    }

    /** Atualiza o andar do jogador; ao mudar, grava a visita e dispara {@link PlayerEnterFloorEvent}. */
    public void track(Player p, Location l) {
        Floor f = floorAt(l);
        int to = f == null ? 0 : f.number();
        Integer before = current.put(p.getUniqueId(), to);
        int from = before == null ? 0 : before;
        if (to == from || to == 0) {
            return;
        }
        boolean first = store.visit(p.getUniqueId(), to);
        plugin.getServer().getPluginManager().callEvent(new PlayerEnterFloorEvent(p, from, to, first));
        if (before != null) {
            msg.actionBar(p, "andar.chegou", Messages.vars("andar", f.name()));
        }
    }

    public void untrack(UUID id) {
        current.remove(id);
    }

    // ------------------------------------------------------------------ liberar

    /**
     * Libera o andar: evento (cancelável), banco, acesso para os participantes, aviso à rede e
     * anúncio em todos os servidores.
     */
    public UnlockResult unlock(int n, Collection<UUID> participants) {
        if (registry.get(n) == null) {
            return UnlockResult.UNKNOWN;
        }
        if (isUnlocked(n)) {
            return UnlockResult.ALREADY;
        }
        FloorUnlockEvent ev = new FloorUnlockEvent(n, participants);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return UnlockResult.CANCELLED;
        }
        List<String> names = new ArrayList<>();
        for (UUID id : participants) {
            store.grant(id, n, true);
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (name != null) {
                names.add(name);
            }
        }
        String joined = String.join(", ", names);
        String guild = sharedGuild(participants);
        store.setUnlocked(n, System.currentTimeMillis(), joined).whenComplete((r, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível gravar a liberação do andar " + n, err);
                return;
            }
            AincradCore core = AincradCore.get();
            core.runSync(() -> {
                core.publish(BUS_STATE, String.valueOf(n));
                core.publish(BUS_UNLOCK, n + String.valueOf(SEP) + joined + SEP + (guild == null ? "" : guild));
            });
        });
        return UnlockResult.OK;
    }

    /** Volta a bloquear (testes e reset). O andar 1 nunca bloqueia. */
    public boolean lock(int n) {
        if (n <= 1 || registry.get(n) == null || !isUnlocked(n)) {
            return false;
        }
        store.setLocked(n).thenRun(() -> AincradCore.get().runSync(() -> AincradCore.get().publish(BUS_STATE, String.valueOf(n))));
        return true;
    }

    /** Outro servidor mudou o estado ou os Gates: relê. */
    public void onState(MessageBus.Message m) {
        if (!server().equals(m.origin())) {
            store.reloadAsync();
        }
    }

    /** Anúncio da liberação (chega em todos os servidores, inclusive neste). */
    public void onUnlockAnnounce(MessageBus.Message m) {
        String[] p = m.payload().split(String.valueOf(SEP), -1);
        int n;
        try {
            n = Integer.parseInt(p[0]);
        } catch (NumberFormatException e) {
            return;
        }
        String names = p.length > 1 ? p[1] : "";
        String guild = p.length > 2 ? p[2] : "";
        var vars = Messages.vars("n", n, "nome", name(n), "jogadores", names, "guilda", guild);
        String som = config.string("anuncio.som", "");
        com.aincrad.floors.api.FloorAnnounceEvent ev = new com.aincrad.floors.api.FloorAnnounceEvent(n, name(n), names, guild);
        plugin.getServer().getPluginManager().callEvent(ev);
        for (Player pl : plugin.getServer().getOnlinePlayers()) {
            if (config.bool("anuncio.titulo", true) && !ev.isTitleHandled()) {
                msg.title(pl, "anuncio.titulo", Duration.ofSeconds(4), vars);
            }
            if (config.bool("anuncio.chat", true)) {
                msg.sendRaw(pl, !guild.isEmpty() ? "anuncio.chat_guilda" : !names.isEmpty() ? "anuncio.chat_por" : "anuncio.chat", vars);
            }
            if (!som.isBlank()) {
                try {
                    pl.playSound(Sound.sound(Key.key(som), Sound.Source.MASTER, 1f, 1f));
                } catch (RuntimeException e) {
                    // nome de som inválido na config: fica sem som
                }
            }
        }
    }

    /** Nome da guilda se todos os participantes são da mesma (AincradSocial instalado). */
    private String sharedGuild(Collection<UUID> participants) {
        if (participants.isEmpty()) {
            return null;
        }
        try {
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (!c.getName().equals("com.aincrad.social.api.AincradSocialAPI")) {
                    continue;
                }
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                if (r == null) {
                    return null;
                }
                String shared = null;
                for (UUID id : participants) {
                    Object g = c.getMethod("getGuild", UUID.class).invoke(r.getProvider(), id);
                    Object name = g == null ? null : g.getClass().getMethod("name").invoke(g);
                    if (name == null || (shared != null && !shared.equals(name.toString()))) {
                        return null;
                    }
                    shared = name.toString();
                }
                return shared;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Social com outra API: anuncia sem a guilda
        }
        return null;
    }

    // ------------------------------------------------------------------ teleporte

    /**
     * Leva o jogador ao andar (Gate ou /andar): confere o acesso, dispara o
     * {@link GateTeleportEvent} e teleporta aqui ou troca de servidor.
     *
     * @return true se o teleporte começou
     */
    public boolean teleport(Player p, int n) {
        Floor f = registry.get(n);
        if (f == null) {
            msg.send(p, "andar.desconhecido", Messages.vars("n", n));
            return false;
        }
        if (!canEnter(p, n)) {
            msg.send(p, isUnlocked(n) ? "andar.sem_acesso" : "andar.bloqueado", Messages.vars("andar", f.name()));
            return false;
        }
        boolean remote = !f.isOn(server());
        GateTeleportEvent ev = new GateTeleportEvent(p, n, remote);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return false;
        }
        if (remote) {
            // O destino viaja no perfil do jogador; o outro servidor teleporta quando ele chegar.
            AincradCore.get().profile(p).set(plugin, PROFILE_DESTINATION, n);
            msg.send(p, "andar.outro_servidor", Messages.vars("andar", f.name()));
            return connect(p, f.server());
        }
        Location to = parseSpawn(f.spawn());
        if (to == null) {
            msg.send(p, "andar.sem_chegada", Messages.vars("andar", f.name(), "n", n));
            return false;
        }
        p.teleportAsync(to);
        return true;
    }

    private boolean connect(Player p, String server) {
        String channel = config.string("rede.canal", "BungeeCord");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, channel)) {
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
            }
            p.sendPluginMessage(plugin, channel, bytes.toByteArray());
            return true;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível mandar " + p.getName() + " para o servidor " + server, e);
            msg.send(p, "andar.rede_falhou");
            return false;
        }
    }

    /** Ao entrar: se veio de um Gate de outro servidor, vai para a chegada do andar pedido. */
    public void arrive(Player p) {
        var profile = AincradCore.get().profile(p);
        int n = profile.getInt(plugin, PROFILE_DESTINATION, 0);
        if (n <= 0) {
            return;
        }
        profile.remove(plugin, PROFILE_DESTINATION);
        Floor f = registry.get(n);
        Location to = f == null || !f.isOn(server()) || !canEnter(p, n) ? null : parseSpawn(f.spawn());
        if (to != null) {
            p.teleportAsync(to);
        }
    }

    /** Chegada do andar 1 (para tirar alguém de um andar bloqueado), se for neste servidor. */
    public Location fallbackSpawn() {
        Floor f = registry.get(1);
        return f == null || !f.isOn(server()) ? null : parseSpawn(f.spawn());
    }
}
