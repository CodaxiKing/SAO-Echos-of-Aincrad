package com.aincrad.bosses.fight;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponte para o MythicMobs 5, por reflexão (o plugin é opcional e não entra na compilação).
 *
 * <p>O AincradBosses decide (fase, alvo, hora do golpe); o MythicMobs executa (modelo, animação,
 * partículas, som). A decisão chega ao Mythic por três variáveis do mob, que as skills dele
 * consultam com {@code ?variableEquals{var=boss_state;value=TELEGRAPH}}:</p>
 * <ul>
 *   <li>{@code boss_state}: {@code PHASE_1}, {@code PHASE_2}..., {@code TELEGRAPH} (aviso do golpe)
 *       ou {@code HEAVY_ATTACK} (o golpe saindo);</li>
 *   <li>{@code boss_phase}: o número da fase, a partir de 1;</li>
 *   <li>{@code boss_attack}: o golpe avisado ou em execução, ou vazio.</li>
 * </ul>
 */
final class MythicLink {

    static final String VAR_STATE = "boss_state";
    static final String VAR_PHASE = "boss_phase";
    static final String VAR_ATTACK = "boss_attack";

    private Object inst;

    boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    private Object inst() throws ReflectiveOperationException {
        if (!available()) {
            inst = null;
            throw new ClassNotFoundException("MythicMobs desligado");
        }
        if (inst == null) {
            inst = Class.forName("io.lumine.mythic.bukkit.MythicBukkit").getMethod("inst").invoke(null);
        }
        return inst;
    }

    /**
     * Chama um método público, procurando-o numa classe ou interface pública da hierarquia: as
     * classes concretas do Mythic nem sempre são públicas, e aí o método achado nelas não roda.
     */
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method m = find(target.getClass(), name, types);
        if (m == null) {
            throw new NoSuchMethodException(name);
        }
        return m.invoke(target, args);
    }

    private static Method find(Class<?> c, String name, Class<?>[] types) {
        if (c == null) {
            return null;
        }
        if (Modifier.isPublic(c.getModifiers())) {
            try {
                return c.getMethod(name, types);
            } catch (NoSuchMethodException e) {
                // Procura mais acima.
            }
        }
        for (Class<?> i : c.getInterfaces()) {
            Method m = find(i, name, types);
            if (m != null) {
                return m;
            }
        }
        return find(c.getSuperclass(), name, types);
    }

    /** Cria o mob do Mythic. Null se o MythicMobs não está ligado ou não conhece o mob. */
    Entity spawn(String id, Location at, int level) {
        try {
            Object helper = call(inst(), "getAPIHelper", new Class<?>[0]);
            return call(helper, "spawnMythicMob", new Class<?>[] {String.class, Location.class, int.class}, id, at, level) instanceof Entity e ? e : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Faz a entidade usar uma skill do Mythic. @return false se não deu (Mythic desligado, skill desconhecida) */
    boolean cast(Entity caster, String skill) {
        if (skill == null || skill.isBlank()) {
            return false;
        }
        try {
            Object helper = call(inst(), "getAPIHelper", new Class<?>[0]);
            return Boolean.TRUE.equals(call(helper, "castSkill", new Class<?>[] {Entity.class, String.class}, caster, skill));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Grava o estado da luta nas variáveis do mob. Não faz nada se o chefe não é um mob do Mythic. */
    void state(Entity boss, String state, int phase, String attack) {
        if (boss == null || !available()) {
            return;
        }
        try {
            Object mobs = call(inst(), "getMobManager", new Class<?>[0]);
            Object found = call(mobs, "getActiveMob", new Class<?>[] {UUID.class}, boss.getUniqueId());
            if (!(found instanceof Optional<?> o) || o.isEmpty()) {
                return;
            }
            Object vars = call(o.get(), "getVariables", new Class<?>[0]);
            Class<?>[] two = {String.class, String.class};
            call(vars, "putString", two, VAR_STATE, state);
            call(vars, "putString", two, VAR_PHASE, String.valueOf(phase));
            call(vars, "putString", two, VAR_ATTACK, attack == null ? "" : attack);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Versão do Mythic sem essas chamadas: a luta segue, só sem as variáveis.
        }
    }
}
