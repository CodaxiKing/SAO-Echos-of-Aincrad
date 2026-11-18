package com.aincrad.life;

import com.aincrad.life.pets.PetRules;
import com.aincrad.life.pets.PetRules.Ability;
import com.aincrad.life.pets.PetRules.Mode;
import com.aincrad.life.pets.PetRules.Species;
import com.aincrad.life.pets.PetRules.Tame;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras dos familiares (etapa 24), sem servidor. */
class PetRulesTest {

    private static final long DAY = 86_400_000L;

    // ------------------------------------------------------------------ 24.1 domar

    @Test
    void oArquivoDoPluginCarregaSemErro() {
        List<String> errors = new ArrayList<>();
        Map<String, Species> species = PetRules.parse(YamlConfiguration.loadConfiguration(new InputStreamReader(
                PetRulesTest.class.getResourceAsStream("/pets.yml"), StandardCharsets.UTF_8)), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(4, species.size());
        // Uma espécie para cada habilidade, e a chance é baixa (1 a 5%).
        for (Ability a : Ability.values()) {
            assertTrue(species.values().stream().anyMatch(s -> s.ability() == a), a.key());
        }
        for (Species s : species.values()) {
            assertTrue(s.chance() >= 0.01 && s.chance() <= 0.05, s.id());
        }
        assertEquals("PARROT", species.get("dragao_plumado").mob());
        assertEquals(Ability.CURA, species.get("dragao_plumado").ability());
    }

    @Test
    void comChanceCemPorCentoOMobViraFamiliarEUmSegundoERecusado() {
        // Chance 100%: qualquer sorteio doma.
        assertTrue(PetRules.tamed(1.0, 1.0, 0.999));
        // Chance 3% com o multiplicador de teste em 100.
        assertTrue(PetRules.tamed(0.03, 100, 0.999));
        // Chance normal: só o sorteio abaixo dela.
        assertTrue(PetRules.tamed(0.03, 1.0, 0.029));
        assertFalse(PetRules.tamed(0.03, 1.0, 0.03));
        assertFalse(PetRules.tamed(0.03, 0, 0.0));
        // Quem já tem um familiar não doma outro.
        assertEquals(Tame.OK, PetRules.canTame(false, false, true));
        assertEquals(Tame.JA_TEM, PetRules.canTame(true, false, true));
    }

    @Test
    void soValeComAComidaCertaEParaQuemNaoAtacouOMob() {
        assertEquals(Tame.COMIDA, PetRules.canTame(false, false, false));
        assertEquals(Tame.ATACOU, PetRules.canTame(false, true, true));
        // A comida errada nem conta como tentativa, mesmo para quem atacou.
        assertEquals(Tame.COMIDA, PetRules.canTame(true, true, false));
    }

    @Test
    void errosNoArquivoSaoApontados() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                familiares:
                  bom: {nome: Bom, mob: PARROT, comida: WHEAT_SEEDS, chance: 0.02, habilidade: cura, vida: 10}
                  mesmo_mob: {nome: M, mob: parrot, comida: APPLE, habilidade: bolha}
                  sem_habilidade: {nome: S, mob: CAT, comida: COD, habilidade: voar}
                  sem_comida: {nome: C, mob: FOX, habilidade: busca}
                  chance_alta: {nome: A, mob: RABBIT, comida: CARROT, chance: 7, habilidade: alerta}
                """);
        List<String> errors = new ArrayList<>();
        Map<String, Species> species = PetRules.parse(y, errors);
        assertEquals(List.of("bom", "chance_alta"), new ArrayList<>(species.keySet()));
        assertEquals(3, errors.size(), errors.toString());
        // A chance nunca passa de 100%.
        assertEquals(1.0, species.get("chance_alta").chance(), 1e-9);
    }

    // ------------------------------------------------------------------ 24.2 comportamento

    @Test
    void oFamiliarGanhaParteDoXpDoDonoESobeDeLevel() {
        assertEquals(20, PetRules.share(100, 0.2));
        assertEquals(0, PetRules.share(4, 0.2));
        // XP para o próximo level = 50 × level ^ 1,5.
        assertEquals(50, PetRules.xpToNext(1, 50, 1.5));
        assertEquals(141, PetRules.xpToNext(2, 50, 1.5));
        // 49 de XP: ainda no level 1.
        assertArrayEquals(new long[]{1, 49}, PetRules.addXp(1, 0, 49, 50, 1.5, 50));
        // 50: level 2, zerado.
        assertArrayEquals(new long[]{2, 0}, PetRules.addXp(1, 0, 50, 50, 1.5, 50));
        // 200 de uma vez: passa do 1 (50) e do 2 (141), sobram 9 no level 3.
        assertArrayEquals(new long[]{3, 9}, PetRules.addXp(1, 0, 200, 50, 1.5, 50));
        // No level máximo o XP não acumula.
        assertArrayEquals(new long[]{5, 0}, PetRules.addXp(5, 0, 99_999, 50, 1.5, 5));
        assertArrayEquals(new long[]{5, 0}, PetRules.addXp(4, 0, 99_999, 50, 1.5, 5));
    }

    @Test
    void oFamiliarFicaMaisResistenteComOLevel() {
        assertEquals(12, PetRules.maxHealth(12, 1, 1), 1e-9);
        assertEquals(21, PetRules.maxHealth(12, 10, 1), 1e-9);
        assertEquals(2, PetRules.heal(2, 1, 0.1), 1e-9);
        assertEquals(2.9, PetRules.heal(2, 10, 0.1), 1e-9);
    }

    @Test
    void comVinculoBaixoAHabilidadePara() {
        assertTrue(PetRules.abilityWorks(30, 30));
        assertTrue(PetRules.abilityWorks(100, 30));
        assertFalse(PetRules.abilityWorks(29, 30));
        assertFalse(PetRules.abilityWorks(0, 30));
    }

    @Test
    void oVinculoCaiSemComidaESobeAoSerAlimentado() {
        // Cai 1 de cada vez, nunca abaixo de zero.
        assertEquals(59, PetRules.bond(60, -1));
        assertEquals(0, PetRules.bond(0, -1));
        // Comida: +15, nunca acima de 100.
        assertEquals(44, PetRules.bond(29, 15));
        assertEquals(100, PetRules.bond(95, 15));
        // Do vínculo inicial (60) ao mínimo da habilidade (30) são 30 quedas: 5 horas sem comer, a 1 a cada 10 min.
        int bond = 60;
        int drops = 0;
        while (PetRules.abilityWorks(bond, 30)) {
            bond = PetRules.bond(bond, -1);
            drops++;
        }
        assertEquals(31, drops);
        // Uma comida devolve a habilidade.
        assertTrue(PetRules.abilityWorks(PetRules.bond(bond, 15), 30));
    }

    @Test
    void modosDoFamiliar() {
        assertEquals(Mode.FICAR, Mode.parse("ficar"));
        assertEquals(Mode.GUARDADO, Mode.parse("GUARDADO"));
        // Um valor estragado no banco não deixa o familiar sumir: ele segue o dono.
        assertEquals(Mode.SEGUIR, Mode.parse("???"));
        assertEquals(Mode.SEGUIR, Mode.parse(null));
    }

    // ------------------------------------------------------------------ 24.3 morte

    @Test
    void reviverDentroDoPrazoSimDepoisNao() {
        long died = 10 * DAY;
        assertTrue(PetRules.canRevive(died, died, 3));
        assertTrue(PetRules.canRevive(died, died + 2 * DAY, 3));
        assertTrue(PetRules.canRevive(died, died + 3 * DAY, 3));
        assertFalse(PetRules.canRevive(died, died + 3 * DAY + 1, 3));
        assertFalse(PetRules.canRevive(died, died + 30 * DAY, 3));
        // Um familiar vivo não tem o que reviver.
        assertFalse(PetRules.canRevive(0, died, 3));
    }
}
