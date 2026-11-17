package com.aincrad.floors.command;

import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import com.aincrad.floors.FloorService;
import com.aincrad.floors.GateService;
import com.aincrad.floors.model.Floor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** {@code /andares}, {@code /andar <n>} (com espera), {@code /aincrad andar} e {@code /aincrad gate}. */
public final class FloorCommands implements TabExecutor, Listener {

    private final Plugin plugin;
    private final FloorService floors;
    private final GateService gates;
    private final Messages msg;
    /** Teleportes em espera: jogador → tarefa. */
    private final Map<UUID, BukkitTask> waiting = new HashMap<>();

    public FloorCommands(Plugin plugin, FloorService floors, GateService gates) {
        this.plugin = plugin;
        this.floors = floors;
        this.gates = gates;
        this.msg = floors.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return true;
        }
        if (command.getName().equalsIgnoreCase("andares")) {
            gates.open(p, false);
            return true;
        }
        if (!floors.config().bool("teleporte.comando", false) && !p.hasPermission(FloorService.BYPASS)) {
            msg.send(p, "andar.comando_desligado");
            return true;
        }
        Integer n = args.length == 0 ? null : number(args[0]);
        if (n == null) {
            msg.send(p, "andar.uso");
            return true;
        }
        Floor f = floors.floor(n);
        if (f == null) {
            msg.send(p, "andar.desconhecido", Messages.vars("n", n));
            return true;
        }
        if (!floors.canEnter(p, n)) {
            msg.send(p, floors.isUnlocked(n) ? "andar.sem_acesso" : "andar.bloqueado", Messages.vars("andar", f.name()));
            return true;
        }
        int seconds = floors.config().integer("teleporte.espera", 3, 0, 60);
        if (seconds == 0 || p.hasPermission(FloorService.BYPASS)) {
            floors.teleport(p, n);
            return true;
        }
        cancel(p.getUniqueId(), false);
        Location start = p.getLocation();
        msg.send(p, "andar.espera", Messages.vars("andar", f.name(), "segundos", seconds));
        waiting.put(p.getUniqueId(), plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int left = seconds * 4;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel(p.getUniqueId(), false);
                    return;
                }
                if (!p.getWorld().equals(start.getWorld()) || p.getLocation().distanceSquared(start) > 0.25) {
                    cancel(p.getUniqueId(), true);
                    return;
                }
                if (--left <= 0) {
                    cancel(p.getUniqueId(), false);
                    floors.teleport(p, n);
                }
            }
        }, 5L, 5L));
        return true;
    }

    private void cancel(UUID id, boolean tell) {
        BukkitTask t = waiting.remove(id);
        if (t == null) {
            return;
        }
        t.cancel();
        Player p = Bukkit.getPlayer(id);
        if (tell && p != null) {
            msg.send(p, "andar.cancelado");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            cancel(p.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), false);
    }

    private static Integer number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> numbers(String prefix) {
        List<String> out = new ArrayList<>();
        for (Floor f : floors.registry().all()) {
            if (String.valueOf(f.number()).startsWith(prefix)) {
                out.add(String.valueOf(f.number()));
            }
        }
        return out;
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return command.getName().equalsIgnoreCase("andar") && args.length == 1 ? numbers(args[0]) : List.of();
    }

    private void log(CommandSender actor, String action, String detail) {
        plugin.getServer().getPluginManager().callEvent(new AincradAdminActionEvent(actor, "floors", action, detail));
    }

    // ------------------------------------------------------------------ /aincrad andar

    public SubCommand floorAdmin() {
        return new SubCommand() {
            @Override public String name() { return "andar"; }
            @Override public String permission() { return "aincrad.admin"; }
            @Override public String usage() { return "andar liberar|bloquear|setspawn|info <n>, conceder|revogar <jogador> <n>"; }
            @Override public Component description() { return msg.get("admin.descricao_andar"); }

            @Override
            public void execute(CommandSender sender, String[] args) {
                String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                boolean perPlayer = op.equals("conceder") || op.equals("revogar");
                Integer n = args.length > (perPlayer ? 2 : 1) ? number(args[perPlayer ? 2 : 1]) : null;
                Floor f = n == null ? null : floors.floor(n);
                if (n == null || !List.of("liberar", "bloquear", "setspawn", "info", "conceder", "revogar").contains(op)) {
                    msg.send(sender, "admin.uso_andar");
                    return;
                }
                if (f == null) {
                    msg.send(sender, "andar.desconhecido", Messages.vars("n", n));
                    return;
                }
                var andar = Messages.vars("andar", f.name(), "n", n);
                switch (op) {
                    case "liberar" -> {
                        switch (floors.unlock(n, List.of())) {
                            case OK -> {
                                msg.send(sender, "admin.liberado", andar);
                                log(sender, "andar.liberar", "andar " + n);
                            }
                            case ALREADY -> msg.send(sender, "admin.ja_liberado", andar);
                            case CANCELLED -> msg.send(sender, "admin.cancelado");
                            default -> msg.send(sender, "andar.desconhecido", andar);
                        }
                    }
                    case "bloquear" -> {
                        if (floors.lock(n)) {
                            msg.send(sender, "admin.bloqueado", andar);
                            log(sender, "andar.bloquear", "andar " + n);
                        } else {
                            msg.send(sender, n == 1 ? "admin.andar1" : "andar.bloqueado", andar);
                        }
                    }
                    case "setspawn" -> {
                        if (!(sender instanceof Player p)) {
                            msg.send(sender, "geral.so_jogador");
                            return;
                        }
                        try {
                            floors.setSpawn(n, p.getLocation());
                        } catch (IOException e) {
                            msg.send(sender, "geral.erro");
                            plugin.getLogger().warning("Não foi possível gravar floors.yml: " + e.getMessage());
                            return;
                        }
                        msg.send(p, "admin.setspawn", andar);
                        Floor at = floors.floorAt(p.getLocation());
                        if (f.isOn(floors.server()) && (at == null || at.number() != n)) {
                            msg.send(p, "admin.outro_lugar", andar);
                        }
                        log(sender, "andar.setspawn", "andar " + n + " em " + FloorService.formatSpawn(p.getLocation()));
                    }
                    case "info" -> msg.send(sender, "admin.info", Messages.vars("andar", f.name(),
                            "estado", floors.isUnlocked(n) ? "liberado" : "bloqueado",
                            "servidor", f.server().isEmpty() ? floors.server() : f.server(),
                            "tipo", f.byRegion() ? "região " + f.region() : "mundo " + f.world(),
                            "cidade", f.city() == null ? "—" : f.city(), "chegada", f.spawn() == null ? "—" : f.spawn()));
                    default -> {
                        Player t = Bukkit.getPlayerExact(args[1]);
                        if (t == null) {
                            msg.send(sender, "admin.offline");
                            return;
                        }
                        boolean on = op.equals("conceder");
                        floors.store().grant(t.getUniqueId(), n, on);
                        msg.send(sender, on ? "admin.concedido" : "admin.revogado", Messages.vars("jogador", t.getName(), "andar", f.name()));
                        log(sender, "andar." + op, t.getName() + " andar " + n);
                    }
                }
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                if (args.length == 1) {
                    return filter(List.of("liberar", "bloquear", "setspawn", "info", "conceder", "revogar"), args[0]);
                }
                boolean perPlayer = args[0].equalsIgnoreCase("conceder") || args[0].equalsIgnoreCase("revogar");
                if (args.length == 2 && perPlayer) {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    return filter(names, args[1]);
                }
                return args.length == (perPlayer ? 3 : 2) ? numbers(args[args.length - 1]) : List.of();
            }
        };
    }

    // ------------------------------------------------------------------ /aincrad gate

    public SubCommand gateAdmin() {
        return new SubCommand() {
            @Override public String name() { return "gate"; }
            @Override public String permission() { return "aincrad.admin"; }
            @Override public String usage() { return "gate criar <n>, remover, listar"; }
            @Override public Component description() { return msg.get("admin.descricao_gate"); }

            @Override
            public void execute(CommandSender sender, String[] args) {
                String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                if (op.equals("listar")) {
                    Map<String, Integer> all = new TreeMap<>(floors.store().gates());
                    List<String> out = new ArrayList<>();
                    all.forEach((ref, n) -> out.add(ref + " → " + n));
                    msg.send(sender, "admin.gate_lista", Messages.vars("n", all.size(), "lista", out.isEmpty() ? "—" : String.join("; ", out)));
                    return;
                }
                if (!(sender instanceof Player p)) {
                    msg.send(sender, "geral.so_jogador");
                    return;
                }
                Block b = p.getTargetBlockExact(5);
                if (b == null) {
                    msg.send(p, "admin.olhe_bloco");
                    return;
                }
                if (op.equals("remover")) {
                    boolean ok = gates.remove(b);
                    msg.send(p, ok ? "admin.gate_removido" : "admin.gate_nada");
                    if (ok) {
                        log(sender, "gate.remover", gates.ref(b));
                    }
                    return;
                }
                Integer n = op.equals("criar") && args.length > 1 ? number(args[1]) : null;
                if (n == null) {
                    msg.send(p, "admin.uso_gate");
                    return;
                }
                Floor f = floors.floor(n);
                if (f == null) {
                    msg.send(p, "andar.desconhecido", Messages.vars("n", n));
                    return;
                }
                gates.create(b, n);
                msg.send(p, "admin.gate_criado", Messages.vars("andar", f.name(), "bloco", b.getX() + ", " + b.getY() + ", " + b.getZ()));
                log(sender, "gate.criar", gates.ref(b) + " andar " + n);
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                if (args.length == 1) {
                    return filter(List.of("criar", "remover", "listar"), args[0]);
                }
                return args.length == 2 && args[0].equalsIgnoreCase("criar") ? numbers(args[1]) : List.of();
            }
        };
    }
}
