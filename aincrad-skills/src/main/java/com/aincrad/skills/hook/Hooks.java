package com.aincrad.skills.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Pontes para os plugins opcionais. As classes {@code *Bridge} usam as APIs deles e só são
 * tocadas quando o plugin está ligado.
 */
public final class Hooks {

    public boolean has(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    /** O lugar é zona segura (cidade) do AincradFloors? Sem o plugin: false. */
    public boolean safeZone(Location l) {
        if (!has("AincradFloors") || l == null) {
            return false;
        }
        try {
            return FloorsBridge.safeZone(l);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** O jogador está num duelo do AincradDuels? Sem o plugin: false. */
    public boolean dueling(Player p) {
        org.bukkit.plugin.Plugin duels = Bukkit.getPluginManager().getPlugin("AincradDuels");
        if (duels == null || !duels.isEnabled()) {
            return false;
        }
        try {
            Class<?> api = Class.forName("com.aincrad.duels.api.AincradDuelsAPI", true, duels.getClass().getClassLoader());
            return Boolean.TRUE.equals(api.getMethod("isDueling", java.util.UUID.class).invoke(null, p.getUniqueId()));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    /**
     * Toca o efeito da skill pelo AincradEffects, para quem está por perto. Procura, nesta ordem,
     * {@code aincrad_skill_<id>}, {@code aincrad_skill_<arma>} e {@code aincrad_skill}.
     *
     * @return true se algum efeito tocou (quem chama então não precisa do som de reserva)
     */
    public boolean skillEffect(Player p, String skill, String name, String weapon, double radius) {
        if (!has("AincradEffects")) {
            return false;
        }
        try {
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (!c.getName().equals("com.aincrad.effects.api.AincradEffectsAPI")) {
                    continue;
                }
                Object api = Bukkit.getServicesManager().load(c);
                if (api == null) {
                    return false;
                }
                for (String id : new String[]{"aincrad_skill_" + skill, "aincrad_skill_" + weapon, "aincrad_skill"}) {
                    if (Boolean.TRUE.equals(c.getMethod("exists", String.class).invoke(api, id))) {
                        Object played = c.getMethod("play", String.class, Location.class, double.class, java.util.Map.class)
                                .invoke(api, id, p.getLocation(), radius, java.util.Map.of("skill", name, "arma", weapon, "jogador", p.getName()));
                        return Boolean.TRUE.equals(played);
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
        return false;
    }

    /** O level do jogador no AincradRPG. Sem o plugin, todos os espaços ficam liberados. */
    public int level(Player p) {
        if (!has("AincradRPG")) {
            return Integer.MAX_VALUE;
        }
        try {
            return RpgBridge.level(p);
        } catch (LinkageError | RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }
}
