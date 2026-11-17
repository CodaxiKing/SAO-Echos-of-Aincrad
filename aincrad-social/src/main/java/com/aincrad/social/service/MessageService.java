package com.aincrad.social.service;

import com.aincrad.social.data.InboxMessage;
import com.aincrad.social.data.SocialStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Caixa de entrada: toda mensagem fica guardada (lida ou não), até o limite por jogador. */
public final class MessageService {

    public static final int MAX_TEXT = 256;

    private final SocialStore store;
    private final LongSupplier clock;
    private int maxInbox;

    public MessageService(SocialStore store, int maxInbox, LongSupplier clock) {
        this.store = store;
        this.maxInbox = maxInbox;
        this.clock = clock;
    }

    public void setMaxInbox(int max) {
        this.maxInbox = max;
    }

    /** Limpa o texto: tira quebras de linha e espaços extras e corta no tamanho máximo. */
    public static String clean(String text) {
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    /** Guarda a mensagem; as mais antigas saem quando a caixa enche. */
    public InboxMessage send(UUID from, String fromName, UUID to, String text) {
        InboxMessage m = new InboxMessage(UUID.randomUUID(), from, fromName, clean(text), clock.getAsLong(), false);
        List<InboxMessage> list = new ArrayList<>(store.inbox(to));
        list.add(m);
        list.sort(Comparator.comparingLong(InboxMessage::time));
        while (list.size() > maxInbox) {
            list.remove(0);
        }
        store.setInbox(to, list);
        return m;
    }

    /** Mais novas primeiro. */
    public List<InboxMessage> inbox(UUID p) {
        List<InboxMessage> list = new ArrayList<>(store.inbox(p));
        list.sort(Comparator.comparingLong(InboxMessage::time).reversed());
        return list;
    }

    public int unread(UUID p) {
        return (int) store.inbox(p).stream().filter(m -> !m.read()).count();
    }

    public InboxMessage find(UUID p, UUID id) {
        return store.inbox(p).stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }

    public void markRead(UUID p, UUID id) {
        List<InboxMessage> list = new ArrayList<>(store.inbox(p));
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id) && !list.get(i).read()) {
                list.set(i, list.get(i).asRead());
                changed = true;
            }
        }
        if (changed) {
            store.setInbox(p, list);
        }
    }

    public boolean delete(UUID p, UUID id) {
        List<InboxMessage> list = new ArrayList<>(store.inbox(p));
        boolean removed = list.removeIf(m -> m.id().equals(id));
        if (removed) {
            store.setInbox(p, list);
        }
        return removed;
    }
}
