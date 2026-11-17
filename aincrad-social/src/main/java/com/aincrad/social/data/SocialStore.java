package com.aincrad.social.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dados que precisam sobreviver a reinícios: amizades, pedidos de amizade, caixa de entrada e
 * os nomes conhecidos (para mostrar quem está offline). Esta classe guarda em memória;
 * {@link YamlSocialStore} grava em arquivo. Party não é guardada: some quando o servidor reinicia.
 */
public class SocialStore {

    protected final Map<UUID, Set<UUID>> friends = new HashMap<>();
    /** destino → quem pediu */
    protected final Map<UUID, Set<UUID>> requests = new HashMap<>();
    protected final Map<UUID, List<InboxMessage>> inbox = new HashMap<>();
    protected final Map<UUID, String> names = new HashMap<>();
    private boolean dirty;

    public Set<UUID> friends(UUID p) {
        return Collections.unmodifiableSet(friends.getOrDefault(p, Set.of()));
    }

    public void addFriend(UUID a, UUID b) {
        friends.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b);
        friends.computeIfAbsent(b, k -> new LinkedHashSet<>()).add(a);
        changed();
    }

    public void removeFriend(UUID a, UUID b) {
        boolean x = remove(friends, a, b);
        boolean y = remove(friends, b, a);
        if (x || y) {
            changed();
        }
    }

    /** Pedidos recebidos por {@code target}. */
    public Set<UUID> requests(UUID target) {
        return Collections.unmodifiableSet(requests.getOrDefault(target, Set.of()));
    }

    public void addRequest(UUID from, UUID to) {
        requests.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
        changed();
    }

    public void removeRequest(UUID from, UUID to) {
        if (remove(requests, to, from)) {
            changed();
        }
    }

    public List<InboxMessage> inbox(UUID p) {
        return Collections.unmodifiableList(inbox.getOrDefault(p, List.of()));
    }

    public void setInbox(UUID p, List<InboxMessage> messages) {
        if (messages.isEmpty()) {
            inbox.remove(p);
        } else {
            inbox.put(p, new ArrayList<>(messages));
        }
        changed();
    }

    public String name(UUID p) {
        return names.get(p);
    }

    public void rememberName(UUID p, String name) {
        if (!name.equals(names.put(p, name))) {
            changed();
        }
    }

    /** Busca por nome, sem diferenciar maiúsculas. */
    public UUID byName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().toLowerCase(Locale.ROOT).equals(n)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static boolean remove(Map<UUID, Set<UUID>> map, UUID key, UUID value) {
        Set<UUID> set = map.get(key);
        if (set == null || !set.remove(value)) {
            return false;
        }
        if (set.isEmpty()) {
            map.remove(key);
        }
        return true;
    }

    protected void changed() {
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }

    protected void clean() {
        dirty = false;
    }

    /** Grava se houver mudanças. Na memória, não faz nada. */
    public void save() {
        clean();
    }
}
