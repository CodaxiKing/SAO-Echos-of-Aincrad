package com.aincrad.life.smith;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tudo o que está em forge.yml e ingots.yml: os materiais de forja, os tipos de item (com os
 * encaixes de cada um), os projetos e as armas nomeadas. Só regras, sem servidor.
 */
public final class ForgeRegistry {

    /** Raridade de um item forjado (as mesmas do AincradRPG). */
    public enum Rarity {
        COMUM, INCOMUM, RARO, EPICO, LENDARIO;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Rarity parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT).replace("É", "E").replace("Á", "A"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Um encaixe da mesa de forja e as classes de material que ele aceita. */
    public enum Role {
        LAMINA(true, "lingote"), CABECA(true, "lingote"), PLACA(true, "lingote"),
        NUCLEO(false, "lingote", "cristal"), GUARDA(false, "lingote"), HASTE(false, "madeira"),
        FORRO(false, "couro", "tecido"), REBITE(false, "lingote"), CABO(false, "couro", "madeira", "tecido"),
        CATALISADOR(false, "catalisador");

        private final boolean primary;
        private final Set<String> classes;

        Role(boolean primary, String... classes) {
            this.primary = primary;
            this.classes = Set.of(classes);
        }

        /** Lâmina, cabeça ou placa: o material base, que dá o teto de raridade. */
        public boolean primary() {
            return primary;
        }

        /** O catalisador é o único encaixe opcional. */
        public boolean optional() {
            return this == CATALISADOR;
        }

        public boolean accepts(Mat m) {
            return m != null && classes.contains(m.clazz());
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Role parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT).replace("Â", "A").replace("Ç", "C").replace("Ú", "U"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Um material de forja (ingots.yml).
     *
     * @param clazz    lingote, cristal, couro, madeira, tecido ou catalisador
     * @param rank     de 1 a 5: mais batidas e anel mais rápido
     * @param cap      teto de raridade quando é o material base
     * @param weapon   bônus como lâmina ou cabeça de arma
     * @param armor    bônus como placa de armadura ou escudo
     * @param support  bônus nos encaixes de apoio (guarda, haste, forro, rebite, cabo)
     * @param core     atributo dominante quando vai no núcleo: afiacao, rapidez, precisao ou peso
     * @param catalyst bônus quando é o catalisador
     */
    public record Mat(String id, String name, String material, boolean custom, String clazz, int rank, Rarity cap,
                      Map<String, Double> weapon, Map<String, Double> armor, Map<String, Double> support, String core,
                      Map<String, Double> catalyst) {
    }

    /** Um tipo de item: os encaixes e as batidas base. */
    public record Type(String id, String name, List<Role> slots, int hits, boolean armor) {
    }

    /**
     * Um projeto de forja.
     *
     * @param material item do Minecraft que sai
     * @param rpgItem  modelo do AincradRPG (items.yml) usado como base, ou null
     */
    public record Blueprint(String id, String name, Type type, String material, String rpgItem, int study, int produce,
                            int difficulty, String path, String spec, boolean unique) {
    }

    /** Uma arma nomeada: um projeto feito com uma combinação exata de materiais. */
    public record Named(String id, String name, String blueprint, Map<Role, String> materials, Map<String, Double> bonus) {
    }

    /** Os bônus que um item forjado pode ter. */
    public static final Set<String> STATS = Set.of("dano", "velocidade_ataque", "armadura", "resistencia", "vida", "empurrao", "critico", "durabilidade");
    private static final Set<String> CLASSES = Set.of("lingote", "cristal", "couro", "madeira", "tecido", "catalisador");
    private static final Set<String> CORES = Set.of("afiacao", "rapidez", "precisao", "peso");

    private final Map<String, Mat> mats = new LinkedHashMap<>();
    private final Map<String, Mat> byMaterial = new HashMap<>();
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Map<String, Blueprint> blueprints = new LinkedHashMap<>();
    private final Map<String, Named> named = new LinkedHashMap<>();
    private final Map<String, Map<String, Double>> dominant = new LinkedHashMap<>();

    public static ForgeRegistry parse(ConfigurationSection ingots, ConfigurationSection forge, List<String> errors) {
        ForgeRegistry reg = new ForgeRegistry();
        ConfigurationSection ms = ingots == null ? null : ingots.getConfigurationSection("materiais");
        if (ms != null) {
            for (String raw : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(raw);
                if (s != null) {
                    reg.addMat(raw.toLowerCase(Locale.ROOT), s, errors);
                }
            }
        }
        if (forge == null) {
            return reg;
        }
        ConfigurationSection ds = forge.getConfigurationSection("dominante");
        if (ds != null) {
            for (String k : ds.getKeys(false)) {
                if (CORES.contains(k.toLowerCase(Locale.ROOT))) {
                    reg.dominant.put(k.toLowerCase(Locale.ROOT), bonus(ds.getConfigurationSection(k), "forge.yml: dominante " + k, errors));
                }
            }
        }
        ConfigurationSection ts = forge.getConfigurationSection("tipos");
        if (ts != null) {
            for (String raw : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(raw);
                if (s == null) {
                    continue;
                }
                String id = raw.toLowerCase(Locale.ROOT);
                List<Role> slots = new ArrayList<>();
                boolean ok = true;
                for (String r : s.getStringList("encaixes")) {
                    Role role = Role.parse(r);
                    if (role == null) {
                        errors.add("forge.yml: tipo " + id + ": encaixe desconhecido \"" + r + "\".");
                        ok = false;
                    } else {
                        slots.add(role);
                    }
                }
                if (ok && slots.stream().noneMatch(Role::primary)) {
                    errors.add("forge.yml: tipo " + id + ": precisa de um encaixe base (lamina, cabeca ou placa).");
                    ok = false;
                }
                if (ok) {
                    reg.types.put(id, new Type(id, s.getString("nome", id), List.copyOf(slots), Math.max(1, s.getInt("batidas", 20)),
                            slots.contains(Role.PLACA)));
                }
            }
        }
        ConfigurationSection ps = forge.getConfigurationSection("projetos");
        if (ps != null) {
            for (String raw : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(raw);
                if (s == null) {
                    continue;
                }
                String id = raw.toLowerCase(Locale.ROOT);
                Type type = reg.types.get(s.getString("tipo", "").toLowerCase(Locale.ROOT));
                String material = s.getString("material", "").toUpperCase(Locale.ROOT);
                if (type == null || material.isEmpty()) {
                    errors.add("forge.yml: projeto " + id + ": confira \"tipo\" (um dos tipos) e \"material\".");
                    continue;
                }
                int study = Math.max(0, s.getInt("proficiencia_estudo", 0));
                int produce = Math.max(study, s.getInt("proficiencia_minima", study));
                String path = s.getString("caminho");
                String spec = s.getString("especializacao");
                String rpg = s.getString("item_rpg");
                reg.blueprints.put(id, new Blueprint(id, s.getString("nome", id), type, material, rpg == null ? null : rpg.toLowerCase(Locale.ROOT),
                        study, produce, Math.max(0, s.getInt("dificuldade", produce)), path == null ? null : path.toLowerCase(Locale.ROOT),
                        spec == null ? null : spec.toLowerCase(Locale.ROOT), s.getBoolean("unico", false)));
            }
        }
        ConfigurationSection ns = forge.getConfigurationSection("nomeadas");
        if (ns != null) {
            for (String raw : ns.getKeys(false)) {
                ConfigurationSection s = ns.getConfigurationSection(raw);
                if (s != null) {
                    reg.addNamed(raw.toLowerCase(Locale.ROOT), s, errors);
                }
            }
        }
        return reg;
    }

    private void addMat(String id, ConfigurationSection s, List<String> errors) {
        String where = "ingots.yml: " + id;
        String clazz = s.getString("classe", "").toLowerCase(Locale.ROOT);
        String material = s.getString("material", "").toUpperCase(Locale.ROOT);
        Rarity cap = Rarity.parse(s.getString("teto", "comum"));
        String core = s.getString("nucleo");
        if (!CLASSES.contains(clazz) || material.isEmpty() || cap == null
                || (core != null && !CORES.contains(core.toLowerCase(Locale.ROOT)))) {
            errors.add(where + ": confira \"classe\" (lingote, cristal, couro, madeira, tecido, catalisador), \"material\", "
                    + "\"teto\" (comum a lendario) e \"nucleo\" (afiacao, rapidez, precisao, peso).");
            return;
        }
        boolean custom = s.getBoolean("item_proprio", false);
        Mat m = new Mat(id, s.getString("nome", id), material, custom, clazz, Math.max(1, Math.min(5, s.getInt("rank", 1))), cap,
                bonus(s.getConfigurationSection("arma"), where, errors), bonus(s.getConfigurationSection("armadura"), where, errors),
                bonus(s.getConfigurationSection("apoio"), where, errors), core == null ? null : core.toLowerCase(Locale.ROOT),
                bonus(s.getConfigurationSection("catalisador"), where, errors));
        if (!custom) {
            Mat other = byMaterial.putIfAbsent(material, m);
            if (other != null) {
                errors.add(where + ": o material " + material + " já é \"" + other.id() + "\" (marque um dos dois com item_proprio: true).");
                return;
            }
        }
        mats.put(id, m);
    }

    private static Map<String, Double> bonus(ConfigurationSection s, String where, List<String> errors) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                String stat = k.toLowerCase(Locale.ROOT);
                if (!STATS.contains(stat)) {
                    errors.add(where + ": bônus desconhecido \"" + k + "\" (" + String.join(", ", new java.util.TreeSet<>(STATS)) + ").");
                    continue;
                }
                out.put(stat, s.getDouble(k));
            }
        }
        return Map.copyOf(out);
    }

    private void addNamed(String id, ConfigurationSection s, List<String> errors) {
        String where = "forge.yml: arma nomeada " + id + ": ";
        Blueprint bp = blueprints.get(s.getString("projeto", "").toLowerCase(Locale.ROOT));
        ConfigurationSection ms = s.getConfigurationSection("materiais");
        if (bp == null || ms == null) {
            errors.add(where + "confira \"projeto\" e \"materiais\".");
            return;
        }
        Map<Role, String> need = new LinkedHashMap<>();
        for (String k : ms.getKeys(false)) {
            Role role = Role.parse(k);
            Mat m = mats.get(String.valueOf(ms.get(k)).toLowerCase(Locale.ROOT));
            if (role == null || m == null || !bp.type().slots().contains(role) || !role.accepts(m)) {
                errors.add(where + "\"" + k + ": " + ms.get(k) + "\" não é um encaixe do projeto com um material que cabe nele.");
                return;
            }
            need.put(role, m.id());
        }
        named.put(id, new Named(id, s.getString("nome", id), bp.id(), Map.copyOf(need), bonus(s.getConfigurationSection("bonus"), where, errors)));
    }

    public Map<String, Mat> mats() {
        return mats;
    }

    public Mat mat(String id) {
        return id == null ? null : mats.get(id.toLowerCase(Locale.ROOT));
    }

    /** O material comum representado por um item do Minecraft (null: nenhum). */
    public Mat common(String material) {
        return byMaterial.get(material.toUpperCase(Locale.ROOT));
    }

    public Map<String, Type> types() {
        return types;
    }

    public Map<String, Blueprint> blueprints() {
        return blueprints;
    }

    public Blueprint blueprint(String id) {
        return id == null ? null : blueprints.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Named> named() {
        return named;
    }

    /** Bônus de cada atributo dominante, por rank do material do núcleo. */
    public Map<String, Map<String, Double>> dominant() {
        return dominant;
    }

    /**
     * A arma nomeada que esta combinação forma. {@code placed} segue a ordem dos encaixes do tipo
     * (null = encaixe vazio). Todos os encaixes citados pela arma precisam ter o material dela.
     */
    public Named named(Blueprint bp, List<Mat> placed) {
        for (Named n : named.values()) {
            if (!n.blueprint().equals(bp.id())) {
                continue;
            }
            boolean all = true;
            for (int i = 0; i < bp.type().slots().size() && all; i++) {
                String need = n.materials().get(bp.type().slots().get(i));
                if (need != null && (placed.get(i) == null || !need.equals(placed.get(i).id()))) {
                    all = false;
                }
            }
            if (all) {
                return n;
            }
        }
        return null;
    }
}
