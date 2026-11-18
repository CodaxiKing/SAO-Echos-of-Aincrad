package com.aincrad.rpg;

import com.aincrad.rpg.model.RareRules;
import com.aincrad.rpg.model.RareRules.Def;
import com.aincrad.rpg.model.SpawnRules;
import com.aincrad.rpg.model.SpawnRules.Entry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mobs raros (etapa 45) e os mobs que dependem do clima e do período (etapas 58 e 59), sem servidor. */
class RareRulesTest {

    private static final String YAML = """
            mobs:
              coelho_do_ragu:
                mob: RABBIT
                nome: "<gold>Ragout Rabbit"
                regiao: Campos_Andar1
                chance: 1.0
                level: 1
                drop: "life:carne_coelho_raro"
              sombra:
                mob: "mythic:Sombra"
                regiao: floresta
                chance: 0.05
                periodo: noite
                clima: Nevoa
                level: "8-10"
                foge: false
                drop: DIAMOND
                drop_quantidade: 3
              sem_regiao: { mob: RABBIT, chance: 0.1 }
              chance_ruim: { mob: RABBIT, regiao: x, chance: 2 }
              periodo_ruim: { mob: RABBIT, regiao: x, chance: 0.1, periodo: tarde }
            """;

    private static Map<String, Def> parse(List<String> errors) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(YAML);
        return RareRules.parse(y.getConfigurationSection("mobs"), errors);
    }

    @Test
    void oCoelhoDoRaguSoltaACarneQueAReceitaAceita() throws Exception {
        List<String> errors = new ArrayList<>();
        Map<String, Def> m = parse(errors);
        assertEquals(List.of("coelho_do_ragu", "sombra"), List.copyOf(m.keySet()));
        assertEquals(3, errors.size(), errors.toString());
        Def rabbit = m.get("coelho_do_ragu");
        assertEquals("RABBIT", rabbit.mob().id());
        assertEquals("campos_andar1", rabbit.region());
        assertTrue(rabbit.flees());
        assertEquals("life:carne_coelho_raro", rabbit.drop());
        assertEquals(1, rabbit.dropAmount());
        assertNull(rabbit.night());
        assertNull(rabbit.weather());
        // Com chance 100%, aparece em toda rodada.
        assertTrue(RareRules.appears(rabbit.chance(), 0.999));
        Def shadow = m.get("sombra");
        assertEquals(SpawnRules.Kind.MYTHIC, shadow.mob().kind());
        assertEquals(Boolean.TRUE, shadow.night());
        assertEquals("nevoa", shadow.weather());
        assertFalse(shadow.flees());
        assertEquals(10, shadow.maxLevel());
        assertFalse(RareRules.appears(shadow.chance(), 0.05));
        assertTrue(RareRules.appears(shadow.chance(), 0.049));
    }

    @Test
    void oArquivoDoPluginVemSemMobsRaros() {
        List<String> errors = new ArrayList<>();
        assertTrue(RareRules.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
                RareRulesTest.class.getResourceAsStream("/rare-mobs.yml"), StandardCharsets.UTF_8)).getConfigurationSection("mobs"), errors).isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void climaEPeriodo() {
        assertTrue(RareRules.matches(null, null, "chuva", true));
        assertTrue(RareRules.matches("nevoa", true, "nevoa", true));
        assertFalse(RareRules.matches("nevoa", true, "nevoa", false));
        assertFalse(RareRules.matches("nevoa", null, "limpo", true));
        assertTrue(RareRules.matches(null, false, "limpo", false));
        assertNull(RareRules.period("tarde"));
        assertNull(RareRules.period(" ")[0]);
        assertEquals(Boolean.FALSE, RareRules.period("Dia")[0]);
    }

    @Test
    void aNevoaTrocaATabelaDeMobsDaRegiao() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                regioes:
                  floresta:
                    mobs:
                      - { mob: WOLF, peso: 3 }
                      - { mob: "mythic:LoboDaNevoa", peso: 5, clima: nevoa }
                      - { mob: SKELETON, peso: 2, periodo: noite }
                      - { mob: COW, periodo: dia, clima: limpo }
                  ruim:
                    mobs:
                      - { mob: WOLF, periodo: madrugada }
                """);
        List<String> errors = new ArrayList<>();
        List<Entry> mobs = SpawnRules.parse(y.getConfigurationSection("regioes"), errors).get("floresta").mobs();
        assertEquals(2, errors.size(), errors.toString());
        // De dia, tempo limpo: lobo e vaca.
        assertEquals(List.of("WOLF", "COW"), SpawnRules.fitting(mobs, "limpo", false).stream().map(e -> e.mob().id()).toList());
        // De dia, com névoa: o lobo da névoa entra e a vaca sai.
        assertEquals(List.of("WOLF", "LoboDaNevoa"), SpawnRules.fitting(mobs, "nevoa", false).stream().map(e -> e.mob().id()).toList());
        // De noite, tempo limpo: lobo e esqueleto.
        assertEquals(List.of("WOLF", "SKELETON"), SpawnRules.fitting(mobs, "limpo", true).stream().map(e -> e.mob().id()).toList());
        // Com névoa, o lobo da névoa domina o sorteio (peso 5 de 8).
        List<Entry> fog = SpawnRules.fitting(mobs, "nevoa", false);
        assertEquals("LoboDaNevoa", SpawnRules.pick(fog, 0.5).mob().id());
        assertEquals("WOLF", SpawnRules.pick(fog, 0.2).mob().id());
    }
}
