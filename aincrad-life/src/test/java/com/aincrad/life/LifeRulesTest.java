package com.aincrad.life;

import com.aincrad.life.inn.Inn;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.model.TreeDef;
import com.aincrad.life.station.StationType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras da base do AincradLife (etapa 22), sem servidor. */
class LifeRulesTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                LifeRulesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    private static TreeDef tree(Profession p) {
        List<String> errors = new ArrayList<>();
        TreeDef t = TreeDef.parse(p, resource("trees/" + p.key() + ".yml"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return t;
    }

    // ------------------------------------------------------------------ 22.2 proficiência

    @Test
    void ganhoMaximoNoLimiteEZeroNoFacilDemais() {
        // No limite (ou acima): ganho máximo.
        assertEquals(8, Profession.gain(300, 300, true, 8, 200, 0.5));
        assertEquals(8, Profession.gain(300, 450, true, 8, 200, 0.5));
        // Fácil demais (200 ou mais abaixo): zero.
        assertEquals(0, Profession.gain(500, 300, true, 8, 200, 0.5));
        assertEquals(0, Profession.gain(900, 300, true, 8, 200, 0.5));
        // No meio da faixa: metade. Falha: metade do normal.
        assertEquals(4, Profession.gain(400, 300, true, 8, 200, 0.5));
        assertEquals(4, Profession.gain(300, 300, false, 8, 200, 0.5));
        // Nunca passa de 1000.
        assertEquals(3, Profession.gain(997, 1000, true, 8, 200, 0.5));
        assertEquals(0, Profession.gain(1000, 1000, true, 8, 200, 0.5));
    }

    @Test
    void ranksEPontosDeOficio() {
        assertEquals(Profession.Rank.APRENDIZ, Profession.Rank.of(249.9));
        assertEquals(Profession.Rank.PRATICANTE, Profession.Rank.of(250));
        assertEquals(Profession.Rank.MESTRE, Profession.Rank.of(999));
        assertEquals(Profession.Rank.COMPLETO, Profession.Rank.of(1000));
        assertEquals(Profession.Rank.ESPECIALISTA, Profession.Rank.PRATICANTE.next());
        assertEquals(0, Profession.craftPoints(49));
        assertEquals(5, Profession.craftPoints(250));
        assertEquals(20, Profession.craftPoints(1000));
        assertEquals(Profession.FERREIRO, Profession.parse("Forja"));
        assertEquals(Profession.CULINARIA, Profession.parse("culinária"));
    }

    // ------------------------------------------------------------------ 22.6 aprender antes de fazer

    @Test
    void aprenderExigeEstudoEProduzirExigeMais() {
        Learnable l = new Learnable("projeto", "Espada", "Espada de Aço", "Projeto", Profession.FERREIRO, 250, 300, false, null, null);
        Set<String> known = new HashSet<>();
        assertEquals(Learnable.Check.PROFICIENCIA, l.canLearn(249, known, Set.of()));
        assertEquals(Learnable.Check.OK, l.canLearn(250, known, Set.of()));
        known.add(l.key());
        assertEquals("projeto:espada", l.key());
        assertEquals(Learnable.Check.JA_SABE, l.canLearn(250, known, Set.of()));
        // Conhece, mas ainda sem a proficiência de produção.
        assertFalse(l.canProduce(299, known));
        assertTrue(l.canProduce(300, known));
        assertFalse(l.canProduce(1000, Set.of()));
    }

    @Test
    void itemDeCaminhoFechadoNaoPodeSerAprendido() {
        Learnable l = new Learnable("projeto", "armadura", "Armadura Rara", "Projeto", Profession.FERREIRO, 250, 300, false, "armoreiro", null);
        assertEquals(Learnable.Check.CAMINHO_FECHADO, l.canLearn(900, Set.of(), Set.of("caminho:armeiro")));
        assertEquals(Learnable.Check.CAMINHO_FECHADO, l.canLearn(900, Set.of(), Set.of())); // ainda sem caminho
        assertEquals(Learnable.Check.OK, l.canLearn(900, Set.of(), Set.of("caminho:armoreiro")));
    }

    // ------------------------------------------------------------------ 22.7 árvore

    @Test
    void asQuatroArvoresTemAMesmaForma() {
        for (Profession p : Profession.values()) {
            TreeDef t = tree(p);
            assertEquals(3, t.paths().size(), p.key());
            assertEquals(5, t.level(null, null).size(), p.key());
            for (TreeDef.Path path : t.paths()) {
                assertEquals(2, path.specs().size(), p.key() + " " + path.id());
                assertEquals(4, t.level(path.id(), null).size(), p.key() + " " + path.id());
                for (String spec : path.specs().keySet()) {
                    List<TreeDef.Node> nodes = t.level(path.id(), spec);
                    assertEquals(4, nodes.size(), p.key() + " " + spec);
                    // O nó final só aos 1000, e é uma técnica.
                    TreeDef.Node last = nodes.get(3);
                    assertEquals(1000, last.proficiency());
                    assertEquals("tecnica", last.kind());
                    // Um ramo inteiro custa mais que os 20 pontos: dois artesãos do mesmo ramo ficam diferentes.
                    int cost = 0;
                    for (TreeDef.Node n : t.level(null, null)) {
                        cost += n.cost();
                    }
                    for (TreeDef.Node n : t.level(path.id(), null)) {
                        cost += n.cost();
                    }
                    for (TreeDef.Node n : nodes) {
                        cost += n.cost();
                    }
                    assertTrue(cost > 20, p.key() + " " + spec + " custa " + cost);
                }
            }
        }
    }

    @Test
    void escolherArmeiroFechaArmoreiroERefinador() {
        TreeDef t = tree(Profession.FERREIRO);
        Set<String> owned = new HashSet<>();
        assertEquals(TreeDef.Deny.PROFICIENCIA, t.canChoosePath("armeiro", 249, owned));
        assertEquals(TreeDef.Deny.OK, t.canChoosePath("armeiro", 250, owned));
        owned.add("caminho:armeiro");
        assertEquals(TreeDef.Deny.JA_ESCOLHEU, t.canChoosePath("armoreiro", 900, owned));
        // Nó de outro caminho: fechado para sempre.
        assertEquals(TreeDef.Deny.CAMINHO_FECHADO, t.canBuy("projetos_de_armaduras", 900, owned));
        assertTrue(t.closed(t.node("pedra_de_amolar"), owned));
        // Nó do próprio caminho: pode.
        assertEquals(TreeDef.Deny.OK, t.canBuy("projetos_de_armas", 250, owned));
        // Especialização: só do caminho escolhido, aos 500.
        assertEquals(TreeDef.Deny.CAMINHO_FECHADO, t.canChooseSpec("placas", 900, owned));
        assertEquals(TreeDef.Deny.PROFICIENCIA, t.canChooseSpec("laminas", 499, owned));
        assertEquals(TreeDef.Deny.OK, t.canChooseSpec("laminas", 500, owned));
        assertEquals(TreeDef.Deny.SEM_CAMINHO, t.canChooseSpec("laminas", 900, new HashSet<>()));
        // Um projeto de armadura rara é recusado para o Armeiro.
        assertFalse(TreeDef.allows("armoreiro", null, owned));
        assertTrue(TreeDef.allows("armeiro", null, owned));
    }

    @Test
    void comprarNoGastaPontosERespeitaRequisitos() {
        TreeDef t = tree(Profession.FERREIRO);
        Set<String> owned = new HashSet<>();
        // 100 de proficiência = 2 pontos.
        assertEquals(2, t.free(100, owned));
        assertEquals(TreeDef.Deny.OK, t.canBuy("mao_firme", 100, owned));
        owned.add("mao_firme");
        assertEquals(TreeDef.Deny.JA_TEM, t.canBuy("mao_firme", 100, owned));
        owned.add("fole_eficiente");
        assertEquals(0, t.free(100, owned));
        assertEquals(TreeDef.Deny.PONTOS, t.canBuy("olho_para_o_metal", 100, owned));
        assertEquals(TreeDef.Deny.PROFICIENCIA, t.canBuy("aproveitar_sobras", 100, owned));
        // Nó de caminho antes de escolher o caminho.
        assertEquals(TreeDef.Deny.SEM_CAMINHO, t.canBuy("projetos_de_armas", 900, owned));
        // Nó que pede o desbloqueio do caminho antes.
        owned.add("caminho:armeiro");
        assertEquals(TreeDef.Deny.REQUISITO, t.canBuy("fio_apurado", 900, owned));
        owned.add("projetos_de_armas");
        assertEquals(TreeDef.Deny.OK, t.canBuy("fio_apurado", 900, owned));
        // Efeitos somam.
        assertEquals(0.05, t.modifier("janela_perfeita", owned), 1e-9);
        assertEquals(0, t.modifier("nao_existe", owned));
        assertEquals(3, t.spent(owned));
    }

    // ------------------------------------------------------------------ 22.9 bancadas

    @Test
    void bancadasDeExemploTemModeloProvisorioNoPack() {
        YamlConfiguration y = resource("stations.yml");
        List<String> errors = new ArrayList<>();
        for (String id : y.getConfigurationSection("estacoes").getKeys(false)) {
            StationType t = StationType.parse(id, y.getConfigurationSection("estacoes." + id), errors);
            String path = t.model().substring(t.model().indexOf(':') + 1);
            for (String f : new String[]{"items/" + path + ".json", "models/" + path + ".json"}) {
                assertTrue(new File("src/main/resources/resourcepack/assets/aincrad/" + f).isFile(), id + ": falta " + f);
            }
            assertFalse(t.marks().isEmpty(), id);
        }
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(7, y.getConfigurationSection("estacoes").getKeys(false).size());
    }

    @Test
    void ancorasGiramComABancada() {
        double[] p = {1, 2, 0};
        assertArrayEquals(new double[]{1, 2, 0}, StationType.rotate(p, 0), 1e-9);
        assertArrayEquals(new double[]{0, 2, 1}, StationType.rotate(p, 90), 1e-9);
        assertArrayEquals(new double[]{-1, 2, 0}, StationType.rotate(p, 180), 1e-9);
        // A bancada fica de frente para quem a colocou.
        assertEquals(180f, StationType.snapYaw(0f));
        assertEquals(0f, StationType.snapYaw(170f));
        assertEquals(270f, StationType.snapYaw(95f));
    }

    // ------------------------------------------------------------------ 22.3 estalagens

    @Test
    void aluguelDeUmASeteDias() {
        List<String> errors = new ArrayList<>();
        YamlConfiguration y = resource("inns.yml");
        Inn inn = Inn.parse("inicio", y.getConfigurationSection("estalagens.inicio"), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(3, inn.rooms().size());
        assertEquals(50, inn.cost(1));
        assertEquals(350, inn.cost(7));
        assertEquals(350, inn.cost(30)); // no máximo 7 dias
        assertEquals(50, inn.cost(0));
        assertNotNull(inn.rooms().get("q1"));

        long now = 1_000_000L;
        // Quarto novo: 3 dias a partir de agora.
        assertEquals(now + 3 * Inn.DAY, Inn.extend(0, now, 3));
        // Renovar soma ao que resta, mas nunca passa de 7 dias à frente.
        assertEquals(now + 5 * Inn.DAY, Inn.extend(now + 2 * Inn.DAY, now, 3));
        assertEquals(now + 7 * Inn.DAY, Inn.extend(now + 6 * Inn.DAY, now, 7));
    }

    @Test
    void soOHospedeEOsConvidadosAbremOQuarto() {
        UUID tenant = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Inn.Rental r = new Inn.Rental("inicio", "q1", tenant, "Kirito", 10_000, guest + ", lixo");
        assertTrue(r.canUse(tenant, 5_000));
        assertTrue(r.canUse(guest, 5_000));
        assertFalse(r.canUse(stranger, 5_000));
        // Aluguel vencido: ninguém.
        assertFalse(r.canUse(tenant, 10_000));
        assertEquals(guest.toString(), r.guestsText());
        // Aviso uma hora antes.
        Inn.Rental soon = new Inn.Rental("inicio", "q1", tenant, "Kirito", 4_000_000, "");
        assertTrue(soon.endingSoon(4_000_000 - 3_600_000));
        assertFalse(soon.endingSoon(4_000_000 - 3_600_001));
        assertFalse(soon.endingSoon(4_000_000));
    }
}
