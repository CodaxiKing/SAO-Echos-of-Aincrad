package com.aincrad.bosses.command;

import com.aincrad.bosses.api.BossFightEndEvent;
import com.aincrad.bosses.data.BossStateStore;
import com.aincrad.bosses.data.RoomStore;
import com.aincrad.bosses.fight.BossFight;
import com.aincrad.bosses.fight.FightManager;
import com.aincrad.bosses.maze.MazeService;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.Cuboid;
import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.RegionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /aincrad chefe ...} e {@code /aincrad labirinto ...}. */
public final class BossCommands {

    /** Maior porta aceita (blocos). */
    private static final int MAX_DOOR = 400;

    private final Plugin plugin;
    private final FightManager fights;
    private final MazeService maze;
    private final Messages msg;

    public BossCommands(Plugin plugin, FightManager fights, MazeService maze) {
        this.plugin = plugin;
        this.fights = fights;
        this.maze = maze;
        this.msg = fights.messages();
    }

    private void log(CommandSender actor, String action, String detail) {
        plugin.getServer().getPluginManager().callEvent(new AincradAdminActionEvent(actor, "bosses", action, detail));
    }

    private static List<String> filter(List<String> opts, String last) {
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last.toLowerCase(Locale.ROOT))) {
                out.add(o);
            }
        }
        return out;
    }

    private List<String> ids() {
        List<String> out = new ArrayList<>();
        fights.registry().all().forEach(d -> out.add(d.id()));
        return out;
    }

    private void saveRooms(CommandSender sender) {
        try {
            fights.rooms().save();
        } catch (IOException e) {
            msg.send(sender, "geral.erro");
            plugin.getLogger().warning("Não foi possível gravar salas.yml: " + e.getMessage());
        }
    }

    /** Seleção da varinha do core como caixa, ou null (com aviso). */
    private Cuboid selection(Player p) {
        RegionManager.Selection sel = AincradCore.get().regions().selection(p);
        if (sel == null || !sel.complete() || !sel.a().getWorld().equals(sel.b().getWorld())) {
            msg.send(p, "admin.sem_selecao");
            return null;
        }
        Location a = sel.a();
        Location b = sel.b();
        return Cuboid.of(a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public SubCommand boss() {
        return new SubCommand() {
            @Override public String name() { return "chefe"; }
            @Override public String permission() { return "aincrad.admin"; }
            @Override public String usage() { return "chefe lista | info|invocar|resetar|porta|esquecer <id> | sala <id> canto1|canto2|spawn|entrada"; }
            @Override public Component description() { return msg.get("admin.descricao_chefe"); }

            @Override
            public void execute(CommandSender sender, String[] args) {
                String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                if (op.equals("lista")) {
                    List<String> out = new ArrayList<>();
                    for (BossDef d : fights.registry().all()) {
                        RoomStore.Room r = fights.rooms().get(d.id());
                        out.add(d.id() + " (andar " + d.floor() + (r != null && r.ready() ? "" : ", sem sala")
                                + (fights.states().defeated(d.id()) ? ", derrotado" : "") + (fights.fight(d.id()) != null ? ", em luta" : "") + ")");
                    }
                    msg.send(sender, "admin.chefe_lista", Messages.vars("lista", out.isEmpty() ? "—" : String.join("; ", out)));
                    return;
                }
                BossDef def = args.length > 1 ? fights.registry().get(args[1]) : null;
                if (!List.of("info", "invocar", "resetar", "porta", "sala", "esquecer").contains(op) || args.length < 2) {
                    msg.send(sender, "admin.uso_chefe");
                    return;
                }
                if (def == null) {
                    msg.send(sender, "admin.chefe_desconhecido", Messages.vars("id", args[1]));
                    return;
                }
                var chefe = Messages.vars("chefe", def.name(), "id", def.id());
                switch (op) {
                    case "info" -> {
                        RoomStore.Room r = fights.rooms().get(def.id());
                        BossStateStore.State s = fights.states().get(def.id());
                        msg.send(sender, "admin.chefe_info", chefe, Messages.vars("andar", def.floor(), "level", def.level(),
                                "vida", Math.round(def.health()), "fases", def.phases().size(),
                                "sala", r == null || r.box == null ? "—" : r.box.text(), "porta", r == null ? 0 : r.door.size(),
                                "vitorias", s == null ? 0 : s.kills(), "last", s == null || s.lastAttack() == null ? "—" : s.lastAttack()));
                    }
                    case "invocar" -> {
                        if (!(sender instanceof Player p)) {
                            msg.send(sender, "geral.so_jogador");
                            return;
                        }
                        if (fights.summon(def, p.getLocation())) {
                            msg.send(sender, "admin.invocado", chefe);
                            log(sender, "chefe.invocar", def.id());
                        } else {
                            msg.send(sender, "admin.ja_em_luta", chefe);
                        }
                    }
                    case "resetar" -> {
                        BossFight f = fights.fight(def.id());
                        if (f == null) {
                            RoomStore.Room r = fights.rooms().get(def.id());
                            if (r != null) {
                                fights.setDoor(r, false);
                            }
                            msg.send(sender, "admin.sem_luta", chefe);
                            return;
                        }
                        fights.end(f, BossFightEndEvent.Reason.RESET);
                        msg.send(sender, "admin.resetado", chefe);
                        log(sender, "chefe.resetar", def.id());
                    }
                    case "esquecer" -> {
                        fights.states().clear(def.id()).thenRun(() ->
                                AincradCore.get().runSync(() -> AincradCore.get().publish(FightManager.BUS_STATE, def.id())));
                        msg.send(sender, "admin.esquecido", chefe);
                        log(sender, "chefe.esquecer", def.id());
                    }
                    case "porta" -> door(sender, def);
                    default -> room(sender, def, args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "");
                }
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                return switch (args.length) {
                    case 1 -> filter(List.of("lista", "info", "invocar", "resetar", "sala", "porta", "esquecer"), args[0]);
                    case 2 -> args[0].equalsIgnoreCase("lista") ? List.of() : filter(ids(), args[1]);
                    case 3 -> args[0].equalsIgnoreCase("sala") ? filter(List.of("canto1", "canto2", "spawn", "entrada"), args[2]) : List.of();
                    default -> List.of();
                };
            }
        };
    }

    private void room(CommandSender sender, BossDef def, String what) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        RoomStore.Room r = fights.rooms().getOrCreate(def.id());
        Location l = p.getLocation();
        var chefe = Messages.vars("chefe", def.name(), "id", def.id());
        switch (what) {
            case "canto1", "canto2" -> {
                int[] c = {l.getBlockX(), l.getBlockY(), l.getBlockZ()};
                if (r.cornerWorld != null && !r.cornerWorld.equals(l.getWorld().getName())) {
                    r.corner1 = null;
                    r.corner2 = null;
                }
                r.cornerWorld = l.getWorld().getName();
                if (what.equals("canto1")) {
                    r.corner1 = c;
                } else {
                    r.corner2 = c;
                }
                msg.send(p, "admin.canto", chefe, Messages.vars("n", what.substring(5), "x", c[0], "y", c[1], "z", c[2]));
                if (r.corner1 != null && r.corner2 != null) {
                    r.box = Cuboid.of(r.cornerWorld, r.corner1[0], r.corner1[1], r.corner1[2], r.corner2[0], r.corner2[1], r.corner2[2]);
                    msg.send(p, "admin.sala_pronta", chefe, Messages.vars("blocos", r.box.volume()));
                    saveRooms(sender);
                    log(sender, "chefe.sala", def.id() + " " + r.box.text());
                }
            }
            case "spawn" -> {
                r.spawn = RoomStore.text(l);
                msg.send(p, "admin.spawn", chefe);
                saveRooms(sender);
            }
            case "entrada" -> {
                r.entry = RoomStore.text(l);
                msg.send(p, "admin.entrada", chefe);
                saveRooms(sender);
            }
            default -> msg.send(p, "admin.uso_chefe");
        }
    }

    /** Os blocos de ar da seleção da varinha viram o vão da porta. */
    private void door(CommandSender sender, BossDef def) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        RoomStore.Room r = fights.rooms().get(def.id());
        var chefe = Messages.vars("chefe", def.name(), "id", def.id());
        if (r == null || r.box == null) {
            msg.send(p, "admin.sem_sala", chefe);
            return;
        }
        Cuboid sel = selection(p);
        if (sel == null) {
            return;
        }
        if (!sel.world().equals(r.box.world()) || sel.volume() > MAX_DOOR) {
            msg.send(p, "admin.porta_grande", Messages.vars("max", MAX_DOOR));
            return;
        }
        fights.setDoor(r, false);
        r.door.clear();
        for (int x = sel.minX(); x <= sel.maxX(); x++) {
            for (int y = sel.minY(); y <= sel.maxY(); y++) {
                for (int z = sel.minZ(); z <= sel.maxZ(); z++) {
                    if (p.getWorld().getBlockAt(x, y, z).getType().isAir()) {
                        r.door.add(new int[]{x, y, z});
                    }
                }
            }
        }
        saveRooms(sender);
        msg.send(p, "admin.porta", chefe, Messages.vars("blocos", r.door.size(), "material", fights.doorMaterial()));
        log(sender, "chefe.porta", def.id() + " " + r.door.size() + " blocos");
    }

    public SubCommand maze() {
        return new SubCommand() {
            @Override public String name() { return "labirinto"; }
            @Override public String permission() { return "aincrad.admin"; }
            @Override public String usage() { return "labirinto zona <andar> <nivel> | listar | remover <id>"; }
            @Override public Component description() { return msg.get("admin.descricao_labirinto"); }

            @Override
            public void execute(CommandSender sender, String[] args) {
                String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                try {
                    switch (op) {
                        case "listar" -> {
                            List<String> out = new ArrayList<>();
                            maze.zones().values().forEach(z -> out.add(z.id() + " (andar " + z.floor() + ", nível " + z.level() + ", até " + z.limit() + " mobs)"));
                            msg.send(sender, "admin.zona_lista", Messages.vars("lista", out.isEmpty() ? "—" : String.join("; ", out)));
                        }
                        case "remover" -> {
                            boolean ok = args.length > 1 && maze.remove(args[1]);
                            msg.send(sender, ok ? "admin.zona_removida" : "admin.zona_desconhecida", Messages.vars("id", args.length > 1 ? args[1] : "?"));
                            if (ok) {
                                log(sender, "labirinto.remover", args[1]);
                            }
                        }
                        case "zona" -> {
                            if (!(sender instanceof Player p)) {
                                msg.send(sender, "geral.so_jogador");
                                return;
                            }
                            int floor;
                            int level;
                            try {
                                floor = Integer.parseInt(args[1]);
                                level = Integer.parseInt(args[2]);
                            } catch (RuntimeException e) {
                                msg.send(sender, "admin.uso_labirinto");
                                return;
                            }
                            Cuboid box = selection(p);
                            if (box == null) {
                                return;
                            }
                            MazeService.Zone z = maze.create(floor, Math.max(1, level), box);
                            msg.send(p, "admin.zona_criada", Messages.vars("id", z.id(), "andar", floor, "nivel", z.level(), "blocos", box.volume()));
                            log(sender, "labirinto.zona", z.id() + " " + box.text());
                        }
                        default -> msg.send(sender, "admin.uso_labirinto");
                    }
                } catch (IOException e) {
                    msg.send(sender, "geral.erro");
                    plugin.getLogger().warning("Não foi possível gravar labirinto.yml: " + e.getMessage());
                }
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                if (args.length == 1) {
                    return filter(List.of("zona", "listar", "remover"), args[0]);
                }
                return args.length == 2 && args[0].equalsIgnoreCase("remover") ? filter(new ArrayList<>(maze.zones().keySet()), args[1]) : List.of();
            }
        };
    }
}
