package com.aincrad.social.guild;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildDataTest {

    private static final File RES = new File("src/main/resources");

    @TempDir
    File tmp;

    @Test
    void gravaELeDeVolta() {
        AtomicLong now = new AtomicLong(System.currentTimeMillis());
        GuildService s = new GuildService(GuildRules.defaults(), now::get);
        UUID kirito = UUID.randomUUID();
        UUID asuna = UUID.randomUUID();
        UUID klein = UUID.randomUUID();
        s.create(asuna, "Cavaleiros do Juramento", "KoB", "✚", 5000);
        Guild g = s.guildOf(asuna);
        s.invite(asuna, kirito);
        s.acceptInvite(kirito, g.id());
        s.promote(asuna, kirito);
        s.setColors(asuna, "#FFFFFF", "#C8102E");
        s.setRecruit(asuna, Guild.Recruit.CANDIDATURA);
        s.apply(klein, g.id());
        s.addXp(g, 1500);
        s.setHome(asuna, "world,1.5,64.0,-3.5,90.0,0.0");
        s.deposit(asuna, "Asuna", 700);
        s.recordBossKill(g, 1, true);
        s.setVaultData(g, ";;abc;");
        s.setPermission(asuna, GuildPerm.CONVIDAR, GuildRank.MEMBRO);
        s.addPendingRefund(klein, 250);

        GuildStore store = new GuildStore(new File(tmp, "guildas.yml"), Logger.getAnonymousLogger());
        store.write(store.snapshot(s));

        GuildService r = new GuildService(GuildRules.defaults(), now::get);
        store.load(r);
        Guild h = r.guildOf(kirito);
        assertNotNull(h);
        assertEquals(g.id(), h.id());
        assertEquals("KOB", h.tag());
        assertEquals("✚", h.emblem());
        assertEquals(GuildRank.MEMBRO, h.rankOf(kirito));
        assertEquals(GuildRank.LIDER, h.rankOf(asuna));
        assertEquals("#C8102E", h.color2());
        assertEquals(Guild.Recruit.CANDIDATURA, h.recruit());
        assertTrue(h.applications().containsKey(klein));
        assertEquals(2, h.level());
        assertEquals(700, h.treasury());
        assertEquals(1, h.bossKills());
        assertEquals(1, h.lastAttacks());
        assertEquals("world,1.5,64.0,-3.5,90.0,0.0", h.home());
        assertEquals(";;abc;", h.vaultData());
        assertEquals(GuildRank.MEMBRO, h.minRank(GuildPerm.CONVIDAR));
        assertEquals(1, h.ledger().size());
        assertEquals(250, r.takePendingRefund(klein));
        assertNull(r.guildOf(klein));
    }

    @Test
    void configPadraoBateComAsRegrasDoPlano() {
        GuildConfig c = new GuildConfig(YamlConfiguration.loadConfiguration(new File(RES, "guilds.yml")));
        GuildRules d = GuildRules.defaults();
        assertEquals(d.levels(), c.rules.levels());
        assertEquals(d.emblems(), c.rules.emblems());
        assertEquals(d.minMembers(), c.rules.minMembers());
        assertEquals(d.recruitPromoteMs(), c.rules.recruitPromoteMs());
        assertEquals(d.leaderInactiveMs(), c.rules.leaderInactiveMs());
        assertEquals(d.rejoinCooldownMs(), c.rules.rejoinCooldownMs());
        assertEquals(3, c.minFloor);
        assertEquals(5000, c.cost);
        assertEquals("#", c.chatPrefix);
    }

    /** Toda mensagem que o código monta a partir de um resultado tem que existir em messages.yml. */
    @Test
    void mensagensDeTodosOsResultados() {
        YamlConfiguration m = YamlConfiguration.loadConfiguration(new File(RES, "messages.yml"));
        for (GuildService.ActResult r : GuildService.ActResult.values()) {
            assertNotNull(m.getString("guilda.erro." + key(r)), "guilda.erro." + key(r));
        }
        for (GuildService.CreateResult r : GuildService.CreateResult.values()) {
            if (r != GuildService.CreateResult.CREATED) {
                assertNotNull(m.getString("guilda.criar." + key(r)), "guilda.criar." + key(r));
            }
        }
        for (GuildService.InviteResult r : GuildService.InviteResult.values()) {
            assertNotNull(m.getString("guilda.convite." + key(r)), "guilda.convite." + key(r));
        }
        for (GuildService.JoinResult r : GuildService.JoinResult.values()) {
            if (r != GuildService.JoinResult.JOINED && r != GuildService.JoinResult.APPLIED) {
                assertNotNull(m.getString("guilda.entrar." + key(r)), "guilda.entrar." + key(r));
            }
        }
        for (GuildService.AllyResult r : GuildService.AllyResult.values()) {
            if (r != GuildService.AllyResult.PROPOSED && r != GuildService.AllyResult.ALLIED && r != GuildService.AllyResult.BROKEN) {
                assertNotNull(m.getString("guilda.alianca." + key(r)), "guilda.alianca." + key(r));
            }
        }
        for (GuildRank r : GuildRank.values()) {
            assertNotNull(m.getString("guilda.cargo." + r.key()), "guilda.cargo." + r.key());
        }
        for (Guild.Recruit r : Guild.Recruit.values()) {
            assertNotNull(m.getString("guilda.recrutamento." + key(r)), "guilda.recrutamento." + key(r));
        }
        for (String t : new String[]{"deposito", "saque", "taxa", "cofre_entrada", "cofre_saida"}) {
            assertNotNull(m.getString("guilda.extrato." + t), "guilda.extrato." + t);
        }
    }

    private static String key(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    @Test
    void redirecionaAdmin() {
        assertEquals("guilda admin dar-xp KOB 100", GuildListener.rewrite("aincrad guilda dar-xp KOB 100"));
        assertNull(GuildListener.rewrite("aincrad guildas"));
        assertNull(GuildListener.rewrite("guilda info"));
    }
}
