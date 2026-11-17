package com.aincrad.bosses.raid;

import com.aincrad.bosses.api.RaidScheduledEvent;
import com.aincrad.bosses.fight.FightManager;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.Raid;
import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Quadro de raid: um jogador marca a raid de um chefe com data, hora e vagas por função; os
 * outros se inscrevem. Avisa os inscritos antes e na hora, e (se a config pedir) reserva a sala
 * para eles. As raids ficam no banco do core e valem para a rede.
 */
public final class RaidService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS boss_raids ({AUTO_ID}, boss VARCHAR(64) NOT NULL, leader VARCHAR(36) NOT NULL, "
                    + "leader_name VARCHAR(16) NOT NULL, at_time BIGINT NOT NULL, tanks INT NOT NULL, damage INT NOT NULL, support INT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS boss_raid_members (raid BIGINT NOT NULL, uuid VARCHAR(36) NOT NULL, "
                    + "name VARCHAR(16) NOT NULL, role VARCHAR(16) NOT NULL, PRIMARY KEY (raid, uuid))");
    public static final String BUS = "bosses:raids";

    private final Plugin plugin;
    private final FightManager fights;
    private final ConfigFile config;
    private final Messages msg;
    private final Database db;
    private volatile Map<Long, Raid> raids = new TreeMap<>();
    /** Avisos já dados neste servidor: "id:antes" e "id:agora". */
    private final Set<String> warned = new HashSet<>();

    public RaidService(Plugin plugin, FightManager fights, ConfigFile config, Database db) {
        this.plugin = plugin;
        this.fights = fights;
        this.config = config;
        this.msg = fights.messages();
        this.db = db;
    }

    private int duration() {
        return config.integer("raid.duracao_minutos", 60, 5, 1440);
    }

    private int before() {
        return config.integer("raid.aviso_minutos_antes", 15, 1, 240);
    }

    // ------------------------------------------------------------------ banco

    private static Map<Long, Raid> read(java.sql.Connection c) throws SQLException {
        Map<Long, Raid> m = new TreeMap<>();
        for (Raid r : Database.query(c, "SELECT id, boss, leader, leader_name, at_time, tanks, damage, support FROM boss_raids",
                rs -> new Raid(rs.getLong(1), rs.getString(2), UUID.fromString(rs.getString(3)), rs.getString(4), rs.getLong(5),
                        rs.getInt(6), rs.getInt(7), rs.getInt(8)))) {
            m.put(r.id(), r);
        }
        for (Object[] row : Database.query(c, "SELECT raid, uuid, name, role FROM boss_raid_members ORDER BY raid",
                rs -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)})) {
            Raid r = m.get((Long) row[0]);
            Raid.Role role = Raid.Role.parse((String) row[3]);
            if (r != null && role != null) {
                r.members().put(UUID.fromString((String) row[1]), new Raid.Member((String) row[2], role));
            }
        }
        return m;
    }

    public void loadBlocking() throws SQLException {
        raids = db.sync(RaidService::read);
    }

    public void reloadAsync() {
        db.async(RaidService::read).thenAccept(m -> raids = m);
    }

    private void changed() {
        AincradCore core = AincradCore.get();
        core.runSync(() -> core.publish(BUS, "mudou"));
    }

    public Map<Long, Raid> all() {
        return raids;
    }

    // ------------------------------------------------------------------ ações

    /** Marca uma raid. O líder entra como tanque, dano ou suporte, na primeira função com vaga. */
    public void create(Player leader, BossDef def, long at, int tanks, int damage, int support) {
        int max = config.integer("raid.max_por_jogador", 2, 1, 50);
        long mine = raids.values().stream().filter(r -> r.leader().equals(leader.getUniqueId())).count();
        if (mine >= max) {
            msg.send(leader, "raid.limite", Messages.vars("max", max));
            return;
        }
        UUID id = leader.getUniqueId();
        String name = leader.getName();
        db.async(c -> {
            Database.update(c, "INSERT INTO boss_raids (boss, leader, leader_name, at_time, tanks, damage, support) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    def.id(), id.toString(), name, at, tanks, damage, support);
            return Database.query(c, "SELECT MAX(id) FROM boss_raids WHERE leader = ? AND at_time = ?", rs -> rs.getLong(1), id.toString(), at).get(0);
        }).whenComplete((raidId, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Não foi possível marcar a raid", err);
                msg.send(leader, "geral.erro");
                return;
            }
            Raid r = new Raid(raidId, def.id(), id, name, at, tanks, damage, support);
            Map<Long, Raid> m = new TreeMap<>(raids);
            m.put(raidId, r);
            raids = m;
            for (Raid.Role role : Raid.Role.values()) {
                if (r.slots(role) > 0) {
                    join(leader, r, role, false);
                    break;
                }
            }
            changed();
            plugin.getServer().getPluginManager().callEvent(new RaidScheduledEvent(def.id(), def.name(), def.floor(), raidId, id, at));
            msg.send(leader, "raid.criada", vars(r));
        }));
    }

    public void join(Player p, Raid r, Raid.Role role, boolean tell) {
        if (!r.join(p.getUniqueId(), p.getName(), role)) {
            msg.send(p, "raid.funcao_cheia", role(role));
            return;
        }
        db.update(db.upsert("boss_raid_members", List.of("raid", "uuid"), List.of("name", "role")),
                r.id(), p.getUniqueId().toString(), p.getName(), role.name()).thenRun(this::changed);
        if (tell) {
            msg.send(p, "raid.entrou", vars(r), role(role));
        }
    }

    public void leave(Player p, Raid r) {
        if (r.leader().equals(p.getUniqueId())) {
            msg.send(p, "raid.lider_nao_sai");
            return;
        }
        if (!r.leave(p.getUniqueId())) {
            msg.send(p, "raid.nao_inscrito");
            return;
        }
        db.update("DELETE FROM boss_raid_members WHERE raid = ? AND uuid = ?", r.id(), p.getUniqueId().toString()).thenRun(this::changed);
        msg.send(p, "raid.saiu", vars(r));
    }

    /** Cancela (líder ou admin). */
    public void cancel(Raid r) {
        Map<Long, Raid> m = new TreeMap<>(raids);
        m.remove(r.id());
        raids = m;
        db.async(c -> {
            Database.update(c, "DELETE FROM boss_raid_members WHERE raid = ?", r.id());
            return Database.update(c, "DELETE FROM boss_raids WHERE id = ?", r.id());
        }).thenRun(this::changed);
        for (UUID id : r.members().keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg.send(p, "raid.cancelada", vars(r));
            }
        }
    }

    // ------------------------------------------------------------------ sala e avisos

    /**
     * A sala do chefe está reservada e o jogador não é inscrito? Admins passam. Usado pelo
     * {@link FightManager} antes de deixar alguém entrar ou começar a luta.
     */
    public boolean blocks(Player p, String bossId) {
        if (!config.bool("raid.reservar_sala", true) || p.hasPermission("aincrad.admin")) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean reserved = false;
        for (Raid r : raids.values()) {
            if (r.boss().equals(bossId) && r.reserved(now, before(), duration())) {
                if (r.members().containsKey(p.getUniqueId())) {
                    return false;
                }
                reserved = true;
            }
        }
        return reserved;
    }

    /** Roda a cada 20 s: avisos de "falta pouco" e "é agora", e limpeza das raids que passaram. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Raid r : new ArrayList<>(raids.values())) {
            if (r.over(now, duration())) {
                // Só o servidor do líder apaga (ou qualquer um, 5 min depois), para não apagarem todos juntos.
                if (Bukkit.getPlayer(r.leader()) != null || r.over(now, duration() + 5)) {
                    Map<Long, Raid> m = new TreeMap<>(raids);
                    m.remove(r.id());
                    raids = m;
                    db.async(c -> {
                        Database.update(c, "DELETE FROM boss_raid_members WHERE raid = ?", r.id());
                        return Database.update(c, "DELETE FROM boss_raids WHERE id = ?", r.id());
                    });
                }
                continue;
            }
            if (now >= r.at() && warned.add(r.id() + ":agora")) {
                tell(r, "raid.aviso_agora");
            } else if (now < r.at() && now >= r.at() - before() * 60_000L && warned.add(r.id() + ":antes")) {
                tell(r, "raid.aviso_antes");
            }
        }
    }

    private void tell(Raid r, String key) {
        for (UUID id : r.members().keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg.send(p, key, vars(r), Messages.vars("minutos", before()));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
            }
        }
    }

    // ------------------------------------------------------------------ textos e menus

    private String bossName(String id) {
        BossDef d = fights.registry().get(id);
        return d == null ? id : d.name();
    }

    public TagResolver vars(Raid r) {
        return Messages.vars("n", r.id(), "chefe", bossName(r.boss()), "lider", r.leaderName(),
                "data", new SimpleDateFormat("dd/MM HH:mm").format(new Date(r.at())), "inscritos", r.members().size(),
                "tanques", r.taken(Raid.Role.TANQUE) + "/" + r.slots(Raid.Role.TANQUE),
                "danos", r.taken(Raid.Role.DANO) + "/" + r.slots(Raid.Role.DANO),
                "suportes", r.taken(Raid.Role.SUPORTE) + "/" + r.slots(Raid.Role.SUPORTE));
    }

    private TagResolver role(Raid.Role role) {
        return Messages.vars("funcao", msg.raw("raid.funcao." + role.key()));
    }

    /** O quadro: uma raid por item; clicar abre a inscrição. */
    public void openBoard(Player p) {
        Menu menu = Menu.of(msg.get("raid.titulo")).rows(6);
        for (Raid r : raids.values()) {
            List<Component> lore = new ArrayList<>(msg.list("raid.item_lore", vars(r)));
            if (r.members().containsKey(p.getUniqueId())) {
                lore.add(msg.get("raid.item_inscrito", role(r.members().get(p.getUniqueId()).role())));
            }
            menu.item(Menu.Button.of(ItemBuilder.of(Material.WITHER_SKELETON_SKULL).build(), msg.get("raid.item", vars(r)), pl -> openRaid(pl, r.id())).lore(lore));
        }
        if (raids.isEmpty()) {
            menu.item(Menu.Button.of(ItemBuilder.of(Material.PAPER).build(), msg.get("raid.vazio"), null));
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("raid.fechar"), pl -> { }).closing());
        AincradCore.get().menus().open(p, menu);
    }

    private void openRaid(Player p, long id) {
        Raid r = raids.get(id);
        if (r == null) {
            openBoard(p);
            return;
        }
        Menu menu = Menu.of(msg.get("raid.item", vars(r))).rows(4);
        Material[] icons = {Material.SHIELD, Material.IRON_SWORD, Material.GOLDEN_APPLE};
        for (Raid.Role role : Raid.Role.values()) {
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("raid.vagas", Messages.vars("ocupadas", r.taken(role), "total", r.slots(role))));
            r.members().values().stream().filter(m -> m.role() == role)
                    .forEach(m -> lore.add(msg.get("raid.membro", Messages.vars("jogador", m.name()))));
            menu.item(Menu.Button.of(ItemBuilder.of(icons[role.ordinal()]).hideAll().build(), msg.get("raid.botao_funcao", role(role)), pl -> {
                Raid now = raids.get(id);
                if (now != null) {
                    join(pl, now, role, true);
                }
                openRaid(pl, id);
            }).lore(lore));
        }
        boolean leader = r.leader().equals(p.getUniqueId());
        if (leader || p.hasPermission("aincrad.admin")) {
            menu.button(Menu.Button.of(ItemBuilder.of(Material.BARRIER).build(), msg.get("raid.botao_cancelar"), pl -> {
                Raid now = raids.get(id);
                if (now != null) {
                    cancel(now);
                }
            }).closing());
        }
        if (!leader && r.members().containsKey(p.getUniqueId())) {
            menu.button(Menu.Button.of(ItemBuilder.of(Material.RED_DYE).build(), msg.get("raid.botao_sair"), pl -> {
                Raid now = raids.get(id);
                if (now != null) {
                    leave(pl, now);
                }
                openBoard(pl);
            }));
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.ARROW).build(), msg.get("raid.voltar"), this::openBoard));
        AincradCore.get().menus().open(p, menu);
    }
}
