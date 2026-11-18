package com.aincrad.reputation;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.reputation.hook.Hooks;
import com.aincrad.reputation.model.MonumentRules;
import com.aincrad.reputation.model.MonumentRules.Cause;
import com.aincrad.reputation.model.MonumentRules.Entry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * O Monumento da Vida: o nome de todos os jogadores que já entraram, riscado quando o jogador
 * morre, com a data e a causa. Fica no banco do core (vale para a rede) e aparece num mural no
 * mundo e em {@code /monumento}.
 */
public final class MonumentService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS rep_monument (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, "
                    + "joined_at BIGINT NOT NULL, died_at BIGINT NOT NULL, cause VARCHAR(16), detail VARCHAR(64), deaths INT NOT NULL)");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private final Hooks hooks;
    private final File file;
    private volatile List<Entry> entries = List.of();
    private Location mural;
    private TextDisplay board;
    private int perPage = 16;
    private int page;

    public MonumentService(Plugin plugin, Database db, Messages msg, Hooks hooks) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.hooks = hooks;
        file = new File(plugin.getDataFolder(), "monumento.yml");
    }

    // ------------------------------------------------------------------ dados

    public void load(int namesPerPage) {
        perPage = namesPerPage;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        World w = y.getString("mundo") == null ? null : Bukkit.getWorld(y.getString("mundo", ""));
        mural = w == null ? null : new Location(w, y.getDouble("x"), y.getDouble("y"), y.getDouble("z"), (float) y.getDouble("yaw"), 0);
        removeBoard();
        refresh();
    }

    /** Relê os nomes do banco (outro servidor da rede pode ter mudado). */
    public void refresh() {
        db.query("SELECT uuid, name, joined_at, died_at, cause, detail, deaths FROM rep_monument", rs -> {
            Cause c;
            try {
                c = rs.getString(5) == null ? Cause.OUTRO : Cause.valueOf(rs.getString(5));
            } catch (IllegalArgumentException e) {
                c = Cause.OUTRO;
            }
            return new Entry(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), c,
                    rs.getString(6) == null ? "" : rs.getString(6), rs.getInt(7));
        }).thenAccept(list -> entries = MonumentRules.ordered(list)).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível ler o Monumento da Vida", ex);
            return null;
        });
    }

    public List<Entry> entries() {
        return entries;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String id = p.getUniqueId().toString();
        long now = System.currentTimeMillis();
        db.async(c -> {
            if (Database.update(c, "UPDATE rep_monument SET name = ? WHERE uuid = ?", p.getName(), id) == 0) {
                Database.update(c, "INSERT INTO rep_monument (uuid, name, joined_at, died_at, cause, detail, deaths) VALUES (?, ?, ?, 0, NULL, NULL, 0)",
                        id, p.getName(), now);
                return true;
            }
            return false;
        }).thenAccept(added -> {
            if (added) {
                refresh();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar " + p.getName() + " no Monumento da Vida", ex);
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        EntityDamageEvent last = p.getLastDamageCause();
        Entity by = last instanceof EntityDamageByEntityEvent e ? e.getDamager() : null;
        if (by instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            by = shooter;
        }
        Player killer = p.getKiller() != null ? p.getKiller() : by instanceof Player pl ? pl : null;
        boolean byPlayer = killer != null && !killer.equals(p);
        String fight = hooks.bossFight(p);
        Cause cause = MonumentRules.cause(last == null ? null : last.getCause().name(), byPlayer, fight != null, by != null);
        String detail = switch (cause) {
            case JOGADOR -> killer.getName();
            case CHEFE -> fight;
            case MOB -> by.getName();
            case QUEDA -> "";
            case OUTRO -> last == null ? "" : last.getCause().name().toLowerCase(Locale.ROOT);
        };
        String d = detail == null ? "" : detail.length() > 64 ? detail.substring(0, 64) : detail;
        long now = System.currentTimeMillis();
        String id = p.getUniqueId().toString();
        db.async(c -> {
            // O nome é riscado na primeira morte; as seguintes só contam.
            Database.update(c, "UPDATE rep_monument SET died_at = ?, cause = ?, detail = ? WHERE uuid = ? AND died_at = 0", now, cause.name(), d, id);
            return Database.update(c, "UPDATE rep_monument SET deaths = deaths + 1 WHERE uuid = ?", id);
        }).thenAccept(n -> refresh()).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível riscar " + p.getName() + " no Monumento da Vida", ex);
            return null;
        });
    }

    // ------------------------------------------------------------------ texto

    private Component line(Entry e) {
        if (!e.struck()) {
            return msg.get("monumento.vivo", Messages.vars("jogador", e.name()));
        }
        return msg.get("monumento.riscado", Messages.vars("jogador", e.name(), "data", DATE.format(Instant.ofEpochMilli(e.diedAt())),
                "causa", cause(e)));
    }

    private String cause(Entry e) {
        String base = msg.raw("monumento.causa." + e.cause().name().toLowerCase(Locale.ROOT));
        return e.detail().isBlank() || e.cause() == Cause.QUEDA ? base : base + ": " + e.detail();
    }

    /** {@code /monumento [busca]}: a mesma lista, em menu. */
    public void menu(Player p, String query) {
        List<Entry> list = MonumentRules.search(entries, query);
        if (list.isEmpty()) {
            msg.send(p, "monumento.ninguem");
            return;
        }
        int[] n = MonumentRules.count(entries);
        Menu menu = Menu.of(msg.get("monumento.menu", Messages.vars("vivos", n[0], "mortos", n[1]))).rows(6);
        for (Entry e : list) {
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("monumento.entrou", Messages.vars("data", DATE.format(Instant.ofEpochMilli(e.joinedAt())))));
            if (e.struck()) {
                lore.add(msg.get("monumento.morreu", Messages.vars("data", DATE.format(Instant.ofEpochMilli(e.diedAt())), "causa", cause(e))));
                if (e.deaths() > 1) {
                    lore.add(msg.get("monumento.mortes", Messages.vars("mortes", e.deaths())));
                }
            }
            menu.item(Menu.Button.of(new ItemStack(e.struck() ? Material.SKELETON_SKULL : Material.PLAYER_HEAD), line(e), pl -> { }).lore(lore));
        }
        AincradCore.get().menus().open(p, menu);
    }

    // ------------------------------------------------------------------ mural

    /** Põe o mural onde o admin está (um texto no ar, virado para onde ele olha). */
    public void define(Player p) {
        mural = p.getLocation().add(0, 1.6, 0);
        YamlConfiguration y = new YamlConfiguration();
        y.set("mundo", mural.getWorld().getName());
        y.set("x", mural.getX());
        y.set("y", mural.getY());
        y.set("z", mural.getZ());
        y.set("yaw", (double) mural.getYaw());
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível gravar o monumento.yml", e);
        }
        removeBoard();
        tick();
    }

    public boolean hasMural() {
        return mural != null;
    }

    public void removeBoard() {
        if (board != null) {
            board.remove();
            board = null;
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (board != null && board.getLocation().getChunk().equals(event.getChunk())) {
            removeBoard();
        }
    }

    /** A cada 10 segundos: mostra a página seguinte de nomes no mural. */
    public void tick() {
        if (mural == null || mural.getWorld() == null
                || !mural.getWorld().isChunkLoaded(mural.getBlockX() >> 4, mural.getBlockZ() >> 4)) {
            return;
        }
        if (board == null || !board.isValid()) {
            // O mural não é salvo com o mapa: o plugin o põe de volta, então nunca duplica.
            board = mural.getWorld().spawn(mural, TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(Display.Billboard.FIXED);
                t.setLineWidth(400);
                t.setSeeThrough(false);
            });
        }
        List<Entry> all = entries;
        int pages = MonumentRules.pages(all.size(), perPage);
        int[] n = MonumentRules.count(all);
        Component text = msg.get("monumento.titulo", Messages.vars("vivos", n[0], "mortos", n[1],
                "pagina", Math.floorMod(page, pages) + 1, "paginas", pages));
        for (Entry e : MonumentRules.page(all, page, perPage)) {
            text = text.append(Component.newline()).append(line(e));
        }
        board.text(text);
        page++;
    }
}
