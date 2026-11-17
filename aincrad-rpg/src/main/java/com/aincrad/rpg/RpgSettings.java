package com.aincrad.rpg;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.rpg.item.Rarity;
import com.aincrad.rpg.model.XpCurve;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Tudo de config.yml, levels.yml e mobs.yml já lido e validado. Imutável: um reload cria outro. */
public final class RpgSettings {

    /** Faixa de level de uma zona, mundo ou padrão. */
    public record LevelRange(int min, int max) {
    }

    /** Dados de uma raridade. */
    public record RarityInfo(double multiplier, String color, int weight, String name) {
    }

    public final int pointsPerLevel;
    public final double damagePerStr;
    public final double attackSpeedPerAgi;
    public final double speedPerAgi;
    public final double healthPerVit;
    public final double healthPerLevel;
    public final boolean xpBar;
    public final boolean blockVanillaXp;
    public final double deathLoss;
    public final Set<String> noLossWorlds;
    public final boolean blockEnchanting;
    public final Map<Rarity, RarityInfo> rarities;
    public final double weaponPenalty;
    public final int saveEvery;
    public final XpCurve curve;

    public final boolean onlyHostile;
    public final LevelRange defaultRange;
    public final Map<String, LevelRange> worlds;
    public final Map<String, LevelRange> zones;
    public final double healthPerMobLevel;
    public final double damagePerMobLevel;
    public final double xpBase;
    public final int xpFreeGap;
    public final double xpStepLoss;
    public final double xpMinFactor;
    public final String mobName;
    public final boolean mobNameVisible;

    public RpgSettings(ConfigFile config, ConfigFile levels, ConfigFile mobs) {
        pointsPerLevel = config.integer("pontos_por_level", 3, 0, 100);
        damagePerStr = config.decimal("atributos.forca.dano", 0.2, 0, 100);
        attackSpeedPerAgi = config.decimal("atributos.agilidade.velocidade_ataque", 1, 0, 100);
        speedPerAgi = config.decimal("atributos.agilidade.velocidade", 0.5, 0, 100);
        healthPerVit = config.decimal("atributos.vitalidade.vida", 1, 0, 100);
        healthPerLevel = config.decimal("vida_por_level", 0.5, 0, 100);
        xpBar = config.bool("barra_de_xp", true);
        blockVanillaXp = xpBar || !config.bool("xp_vanilla", false);
        deathLoss = config.decimal("morte.perda_xp", 0.1, 0, 1);
        noLossWorlds = new HashSet<>(config.yaml().getStringList("morte.mundos_sem_perda"));
        blockEnchanting = !"permitir".equalsIgnoreCase(config.string("encantamento", "bloquear"));
        weaponPenalty = config.decimal("penalidade_arma", 0.25, 0, 1);
        saveEvery = config.integer("salvar_a_cada", 60, 10, 3600);

        rarities = new EnumMap<>(Rarity.class);
        double[] defMult = {1.0, 1.15, 1.3, 1.5, 1.8};
        String[] defColor = {"<white>", "<green>", "<aqua>", "<light_purple>", "<gold>"};
        int[] defWeight = {60, 25, 10, 4, 1};
        for (Rarity r : Rarity.values()) {
            int i = r.ordinal();
            String base = "raridades." + r.key() + ".";
            rarities.put(r, new RarityInfo(
                    config.decimal(base + "multiplicador", defMult[i], 0, 100),
                    config.string(base + "cor", defColor[i]),
                    config.integer(base + "peso", defWeight[i], 0, 1_000_000),
                    config.string(base + "nome", r.key())));
        }

        int max = levels.integer("level_maximo", 100, 1, 10_000);
        if ("tabela".equalsIgnoreCase(levels.string("modo", "formula"))) {
            List<Long> table = new ArrayList<>();
            for (Object o : levels.yaml().getList("tabela", List.of())) {
                if (o instanceof Number n) {
                    table.add(n.longValue());
                }
            }
            curve = table.isEmpty()
                    ? XpCurve.formula(max, 100, 1.5)
                    : XpCurve.table(max, table);
        } else {
            curve = XpCurve.formula(max, levels.decimal("formula.base", 100, 1, 1e12),
                    levels.decimal("formula.expoente", 1.5, 0, 10));
        }

        onlyHostile = mobs.bool("apenas_hostis", true);
        defaultRange = range(mobs.yaml().getConfigurationSection("padrao"), new LevelRange(1, 3));
        worlds = ranges(mobs.yaml().getConfigurationSection("mundos"));
        zones = ranges(mobs.yaml().getConfigurationSection("zonas"));
        healthPerMobLevel = mobs.decimal("escala.vida_por_level", 0.12, 0, 100);
        damagePerMobLevel = mobs.decimal("escala.dano_por_level", 0.08, 0, 100);
        xpBase = mobs.decimal("xp.base_por_level", 6, 0, 1e9);
        xpFreeGap = mobs.integer("xp.diferenca_livre", 3, 0, 10_000);
        xpStepLoss = mobs.decimal("xp.perda_por_level", 0.15, 0, 1);
        xpMinFactor = mobs.decimal("xp.minimo", 0.1, 0, 1);
        mobName = mobs.string("nome", "<gray>[<yellow>Lv.<level></yellow>]</gray> <white><mob>");
        mobNameVisible = mobs.bool("nome_visivel", true);
    }

    private static Map<String, LevelRange> ranges(ConfigurationSection sec) {
        Map<String, LevelRange> out = new HashMap<>();
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                LevelRange r = range(sec.getConfigurationSection(k), null);
                if (r != null) {
                    out.put(k.toLowerCase(Locale.ROOT), r);
                }
            }
        }
        return Map.copyOf(out);
    }

    private static LevelRange range(ConfigurationSection s, LevelRange def) {
        if (s == null) {
            return def;
        }
        int a = Math.max(1, s.getInt("min", 1));
        int b = Math.max(1, s.getInt("max", a));
        return new LevelRange(Math.min(a, b), Math.max(a, b));
    }

    /** Pesos de sorteio por raridade. */
    public Map<Rarity, Integer> weights() {
        Map<Rarity, Integer> w = new EnumMap<>(Rarity.class);
        rarities.forEach((r, i) -> w.put(r, i.weight()));
        return w;
    }
}
