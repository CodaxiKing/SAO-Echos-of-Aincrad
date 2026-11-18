package com.aincrad.skills.api;

import com.aincrad.skills.SkillEngine;
import com.aincrad.skills.SkillStore;
import com.aincrad.skills.UniqueService;
import com.aincrad.skills.model.SkillDef;
import com.aincrad.skills.model.WeaponType;
import org.bukkit.entity.Player;

import java.util.UUID;

/** API do AincradSkills. Métodos estáticos (dá para chamar por reflexão); também no ServicesManager. */
public final class AincradSkillsAPI {

    private static final AincradSkillsAPI INSTANCE = new AincradSkillsAPI();
    private static SkillEngine engine;
    private static SkillStore store;
    private static UniqueService unique;

    private AincradSkillsAPI() {
    }

    public static void init(SkillEngine e, SkillStore s, UniqueService u) {
        engine = e;
        store = s;
        unique = u;
    }

    public static AincradSkillsAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return engine != null;
    }

    /** Usa a skill pelo jogador, com as mesmas regras do atalho. True se saiu. */
    public static boolean castSkill(Player p, String skill) {
        SkillDef def = engine == null ? null : engine.skill(skill);
        return def != null && engine.cast(p, def) == SkillEngine.Fail.OK;
    }

    /** Proficiência (0 a 1000) na arma: "espada", "florete", "adaga", "machado", "lanca", "duas_maos", "duas_espadas". */
    public static int getProficiency(Player p, String weapon) {
        WeaponType t = WeaponType.parse(weapon);
        SkillStore.Data d = store == null ? null : store.data(p);
        return t == null || d == null ? 0 : d.prof(t);
    }

    /** O tipo da arma na mão do jogador, ou null. */
    public static String getWeaponType(Player p) {
        WeaponType t = engine == null ? null : engine.weapon(p);
        return t == null ? null : t.key();
    }

    public static boolean hasUniqueSkill(Player p, String skill) {
        return unique != null && unique.has(p, skill);
    }

    /** O dono da skill única na rede, ou null. */
    public static UUID getUniqueHolder(String skill) {
        UniqueService.Holder h = unique == null ? null : unique.holder(skill);
        return h == null ? null : h.uuid();
    }
}
