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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As quests do pacote do andar 3 (conteudo/andar3), lidas junto com as dos andares 1 e 2. */
class Andar3PackTest {

    private static final File PACK = new File("../conteudo/andar3/plugins");
    private static final Set<String> NPCS = Set.of(
            "instrutor", "guarda_inicio", "curtidor", "estalajadeiro", "pescador", "mae_de_agatha", "diabel",
            "guarda_urbus", "curtidor_urbus", "cozinheira_marome", "eremita", "capitao_taran",
            "guarda_zumfut", "lenhador_zumfut", "escrivao_zumfut", "kizmel", "comandante_elfico", "capitao_posto");
    private static final Set<String> PLACES = Set.of("horunka", "tolbana", "labirinto_andar1", "urbus", "marome", "taran", "labirinto_andar2",
            "zumfut", "acampamento_elfico", "posto_avancado", "labirinto_andar3");

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

    /** Os três arquivos, como o plugin os lê: um mapa só. */
    private static Map<String, QuestDef> quests() {
        Map<String, QuestDef> all = new LinkedHashMap<>();
        for (String file : List.of("andar1.yml", "andar2.yml", "andar3.yml")) {
            List<String> errors = new ArrayList<>();
            Map<String, QuestDef> q = QuestDef.parse(yaml("AincradQuests/quests/" + file), file, errors);
            assertTrue(errors.isEmpty(), errors.toString());
            for (String id : q.keySet()) {
                assertFalse(all.containsKey(id), "a quest " + id + " está em dois arquivos");
            }
            all.putAll(q);
        }
        return all;
    }

    @Test
    void tudoQueAsQuestsCitamExiste() {
        Map<String, QuestDef> quests = quests();
        Set<String> mobs = new HashSet<>();
        YamlConfiguration spawns = yaml("AincradRPG/spawns.yml");
        for (String region : spawns.getConfigurationSection("regioes").getKeys(false)) {
            for (Map<?, ?> m : spawns.getMapList("regioes." + region + ".mobs")) {
                mobs.add(String.valueOf(m.get("id")));
            }
        }
        Set<String> items = yaml("AincradRPG/items.yml").getConfigurationSection("itens").getKeys(false);
        Set<String> bosses = new HashSet<>(yaml("AincradBosses/bosses/andar1.yml").getKeys(false));
        bosses.addAll(yaml("AincradBosses/bosses/andar2.yml").getKeys(false));
        bosses.addAll(yaml("AincradBosses/bosses/andar3.yml").getKeys(false));
        for (QuestDef q : quests.values()) {
            assertTrue(NPCS.contains(q.npc()), q.id() + ": NPC " + q.npc() + " não está no guia");
            assertTrue(q.after() == null || quests.containsKey(q.after()), q.id() + ": quest anterior " + q.after());
            assertTrue(q.floor() <= 3, q.id() + ": pede andar acima do 3");
            for (Stage s : q.stages()) {
                String where = q.id() + " (" + s.text() + ")";
                assertFalse(s.text().isBlank(), q.id() + ": etapa sem texto");
                switch (s.goal()) {
                    case MATAR -> assertTrue(s.target() == null || mobs.contains(s.target()), where + ": mob " + s.target());
                    case COLETAR, ENTREGAR -> assertNotNull(material(s.target()), where + ": item " + s.target());
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
    void aHistoriaDoAndar3ContinuaADoAndar2() {
        Map<String, QuestDef> quests = quests();
        List<String> chain = new ArrayList<>();
        QuestDef q = quests.get("o_treant_maligno");
        while (q != null) {
            chain.add(0, q.id());
            assertEquals(Kind.HISTORIA, q.kind(), q.id());
            q = q.after() == null ? null : quests.get(q.after());
        }
        // Do Nerius para trás chega-se à primeira quest do andar 1, passando pelo Illfang e pelo Asterius.
        assertEquals("primeiro_dia", chain.get(0));
        assertTrue(chain.contains("o_senhor_dos_kobolds"));
        assertTrue(chain.indexOf("o_rei_taurus") + 1 == chain.indexOf("chegada_a_zumfut"));
        assertEquals(List.of("chegada_a_zumfut", "a_floresta_que_anda", "a_cavaleira_na_nevoa", "a_chave_de_jade", "a_linha_de_frente",
                "raizes_da_torre", "os_cavaleiros_da_floresta", "o_treant_maligno"), chain.subList(chain.indexOf("chegada_a_zumfut"), chain.size()));
        // A primeira quest do andar só existe com o andar 3 liberado.
        assertEquals(3, quests.get("chegada_a_zumfut").floor());
        int level = 0;
        long xp = 0;
        for (String id : chain) {
            QuestDef step = quests.get(id);
            assertTrue(step.minLevel() == 0 || step.minLevel() >= level, id + ": level pedido cai");
            level = Math.max(level, step.minLevel());
            assertTrue(step.reward().xp() >= xp, id + ": dá menos XP que a anterior");
            xp = step.reward().xp();
        }
        assertTrue(quests.get("o_treant_maligno").stages().get(0).matches(Goal.CHEFE, "nerius"));
        assertTrue(quests.get("o_sabre_da_cavaleira").reward().items().contains("rpg:sabre_da_cavaleira"));
        assertTrue(quests.get("lenha_para_zumfut").reward().items().contains("rpg:machado_do_lenhador"));
        // A quest que o AincradSocial pede para fundar uma guilda (guilds.yml) existe e abre com o andar 3.
        assertEquals("chegada_a_zumfut", quests.get("selo_da_guilda").after());
        assertTrue(quests.get("a_rainha_das_aranhas").stages().get(0).matches(Goal.CHEFE, "nephila_regina"));
    }
}
