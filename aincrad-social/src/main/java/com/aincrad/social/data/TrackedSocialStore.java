package com.aincrad.social.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link SocialStore} que anota cada mudança, para o banco gravar só o que mudou e avisar os
 * outros servidores de quais jogadores precisam ser relidos. Sem servidor nem banco aqui.
 */
public final class TrackedSocialStore extends SocialStore {

    public enum Kind { FRIEND_ADD, FRIEND_DEL, REQUEST_ADD, REQUEST_DEL, INBOX_SET, NAME }

    /**
     * Uma mudança a gravar. FRIEND_*: {@code a} e {@code b} são os dois amigos. REQUEST_*: {@code a}
     * pediu a {@code b}. INBOX_SET: a caixa inteira de {@code a}. NAME: {@code a} se chama {@code text}.
     */
    public record Op(Kind kind, UUID a, UUID b, String text, List<InboxMessage> inbox) {
    }

    /** O que mudou desde a última gravação, e de quem. */
    public record Changes(List<Op> ops, Set<UUID> players) {
        public boolean isEmpty() {
            return ops.isEmpty();
        }
    }

    private List<Op> ops = new ArrayList<>();
    private Set<UUID> touched = new LinkedHashSet<>();

    private void op(Kind kind, UUID a, UUID b, String text, List<InboxMessage> inbox) {
        ops.add(new Op(kind, a, b, text, inbox));
        touched.add(a);
        if (b != null) {
            touched.add(b);
        }
    }

    @Override
    public void addFriend(UUID a, UUID b) {
        super.addFriend(a, b);
        op(Kind.FRIEND_ADD, a, b, null, null);
    }

    @Override
    public void removeFriend(UUID a, UUID b) {
        super.removeFriend(a, b);
        op(Kind.FRIEND_DEL, a, b, null, null);
    }

    @Override
    public void addRequest(UUID from, UUID to) {
        super.addRequest(from, to);
        op(Kind.REQUEST_ADD, from, to, null, null);
    }

    @Override
    public void removeRequest(UUID from, UUID to) {
        super.removeRequest(from, to);
        op(Kind.REQUEST_DEL, from, to, null, null);
    }

    @Override
    public void setInbox(UUID p, List<InboxMessage> messages) {
        super.setInbox(p, messages);
        op(Kind.INBOX_SET, p, null, null, List.copyOf(messages));
    }

    @Override
    public void rememberName(UUID p, String name) {
        if (!name.equals(names.get(p))) {
            super.rememberName(p, name);
            op(Kind.NAME, p, null, name, null);
        }
    }

    /** Tira e devolve o que mudou desde a última chamada. */
    public Changes drain() {
        Changes c = new Changes(ops, touched);
        ops = new ArrayList<>();
        touched = new LinkedHashSet<>();
        clean();
        return c;
    }

    // ------------------------------------------------------------------ vindo do banco (não gera mudança)

    public void loadFriend(UUID a, UUID b) {
        friends.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b);
        friends.computeIfAbsent(b, k -> new LinkedHashSet<>()).add(a);
    }

    public void loadRequest(UUID from, UUID to) {
        requests.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
    }

    public void loadInbox(UUID owner, InboxMessage m) {
        inbox.computeIfAbsent(owner, k -> new ArrayList<>()).add(m);
    }

    public void loadName(UUID p, String name) {
        names.put(p, name);
    }

    /**
     * Outro servidor mudou os dados de {@code p}: troca o que está na memória pelo que veio do banco.
     *
     * @param newRequests quem pediu amizade a {@code p}
     * @param name        null mantém o nome conhecido
     */
    public void apply(UUID p, Set<UUID> newFriends, Set<UUID> newRequests, List<InboxMessage> newInbox, String name) {
        Set<UUID> old = friends.getOrDefault(p, Set.of());
        // A amizade vale para os dois lados: quem deixou de ser amigo perde p, quem virou ganha.
        for (UUID x : new ArrayList<>(old)) {
            if (!newFriends.contains(x)) {
                Set<UUID> theirs = friends.get(x);
                if (theirs != null) {
                    theirs.remove(p);
                    if (theirs.isEmpty()) {
                        friends.remove(x);
                    }
                }
            }
        }
        for (UUID x : newFriends) {
            friends.computeIfAbsent(x, k -> new LinkedHashSet<>()).add(p);
        }
        put(friends, p, newFriends);
        put(requests, p, newRequests);
        if (newInbox.isEmpty()) {
            inbox.remove(p);
        } else {
            inbox.put(p, new ArrayList<>(newInbox));
        }
        if (name != null) {
            names.put(p, name);
        }
    }

    private static void put(Map<UUID, Set<UUID>> map, UUID key, Set<UUID> value) {
        if (value.isEmpty()) {
            map.remove(key);
        } else {
            map.put(key, new LinkedHashSet<>(value));
        }
    }

    /** Copia tudo de um armazenamento antigo (dados.yml), anotando como mudança para ir ao banco. */
    public void importFrom(SocialStore other) {
        other.names.forEach(this::rememberName);
        other.friends.forEach((a, set) -> {
            for (UUID b : set) {
                if (a.compareTo(b) < 0 || !other.friends.getOrDefault(b, Set.of()).contains(a)) {
                    addFriend(a, b);
                }
            }
        });
        other.requests.forEach((to, set) -> set.forEach(from -> addRequest(from, to)));
        other.inbox.forEach(this::setInbox);
    }

    /** Quantas mensagens não lidas {@code p} tem. */
    public int unread(UUID p) {
        int n = 0;
        for (InboxMessage m : inbox.getOrDefault(p, List.of())) {
            if (!m.read()) {
                n++;
            }
        }
        return n;
    }
}
