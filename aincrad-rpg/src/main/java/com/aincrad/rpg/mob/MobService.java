package com.aincrad.rpg.mob;

import com.aincrad.core.AincradCore;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.rpg.RpgService;
import com.aincrad.rpg.RpgSettings;
import com.aincrad.rpg.item.ItemService;
import com.aincrad.rpg.model.MobRewards;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Level dos mobs: sorteado na zona onde nasce, guardado no mob, aumenta vida e dano, aparece no
 * nome; ao morrer para um jogador dá XP (com redução se o jogador está muito acima) e pode dropar
 * itens com raridade.
 */
public final class MobService implements Listener {

    private final RpgService rpg;
    private final ItemService items;
    private final NamespacedKey keyLevel;
    private final NamespacedKey keyHealth;

    public MobService(Plugin plugin, RpgService rpg, ItemService items) {
        this.rpg = rpg;
        this.items = items;
        keyLevel = new NamespacedKey(plugin, "mob_level");
        keyHealth = new NamespacedKey(plugin, "mob_vida");
    }

    /** Level do mob (0 = sem level: jogador, boss de outro plugin, passivo...). */
    public int level(Entity e) {
        Integer v = e.getPersistentDataContainer().get(keyLevel, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Faixa de level para o lugar: a menor região com zona, depois o mundo, depois o padrão. */
    public RpgSettings.LevelRange rangeAt(Location l) {
        RpgSettings s = rpg.settings();
        Region best = null;
        for (Region r : AincradCore.get().regions().at(l)) {
            if (s.zones.containsKey(r.name().toLowerCase(Locale.ROOT)) && (best == null || r.volume() < best.volume())) {
                best = r;
            }
        }
        if (best != null) {
            return s.zones.get(best.name().toLowerCase(Locale.ROOT));
        }
        RpgSettings.LevelRange w = s.worlds.get(l.getWorld().getName().toLowerCase(Locale.ROOT));
        return w != null ? w : s.defaultRange;
    }

    /** Dá level ao mob (também usado por outros plugins pela API). */
    public void setLevel(LivingEntity e, int level) {
        setLevel(e, level, null);
    }

    /**
     * Dá level ao mob.
     *
     * @param baseName o nome do mob no lugar do nome do Minecraft (mob do MythicMobs), ou null
     */
    public void setLevel(LivingEntity e, int level, Component baseName) {
        RpgSettings s = rpg.settings();
        int lv = Math.max(1, level);
        e.getPersistentDataContainer().set(keyLevel, PersistentDataType.INTEGER, lv);
        AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(keyHealth);
            double extra = MobRewards.scale(lv, s.healthPerMobLevel) - 1;
            if (extra > 0) {
                hp.addModifier(new AttributeModifier(keyHealth, extra, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
            }
            e.setHealth(hp.getValue());
        }
        Component name = Messages.parse(s.mobName, Messages.vars("level", lv),
                Placeholder.component("mob", baseName != null ? baseName : Component.translatable(e.getType().translationKey())));
        e.customName(name);
        e.setCustomNameVisible(s.mobNameVisible);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e instanceof Mob) || (rpg.settings().onlyHostile && !(e instanceof Monster))) {
            return;
        }
        // Mob com nome dado por outro plugin (boss) ou já com level: não mexe.
        if (level(e) > 0 || e.customName() != null) {
            return;
        }
        RpgSettings.LevelRange r = rangeAt(e.getLocation());
        setLevel(e, MobRewards.pick(r.min(), r.max(), ThreadLocalRandom.current().nextDouble()));
    }

    /** Dano do mob escala com o level dele; arma acima do level do jogador bate fraco. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity src = event.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            src = shooter;
        }
        int lv = level(src);
        if (lv > 1 && !(src instanceof Player)) {
            event.setDamage(event.getDamage() * MobRewards.scale(lv, rpg.settings().damagePerMobLevel));
        }
        if (src instanceof Player p) {
            int need = items.requiredLevel(p.getInventory().getItemInMainHand());
            if (need > rpg.progress(p).level()) {
                event.setDamage(event.getDamage() * rpg.settings().weaponPenalty);
                rpg.messages().actionBar(p, "item.arma_level", Messages.vars("level", need));
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player) {
            return;
        }
        event.setDroppedExp(0);
        int lv = level(e);
        Player killer = e.getKiller();
        if (lv <= 0 || killer == null) {
            return;
        }
        RpgSettings s = rpg.settings();
        long xp = MobRewards.xp(s.xpBase, lv, rpg.progress(killer).level(), s.xpFreeGap, s.xpStepLoss, s.xpMinFactor);
        rpg.giveXp(killer, xp, "mob", true);

        List<ItemService.Template> drops = items.drops(lv);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (ItemService.Template t : drops) {
            if (rnd.nextDouble() < t.dropChance()) {
                ItemStack it = items.create(t.id(), null);
                if (it != null) {
                    event.getDrops().add(it);
                }
            }
        }
    }
}
