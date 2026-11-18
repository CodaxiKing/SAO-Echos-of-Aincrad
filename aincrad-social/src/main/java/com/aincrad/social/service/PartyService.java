package com.aincrad.social.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Parties em memória (somem quando o servidor reinicia, como uma sessão de jogo). Só lógica:
 * quem chama cuida das mensagens e notificações.
 */
public final class PartyService {

    /** Uma party. O primeiro de {@code members} não é necessariamente o líder. */
    public static final class Party {
        private final UUID id;
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        Party(UUID leader) {
            this(UUID.randomUUID(), leader);
        }

        Party(UUID id, UUID leader) {
            this.id = id;
            this.leader = leader;
            members.add(leader);
        }

        public UUID id() {
            return id;
        }

        public UUID leader() {
            return leader;
        }

        /** Membros na ordem de entrada (o líder incluído). */
        public List<UUID> members() {
            return List.copyOf(members);
        }

        public int size() {
            return members.size();
        }

        public boolean has(UUID p) {
            return members.contains(p);
        }
    }

    public enum InviteResult { SENT, SELF, NOT_LEADER, ALREADY_IN_YOUR_PARTY, TARGET_IN_PARTY, FULL, ALREADY_INVITED }

    public enum JoinResult { JOINED, NO_INVITE, EXPIRED, ALREADY_IN_PARTY, FULL }

    public enum LeaveKind { NOT_IN_PARTY, LEFT, DISBANDED }

    /**
     * Resultado de sair/ser expulso.
     *
     * @param remaining quem continua na party (ou quem estava, se ela acabou)
     * @param newLeader novo líder, se o líder saiu (senão null)
     */
    public record Leave(LeaveKind kind, List<UUID> remaining, UUID newLeader) {
    }

    public enum KickResult { KICKED, NOT_IN_PARTY, NOT_LEADER, NOT_MEMBER, SELF }

    private final Map<UUID, Party> byMember = new HashMap<>();
    /** convidado → (quem convidou → expira em) */
    private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();
    private final LongSupplier clock;
    private int maxSize;
    private long inviteTtlMillis;

    public PartyService(int maxSize, long inviteTtlMillis, LongSupplier clock) {
        this.maxSize = maxSize;
        this.inviteTtlMillis = inviteTtlMillis;
        this.clock = clock;
    }

    public void configure(int maxSize, long inviteTtlMillis) {
        this.maxSize = maxSize;
        this.inviteTtlMillis = inviteTtlMillis;
    }

    public int maxSize() {
        return maxSize;
    }

    public Party partyOf(UUID p) {
        return byMember.get(p);
    }

    public boolean sameParty(UUID a, UUID b) {
        Party pa = byMember.get(a);
        return pa != null && pa.has(b);
    }

    public InviteResult invite(UUID from, UUID to) {
        if (from.equals(to)) {
            return InviteResult.SELF;
        }
        Party p = byMember.get(from);
        if (p != null && !p.leader().equals(from)) {
            return InviteResult.NOT_LEADER;
        }
        Party target = byMember.get(to);
        if (target != null) {
            return target == p ? InviteResult.ALREADY_IN_YOUR_PARTY : InviteResult.TARGET_IN_PARTY;
        }
        if (p != null && p.size() >= maxSize) {
            return InviteResult.FULL;
        }
        Map<UUID, Long> mine = invites.computeIfAbsent(to, k -> new LinkedHashMap<>());
        Long exp = mine.get(from);
        long now = clock.getAsLong();
        if (exp != null && exp > now) {
            return InviteResult.ALREADY_INVITED;
        }
        mine.put(from, now + inviteTtlMillis);
        return InviteResult.SENT;
    }

    /** Quem convidou {@code p} (convites ainda válidos, mais antigos primeiro). */
    public List<UUID> invitesFor(UUID p) {
        Map<UUID, Long> mine = invites.get(p);
        if (mine == null) {
            return List.of();
        }
        long now = clock.getAsLong();
        mine.values().removeIf(exp -> exp <= now);
        if (mine.isEmpty()) {
            invites.remove(p);
            return List.of();
        }
        return List.copyOf(mine.keySet());
    }

    public JoinResult accept(UUID me, UUID from) {
        Map<UUID, Long> mine = invites.get(me);
        Long exp = mine == null ? null : mine.get(from);
        if (exp == null) {
            return JoinResult.NO_INVITE;
        }
        if (exp <= clock.getAsLong()) {
            mine.remove(from);
            return JoinResult.EXPIRED;
        }
        if (byMember.containsKey(me)) {
            return JoinResult.ALREADY_IN_PARTY;
        }
        Party p = byMember.get(from);
        if (p == null) {
            p = new Party(from);
            byMember.put(from, p);
        }
        if (p.size() >= maxSize) {
            return JoinResult.FULL;
        }
        p.members.add(me);
        byMember.put(me, p);
        invites.remove(me);
        return JoinResult.JOINED;
    }

    public boolean decline(UUID me, UUID from) {
        Map<UUID, Long> mine = invites.get(me);
        boolean had = mine != null && mine.remove(from) != null;
        if (mine != null && mine.isEmpty()) {
            invites.remove(me);
        }
        return had;
    }

    public Leave leave(UUID me) {
        Party p = byMember.get(me);
        if (p == null) {
            return new Leave(LeaveKind.NOT_IN_PARTY, List.of(), null);
        }
        return removeMember(p, me);
    }

    private Leave removeMember(Party p, UUID who) {
        p.members.remove(who);
        byMember.remove(who);
        if (p.size() < 2) {
            List<UUID> rest = new ArrayList<>(p.members);
            for (UUID m : rest) {
                byMember.remove(m);
            }
            p.members.clear();
            return new Leave(LeaveKind.DISBANDED, rest, null);
        }
        UUID newLeader = null;
        if (p.leader.equals(who)) {
            p.leader = p.members.iterator().next();
            newLeader = p.leader;
        }
        return new Leave(LeaveKind.LEFT, p.members(), newLeader);
    }

    public KickResult kick(UUID leader, UUID target) {
        Party p = byMember.get(leader);
        if (p == null) {
            return KickResult.NOT_IN_PARTY;
        }
        if (!p.leader().equals(leader)) {
            return KickResult.NOT_LEADER;
        }
        if (leader.equals(target)) {
            return KickResult.SELF;
        }
        if (!p.has(target)) {
            return KickResult.NOT_MEMBER;
        }
        removeMember(p, target);
        return KickResult.KICKED;
    }

    /** O líder passa a liderança para outro membro. */
    public boolean promote(UUID leader, UUID target) {
        Party p = byMember.get(leader);
        if (p == null || !p.leader().equals(leader) || !p.has(target) || leader.equals(target)) {
            return false;
        }
        p.leader = target;
        return true;
    }

    /** O líder desfaz a party. @return quem estava nela (vazio se não pôde) */
    public List<UUID> disband(UUID leader) {
        Party p = byMember.get(leader);
        if (p == null || !p.leader().equals(leader)) {
            return List.of();
        }
        List<UUID> all = p.members();
        for (UUID m : all) {
            byMember.remove(m);
        }
        p.members.clear();
        return all;
    }

    // ------------------------------------------------------------------ rede

    /** "líder|m1,m2": o estado de uma party em texto, para comparar e mandar aos outros servidores. */
    public static String encode(UUID leader, List<UUID> members) {
        StringBuilder sb = new StringBuilder(leader.toString()).append('|');
        for (int i = 0; i < members.size(); i++) {
            sb.append(i == 0 ? "" : ",").append(members.get(i));
        }
        return sb.toString();
    }

    /** Todas as parties (id → estado em texto). */
    public Map<UUID, String> snapshot() {
        Map<UUID, String> out = new HashMap<>();
        for (Party p : new LinkedHashSet<>(byMember.values())) {
            out.put(p.id, encode(p.leader, p.members()));
        }
        return out;
    }

    /** Põe aqui uma party como outro servidor a descreveu (cria ou troca). */
    public void apply(UUID id, UUID leader, List<UUID> members) {
        remove(id);
        Party p = new Party(id, leader);
        // A ordem dos membros é a de quem mandou (o líder não vai para a frente): os dois
        // servidores ficam com o mesmo estado e não há o que reenviar.
        p.members.clear();
        for (UUID m : members) {
            Party other = byMember.get(m);
            if (other != null) {
                // Quem estava em outra party aqui saiu dela lá.
                other.members.remove(m);
            }
            p.members.add(m);
            byMember.put(m, p);
            invites.remove(m);
        }
        if (!p.members.contains(leader)) {
            p.leader = p.members.iterator().next();
        }
    }

    /** Tira a party (acabou em outro servidor). */
    public void remove(UUID id) {
        for (Party p : new LinkedHashSet<>(byMember.values())) {
            if (p.id.equals(id)) {
                for (UUID m : p.members) {
                    byMember.remove(m, p);
                }
                p.members.clear();
            }
        }
    }

    /** Todas as parties ativas (para depuração e testes). */
    public Set<Party> parties() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(byMember.values()));
    }
}
