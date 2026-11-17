package com.aincrad.life.command;

import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import com.aincrad.life.inn.Inn;
import com.aincrad.life.inn.InnService;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.station.PanelManager;
import com.aincrad.life.station.StationPanel;
import com.aincrad.life.station.StationService;
import com.aincrad.life.station.StationType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Os subcomandos de admin do AincradLife em {@code /aincrad}: estacao, saber, arvore, profissao e estalagem. */
public final class LifeCommands {

    private final Plugin plugin;
    private final Messages msg;
    private final ProfessionService professions;
    private final StationService stations;
    private final PanelManager panels;
    private final Map<UUID, BukkitTask> tests = new HashMap<>();

    public LifeCommands(Plugin plugin, ProfessionService professions, StationService stations, PanelManager panels) {
        this.plugin = plugin;
        this.msg = professions.messages();
        this.professions = professions;
        this.stations = stations;
        this.panels = panels;
    }

    private void log(CommandSender actor, String action, String detail) {
        Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(actor, "life", action, detail));
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

    private static List<String> players() {
        List<String> out = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        return out;
    }

    private static List<String> professionKeys() {
        List<String> out = new ArrayList<>();
        for (Profession p : Profession.values()) {
            out.add(p.key());
        }
        return out;
    }

    private Player online(CommandSender sender, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) {
            msg.send(sender, "admin.offline");
        }
        return t;
    }

    private SubCommand sub(String name, String usage, String descKey, Exec exec, Complete complete) {
        return new SubCommand() {
            @Override public String name() { return name; }
            @Override public String permission() { return "aincrad.admin"; }
            @Override public String usage() { return usage; }
            @Override public Component description() { return msg.get(descKey); }
            @Override public void execute(CommandSender sender, String[] args) { exec.run(sender, args); }
            @Override public List<String> complete(CommandSender sender, String[] args) { return complete.run(args); }
        };
    }

    private interface Exec {
        void run(CommandSender sender, String[] args);
    }

    private interface Complete {
        List<String> run(String[] args);
    }

    // ------------------------------------------------------------------ /aincrad estacao

    public SubCommand station() {
        return sub("estacao", "estacao criar <tipo> | remover | listar | teste", "admin.descricao_estacao", (sender, args) -> {
            String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if (op.equals("listar")) {
                List<String> out = new ArrayList<>();
                stations.placed().values().forEach(p -> out.add("#" + p.id + " " + p.type.id() + " (" + p.world + " " + p.x + ", " + p.y + ", " + p.z + ")"));
                msg.send(sender, "estacao.lista", Messages.vars("tipos", String.join(", ", stations.types().keySet()),
                        "postas", out.isEmpty() ? "—" : String.join("; ", out)));
                return;
            }
            if (!(sender instanceof Player p)) {
                msg.send(sender, "geral.so_jogador");
                return;
            }
            switch (op) {
                case "criar" -> {
                    StationType type = args.length > 1 ? stations.types().get(args[1].toLowerCase(Locale.ROOT)) : null;
                    if (type == null) {
                        msg.send(p, "estacao.tipo_desconhecido", Messages.vars("tipos", String.join(", ", stations.types().keySet())));
                        return;
                    }
                    Block target = p.getTargetBlockExact(5);
                    Block at = target == null ? null : target.getRelative(BlockFace.UP);
                    if (at == null || !at.getType().isAir() || stations.at(at) != null) {
                        msg.send(p, "estacao.sem_lugar");
                        return;
                    }
                    stations.create(p, type, at);
                    log(sender, "estacao.criar", type.id() + " em " + at.getWorld().getName() + " " + at.getX() + "," + at.getY() + "," + at.getZ());
                }
                case "remover" -> {
                    StationService.Placed st = stations.lookingAt(p);
                    if (st == null) {
                        msg.send(p, "estacao.nenhuma");
                        return;
                    }
                    stations.remove(st);
                    msg.send(p, "estacao.removida", Messages.vars("tipo", st.type.name()));
                    log(sender, "estacao.remover", "#" + st.id + " " + st.type.id());
                }
                case "teste" -> testPanel(p);
                default -> msg.send(p, "admin.uso_estacao");
            }
        }, args -> switch (args.length) {
            case 1 -> filter(List.of("criar", "remover", "listar", "teste"), args[0]);
            case 2 -> args[0].equalsIgnoreCase("criar") ? filter(new ArrayList<>(stations.types().keySet()), args[1]) : List.of();
            default -> List.of();
        });
    }

    /**
     * Painel de teste sobre a bancada que o jogador olha: 3 encaixes, uma barra e uma marca de
     * batida a cada 3 s, dizendo se o clique foi no tempo. Fecha sozinho em 60 s (ou repetindo o comando).
     */
    private void testPanel(Player p) {
        BukkitTask old = tests.remove(p.getUniqueId());
        if (old != null) {
            old.cancel();
            panels.close(p, true);
            msg.send(p, "painel.teste_fim");
            return;
        }
        StationService.Placed st = stations.lookingAt(p);
        if (st == null) {
            msg.send(p, "estacao.nenhuma");
            return;
        }
        StationPanel panel = panels.open(p, st.base(), st.yaw, st.type.hologram());
        List<double[]> slots = st.type.slots().isEmpty()
                ? List.of(new double[]{-0.7, 1.5, 0}, new double[]{0, 1.7, 0}, new double[]{0.7, 1.5, 0}) : st.type.slots();
        for (int i = 0; i < Math.min(3, slots.size()); i++) {
            panel.addSlot(msg.raw("painel.teste_encaixe") + " " + (i + 1), slots.get(i), i < 2, it -> true);
        }
        panel.markPoints(st.type.marks());
        String[] last = {msg.raw("painel.teste_espera")};
        Supplier<List<Component>> lines = () -> {
            long filled = panel.slots().stream().filter(s -> s.item() != null).count();
            return List.of(msg.get("painel.teste_titulo"),
                    Messages.parse("<gray>" + msg.raw("painel.teste_encaixes") + " " + StationPanel.bar(filled / (double) panel.slots().size(), 10)
                            + " <white>" + filled + " / " + panel.slots().size()),
                    Messages.parse(last[0]));
        };
        panel.hologram(lines.get());
        panel.onSlotChange(() -> panel.hologram(lines.get()));
        if (panel.simple()) {
            panels.openSlotMenu(p, msg.get("painel.teste_titulo"), pl -> { });
        }
        int[] ticks = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks[0] += 20;
            if (panel.closed() || ticks[0] > 60 * 20) {
                BukkitTask t = tests.remove(p.getUniqueId());
                if (t != null) {
                    t.cancel();
                }
                panel.close(true);
                return;
            }
            if (ticks[0] % 60 == 0 && !panel.markActive()) {
                panel.mark(ThreadLocalRandom.current().nextInt(panel.markCount()), 30, 2, 6, hit -> {
                    last[0] = msg.raw("painel.batida_" + hit.name().toLowerCase(Locale.ROOT));
                    panel.hologram(lines.get());
                });
            }
        }, 20L, 20L);
        tests.put(p.getUniqueId(), task);
        msg.send(p, "painel.teste_inicio");
    }

    // ------------------------------------------------------------------ /aincrad saber

    public SubCommand knowledge() {
        return sub("saber", "saber dar|ensinar|esquecer <jogador> <tipo> <id> | pergaminho <jogador> [qtd] | lista", "admin.descricao_saber", (sender, args) -> {
            String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if (op.equals("lista")) {
                List<String> out = new ArrayList<>();
                professions.learnables().forEach(l -> out.add(l.kind() + " " + l.id()));
                msg.send(sender, "saber.lista", Messages.vars("lista", out.isEmpty() ? "—" : String.join(", ", out)));
                return;
            }
            Player t = args.length > 1 ? online(sender, args[1]) : null;
            if (args.length < 2 || t == null) {
                if (args.length < 2) {
                    msg.send(sender, "admin.uso_saber");
                }
                return;
            }
            if (op.equals("pergaminho")) {
                int n = 1;
                try {
                    n = args.length > 2 ? Math.max(1, Math.min(64, Integer.parseInt(args[2]))) : 1;
                } catch (NumberFormatException e) {
                    // fica 1
                }
                t.getInventory().addItem(professions.blankScroll(n)).values().forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
                msg.send(sender, "saber.pergaminho_dado", Messages.vars("qtd", n, "jogador", t.getName()));
                return;
            }
            Learnable l = args.length > 3 ? professions.learnable(args[2], args[3]) : null;
            if (l == null || !List.of("dar", "ensinar", "esquecer").contains(op)) {
                msg.send(sender, l == null && args.length > 3 ? "saber.desconhecido" : "admin.uso_saber");
                return;
            }
            var vars = Messages.vars("nome", l.name(), "jogador", t.getName());
            switch (op) {
                case "dar" -> {
                    t.getInventory().addItem(professions.scroll(l)).values().forEach(r -> t.getWorld().dropItemNaturally(t.getLocation(), r));
                    msg.send(sender, "saber.item_dado", vars);
                }
                case "ensinar" -> {
                    professions.teach(t, l);
                    msg.send(sender, "saber.ensinado", vars);
                }
                default -> {
                    professions.forget(t, l);
                    msg.send(sender, "saber.esquecido", vars);
                }
            }
            log(sender, "saber." + op, t.getName() + " " + l.key());
        }, args -> {
            if (args.length == 1) {
                return filter(List.of("dar", "ensinar", "esquecer", "pergaminho", "lista"), args[0]);
            }
            if (args.length == 2) {
                return filter(players(), args[1]);
            }
            List<String> out = new ArrayList<>();
            if (args.length == 3) {
                professions.learnables().forEach(l -> {
                    if (!out.contains(l.kind())) {
                        out.add(l.kind());
                    }
                });
            } else if (args.length == 4) {
                professions.learnables().forEach(l -> {
                    if (l.kind().equalsIgnoreCase(args[2])) {
                        out.add(l.id());
                    }
                });
            }
            return filter(out, args[args.length - 1]);
        });
    }

    // ------------------------------------------------------------------ /aincrad arvore e profissao

    public SubCommand tree() {
        return sub("arvore", "arvore resetar <jogador> <profissão>", "admin.descricao_arvore", (sender, args) -> {
            Player t = args.length > 2 && args[0].equalsIgnoreCase("resetar") ? online(sender, args[1]) : null;
            Profession prof = args.length > 2 ? Profession.parse(args[2]) : null;
            if (args.length < 3 || !args[0].equalsIgnoreCase("resetar") || prof == null) {
                msg.send(sender, "admin.uso_arvore");
                return;
            }
            if (t == null) {
                return;
            }
            professions.resetTree(t, prof);
            msg.send(sender, "arvore.resetada", Messages.vars("jogador", t.getName(), "profissao", professions.name(prof)));
            msg.send(t, "arvore.resetada_voce", Messages.vars("profissao", professions.name(prof)));
            log(sender, "arvore.resetar", t.getName() + " " + prof.key());
        }, args -> switch (args.length) {
            case 1 -> filter(List.of("resetar"), args[0]);
            case 2 -> filter(players(), args[1]);
            case 3 -> filter(professionKeys(), args[2]);
            default -> List.of();
        });
    }

    public SubCommand profession() {
        return sub("profissao", "profissao definir <jogador> <profissão> <0-1000> | ver <jogador>", "admin.descricao_profissao", (sender, args) -> {
            String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            Player t = args.length > 1 ? online(sender, args[1]) : null;
            if (args.length < 2 || !List.of("definir", "ver").contains(op)) {
                msg.send(sender, "admin.uso_profissao");
                return;
            }
            if (t == null) {
                return;
            }
            if (op.equals("ver")) {
                for (Profession prof : Profession.values()) {
                    msg.send(sender, "admin.profissao_ver", Messages.vars("jogador", t.getName(), "profissao", professions.name(prof),
                            "valor", (int) professions.proficiency(t, prof), "rank", msg.raw("rank." + professions.rank(t, prof).key()),
                            "livres", professions.freePoints(t, prof)));
                }
                return;
            }
            Profession prof = args.length > 3 ? Profession.parse(args[2]) : null;
            double value;
            try {
                value = args.length > 3 ? Double.parseDouble(args[3]) : -1;
            } catch (NumberFormatException e) {
                value = -1;
            }
            if (prof == null || value < 0) {
                msg.send(sender, "admin.uso_profissao");
                return;
            }
            professions.setProficiency(t, prof, value, false);
            msg.send(sender, "admin.profissao_definida", Messages.vars("jogador", t.getName(), "profissao", professions.name(prof),
                    "valor", (int) professions.proficiency(t, prof)));
            log(sender, "profissao.definir", t.getName() + " " + prof.key() + " " + (int) value);
        }, args -> switch (args.length) {
            case 1 -> filter(List.of("definir", "ver"), args[0]);
            case 2 -> filter(players(), args[1]);
            case 3 -> args[0].equalsIgnoreCase("definir") ? filter(professionKeys(), args[2]) : List.of();
            default -> List.of();
        });
    }

    // ------------------------------------------------------------------ /aincrad estalagem

    public SubCommand inn(InnService inns) {
        return sub("estalagem", "estalagem balcao <id> | removerbalcao | cama <estalagem> <quarto> | listar | despejar <jogador>", "admin.descricao_estalagem", (sender, args) -> {
            String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if (op.equals("listar")) {
                List<String> out = new ArrayList<>();
                inns.inns().values().forEach(i -> out.add(i.id() + " (" + i.rooms().size() + " quartos, andar " + i.floor() + ")"));
                msg.send(sender, "estalagem.lista", Messages.vars("lista", out.isEmpty() ? "—" : String.join("; ", out)));
                return;
            }
            if (op.equals("despejar") && args.length > 1) {
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
                Inn.Rental r = t == null ? null : inns.of(t.getUniqueId());
                if (r == null) {
                    msg.send(sender, "estalagem.sem_quarto");
                    return;
                }
                inns.checkout(r);
                msg.send(sender, "estalagem.despejado", Messages.vars("jogador", args[1]));
                log(sender, "estalagem.despejar", args[1] + " " + r.inn + "/" + r.room);
                return;
            }
            if (!(sender instanceof Player p)) {
                msg.send(sender, "geral.so_jogador");
                return;
            }
            switch (op) {
                case "balcao" -> {
                    Inn inn = args.length > 1 ? inns.inns().get(args[1].toLowerCase(Locale.ROOT)) : null;
                    if (inn == null) {
                        msg.send(p, "estalagem.desconhecida");
                        return;
                    }
                    Entity e = p.getTargetEntity(5);
                    Block b = p.getTargetBlockExact(5);
                    if (e != null && !(e instanceof Player)) {
                        inns.link(InnService.entityRef(e), inn.id());
                    } else if (b != null) {
                        inns.link(inns.blockRef(b), inn.id());
                    } else {
                        msg.send(p, "estalagem.olhe");
                        return;
                    }
                    msg.send(p, "estalagem.balcao_criado", Messages.vars("estalagem", inn.name()));
                    log(sender, "estalagem.balcao", inn.id());
                }
                case "removerbalcao" -> {
                    Entity e = p.getTargetEntity(5);
                    Block b = p.getTargetBlockExact(5);
                    boolean ok = (e != null && inns.unlink(InnService.entityRef(e))) || (b != null && inns.unlink(inns.blockRef(b)));
                    msg.send(p, ok ? "estalagem.balcao_removido" : "estalagem.olhe");
                }
                case "cama" -> {
                    try {
                        if (args.length < 3 || !inns.setBed(args[1], args[2], p.getLocation())) {
                            msg.send(p, "estalagem.desconhecida");
                            return;
                        }
                    } catch (IOException e) {
                        msg.send(p, "geral.erro");
                        plugin.getLogger().warning("Não foi possível gravar inns.yml: " + e.getMessage());
                        return;
                    }
                    msg.send(p, "estalagem.cama_definida", Messages.vars("quarto", args[2]));
                }
                default -> msg.send(p, "admin.uso_estalagem");
            }
        }, args -> switch (args.length) {
            case 1 -> filter(List.of("balcao", "removerbalcao", "cama", "listar", "despejar"), args[0]);
            case 2 -> args[0].equalsIgnoreCase("despejar") ? filter(players(), args[1])
                    : args[0].equalsIgnoreCase("balcao") || args[0].equalsIgnoreCase("cama") ? filter(new ArrayList<>(inns.inns().keySet()), args[1]) : List.of();
            case 3 -> {
                Inn inn = args[0].equalsIgnoreCase("cama") ? inns.inns().get(args[1].toLowerCase(Locale.ROOT)) : null;
                yield inn == null ? List.of() : filter(new ArrayList<>(inn.rooms().keySet()), args[2]);
            }
            default -> List.of();
        });
    }

    /** Ao desligar: encerra os painéis de teste. */
    public void shutdown() {
        tests.values().forEach(BukkitTask::cancel);
        tests.clear();
    }
}
