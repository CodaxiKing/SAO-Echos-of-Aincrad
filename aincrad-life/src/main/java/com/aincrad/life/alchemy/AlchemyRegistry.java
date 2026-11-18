package com.aincrad.life.alchemy;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Os reagentes e os pontos de coleta (reagents.yml) e as fórmulas (formulas.yml), e a busca de
 * uma fórmula pelo que está nos encaixes da mesa. Só regras, sem servidor.
 */
public final class AlchemyRegistry {

    /** As cores pelas quais o líquido passa na infusão. */
    public static final List<String> COLORS = List.of("vermelho", "laranja", "amarelo", "verde", "azul", "roxo");

    /** Os cinco encaixes da mesa de alquimia. O catalisador é opcional. */
    public enum Slot {
        FRASCO, BASE, PRINCIPAL, SECUNDARIO, CATALISADOR;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean required() {
            return this != CATALISADOR;
        }

        static Slot parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT).replace("Á", "A"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** O que uma fórmula produz. */
    public enum Kind {
        /** Poção que recupera vida aos poucos. */
        CURA,
        /** Tira efeitos ruins. */
        ANTIDOTO,
        /** Poção de efeito (só uma ativa por vez). */
        BUFF,
        /** Óleo de arma: dano extra por um número de golpes. */
        OLEO,
        /** Veneno de arma: efeito no alvo por um número de golpes. */
        VENENO,
        /** Frasco de área. */
        ARREMESSAVEL,
        /** Insumo de outra profissão (isca, ingrediente, pó de têmpera). */
        INSUMO,
        /** Cristal do AincradItems (cura ou antídoto). */
        CRISTAL;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT).replace("Á", "A").replace("Í", "I").replace("Ó", "O"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Um reagente.
     *
     * @param doses para um frasco: quantas doses rende
     * @param cap   para um frasco: a maior pureza que ele comporta
     */
    public record Reagent(String id, String name, String material, boolean custom, Slot slot, int doses, AlchemyMath.Purity cap) {
    }

    /**
     * Um tipo de ponto de coleta.
     *
     * @param rare            reagente raro que quem tem proficiência pode achar no lugar do comum (ou null)
     * @param rareProficiency proficiência em alquimia para reconhecer o raro
     */
    public record Gather(String id, String reagent, int respawnSeconds, String rare, double rareChance, int rareProficiency) {
    }

    /**
     * O produto de uma fórmula. O que cada campo quer dizer depende do tipo:
     * cura (value = vida, seconds), antídoto (effect = efeitos tirados), buff (effect, value =
     * nível, seconds), óleo (value = dano, hits), veneno (effect, value = nível, seconds, hits),
     * arremessável (effect, value = nível, seconds, radius), insumo (effect = isca, ingrediente
     * ou po_de_tempera; ref = id; value = quantidade), cristal (ref = tipo; value = quantidade).
     */
    public record Product(Kind kind, String effect, double value, double seconds, int hits, double radius, String ref) {
    }

    /** Um passo da infusão: qual reagente entra e em que cor do líquido. */
    public record Infusion(Slot reagent, String color) {
    }

    /**
     * Uma fórmula.
     *
     * @param infusion      a ordem dos reagentes e a cor esperada em cada um
     * @param catalystColor cor em que o catalisador entra, quando há um (sempre por último)
     * @param tint          cor da poção pronta, como "#FF5555"
     */
    public record Formula(String id, String name, String base, String principal, String secondary, int study, int produce,
                          int difficulty, List<Infusion> infusion, String catalystColor, Product product, String path,
                          String spec, boolean unique, String tint) {
    }

    private final Map<String, Reagent> reagents = new LinkedHashMap<>();
    private final Map<String, Reagent> byMaterial = new HashMap<>();
    private final Map<String, Gather> gathers = new LinkedHashMap<>();
    private final Map<String, Formula> formulas = new LinkedHashMap<>();
    private final Map<String, Formula> byCombo = new HashMap<>();

    public static AlchemyRegistry parse(ConfigurationSection reagentsRoot, ConfigurationSection formulasRoot, List<String> errors) {
        AlchemyRegistry reg = new AlchemyRegistry();
        ConfigurationSection rs = reagentsRoot == null ? null : reagentsRoot.getConfigurationSection("reagentes");
        if (rs != null) {
            for (String raw : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(raw);
                if (s != null) {
                    reg.addReagent(raw.toLowerCase(Locale.ROOT), s, errors);
                }
            }
        }
        ConfigurationSection gs = reagentsRoot == null ? null : reagentsRoot.getConfigurationSection("coleta");
        if (gs != null) {
            for (String raw : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(raw);
                if (s == null) {
                    continue;
                }
                String id = raw.toLowerCase(Locale.ROOT);
                String reagent = s.getString("reagente", "").toLowerCase(Locale.ROOT);
                String rare = s.getString("raro.reagente");
                if (!reg.reagents.containsKey(reagent) || (rare != null && !reg.reagents.containsKey(rare.toLowerCase(Locale.ROOT)))) {
                    errors.add("reagents.yml: coleta " + id + ": \"reagente\" (e \"raro.reagente\") precisam ser reagentes.");
                    continue;
                }
                reg.gathers.put(id, new Gather(id, reagent, Math.max(1, s.getInt("renasce_segundos", 120)),
                        rare == null ? null : rare.toLowerCase(Locale.ROOT), Math.max(0, Math.min(1, s.getDouble("raro.chance", 0.1))),
                        Math.max(0, s.getInt("raro.proficiencia", 0))));
            }
        }
        ConfigurationSection fs = formulasRoot == null ? null : formulasRoot.getConfigurationSection("formulas");
        if (fs != null) {
            for (String raw : fs.getKeys(false)) {
                ConfigurationSection s = fs.getConfigurationSection(raw);
                if (s != null) {
                    reg.addFormula(raw.toLowerCase(Locale.ROOT), s, errors);
                }
            }
        }
        return reg;
    }

    private void addReagent(String id, ConfigurationSection s, List<String> errors) {
        Slot slot = Slot.parse(s.getString("encaixe"));
        String material = s.getString("material", "").toUpperCase(Locale.ROOT);
        AlchemyMath.Purity cap = AlchemyMath.Purity.parse(s.getString("teto", "perfeita"));
        if (slot == null || material.isEmpty() || cap == null) {
            errors.add("reagents.yml: " + id + ": confira \"encaixe\" (frasco, base, principal, secundario, catalisador), "
                    + "\"material\" e \"teto\" (impura, comum, pura, perfeita).");
            return;
        }
        boolean custom = s.getBoolean("item_proprio", false);
        Reagent r = new Reagent(id, s.getString("nome", id), material, custom, slot, Math.max(1, s.getInt("doses", 1)), cap);
        if (!custom) {
            Reagent other = byMaterial.putIfAbsent(material, r);
            if (other != null) {
                errors.add("reagents.yml: " + id + ": o material " + material + " já é o reagente comum \"" + other.id()
                        + "\" (marque um dos dois com item_proprio: true).");
                return;
            }
        }
        reagents.put(id, r);
    }

    private Reagent reagent(ConfigurationSection s, String key, Slot slot, String where, List<String> errors) {
        String id = s.getString(key, "").toLowerCase(Locale.ROOT);
        Reagent r = reagents.get(id);
        if (r == null || r.slot() != slot) {
            errors.add(where + "\"" + key + ": " + id + "\" precisa ser um reagente do encaixe " + slot.key() + ".");
            return null;
        }
        return r;
    }

    private void addFormula(String id, ConfigurationSection s, List<String> errors) {
        String where = "formulas.yml: " + id + ": ";
        Reagent base = reagent(s, "base", Slot.BASE, where, errors);
        Reagent principal = reagent(s, "principal", Slot.PRINCIPAL, where, errors);
        Reagent secondary = reagent(s, "secundario", Slot.SECUNDARIO, where, errors);
        if (base == null || principal == null || secondary == null) {
            return;
        }
        List<Infusion> infusion = new ArrayList<>();
        for (Map<?, ?> step : s.getMapList("infusao")) {
            Slot slot = Slot.parse(String.valueOf(step.get("reagente")));
            String color = String.valueOf(step.get("cor")).toLowerCase(Locale.ROOT);
            if ((slot != Slot.PRINCIPAL && slot != Slot.SECUNDARIO) || !COLORS.contains(color)) {
                errors.add(where + "infusao: cada passo é {reagente: principal|secundario, cor: " + String.join("|", COLORS) + "}.");
                return;
            }
            infusion.add(new Infusion(slot, color));
        }
        if (infusion.stream().noneMatch(i -> i.reagent() == Slot.PRINCIPAL) || infusion.stream().noneMatch(i -> i.reagent() == Slot.SECUNDARIO)
                || infusion.size() != 2) {
            errors.add(where + "infusao: precisa de dois passos, um com o reagente principal e um com o secundário.");
            return;
        }
        String catalystColor = s.getString("cor_catalisador", "roxo").toLowerCase(Locale.ROOT);
        if (!COLORS.contains(catalystColor)) {
            errors.add(where + "\"cor_catalisador\" precisa ser uma de: " + String.join(", ", COLORS) + ".");
            return;
        }
        ConfigurationSection p = s.getConfigurationSection("produto");
        Kind kind = p == null ? null : Kind.parse(p.getString("tipo"));
        if (kind == null) {
            errors.add(where + "\"produto.tipo\" precisa ser cura, antidoto, buff, oleo, veneno, arremessavel, insumo ou cristal.");
            return;
        }
        String effect = switch (kind) {
            case ANTIDOTO -> String.join(",", p.getStringList("remove"));
            case INSUMO -> p.getString("insumo", "");
            default -> p.getString("efeito", "");
        };
        double seconds = p.contains("minutos") ? p.getDouble("minutos") * 60 : p.getDouble("segundos", 0);
        double value = switch (kind) {
            case CURA -> p.getDouble("vida", 6);
            case OLEO -> p.getDouble("dano", 1);
            case INSUMO, CRISTAL -> p.getDouble("quantidade", 1);
            default -> p.getDouble("nivel", 1);
        };
        Product product = new Product(kind, effect.toLowerCase(Locale.ROOT), value, seconds, Math.max(0, p.getInt("golpes", 0)),
                Math.max(0, p.getDouble("raio", 0)), p.getString("id", "").toLowerCase(Locale.ROOT));
        String problem = switch (kind) {
            case CURA -> seconds <= 0 || value <= 0 ? "cura precisa de \"vida\" e \"segundos\"" : null;
            case ANTIDOTO -> effect.isEmpty() ? "antidoto precisa de \"remove\" (lista de efeitos)" : null;
            case BUFF -> effect.isEmpty() || seconds <= 0 ? "buff precisa de \"efeito\" e \"minutos\"" : null;
            case OLEO -> product.hits() <= 0 ? "oleo precisa de \"dano\" e \"golpes\"" : null;
            case VENENO -> effect.isEmpty() || seconds <= 0 || product.hits() <= 0 ? "veneno precisa de \"efeito\", \"segundos\" e \"golpes\"" : null;
            case ARREMESSAVEL -> effect.isEmpty() || seconds <= 0 || product.radius() <= 0 ? "arremessavel precisa de \"efeito\", \"segundos\" e \"raio\"" : null;
            case INSUMO -> !List.of("isca", "ingrediente", "po_de_tempera").contains(product.effect())
                    || (!product.effect().equals("po_de_tempera") && product.ref().isEmpty())
                    ? "insumo precisa de \"insumo\" (isca, ingrediente ou po_de_tempera) e, para os dois primeiros, \"id\"" : null;
            case CRISTAL -> product.ref().isEmpty() ? "cristal precisa de \"id\" (cura ou antidoto)" : null;
        };
        if (problem != null) {
            errors.add(where + "produto: " + problem + ".");
            return;
        }
        int study = Math.max(0, s.getInt("proficiencia_estudo", 0));
        int produce = Math.max(study, s.getInt("proficiencia_minima", study));
        String path = s.getString("caminho");
        String spec = s.getString("especializacao");
        Formula f = new Formula(id, s.getString("nome", id), base.id(), principal.id(), secondary.id(), study, produce,
                Math.max(0, s.getInt("dificuldade", produce)), List.copyOf(infusion), catalystColor, product,
                path == null ? null : path.toLowerCase(Locale.ROOT), spec == null ? null : spec.toLowerCase(Locale.ROOT),
                s.getBoolean("unico", false), s.getString("cor", "#3F76E4"));
        Formula same = byCombo.putIfAbsent(combo(f.base(), f.principal(), f.secondary()), f);
        if (same != null) {
            errors.add(where + "usa a mesma combinação de reagentes de \"" + same.id() + "\".");
            return;
        }
        formulas.put(id, f);
    }

    private static String combo(String base, String principal, String secondary) {
        return base + "|" + principal + "|" + secondary;
    }

    public Map<String, Reagent> reagents() {
        return reagents;
    }

    public Reagent reagent(String id) {
        return id == null ? null : reagents.get(id.toLowerCase(Locale.ROOT));
    }

    /** O reagente comum representado por um item do Minecraft (null: nenhum). */
    public Reagent common(String material) {
        return byMaterial.get(material.toUpperCase(Locale.ROOT));
    }

    public Map<String, Gather> gathers() {
        return gathers;
    }

    public Gather gather(String id) {
        return id == null ? null : gathers.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Formula> formulas() {
        return formulas;
    }

    public Formula formula(String id) {
        return id == null ? null : formulas.get(id.toLowerCase(Locale.ROOT));
    }

    /** A fórmula de uma combinação de base, reagente principal e secundário (null: nenhuma). */
    public Formula match(String base, String principal, String secondary) {
        return base == null || principal == null || secondary == null ? null : byCombo.get(combo(base, principal, secondary));
    }

    /** Os passos da infusão de um preparo: os da fórmula e, havendo catalisador, ele por último. */
    public static List<Infusion> steps(Formula f, boolean catalyst) {
        List<Infusion> out = new ArrayList<>(f.infusion());
        if (catalyst) {
            out.add(new Infusion(Slot.CATALISADOR, f.catalystColor()));
        }
        return out;
    }
}
