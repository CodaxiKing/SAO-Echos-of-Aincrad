package com.aincrad.quests;

import com.aincrad.quests.model.QuestDef;
import com.aincrad.quests.model.QuestDef.Goal;
import com.aincrad.quests.model.QuestDef.Kind;
import com.aincrad.quests.model.QuestDef.Stage;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As quests do pacote do andar 1 (conteudo/andar1): carregam sem erro, e cada mob, item, região,
 * chefe e quest anterior que elas citam existe nos outros arquivos do pacote.
 */
class Andar1PackTest {

    private static final File PACK = new File("../conteudo/andar1/plugins");
    /** Os NPCs que o guia de montagem manda criar. */
    private static final Set<String> NPCS = Set.of("instrutor", "guarda_inicio", "curtidor", "estalajadeiro", "pescador",
            "mae_de_agatha", "diabel");
    /** As regiões que o guia manda criar e que servem a uma etapa "chegar". */
    private static final Set<String> PLACES = Set.of("cidade_inicio", "horunka", "tolbana", "campos_oeste", "floresta_horunka",
            "planicie_tolbana", "labirinto_andar1", "labirinto_andar1_topo");

    /** O material pelo nome, sem precisar de servidor. Null se não existe. */
    private static Material material(String name) {
        try {
            return Material.valueOf(String.valueOf(name).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static YamlConfiguration yaml(String path) {
        File f = new File(PACK, path);
        assertTrue(f.isFile(), "falta " + f);
        return YamlConfiguration.loadConfiguration(f);
    }

    private static Map<String, QuestDef> quests() {
        List<String> errors = new ArrayList<>();
        Map<String, QuestDef> q = QuestDef.parse(yaml("AincradQuests/quests/andar1.yml"), "andar1.yml", errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return q;
    }

    /** Os "id" das linhas do spawns.yml, por região. */
    private static Set<String> mobIds() {
        Set<String> ids = new HashSet<>();
        YamlConfiguration y = yaml("AincradRPG/spawns.yml");
        for (String region : y.getConfigurationSection("regioes").getKeys(false)) {
            for (Map<?, ?> m : y.getMapList("regioes." + region + ".mobs")) {
                ids.add(String.valueOf(m.get("id")));
            }
        }
        return ids;
    }

    @Test
    void tudoQueAsQuestsCitamExiste() {
        Map<String, QuestDef> quests = quests();
        Set<String> mobs = mobIds();
        Set<String> items = yaml("AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        Set<String> bosses = yaml("AincradBosses/bosses/andar1.yml").getKeys(false);
        for (QuestDef q : quests.values()) {
            assertTrue(NPCS.contains(q.npc()), q.id() + ": NPC " + q.npc() + " não está no guia");
            assertTrue(q.after() == null || quests.containsKey(q.after()), q.id() + ": quest anterior " + q.after());
            assertTrue(q.floor() <= 1, q.id() + ": pede andar acima do 1");
            assertFalse(q.stages().isEmpty(), q.id());
            for (Stage s : q.stages()) {
                String where = q.id() + " (" + s.text() + ")";
                assertFalse(s.text().isBlank(), q.id() + ": etapa sem texto");
                switch (s.goal()) {
                    case MATAR -> assertTrue(s.target() == null || mobs.contains(s.target()), where + ": mob " + s.target());
                    case COLETAR, ENTREGAR -> {
                        Material m = material(s.target());
                        assertTrue(m != null, where + ": item " + s.target());
                    }
                    case FALAR -> assertTrue(NPCS.contains(s.target()), where + ": NPC " + s.target());
                    case CHEGAR -> assertTrue(PLACES.contains(s.target()), where + ": região " + s.target());
                    case CHEFE -> assertTrue(s.target() == null || bosses.contains(s.target()), where + ": chefe " + s.target());
                    default -> {
                    }
                }
            }
            for (String it : q.reward().items()) {
                if (it.startsWith("rpg:")) {
                    assertTrue(items.contains(it.substring(4)), q.id() + ": recompensa " + it);
                } else {
                    assertNotNull(material(it.split(" ")[0]), q.id() + ": recompensa " + it);
                }
            }
            assertTrue(q.reward().xp() > 0 && q.reward().col() > 0, q.id() + ": sem XP ou Col");
        }
    }

    @Test
    void aHistoriaVaiDaPracaAoChefeSemBuraco() {
        Map<String, QuestDef> quests = quests();
        // Do chefe para trás, pela quest anterior, chega-se à primeira.
        List<String> chain = new ArrayList<>();
        QuestDef q = quests.get("o_senhor_dos_kobolds");
        while (q != null) {
            chain.add(0, q.id());
            assertEquals(Kind.HISTORIA, q.kind(), q.id());
            q = q.after() == null ? null : quests.get(q.after());
        }
        assertEquals(List.of("primeiro_dia", "lobos_na_estrada", "caminho_para_horunka", "estrada_para_tolbana",
                "batedores_do_labirinto", "a_porta_do_chefe", "o_senhor_dos_kobolds"), chain);
        // O level pedido só sobe ao longo da história, e a recompensa também.
        int level = 0;
        long xp = 0;
        for (String id : chain) {
            QuestDef step = quests.get(id);
            assertTrue(step.minLevel() == 0 || step.minLevel() >= level, id + ": level pedido cai");
            level = Math.max(level, step.minLevel());
            assertTrue(step.reward().xp() >= xp, id + ": dá menos XP que a anterior");
            xp = step.reward().xp();
        }
        assertTrue(quests.get("o_senhor_dos_kobolds").stages().get(0).matches(Goal.CHEFE, "illfang"));
        // A quest da Anneal Blade.
        assertTrue(quests.get("remedio_secreto_da_floresta").reward().items().contains("rpg:anneal_blade"));
    }

    @Test
    void haDiariasEUmaSemanal() {
        Map<String, QuestDef> quests = quests();
        assertEquals(3, quests.values().stream().filter(x -> x.kind() == Kind.DIARIA).count());
        assertEquals(1, quests.values().stream().filter(x -> x.kind() == Kind.SEMANAL).count());
        // As que mandam matar no labirinto valem para a party.
        assertTrue(quests.get("batedores_do_labirinto").party());
        assertTrue(quests.get("limpeza_do_labirinto").party());
    }
}
