package com.aincrad.bosses.fight;

import com.aincrad.bosses.hook.Hooks;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.core.message.Messages;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * As habilidades dos chefes. A cada passo da luta, se já passou o intervalo entre habilidades,
 * uma das que estão fora da recarga é escolhida ao acaso e usada no lutador mais próximo.
 */
final class Skills {

    /** Ticks entre uma habilidade e outra, além da recarga de cada uma. */
    private static final int GAP = 50;
    private static final int MAX_MINIONS = 12;

    private final Plugin plugin;
    private final FightManager fights;
    private final Hooks hooks;

    Skills(Plugin plugin, FightManager fights, Hooks hooks) {
        this.plugin = plugin;
        this.fights = fights;
        this.hooks = hooks;
    }

    void tick(BossFight f) {
        long now = fights.now();
        Player target = fights.target(f);
        if (target == null || now < f.nextSkill) {
            return;
        }
        List<BossDef.Skill> list = f.currentPhase().skills();
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (f.ready.getOrDefault(i, 0L) <= now) {
                ready.add(i);
            }
        }
        if (ready.isEmpty()) {
            return;
        }
        int pick = ready.get(ThreadLocalRandom.current().nextInt(ready.size()));
        BossDef.Skill s = list.get(pick);
        f.ready.put(pick, now + (long) (s.cooldown() * 20));
        f.nextSkill = now + GAP;
        fights.sawSkill(f, s);
        use(f, s, target);
    }

    private void use(BossFight f, BossDef.Skill s, Player target) {
        if (!s.label().isBlank() && s.type() != BossDef.SkillType.LACAIOS) {
            fights.say(f, Messages.parse(s.label(), Messages.vars("chefe", f.def.name(), "alvo", target.getName())));
        }
        if (s.type() == BossDef.SkillType.AREA || s.type() == BossDef.SkillType.SOPRO) {
            fights.telegraph(f, s.warnTicks(), s.type().name().toLowerCase(java.util.Locale.ROOT), true);
        }
        switch (s.type()) {
            case MYTHIC -> mythic(f, s);
            case INVESTIDA -> charge(f, s, target);
            case AREA -> area(f, s, target.getLocation());
            case LACAIOS -> minions(f, s);
            case PROJETIL -> projectile(f, s, target);
            case SALTO -> leap(f, s, target);
            case FURIA -> fury(f, s);
            case SOPRO -> breath(f, s, target);
        }
    }

    /**
     * Golpe do MythicMobs: toca a skill de aviso (se houver), espera {@code aviso_ticks} com o
     * chefe parado e a janela de punição aberta, e então solta a skill do golpe. O dano e os
     * efeitos são os da skill do Mythic.
     */
    private void mythic(BossFight f, BossDef.Skill s) {
        MythicLink link = fights.mythic();
        if (!link.available()) {
            plugin.getLogger().warning("Chefe " + f.def.id() + ": a habilidade mythic \"" + s.mob() + "\" precisa do MythicMobs ligado.");
            return;
        }
        int warn = s.warnTicks();
        fights.telegraph(f, warn, s.mob(), false);
        link.cast(f.entity, s.warnSkill());
        later(f, warn, () -> {
            fights.publish(f, "HEAVY_ATTACK", s.mob());
            if (!link.cast(f.entity, s.mob())) {
                plugin.getLogger().warning("Chefe " + f.def.id() + ": o MythicMobs não conhece a skill \"" + s.mob() + "\".");
            }
            later(f, 20, () -> fights.publish(f, "PHASE_" + (f.phase + 1), ""));
        });
    }

    /** A luta ainda é a mesma e o chefe está vivo? (as habilidades têm partes atrasadas) */
    private boolean alive(BossFight f) {
        return !f.over && f.entity != null && f.entity.isValid() && !f.entity.isDead();
    }

    private void later(BossFight f, long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (alive(f)) {
                r.run();
            }
        }, ticks);
    }

    /** Dano em todos os lutadores a até {@code radius} blocos de {@code center}. */
    private void hit(BossFight f, Location center, double radius, double damage) {
        double amount = damage * f.damageTimes(fights.now());
        for (Player p : fights.fighters(f)) {
            if (p.getWorld().equals(center.getWorld()) && p.getLocation().distanceSquared(center) <= radius * radius) {
                p.damage(amount, f.entity);
            }
        }
    }

    private static void ring(Location center, double radius, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        int points = (int) Math.max(12, radius * 10);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            center.getWorld().spawnParticle(Particle.DUST, center.getX() + Math.cos(a) * radius, center.getY() + 0.15,
                    center.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * Sopro: um cone à frente do chefe, na direção do alvo. Depois do aviso, quem ficou no cone
     * leva o dano e fica paralisado (lentidão e fadiga) por {@code segundos}. A Poção
     * Antiparalisia do AincradLife barra a paralisia, não o dano.
     */
    private void breath(BossFight f, BossDef.Skill s, Player target) {
        LivingEntity boss = f.entity;
        Vector aim = target.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        if (aim.lengthSquared() < 0.01) {
            aim = boss.getLocation().getDirection().setY(0);
        }
        Vector dir = aim.normalize();
        Location from = boss.getLocation().add(0, 1.2, 0);
        double reach = Math.max(3, s.radius());
        int warn = Math.max(5, s.warnTicks());
        boss.getWorld().playSound(from, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.4f);
        for (int t = 0; t < warn; t += 5) {
            later(f, t, () -> cone(from, dir, reach, Particle.SMOKE));
        }
        later(f, warn, () -> {
            cone(from, dir, reach, Particle.SOUL_FIRE_FLAME);
            boss.getWorld().playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
            double amount = s.damage() * f.damageTimes(fights.now());
            int ticks = (int) Math.round(s.seconds() * 20);
            for (Player p : fights.fighters(f)) {
                if (inCone(from, dir, reach, p.getLocation())) {
                    p.damage(amount, boss);
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, ticks, 3));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE, ticks, 1));
                }
            }
        });
    }

    /** O lugar está no cone de 60° que sai de {@code from} na direção {@code dir}? */
    static boolean inCone(Location from, Vector dir, double reach, Location where) {
        if (!from.getWorld().equals(where.getWorld())) {
            return false;
        }
        Vector to = where.toVector().subtract(from.toVector()).setY(0);
        double distance = to.length();
        if (distance > reach) {
            return false;
        }
        return distance < 1.2 || to.normalize().dot(dir) >= 0.5; // cos 60°
    }

    private static void cone(Location from, Vector dir, double reach, Particle particle) {
        for (double d = 1; d <= reach; d += 0.8) {
            double half = d * 0.55;
            Vector side = new Vector(-dir.getZ(), 0, dir.getX());
            for (double o = -half; o <= half; o += 0.7) {
                Location at = from.clone().add(dir.clone().multiply(d)).add(side.clone().multiply(o));
                from.getWorld().spawnParticle(particle, at, 1, 0.1, 0.2, 0.1, 0.01);
            }
        }
    }

    private void charge(BossFight f, BossDef.Skill s, Player target) {
        LivingEntity boss = f.entity;
        Vector dir = target.getLocation().toVector().subtract(boss.getLocation().toVector());
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        dir.setY(0).normalize().multiply(1.6).setY(0.25);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
        boss.setVelocity(dir);
        later(f, 8, () -> {
            boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(0, 1, 0), 6, 1, 0.3, 1, 0);
            hit(f, boss.getLocation(), s.radius(), s.damage());
        });
    }

    private void area(BossFight f, BossDef.Skill s, Location where) {
        Location center = where.clone();
        int warn = Math.max(5, s.warnTicks());
        // Aviso: o círculo vermelho pisca no chão até a hora do golpe.
        for (int t = 0; t < warn; t += 5) {
            later(f, t, () -> ring(center, s.radius(), Color.RED));
        }
        later(f, warn, () -> {
            center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.5, 0), 4, s.radius() / 2, 0.2, s.radius() / 2, 0);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
            hit(f, center, s.radius(), s.damage());
        });
    }

    private void minions(BossFight f, BossDef.Skill s) {
        f.minions.removeIf(m -> !m.isValid());
        EntityType type;
        try {
            type = EntityType.valueOf(s.mob().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Chefe " + f.def.id() + ": lacaio \"" + s.mob() + "\" não existe.");
            return;
        }
        if (!type.isAlive() || !type.isSpawnable()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < s.count() && f.minions.size() < MAX_MINIONS; i++) {
            Location at = f.entity.getLocation().add(rnd.nextDouble(-2.5, 2.5), 0, rnd.nextDouble(-2.5, 2.5));
            if (!f.inArena(at) || !at.getBlock().isPassable()) {
                at = f.entity.getLocation();
            }
            Entity e = at.getWorld().spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM, raw -> {
                raw.getPersistentDataContainer().set(fights.minionKey(), PersistentDataType.STRING, f.def.id());
                if (raw instanceof LivingEntity le) {
                    le.setRemoveWhenFarAway(false);
                    le.setPersistent(false);
                }
            });
            if (e instanceof LivingEntity le) {
                hooks.setMobLevel(le, s.level());
                if (!s.label().isBlank()) {
                    le.customName(Messages.parse(fights.messages().raw("luta.lacaio"), Messages.vars("nome", s.label(), "level", s.level())));
                    le.setCustomNameVisible(true);
                }
                f.minions.add(le);
                le.getWorld().spawnParticle(Particle.POOF, le.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    private void projectile(BossFight f, BossDef.Skill s, Player target) {
        LivingEntity boss = f.entity;
        Vector dir = target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector());
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        dir.normalize();
        if (s.mob().equalsIgnoreCase("bola_de_fogo")) {
            SmallFireball ball = boss.launchProjectile(SmallFireball.class, dir.multiply(1.2));
            ball.setIsIncendiary(false);
            ball.setYield(0);
        } else {
            Arrow arrow = boss.launchProjectile(Arrow.class, dir.multiply(2.2));
            arrow.setDamage(s.damage() * f.damageTimes(fights.now()) / 2.2);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1f, 0.7f);
    }

    private void leap(BossFight f, BossDef.Skill s, Player target) {
        LivingEntity boss = f.entity;
        Vector dir = target.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
        double dist = Math.min(12, dir.length());
        if (dist > 0.1) {
            dir.normalize().multiply(dist / 9.0);
        }
        boss.setVelocity(dir.setY(0.85));
        later(f, 18, () -> {
            Location at = boss.getLocation();
            ring(at, s.radius(), Color.ORANGE);
            at.getWorld().spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.5, 0), 3, 1, 0.2, 1, 0);
            at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            hit(f, at, s.radius(), s.damage());
        });
    }

    private void fury(BossFight f, BossDef.Skill s) {
        f.furyTimes = s.multiplier();
        f.furyUntil = fights.now() + (long) (s.seconds() * 20);
        LivingEntity boss = f.entity;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.4f);
        for (int t = 0; t < s.seconds() * 20; t += 10) {
            later(f, t, () -> boss.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0, boss.getHeight(), 0), 3, 0.4, 0.2, 0.4, 0));
        }
    }
}
