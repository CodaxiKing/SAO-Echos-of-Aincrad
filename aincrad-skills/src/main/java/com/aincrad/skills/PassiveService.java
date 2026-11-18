package com.aincrad.skills;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.skills.model.Passive;
import com.aincrad.skills.model.SkillRules;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** As skills passivas: cura em batalha, aparar, busca, rastreio e arremesso de lâminas. */
public final class PassiveService implements Listener {

    private final Messages msg;
    private final SkillStore store;
    private final SkillEngine engine;
    private final NamespacedKey keyThrow;

    private int healEvery;
    private double healAmount;
    private double parryChance;
    private long parryCooldown;
    private int searchRadius;
    private int trackRange;
    private int trackSeconds;
    private Material throwMaterial;
    private double throwDamage;
    private long throwCooldown;

    private int second;
    private final Map<UUID, Long> parryUntil = new HashMap<>();
    private final Map<UUID, Long> throwUntil = new HashMap<>();
    private final Map<UUID, UUID> tracking = new HashMap<>();
    private final Map<UUID, Long> trackingUntil = new HashMap<>();
    private final Map<UUID, Integer> parries = new HashMap<>();

    public PassiveService(Plugin plugin, Messages msg, SkillStore store, SkillEngine engine) {
        this.msg = msg;
        this.store = store;
        this.engine = engine;
        keyThrow = new NamespacedKey(plugin, "lamina");
    }

    public void reload(ConfigFile c) {
        healEvery = c.integer("passivas.cura_em_batalha.intervalo", 10, 1, 600);
        healAmount = c.decimal("passivas.cura_em_batalha.vida", 1.0, 0, 100);
        parryChance = c.decimal("passivas.aparar.chance", 0.2, 0, 1);
        parryCooldown = Math.round(c.decimal("passivas.aparar.recarga", 3, 0, 600) * 1000);
        searchRadius = c.integer("passivas.busca.raio", 24, 4, 64);
        trackRange = c.integer("passivas.rastreio.alcance", 120, 8, 512);
        trackSeconds = c.integer("passivas.rastreio.duracao", 120, 5, 3600);
        Material m = Material.matchMaterial(c.string("passivas.arremesso.material", "IRON_NUGGET"));
        throwMaterial = m == null || !m.isItem() ? Material.IRON_NUGGET : m;
        throwDamage = c.decimal("passivas.arremesso.dano", 4, 0, 1000);
        throwCooldown = Math.round(c.decimal("passivas.arremesso.recarga", 1, 0, 60) * 1000);
    }

    private boolean has(Player p, Passive passive) {
        SkillStore.Data d = store.data(p);
        return d != null && d.has(passive);
    }

    /** Golpes aparados pelo jogador desde que entrou (para critérios de skill única). */
    public int parries(Player p) {
        return parries.getOrDefault(p.getUniqueId(), 0);
    }

    public void forget(UUID id) {
        parryUntil.remove(id);
        throwUntil.remove(id);
        tracking.remove(id);
        trackingUntil.remove(id);
        parries.remove(id);
    }

    /** Uma vez por segundo. */
    public void tick() {
        second++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            SkillStore.Data d = store.data(p);
            if (d == null || p.isDead()) {
                continue;
            }
            if (second % healEvery == 0 && d.has(Passive.CURA_EM_BATALHA)) {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                if (max != null && p.getHealth() < max.getValue()) {
                    p.setHealth(Math.min(max.getValue(), p.getHealth() + healAmount));
                }
            }
            if (second % 2 == 0 && d.has(Passive.BUSCA)) {
                for (Entity e : p.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
                    if (e instanceof Monster) {
                        // Só este jogador vê a marca.
                        p.spawnParticle(Particle.END_ROD, e.getLocation().add(0, e.getHeight() + 0.4, 0), 2, 0.05, 0.1, 0.05, 0);
                    }
                }
            }
            track(p, d);
        }
    }

    private void track(Player p, SkillStore.Data d) {
        UUID target = tracking.get(p.getUniqueId());
        if (target == null) {
            return;
        }
        Entity e = Bukkit.getEntity(target);
        Long until = trackingUntil.get(p.getUniqueId());
        if (!d.has(Passive.RASTREIO) || e == null || !e.isValid() || !e.getWorld().equals(p.getWorld())
                || until == null || until < System.currentTimeMillis()
                || e.getLocation().distanceSquared(p.getLocation()) > (double) trackRange * trackRange) {
            tracking.remove(p.getUniqueId());
            trackingUntil.remove(p.getUniqueId());
            msg.actionBar(p, "passiva.rastro_perdido");
            return;
        }
        Location a = p.getLocation();
        Location b = e.getLocation();
        msg.actionBar(p, "passiva.rastro", Messages.vars("seta", SkillRules.arrow(a.getYaw(), a.getX(), a.getZ(), b.getX(), b.getZ()),
                "alvo", e.getName(), "distancia", (int) Math.round(a.distance(b))));
    }

    /** {@code /rastrear}: marca o alvo que o jogador está olhando. */
    public void startTracking(Player p) {
        if (!has(p, Passive.RASTREIO)) {
            msg.send(p, "passiva.sem_rastreio");
            return;
        }
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 40, 0.8,
                e -> e instanceof LivingEntity && !e.equals(p));
        if (r == null || r.getHitEntity() == null) {
            if (tracking.remove(p.getUniqueId()) != null) {
                msg.send(p, "passiva.rastro_parou");
            } else {
                msg.send(p, "passiva.rastro_sem_alvo");
            }
            return;
        }
        tracking.put(p.getUniqueId(), r.getHitEntity().getUniqueId());
        trackingUntil.put(p.getUniqueId(), System.currentTimeMillis() + trackSeconds * 1000L);
        msg.send(p, "passiva.rastro_marcado", Messages.vars("alvo", r.getHitEntity().getName()));
    }

    /** Aparar: com a arma na mão, chance de anular um golpe que vem de frente. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onParry(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player p) || !(event.getDamager() instanceof LivingEntity attacker)
                || !has(p, Passive.APARAR) || engine.weapon(p) == null || engine.frozen(p)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (parryUntil.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        Vector to = attacker.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        Vector look = p.getLocation().getDirection().setY(0);
        if (to.lengthSquared() < 1e-6 || look.lengthSquared() < 1e-6 || look.normalize().dot(to.normalize()) < 0.5) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= parryChance) {
            return;
        }
        event.setCancelled(true);
        parryUntil.put(p.getUniqueId(), now + parryCooldown);
        parries.merge(p.getUniqueId(), 1, Integer::sum);
        store.count(p, com.aincrad.skills.model.UniqueRules.PARRIES, 1);
        p.getWorld().playSound(p.getLocation(), "item.shield.block", 1f, 1.3f);
        p.getWorld().spawnParticle(Particle.CRIT, p.getEyeLocation().add(look.multiply(0.6)), 8, 0.2, 0.2, 0.2, 0.1);
        msg.actionBar(p, "passiva.aparou");
    }

    /** Arremesso: botão direito com uma lâmina na mão. */
    @EventHandler
    public void onThrow(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != throwMaterial || !has(p, Passive.ARREMESSO) || engine.frozen(p)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (throwUntil.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        event.setCancelled(true);
        throwUntil.put(p.getUniqueId(), now + throwCooldown);
        hand.setAmount(hand.getAmount() - 1);
        Snowball ball = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(1.8));
        ball.setItem(new ItemStack(throwMaterial));
        ball.getPersistentDataContainer().set(keyThrow, PersistentDataType.DOUBLE, throwDamage);
        p.getWorld().playSound(p.getLocation(), "entity.arrow.shoot", 0.8f, 1.4f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onThrowHit(ProjectileHitEvent event) {
        Double damage = event.getEntity().getPersistentDataContainer().get(keyThrow, PersistentDataType.DOUBLE);
        if (damage != null && event.getHitEntity() instanceof LivingEntity target
                && event.getEntity().getShooter() instanceof Player p && !target.equals(p)) {
            target.damage(damage, p);
        }
    }
}
