package com.aincrad.life.marriage;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.life.api.MarriageEvent;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.marriage.MarriageRules.Refusal;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Casamento: o pedido com anel, em zona segura; o baú do casal, com o autor de cada depósito e
 * uma trava que vale entre os servidores da rede; a vida, o level e o andar do par numa barra;
 * um teleporte por dia até ele; a casa de um aberta para o outro; e o divórcio, que devolve cada
 * item do baú a quem o colocou.
 */
public final class MarriageService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_marriages ({AUTO_ID}, a VARCHAR(36) NOT NULL, a_name VARCHAR(32) NOT NULL, b VARCHAR(36) NOT NULL, "
                    + "b_name VARCHAR(32) NOT NULL, since BIGINT NOT NULL, lock_owner VARCHAR(36), lock_until BIGINT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS life_marriage_chest (marriage_id BIGINT NOT NULL, slot INT NOT NULL, item TEXT NOT NULL, "
                    + "author VARCHAR(36) NOT NULL, PRIMARY KEY (marriage_id, slot));"
                    + "CREATE TABLE IF NOT EXISTS life_divorces (uuid VARCHAR(36) NOT NULL PRIMARY KEY, at_time BIGINT NOT NULL)");

    /** Canal do MessageBus: um casal mudou (payload "uuid,uuid") ou o estado de um jogador casado ("=" + dados). */
    public static final String BUS = "life:casamento";

    private static final int CHEST_SIZE = 27;
    private static final long LOCK_MS = 60_000L;

    /** Um casal. */
    public record Marriage(long id, UUID a, String aName, UUID b, String bName, long since) {
        public UUID spouse(UUID me) {
            return a.equals(me) ? b : a;
        }

        public String spouseName(UUID me) {
            return a.equals(me) ? bName : aName;
        }
    }

    /** Como o par está agora (vida, level, andar), para a barra. */
    private record Status(double health, double max, int level, int floor, String server, long at) {
    }

    /** O baú do casal aberto por um dos dois. */
    private static final class ChestSession implements InventoryHolder {
        final Marriage marriage;
        final UUID opener;
        final ItemStack[] before = new ItemStack[CHEST_SIZE];
        final UUID[] authors = new UUID[CHEST_SIZE];
        Inventory inventory;
        int seconds;

        ChestSession(Marriage marriage, UUID opener) {
            this.marriage = marriage;
            this.opener = opener;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final Database db;
    private final NamespacedKey keyRing;
    private final Map<UUID, Marriage> married = new ConcurrentHashMap<>();
    private final Map<UUID, ChestSession> chests = new HashMap<>();
    private final Map<UUID, Status> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, UUID> proposals = new HashMap<>();
    private int ticks;

    public MarriageService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.db = AincradCore.get().database();
        this.keyRing = new NamespacedKey(plugin, "anel");
    }

    /** Carrega os casais de quem já está online (ao ligar e no /aincrad reload). */
    public void load() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                loadBlocking(p.getUniqueId());
            } catch (SQLException e) {
                plugin.getLogger().warning("Não foi possível carregar o casamento de " + p.getName() + ": " + e.getMessage());
            }
        }
    }

    public Marriage marriage(UUID player) {
        return married.get(player);
    }

    // ------------------------------------------------------------------ banco

    private static Marriage read(Connection c, String uuid) throws SQLException {
        List<Marriage> l = Database.query(c, "SELECT id, a, a_name, b, b_name, since FROM life_marriages WHERE a = ? OR b = ?",
                rs -> new Marriage(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3), UUID.fromString(rs.getString(4)),
                        rs.getString(5), rs.getLong(6)), uuid, uuid);
        return l.isEmpty() ? null : l.get(0);
    }

    private void loadBlocking(UUID id) throws SQLException {
        Marriage m = db.sync(c -> read(c, id.toString()));
        if (m == null) {
            married.remove(id);
        } else {
            married.put(id, m);
        }
    }

    private <T> void run(CommandSender who, Database.SqlFunction<T> work, Consumer<T> then) {
        db.async(work).whenComplete((value, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Casamento: erro no banco", err);
                msg.send(who, "geral.erro");
                return;
            }
            then.accept(value);
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            loadBlocking(event.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("Não foi possível carregar o casamento de " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        married.remove(id);
        statuses.remove(id);
        proposals.remove(id);
        proposals.values().removeIf(id::equals);
        BossBar bar = bars.remove(id);
        if (bar != null) {
            event.getPlayer().hideBossBar(bar);
        }
    }

    /** Aviso de outro servidor: um casal mudou, ou o estado de alguém casado. */
    public void onBus(String payload) {
        if (payload.startsWith("=")) {
            String[] p = payload.substring(1).split(";");
            try {
                statuses.put(UUID.fromString(p[0]), new Status(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Integer.parseInt(p[3]),
                        Integer.parseInt(p[4]), p[5], System.currentTimeMillis()));
            } catch (RuntimeException e) {
                // aviso estragado: ignora
            }
            return;
        }
        for (String raw : payload.split(",")) {
            try {
                UUID id = UUID.fromString(raw);
                if (Bukkit.getPlayer(id) != null) {
                    db.async(c -> read(c, raw)).thenAccept(m -> AincradCore.get().runSync(() -> {
                        if (m == null) {
                            married.remove(id);
                        } else if (Bukkit.getPlayer(id) != null) {
                            married.put(id, m);
                        }
                    }));
                }
            } catch (IllegalArgumentException e) {
                // aviso estragado: ignora
            }
        }
    }

    // ------------------------------------------------------------------ anel e pedido

    /** O anel de casamento. Quem pede precisa ter um na mão; ele é gasto quando o pedido é aceito. */
    public ItemStack ring(int amount) {
        ItemStack it = new ItemStack(ringMaterial(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("casamento.anel_nome"));
        meta.lore(msg.list("casamento.anel_lore"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyRing, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private Material ringMaterial() {
        Material m = Material.matchMaterial(config.string("casamento.anel.item", "GOLD_NUGGET"));
        return m == null ? Material.GOLD_NUGGET : m;
    }

    /** O item é um anel? O anel do plugin sempre; o item comum do mesmo material, só se a config deixar (para vender em loja de NPC). */
    private boolean isRing(ItemStack it) {
        if (it == null || it.getType() != ringMaterial()) {
            return false;
        }
        boolean tagged = it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(keyRing);
        return tagged || config.bool("casamento.anel.aceitar_item_comum", false);
    }

    private Refusal check(Player from, Player to) {
        boolean sameWorld = from.getWorld().equals(to.getWorld());
        boolean safe = !config.bool("casamento.exigir_zona_segura", true) || !hooks.hasFloors()
                || (hooks.safeZone(from.getLocation()) && hooks.safeZone(to.getLocation()));
        return MarriageRules.canPropose(from.equals(to), married.containsKey(from.getUniqueId()), married.containsKey(to.getUniqueId()),
                isRing(from.getInventory().getItemInMainHand()), sameWorld ? from.getLocation().distance(to.getLocation()) : -1,
                config.decimal("casamento.distancia", 8, 1, 64), safe);
    }

    /** /casar &lt;jogador&gt;: com o anel na mão, os dois no mesmo lugar e em zona segura. */
    public void propose(Player p, String targetName) {
        Player t = Bukkit.getPlayerExact(targetName);
        if (t == null) {
            msg.send(p, "admin.offline");
            return;
        }
        Refusal r = check(p, t);
        if (r != Refusal.OK) {
            msg.send(p, "casamento.recusa." + r.name().toLowerCase(Locale.ROOT), Messages.vars("jogador", t.getName()));
            return;
        }
        int days = config.integer("casamento.espera_dias", 7, 0, 3650);
        String a = p.getUniqueId().toString();
        String b = t.getUniqueId().toString();
        run(p, c -> {
            long now = System.currentTimeMillis();
            long[] left = new long[2];
            for (Object[] row : Database.query(c, "SELECT uuid, at_time FROM life_divorces WHERE uuid = ? OR uuid = ?",
                    rs -> new Object[]{rs.getString(1), rs.getLong(2)}, a, b)) {
                left[row[0].equals(a) ? 0 : 1] = MarriageRules.waitLeft((Long) row[1], now, days);
            }
            return left;
        }, left -> {
            if (left[0] > 0 || left[1] > 0) {
                msg.send(p, left[0] > 0 ? "casamento.espera_sua" : "casamento.espera_par",
                        Messages.vars("tempo", TimeFormat.duration(Math.max(left[0], left[1]), 2), "jogador", t.getName()));
                return;
            }
            if (!p.isOnline() || !t.isOnline()) {
                return;
            }
            proposals.put(t.getUniqueId(), p.getUniqueId());
            msg.send(p, "casamento.pediu", Messages.vars("jogador", t.getName()));
            t.playSound(t.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
            AincradCore.get().menus().confirm(t, msg.get("casamento.pedido", Messages.vars("jogador", p.getName())), yes -> accept(yes, p.getUniqueId()),
                    no -> {
                        if (proposals.remove(no.getUniqueId(), p.getUniqueId()) && p.isOnline()) {
                            msg.send(p, "casamento.recusou", Messages.vars("jogador", no.getName()));
                        }
                    });
        });
    }

    /** O aceite: tudo é conferido de novo, o anel é gasto e o casal é gravado. */
    private void accept(Player t, UUID proposerId) {
        Player p = Bukkit.getPlayer(proposerId);
        if (!proposals.remove(t.getUniqueId(), proposerId) || p == null) {
            msg.send(t, "casamento.pedido_expirou");
            return;
        }
        Refusal r = check(p, t);
        if (r != Refusal.OK) {
            msg.send(p, "casamento.recusa." + r.name().toLowerCase(Locale.ROOT), Messages.vars("jogador", t.getName()));
            msg.send(t, "casamento.pedido_expirou");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
        long now = System.currentTimeMillis();
        String a = p.getUniqueId().toString();
        String b = t.getUniqueId().toString();
        run(p, c -> {
            // Outro servidor pode ter casado um dos dois neste instante.
            if (read(c, a) != null || read(c, b) != null) {
                return null;
            }
            Database.update(c, "INSERT INTO life_marriages (a, a_name, b, b_name, since, lock_until) VALUES (?, ?, ?, ?, ?, 0)",
                    a, p.getName(), b, t.getName(), now);
            return read(c, a);
        }, m -> {
            if (m == null) {
                if (p.isOnline()) {
                    SafeGive.give(p, ring(1));
                }
                msg.send(p, "casamento.recusa.ja_casado");
                return;
            }
            married.put(m.a(), m);
            married.put(m.b(), m);
            AincradCore.get().publish(BUS, a + "," + b);
            // A casa de um abre para o outro.
            hooks.houseAccess(m.a(), m.b(), true);
            hooks.houseAccess(m.b(), m.a(), true);
            for (Player pl : List.of(p, t)) {
                pl.getWorld().spawnParticle(Particle.HEART, pl.getLocation().add(0, 2, 0), 20, 0.5, 0.5, 0.5, 0);
                pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                msg.send(pl, "casamento.casou", Messages.vars("jogador", m.spouseName(pl.getUniqueId())));
            }
            if (config.bool("casamento.anunciar", true)) {
                Bukkit.broadcast(msg.prefix().append(msg.get("casamento.anuncio", Messages.vars("a", p.getName(), "b", t.getName()))));
            }
            Bukkit.getPluginManager().callEvent(new MarriageEvent(m.a(), m.b(), true));
        });
    }

    // ------------------------------------------------------------------ baú do casal

    private static String key(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return null;
        }
        ItemStack one = it.clone();
        one.setAmount(1);
        return Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    private static int amount(ItemStack it) {
        return it == null || it.getType().isAir() ? 0 : it.getAmount();
    }

    /** Pega a trava do baú para o jogador. Só passa se ninguém a tem (ou se venceu, ou se já é dele). */
    private static boolean lock(Connection c, long marriage, String me, long now) throws SQLException {
        return Database.update(c, "UPDATE life_marriages SET lock_owner = ?, lock_until = ? WHERE id = ? AND "
                + "(lock_owner IS NULL OR lock_until < ? OR lock_owner = ?)", me, now + LOCK_MS, marriage, now, me) > 0;
    }

    /** Abre o baú do casal. Enquanto um dos dois está com ele aberto, em qualquer servidor, o outro não abre. */
    public void openChest(Player p) {
        Marriage m = married.get(p.getUniqueId());
        if (m == null) {
            msg.send(p, "casamento.nao_casado");
            return;
        }
        if (chests.containsKey(p.getUniqueId())) {
            return;
        }
        String me = p.getUniqueId().toString();
        run(p, c -> {
            if (!lock(c, m.id(), me, System.currentTimeMillis())) {
                return null;
            }
            return Database.query(c, "SELECT slot, item, author FROM life_marriage_chest WHERE marriage_id = ?",
                    rs -> new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3)}, m.id());
        }, rows -> {
            if (rows == null) {
                msg.send(p, "casamento.bau_ocupado", Messages.vars("jogador", m.spouseName(p.getUniqueId())));
                return;
            }
            if (!p.isOnline()) {
                db.update("UPDATE life_marriages SET lock_owner = NULL WHERE id = ? AND lock_owner = ?", m.id(), me);
                return;
            }
            ChestSession s = new ChestSession(m, p.getUniqueId());
            s.inventory = Bukkit.createInventory(s, CHEST_SIZE, msg.get("casamento.bau_titulo"));
            for (Object[] row : rows) {
                int slot = (Integer) row[0];
                ItemStack it = unpack((String) row[1]);
                if (slot >= 0 && slot < CHEST_SIZE && it != null) {
                    s.inventory.setItem(slot, it);
                    s.before[slot] = it.clone();
                    try {
                        s.authors[slot] = UUID.fromString((String) row[2]);
                    } catch (IllegalArgumentException e) {
                        s.authors[slot] = p.getUniqueId();
                    }
                }
            }
            chests.put(p.getUniqueId(), s);
            p.openInventory(s.inventory);
        });
    }

    private static ItemStack unpack(String raw) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Grava o baú como está agora. Cada espaço guarda o autor: o que não mudou continua de quem
     * pôs; o que entrou ou cresceu é de quem está com o baú aberto.
     *
     * @param release solta a trava depois de gravar (o baú foi fechado)
     */
    private void saveChest(ChestSession s, boolean release) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < CHEST_SIZE; i++) {
            ItemStack now = s.inventory.getItem(i);
            UUID author = MarriageRules.author(key(s.before[i]), amount(s.before[i]), s.authors[i], key(now), amount(now), s.opener);
            s.before[i] = author == null ? null : now.clone();
            s.authors[i] = author;
            if (author != null) {
                rows.add(new Object[]{i, Base64.getEncoder().encodeToString(now.serializeAsBytes()), author.toString()});
            }
        }
        long id = s.marriage.id();
        String me = s.opener.toString();
        db.async(c -> {
            // Só grava quem ainda tem a trava: se ela venceu e o par abriu o baú, este estado não vale mais.
            if (Database.update(c, "UPDATE life_marriages SET lock_until = ? WHERE id = ? AND lock_owner = ?",
                    System.currentTimeMillis() + LOCK_MS, id, me) == 0) {
                return false;
            }
            Database.update(c, "DELETE FROM life_marriage_chest WHERE marriage_id = ?", id);
            for (Object[] r : rows) {
                Database.update(c, "INSERT INTO life_marriage_chest (marriage_id, slot, item, author) VALUES (?, ?, ?, ?)", id, r[0], r[1], r[2]);
            }
            if (release) {
                Database.update(c, "UPDATE life_marriages SET lock_owner = NULL, lock_until = 0 WHERE id = ? AND lock_owner = ?", id, me);
            }
            return true;
        }).whenComplete((ok, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Casamento: não foi possível gravar o baú do casal " + id, err);
            } else if (!ok) {
                plugin.getLogger().warning("Casamento: a trava do baú do casal " + id + " venceu antes de gravar; o estado não foi salvo.");
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ChestSession s && chests.remove(s.opener, s)) {
            saveChest(s, true);
        }
    }

    // ------------------------------------------------------------------ status e teleporte

    private Status statusOf(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        return new Status(p.getHealth(), max == null ? 20 : max.getValue(), hooks.rpgLevel(p), hooks.floorOf(p), AincradCore.get().server(),
                System.currentTimeMillis());
    }

    private boolean hud(Player p) {
        return AincradCore.get().profile(p).getBool(plugin, "casal_hud", true);
    }

    /** Roda uma vez por segundo: renova a trava dos baús abertos e atualiza a barra com o estado do par. */
    public void second() {
        ticks++;
        for (ChestSession s : new ArrayList<>(chests.values())) {
            // Grava de tempos em tempos: se o servidor cair, pouco se perde e a trava não fica presa.
            if (++s.seconds % 15 == 0) {
                saveChest(s, false);
            }
        }
        if (ticks % 2 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Marriage m = married.get(p.getUniqueId());
            BossBar bar = bars.get(p.getUniqueId());
            if (m == null || !hud(p)) {
                if (bar != null) {
                    p.hideBossBar(bar);
                    bars.remove(p.getUniqueId());
                }
                continue;
            }
            // O próprio estado: para a barra do par, aqui ou em outro servidor.
            Status mine = statusOf(p);
            statuses.put(p.getUniqueId(), mine);
            if (ticks % 6 == 0) {
                AincradCore.get().publish(BUS, "=" + p.getUniqueId() + ";" + mine.health() + ";" + mine.max() + ";" + mine.level() + ";"
                        + mine.floor() + ";" + mine.server());
            }
            Status other = statuses.get(m.spouse(p.getUniqueId()));
            boolean fresh = other != null && now - other.at() < 20_000L;
            Component title = fresh ? msg.get("casamento.barra", Messages.vars("jogador", m.spouseName(p.getUniqueId()), "level", other.level(),
                    "andar", other.floor(), "vida", Math.round(other.health()), "maximo", Math.round(other.max())))
                    : msg.get("casamento.barra_offline", Messages.vars("jogador", m.spouseName(p.getUniqueId())));
            float progress = fresh ? (float) Math.max(0, Math.min(1, other.health() / Math.max(1, other.max()))) : 0f;
            if (bar == null) {
                bar = BossBar.bossBar(title, progress, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
                bars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else {
                bar.name(title);
                bar.progress(progress);
            }
        }
    }

    /** Uma vez por dia: teleporta até o par, se ele está neste servidor e fora de sala de chefe. */
    public void teleport(Player p) {
        Marriage m = married.get(p.getUniqueId());
        if (m == null) {
            msg.send(p, "casamento.nao_casado");
            return;
        }
        Player spouse = Bukkit.getPlayer(m.spouse(p.getUniqueId()));
        var vars = Messages.vars("jogador", m.spouseName(p.getUniqueId()));
        if (spouse == null) {
            msg.send(p, "casamento.tp_longe", vars);
            return;
        }
        String today = LocalDate.now().toString();
        if (!MarriageRules.canTeleport(AincradCore.get().profile(p).get(plugin, "casal_tp"), today)) {
            msg.send(p, "casamento.tp_ja_usou");
            return;
        }
        Location to = spouse.getLocation();
        if (hooks.inBossRoom(to) || hooks.inBossRoom(p.getLocation())) {
            msg.send(p, "casamento.tp_sala_de_chefe");
            return;
        }
        AincradCore.get().profile(p).set(plugin, "casal_tp", today);
        p.teleport(to);
        p.getWorld().spawnParticle(Particle.HEART, to.clone().add(0, 2, 0), 8, 0.4, 0.3, 0.4, 0);
        p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
        msg.send(p, "casamento.tp_foi", vars);
        msg.send(spouse, "casamento.tp_chegou", Messages.vars("jogador", p.getName()));
    }

    // ------------------------------------------------------------------ divórcio

    /**
     * Desfaz o casamento. Cada item do baú volta para quem o colocou, pela caixa de retirada; a
     * casa de um deixa de abrir para o outro; os dois esperam alguns dias para casar de novo.
     *
     * @param by quem pediu (ou o admin); {@code player} é um dos dois
     */
    public void divorce(CommandSender by, UUID player) {
        String me = player.toString();
        run(by, c -> {
            Marriage m = read(c, me);
            if (m == null) {
                return new Object[]{"casamento.nao_casado"};
            }
            // Com o baú aberto pelo par, o divórcio espera: nada pode se perder nem duplicar.
            long now = System.currentTimeMillis();
            if (!lock(c, m.id(), "divorcio", now)) {
                return new Object[]{"casamento.bau_ocupado_divorcio"};
            }
            List<String[]> items = Database.query(c, "SELECT author, item FROM life_marriage_chest WHERE marriage_id = ?",
                    rs -> new String[]{rs.getString(1), rs.getString(2)}, m.id());
            Database.update(c, "DELETE FROM life_marriage_chest WHERE marriage_id = ?", m.id());
            Database.update(c, "DELETE FROM life_marriages WHERE id = ?", m.id());
            for (UUID u : List.of(m.a(), m.b())) {
                Database.update(c, "DELETE FROM life_divorces WHERE uuid = ?", u.toString());
                Database.update(c, "INSERT INTO life_divorces (uuid, at_time) VALUES (?, ?)", u.toString(), now);
            }
            return new Object[]{null, m, items};
        }, data -> {
            if (data[0] != null) {
                msg.send(by, (String) data[0]);
                return;
            }
            Marriage m = (Marriage) data[1];
            @SuppressWarnings("unchecked")
            List<String[]> items = (List<String[]>) data[2];
            int returned = 0;
            for (String[] row : items) {
                ItemStack it = unpack(row[1]);
                try {
                    if (it != null && hooks.mail(UUID.fromString(row[0]), it, "divorcio")) {
                        returned++;
                    }
                } catch (IllegalArgumentException e) {
                    // autor estragado: o item fica com quem pediu o divórcio
                    hooks.mail(player, it, "divorcio");
                }
            }
            married.remove(m.a());
            married.remove(m.b());
            AincradCore.get().publish(BUS, m.a() + "," + m.b());
            hooks.houseAccess(m.a(), m.b(), false);
            hooks.houseAccess(m.b(), m.a(), false);
            int days = config.integer("casamento.espera_dias", 7, 0, 3650);
            for (UUID u : List.of(m.a(), m.b())) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null) {
                    msg.send(pl, "casamento.divorciou", Messages.vars("jogador", m.spouseName(u), "itens", returned, "dias", days));
                }
            }
            if (!(by instanceof Player)) {
                msg.send(by, "casamento.admin_divorciou", Messages.vars("a", m.aName(), "b", m.bName()));
            }
            Bukkit.getPluginManager().callEvent(new MarriageEvent(player, m.spouse(player), false));
        });
    }

    // ------------------------------------------------------------------ menu e comandos

    /** O menu do casal (também é a entrada "Casal" do menu do AincradUI). */
    public void menu(Player p) {
        Marriage m = married.get(p.getUniqueId());
        if (m == null) {
            msg.send(p, "casamento.nao_casado");
            return;
        }
        Status other = statuses.get(m.spouse(p.getUniqueId()));
        boolean fresh = other != null && System.currentTimeMillis() - other.at() < 20_000L;
        List<Component> lore = new ArrayList<>();
        lore.add(msg.get("casamento.menu_desde", Messages.vars("tempo", TimeFormat.duration(System.currentTimeMillis() - m.since(), 2))));
        lore.add(fresh ? msg.get("casamento.menu_estado", Messages.vars("vida", Math.round(other.health()), "maximo", Math.round(other.max()),
                "level", other.level(), "andar", other.floor(), "servidor", other.server())) : msg.get("casamento.menu_offline"));
        Menu menu = Menu.of(msg.get("casamento.menu_titulo", Messages.vars("jogador", m.spouseName(p.getUniqueId())))).rows(3);
        menu.floating();
        menu.item(Menu.Button.of(new ItemStack(Material.PLAYER_HEAD), Messages.parse("<light_purple><nome>", Messages.vars("nome", m.spouseName(p.getUniqueId()))),
                pl -> { }).lore(lore));
        menu.item(Menu.Button.of(new ItemStack(Material.CHEST), msg.get("casamento.menu_bau"), this::openChest).closing());
        menu.item(Menu.Button.of(new ItemStack(Material.ENDER_PEARL), msg.get("casamento.menu_ir"), this::teleport).closing()
                .lore(msg.list("casamento.menu_ir_lore")));
        menu.item(Menu.Button.of(new ItemStack(Material.SPYGLASS), msg.get(hud(p) ? "casamento.menu_hud_ligado" : "casamento.menu_hud_desligado"),
                this::toggleHud).closing());
        menu.item(Menu.Button.of(new ItemStack(Material.BARRIER), msg.get("casamento.menu_divorciar"), this::askDivorce).closing());
        AincradCore.get().menus().open(p, menu);
    }

    private void toggleHud(Player p) {
        boolean now = !hud(p);
        AincradCore.get().profile(p).set(plugin, "casal_hud", now);
        msg.send(p, now ? "casamento.hud_ligou" : "casamento.hud_desligou");
    }

    private void askDivorce(Player p) {
        Marriage m = married.get(p.getUniqueId());
        if (m == null) {
            msg.send(p, "casamento.nao_casado");
            return;
        }
        AincradCore.get().menus().confirm(p, msg.get("casamento.confirmar_divorcio", Messages.vars("jogador", m.spouseName(p.getUniqueId()),
                "dias", config.integer("casamento.espera_dias", 7, 0, 3650))), yes -> divorce(yes, yes.getUniqueId()), no -> { });
    }

    /** /casar &lt;jogador&gt; */
    public void marryCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
        } else if (args.length == 0) {
            msg.send(p, "casamento.uso");
        } else {
            propose(p, args[0]);
        }
    }

    /** /divorciar */
    public void divorceCommand(CommandSender sender, String[] args) {
        if (sender instanceof Player p) {
            askDivorce(p);
        } else {
            msg.send(sender, "geral.so_jogador");
        }
    }

    /** /casal [bau | ir | hud] */
    public void coupleCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        switch (args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT)) {
            case "" -> menu(p);
            case "bau", "baú" -> openChest(p);
            case "ir" -> teleport(p);
            case "hud" -> toggleHud(p);
            default -> msg.send(p, "casamento.uso");
        }
    }

    /** Ao desligar o módulo: fecha os baús abertos (gravando) e tira as barras. */
    public void shutdown() {
        for (ChestSession s : new ArrayList<>(chests.values())) {
            Player p = Bukkit.getPlayer(s.opener);
            if (p != null) {
                p.closeInventory();
            }
        }
        // Quem não fechou pelo evento (servidor desligando): grava agora.
        for (ChestSession s : new ArrayList<>(chests.values())) {
            saveChest(s, true);
        }
        chests.clear();
        bars.forEach((id, bar) -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        });
        bars.clear();
    }

    /** Entrega de item sem perder o que não coube. */
    private static final class SafeGive {
        static void give(Player p, ItemStack it) {
            p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        }
    }
}
