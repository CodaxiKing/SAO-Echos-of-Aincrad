package com.aincrad.rpg.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Regras dos mobs por região (spawns.yml): leitura do arquivo, sorteio e contas, sem servidor. */
public final class SpawnRules {

    private SpawnRules() {
    }

    /** De onde vem o mob. */
    public enum Kind { VANILLA, MYTHIC }

    /** Um mob: {@code ZOMBIE} (Minecraft) ou {@code mythic:JavaliFrenetico} (MythicMobs). */
    public record MobRef(Kind kind, String id) {

        public static MobRef parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String s = raw.trim();
            int i = s.indexOf(':');
            if (i > 0) {
                String prefix = s.substring(0, i).toLowerCase(Locale.ROOT);
                String rest = s.substring(i + 1).trim();
                if (rest.isEmpty()) {
                    return null;
                }
                return switch (prefix) {
                    case "mythic", "mythicmobs", "mm" -> new MobRef(Kind.MYTHIC, rest);
                    case "minecraft", "vanilla" -> new MobRef(Kind.VANILLA, rest.toUpperCase(Locale.ROOT));
                    default -> null;
                };
            }
            return new MobRef(Kind.VANILLA, s.toUpperCase(Locale.ROOT));
        }

        public String text() {
            return kind == Kind.MYTHIC ? "mythic:" + id : id;
        }
    }

    /**
     * Um item que o mob solta ao ser morto por um jogador.
     *
     * @param item   MATERIAL, {@code rpg:<id>} (items.yml) ou {@code life:<ingrediente>} (AincradLife)
     * @param chance de 0 a 1
     */
    public record Drop(String item, double chance, int min, int max) {
    }

    /**
     * O que faz de um mob do Minecraft um mob do andar: nome, equipamento e drops próprios.
     *
     * @param id          nome curto da linha, usado pelas quests no lugar do tipo (ou null)
     * @param name        nome mostrado, em MiniMessage (ou null: o nome do Minecraft)
     * @param equipment   mao, mao_secundaria, cabeca, peito, pernas, pes → material
     * @param drops       vazio = os drops normais do Minecraft
     * @param hostile     ataca jogadores mesmo sendo um mob neutro (lobo)
     */
    public record Extra(String id, String name, Map<String, String> equipment, List<Drop> drops, boolean hostile) {
        public static final Extra NONE = new Extra(null, null, Map.of(), List.of(), false);
    }

    /** Os espaços aceitos em {@code equipamento}. */
    public static final List<String> SLOTS = List.of("mao", "mao_secundaria", "cabeca", "peito", "pernas", "pes");

    /**
     * Uma linha da lista de mobs de uma região.
     *
     * @param minLevel 0 = usa a faixa de level do lugar (mobs.yml)
     * @param model    modelo do ModelEngine posto sobre o mob (ou null)
     * @param weather  só nasce com este clima do AincradWorld ("nevoa"), ou null
     * @param night    só nasce de noite (true), só de dia (false), ou null
     */
    public record Entry(MobRef mob, int weight, int minLevel, int maxLevel, String model, String weather, Boolean night, Extra extra) {

        public Entry(MobRef mob, int weight, int minLevel, int maxLevel, String model, String weather, Boolean night) {
            this(mob, weight, minLevel, maxLevel, model, weather, night, Extra.NONE);
        }

        /** Como o mob é conhecido pelas quests e pelos drops: o {@code id} da linha, ou o próprio mob. */
        public String key() {
            return extra.id() != null ? extra.id() : mob.text();
        }

        /** O clima e o período de agora servem para esta linha? */
        public boolean fits(String weatherNow, boolean nightNow) {
            return RareRules.matches(weather, night, weatherNow, nightNow);
        }

        public boolean fixedLevel() {
            return minLevel > 0;
        }
    }

    /** A configuração de uma região. */
    public record Area(String region, int max, int perRound, int radius, int minDistance, int despawn,
                       boolean natural, List<Entry> mobs) {
    }

    /** Lê {@code level}: {@code 3}, {@code "2-4"} ou {@code {min: 2, max: 4}}. Null = inválido; {0,0} = sem level próprio. */
    public static int[] levels(Object raw) {
        if (raw == null) {
            return new int[] {0, 0};
        }
        try {
            int a;
            int b;
            if (raw instanceof Number n) {
                a = b = n.intValue();
            } else if (raw instanceof Map<?, ?> m) {
                Object min = m.get("min");
                Object max = m.get("max");
                if (!(min instanceof Number mn)) {
                    return null;
                }
                a = mn.intValue();
                b = max instanceof Number mx ? mx.intValue() : a;
            } else {
                String[] p = raw.toString().trim().split("\\s*-\\s*");
                if (p.length < 1 || p.length > 2) {
                    return null;
                }
                a = Integer.parseInt(p[0]);
                b = p.length == 2 ? Integer.parseInt(p[1]) : a;
            }
            if (a < 1 || b < 1) {
                return null;
            }
            return new int[] {Math.min(a, b), Math.max(a, b)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** As linhas que valem com o clima e o período de agora. */
    public static List<Entry> fitting(List<Entry> entries, String weather, boolean night) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.fits(weather, night)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Sorteia uma linha pelo peso, com {@code roll} entre 0 e 1. */
    public static Entry pick(List<Entry> entries, double roll) {
        long total = 0;
        for (Entry e : entries) {
            total += e.weight();
        }
        if (total <= 0) {
            return null;
        }
        double at = Math.max(0, Math.min(0.999999999, roll)) * total;
        long sum = 0;
        for (Entry e : entries) {
            sum += e.weight();
            if (at < sum) {
                return e;
            }
        }
        return entries.get(entries.size() - 1);
    }

    /** Quantos mobs nascem nesta rodada. */
    public static int toSpawn(int alive, int max, int perRound) {
        return Math.max(0, Math.min(perRound, max - alive));
    }

    /** Distância ao quadrado de um ponto até a caixa da região (0 se está dentro). */
    public static double distanceSq(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double x, double y, double z) {
        double dx = Math.max(0, Math.max(minX - x, x - (maxX + 1)));
        double dy = Math.max(0, Math.max(minY - y, y - (maxY + 1)));
        double dz = Math.max(0, Math.max(minZ - z, z - (maxZ + 1)));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Lê a seção {@code regioes} do spawns.yml. Linhas com erro ficam de fora e entram em {@code errors}. */
    public static Map<String, Area> parse(ConfigurationSection regions, List<String> errors) {
        Map<String, Area> out = new LinkedHashMap<>();
        if (regions == null) {
            return out;
        }
        for (String key : regions.getKeys(false)) {
            ConfigurationSection s = regions.getConfigurationSection(key);
            if (s == null) {
                errors.add(key + ": não é uma seção");
                continue;
            }
            List<Entry> mobs = new ArrayList<>();
            int line = 0;
            for (Map<?, ?> m : s.getMapList("mobs")) {
                line++;
                String where = key + ", mob " + line;
                Object rawMob = m.get("mob");
                MobRef ref = MobRef.parse(rawMob == null ? null : rawMob.toString());
                if (ref == null) {
                    errors.add(where + ": 'mob' faltando ou inválido (use ZOMBIE ou mythic:Nome)");
                    continue;
                }
                int[] lv = levels(m.get("level"));
                if (lv == null) {
                    errors.add(where + ": 'level' inválido (use 3, \"2-4\" ou {min: 2, max: 4})");
                    continue;
                }
                int weight = m.get("peso") instanceof Number n ? n.intValue() : 1;
                if (weight <= 0) {
                    errors.add(where + ": 'peso' precisa ser maior que zero");
                    continue;
                }
                Object model = m.get("modelo");
                Object weather = m.get("clima");
                Boolean[] night = RareRules.period(m.get("periodo") == null ? null : m.get("periodo").toString());
                if (night == null) {
                    errors.add(where + ": 'periodo' precisa ser dia ou noite");
                    continue;
                }
                Extra extra = extra(m, where, errors);
                if (extra == null) {
                    continue;
                }
                mobs.add(new Entry(ref, weight, lv[0], lv[1],
                        model == null || model.toString().isBlank() ? null : model.toString().trim(),
                        weather == null || weather.toString().isBlank() ? null : weather.toString().trim().toLowerCase(Locale.ROOT),
                        night[0], extra));
            }
            if (mobs.isEmpty()) {
                errors.add(key + ": nenhum mob válido na lista 'mobs'");
                continue;
            }
            int radius = clamp(s.getInt("raio", 40), 8, 128);
            int minDistance = clamp(s.getInt("distancia_minima", 10), 0, radius - 1);
            out.put(key.toLowerCase(Locale.ROOT), new Area(key.toLowerCase(Locale.ROOT),
                    clamp(s.getInt("maximo", 10), 1, 200),
                    clamp(s.getInt("por_vez", 2), 1, 20),
                    radius, minDistance,
                    clamp(s.getInt("sumir_longe", 72), radius + 8, 256),
                    s.getBoolean("spawn_natural", false),
                    List.copyOf(mobs)));
        }
        return out;
    }

    /** Lê {@code id}, {@code nome}, {@code equipamento}, {@code drops} e {@code agressivo}. Null = linha com erro. */
    private static Extra extra(Map<?, ?> m, String where, List<String> errors) {
        String id = text(m.get("id"));
        if (id != null) {
            id = id.toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_]+")) {
                errors.add(where + ": 'id' só pode ter letras minúsculas, números e _");
                return null;
            }
        }
        Map<String, String> equipment = new LinkedHashMap<>();
        if (m.get("equipamento") instanceof Map<?, ?> eq) {
            for (Map.Entry<?, ?> e : eq.entrySet()) {
                String slot = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                String item = text(e.getValue());
                if (!SLOTS.contains(slot) || item == null) {
                    errors.add(where + ": 'equipamento." + slot + "' inválido (use " + String.join(", ", SLOTS) + ")");
                    return null;
                }
                equipment.put(slot, item.toUpperCase(Locale.ROOT));
            }
        }
        List<Drop> drops = new ArrayList<>();
        if (m.get("drops") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> d) || text(d.get("item")) == null) {
                    errors.add(where + ": cada drop precisa de 'item'");
                    return null;
                }
                double chance = d.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
                int[] amount = levels(d.get("quantidade"));
                if (chance <= 0 || chance > 1 || amount == null) {
                    errors.add(where + ": drop '" + d.get("item") + "' com 'chance' (0 a 1) ou 'quantidade' inválida");
                    return null;
                }
                drops.add(new Drop(text(d.get("item")), chance, Math.max(1, amount[0]), Math.max(1, amount[1])));
            }
        }
        return new Extra(id, text(m.get("nome")), Map.copyOf(equipment), List.copyOf(drops), Boolean.TRUE.equals(m.get("agressivo")));
    }

    private static String text(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString().trim();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
