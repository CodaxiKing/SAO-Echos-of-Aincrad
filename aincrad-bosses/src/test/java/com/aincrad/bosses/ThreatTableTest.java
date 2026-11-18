package com.aincrad.bosses;

import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.ThreatTable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tabela de ameaça e os chefes do MythicMobs, sem servidor. */
class ThreatTableTest {

    private static final UUID TANK = new UUID(0, 1);
    private static final UUID DPS = new UUID(0, 2);
    private static final long T0 = 1_000_000L;

    @Test
    void oChefeAtacaQuemTemMaisAmeacaEOTanqueSegura() {
        ThreatTable t = new ThreatTable();
        assertEquals(Optional.empty(), t.selectTarget(null, 1.15, id -> true));
        t.add(TANK, 100, T0);
        t.add(DPS, 60, T0);
        assertEquals(Optional.of(TANK), t.selectTarget(null, 1.15, id -> true));
        // O atacante passa o tanque por pouco (110 x 100): com margem de 15%, o alvo não troca.
        t.add(DPS, 50, T0);
        assertEquals(Optional.of(TANK), t.selectTarget(TANK, 1.15, id -> true));
        // Passou de 115: rouba o alvo.
        t.add(DPS, 10, T0);
        assertEquals(Optional.of(DPS), t.selectTarget(TANK, 1.15, id -> true));
        // Dano zero ou negativo não conta.
        t.add(TANK, 0, T0);
        t.add(TANK, -5, T0);
        assertEquals(100, t.of(TANK), 1e-9);
    }

    @Test
    void quemSaiDaLutaPerdeOAlvo() {
        ThreatTable t = new ThreatTable();
        t.add(TANK, 100, T0);
        t.add(DPS, 10, T0);
        // O tanque morreu: o próximo da tabela assume, mesmo com menos ameaça.
        assertEquals(Optional.of(DPS), t.selectTarget(TANK, 1.15, id -> !id.equals(TANK)));
        t.prune(id -> !id.equals(TANK));
        assertEquals(0, t.of(TANK), 1e-9);
        t.prune(id -> false);
        assertTrue(t.isEmpty());
    }

    @Test
    void aAmeacaCaiDepoisDaCarencia() {
        ThreatTable t = new ThreatTable();
        t.add(TANK, 100, T0);
        // Dentro da carência de 3 s: não cai.
        t.decay(50, 1, 3, T0 + 2_000);
        assertEquals(100, t.of(TANK), 1e-9);
        // Passou a carência: 50% por segundo.
        t.decay(50, 1, 3, T0 + 3_000);
        assertEquals(50, t.of(TANK), 1e-9);
        t.decay(50, 2, 3, T0 + 5_000);
        assertEquals(12.5, t.of(TANK), 1e-9);
        // Um golpe novo reinicia a carência.
        t.add(TANK, 1, T0 + 6_000);
        t.decay(50, 1, 3, T0 + 7_000);
        assertEquals(13.5, t.of(TANK), 1e-9);
        // Perda de 0% desliga; quem chega perto de zero sai da tabela.
        t.decay(0, 100, 0, T0 + 99_000);
        assertEquals(13.5, t.of(TANK), 1e-9);
        t.decay(100, 1, 0, T0 + 99_000);
        assertTrue(t.isEmpty());
    }

    @Test
    void oPainelMostraAFatiaDeCadaUm() {
        ThreatTable t = new ThreatTable();
        t.add(DPS, 25, T0);
        t.add(TANK, 75, T0);
        assertEquals(List.of(TANK, DPS), new ArrayList<>(t.snapshot().keySet()));
        assertEquals(75, t.shares().get(TANK));
        assertEquals(25, t.shares().get(DPS));
        assertTrue(new ThreatTable().shares().isEmpty());
    }

    @Test
    void chefeComCorpoEGolpesDoMythicMobs() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                illfang:
                  andar: 1
                  vida: 600
                  mob: "mythic:IllfangKoboldLord"
                  fases:
                    - vida: 100
                      habilidades:
                        - { tipo: mythic, skill: Illfang_Nodachi, aviso_skill: Illfang_Aviso, aviso_ticks: 40, recarga: 12 }
                        - { tipo: mythic, recarga: 12 }
                        - { tipo: area, recarga: 10 }
                """);
        List<String> errors = new ArrayList<>();
        BossDef b = BossDef.parse("illfang", y.getConfigurationSection("illfang"), errors);
        assertNotNull(b);
        // O id do Mythic mantém as maiúsculas e minúsculas do arquivo.
        assertTrue(b.isMythic());
        assertEquals("IllfangKoboldLord", b.mythicId());
        // A habilidade mythic sem "skill" é recusada com um erro; as outras duas ficam.
        assertEquals(1, errors.size());
        List<BossDef.Skill> skills = b.phases().get(0).skills();
        assertEquals(2, skills.size());
        assertEquals(BossDef.SkillType.MYTHIC, skills.get(0).type());
        assertEquals("Illfang_Nodachi", skills.get(0).mob());
        assertEquals("Illfang_Aviso", skills.get(0).warnSkill());
        assertEquals(40, skills.get(0).warnTicks());
        assertEquals("", skills.get(1).warnSkill());
    }

    @Test
    void chefeDoMinecraftContinuaIgual() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new InputStreamReader(
                ThreatTableTest.class.getResourceAsStream("/bosses/andar1.yml"), StandardCharsets.UTF_8));
        BossDef b = BossDef.parse("illfang", y.getConfigurationSection("illfang"), new ArrayList<>());
        assertNotNull(b);
        assertFalse(b.isMythic());
        assertEquals("", b.mythicId());
        assertEquals("WITHER_SKELETON", b.mob());
        // A config e as mensagens novas existem.
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                ThreatTableTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertTrue(cfg.getBoolean("ameaca.ativo"));
        assertEquals(1.5, cfg.getDouble("punicao.multiplicador"), 1e-9);
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(new InputStreamReader(
                ThreatTableTest.class.getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (String k : new String[] {"luta.ameaca", "luta.ameaca_voce", "luta.ameaca_outro", "luta.punicao", "guia.tipo.mythic"}) {
            assertNotNull(msg.getString(k), k);
        }
        assertNull(msg.getString("luta.nao_existe"));
    }
}
