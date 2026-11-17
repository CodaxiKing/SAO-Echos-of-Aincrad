package com.aincrad.social.guild;

import com.aincrad.social.guild.GuildService.ActResult;
import com.aincrad.social.guild.GuildService.AllyResult;
import com.aincrad.social.guild.GuildService.CreateResult;
import com.aincrad.social.guild.GuildService.Event;
import com.aincrad.social.guild.GuildService.InviteResult;
import com.aincrad.social.guild.GuildService.JoinResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildServiceTest {

    private static final long DAY = 86_400_000L;

    private final UUID heathcliff = UUID.randomUUID();
    private final UUID asuna = UUID.randomUUID();
    private final UUID godfree = UUID.randomUUID();
    private final UUID kuradeel = UUID.randomUUID();
    private final UUID klein = UUID.randomUUID();
    private final UUID kirito = UUID.randomUUID();

    private AtomicLong now;
    private GuildService s;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000_000);
        s = new GuildService(GuildRules.defaults(), now::get);
    }

    private Guild kob() {
        assertEquals(CreateResult.CREATED, s.create(heathcliff, "Cavaleiros do Juramento", "KoB", "✚", 5000));
        return s.guildOf(heathcliff);
    }

    private void join(Guild g, UUID p) {
        assertEquals(InviteResult.SENT, s.invite(g.leader(), p));
        assertEquals(JoinResult.JOINED, s.acceptInvite(p, g.id()));
    }

    @Test
    void criacaoValidaNomeTagEUnicidade() {
        Guild g = kob();
        assertEquals("KOB", g.tag());
        assertEquals(GuildRank.LIDER, g.rankOf(heathcliff));
        assertEquals(CreateResult.IN_GUILD, s.create(heathcliff, "Outra", "OUT", null, 0));
        assertEquals(CreateResult.NAME_TAKEN, s.create(klein, "cavaleiros do juramento", "FRK", null, 0));
        assertEquals(CreateResult.TAG_TAKEN, s.create(klein, "Fuurinkazan", "kob", null, 0));
        assertEquals(CreateResult.NAME_INVALID, s.create(klein, "Fu", "FRK", null, 0));
        assertEquals(CreateResult.TAG_INVALID, s.create(klein, "Fuurinkazan", "F", null, 0));
        assertEquals(CreateResult.TAG_INVALID, s.create(klein, "Fuurinkazan", "F-K", null, 0));
        assertEquals(CreateResult.EMBLEM_INVALID, s.create(klein, "Fuurinkazan", "FRK", "X", 0));
        assertEquals(CreateResult.CREATED, s.create(klein, "Fuurinkazan", "FRK", null, 0));
    }

    @Test
    void palavrasBloqueadas() {
        GuildRules d = GuildRules.defaults();
        s.setRules(new GuildRules(d.nameMin(), d.nameMax(), d.tagMin(), d.tagMax(), List.of("feio"), d.emblems(),
                d.minMembers(), d.minMembersDeadlineMs(), d.refundFraction(), d.recruitPromoteMs(), d.leaderInactiveMs(),
                d.rejoinCooldownMs(), d.inviteTtlMs(), d.memberWithdrawalsPerDay(), d.maxTaxPercent(), d.maxAllies(),
                d.alliesMinLevel(), d.uniformMinLevel(), d.homeMinLevel(), d.ledgerMax(), d.levels()));
        assertEquals(CreateResult.BLOCKED_WORD, s.create(klein, "Guilda do Feio", "GF", null, 0));
        assertEquals(CreateResult.BLOCKED_WORD, s.create(klein, "Guilda Boa", "FEIO", null, 0));
    }

    @Test
    void conviteEntraComoRecrutaEPermissoes() {
        Guild g = kob();
        join(g, asuna);
        assertEquals(GuildRank.RECRUTA, g.rankOf(asuna));
        assertFalse(s.has(asuna, GuildPerm.DEPOSITAR));
        assertTrue(s.has(asuna, GuildPerm.CASA));
        assertEquals(InviteResult.NO_PERMISSION, s.invite(asuna, klein));
        assertEquals(InviteResult.TARGET_IN_GUILD, s.invite(heathcliff, asuna));
        assertEquals(InviteResult.SELF, s.invite(heathcliff, heathcliff));
        assertEquals(InviteResult.SENT, s.invite(heathcliff, klein));
        assertEquals(InviteResult.ALREADY_INVITED, s.invite(heathcliff, klein));
        now.addAndGet(GuildRules.defaults().inviteTtlMs() + 1);
        assertEquals(JoinResult.EXPIRED, s.acceptInvite(klein, g.id()));
    }

    @Test
    void cargosPromoverRebaixarExpulsar() {
        Guild g = kob();
        join(g, asuna);
        join(g, godfree);
        join(g, kuradeel);
        assertEquals(ActResult.OK, s.promote(heathcliff, asuna));   // recruta → membro
        assertEquals(ActResult.OK, s.promote(heathcliff, asuna));   // → oficial
        assertEquals(ActResult.OK, s.promote(heathcliff, asuna));   // → sub-líder
        assertEquals(GuildRank.SUBLIDER, g.rankOf(asuna));
        assertEquals(ActResult.LIMIT, s.promote(heathcliff, asuna)); // liderança só por transferência
        s.promote(heathcliff, godfree);
        s.promote(heathcliff, godfree);
        assertEquals(ActResult.SUB_TAKEN, s.promote(heathcliff, godfree));
        // O sub-líder promove até oficial, não até sub-líder.
        assertEquals(ActResult.OK, s.promote(asuna, kuradeel));
        assertEquals(ActResult.OK, s.promote(asuna, kuradeel));
        assertEquals(GuildRank.OFICIAL, g.rankOf(kuradeel));
        // Oficial não expulsa oficial; sub-líder expulsa.
        assertEquals(ActResult.HIGHER_RANK, s.kick(godfree, kuradeel));
        assertEquals(ActResult.HIGHER_RANK, s.kick(asuna, heathcliff));
        assertEquals(ActResult.OK, s.demote(asuna, kuradeel));
        assertEquals(GuildRank.MEMBRO, g.rankOf(kuradeel));
        assertEquals(ActResult.OK, s.kick(godfree, kuradeel));
        assertNull(s.guildOf(kuradeel));
        assertTrue(s.cooldownLeft(kuradeel) > 0);
        assertEquals(InviteResult.TARGET_COOLDOWN, s.invite(heathcliff, kuradeel));
        now.addAndGet(DAY);
        assertEquals(InviteResult.SENT, s.invite(heathcliff, kuradeel));
    }

    @Test
    void transferirLiderancaESaida() {
        Guild g = kob();
        join(g, asuna);
        join(g, godfree);
        s.promote(heathcliff, godfree);
        s.promote(heathcliff, godfree);
        s.promote(heathcliff, godfree); // godfree sub-líder
        assertEquals(ActResult.LEADER_MUST_TRANSFER, s.leave(heathcliff).result());
        assertEquals(ActResult.OK, s.transfer(heathcliff, asuna));
        assertEquals(GuildRank.LIDER, g.rankOf(asuna));
        assertEquals(GuildRank.SUBLIDER, g.rankOf(heathcliff));
        assertEquals(GuildRank.OFICIAL, g.rankOf(godfree));
        assertEquals(asuna, g.leader());
        GuildService.Leave l = s.leave(heathcliff);
        assertEquals(ActResult.OK, l.result());
        assertFalse(l.disbanded());
    }

    @Test
    void liderSozinhoSaiEAGuildaAcaba() {
        Guild g = kob();
        GuildService.Leave l = s.leave(heathcliff);
        assertTrue(l.disbanded());
        assertNull(s.get(g.id()));
        assertEquals(0, s.cooldownLeft(heathcliff), "desfazer não dá espera");
    }

    @Test
    void candidaturaEModoAberto() {
        Guild g = kob();
        assertEquals(JoinResult.CLOSED, s.apply(klein, g.id()));
        assertEquals(ActResult.NO_GUILD, s.setRecruit(klein, Guild.Recruit.ABERTA));
        join(g, asuna);
        assertEquals(ActResult.NO_PERMISSION, s.setRecruit(asuna, Guild.Recruit.ABERTA));
        s.setRecruit(heathcliff, Guild.Recruit.CANDIDATURA);
        assertEquals(JoinResult.APPLIED, s.apply(klein, g.id()));
        assertEquals(JoinResult.ALREADY_APPLIED, s.apply(klein, g.id()));
        assertEquals(ActResult.OK, s.answerApplication(heathcliff, klein, true));
        assertEquals(GuildRank.RECRUTA, g.rankOf(klein));
        s.setRecruit(heathcliff, Guild.Recruit.ABERTA);
        assertEquals(JoinResult.JOINED, s.apply(kirito, g.id()));
    }

    @Test
    void vagasPorNivelEXp() {
        Guild g = kob();
        assertEquals(10, s.slots(g));
        for (int i = 0; i < 9; i++) {
            join(g, UUID.randomUUID());
        }
        assertEquals(InviteResult.FULL, s.invite(heathcliff, klein));
        assertEquals(1, s.addXp(g, 1000));
        assertEquals(2, g.level());
        assertEquals(20, s.slots(g));
        assertEquals(18, s.vaultSize(g));
        assertEquals(3, s.addXp(g, 100_000)); // 2 → 5, limitado ao último nível
        assertEquals(5, g.level());
        assertEquals(0, s.addXp(g, 1));
    }

    @Test
    void tesouroTaxaECofre() {
        Guild g = kob();
        join(g, asuna);
        now.addAndGet(3 * DAY);
        s.maintenance(); // recruta vira membro
        assertEquals(GuildRank.MEMBRO, g.rankOf(asuna));
        assertEquals(ActResult.OK, s.deposit(asuna, "Asuna", 500));
        assertEquals(ActResult.NO_PERMISSION, s.withdraw(asuna, "Asuna", 100));
        assertEquals(ActResult.NOT_ENOUGH, s.withdraw(heathcliff, "Heathcliff", 501));
        assertEquals(ActResult.OK, s.withdraw(heathcliff, "Heathcliff", 200));
        assertEquals(300, g.treasury());
        assertEquals(ActResult.INVALID, s.setTax(heathcliff, 50));
        s.setTax(heathcliff, 10);
        assertEquals(15, s.tax(asuna, "Asuna", 150));
        assertEquals(315, g.treasury());
        assertEquals(3, g.ledger().size());
        for (int i = 0; i < 16; i++) {
            assertTrue(s.canWithdrawItem(asuna));
            s.recordVault(asuna, "Asuna", true, "DIAMOND", 1);
        }
        assertFalse(s.canWithdrawItem(asuna), "membro tem limite de retiradas por dia");
        assertTrue(s.canWithdrawItem(heathcliff));
        now.addAndGet(DAY);
        assertTrue(s.canWithdrawItem(asuna));
    }

    @Test
    void aliancasPrecisamDeNivelEAceite() {
        Guild a = kob();
        assertEquals(CreateResult.CREATED, s.create(klein, "Fuurinkazan", "FRK", null, 0));
        Guild b = s.guildOf(klein);
        assertEquals(AllyResult.LEVEL_TOO_LOW, s.proposeAlliance(heathcliff, b.id()));
        s.addXp(a, 3000);
        s.addXp(b, 3000);
        assertEquals(AllyResult.PROPOSED, s.proposeAlliance(heathcliff, b.id()));
        assertTrue(b.allyProposals().contains(a.id()));
        assertEquals(AllyResult.ALLIED, s.acceptAlliance(klein, a.id()));
        assertTrue(s.areAllies(heathcliff, klein));
        assertFalse(s.areAllies(heathcliff, heathcliff));
        assertEquals(AllyResult.ALREADY, s.proposeAlliance(klein, a.id()));
        assertEquals(AllyResult.BROKEN, s.breakAlliance(klein, a.id()));
        assertFalse(s.areAllies(heathcliff, klein));
    }

    @Test
    void manutencaoLiderInativoEGuildaPequena() {
        Guild g = kob();
        join(g, asuna);
        join(g, godfree);
        s.promote(heathcliff, godfree);
        s.promote(heathcliff, godfree); // godfree oficial
        assertTrue(g.size() >= 3);
        now.addAndGet(31 * DAY);
        s.markSeen(asuna);
        List<Event> ev = s.maintenance();
        assertTrue(ev.stream().anyMatch(e -> e.kind() == Event.Kind.LEADER_CHANGED && e.who().equals(godfree)));
        assertEquals(GuildRank.LIDER, g.rankOf(godfree));
        assertEquals(GuildRank.OFICIAL, g.rankOf(heathcliff));
        assertNotNull(s.get(g.id()), "chegou ao mínimo: não é desfeita");

        assertEquals(CreateResult.CREATED, s.create(klein, "Fuurinkazan", "FRK", null, 5000));
        Guild small = s.guildOf(klein);
        now.addAndGet(8 * DAY);
        List<Event> ev2 = s.maintenance();
        Event d = ev2.stream().filter(e -> e.kind() == Event.Kind.DISBANDED_SMALL).findFirst().orElseThrow();
        assertEquals(2500, d.refund());
        assertNull(s.get(small.id()));
        assertNull(s.guildOf(klein));
    }

    @Test
    void ranking() {
        Guild a = kob();
        s.create(klein, "Divine Dragons", "DDA", null, 0);
        Guild b = s.guildOf(klein);
        s.recordBossKill(b, 3, true);
        s.recordBossKill(a, 5, false);
        s.recordBossKill(a, 6, true);
        assertEquals(List.of(a, b), s.ranking());
        assertEquals(6, a.highestFloor());
    }

    @Test
    void edicaoDoLider() {
        Guild g = kob();
        join(g, asuna);
        assertEquals(ActResult.NO_PERMISSION, s.setColors(asuna, "#FFFFFF", "#000000"));
        assertEquals(ActResult.INVALID, s.setColors(heathcliff, "branco", "#000000"));
        assertEquals(ActResult.OK, s.setColors(heathcliff, "#ffffff", "#c8102e"));
        assertEquals("#C8102E", g.color2());
        assertEquals(ActResult.INVALID, s.setPermission(heathcliff, GuildPerm.LIDERANCA, GuildRank.MEMBRO));
        assertEquals(ActResult.OK, s.setPermission(heathcliff, GuildPerm.CONVIDAR, GuildRank.RECRUTA));
        assertTrue(s.has(asuna, GuildPerm.CONVIDAR));
        assertEquals(ActResult.LEVEL_TOO_LOW, s.setHome(heathcliff, "world,0,64,0,0,0"));
        s.addXp(g, 1000);
        assertEquals(ActResult.OK, s.setHome(heathcliff, "world,0,64,0,0,0"));
    }
}
