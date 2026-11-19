package com.aincrad.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ordem de carga de todos os plugins Aincrad, lida dos plugin.yml das pastas vizinhas.
 *
 * <p>O Paper recusa ligar o servidor quando {@code depend}, {@code softdepend} e {@code loadbefore}
 * formam um círculo ("Circular plugin loading detected"). Este teste falha antes disso.</p>
 */
class LoadOrderTest {

    /** A ordem combinada: um plugin só pode esperar pelos que vêm antes dele nesta lista. */
    private static final List<String> ORDER = List.of("AincradCore", "AincradEffects", "AincradUI", "AincradRPG",
            "AincradEconomy", "AincradFloors", "AincradBosses", "AincradItems", "AincradLife", "AincradReputation",
            "AincradDuels", "AincradSocial", "AincradSkills", "AincradQuests", "AincradWorld", "AincradAdmin");

    /** plugin → os plugins que precisam ligar antes dele. */
    private static Map<String, Set<String>> graph() {
        Map<String, Set<String>> before = new LinkedHashMap<>();
        Map<String, List<String>> loadBefore = new LinkedHashMap<>();
        File[] dirs = new File("..").listFiles((d, n) -> n.startsWith("aincrad-"));
        if (dirs == null) {
            return before;
        }
        for (File dir : dirs) {
            File f = new File(dir, "src/main/resources/plugin.yml");
            if (!f.isFile()) {
                continue;
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String name = y.getString("name");
            Set<String> deps = new LinkedHashSet<>(y.getStringList("depend"));
            deps.addAll(y.getStringList("softdepend"));
            before.put(name, deps);
            loadBefore.put(name, y.getStringList("loadbefore"));
        }
        // "A loadbefore B" é o mesmo que "B espera por A".
        loadBefore.forEach((a, list) -> list.forEach(b -> {
            if (before.containsKey(b)) {
                before.get(b).add(a);
            }
        }));
        // Plugins de fora (Vault, MythicMobs...) não entram na conta.
        before.values().forEach(deps -> deps.retainAll(before.keySet()));
        return before;
    }

    private static List<String> cycle(Map<String, Set<String>> g, String at, List<String> path, Set<String> done) {
        if (path.contains(at)) {
            List<String> loop = new ArrayList<>(path.subList(path.indexOf(at), path.size()));
            loop.add(at);
            return loop;
        }
        if (done.contains(at)) {
            return null;
        }
        path.add(at);
        for (String dep : g.getOrDefault(at, Set.of())) {
            List<String> loop = cycle(g, dep, path, done);
            if (loop != null) {
                return loop;
            }
        }
        path.remove(path.size() - 1);
        done.add(at);
        return null;
    }

    @Test
    void nenhumPluginEsperaEmCirculo() {
        Map<String, Set<String>> g = graph();
        Set<String> done = new HashSet<>();
        for (String name : g.keySet()) {
            List<String> loop = cycle(g, name, new ArrayList<>(), done);
            assertTrue(loop == null, "Dependências em círculo nos plugin.yml: " + loop);
        }
    }

    @Test
    void cadaPluginSoEsperaPelosQueVemAntes() {
        Map<String, Set<String>> g = graph();
        List<String> wrong = new ArrayList<>();
        g.forEach((name, deps) -> {
            assertTrue(ORDER.contains(name), name + " não está na ordem de carga: acrescente-o em LoadOrderTest e nos comentários dos plugin.yml.");
            for (String dep : deps) {
                if (ORDER.indexOf(dep) >= ORDER.indexOf(name)) {
                    wrong.add(name + " -> " + dep);
                }
            }
        });
        assertEquals(List.of(), wrong, "depend/softdepend/loadbefore contra a ordem de carga");
    }
}
