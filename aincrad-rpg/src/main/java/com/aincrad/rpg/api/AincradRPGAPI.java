package com.aincrad.rpg.api;

import com.aincrad.rpg.RpgService;
import com.aincrad.rpg.command.StatusCommand;
import com.aincrad.rpg.item.ItemService;
import com.aincrad.rpg.item.Rarity;
import com.aincrad.rpg.mob.MobService;
import com.aincrad.rpg.model.Stat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * API do AincradRPG para os outros plugins (andares, bosses, quests, social...). Métodos
 * estáticos para dar para chamar por reflexão sem depender do jar. Use na thread principal.
 * Sem o plugin ligado, os métodos devolvem valores neutros (level 1, 0, null). A instância
 * {@link #instance()} fica registrada no ServicesManager do Bukkit (é assim que o AincradSocial acha).
 */
public final class AincradRPGAPI {

    private static final AincradRPGAPI INSTANCE = new AincradRPGAPI();

    private static RpgService rpg;
    private static MobService mobs;
    private static ItemService items;
    private static StatusCommand status;

    private AincradRPGAPI() {
    }

    /** Chamado pelo plugin ao ligar (e com nulls ao desligar). */
    public static void init(RpgService r, MobService m, ItemService i, StatusCommand s) {
        rpg = r;
        mobs = m;
        items = i;
        status = s;
    }

    /** Objeto registrado como serviço (os métodos são estáticos; serve só para ser encontrado). */
    public static AincradRPGAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return rpg != null;
    }

    public static int getLevel(Player p) {
        return rpg == null ? 1 : rpg.progress(p).level();
    }

    /** XP juntado dentro do level atual. */
    public static long getXp(Player p) {
        return rpg == null ? 0 : rpg.progress(p).xp();
    }

    /** XP que falta juntar no level atual para subir (total do level, não o que resta). */
    public static long getXpToNext(Player p) {
        return rpg == null ? 0 : rpg.settings().curve.toNext(rpg.progress(p).level());
    }

    public static int getMaxLevel() {
        return rpg == null ? 1 : rpg.settings().curve.maxLevel();
    }

    /** Dá XP (passa pelo {@link XpGainEvent}). @return XP que entrou de fato */
    public static long addXp(Player p, long amount, String reason) {
        return rpg == null ? 0 : rpg.giveXp(p, amount, reason, true);
    }

    /** Pontos no atributo ("forca", "agilidade", "vitalidade"); desconhecido: 0. */
    public static int getAttribute(Player p, String stat) {
        Stat s = Stat.parse(stat);
        return rpg == null || s == null ? 0 : rpg.progress(p).stat(s);
    }

    public static int getFreePoints(Player p) {
        return rpg == null ? 0 : rpg.progress(p).points();
    }

    /** Chave onde o gerador de mobs por região guarda de qual linha do spawns.yml o mob veio. */
    public static final org.bukkit.NamespacedKey MOB_ID = new org.bukkit.NamespacedKey("aincradrpg", "mob_id");

    /**
     * O mob do spawns.yml de onde a entidade veio: {@code "mythic:LoboTerrivel"} ou {@code "WOLF"}.
     * Null se não foi criada pelo gerador de mobs por região.
     */
    public static String getMobId(Entity e) {
        return e.getPersistentDataContainer().get(MOB_ID, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Level do mob (0 = sem level). */
    public static int getMobLevel(Entity e) {
        return mobs == null ? 0 : mobs.level(e);
    }

    /** Dá level a um mob (bosses, spawners de quest). */
    public static void setMobLevel(LivingEntity e, int level) {
        if (mobs != null) {
            mobs.setLevel(e, level);
        }
    }

    /** Cria um item de items.yml; {@code rarity} null ou desconhecida = sorteada. Modelo desconhecido: null. */
    public static ItemStack createItem(String id, String rarity) {
        return items == null ? null : items.create(id, Rarity.parse(rarity));
    }

    /** Raridade do item ("comum"... "lendario") ou null se não é item do RPG. */
    public static String getRarity(ItemStack it) {
        Rarity r = items == null ? null : items.rarity(it);
        return r == null ? null : r.key();
    }

    /** Level mínimo do item (0 = sem exigência). */
    public static int getRequiredLevel(ItemStack it) {
        return items == null ? 0 : items.requiredLevel(it);
    }

    /** Abre o menu de status (botão do menu principal do AincradUI). */
    public static void openStatus(Player p) {
        if (status != null) {
            status.open(p);
        }
    }
}
