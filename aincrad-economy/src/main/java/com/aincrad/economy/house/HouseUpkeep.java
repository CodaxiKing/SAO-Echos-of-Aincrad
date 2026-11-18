package com.aincrad.economy.house;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.economy.EconomyService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * O que mantém as casas em ordem: a placa flutuante na frente de cada uma, a casa que volta ao
 * estado original quando o dono sai, e a retomada da casa de quem sumiu do servidor.
 */
public final class HouseUpkeep implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS eco_house_signs (name VARCHAR(64) NOT NULL PRIMARY KEY, server VARCHAR(64) NOT NULL, "
                    + "world VARCHAR(64) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL)",
            "CREATE TABLE IF NOT EXISTS eco_house_seen (uuid VARCHAR(36) NOT NULL PRIMARY KEY, seen BIGINT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS eco_house_reset (name VARCHAR(64) NOT NULL PRIMARY KEY, since BIGINT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS eco_house_tax (name VARCHAR(64) NOT NULL PRIMARY KEY, owner VARCHAR(36) NOT NULL, due BIGINT NOT NULL)");

    private static final String TAG = "aincrad_casa_placa";

    /** Onde fica a placa de uma casa. */
    private record SignSpot(String server, String world, double x, double y, double z) {
    }

    private final Plugin plugin;
    private final EconomyService eco;
    private final Messages msg;
    private final HouseService houses;
    private final HouseAuctionService auctions;
    private final Database db;
    private final File dir;
    private final Map<String, SignSpot> signs = new ConcurrentHashMap<>();
    private final Map<String, UUID> displays = new HashMap<>();
    private final Map<UUID, Long> seen = new ConcurrentHashMap<>();
    /** casa → dono e vencimento do imposto. */
    private final Map<String, TaxRow> taxes = new ConcurrentHashMap<>();

    private record TaxRow(String owner, long due) {
    }

    public HouseUpkeep(Plugin plugin, EconomyService eco, HouseService houses, HouseAuctionService auctions, Database db) {
        this.plugin = plugin;
        this.eco = eco;
        this.msg = eco.messages();
        this.houses = houses;
        this.auctions = auctions;
        this.db = db;
        this.dir = new File(plugin.getDataFolder(), "casas");
    }

    public void loadBlocking() throws SQLException {
        signs.putAll(db.sync(c -> {
            Map<String, SignSpot> out = new HashMap<>();
            Database.query(c, "SELECT name, server, world, x, y, z FROM eco_house_signs", rs -> out.put(rs.getString(1),
                    new SignSpot(rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5), rs.getDouble(6))));
            return out;
        }));
        seen.putAll(db.sync(c -> {
            Map<UUID, Long> out = new HashMap<>();
            Database.query(c, "SELECT uuid, seen FROM eco_house_seen", rs -> out.put(UUID.fromString(rs.getString(1)), rs.getLong(2)));
            return out;
        }));
    }

    public void shutdown() {
        for (UUID id : displays.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        displays.clear();
    }

    // ------------------------------------------------------------------ placa na frente da casa

    /** Admin: põe a placa da casa onde ele está. */
    public void setSign(Player admin, House h) {
        Location l = admin.getLocation().add(0, 1.6, 0);
        SignSpot spot = new SignSpot(AincradCore.get().server(), l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
        signs.put(h.name(), spot);
        db.update(db.upsert("eco_house_signs", List.of("name"), List.of("server", "world", "x", "y", "z")),
                h.name(), spot.server(), spot.world(), spot.x(), spot.y(), spot.z());
        removeDisplay(h.name());
        tickSigns();
        msg.send(admin, "placa.posta", Messages.vars("casa", h.name()));
    }

    public boolean removeSign(String house) {
        removeDisplay(house);
        if (signs.remove(house) == null) {
            return false;
        }
        db.update("DELETE FROM eco_house_signs WHERE name = ?", house);
        return true;
    }

    private void removeDisplay(String house) {
        UUID id = displays.remove(house);
        Entity e = id == null ? null : Bukkit.getEntity(id);
        if (e != null) {
            e.remove();
        }
    }

    private Component text(House h) {
        HouseRules.Sign kind = HouseRules.sign(h.forSale(), auctions != null && auctions.auctioned(h.name()));
        return switch (kind) {
            case DONO -> msg.get("placa.dono", Messages.vars("casa", h.name(), "dono", String.valueOf(h.ownerName())));
            case VENDA -> msg.get("placa.venda", Messages.vars("casa", h.name(), "andar", h.floor()), eco.col(h.price()));
            case LEILAO -> {
                HouseAuctionService.Auction a = auctions.of(h.name());
                long value = a == null ? 0 : Math.max(a.bid(), a.minBid());
                yield msg.get("placa.leilao", Messages.vars("casa", h.name(), "andar", h.floor()), eco.col(value));
            }
        };
    }

    /** A cada 5 s: cria a placa que falta (o texto não é salvo com o mundo) e atualiza o que ela diz. */
    public void tickSigns() {
        String here = AincradCore.get().server();
        for (Map.Entry<String, SignSpot> e : new ArrayList<>(signs.entrySet())) {
            String name = e.getKey();
            SignSpot s = e.getValue();
            if (!here.equals(s.server())) {
                continue;
            }
            House h = houses.byName(name);
            if (h == null) {
                removeSign(name);
                continue;
            }
            World w = Bukkit.getWorld(s.world());
            if (w == null || !w.isChunkLoaded((int) Math.floor(s.x()) >> 4, (int) Math.floor(s.z()) >> 4)) {
                displays.remove(name);
                continue;
            }
            UUID id = displays.get(name);
            Entity old = id == null ? null : Bukkit.getEntity(id);
            TextDisplay d;
            if (old instanceof TextDisplay t && t.isValid()) {
                d = t;
            } else {
                d = w.spawn(new Location(w, s.x(), s.y(), s.z()), TextDisplay.class, t -> {
                    t.setPersistent(false);
                    t.setBillboard(Display.Billboard.CENTER);
                    t.addScoreboardTag(TAG);
                });
                displays.put(name, d.getUniqueId());
            }
            d.text(text(h));
        }
    }

    // ------------------------------------------------------------------ dono que sumiu

    private void touch(UUID id) {
        long now = System.currentTimeMillis();
        seen.put(id, now);
        db.update(db.upsert("eco_house_seen", List.of("uuid"), List.of("seen")), id.toString(), now);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (houses.ownedBy(p.getUniqueId()).isEmpty()) {
            return;
        }
        touch(p.getUniqueId());
        // O imposto vencido é cobrado (ou avisado) logo depois de o dono entrar.
        plugin.getServer().getScheduler().runTaskLater(plugin, this::tickTax, 100L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!houses.ownedBy(event.getPlayer().getUniqueId()).isEmpty()) {
            touch(event.getPlayer().getUniqueId());
        }
    }

    /** Dias que faltam para a casa do jogador ser retomada, ou -1 se a regra está desligada. */
    public long daysLeft(UUID owner) {
        int days = eco.config().integer("casas.inatividade_dias", 0, 0, 3650);
        Long s = seen.get(owner);
        return days <= 0 || s == null ? -1 : HouseRules.daysLeft(s, System.currentTimeMillis(), days);
    }

    /** A cada 10 min: a casa de quem não entra há {@code casas.inatividade_dias} volta ao mercado. */
    public void tickInactive() {
        int days = eco.config().integer("casas.inatividade_dias", 0, 0, 3650);
        if (days <= 0) {
            return;
        }
        // Lê do banco antes: o dono pode ter entrado em outro servidor da rede.
        db.query("SELECT uuid, seen FROM eco_house_seen", rs -> Map.entry(rs.getString(1), rs.getLong(2)))
                .thenAccept(rows -> AincradCore.get().runSync(() -> {
                    rows.forEach(r -> seen.merge(UUID.fromString(r.getKey()), r.getValue(), Math::max));
                    long now = System.currentTimeMillis();
                    for (House h : new ArrayList<>(houses.all())) {
                        UUID owner = h.owner();
                        if (owner == null) {
                            continue;
                        }
                        Long s = seen.get(owner);
                        if (s == null || Bukkit.getPlayer(owner) != null) {
                            // Primeira vez que a regra vê este dono, ou ele está online: conta a partir de agora.
                            touch(owner);
                        } else if (HouseRules.inactive(s, now, days)) {
                            plugin.getLogger().info("Casa " + h.name() + " retomada: " + h.ownerName() + " não entra há " + days + " dias.");
                            houses.evict(h.name());
                        }
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível conferir os donos de casas", ex);
                    return null;
                });
    }

    // ------------------------------------------------------------------ imposto

    private static final long DAY = 86_400_000L;

    private double taxFraction() {
        return eco.config().decimal("casas.imposto.fracao", 0.02, 0, 1);
    }

    private int taxPeriod() {
        return eco.config().integer("casas.imposto.a_cada_dias", 7, 1, 365);
    }

    private int taxGrace() {
        return eco.config().integer("casas.imposto.tolerancia_dias", 7, 0, 365);
    }

    private long taxOf(House h) {
        return HouseRules.tax(h.price(), taxFraction());
    }

    /**
     * A cada 10 min: cobra o imposto vencido de quem está online e retoma a casa de quem passou
     * da tolerância. O dono novo de uma casa começa com um período inteiro pago.
     */
    public void tickTax() {
        if (taxFraction() <= 0) {
            return;
        }
        db.query("SELECT name, owner, due FROM eco_house_tax", rs -> Map.entry(rs.getString(1), new TaxRow(rs.getString(2), rs.getLong(3))))
                .thenAccept(rows -> AincradCore.get().runSync(() -> {
                    taxes.clear();
                    rows.forEach(r -> taxes.put(r.getKey(), r.getValue()));
                    long now = System.currentTimeMillis();
                    for (House h : new ArrayList<>(houses.all())) {
                        UUID owner = h.owner();
                        if (owner == null || taxOf(h) <= 0) {
                            continue;
                        }
                        TaxRow row = taxes.get(h.name());
                        if (row == null || !row.owner().equals(owner.toString())) {
                            TaxRow fresh = new TaxRow(owner.toString(), now + taxPeriod() * DAY);
                            taxes.put(h.name(), fresh);
                            db.update(db.upsert("eco_house_tax", List.of("name"), List.of("owner", "due")), h.name(), fresh.owner(), fresh.due());
                            continue;
                        }
                        HouseRules.Tax state = HouseRules.taxState(row.due(), now, taxGrace());
                        Player p = Bukkit.getPlayer(owner);
                        if (state == HouseRules.Tax.EM_DIA) {
                            continue;
                        }
                        if (p != null && eco.balance(p) >= taxOf(h)) {
                            pay(p, h, 1);
                        } else if (state == HouseRules.Tax.PERDIDO) {
                            plugin.getLogger().info("Casa " + h.name() + " retomada: imposto atrasado de " + h.ownerName() + ".");
                            if (p != null) {
                                msg.send(p, "imposto.perdeu", Messages.vars("casa", h.name()));
                            }
                            taxes.remove(h.name());
                            db.update("DELETE FROM eco_house_tax WHERE name = ?", h.name());
                            houses.evict(h.name());
                        } else if (p != null) {
                            msg.send(p, "imposto.vencido", Messages.vars("casa", h.name(),
                                    "faltam", HouseRules.daysUntil(row.due() + taxGrace() * DAY, now)), eco.col(taxOf(h)));
                        }
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível conferir o imposto das casas", ex);
                    return null;
                });
    }

    /** {@code /casa imposto}: quanto é e quando vence. */
    public void taxInfo(Player p, House h) {
        TaxRow row = taxes.get(h.name());
        long tax = taxOf(h);
        if (tax <= 0 || row == null) {
            msg.send(p, tax <= 0 ? "imposto.desligado" : "imposto.sem_data");
            return;
        }
        long now = System.currentTimeMillis();
        boolean late = HouseRules.taxState(row.due(), now, taxGrace()) != HouseRules.Tax.EM_DIA;
        msg.send(p, late ? "imposto.vencido" : "imposto.info", Messages.vars("casa", h.name(), "dias", taxPeriod(),
                "faltam", HouseRules.daysUntil(late ? row.due() + taxGrace() * DAY : row.due(), now)), eco.col(tax));
    }

    /** {@code /casa imposto pagar [períodos]}: paga o que venceu, ou adianta. */
    public void pay(Player p, House h, int periods) {
        TaxRow row = taxes.get(h.name());
        long tax = taxOf(h);
        if (tax <= 0 || row == null) {
            msg.send(p, tax <= 0 ? "imposto.desligado" : "imposto.sem_data");
            return;
        }
        long now = System.currentTimeMillis();
        int max = HouseRules.payable(row.due(), now, taxPeriod(), eco.config().integer("casas.imposto.max_adiantado", 8, 0, 520));
        int n = Math.min(Math.max(1, periods), max);
        if (n <= 0) {
            msg.send(p, "imposto.limite");
            return;
        }
        long cost = tax * n;
        if (eco.balance(p) < cost) {
            msg.send(p, "imposto.sem_saldo", eco.col(cost));
            return;
        }
        long newDue = HouseRules.paid(row.due(), n, taxPeriod());
        UUID id = p.getUniqueId();
        // Só cobra se o vencimento ainda é o que este servidor viu: outro servidor pode ter cobrado antes.
        db.update("UPDATE eco_house_tax SET due = ? WHERE name = ? AND due = ?", newDue, h.name(), row.due())
                .whenComplete((changed, ex) -> AincradCore.get().runSync(() -> {
                    if (ex != null || changed == null || changed == 0) {
                        return;
                    }
                    if (!eco.withdraw(id, cost, "casa:imposto")) {
                        db.update("UPDATE eco_house_tax SET due = ? WHERE name = ? AND due = ?", row.due(), h.name(), newDue);
                        return;
                    }
                    taxes.put(h.name(), new TaxRow(row.owner(), newDue));
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) {
                        msg.send(online, "imposto.pago", Messages.vars("casa", h.name(),
                                "faltam", HouseRules.daysUntil(newDue, System.currentTimeMillis())), eco.col(cost));
                    }
                }));
    }

    // ------------------------------------------------------------------ a casa volta ao estado original

    private File file(String house) {
        return new File(dir, house + ".nbt");
    }

    private boolean resetEnabled() {
        return eco.config().bool("casas.restaurar_ao_devolver", true);
    }

    /** Guarda a casa como está agora: é assim que ela volta quando o dono sair. */
    public boolean capture(House h) {
        Region r = AincradCore.get().regions().get(h.region());
        World w = r == null ? null : Bukkit.getWorld(r.world());
        if (w == null) {
            return false;
        }
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("não foi possível criar " + dir);
            }
            Structure s = Bukkit.getStructureManager().createStructure();
            s.fill(new Location(w, r.minX(), r.minY(), r.minZ()),
                    new BlockVector(r.maxX() - r.minX() + 1, r.maxY() - r.minY() + 1, r.maxZ() - r.minZ() + 1), true);
            Bukkit.getStructureManager().saveStructure(file(h.name()), s);
            return true;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível guardar o modelo da casa " + h.name(), e);
            return false;
        }
    }

    /** Repõe a casa como no modelo guardado. Só funciona no servidor onde a casa fica. */
    private boolean restore(House h) {
        Region r = AincradCore.get().regions().get(h.region());
        World w = r == null ? null : Bukkit.getWorld(r.world());
        File f = file(h.name());
        if (w == null || !f.isFile()) {
            return false;
        }
        try {
            Structure s = Bukkit.getStructureManager().loadStructure(f);
            BoundingBox box = new BoundingBox(r.minX(), r.minY(), r.minZ(), r.maxX() + 1, r.maxY() + 1, r.maxZ() + 1);
            // Molduras, suportes de armadura e itens no chão do dono antigo saem antes de o modelo voltar.
            for (Entity e : w.getNearbyEntities(box)) {
                if (e instanceof Hanging || e instanceof ArmorStand || e instanceof Item) {
                    e.remove();
                }
            }
            s.place(new Location(w, r.minX(), r.minY(), r.minZ()), true, StructureRotation.NONE, Mirror.NONE, 0, 1f, new Random());
            return true;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível restaurar a casa " + h.name(), e);
            return false;
        }
    }

    /** O dono saiu (vendeu, foi despejado ou sumiu): a casa fica marcada para voltar ao modelo. */
    public void vacated(String house) {
        if (!resetEnabled()) {
            return;
        }
        db.update(db.upsert("eco_house_reset", List.of("name"), List.of("since")), house, System.currentTimeMillis())
                .thenRun(() -> AincradCore.get().runSync(this::tickResets));
    }

    /**
     * A cada minuto: restaura as casas marcadas que ficam neste servidor. A marca fica no banco
     * porque, numa rede, quem vende pode estar em outro servidor.
     */
    public void tickResets() {
        db.query("SELECT name FROM eco_house_reset", rs -> rs.getString(1)).thenAccept(names -> AincradCore.get().runSync(() -> {
            for (String name : names) {
                House h = houses.byName(name);
                // Casa removida, ou já comprada de novo: não se apaga a casa do dono novo.
                boolean drop = h == null || !h.forSale();
                if (drop || restore(h)) {
                    db.update("DELETE FROM eco_house_reset WHERE name = ?", name);
                    if (!drop) {
                        plugin.getLogger().info("Casa " + name + " restaurada ao modelo.");
                    }
                }
            }
        })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler as casas a restaurar", ex);
            return null;
        });
    }

    public boolean hasModel(String house) {
        return file(house).isFile();
    }
}
