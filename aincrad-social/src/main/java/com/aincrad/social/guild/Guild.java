package com.aincrad.social.guild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Uma guilda. Alterada só pelo {@link GuildService}. */
public final class Guild {

    public enum Recruit { CONVITE, CANDIDATURA, ABERTA }

    /** Um membro: cargo, quando entrou e quando foi visto pela última vez (ms). */
    public static final class Member {
        GuildRank rank;
        final long joined;
        long lastSeen;

        Member(GuildRank rank, long joined) {
            this.rank = rank;
            this.joined = joined;
            this.lastSeen = joined;
        }

        public GuildRank rank() {
            return rank;
        }

        public long joined() {
            return joined;
        }

        public long lastSeen() {
            return lastSeen;
        }
    }

    /**
     * Linha do extrato (tesouro) e do histórico do cofre.
     *
     * @param type deposito, saque, taxa, cofre_entrada, cofre_saida, compra...
     */
    public record Ledger(long time, UUID who, String whoName, String type, long amount, String detail) {
    }

    final UUID id;
    String name;
    String tag;
    String emblem;
    String color1;
    String color2;
    final long created;
    int level = 1;
    long xp;
    long treasury;
    int taxPercent;
    Recruit recruit = Recruit.CONVITE;
    final Map<UUID, Member> members = new LinkedHashMap<>();
    final Map<GuildPerm, GuildRank> perms = new EnumMap<>(GuildPerm.class);
    final Set<UUID> allies = new LinkedHashSet<>();
    final Set<UUID> allyProposals = new LinkedHashSet<>();
    final Map<UUID, Long> applications = new LinkedHashMap<>();
    final Map<UUID, Long> invites = new LinkedHashMap<>();
    final List<Ledger> ledger = new ArrayList<>();
    String home;
    int bossKills;
    int lastAttacks;
    int highestFloor;
    /** Itens do cofre (serializados pelo GuildVault; opaco aqui). */
    String vaultData = "";
    /** Col pago na criação (para o reembolso se a guilda não chegar ao mínimo de membros). */
    long creationCost;
    /** Já chegou ao mínimo de membros alguma vez (depois disso não é mais desfeita por falta deles). */
    boolean established;

    Guild(UUID id, String name, String tag, String emblem, long created) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.emblem = emblem;
        this.created = created;
        this.color1 = "#FFFFFF";
        this.color2 = "#C8102E";
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String tag() {
        return tag;
    }

    public String emblem() {
        return emblem;
    }

    public String color1() {
        return color1;
    }

    public String color2() {
        return color2;
    }

    public long created() {
        return created;
    }

    public int level() {
        return level;
    }

    public long xp() {
        return xp;
    }

    public long treasury() {
        return treasury;
    }

    public int taxPercent() {
        return taxPercent;
    }

    public Recruit recruit() {
        return recruit;
    }

    public Map<UUID, Member> members() {
        return Collections.unmodifiableMap(members);
    }

    public int size() {
        return members.size();
    }

    public boolean has(UUID p) {
        return members.containsKey(p);
    }

    public GuildRank rankOf(UUID p) {
        Member m = members.get(p);
        return m == null ? null : m.rank;
    }

    public UUID leader() {
        for (Map.Entry<UUID, Member> e : members.entrySet()) {
            if (e.getValue().rank == GuildRank.LIDER) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Membros com o cargo dado, mais antigos primeiro. */
    public List<UUID> withRank(GuildRank r) {
        List<UUID> out = new ArrayList<>();
        members.entrySet().stream()
                .filter(e -> e.getValue().rank == r)
                .sorted((a, b) -> Long.compare(a.getValue().joined, b.getValue().joined))
                .forEach(e -> out.add(e.getKey()));
        return out;
    }

    public GuildRank minRank(GuildPerm perm) {
        return perm.fixed() ? perm.defaultMin() : perms.getOrDefault(perm, perm.defaultMin());
    }

    public Set<UUID> allies() {
        return Collections.unmodifiableSet(allies);
    }

    public Set<UUID> allyProposals() {
        return Collections.unmodifiableSet(allyProposals);
    }

    public Map<UUID, Long> applications() {
        return Collections.unmodifiableMap(applications);
    }

    public List<Ledger> ledger() {
        return Collections.unmodifiableList(ledger);
    }

    public String home() {
        return home;
    }

    public int bossKills() {
        return bossKills;
    }

    public int lastAttacks() {
        return lastAttacks;
    }

    public int highestFloor() {
        return highestFloor;
    }

    public String vaultData() {
        return vaultData;
    }

    public long creationCost() {
        return creationCost;
    }
}
