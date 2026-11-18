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

/** As quests do pacote do andar 2 (conteudo/andar2), lidas junto com as do andar 1. */
class Andar2PackTest {

    private static final File PACK = new File("../conteudo/andar2/plugins");
    private static final Set<String> NPCS = Set.of(
            "instrutor", "guarda_inicio", "curtidor", "estalajadeiro", "pescador", "mae_de_agatha", "diabel",
            "guarda_urbus", "curtidor_urbus", "cozinheira_marome", "eremita", "capitao_taran");
    private static final Set<String> PLACES = Set.of("horunka", "tolbana", "labirinto_andar1", "urbus", "marome", "taran", "labirinto_andar2");

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

    /** Os dois arquivos, como o plugin os lê: um mapa só. */
    private static Map<String, QuestDef> quests() {
        Map<String, QuestDef> all = new LinkedHashMap<>();
        for (String file : List.of("andar1.yml", "andar2.yml")) {
            List<String> errors = new ArrayList<>();
            Map<String, QuestDef> q = QuestDef.parse(yaml("AincradQuests/quests/" + file), file, errors);
            assertTrue(errors.isEmpty(), errors.toString());
            for (String id : q.keySet()) {
                assertFalse(all.containsKey(id), "a quest " + id + " está nos dois arquivos");
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
        for (QuestDef q : quests.values()) {
            assertTrue(NPCS.contains(q.npc()), q.id() + ": NPC " + q.npc() + " não está no guia");
            assertTrue(q.after() == null || quests.containsKey(q.after()), q.id() + ": quest anterior " + q.after());
            assertTrue(q.floor() <= 2, q.id() + ": pede andar acima do 2");
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
    void aHistoriaDoAndar2ContinuaADoAndar1() {
        Map<String, QuestDef> quests = quests();
        List<String> chain = new ArrayList<>();
        QuestDef q = quests.get("o_rei_taurus");
        while (q != null) {
            chain.add(0, q.id());
            assertEquals(Kind.HISTORIA, q.kind(), q.id());
            q = q.after() == null ? null : quests.get(q.after());
        }
        // Do Rei Taurus para trás chega-se à primeira quest do andar 1, passando pelo Illfang.
        assertEquals("primeiro_dia", chain.get(0));
        assertTrue(chain.indexOf("o_senhor_dos_kobolds") + 1 == chain.indexOf("chegada_a_urbus"));
        assertEquals(List.of("chegada_a_urbus", "bois_do_planalto", "caminho_para_marome", "estrada_para_taran",
                "golpeadores_do_labirinto", "a_guarda_do_rei", "o_rei_taurus"), chain.subList(chain.indexOf("chegada_a_urbus"), chain.size()));
        // A primeira quest do andar só existe com o andar 2 liberado.
        assertEquals(2, quests.get("chegada_a_urbus").floor());
        int level = 0;
        long xp = 0;
        for (String id : chain) {
            QuestDef step = quests.get(id);
            assertTrue(step.minLevel() == 0 || step.minLevel() >= level, id + ": level pedido cai");
            level = Math.max(level, step.minLevel());
            assertTrue(step.reward().xp() >= xp, id + ": dá menos XP que a anterior");
            xp = step.reward().xp();
        }
        assertTrue(quests.get("o_rei_taurus").stages().get(0).matches(Goal.CHEFE, "asterius"));
        assertTrue(quests.get("a_rocha_do_eremita").reward().items().contains("rpg:lamina_do_eremita"));
        assertTrue(quests.get("o_touro_da_pradaria").stages().get(0).matches(Goal.CHEFE, "bullbous_bow"));
    }
}
