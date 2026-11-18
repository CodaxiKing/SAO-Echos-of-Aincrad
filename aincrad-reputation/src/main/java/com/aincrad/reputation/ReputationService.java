package com.aincrad.reputation;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.reputation.api.AchievementGrantEvent;
import com.aincrad.reputation.api.CursorColorChangeEvent;
import com.aincrad.reputation.model.Achievements;
import com.aincrad.reputation.model.Achievements.Def;
import com.aincrad.reputation.model.Achievements.Type;
import com.aincrad.reputation.model.CursorRules;
import com.aincrad.reputation.model.CursorRules.Color;
import com.aincrad.reputation.model.CursorRules.Cursor;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * O que cada jogador fez e como isso aparece: conquistas, títulos e a cor do cursor. Os dados
 * ficam no banco do core (valem para a rede) e são carregados quando o jogador entra.
 */
public final class ReputationService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS rep_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, title VARCHAR(64), "
                    + "orange_until BIGINT NOT NULL, red_until BIGINT NOT NULL, kills INT NOT NULL, jail_until BIGINT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS rep_achievements (uuid VARCHAR(36) NOT NULL, id VARCHAR(96) NOT NULL, title VARCHAR(64), "
                    + "at_time BIGINT NOT NULL, PRIMARY KEY (uuid, id));"
                    + "CREATE TABLE IF NOT EXISTS rep_firsts (what VARCHAR(96) NOT NULL PRIMARY KEY, uuid VARCHAR(36) NOT NULL, "
                    + "name VARCHAR(32) NOT NULL, at_time BIGINT NOT NULL)");

    /** O que se sabe de um jogador online. */
    public static final class Rep {
        final UUID id;
        String name;
        String title;
        Cursor cursor = Cursor.CLEAN;
        long jailUntil;
        /** Chave da conquista → título que ela liberou ("" = nenhum). */
        final Map<String, String> achievements = new LinkedHashMap<>();
        Color shown = Color.VERDE;

        Rep(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public Cursor cursor() {
            return cursor;
        }

        public long jailUntil() {
            return jailUntil;
        }

        public String title() {
            return title;
        }
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Database db;
    private final Map<UUID, Rep> players = new ConcurrentHashMap<>();
    private Achievements achievements = Achievements.parse(null, new ArrayList<>());

    public ReputationService(Plugin plugin, Messages msg, ConfigFile config) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.db = AincradCore.get().database();
    }

    /** Lê achievements.yml e carrega quem já está online. */
    public void load() {
        File f = new File(plugin.getDataFolder(), "achievements.yml");
        if (!f.exists()) {
            plugin.saveResource("achievements.yml", false);
        }
        List<String> errors = new ArrayList<>();
        achievements = Achievements.parse(YamlConfiguration.loadConfiguration(f), errors);
        errors.forEach(e -> plugin.getLogger().warning(e));
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                loadBlocking(p.getUniqueId(), p.getName());
                refresh(p);
            } catch (SQLException e) {
                plugin.getLogger().warning("Não foi possível carregar a reputação de " + p.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info(achievements.all().size() + " conquista(s) carregada(s).");
    }

    public Achievements achievements() {
        return achievements;
    }

    public Messages messages() {
        return msg;
    }

    /** Os dados do jogador online (vazios se o pré-login falhou). */
    public Rep rep(Player p) {
        return players.computeIfAbsent(p.getUniqueId(), k -> new Rep(k, p.getName()));
    }

    public Color color(Player p) {
        return rep(p).cursor.color(System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ banco

    private void loadBlocking(UUID id, String name) throws SQLException {
        Rep r = new Rep(id, name);
        String key = id.toString();
        db.sync(c -> {
            for (Object[] row : Database.query(c, "SELECT title, orange_until, red_until, kills, jail_until FROM rep_players WHERE uuid = ?",
                    rs -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getLong(5)}, key)) {
                r.title = (String) row[0];
                r.cursor = new Cursor((Long) row[1], (Long) row[2], (Integer) row[3]);
                r.jailUntil = (Long) row[4];
            }
            for (String[] row : Database.query(c, "SELECT id, title FROM rep_achievements WHERE uuid = ? ORDER BY at_time",
                    rs -> new String[]{rs.getString(1), rs.getString(2)}, key)) {
                r.achievements.put(row[0], row[1] == null ? "" : row[1]);
            }
            return null;
        });
        // A cor com que ele entra não é uma mudança: sem aviso nem evento.
        r.shown = r.cursor.color(System.currentTimeMillis());
        players.put(id, r);
    }

    void save(Rep r) {
        db.update(db.upsert("rep_players", List.of("uuid"), List.of("name", "title", "orange_until", "red_until", "kills", "jail_until")),
                r.id.toString(), r.name, r.title, r.cursor.orangeUntil(), r.cursor.redUntil(), r.cursor.kills(), r.jailUntil);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            loadBlocking(event.getUniqueId(), event.getName());
        } catch (SQLException e) {
            plugin.getLogger().warning("Não foi possível carregar a reputação de " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Rep r = players.remove(event.getPlayer().getUniqueId());
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName(event.getPlayer().getUniqueId()));
        if (team != null) {
            team.unregister();
        }
        if (r != null) {
            save(r);
        }
    }

    /** Ao desligar: tira os times de nome que este plugin criou. */
    public void shutdown() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (UUID id : players.keySet()) {
            Team team = board.getTeam(teamName(id));
            if (team != null) {
                team.unregister();
            }
        }
        players.values().forEach(this::save);
        players.clear();
    }

    // ------------------------------------------------------------------ conquistas

    /**
     * Algo aconteceu com o jogador: libera as conquistas daquele tipo (e andar, e profissão).
     * "Primeiro no andar" só vai para o primeiro do servidor inteiro.
     */
    public void happened(Player p, Type type, int floor, String profession) {
        for (Def d : achievements.matching(type, floor, profession)) {
            if (type == Type.PRIMEIRO_NO_ANDAR) {
                grantFirst(p, d, floor);
            } else {
                grant(p, d, floor);
            }
        }
    }

    /** O mesmo para quem pode estar offline (participantes de um chefe): grava direto, sem anúncio. */
    public void happened(UUID player, Type type, int floor, String profession) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            happened(online, type, floor, profession);
            return;
        }
        for (Def d : achievements.matching(type, floor, profession)) {
            if (type != Type.PRIMEIRO_NO_ANDAR) {
                String key = d.key(floor);
                String title = Def.fill(d.title(), floor);
                db.async(c -> {
                    // Quem já tem a conquista fica como está.
                    if (Database.query(c, "SELECT 1 FROM rep_achievements WHERE uuid = ? AND id = ?", rs -> 1, player.toString(), key).isEmpty()) {
                        Database.update(c, "INSERT INTO rep_achievements (uuid, id, title, at_time) VALUES (?, ?, ?, ?)", player.toString(), key,
                                title, System.currentTimeMillis());
                    }
                    return null;
                });
            }
        }
    }

    /** Só o primeiro jogador da rede a conseguir fica com a conquista: quem grava a linha primeiro. */
    private void grantFirst(Player p, Def d, int floor) {
        String what = d.key(floor);
        db.async(c -> {
            if (!Database.query(c, "SELECT 1 FROM rep_firsts WHERE what = ?", rs -> 1, what).isEmpty()) {
                return false;
            }
            try {
                Database.update(c, "INSERT INTO rep_firsts (what, uuid, name, at_time) VALUES (?, ?, ?, ?)", what, p.getUniqueId().toString(),
                        p.getName(), System.currentTimeMillis());
                return true;
            } catch (SQLException taken) {
                // Outro servidor gravou no mesmo instante.
                return false;
            }
        }).whenComplete((first, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Reputação: erro ao conferir " + what, err);
            } else if (first && p.isOnline()) {
                grant(p, d, floor);
            }
        }));
    }

    /**
     * Dá a conquista ao jogador (uma vez só).
     *
     * @return true se ele ganhou agora
     */
    public boolean grant(Player p, Def d, int floor) {
        Rep r = rep(p);
        String key = d.key(floor);
        if (r.achievements.containsKey(key)) {
            return false;
        }
        String title = Def.fill(d.title(), floor);
        String name = Def.fill(d.name(), floor);
        r.achievements.put(key, title == null ? "" : title);
        db.update("INSERT INTO rep_achievements (uuid, id, title, at_time) VALUES (?, ?, ?, ?)", p.getUniqueId().toString(), key, title,
                System.currentTimeMillis());
        var vars = Messages.vars("conquista", name, "titulo", title == null ? "" : title, "jogador", p.getName());
        msg.title(p, "conquista.titulo", Duration.ofSeconds(3), vars);
        msg.send(p, title == null ? "conquista.ganhou" : "conquista.ganhou_titulo", vars);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (d.announce()) {
            Component line = msg.prefix().append(msg.get("conquista.anuncio", vars));
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != p) {
                    other.sendMessage(line);
                }
            }
        }
        // O primeiro título vira o título em uso, para o jogador já ver o efeito.
        if (title != null && r.title == null) {
            r.title = title;
            save(r);
            refresh(p);
        }
        Bukkit.getPluginManager().callEvent(new AchievementGrantEvent(p, key, name, title, d.announce()));
        return true;
    }

    /** Os títulos que o jogador pode usar, na ordem em que os ganhou. */
    public Set<String> titles(Rep r) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : r.achievements.values()) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** Escolhe o título em uso ({@code null} = nenhum). @return false se o jogador não tem esse título */
    public boolean setTitle(Player p, String title) {
        Rep r = rep(p);
        if (title != null && !titles(r).contains(title)) {
            return false;
        }
        r.title = title;
        save(r);
        refresh(p);
        return true;
    }

    /** Admin: dá um título avulso ao jogador (fica guardado como uma conquista "titulo:..."). */
    public void giveTitle(Player p, String title) {
        Rep r = rep(p);
        String key = "titulo:" + title.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (r.achievements.put(key, title) == null) {
            db.update("INSERT INTO rep_achievements (uuid, id, title, at_time) VALUES (?, ?, ?, ?)", p.getUniqueId().toString(),
                    key.length() > 96 ? key.substring(0, 96) : key, title, System.currentTimeMillis());
        }
    }

    // ------------------------------------------------------------------ cursor

    /** Troca o estado do cursor do jogador, grava e atualiza como ele aparece. */
    public void setCursor(Player p, Cursor cursor) {
        Rep r = rep(p);
        r.cursor = cursor;
        save(r);
        refresh(p);
    }

    public void setJail(Player p, long until) {
        Rep r = rep(p);
        r.jailUntil = until;
        save(r);
    }

    // ------------------------------------------------------------------ como aparece

    private static String teamName(UUID id) {
        return "rep_" + id.toString().replace("-", "").substring(0, 12);
    }

    private static NamedTextColor textColor(Color c) {
        return switch (c) {
            case VERDE -> NamedTextColor.GREEN;
            case LARANJA -> NamedTextColor.GOLD;
            case VERMELHO -> NamedTextColor.RED;
        };
    }

    private Component titlePrefix(Rep r) {
        return r.title == null ? Component.empty() : msg.get("titulo.formato", Messages.vars("titulo", r.title));
    }

    /** O nome sobre a cabeça é deste plugin? ("auto": só quando o AincradSocial, que põe a tag da guilda, não está no servidor) */
    private boolean ownsNametag() {
        String mode = config.string("nome.sobre_a_cabeca", "auto");
        return mode.equalsIgnoreCase("sim") || (mode.equalsIgnoreCase("auto") && !Bukkit.getPluginManager().isPluginEnabled("AincradSocial"));
    }

    /** Atualiza a tab, o nome sobre a cabeça e o prefixo do LuckPerms conforme o título e a cor do cursor. */
    public void refresh(Player p) {
        Rep r = rep(p);
        Color now = r.cursor.color(System.currentTimeMillis());
        if (now != r.shown) {
            Color before = r.shown;
            r.shown = now;
            msg.send(p, "cursor.agora." + now.key());
            Bukkit.getPluginManager().callEvent(new CursorColorChangeEvent(p, before.key(), now.key()));
        }
        Component prefix = titlePrefix(r);
        if (config.bool("nome.na_tab", true)) {
            p.playerListName(prefix.append(Component.text(p.getName(), textColor(now))));
        }
        if (ownsNametag()) {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam(teamName(p.getUniqueId()));
            if (team == null) {
                team = board.registerNewTeam(teamName(p.getUniqueId()));
            }
            team.color(textColor(now));
            team.prefix(prefix);
            if (!team.hasEntry(p.getName())) {
                team.addEntry(p.getName());
            }
        }
        if (config.bool("titulos.luckperms", false) && Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            int priority = config.integer("titulos.luckperms_prioridade", 50, 0, 10_000);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getUniqueId() + " meta removeprefix " + priority);
            if (r.title != null) {
                String legacy = LegacyComponentSerializer.legacyAmpersand().serialize(prefix).replace("\"", "'");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getUniqueId() + " meta addprefix " + priority + " \"" + legacy + "\"");
            }
        }
    }

    /** O título antes do nome no chat (quando o LuckPerms não está cuidando disso). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config.bool("titulos.no_chat", true) || config.bool("titulos.luckperms", false)) {
            return;
        }
        Rep r = players.get(event.getPlayer().getUniqueId());
        if (r == null || r.title == null) {
            return;
        }
        Component prefix = titlePrefix(r);
        ChatRenderer before = event.renderer();
        event.renderer((source, displayName, message, viewer) -> prefix.append(before.render(source, displayName, message, viewer)));
    }

    /** Roda a cada 5 s: quando o tempo de laranja ou de vermelho acaba, o jogador volta a aparecer com a cor certa. */
    public void tickColors() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Rep r = players.get(p.getUniqueId());
            if (r != null && r.cursor.color(now) != r.shown) {
                refresh(p);
            }
        }
    }

    // ------------------------------------------------------------------ telas

    /** /titulos: os títulos do jogador; clicar escolhe. */
    public void titlesMenu(Player p) {
        Rep r = rep(p);
        Set<String> mine = titles(r);
        if (mine.isEmpty()) {
            msg.send(p, "titulo.nenhum");
            return;
        }
        Menu menu = Menu.of(msg.get("titulo.menu")).rows(6);
        menu.floating();
        for (String t : mine) {
            boolean using = t.equals(r.title);
            menu.item(Menu.Button.of(new ItemStack(using ? Material.ENCHANTED_BOOK : Material.NAME_TAG), msg.get("titulo.item", Messages.vars("titulo", t)), pl -> {
                setTitle(pl, t);
                msg.send(pl, "titulo.escolheu", Messages.vars("titulo", t));
            }).closing().lore(List.of(msg.get(using ? "titulo.em_uso" : "titulo.clique"))));
        }
        menu.button(Menu.Button.of(new ItemStack(Material.BARRIER), msg.get("titulo.tirar"), pl -> {
            setTitle(pl, null);
            msg.send(pl, "titulo.tirou");
        }).closing());
        AincradCore.get().menus().open(p, menu);
    }

    /** /conquistas: todas as conquistas, as que o jogador tem em destaque. */
    public void achievementsMenu(Player p) {
        Rep r = rep(p);
        Menu menu = Menu.of(msg.get("conquista.menu", Messages.vars("tem", r.achievements.size()))).rows(6);
        menu.floating();
        for (Def d : achievements.all().values()) {
            // Uma conquista por andar aparece uma vez para cada andar em que o jogador a conseguiu.
            List<String> keys = new ArrayList<>();
            for (String k : r.achievements.keySet()) {
                if (k.equals(d.id()) || (d.perFloor() && k.startsWith(d.id() + "_"))) {
                    keys.add(k);
                }
            }
            if (keys.isEmpty()) {
                menu.item(Menu.Button.of(new ItemStack(Material.GRAY_DYE), msg.get("conquista.item_falta", Messages.vars("conquista", clean(d.name()))),
                        pl -> { }).lore(List.of(msg.get("conquista.descricao", Messages.vars("descricao", clean(d.description())))))
                        .category(plainText("conquista.aba_falta")));
                continue;
            }
            for (String k : keys) {
                int floor = 0;
                try {
                    floor = d.perFloor() && k.length() > d.id().length() ? Integer.parseInt(k.substring(d.id().length() + 1)) : d.floor();
                } catch (NumberFormatException e) {
                    // chave antiga: mostra sem o andar
                }
                List<Component> lore = new ArrayList<>();
                lore.add(msg.get("conquista.descricao", Messages.vars("descricao", Def.fill(d.description(), floor))));
                String title = r.achievements.get(k);
                if (title != null && !title.isEmpty()) {
                    lore.add(msg.get("conquista.libera", Messages.vars("titulo", title)));
                }
                menu.item(Menu.Button.of(new ItemStack(Material.LIME_DYE), msg.get("conquista.item_tem", Messages.vars("conquista", Def.fill(d.name(), floor))),
                        pl -> { }).lore(lore).category(plainText("conquista.aba_tem")));
            }
        }
        AincradCore.get().menus().open(p, menu);
    }

    private String plainText(String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(msg.get(key));
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace("<andar>", "?");
    }

    /** /reputacao [jogador]: a cor do cursor, quanto falta e as mortes causadas. */
    public void describe(CommandSender to, Player target) {
        Rep r = rep(target);
        long now = System.currentTimeMillis();
        Color c = r.cursor.color(now);
        long left = c == Color.VERMELHO ? r.cursor.redUntil() - now : c == Color.LARANJA ? r.cursor.orangeUntil() - now : 0;
        msg.send(to, "cursor.info", Messages.vars("jogador", target.getName(), "cor", msg.get("cursor.cor." + c.key()),
                "tempo", left > 0 ? TimeFormat.duration(left, 2) : "—", "mortes", r.cursor.kills(),
                "titulo", r.title == null ? "—" : r.title, "conquistas", r.achievements.size()));
        if (r.jailUntil > now) {
            msg.sendRaw(to, "prisao.info", Messages.vars("tempo", TimeFormat.duration(r.jailUntil - now, 2)));
        }
    }

    /** Admin: limpa a cor do cursor, as mortes e a prisão do jogador. */
    public void clear(Player p) {
        Rep r = rep(p);
        r.cursor = CursorRules.Cursor.CLEAN;
        r.jailUntil = 0;
        save(r);
        refresh(p);
    }
}
