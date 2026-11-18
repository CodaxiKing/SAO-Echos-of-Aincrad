package com.aincrad.admin;

import com.aincrad.admin.model.DiscordRules;
import com.aincrad.admin.model.RankingRules;
import com.aincrad.admin.model.RankingRules.BossDamage;
import com.aincrad.admin.model.RankingRules.DamageRow;
import com.aincrad.admin.model.RankingRules.FloorTime;
import com.aincrad.admin.model.RankingRules.Row;
import com.aincrad.admin.model.RankingRules.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O ranking em site e o Discord (etapa 29), sem servidor e sem rede. */
class RankingDiscordTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void osTresMaioresDanosDeCadaChefe() {
        List<BossDamage> top = RankingRules.topPerBoss(List.of(
                new DamageRow("Illfang", 1, "Kirito", 900), new DamageRow("Illfang", 1, "Asuna", 700),
                new DamageRow("Illfang", 1, "Kirito", 1200), new DamageRow("Illfang", 1, "Klein", 300),
                new DamageRow("Illfang", 1, "Agil", 500), new DamageRow("Asterius", 2, "Asuna", 2000)), 3);
        assertEquals(2, top.size());
        assertEquals("Illfang", top.get(0).boss());
        // O melhor de cada jogador, do maior para o menor, só três.
        assertEquals(List.of(new Row("Kirito", 1200), new Row("Asuna", 700), new Row("Agil", 500)), top.get(0).top());
        assertEquals(2, top.get(1).floor());
    }

    @Test
    void oTempoDeCadaAndarContaDesdeOAnterior() {
        List<FloorTime> t = RankingRules.floorTimes(Map.of(2, 10 * HOUR, 3, 36 * HOUR, 5, 100 * HOUR));
        assertEquals(List.of(new FloorTime(2, 10 * HOUR, -1), new FloorTime(3, 36 * HOUR, 26 * HOUR),
                new FloorTime(5, 100 * HOUR, -1)), t);
        assertEquals("1d 2h", RankingRules.duration(26 * HOUR));
        assertEquals("2h 5min", RankingRules.duration(2 * HOUR + 5 * 60_000));
        assertEquals("45min", RankingRules.duration(45 * 60_000));
        assertEquals("menos de 1min", RankingRules.duration(5_000));
        assertEquals("—", RankingRules.duration(-1));
    }

    @Test
    void oJsonEAPaginaEscapamOQueVemDoJogo() {
        Snapshot s = new Snapshot(3, List.of(new Row("Kirito", 2)), List.of(new Row("Ki\"rito", 1)),
                List.of(new BossDamage("<script>alert(1)</script>", 1, List.of(new Row("Asuna", 1234.5)))),
                List.of(new FloorTime(2, 10 * HOUR, -1), new FloorTime(3, 36 * HOUR, 26 * HOUR)), 50 * HOUR);
        String json = RankingRules.json(s);
        assertTrue(json.startsWith("{\"gerado_em\":180000000,\"linha_de_frente\":{\"andar\":3,\"jogadores\":[{\"jogador\":\"Kirito\",\"primeiras_vitorias\":2}]}"), json);
        assertTrue(json.contains("{\"jogador\":\"Ki\\\"rito\",\"quantidade\":1}"), json);
        assertTrue(json.contains("\"maiores_danos\":[{\"jogador\":\"Asuna\",\"dano\":1234.5}]"), json);
        assertTrue(json.contains("{\"andar\":2,\"liberado_em\":36000000,\"tempo_ms\":null}"), json);
        assertTrue(json.endsWith("\"tempo_ms\":93600000}]}"), json);

        String page = RankingRules.page(s, "Aincrad & Ranking", ZoneId.of("UTC"));
        assertTrue(page.contains("<title>Aincrad &amp; Ranking</title>"));
        assertFalse(page.contains("<script>"));
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(page.contains("andar 3"));
        assertTrue(page.contains("1d 2h"));
        // Sem dados a página ainda abre.
        assertTrue(RankingRules.page(Snapshot.EMPTY, "x", ZoneId.of("UTC")).contains("Ainda sem dados."));
        assertEquals("\"a\\nb\\u0001\"", RankingRules.jsonString("a\nb\u0001"));
    }

    @Test
    void soWebhookDoDiscordPorHttps() {
        assertTrue(DiscordRules.validUrl("https://discord.com/api/webhooks/123/abc"));
        assertTrue(DiscordRules.validUrl("https://ptb.discord.com/api/webhooks/123/abc"));
        assertTrue(DiscordRules.validUrl("https://discordapp.com/api/webhooks/123/abc"));
        assertFalse(DiscordRules.validUrl("http://discord.com/api/webhooks/123/abc"));
        assertFalse(DiscordRules.validUrl("https://discord.com.evil.net/api/webhooks/123/abc"));
        assertFalse(DiscordRules.validUrl("https://evil.net/api/webhooks/123/abc"));
        assertFalse(DiscordRules.validUrl("https://discord.com/outra/coisa"));
        assertFalse(DiscordRules.validUrl("nada disso"));
        assertFalse(DiscordRules.validUrl(""));
        assertFalse(DiscordRules.validUrl(null));
    }

    @Test
    void aMensagemDoChefeTemLastAttackETop3() {
        Map<String, Double> damage = new LinkedHashMap<>();
        damage.put("Klein", 300.0);
        damage.put("Kirito", 1234.4);
        damage.put("Asuna", 980.0);
        damage.put("Agil", 500.0);
        assertEquals("Kirito (1.234), Asuna (980), Agil (500)", DiscordRules.top(damage, 3));
        String text = DiscordRules.fill("**<chefe>** (andar <andar>) — Last Attack: **<last_attack>**. Maiores danos: <top>",
                Map.of("chefe", "Illfang", "andar", "1", "last_attack", "Kirito", "top", DiscordRules.top(damage, 3)));
        assertEquals("**Illfang** (andar 1) — Last Attack: **Kirito**. Maiores danos: Kirito \\(1.234\\), Asuna \\(980\\), Agil \\(500\\)", text);
    }

    @Test
    void nomeDeJogadorNaoViraMarkdownNemMencao() {
        assertEquals("\\@everyone \\*\\*oi\\*\\* \\_x\\_", DiscordRules.escape("@everyone **oi** _x_"));
        String body = DiscordRules.payload(DiscordRules.fill("<jogador> venceu", Map.of("jogador", "@everyone\"")));
        assertEquals("{\"content\":\"\\\\@everyone\\\" venceu\",\"allowed_mentions\":{\"parse\":[]}}", body);
        // Texto longo demais é cortado no limite do Discord.
        assertEquals(DiscordRules.MAX, DiscordRules.fill("x".repeat(3000), Map.of()).length());
    }
}
