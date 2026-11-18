package com.aincrad.floors;

import com.aincrad.floors.model.DeathGameRules;
import com.aincrad.floors.model.DeathGameRules.Line;
import com.aincrad.floors.model.DeathGameRules.Outcome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras do jogo da morte (etapas 40 e 43), sem servidor. */
class DeathGameRulesTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                DeathGameRulesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    @Test
    void oDiscursoDoArquivoTocaEmOrdem() {
        YamlConfiguration y = resource("deathgame.yml");
        List<Line> lines = DeathGameRules.lines(y.getMapList("anuncio.falas"));
        assertEquals(5, lines.size());
        assertEquals(0, lines.get(0).atTicks());
        assertNotNull(lines.get(0).title());
        assertNull(lines.get(0).chat());
        assertEquals(24 * 20, lines.get(4).atTicks());
        // O logout some 4 segundos depois da última fala.
        assertEquals(28 * 20, DeathGameRules.durationTicks(lines, y.getDouble("anuncio.pausa_final")));
        assertEquals(1, DeathGameRules.lines(y.getMapList("anuncio.falas_curtas")).size());
        assertFalse(DeathGameRules.lines(y.getMapList("fim.falas")).isEmpty());
    }

    @Test
    void falasForaDeOrdemEVazias() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                falas:
                  - { em: 5, chat: "depois" }
                  - { em: 0.5, titulo: "antes" }
                  - { em: 2 }
                  - { chat: "sem tempo" }
                """);
        List<Line> lines = DeathGameRules.lines(y.getMapList("falas"));
        assertEquals(List.of("sem tempo", "antes", "depois"),
                lines.stream().map(l -> l.chat() != null ? l.chat() : l.title()).toList());
        assertEquals(10, lines.get(1).atTicks());
        assertEquals(0, DeathGameRules.durationTicks(List.of(), 0));
    }

    @Test
    void oAndarFinal() {
        // Sem andar_final na config, vale o último andar do floors.yml.
        assertTrue(DeathGameRules.isFinal(100, 0, 100));
        assertFalse(DeathGameRules.isFinal(99, 0, 100));
        // Como no anime: o jogo acaba no 75, mesmo com 100 andares no arquivo.
        assertTrue(DeathGameRules.isFinal(75, 75, 100));
        assertFalse(DeathGameRules.isFinal(100, 75, 100));
        assertFalse(DeathGameRules.isFinal(0, 0, 0));
    }

    @Test
    void oQueFazerComOMundo() {
        assertEquals(Outcome.CONTINUAR, Outcome.parse("continuar"));
        assertEquals(Outcome.REINICIAR, Outcome.parse(" Reiniciar "));
        assertEquals(Outcome.FECHAR, Outcome.parse("FECHAR"));
        // Um valor errado não fecha nem reinicia o mundo por engano.
        assertEquals(Outcome.CONTINUAR, Outcome.parse("apagar tudo"));
        assertEquals(Outcome.CONTINUAR, Outcome.parse(null));
        assertEquals(Outcome.CONTINUAR, Outcome.parse(resource("deathgame.yml").getString("fim.ao_vencer")));
        YamlConfiguration msg = resource("messages.yml");
        for (Outcome o : Outcome.values()) {
            assertNotNull(msg.getString("jogo.depois." + o.name().toLowerCase(Locale.ROOT)), o.name());
        }
    }

    @Test
    void oMapaComecaEmBrancoEERevealdoAPe() {
        var M = com.aincrad.floors.model.MapRules.class;
        // Setores de 64 blocos: 0 a 63 é o setor 0; -1 já é o setor -1.
        assertEquals(0, com.aincrad.floors.model.MapRules.sector(63, 64));
        assertEquals(1, com.aincrad.floors.model.MapRules.sector(64, 64));
        assertEquals(-1, com.aincrad.floors.model.MapRules.sector(-1, 64));
        long k = com.aincrad.floors.model.MapRules.key(-3, 7);
        assertEquals(-3, com.aincrad.floors.model.MapRules.sx(k));
        assertEquals(7, com.aincrad.floors.model.MapRules.sz(k));
        java.util.Set<Long> seen = new java.util.LinkedHashSet<>(List.of(com.aincrad.floors.model.MapRules.key(0, 0),
                com.aincrad.floors.model.MapRules.key(1, 0)));
        // O jogador está no setor (0,0): o vizinho a leste é conhecido, o resto está em branco.
        assertEquals(List.of("...", ".@#", "..."), com.aincrad.floors.model.MapRules.grid(seen, 0, 0, 1, "@", "#", "."));
        // Um andar de 256 x 256 blocos tem 16 setores; dois vistos são 13%.
        assertEquals(16, com.aincrad.floors.model.MapRules.total(0, 255, 0, 255, 64));
        assertEquals(13, com.aincrad.floors.model.MapRules.percent(2, 16));
        assertEquals(0, com.aincrad.floors.model.MapRules.percent(5, 0));
        // Os dados de mapa: vão para o item e voltam iguais; lixo é ignorado.
        String data = com.aincrad.floors.model.MapRules.encode(seen, 100);
        assertEquals("0,0;1,0", data);
        assertEquals(seen, com.aincrad.floors.model.MapRules.decode(data));
        assertEquals(1, com.aincrad.floors.model.MapRules.decode("5,5;lixo;;9").size());
        assertEquals("0,0", com.aincrad.floors.model.MapRules.encode(seen, 1));
        assertTrue(com.aincrad.floors.model.MapRules.decode(null).isEmpty());
        assertEquals("MapRules", M.getSimpleName());
    }

    @Test
    void osCreditosCortamAListaLonga() {
        assertEquals("", DeathGameRules.credits(List.of(), 3, "e mais <n>"));
        assertEquals("Kirito, Asuna", DeathGameRules.credits(List.of("Kirito", "Asuna"), 3, "e mais <n>"));
        assertEquals("Kirito, Asuna, Klein e mais 2",
                DeathGameRules.credits(List.of("Kirito", "Asuna", "Klein", "Agil", "Silica"), 3, "e mais <n>"));
    }
}
