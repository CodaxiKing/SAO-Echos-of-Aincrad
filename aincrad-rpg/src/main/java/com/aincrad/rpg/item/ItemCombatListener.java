package com.aincrad.rpg.item;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.rpg.RpgService;
import com.aincrad.rpg.model.ItemCombat;
import com.aincrad.rpg.model.ItemCombat.Limits;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Os bônus de combate dos itens do RPG num golpe: o defensor pode esquivar ou aparar; o atacante
 * pode dar um crítico e recupera vida com o roubo de vida.
 */
public final class ItemCombatListener implements Listener {

    private final RpgService rpg;
    private final ItemService items;
    private final ConfigFile config;

    public ItemCombatListener(RpgService rpg, ItemService items, ConfigFile config) {
        this.rpg = rpg;
        this.items = items;
        this.config = config;
    }

    private Limits limits() {
        return Limits.parse(config.yaml().getConfigurationSection("combate_itens"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !config.bool("combate_itens.ativo", true)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        Limits limits = limits();
        boolean warn = config.bool("combate_itens.avisar", true);
        Random rnd = ThreadLocalRandom.current();

        if (victim instanceof Player def) {
            Map<String, Double> stats = items.combat(def, rpg.progress(def).level());
            if (ItemCombat.happens(ItemCombat.of(stats, ItemCombat.DODGE), limits.dodge(), rnd)) {
                event.setCancelled(true);
                def.getWorld().playSound(def.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.5f);
                def.getWorld().spawnParticle(Particle.CLOUD, def.getLocation().add(0, 1, 0), 8, 0.2, 0.4, 0.2, 0.03);
                if (warn) {
                    rpg.messages().actionBar(def, "item.esquivou");
                    if (attacker != null) {
                        rpg.messages().actionBar(attacker, "item.alvo_esquivou");
                    }
                }
                return;
            }
            if (holdsWeapon(def) && ItemCombat.happens(ItemCombat.of(stats, ItemCombat.PARRY), limits.parry(), rnd)) {
                event.setDamage(ItemCombat.parried(event.getDamage(), limits));
                def.getWorld().playSound(def.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.3f);
                def.getWorld().spawnParticle(Particle.CRIT, def.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.1);
                if (warn) {
                    rpg.messages().actionBar(def, "item.aparou");
                    if (attacker != null) {
                        rpg.messages().actionBar(attacker, "item.alvo_aparou");
                    }
                }
            }
        }

        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK && cause != DamageCause.PROJECTILE) {
            return;
        }
        Map<String, Double> stats = items.combat(attacker, rpg.progress(attacker).level());
        if (stats.isEmpty()) {
            return;
        }
        if (ItemCombat.happens(ItemCombat.of(stats, ItemCombat.CRIT), limits.crit(), rnd)) {
            event.setDamage(ItemCombat.critical(event.getDamage(), ItemCombat.of(stats, ItemCombat.CRIT_DAMAGE), limits));
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
            victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.15);
            if (warn) {
                rpg.messages().actionBar(attacker, "item.critico");
            }
        }
        double heal = ItemCombat.heal(event.getFinalDamage(), ItemCombat.of(stats, ItemCombat.LIFESTEAL), limits);
        AttributeInstance max = attacker.getAttribute(Attribute.MAX_HEALTH);
        if (heal > 0 && max != null && !attacker.isDead()) {
            attacker.setHealth(Math.min(max.getValue(), attacker.getHealth() + heal));
        }
    }

    /** Aparar pede uma arma na mão: espada, machado, lança, maça ou tridente. */
    private static boolean holdsWeapon(Player p) {
        Material m = p.getInventory().getItemInMainHand().getType();
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_SPEAR") || m == Material.MACE || m == Material.TRIDENT;
    }
}
