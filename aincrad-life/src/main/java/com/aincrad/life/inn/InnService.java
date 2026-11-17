package com.aincrad.life.inn;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.life.hook.Hooks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estalagens: aluguel de quartos em Col, porta e baú só do hóspede e dos convidados, descanso na
 * cama (bônus "Descansado") e ponto de retorno ao morrer — inclusive em outro servidor da rede.
 * No fim do aluguel o baú vai para a caixa de retirada do AincradEconomy.
 */
public final class InnService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_inn_rooms (inn VARCHAR(64) NOT NULL, room VARCHAR(64) NOT NULL, "
                    + "uuid VARCHAR(36) NOT NULL, name VARCHAR(16) NOT NULL, until_time BIGINT NOT NULL, guests TEXT, PRIMARY KEY (inn, room));"
                    + "CREATE TABLE IF NOT EXISTS life_links (ref VARCHAR(160) NOT NULL PRIMARY KEY, kind VARCHAR(16) NOT NULL, target VARCHAR(64) NOT NULL)");
    public static final String BUS = "life:inns";
    private static final String PROFILE_RESTED = "descansado_ate";
    private static final String PROFILE_RETURN = "retorno";
    private static final String ADMIN = "aincrad.admin";

    private final Plugin plugin;
    private final Messages msg;
    private final Hooks hooks;
    private final Database db;
    private final File file;
    private final NamespacedKey restedKey;
    private ConfigFile config;
    private Map<String, Inn> inns = Map.of();
    /** Região do quarto → {estalagem, quarto}. */
    private final Map<String, String[]> roomByRegion = new HashMap<>();
    /** "estalagem/quarto" → aluguel. */
    private volatile Map<String, Inn.Rental> rentals = new HashMap<>();
    private volatile Map<String, String> counters = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final Map<UUID, Integer> resting = new HashMap<>();

    public InnService(Plugin plugin, Messages msg, Hooks hooks, Database db, ConfigFile config) {
        this.plugin = plugin;
        this.msg = msg;
        this.hooks = hooks;
        this.db = db;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "inns.yml");
        this.restedKey = new NamespacedKey(plugin, "descansado");
        loadInns();
    }

    public void reload(ConfigFile c) {
        this.config = c;
        loadInns();
    }

    private void loadInns() {
        if (!file.exists()) {
            plugin.saveResource("inns.yml", false);
        }
        Map<String, Inn> out = new LinkedHashMap<>();
        roomByRegion.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("estalagens");
        List<String> errors = new ArrayList<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Inn inn = Inn.parse(id, s, errors);
                out.put(inn.id(), inn);
                if (inn.isOn(server())) {
                    inn.rooms().values().forEach(r -> roomByRegion.put(r.region(), new String[]{inn.id(), r.id()}));
                }
            }
        }
        errors.forEach(e -> plugin.getLogger().warning("inns.yml: " + e));
        inns = out;
    }

    private String server() {
        return AincradCore.get().server();
    }

    private static String key(String inn, String room) {
        return inn + "/" + room;
    }

    public Map<String, Inn> inns() {
        return inns;
    }

    // ------------------------------------------------------------------ banco

    private record State(Map<String, Inn.Rental> rentals, Map<String, String> counters) {
    }

    private static State read(java.sql.Connection c) throws SQLException {
        Map<String, Inn.Rental> r = new HashMap<>();
        for (Inn.Rental x : Database.query(c, "SELECT inn, room, uuid, name, until_time, guests FROM life_inn_rooms",
                rs -> new Inn.Rental(rs.getString(1), rs.getString(2), UUID.fromString(rs.getString(3)), rs.getString(4), rs.getLong(5), rs.getString(6)))) {
            r.put(key(x.inn, x.room), x);
        }
        Map<String, String> l = new HashMap<>();
        for (String[] row : Database.query(c, "SELECT ref, target FROM life_links WHERE kind = 'estalagem'",
                rs -> new String[]{rs.getString(1), rs.getString(2)})) {
            l.put(row[0], row[1]);
        }
        return new State(r, l);
    }

    public void loadBlocking() throws SQLException {
        State s = db.sync(InnService::read);
        rentals = s.rentals();
        counters = s.counters();
    }

    public void reloadAsync() {
        db.async(InnService::read).thenAccept(s -> {
            rentals = s.rentals();
            counters = s.counters();
        });
    }

    private void changed() {
        AincradCore core = AincradCore.get();
        core.runSync(() -> core.publish(BUS, "mudou"));
    }

    private void save(Inn.Rental r) {
        db.update(db.upsert("life_inn_rooms", List.of("inn", "room"), List.of("uuid", "name", "until_time", "guests")),
                r.inn, r.room, r.tenant.toString(), r.tenantName, r.until, r.guestsText()).thenRun(this::changed);
    }

    private void delete(Inn.Rental r) {
        Map<String, Inn.Rental> m = new HashMap<>(rentals);
        m.remove(key(r.inn, r.room));
        rentals = m;
        db.update("DELETE FROM life_inn_rooms WHERE inn = ? AND room = ?", r.inn, r.room).thenRun(this::changed);
    }

    /** O quarto alugado pelo jogador (ativo), ou null. */
    public Inn.Rental of(UUID player) {
        long now = System.currentTimeMillis();
        for (Inn.Rental r : rentals.values()) {
            if (r.tenant.equals(player) && r.active(now)) {
                return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ balcão

    public String blockRef(Block b) {
        return server() + ":" + b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public static String entityRef(Entity e) {
        return "e:" + e.getUniqueId();
    }

    public void link(String ref, String inn) {
        Map<String, String> m = new HashMap<>(counters);
        m.put(ref, inn);
        counters = m;
        db.update(db.upsert("life_links", List.of("ref"), List.of("kind", "target")), ref, "estalagem", inn).thenRun(this::changed);
    }

    public boolean unlink(String ref) {
        if (!counters.containsKey(ref)) {
            return false;
        }
        Map<String, String> m = new HashMap<>(counters);
        m.remove(ref);
        counters = m;
        db.update("DELETE FROM life_links WHERE ref = ?", ref).thenRun(this::changed);
        return true;
    }

    private Material fallbackItem() {
        Material m = Material.matchMaterial(config.string("sem_economia.item", "GOLD_INGOT"));
        return m == null ? Material.GOLD_INGOT : m;
    }

    private long colPerItem() {
        return config.integer("sem_economia.col_por_item", 100, 1, Integer.MAX_VALUE);
    }

    private String price(long col) {
        return hooks.price(col, fallbackItem(), colPerItem());
    }

    private String left(Inn.Rental r) {
        return TimeFormat.duration(r.until - System.currentTimeMillis());
    }

    /** O menu do balcão: os quartos livres, ou o quarto do jogador com a opção de renovar. */
    public void openCounter(Player p, Inn inn) {
        Inn.Rental mine = of(p.getUniqueId());
        Menu menu = Menu.of(msg.get("estalagem.titulo", Messages.vars("estalagem", inn.name()))).rows(4);
        long now = System.currentTimeMillis();
        if (mine != null) {
            Inn at = inns.get(mine.inn);
            menu.item(Menu.Button.of(ItemBuilder.of(Material.RED_BED).build(), msg.get("estalagem.seu_quarto",
                    Messages.vars("quarto", mine.room, "estalagem", at == null ? mine.inn : at.name(), "tempo", left(mine))), null));
            if (mine.inn.equals(inn.id())) {
                for (int days : new int[]{1, 3, 7}) {
                    menu.item(Menu.Button.of(ItemBuilder.of(Material.CLOCK).amount(days).build(),
                            msg.get("estalagem.renovar", Messages.vars("dias", days, "preco", price(inn.cost(days)))),
                            pl -> rent(pl, inn, mine.room, days)).closing());
                }
            }
        } else {
            boolean any = false;
            for (Inn.Room room : inn.rooms().values()) {
                Inn.Rental r = rentals.get(key(inn.id(), room.id()));
                if (r != null && r.active(now)) {
                    continue; // quarto ocupado não aparece
                }
                any = true;
                menu.item(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("estalagem.quarto_livre",
                        Messages.vars("quarto", room.id(), "preco", price(inn.price()))), pl -> chooseDays(pl, inn, room.id())));
            }
            if (!any) {
                menu.item(Menu.Button.of(ItemBuilder.of(Material.BARRIER).build(), msg.get("estalagem.lotada"), null));
            }
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("menu.fechar"), pl -> { }).closing());
        AincradCore.get().menus().open(p, menu);
    }

    private void chooseDays(Player p, Inn inn, String room) {
        Menu menu = Menu.of(msg.get("estalagem.dias_titulo", Messages.vars("quarto", room))).rows(3);
        for (int d = 1; d <= Inn.MAX_DAYS; d++) {
            int days = d;
            menu.item(Menu.Button.of(ItemBuilder.of(Material.CLOCK).amount(days).build(),
                    msg.get("estalagem.dias", Messages.vars("dias", days, "preco", price(inn.cost(days)))), pl -> rent(pl, inn, room, days)).closing());
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.ARROW).build(), msg.get("menu.voltar"), pl -> openCounter(pl, inn)));
        AincradCore.get().menus().open(p, menu);
    }

    /** Aluga o quarto (ou renova o que já é do jogador). Um quarto por jogador. */
    public void rent(Player p, Inn inn, String room, int days) {
        long now = System.currentTimeMillis();
        Inn.Rental mine = of(p.getUniqueId());
        Inn.Rental current = rentals.get(key(inn.id(), room));
        boolean renew = mine != null && mine.inn.equals(inn.id()) && mine.room.equals(room);
        if (mine != null && !renew) {
            msg.send(p, "estalagem.ja_tem");
            return;
        }
        if (!renew && current != null && current.active(now)) {
            msg.send(p, "estalagem.ocupado");
            return;
        }
        if (renew && mine.until - now >= (Inn.MAX_DAYS - 1) * Inn.DAY) {
            msg.send(p, "estalagem.maximo", Messages.vars("dias", Inn.MAX_DAYS));
            return;
        }
        long cost = inn.cost(days);
        if (!hooks.charge(p, cost, fallbackItem(), colPerItem(), "life:estalagem")) {
            msg.send(p, "estalagem.sem_col", Messages.vars("preco", price(cost)));
            return;
        }
        Inn.Rental r = renew ? mine : new Inn.Rental(inn.id(), room, p.getUniqueId(), p.getName(), 0, "");
        r.until = Inn.extend(r.until, now, days);
        Map<String, Inn.Rental> m = new HashMap<>(rentals);
        m.put(key(inn.id(), room), r);
        rentals = m;
        warned.remove(key(inn.id(), room));
        save(r);
        msg.send(p, renew ? "estalagem.renovou" : "estalagem.alugou", Messages.vars("quarto", room, "estalagem", inn.name(),
                "tempo", left(r), "preco", price(cost)));
    }

    public void invite(Player owner, OfflinePlayer guest, boolean add) {
        Inn.Rental r = of(owner.getUniqueId());
        if (r == null) {
            msg.send(owner, "estalagem.sem_quarto");
            return;
        }
        boolean changed = add ? !guest.getUniqueId().equals(owner.getUniqueId()) && r.guests.add(guest.getUniqueId())
                : r.guests.remove(guest.getUniqueId());
        if (!changed) {
            msg.send(owner, add ? "estalagem.ja_convidado" : "estalagem.nao_convidado");
            return;
        }
        save(r);
        msg.send(owner, add ? "estalagem.convidou" : "estalagem.removeu", Messages.vars("jogador", String.valueOf(guest.getName())));
    }

    /** Devolve o quarto agora (o baú é esvaziado para a caixa de retirada). */
    public void checkout(Inn.Rental r) {
        r.until = 0;
        end(r);
    }

    // ------------------------------------------------------------------ privacidade

    private Inn.Rental rentalAt(Location l) {
        if (roomByRegion.isEmpty()) {
            return null;
        }
        for (Region region : AincradCore.get().regions().at(l)) {
            String[] ir = roomByRegion.get(region.name().toLowerCase(Locale.ROOT));
            if (ir != null) {
                Inn.Rental r = rentals.get(key(ir[0], ir[1]));
                // Quarto vago: um aluguel "vazio" para o chamador saber que é um quarto.
                return r != null ? r : new Inn.Rental(ir[0], ir[1], new UUID(0, 0), "", 0, "");
            }
        }
        return null;
    }

    /** Porta, alçapão, baú e cama de um quarto: só o hóspede e os convidados. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Block b = event.getClickedBlock();
        if (b == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            String inn = counters.get(blockRef(b));
            if (inn != null && inns.containsKey(inn) && !p.isSneaking()) {
                event.setCancelled(true);
                openCounter(p, inns.get(inn));
                return;
            }
            boolean guarded = b.getBlockData() instanceof Openable || b.getBlockData() instanceof Bed || b.getState(false) instanceof Container;
            if (!guarded) {
                return;
            }
            Inn.Rental r = rentalAt(b.getLocation());
            if (r != null && !p.hasPermission(ADMIN) && !r.canUse(p.getUniqueId(), System.currentTimeMillis())) {
                event.setCancelled(true);
                msg.actionBar(p, "estalagem.trancado");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCounterNpc(PlayerInteractEntityEvent event) {
        String inn = event.getHand() == EquipmentSlot.HAND ? counters.get(entityRef(event.getRightClicked())) : null;
        if (inn != null && inns.containsKey(inn)) {
            event.setCancelled(true);
            openCounter(event.getPlayer(), inns.get(inn));
        }
    }

    // ------------------------------------------------------------------ descanso

    /** Deitar na cama do próprio quarto funciona a qualquer hora e começa a contagem do descanso. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBed(PlayerBedEnterEvent event) {
        Player p = event.getPlayer();
        Inn.Rental r = rentalAt(event.getBed().getLocation());
        if (r == null) {
            return;
        }
        if (!r.tenant.equals(p.getUniqueId()) || !r.active(System.currentTimeMillis())) {
            event.setCancelled(true);
            return;
        }
        event.setUseBed(Event.Result.ALLOW);
        resting.put(p.getUniqueId(), 0);
        msg.actionBar(p, "estalagem.descansando", Messages.vars("segundos", config.integer("estalagens.descanso.segundos", 10, 1, 600)));
    }

    public boolean rested(Player p) {
        return AincradCore.get().profile(p).getLong(plugin, PROFILE_RESTED, 0) > System.currentTimeMillis();
    }

    /** Fração de XP a mais para quem está descansado (0 se não está). */
    public double xpBonus(Player p) {
        return rested(p) ? config.decimal("estalagens.descanso.xp_pct", 5, 0, 1000) / 100.0 : 0;
    }

    private void applyRested(Player p, boolean on) {
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp == null) {
            return;
        }
        hp.removeModifier(restedKey);
        if (on) {
            hp.addTransientModifier(new AttributeModifier(restedKey, config.decimal("estalagens.descanso.vida_pct", 10, 0, 1000) / 100.0,
                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        }
    }

    /** Roda uma vez por segundo: conta o descanso de quem está deitado e tira o bônus vencido. */
    public void second() {
        int need = config.integer("estalagens.descanso.segundos", 10, 1, 600);
        for (UUID id : new ArrayList<>(resting.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isSleeping()) {
                resting.remove(id);
                continue;
            }
            int n = resting.merge(id, 1, Integer::sum);
            if (n >= need) {
                resting.remove(id);
                long minutes = config.integer("estalagens.descanso.minutos", 30, 1, 10_000);
                AincradCore.get().profile(p).set(plugin, PROFILE_RESTED, System.currentTimeMillis() + minutes * 60_000L);
                applyRested(p, true);
                p.wakeup(false);
                msg.send(p, "estalagem.descansado", Messages.vars("minutos", minutes));
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null && hp.getModifier(restedKey) != null && !rested(p)) {
                applyRested(p, false);
                msg.send(p, "estalagem.descanso_acabou");
            }
        }
    }

    // ------------------------------------------------------------------ ponto de retorno

    /** Onde o hóspede aparece no quarto: a cama marcada, ou o meio da região. */
    private Location roomSpot(Inn.Rental r) {
        Inn inn = inns.get(r.inn);
        Inn.Room room = inn == null ? null : inn.rooms().get(r.room);
        if (room == null || !inn.isOn(server())) {
            return null;
        }
        Location bed = parse(room.bed());
        if (bed != null) {
            return bed;
        }
        Region region = AincradCore.get().regions().get(room.region());
        World w = region == null ? null : Bukkit.getWorld(region.world());
        return w == null ? null : new Location(w, (region.minX() + region.maxX() + 1) / 2.0, region.minY(), (region.minZ() + region.maxZ() + 1) / 2.0);
    }

    private static Location parse(String s) {
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

    /** Ao morrer, o jogador volta ao quarto. Sem quarto, à cidade do andar 1. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Inn.Rental r = of(p.getUniqueId());
        if (r != null) {
            Location spot = roomSpot(r);
            if (spot != null) {
                event.setRespawnLocation(spot);
                return;
            }
            // O quarto fica em outro servidor: o destino viaja no perfil e o jogador troca de servidor.
            Inn inn = inns.get(r.inn);
            if (inn != null && !inn.isOn(server())) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) {
                        AincradCore.get().profile(p).set(plugin, PROFILE_RETURN, r.inn + "/" + r.room);
                        connect(p, inn.server());
                    }
                });
            }
            return;
        }
        if (config.bool("estalagens.sem_quarto_volta_ao_andar_1", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    hooks.toFirstFloor(p);
                }
            });
        }
    }

    private void connect(Player p, String server) {
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
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Não foi possível mandar " + p.getName() + " para o servidor " + server + ": " + e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // Um tick depois: o perfil do core já carregou.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (rested(p)) {
                applyRested(p, true);
            }
            var profile = AincradCore.get().profile(p);
            String back = profile.get(plugin, PROFILE_RETURN, "");
            if (!back.isEmpty()) {
                profile.remove(plugin, PROFILE_RETURN);
                Inn.Rental r = rentals.get(back);
                Location spot = r == null || !r.tenant.equals(p.getUniqueId()) ? null : roomSpot(r);
                if (spot != null) {
                    p.teleportAsync(spot);
                }
            }
        });
    }

    // ------------------------------------------------------------------ fim do aluguel

    /** Roda uma vez por minuto: avisa uma hora antes e encerra os aluguéis vencidos deste servidor. */
    public void minute() {
        long now = System.currentTimeMillis();
        for (Inn.Rental r : new ArrayList<>(rentals.values())) {
            String k = key(r.inn, r.room);
            Player tenant = Bukkit.getPlayer(r.tenant);
            if (r.endingSoon(now) && tenant != null && warned.add(k)) {
                msg.send(tenant, "estalagem.acaba_logo", Messages.vars("tempo", left(r), "quarto", r.room));
            }
            Inn inn = inns.get(r.inn);
            if (!r.active(now) && inn != null && inn.isOn(server())) {
                end(r);
            }
        }
    }

    /** Encerra o aluguel: esvazia os baús do quarto para a caixa de retirada e libera o quarto. */
    private void end(Inn.Rental r) {
        Inn inn = inns.get(r.inn);
        Inn.Room room = inn == null ? null : inn.rooms().get(r.room);
        int moved = 0;
        if (room != null && inn.isOn(server())) {
            Region region = AincradCore.get().regions().get(room.region());
            World w = region == null ? null : Bukkit.getWorld(region.world());
            if (w != null && region.volume() <= 40_000) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    for (int y = region.minY(); y <= region.maxY(); y++) {
                        for (int z = region.minZ(); z <= region.maxZ(); z++) {
                            if (w.getBlockAt(x, y, z).getState(false) instanceof Container c) {
                                for (ItemStack it : c.getInventory().getContents()) {
                                    // Sem a economia não há caixa de retirada: os itens ficam no baú.
                                    if (it != null && !it.getType().isAir() && hooks.mail(r.tenant, it, "estalagem")) {
                                        c.getInventory().remove(it);
                                        moved++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        delete(r);
        warned.remove(key(r.inn, r.room));
        Player tenant = Bukkit.getPlayer(r.tenant);
        if (tenant != null) {
            msg.send(tenant, moved > 0 ? "estalagem.acabou_itens" : "estalagem.acabou", Messages.vars("quarto", r.room, "itens", moved));
        }
    }

    /** Grava em inns.yml o ponto onde o hóspede aparece no quarto. */
    public boolean setBed(String innId, String roomId, Location l) throws IOException {
        Inn inn = inns.get(innId.toLowerCase(Locale.ROOT));
        if (inn == null || !inn.rooms().containsKey(roomId.toLowerCase(Locale.ROOT))) {
            return false;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        y.set("estalagens." + inn.id() + ".quartos." + roomId.toLowerCase(Locale.ROOT) + ".cama",
                String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.1f,%.1f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        y.save(file);
        loadInns();
        return true;
    }

    /** Texto do quarto do jogador para o /estalagem. */
    public Component describe(Player p) {
        Inn.Rental r = of(p.getUniqueId());
        if (r == null) {
            return msg.get("estalagem.sem_quarto");
        }
        Inn inn = inns.get(r.inn);
        List<String> names = new ArrayList<>();
        r.guests.forEach(g -> names.add(String.valueOf(Bukkit.getOfflinePlayer(g).getName())));
        return msg.get("estalagem.info", Messages.vars("quarto", r.room, "estalagem", inn == null ? r.inn : inn.name(), "tempo", left(r),
                "convidados", names.isEmpty() ? "—" : String.join(", ", names)));
    }
}
