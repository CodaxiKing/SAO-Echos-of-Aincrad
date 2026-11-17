package com.aincrad.floors;

import com.aincrad.floors.model.Floor;
import com.aincrad.floors.model.FloorRegistry;
import com.aincrad.floors.model.FloorRules;
import com.aincrad.floors.model.FloorRules.Mode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorRulesTest {

    private static FloorRegistry load(String yaml) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return FloorRegistry.load(c.getConfigurationSection("andares"));
    }

    @Test
    void osTresAndaresDeExemploCarregam() throws Exception {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/floors.yml"), StandardCharsets.UTF_8));
        FloorRegistry reg = FloorRegistry.load(c.getConfigurationSection("andares"));
        assertEquals(3, reg.size());
        assertTrue(reg.errors().isEmpty(), reg.errors().toString());
        Floor one = reg.get(1);
        assertEquals("world", one.world());
        assertFalse(one.byRegion());
        assertNull(one.spawn());
        assertTrue(reg.get(2).byRegion());
        assertEquals(Set.of("cidade_inicio", "urbus", "zumfut"), reg.cities());
        assertTrue(one.isOn("qualquer"));
    }

    @Test
    void errosDeCadastroAparecem() throws Exception {
        FloorRegistry reg = load("""
                andares:
                  1: { nome: "Um", tipo: mundo, mundo: world, chegada: "world,1,2" }
                  3: { nome: "Três", tipo: regiao }
                  x: { nome: "Xis" }
                  4: { nome: "Quatro", tipo: caverna }
                  5: { nome: "Cinco", tipo: regiao, regiao: Andar5, servidor: andar-5 }
                """);
        assertEquals(2, reg.size());
        assertEquals(5, reg.errors().size(), reg.errors().toString());
        assertTrue(reg.errors().stream().anyMatch(e -> e.contains("falta o andar 2")));
        assertEquals("andar5", reg.get(5).region());
        assertFalse(reg.get(5).isOn("andar-1"));
        assertTrue(reg.get(5).isOn("ANDAR-5"));
        assertNull(reg.get(1).spawn());
    }

    @Test
    void linhaDeFrenteSobeDeUmEmUm() {
        assertEquals(1, FloorRules.frontline(Set.of(), 5));
        assertEquals(3, FloorRules.frontline(Set.of(2, 3), 5));
        assertEquals(2, FloorRules.frontline(Set.of(2, 4), 5)); // o 4 liberado sem o 3 não conta
        assertEquals(3, FloorRules.frontline(Set.of(2, 3, 4), 3));
    }

    @Test
    void modoGlobal() {
        assertTrue(FloorRules.canEnter(Mode.GLOBAL, 1, Set.of(), Set.of(), false));
        assertFalse(FloorRules.canEnter(Mode.GLOBAL, 2, Set.of(), Set.of(), false));
        assertTrue(FloorRules.canEnter(Mode.GLOBAL, 2, Set.of(2), Set.of(), false));
        assertTrue(FloorRules.canEnter(Mode.GLOBAL, 2, Set.of(), Set.of(), true)); // bypass
        assertTrue(FloorRules.gateOffers(Mode.GLOBAL, 2, Set.of(2), Set.of(), Set.of(), false));
    }

    @Test
    void modoPorJogador() {
        assertFalse(FloorRules.canEnter(Mode.POR_JOGADOR, 2, Set.of(2), Set.of(), false));
        assertTrue(FloorRules.canEnter(Mode.POR_JOGADOR, 2, Set.of(2), Set.of(2), false));
        assertFalse(FloorRules.canEnter(Mode.POR_JOGADOR, 2, Set.of(), Set.of(2), false)); // acesso sem o andar liberado
        assertTrue(FloorRules.canEnter(Mode.POR_JOGADOR, 1, Set.of(), Set.of(), false));
    }

    @Test
    void modoMisto() {
        assertTrue(FloorRules.canEnter(Mode.MISTO, 2, Set.of(2), Set.of(), false));
        assertFalse(FloorRules.gateOffers(Mode.MISTO, 2, Set.of(2), Set.of(), Set.of(), false));
        assertTrue(FloorRules.gateOffers(Mode.MISTO, 2, Set.of(2), Set.of(), Set.of(2), false));
        assertTrue(FloorRules.gateOffers(Mode.MISTO, 1, Set.of(), Set.of(), Set.of(), false));
        assertEquals(Mode.MISTO, Mode.parse(" misto "));
        assertEquals(Mode.GLOBAL, Mode.parse("qualquer coisa"));
    }
}
