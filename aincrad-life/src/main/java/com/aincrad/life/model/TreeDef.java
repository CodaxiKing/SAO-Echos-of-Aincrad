package com.aincrad.life.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A árvore de especialização de uma profissão (trees/&lt;profissão&gt;.yml): três caminhos, duas
 * especializações em cada, e os nós. As regras de compra ficam aqui, sem servidor.
 *
 * <p>O que o jogador tem é um conjunto de textos: o id de cada nó comprado, mais
 * "caminho:&lt;id&gt;" e "espec:&lt;id&gt;" para as duas escolhas sem volta.
 */
public final class TreeDef {

    /** Proficiência para escolher o caminho e a especialização. */
    public static final int PATH_AT = 250;
    public static final int SPEC_AT = 500;

    /** Um caminho, com as duas especializações dele. */
    public record Path(String id, String name, String exclusive, Map<String, String> specs) {
    }

    /**
     * Um nó.
     *
     * @param path        caminho a que pertence (null = tronco comum)
     * @param spec        especialização a que pertence (null = do caminho inteiro ou do tronco)
     * @param proficiency proficiência mínima para comprar
     * @param cost        pontos de ofício
     * @param requires    nós que precisam estar comprados antes
     * @param effects     chave → valor, somados pelo {@code modifier} (ex.: janela_perfeita: 0.1)
     */
    public record Node(String id, String name, String description, String kind, String path, String spec, int proficiency,
                       int cost, List<String> requires, Map<String, Double> effects) {
    }

    /** Por que uma compra ou escolha não pode ser feita. */
    public enum Deny {
        OK, DESCONHECIDO, JA_TEM, PROFICIENCIA, PONTOS, REQUISITO, CAMINHO_FECHADO, SEM_CAMINHO, JA_ESCOLHEU
    }

    private final Profession profession;
    private final Map<String, Path> paths = new LinkedHashMap<>();
    private final Map<String, Node> nodes = new LinkedHashMap<>();

    public TreeDef(Profession profession) {
        this.profession = profession;
    }

    /** Lê o YAML de uma árvore. Problemas vão para {@code errors}. */
    public static TreeDef parse(Profession profession, ConfigurationSection root, List<String> errors) {
        TreeDef t = new TreeDef(profession);
        String where = "trees/" + profession.key() + ".yml: ";
        ConfigurationSection ps = root.getConfigurationSection("caminhos");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection p = ps.getConfigurationSection(id);
                Map<String, String> specs = new LinkedHashMap<>();
                ConfigurationSection ss = p == null ? null : p.getConfigurationSection("especializacoes");
                if (ss != null) {
                    for (String sid : ss.getKeys(false)) {
                        specs.put(sid.toLowerCase(Locale.ROOT), ss.getString(sid, sid));
                    }
                }
                if (p != null) {
                    t.paths.put(id.toLowerCase(Locale.ROOT), new Path(id.toLowerCase(Locale.ROOT), p.getString("nome", id),
                            p.getString("exclusivo", ""), specs));
                }
            }
        }
        ConfigurationSection ns = root.getConfigurationSection("nos");
        if (ns != null) {
            for (String id : ns.getKeys(false)) {
                ConfigurationSection n = ns.getConfigurationSection(id);
                if (n == null) {
                    continue;
                }
                String path = lower(n.getString("caminho"));
                String spec = lower(n.getString("especializacao"));
                if (path != null && !t.paths.containsKey(path)) {
                    errors.add(where + "nó " + id + ": caminho \"" + path + "\" não existe.");
                    continue;
                }
                if (spec != null && (path == null || !t.paths.get(path).specs().containsKey(spec))) {
                    errors.add(where + "nó " + id + ": especialização \"" + spec + "\" não existe no caminho.");
                    continue;
                }
                Map<String, Double> effects = new LinkedHashMap<>();
                ConfigurationSection es = n.getConfigurationSection("efeito");
                if (es != null) {
                    for (String k : es.getKeys(false)) {
                        effects.put(k, es.getDouble(k));
                    }
                }
                int min = n.getInt("proficiencia", spec != null ? SPEC_AT : path != null ? PATH_AT : 0);
                t.nodes.put(id.toLowerCase(Locale.ROOT), new Node(id.toLowerCase(Locale.ROOT), n.getString("nome", id),
                        n.getString("descricao", ""), n.getString("tipo", "passivo"), path, spec, min,
                        Math.max(1, n.getInt("custo", 1)), n.getStringList("requer").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList(),
                        Map.copyOf(effects)));
            }
        }
        for (Node n : t.nodes.values()) {
            for (String r : n.requires()) {
                if (!t.nodes.containsKey(r)) {
                    errors.add(where + "nó " + n.id() + ": requer \"" + r + "\", que não existe.");
                }
            }
        }
        return t;
    }

    private static String lower(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    public Profession profession() {
        return profession;
    }

    public Collection<Path> paths() {
        return paths.values();
    }

    public Path path(String id) {
        return id == null ? null : paths.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public Node node(String id) {
        return id == null ? null : nodes.get(id.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ o que o jogador tem

    public static String pathOf(Set<String> owned) {
        return pick(owned, "caminho:");
    }

    public static String specOf(Set<String> owned) {
        return pick(owned, "espec:");
    }

    private static String pick(Set<String> owned, String prefix) {
        for (String s : owned) {
            if (s.startsWith(prefix)) {
                return s.substring(prefix.length());
            }
        }
        return null;
    }

    /** Pontos de ofício já gastos. */
    public int spent(Set<String> owned) {
        int n = 0;
        for (String s : owned) {
            Node node = nodes.get(s);
            if (node != null) {
                n += node.cost();
            }
        }
        return n;
    }

    public int free(double proficiency, Set<String> owned) {
        return Profession.craftPoints(proficiency) - spent(owned);
    }

    // ------------------------------------------------------------------ regras

    /** O jogador pode escolher este caminho agora? */
    public Deny canChoosePath(String path, double proficiency, Set<String> owned) {
        if (path(path) == null) {
            return Deny.DESCONHECIDO;
        }
        if (pathOf(owned) != null) {
            return Deny.JA_ESCOLHEU;
        }
        return proficiency < PATH_AT ? Deny.PROFICIENCIA : Deny.OK;
    }

    /** O jogador pode escolher esta especialização agora? (precisa ser do caminho dele) */
    public Deny canChooseSpec(String spec, double proficiency, Set<String> owned) {
        String mine = pathOf(owned);
        if (mine == null) {
            return Deny.SEM_CAMINHO;
        }
        if (specOf(owned) != null) {
            return Deny.JA_ESCOLHEU;
        }
        Path p = paths.get(mine);
        if (p == null || spec == null || !p.specs().containsKey(spec.toLowerCase(Locale.ROOT))) {
            return Deny.CAMINHO_FECHADO;
        }
        return proficiency < SPEC_AT ? Deny.PROFICIENCIA : Deny.OK;
    }

    /** O nó é de um ramo que o jogador fechou (ou ainda não abriu)? */
    public boolean closed(Node n, Set<String> owned) {
        String mine = pathOf(owned);
        String spec = specOf(owned);
        if (n.path() != null && mine != null && !n.path().equals(mine)) {
            return true;
        }
        return n.spec() != null && spec != null && !n.spec().equals(spec);
    }

    /** O jogador pode comprar o nó agora? */
    public Deny canBuy(String id, double proficiency, Set<String> owned) {
        Node n = node(id);
        if (n == null) {
            return Deny.DESCONHECIDO;
        }
        if (owned.contains(n.id())) {
            return Deny.JA_TEM;
        }
        if (closed(n, owned)) {
            return Deny.CAMINHO_FECHADO;
        }
        // Nó de caminho ou de especialização só depois da escolha correspondente.
        if ((n.path() != null && pathOf(owned) == null) || (n.spec() != null && specOf(owned) == null)) {
            return Deny.SEM_CAMINHO;
        }
        if (proficiency < n.proficiency()) {
            return Deny.PROFICIENCIA;
        }
        for (String r : n.requires()) {
            if (!owned.contains(r)) {
                return Deny.REQUISITO;
            }
        }
        return free(proficiency, owned) < n.cost() ? Deny.PONTOS : Deny.OK;
    }

    /** Soma do efeito {@code key} nos nós comprados (0 se nenhum tem). */
    public double modifier(String key, Set<String> owned) {
        double total = 0;
        for (String s : owned) {
            Node n = nodes.get(s);
            if (n != null) {
                total += n.effects().getOrDefault(key, 0.0);
            }
        }
        return total;
    }

    /** Um item exclusivo de um caminho (e, se dado, de uma especialização) pode ser aprendido por este jogador? */
    public static boolean allows(String needPath, String needSpec, Set<String> owned) {
        if (needPath != null && !needPath.equalsIgnoreCase(pathOf(owned))) {
            return false;
        }
        return needSpec == null || needSpec.equalsIgnoreCase(specOf(owned));
    }

    /** Nós de um nível: tronco (path null), de um caminho (spec null) ou de uma especialização. */
    public List<Node> level(String path, String spec) {
        List<Node> out = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (java.util.Objects.equals(n.path(), path) && java.util.Objects.equals(n.spec(), spec)) {
                out.add(n);
            }
        }
        return out;
    }
}
