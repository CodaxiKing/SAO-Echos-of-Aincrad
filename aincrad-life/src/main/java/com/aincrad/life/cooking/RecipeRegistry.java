package com.aincrad.life.cooking;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Os ingredientes (ingredients.yml) e as receitas (recipes.yml), e a busca de uma receita pela
 * combinação posta nos encaixes. Só regras, sem servidor.
 */
public final class RecipeRegistry {

    private final Map<String, Ingredient> ingredients = new LinkedHashMap<>();
    /** Material → ingrediente comum (qualquer item daquele material serve). */
    private final Map<String, Ingredient> byMaterial = new HashMap<>();
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final Map<String, Recipe> byCombo = new HashMap<>();

    /** Lê os dois arquivos; os erros (com o id do item) vão para {@code errors} e o item é pulado. */
    public static RecipeRegistry parse(ConfigurationSection ingredientsRoot, ConfigurationSection recipesRoot, List<String> errors) {
        RecipeRegistry reg = new RecipeRegistry();
        ConfigurationSection ing = ingredientsRoot == null ? null : ingredientsRoot.getConfigurationSection("ingredientes");
        if (ing != null) {
            for (String rawId : ing.getKeys(false)) {
                ConfigurationSection s = ing.getConfigurationSection(rawId);
                if (s != null) {
                    reg.addIngredient(rawId.toLowerCase(Locale.ROOT), s, errors);
                }
            }
        }
        ConfigurationSection rec = recipesRoot == null ? null : recipesRoot.getConfigurationSection("receitas");
        if (rec != null) {
            for (String rawId : rec.getKeys(false)) {
                ConfigurationSection s = rec.getConfigurationSection(rawId);
                if (s != null) {
                    reg.addRecipe(rawId.toLowerCase(Locale.ROOT), s, errors);
                }
            }
        }
        return reg;
    }

    private void addIngredient(String id, ConfigurationSection s, List<String> errors) {
        SlotKind slot = SlotKind.parse(s.getString("encaixe"));
        if (slot == null) {
            errors.add("ingredients.yml: " + id + ": \"encaixe\" precisa ser base, proteina, vegetal, tempero ou especial.");
            return;
        }
        String rank = s.getString("rank", "E").toUpperCase(Locale.ROOT);
        if (!Ingredient.validRank(rank)) {
            errors.add("ingredients.yml: " + id + ": \"rank\" precisa ser E, D, C, B, A ou S.");
            return;
        }
        String material = s.getString("material", "").toUpperCase(Locale.ROOT);
        if (material.isEmpty()) {
            errors.add("ingredients.yml: " + id + ": falta \"material\".");
            return;
        }
        boolean custom = s.getBoolean("item_proprio", false);
        Ingredient i = new Ingredient(id, s.getString("nome", id), rank, slot, material, custom, s.getString("origem", ""));
        if (!custom) {
            Ingredient other = byMaterial.putIfAbsent(material, i);
            if (other != null) {
                errors.add("ingredients.yml: " + id + ": o material " + material + " já é o ingrediente comum \"" + other.id()
                        + "\" (marque um dos dois com item_proprio: true).");
                return;
            }
        }
        ingredients.put(id, i);
    }

    private void addRecipe(String id, ConfigurationSection s, List<String> errors) {
        String where = "recipes.yml: " + id + ": ";
        ConfigurationSection sl = s.getConfigurationSection("encaixes");
        Map<SlotKind, String> slots = new EnumMap<>(SlotKind.class);
        if (sl != null) {
            for (String k : sl.getKeys(false)) {
                SlotKind kind = SlotKind.parse(k);
                String ingId = String.valueOf(sl.get(k)).toLowerCase(Locale.ROOT);
                Ingredient i = ingredients.get(ingId);
                if (kind == null) {
                    errors.add(where + "encaixe desconhecido \"" + k + "\".");
                    return;
                }
                if (i == null) {
                    errors.add(where + "ingrediente desconhecido \"" + ingId + "\".");
                    return;
                }
                if (i.slot() != kind) {
                    errors.add(where + "\"" + ingId + "\" é do encaixe " + i.slot().key() + ", não " + kind.key() + ".");
                    return;
                }
                slots.put(kind, ingId);
            }
        }
        for (SlotKind k : SlotKind.values()) {
            if (k.required() && !slots.containsKey(k)) {
                errors.add(where + "falta o encaixe \"" + k.key() + "\".");
                return;
            }
        }
        List<Step> steps = new ArrayList<>();
        for (String raw : s.getStringList("etapas")) {
            Step st = Step.parse(raw);
            if (st == null) {
                errors.add(where + "etapa desconhecida \"" + raw + "\" (cortar, fogo, mexer, temperar, empratar).");
                return;
            }
            steps.add(st);
        }
        if (steps.isEmpty()) {
            errors.add(where + "precisa de pelo menos uma etapa.");
            return;
        }
        ConfigurationSection b = s.getConfigurationSection("buff");
        FoodBuff.Type type = b == null ? null : FoodBuff.Type.parse(b.getString("tipo"));
        if (type == null) {
            errors.add(where + "\"buff.tipo\" precisa ser forca, regeneracao, velocidade, vida_maxima, xp ou sorte_pesca.");
            return;
        }
        Recipe.Buff buff = new Recipe.Buff(type, b.getDouble("valor", 1), Math.max(1, b.getInt("minutos", 10)));
        int study = Math.max(0, s.getInt("proficiencia_estudo", 0));
        int produce = Math.max(study, s.getInt("proficiencia_minima", study));
        String path = s.getString("caminho");
        String spec = s.getString("especializacao");
        Recipe r = new Recipe(id, s.getString("nome", id), Map.copyOf(slots), study, produce,
                Math.max(0, s.getInt("dificuldade", produce)), List.copyOf(steps), buff, s.getBoolean("secreta", false),
                Math.max(1, s.getInt("porcoes", 1)), path == null ? null : path.toLowerCase(Locale.ROOT),
                spec == null ? null : spec.toLowerCase(Locale.ROOT), s.getString("item", "RABBIT_STEW").toUpperCase(Locale.ROOT));
        String combo = Recipe.combo(slots, true);
        Recipe same = byCombo.putIfAbsent(combo, r);
        if (same != null) {
            errors.add(where + "usa a mesma combinação de ingredientes de \"" + same.id() + "\".");
            return;
        }
        recipes.put(id, r);
    }

    public Map<String, Ingredient> ingredients() {
        return ingredients;
    }

    public Ingredient ingredient(String id) {
        return id == null ? null : ingredients.get(id.toLowerCase(Locale.ROOT));
    }

    /** O ingrediente comum representado por um material (null: nenhum). */
    public Ingredient common(String material) {
        return byMaterial.get(material.toUpperCase(Locale.ROOT));
    }

    public Map<String, Recipe> recipes() {
        return recipes;
    }

    public Recipe recipe(String id) {
        return id == null ? null : recipes.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * A receita de uma combinação. Vale a combinação exata; se não houver, a mesma sem o Especial
     * (o Especial a mais entra só como capricho do cozinheiro).
     */
    public Recipe match(Map<SlotKind, String> slots) {
        Recipe exact = byCombo.get(Recipe.combo(slots, true));
        if (exact != null || slots.get(SlotKind.ESPECIAL) == null) {
            return exact;
        }
        Map<SlotKind, String> plain = new EnumMap<>(slots);
        plain.remove(SlotKind.ESPECIAL);
        return byCombo.get(Recipe.combo(plain, true));
    }
}
