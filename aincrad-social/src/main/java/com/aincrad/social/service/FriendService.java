package com.aincrad.social.service;

import com.aincrad.social.data.SocialStore;

import java.util.Set;
import java.util.UUID;

/** Regras de amizade. Só lógica: quem chama cuida das mensagens e notificações. */
public final class FriendService {

    public enum RequestResult {
        /** Pedido enviado. */
        SENT,
        /** O outro já tinha pedido: viraram amigos na hora. */
        ACCEPTED,
        ALREADY_FRIENDS,
        ALREADY_SENT,
        SELF,
        /** Quem pediu já tem o máximo de amigos. */
        LIMIT_SELF,
        /** O outro já tem o máximo de amigos. */
        LIMIT_OTHER
    }

    public enum AcceptResult { OK, NO_REQUEST, LIMIT_SELF, LIMIT_OTHER }

    private final SocialStore store;
    private int maxFriends;

    public FriendService(SocialStore store, int maxFriends) {
        this.store = store;
        this.maxFriends = maxFriends;
    }

    public void setMaxFriends(int max) {
        this.maxFriends = max;
    }

    public Set<UUID> friends(UUID p) {
        return store.friends(p);
    }

    public Set<UUID> incoming(UUID p) {
        return store.requests(p);
    }

    public boolean areFriends(UUID a, UUID b) {
        return store.friends(a).contains(b);
    }

    public RequestResult request(UUID from, UUID to) {
        if (from.equals(to)) {
            return RequestResult.SELF;
        }
        if (areFriends(from, to)) {
            return RequestResult.ALREADY_FRIENDS;
        }
        if (store.requests(from).contains(to)) {
            AcceptResult r = accept(from, to);
            return switch (r) {
                case OK -> RequestResult.ACCEPTED;
                case LIMIT_SELF -> RequestResult.LIMIT_SELF;
                case LIMIT_OTHER -> RequestResult.LIMIT_OTHER;
                case NO_REQUEST -> RequestResult.SENT;
            };
        }
        if (store.requests(to).contains(from)) {
            return RequestResult.ALREADY_SENT;
        }
        if (store.friends(from).size() >= maxFriends) {
            return RequestResult.LIMIT_SELF;
        }
        if (store.friends(to).size() >= maxFriends) {
            return RequestResult.LIMIT_OTHER;
        }
        store.addRequest(from, to);
        return RequestResult.SENT;
    }

    /** {@code me} aceita o pedido de {@code from}. */
    public AcceptResult accept(UUID me, UUID from) {
        if (!store.requests(me).contains(from)) {
            return AcceptResult.NO_REQUEST;
        }
        if (store.friends(me).size() >= maxFriends) {
            return AcceptResult.LIMIT_SELF;
        }
        if (store.friends(from).size() >= maxFriends) {
            return AcceptResult.LIMIT_OTHER;
        }
        store.removeRequest(from, me);
        store.removeRequest(me, from);
        store.addFriend(me, from);
        return AcceptResult.OK;
    }

    public boolean decline(UUID me, UUID from) {
        if (!store.requests(me).contains(from)) {
            return false;
        }
        store.removeRequest(from, me);
        return true;
    }

    public boolean remove(UUID me, UUID other) {
        if (!areFriends(me, other)) {
            return false;
        }
        store.removeFriend(me, other);
        return true;
    }
}
