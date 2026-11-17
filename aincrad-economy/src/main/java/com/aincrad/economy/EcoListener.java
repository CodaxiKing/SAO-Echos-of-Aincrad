package com.aincrad.economy;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Carrega/solta as contas e dá Col por abate de mob (pelo level do AincradRPG). */
public final class EcoListener implements Listener {

    /** Level que o AincradRPG grava no mob. */
    private static final NamespacedKey RPG_LEVEL = NamespacedKey.fromString("aincradrpg:mob_level");

    private final Plugin plugin;
    private final EconomyService eco;

    public EcoListener(Plugin plugin, EconomyService eco) {
        this.plugin = plugin;
        this.eco = eco;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            eco.accounts().loadBlocking(event.getUniqueId(), event.getName());
        } catch (SQLException e) {
            plugin.getLogger().warning("Não foi possível carregar o Col de " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // O banco já tem tudo (cada mudança grava na hora); só tira da memória.
        UUID id = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getServer().getPlayer(id) == null) {
                eco.accounts().forget(id);
            }
        }, 1L);
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        Player killer = e.getKiller();
        if (killer == null || !(e instanceof Mob) || !eco.config().bool("mobs.ativo", true)) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() >= eco.config().decimal("mobs.chance", 1, 0, 1)) {
            return;
        }
        Integer lv = RPG_LEVEL == null ? null : e.getPersistentDataContainer().get(RPG_LEVEL, PersistentDataType.INTEGER);
        int level = lv != null ? lv : eco.config().integer("mobs.level_sem_rpg", 1, 0, 10_000);
        long col = EcoMath.mobCol(level, eco.config().decimal("mobs.col_por_level", 3, 0, 1e9),
                eco.config().decimal("mobs.variacao", 0.25, 0, 1), rnd.nextDouble());
        if (col > 0 && eco.deposit(killer.getUniqueId(), col, "mob")) {
            eco.messages().actionBar(killer, "col.mob", eco.col(col));
        }
    }

}
