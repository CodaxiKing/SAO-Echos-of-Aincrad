package com.aincrad.quests.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Pontes para os plugins opcionais: level e XP (RPG), Col (Economy), linha de frente (Floors) e
 * party (Social, por reflexão). Sem o plugin, cada método tem um resultado neutro.
 */
public final class Hooks {

    private final Plugin plugin;

    public Hooks(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean has(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    private void warn(String what, Throwable e) {
        plugin.getLogger().warning(what + " incompatível: " + e);
    }

    /** Level do jogador; sem o AincradRPG, nenhum requisito de level barra. */
    public int level(Player p) {
        if (has("AincradRPG")) {
            try {
                return Bridges.Rpg.level(p);
            } catch (LinkageError | RuntimeException e) {
                warn("AincradRPG (level)", e);
            }
        }
        return Integer.MAX_VALUE;
    }

    /** @return false se o XP não pôde ser dado (sem o AincradRPG) */
    public boolean xp(Player p, long amount, String reason) {
        if (has("AincradRPG")) {
            try {
                Bridges.Rpg.xp(p, amount, reason);
                return true;
            } catch (LinkageError | RuntimeException e) {
                warn("AincradRPG (XP)", e);
            }
        }
        return false;
    }

    /** Um item do items.yml do AincradRPG, ou null. */
    public ItemStack rpgItem(String id) {
        if (has("AincradRPG")) {
            try {
                return Bridges.Rpg.item(id);
            } catch (LinkageError | RuntimeException e) {
                warn("AincradRPG (item)", e);
            }
        }
        return null;
    }

    /** O mob do spawns.yml de onde a entidade veio ({@code mythic:Lobo}), ou null. */
    public String mobId(Entity e) {
        if (has("AincradRPG")) {
            try {
                return Bridges.Rpg.mobId(e);
            } catch (LinkageError | RuntimeException ex) {
                warn("AincradRPG (mob)", ex);
            }
        }
        return null;
    }

    /** @return false se o Col não pôde ser dado (sem o AincradEconomy) */
    public boolean col(Player p, long amount, String reason) {
        if (has("AincradEconomy")) {
            try {
                return Bridges.Eco.deposit(p, amount, reason);
            } catch (LinkageError | RuntimeException e) {
                warn("AincradEconomy (Col)", e);
            }
        }
        return false;
    }

    /** O andar mais alto liberado; sem o AincradFloors, nenhum requisito de andar barra. */
    public int frontline() {
        if (has("AincradFloors")) {
            try {
                return Bridges.Floors.frontline();
            } catch (LinkageError | RuntimeException e) {
                warn("AincradFloors (linha de frente)", e);
            }
        }
        return Integer.MAX_VALUE;
    }

    private Object social() {
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals("com.aincrad.social.api.AincradSocialAPI")) {
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                return r == null ? null : r.getProvider();
            }
        }
        return null;
    }

    /** Os membros da party do jogador (com ele). Sem o AincradSocial, ou sem party: só ele. */
    public List<UUID> party(UUID player) {
        Object api = social();
        if (api != null) {
            try {
                Object r = api.getClass().getMethod("getParty", UUID.class).invoke(api, player);
                if (r instanceof Collection<?> c && !c.isEmpty()) {
                    List<UUID> out = new java.util.ArrayList<>();
                    for (Object o : c) {
                        if (o instanceof UUID u) {
                            out.add(u);
                        }
                    }
                    if (!out.contains(player)) {
                        out.add(player);
                    }
                    return out;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                warn("AincradSocial (party)", e);
            }
        }
        return List.of(player);
    }

    /** Avisa o AincradSocial de uma quest concluída (XP de guilda). */
    public void socialQuestDone(UUID player) {
        Object api = social();
        if (api != null) {
            try {
                api.getClass().getMethod("questCompleted", UUID.class).invoke(api, player);
            } catch (ReflectiveOperationException | RuntimeException e) {
                warn("AincradSocial (quest concluída)", e);
            }
        }
    }
}
