package com.aincrad.social.service;

import com.aincrad.social.data.InboxMessage;
import com.aincrad.social.data.SocialStore;
import com.aincrad.social.service.FriendService.AcceptResult;
import com.aincrad.social.service.FriendService.RequestResult;
import com.aincrad.social.service.PartyService.InviteResult;
import com.aincrad.social.service.PartyService.JoinResult;
import com.aincrad.social.service.PartyService.KickResult;
import com.aincrad.social.service.PartyService.LeaveKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicesTest {

    private final UUID kirito = UUID.randomUUID();
    private final UUID asuna = UUID.randomUUID();
    private final UUID klein = UUID.randomUUID();
    private final UUID agil = UUID.randomUUID();

    // ------------------------------------------------------------------ amigos

    @Test
    void pedidoEAceite() {
        SocialStore store = new SocialStore();
        FriendService f = new FriendService(store, 10);
        assertEquals(RequestResult.SENT, f.request(kirito, asuna));
        assertEquals(RequestResult.ALREADY_SENT, f.request(kirito, asuna));
        assertTrue(f.incoming(asuna).contains(kirito));
        assertEquals(AcceptResult.OK, f.accept(asuna, kirito));
        assertTrue(f.areFriends(kirito, asuna));
        assertTrue(f.areFriends(asuna, kirito));
        assertTrue(f.incoming(asuna).isEmpty());
        assertEquals(RequestResult.ALREADY_FRIENDS, f.request(asuna, kirito));
        assertTrue(store.dirty());
    }

    @Test
    void pedidoCruzadoViraAmizade() {
        FriendService f = new FriendService(new SocialStore(), 10);
        f.request(kirito, asuna);
        assertEquals(RequestResult.ACCEPTED, f.request(asuna, kirito));
        assertTrue(f.areFriends(kirito, asuna));
    }

    @Test
    void recusarRemoverELimites() {
        FriendService f = new FriendService(new SocialStore(), 1);
        assertEquals(RequestResult.SELF, f.request(kirito, kirito));
        f.request(kirito, asuna);
        assertTrue(f.decline(asuna, kirito));
        assertFalse(f.decline(asuna, kirito));
        assertEquals(AcceptResult.NO_REQUEST, f.accept(asuna, kirito));

        f.request(kirito, asuna);
        f.accept(asuna, kirito);
        assertEquals(RequestResult.LIMIT_SELF, f.request(kirito, klein));
        assertEquals(RequestResult.LIMIT_OTHER, f.request(klein, kirito));
        assertTrue(f.remove(kirito, asuna));
        assertFalse(f.areFriends(asuna, kirito));
        assertFalse(f.remove(kirito, asuna));
    }

    // ------------------------------------------------------------------ party

    @Test
    void convidarAceitarCriaParty() {
        AtomicLong now = new AtomicLong(1000);
        PartyService p = new PartyService(3, 60_000, now::get);
        assertEquals(InviteResult.SENT, p.invite(kirito, asuna));
        assertEquals(InviteResult.ALREADY_INVITED, p.invite(kirito, asuna));
        assertEquals(List.of(kirito), p.invitesFor(asuna));
        assertEquals(JoinResult.JOINED, p.accept(asuna, kirito));
        PartyService.Party party = p.partyOf(kirito);
        assertSame(party, p.partyOf(asuna));
        assertEquals(kirito, party.leader());
        assertEquals(List.of(kirito, asuna), party.members());
        assertTrue(p.sameParty(asuna, kirito));
        assertEquals(InviteResult.ALREADY_IN_YOUR_PARTY, p.invite(kirito, asuna));
        assertEquals(InviteResult.NOT_LEADER, p.invite(asuna, klein));
    }

    @Test
    void conviteExpiraELotacao() {
        AtomicLong now = new AtomicLong(0);
        PartyService p = new PartyService(2, 1000, now::get);
        p.invite(kirito, asuna);
        now.set(2000);
        assertTrue(p.invitesFor(asuna).isEmpty());
        p.invite(kirito, asuna);
        now.set(2500);
        assertEquals(JoinResult.JOINED, p.accept(asuna, kirito));
        assertEquals(InviteResult.FULL, p.invite(kirito, klein));
        assertEquals(JoinResult.NO_INVITE, p.accept(klein, kirito));

        PartyService q = new PartyService(4, 1000, now::get);
        q.invite(kirito, klein);
        now.set(4000);
        assertEquals(JoinResult.EXPIRED, q.accept(klein, kirito));
    }

    @Test
    void outraPartyERecusa() {
        PartyService p = new PartyService(4, 60_000, () -> 0);
        p.invite(kirito, asuna);
        p.accept(asuna, kirito);
        p.invite(klein, agil);
        p.accept(agil, klein);
        assertEquals(InviteResult.TARGET_IN_PARTY, p.invite(kirito, agil));
        UUID silica = UUID.randomUUID();
        assertEquals(InviteResult.SENT, p.invite(kirito, silica));
        assertTrue(p.decline(silica, kirito));
        assertFalse(p.decline(silica, kirito));
        assertEquals(2, p.parties().size());
    }

    @Test
    void liderSaiEPassaALideranca() {
        PartyService p = new PartyService(4, 60_000, () -> 0);
        p.invite(kirito, asuna);
        p.accept(asuna, kirito);
        p.invite(kirito, klein);
        p.accept(klein, kirito);
        PartyService.Leave l = p.leave(kirito);
        assertEquals(LeaveKind.LEFT, l.kind());
        assertEquals(asuna, l.newLeader());
        assertEquals(asuna, p.partyOf(klein).leader());
        assertNull(p.partyOf(kirito));
    }

    @Test
    void partyDeUmSeDesfaz() {
        PartyService p = new PartyService(4, 60_000, () -> 0);
        p.invite(kirito, asuna);
        p.accept(asuna, kirito);
        PartyService.Leave l = p.leave(asuna);
        assertEquals(LeaveKind.DISBANDED, l.kind());
        assertEquals(List.of(kirito), l.remaining());
        assertNull(p.partyOf(kirito));
        assertEquals(LeaveKind.NOT_IN_PARTY, p.leave(asuna).kind());
    }

    @Test
    void expulsarPromoverDesfazer() {
        PartyService p = new PartyService(4, 60_000, () -> 0);
        p.invite(kirito, asuna);
        p.accept(asuna, kirito);
        p.invite(kirito, klein);
        p.accept(klein, kirito);
        assertEquals(KickResult.NOT_LEADER, p.kick(asuna, klein));
        assertEquals(KickResult.SELF, p.kick(kirito, kirito));
        assertEquals(KickResult.NOT_MEMBER, p.kick(kirito, agil));
        assertEquals(KickResult.KICKED, p.kick(kirito, klein));
        assertNull(p.partyOf(klein));
        assertTrue(p.promote(kirito, asuna));
        assertEquals(asuna, p.partyOf(kirito).leader());
        assertFalse(p.promote(kirito, asuna));
        assertTrue(p.disband(kirito).isEmpty()); // kirito não é mais líder
        assertEquals(List.of(kirito, asuna), p.disband(asuna));
        assertNull(p.partyOf(kirito));
    }

    // ------------------------------------------------------------------ mensagens

    @Test
    void caixaDeEntrada() {
        AtomicLong now = new AtomicLong(1);
        SocialStore store = new SocialStore();
        MessageService m = new MessageService(store, 2, now::get);
        m.send(kirito, "Kirito", asuna, "  oi\n\nAsuna  ");
        now.set(2);
        InboxMessage second = m.send(klein, "Klein", asuna, "bora caçar");
        now.set(3);
        m.send(agil, "Agil", asuna, "loja aberta");
        List<InboxMessage> box = m.inbox(asuna);
        assertEquals(2, box.size(), "a mais antiga sai quando enche");
        assertEquals("Agil", box.get(0).fromName());
        assertEquals(2, m.unread(asuna));
        m.markRead(asuna, second.id());
        assertEquals(1, m.unread(asuna));
        assertTrue(m.delete(asuna, second.id()));
        assertFalse(m.delete(asuna, second.id()));
        assertEquals("oi Asuna", MessageService.clean("  oi\n\nAsuna  "));
        assertEquals(MessageService.MAX_TEXT, MessageService.clean("x".repeat(400)).length());
    }
}
