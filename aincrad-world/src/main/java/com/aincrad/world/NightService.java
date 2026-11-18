package com.aincrad.world;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.world.hook.Hooks;
import com.aincrad.world.model.WorldRules;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dia e noite que importam: à noite, fora das cidades, os mobs nascem com level maior e rendem
 * mais XP e mais drops. Ao anoitecer, quem está fora de zona segura é avisado.
 */
public final class NightService implements Listener {

    private final Plugin plugin;
    private final Messages msg;
    private final Hooks hooks;
    private final NamespacedKey keyNight;
    private boolean enabled;
    private double levelTimes;
    private double xpTimes;
    private double dropTimes;
    private boolean warn;
    private final Map<String, Boolean> wasNight = new HashMap<>();

    public NightService(Plugin plugin, Messages msg, Hooks hooks) {
        this.plugin = plugin;
        this.msg = msg;
        this.hooks = hooks;
        keyNight = new NamespacedKey(plugin, "noite");
    }

    public void reload(ConfigFile c) {
        enabled = c.bool("noite.ativa", true);
        levelTimes = c.decimal("noite.level", 1.25, 1, 10);
        xpTimes = c.decimal("noite.xp", 1.5, 1, 100);
        dropTimes = c.decimal("noite.drop", 1.5, 1, 100);
        warn = c.bool("noite.aviso", true);
    }

    public boolean night(World w) {
        return w != null && w.getEnvironment() == World.Environment.NORMAL && WorldRules.night(w.getTime());
    }

    /** O mob nasceu de noite (level maior, mais XP e mais drops)? */
    public boolean nightMob(LivingEntity e) {
        return e.getPersistentDataContainer().has(keyNight, PersistentDataType.BYTE);
    }

    /** O multiplicador de XP de um abate feito agora pelo jogador (1 de dia, ou em zona segura). */
    public double xpTimes(Player p) {
        return enabled && night(p.getWorld()) && !hooks.safeZone(p.getLocation()) ? xpTimes : 1;
    }

    /** Depois que o AincradRPG deu o level ao mob: de noite, fora das cidades, ele sobe. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity e = event.getEntity();
        if (!enabled || !(e instanceof Mob) || !night(e.getWorld())) {
            return;
        }
        // No tick seguinte: o level do RPG (e o do gerador de mobs por região) já foi definido.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!e.isValid() || nightMob(e) || hooks.safeZone(e.getLocation())) {
                return;
            }
            int level = hooks.mobLevel(e);
            if (level <= 0) {
                return;
            }
            e.getPersistentDataContainer().set(keyNight, PersistentDataType.BYTE, (byte) 1);
            int up = WorldRules.nightLevel(level, levelTimes);
            if (up > level) {
                hooks.setMobLevel(e, up);
            }
        });
    }

    /** Mob da noite morto por um jogador: os drops rendem mais. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!enabled || e.getKiller() == null || !nightMob(e) || dropTimes <= 1) {
            return;
        }
        List<ItemStack> more = new ArrayList<>();
        for (ItemStack it : event.getDrops()) {
            int extra = WorldRules.extra(it.getAmount(), dropTimes, ThreadLocalRandom.current().nextDouble());
            if (extra > 0) {
                ItemStack copy = it.clone();
                copy.setAmount(Math.min(extra, Math.max(1, it.getMaxStackSize())));
                more.add(copy);
            }
        }
        event.getDrops().addAll(more);
    }

    /** A cada 5 segundos: quando anoitece num mundo, quem está fora das cidades é avisado. */
    public void tick() {
        if (!enabled || !warn) {
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            boolean now = night(w);
            Boolean before = wasNight.put(w.getName(), now);
            if (before == null || before == now) {
                continue;
            }
            for (Player p : w.getPlayers()) {
                if (now && !hooks.safeZone(p.getLocation())) {
                    msg.title(p, "noite.anoiteceu", Duration.ofSeconds(3));
                } else if (!now) {
                    msg.actionBar(p, "noite.amanheceu");
                }
            }
        }
    }
}
