package com.aincrad.bosses.raid;

import com.aincrad.bosses.fight.FightManager;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.Raid;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.floors.api.AincradFloorsAPI;
import net.kyori.adventure.inventory.Book;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Reunião de estratégia: antes de um chefe, o líder da raid convoca os inscritos a um ponto da
 * cidade. Quem aparece recebe a função, vê as páginas do guia que o líder tem e fica marcado como
 * "informado" na raid.
 */
public final class MeetingService {

    /** Por que a reunião não pode ser convocada (null = pode). */
    public enum Block { NAO_E_LIDER, FORA_DA_CIDADE, JA_TEM, RAID_PASSOU }

    public static Block blocked(boolean leader, boolean inCity, boolean running, long raidAt, long now) {
        if (!leader) {
            return Block.NAO_E_LIDER;
        }
        if (running) {
            return Block.JA_TEM;
        }
        if (raidAt <= now) {
            return Block.RAID_PASSOU;
        }
        return inCity ? null : Block.FORA_DA_CIDADE;
    }

    /** Uma reunião em andamento. */
    private static final class Meeting {
        final Location at;
        final long until;
        final UUID leader;

        Meeting(Location at, long until, UUID leader) {
            this.at = at;
            this.until = until;
            this.leader = leader;
        }
    }

    private final Messages msg;
    private final FightManager fights;
    private final RaidService raids;
    private final ConfigFile config;
    private final BiFunction<BossDef, Player, Book> leaderBook;
    private final Map<Long, Meeting> meetings = new HashMap<>();
    private final Map<Long, Set<UUID>> briefed = new HashMap<>();

    /** @param leaderBook monta o guia do chefe com as páginas que o jogador (o líder) tem */
    public MeetingService(Messages msg, FightManager fights, RaidService raids, ConfigFile config, BiFunction<BossDef, Player, Book> leaderBook) {
        this.msg = msg;
        this.fights = fights;
        this.raids = raids;
        this.config = config;
        this.leaderBook = leaderBook;
    }

    private double radius() {
        return config.decimal("reuniao.raio", 16, 4, 64);
    }

    /** O jogador esteve na reunião desta raid? */
    public boolean briefed(long raid, UUID player) {
        Set<UUID> s = briefed.get(raid);
        return s != null && s.contains(player);
    }

    /** Os nomes dos inscritos que estiveram na reunião. */
    public List<String> briefedNames(Raid r) {
        List<String> out = new ArrayList<>();
        Set<UUID> s = briefed.getOrDefault(r.id(), Set.of());
        r.members().forEach((id, m) -> {
            if (s.contains(id)) {
                out.add(m.name());
            }
        });
        return out;
    }

    /** {@code /raid reuniao <n>}: o líder convoca a reunião onde está. */
    public void call(Player leader, Raid r) {
        boolean inCity;
        try {
            inCity = !config.bool("reuniao.so_na_cidade", true) || AincradFloorsAPI.isSafeZone(leader.getLocation());
        } catch (RuntimeException e) {
            inCity = true;
        }
        long now = System.currentTimeMillis();
        Block b = blocked(leader.getUniqueId().equals(r.leader()), inCity, meetings.containsKey(r.id()), r.at(), now);
        if (b != null) {
            msg.send(leader, "reuniao.bloqueio." + b.name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        int minutes = config.integer("reuniao.minutos", 5, 1, 60);
        meetings.put(r.id(), new Meeting(leader.getLocation().clone(), now + minutes * 60_000L, leader.getUniqueId()));
        briefed.computeIfAbsent(r.id(), k -> new HashSet<>());
        BossDef def = fights.registry().get(r.boss());
        Location l = leader.getLocation();
        var vars = Messages.vars("n", r.id(), "chefe", def == null ? r.boss() : def.name(), "lider", leader.getName(), "minutos", minutes,
                "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ());
        msg.send(leader, "reuniao.convocada", vars);
        for (UUID id : r.members().keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.equals(leader)) {
                msg.send(p, "reuniao.convite", vars);
            }
        }
    }

    /** A cada 2 segundos: quem chegou ao ponto da reunião é informado; a reunião que venceu acaba. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (var it = meetings.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Raid r = raids.all().get(en.getKey());
            Meeting m = en.getValue();
            Player leader = Bukkit.getPlayer(m.leader);
            if (r == null || m.until <= now) {
                it.remove();
                if (r != null && leader != null) {
                    List<String> names = briefedNames(r);
                    msg.send(leader, "reuniao.acabou", Messages.vars("n", r.id(), "qtd", names.size(), "total", r.members().size(),
                            "nomes", names.isEmpty() ? "—" : String.join(", ", names)));
                }
                continue;
            }
            BossDef def = fights.registry().get(r.boss());
            Set<UUID> seen = briefed.computeIfAbsent(r.id(), k -> new HashSet<>());
            for (var member : r.members().entrySet()) {
                Player p = Bukkit.getPlayer(member.getKey());
                if (p == null || seen.contains(p.getUniqueId()) || !p.getWorld().equals(m.at.getWorld())
                        || p.getLocation().distanceSquared(m.at) > radius() * radius()) {
                    continue;
                }
                seen.add(p.getUniqueId());
                msg.send(p, "reuniao.informado", Messages.vars("chefe", def == null ? r.boss() : def.name(),
                        "funcao", msg.raw("raid.funcao." + member.getValue().role().key())));
                // O guia do líder, uma vez: o que ele comprou ou viu, todos os presentes veem.
                if (def != null && leader != null && !p.equals(leader)) {
                    p.openBook(leaderBook.apply(def, leader));
                }
            }
        }
        briefed.keySet().removeIf(id -> !raids.all().containsKey(id));
    }
}
