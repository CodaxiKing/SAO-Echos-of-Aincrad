package com.aincrad.life.api;

import com.aincrad.life.inn.InnService;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import org.bukkit.entity.Player;

/**
 * API do AincradLife. Métodos estáticos e a {@link #instance()} registrada no ServicesManager.
 * Use na thread principal. Evento: {@link ProfessionRankUpEvent}.
 */
public final class AincradLifeAPI {

    private static final AincradLifeAPI INSTANCE = new AincradLifeAPI();
    private static ProfessionService professions;
    private static InnService inns;
    private static com.aincrad.life.cooking.FoodBuffService food;
    private static com.aincrad.life.pets.PetService pets;
    private static com.aincrad.life.marriage.MarriageService marriages;

    private AincradLifeAPI() {
    }

    public static void init(ProfessionService p, InnService i) {
        professions = p;
        inns = i;
    }

    /** Interno: o módulo de culinária (null quando desligado). */
    public static void food(com.aincrad.life.cooking.FoodBuffService f) {
        food = f;
    }

    private static com.aincrad.life.cooking.CookingService cooking;

    /** Interno: o módulo de culinária (null quando desligado). */
    public static void cooking(com.aincrad.life.cooking.CookingService c) {
        cooking = c;
    }

    /**
     * O item de um ingrediente do ingredients.yml (para drops de mobs e recompensas de quest).
     * Null se o ingrediente não existe ou a culinária está desligada.
     */
    public static org.bukkit.inventory.ItemStack createIngredient(String id, int amount) {
        if (cooking == null || id == null) {
            return null;
        }
        var ingredient = cooking.registry().ingredient(id.toLowerCase(java.util.Locale.ROOT));
        return ingredient == null ? null : cooking.ingredientItem(ingredient, amount);
    }

    /** Interno: o módulo de familiares (null quando desligado). */
    public static void pets(com.aincrad.life.pets.PetService p) {
        pets = p;
    }

    /** Interno: o módulo de casamento (null quando desligado). */
    public static void marriages(com.aincrad.life.marriage.MarriageService m) {
        marriages = m;
    }

    public static AincradLifeAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return professions != null;
    }

    /** Proficiência (0 a 1000) na profissão: "culinaria", "pesca", "ferreiro" ou "alquimia". Desconhecida: 0. */
    public static double getProficiency(Player player, String profession) {
        Profession p = Profession.parse(profession);
        return professions == null || p == null ? 0 : professions.proficiency(player, p);
    }

    /** "aprendiz", "praticante", "especialista", "mestre" ou "completo". */
    public static String getRank(Player player, String profession) {
        Profession p = Profession.parse(profession);
        return professions == null || p == null ? "aprendiz" : professions.rank(player, p).key();
    }

    /** Caminho escolhido na árvore da profissão (null = ainda não escolheu). */
    public static String getPath(Player player, String profession) {
        Profession p = Profession.parse(profession);
        return professions == null || p == null ? null : com.aincrad.life.model.TreeDef.pathOf(professions.owned(player, p));
    }

    /** Especialização escolhida (null = ainda não escolheu). */
    public static String getSpecialization(Player player, String profession) {
        Profession p = Profession.parse(profession);
        return professions == null || p == null ? null : com.aincrad.life.model.TreeDef.specOf(professions.owned(player, p));
    }

    /** O jogador conhece o item ("receita", "projeto", "formula"...)? */
    public static boolean knows(Player player, String kind, String id) {
        var l = professions == null ? null : professions.learnable(kind, id);
        return l != null && professions.knows(player, l);
    }

    /** Está com o bônus "Descansado" da estalagem? */
    public static boolean isRested(Player player) {
        return inns != null && inns.rested(player);
    }

    /** Tem um quarto alugado numa estalagem? */
    public static boolean hasRoom(Player player) {
        return inns != null && inns.of(player.getUniqueId()) != null;
    }

    /** Tipo do buff de comida ativo ("forca", "regeneracao", "velocidade", "vida_maxima", "xp", "sorte_pesca"), ou null. */
    public static String getFoodBuff(Player player) {
        var b = food == null ? null : food.current(player);
        return b == null ? null : b.type().key();
    }

    /** Fração de sorte a mais na pesca vinda da comida (0.1 = +10%). */
    public static double getFishingLuck(Player player) {
        return food == null ? 0 : food.fishingLuck(player);
    }

    /** O jogador tem um familiar vivo? */
    public static boolean hasPet(Player player) {
        var p = pets == null ? null : pets.pet(player.getUniqueId());
        return p != null && p.alive();
    }

    /** Level do familiar do jogador (0 se não tem). */
    public static int getPetLevel(Player player) {
        var p = pets == null ? null : pets.pet(player.getUniqueId());
        return p == null ? 0 : p.level();
    }

    /** O jogador (online neste servidor) é casado? */
    public static boolean isMarried(Player player) {
        return marriages != null && marriages.marriage(player.getUniqueId()) != null;
    }

    /** O par do jogador, ou null se ele não é casado. */
    public static java.util.UUID getSpouse(Player player) {
        var m = marriages == null ? null : marriages.marriage(player.getUniqueId());
        return m == null ? null : m.spouse(player.getUniqueId());
    }
}
