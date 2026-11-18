package com.aincrad.life.fishing;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tudo o que está em fishing.yml: varas, iscas, zonas, peixes e pontos de pesca, e as regras de
 * zona e de sorteio. Só regras, sem servidor.
 */
public final class FishingRegistry {

    /** Raridade de um peixe. */
    public enum Rarity {
        COMUM, INCOMUM, RARO, LENDARIO;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Rarity parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT).replace("Á", "A"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** As três zonas de um ponto de pesca, da margem para o fundo. */
    public enum Zone {
        RASO, MEIO, FUNDO;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Zone parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Uma vara.
     *
     * @param rank      a zona mais funda que alcança depende dele (zonas → vara_minima)
     * @param snapTicks quanto tempo a linha aguenta no vermelho antes de arrebentar
     * @param luck      sorte a mais para peixes incomuns e raros
     */
    public record Rod(String id, String name, int rank, int snapTicks, double luck) {
    }

    /** Uma isca (vai na outra mão). {@code legendary}: é a isca que atrai um peixe lendário. */
    public record Bait(String id, String name, String material, double luck, boolean legendary) {
    }

    /** Um peixe. Tamanhos em centímetros. */
    public record Fish(String id, String name, String material, Rarity rarity, int difficulty, double minCm, double maxCm) {
    }

    /**
     * O lendário de um ponto: fisgá-lo não entrega o peixe, faz o Senhor do Lago sair da água.
     *
     * @param bait    isca exigida
     * @param chance  chance por lançamento na zona funda, com a isca e o rank de Mestre
     * @param minutes quanto tempo o chefe fica antes de voltar para o fundo
     */
    public record Legendary(String fish, String bait, double chance, String mob, String bossName, int level,
                            double health, double damage, int minutes) {
    }

    /** Um ponto de pesca: uma região do core, o andar e a tabela de cada zona (peixe → peso). */
    public record Point(String id, String region, int floor, Map<Zone, Map<String, Integer>> tables, Legendary legendary) {
    }

    private final Map<String, Rod> rods = new LinkedHashMap<>();
    private final Map<String, Bait> baits = new LinkedHashMap<>();
    private final Map<String, Fish> fishes = new LinkedHashMap<>();
    private final Map<String, Point> points = new LinkedHashMap<>();
    private final Map<Zone, Double> zoneUpTo = new EnumMap<>(Zone.class);
    private final Map<Zone, Integer> zoneMinRod = new EnumMap<>(Zone.class);
    private String defaultRod = "";

    public static FishingRegistry parse(ConfigurationSection root, List<String> errors) {
        FishingRegistry reg = new FishingRegistry();
        reg.zoneUpTo.put(Zone.RASO, 0.4);
        reg.zoneUpTo.put(Zone.MEIO, 0.75);
        reg.zoneUpTo.put(Zone.FUNDO, 1.0);
        for (Zone z : Zone.values()) {
            reg.zoneMinRod.put(z, z.ordinal() + 1);
        }
        if (root == null) {
            return reg;
        }
        ConfigurationSection zs = root.getConfigurationSection("zonas");
        if (zs != null) {
            for (String k : zs.getKeys(false)) {
                Zone z = Zone.parse(k);
                ConfigurationSection s = zs.getConfigurationSection(k);
                if (z == null || s == null) {
                    errors.add("fishing.yml: zonas: \"" + k + "\" não existe (raso, meio, fundo).");
                    continue;
                }
                reg.zoneUpTo.put(z, Math.max(0, Math.min(1, s.getDouble("ate", reg.zoneUpTo.get(z)))));
                reg.zoneMinRod.put(z, Math.max(0, s.getInt("vara_minima", reg.zoneMinRod.get(z))));
            }
        }
        ConfigurationSection rs = root.getConfigurationSection("varas");
        if (rs != null) {
            for (String raw : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(raw);
                if (s != null) {
                    String id = raw.toLowerCase(Locale.ROOT);
                    reg.rods.put(id, new Rod(id, s.getString("nome", id), Math.max(0, s.getInt("rank", 1)),
                            Math.max(1, s.getInt("resistencia_ticks", 8)), s.getDouble("sorte", 0)));
                }
            }
        }
        reg.defaultRod = root.getString("vara_padrao", "").toLowerCase(Locale.ROOT);
        if (!reg.rods.containsKey(reg.defaultRod)) {
            errors.add("fishing.yml: \"vara_padrao\" precisa ser uma das varas.");
            reg.defaultRod = reg.rods.isEmpty() ? "" : reg.rods.keySet().iterator().next();
        }
        ConfigurationSection bs = root.getConfigurationSection("iscas");
        if (bs != null) {
            for (String raw : bs.getKeys(false)) {
                ConfigurationSection s = bs.getConfigurationSection(raw);
                if (s != null) {
                    String id = raw.toLowerCase(Locale.ROOT);
                    reg.baits.put(id, new Bait(id, s.getString("nome", id), s.getString("material", "WHEAT_SEEDS").toUpperCase(Locale.ROOT),
                            s.getDouble("sorte", 0), s.getBoolean("lendario", false)));
                }
            }
        }
        ConfigurationSection fs = root.getConfigurationSection("peixes");
        if (fs != null) {
            for (String raw : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(raw);
                if (s == null) {
                    continue;
                }
                String id = raw.toLowerCase(Locale.ROOT);
                Rarity r = Rarity.parse(s.getString("raridade", "comum"));
                List<Double> size = s.getDoubleList("tamanho_cm");
                if (r == null || size.size() != 2 || size.get(0) <= 0 || size.get(1) < size.get(0)) {
                    errors.add("fishing.yml: peixe " + id + ": confira \"raridade\" (comum, incomum, raro, lendario) e \"tamanho_cm\" ([mínimo, máximo]).");
                    continue;
                }
                reg.fishes.put(id, new Fish(id, s.getString("nome", id), s.getString("material", "COD").toUpperCase(Locale.ROOT), r,
                        Math.max(0, s.getInt("dificuldade", 0)), size.get(0), size.get(1)));
            }
        }
        ConfigurationSection ps = root.getConfigurationSection("pontos");
        if (ps != null) {
            for (String raw : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(raw);
                if (s != null) {
                    reg.addPoint(raw.toLowerCase(Locale.ROOT), s, errors);
                }
            }
        }
        return reg;
    }

    private void addPoint(String id, ConfigurationSection s, List<String> errors) {
        String where = "fishing.yml: ponto " + id + ": ";
        String region = s.getString("regiao", "");
        if (region.isEmpty()) {
            errors.add(where + "falta \"regiao\" (uma região do core).");
            return;
        }
        Map<Zone, Map<String, Integer>> tables = new EnumMap<>(Zone.class);
        ConfigurationSection zs = s.getConfigurationSection("zonas");
        if (zs != null) {
            for (String k : zs.getKeys(false)) {
                Zone z = Zone.parse(k);
                ConfigurationSection t = zs.getConfigurationSection(k);
                if (z == null || t == null) {
                    errors.add(where + "zona desconhecida \"" + k + "\".");
                    return;
                }
                Map<String, Integer> table = new LinkedHashMap<>();
                for (String f : t.getKeys(false)) {
                    Fish fish = fishes.get(f.toLowerCase(Locale.ROOT));
                    if (fish == null || fish.rarity() == Rarity.LENDARIO) {
                        errors.add(where + "zona " + z.key() + ": \"" + f + "\" não é um peixe (o lendário vai em \"lendario\").");
                        return;
                    }
                    if (t.getInt(f) > 0) {
                        table.put(fish.id(), t.getInt(f));
                    }
                }
                tables.put(z, table);
            }
        }
        for (Zone z : Zone.values()) {
            if (tables.getOrDefault(z, Map.of()).isEmpty()) {
                errors.add(where + "a zona " + z.key() + " não tem peixes.");
                return;
            }
        }
        Legendary legendary = null;
        ConfigurationSection l = s.getConfigurationSection("lendario");
        if (l != null) {
            Fish fish = fishes.get(l.getString("peixe", "").toLowerCase(Locale.ROOT));
            Bait bait = baits.get(l.getString("isca", "").toLowerCase(Locale.ROOT));
            if (fish == null || fish.rarity() != Rarity.LENDARIO || bait == null) {
                errors.add(where + "lendario: \"peixe\" precisa ser um peixe de raridade lendario e \"isca\" uma das iscas.");
                return;
            }
            ConfigurationSection b = l.getConfigurationSection("chefe");
            legendary = new Legendary(fish.id(), bait.id(), Math.max(0, Math.min(1, l.getDouble("chance", 0.02))),
                    (b == null ? "GUARDIAN" : b.getString("mob", "GUARDIAN")).toUpperCase(Locale.ROOT),
                    b == null ? fish.name() : b.getString("nome", fish.name()), b == null ? 10 : Math.max(1, b.getInt("level", 10)),
                    b == null ? 100 : Math.max(1, b.getDouble("vida", 100)), b == null ? 6 : Math.max(0, b.getDouble("dano", 6)),
                    b == null ? 5 : Math.max(1, b.getInt("minutos", 5)));
        }
        points.put(id, new Point(id, region, Math.max(1, s.getInt("andar", 1)), tables, legendary));
    }

    public Map<String, Rod> rods() {
        return rods;
    }

    public Rod rod(String id) {
        return id == null ? null : rods.get(id.toLowerCase(Locale.ROOT));
    }

    /** A vara que uma vara de pescar comum do Minecraft representa. */
    public Rod defaultRod() {
        return rods.get(defaultRod);
    }

    public Map<String, Bait> baits() {
        return baits;
    }

    public Bait bait(String id) {
        return id == null ? null : baits.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Fish> fishes() {
        return fishes;
    }

    public Fish fish(String id) {
        return id == null ? null : fishes.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Point> points() {
        return points;
    }

    /** O ponto de pesca de uma região do core (null: a região não é ponto de pesca). */
    public Point pointOfRegion(String region) {
        for (Point p : points.values()) {
            if (p.region().equalsIgnoreCase(region)) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ regras

    /** A zona em que o lançamento cai, pela força (0 a 1). */
    public Zone zone(double power) {
        if (power <= zoneUpTo.get(Zone.RASO)) {
            return Zone.RASO;
        }
        return power <= zoneUpTo.get(Zone.MEIO) ? Zone.MEIO : Zone.FUNDO;
    }

    public int minRod(Zone z) {
        return zoneMinRod.get(z);
    }

    /** A vara alcança a zona? */
    public boolean reaches(Rod rod, Zone z) {
        return rod != null && rod.rank() >= zoneMinRod.get(z);
    }

    /** A zona mais funda que a vara alcança (null: nenhuma). */
    public Zone deepest(Rod rod) {
        Zone out = null;
        for (Zone z : Zone.values()) {
            if (reaches(rod, z)) {
                out = z;
            }
        }
        return out;
    }

    /**
     * Sorteia um peixe da tabela da zona. A sorte aumenta o peso dos incomuns (1× a sorte) e dos
     * raros (2× a sorte); os comuns ficam como estão.
     *
     * @param luck soma da sorte da vara, da isca, da proficiência e dos bônus (0.1 = 10%)
     * @param roll sorteio de 0 (inclusive) a 1 (exclusive)
     */
    public Fish roll(Point point, Zone zone, double luck, double roll) {
        Map<String, Integer> table = point.tables().get(zone);
        double total = 0;
        for (Map.Entry<String, Integer> e : table.entrySet()) {
            total += weight(fishes.get(e.getKey()), e.getValue(), luck);
        }
        double at = Math.max(0, Math.min(0.999999, roll)) * total;
        Fish last = null;
        for (Map.Entry<String, Integer> e : table.entrySet()) {
            last = fishes.get(e.getKey());
            at -= weight(last, e.getValue(), luck);
            if (at < 0) {
                return last;
            }
        }
        return last;
    }

    private static double weight(Fish f, int base, double luck) {
        double l = Math.max(0, luck);
        return switch (f.rarity()) {
            case INCOMUM -> base * (1 + l);
            case RARO -> base * (1 + 2 * l);
            default -> base;
        };
    }

    /**
     * O lendário pode morder neste lançamento? Só na zona funda, com a isca certa e o rank de
     * Mestre (750 de proficiência).
     */
    public boolean legendaryPossible(Point point, Zone zone, Bait bait, double proficiency) {
        Legendary l = point.legendary();
        return l != null && zone == Zone.FUNDO && bait != null && bait.id().equals(l.bait()) && proficiency >= 750;
    }

    /** Tamanho de um peixe, em milímetros: a maioria fica perto do mínimo; os grandes são raros. */
    public static int sizeMm(Fish f, double roll) {
        double r = Math.max(0, Math.min(1, roll));
        return (int) Math.round((f.minCm() + (f.maxCm() - f.minCm()) * r * r) * 10);
    }
}
