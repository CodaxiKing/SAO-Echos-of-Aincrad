package com.aincrad.ui.hud;

import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.flat.FlatHud;
import com.aincrad.ui.flat.FlatOverlay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * O alvo do jogador: o mob em que ele bateu (ou que bateu nele) nos últimos segundos; sem isso,
 * o que ele está mirando. Dá o nome, a vida e a cor do cursor para a HUD.
 */
public final class TargetTracker implements Listener {

    private record Sticky(UUID entity, long until) {
    }

    private final Map<UUID, Sticky> sticky = new HashMap<>();
    private boolean looked;
    private Method mobLevel;

    private void stick(Player p, Entity e, long millis) {
        if (e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.equals(p)) {
            sticky.put(p.getUniqueId(), new Sticky(e.getUniqueId(), System.currentTimeMillis() + millis));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player p) {
            stick(p, event.getEntity(), 6000);
        } else if (event.getEntity() instanceof Player victim) {
            // Quem bate no jogador vira o alvo só se ele não está de olho em outro.
            Sticky s = sticky.get(victim.getUniqueId());
            if (s == null || s.until() < System.currentTimeMillis()) {
                stick(victim, damager, 4000);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sticky.remove(event.getPlayer().getUniqueId());
    }

    /** O alvo para desenhar, ou null. */
    public FlatOverlay.Target of(Player p, UiSettings st) {
        LivingEntity e = null;
        Sticky s = sticky.get(p.getUniqueId());
        if (s != null) {
            Entity found = s.until() > System.currentTimeMillis() ? Bukkit.getEntity(s.entity()) : null;
            if (found instanceof LivingEntity le && !le.isDead() && le.getWorld().equals(p.getWorld())
                    && le.getLocation().distanceSquared(p.getLocation()) < 48 * 48) {
                e = le;
            } else {
                sticky.remove(p.getUniqueId());
            }
        }
        if (e == null && p.getTargetEntity(st.targetRange) instanceof LivingEntity le && !(le instanceof ArmorStand) && !le.isDead()
                && !le.isInvisible()) {
            e = le;
        }
        if (e == null) {
            return null;
        }
        AttributeInstance attr = e.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? Math.max(1.0, attr.getValue()) : Math.max(1.0, e.getHealth());
        double ratio = Math.max(0, Math.min(1, e.getHealth() / max));
        int color = e instanceof Player ? CursorRules.NEUTRAL : CursorRules.color(mobLevel(e), FlatHud.level(p));
        return new FlatOverlay.Target(name(e), ratio, color, CursorRules.bars(max, st.targetBossHealth, st.targetBossBars));
    }

    private static String name(LivingEntity e) {
        if (e instanceof Player pl) {
            return pl.getName();
        }
        Component custom = e.customName();
        if (custom != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(custom).trim();
            if (!plain.isEmpty()) {
                return plain;
            }
        }
        String type = e.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    /** Level do mob pelo AincradRPG (opcional, por reflexão); 0 sem ele. */
    private int mobLevel(Entity e) {
        if (!looked) {
            looked = true;
            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (c.getName().equals("com.aincrad.rpg.api.AincradRPGAPI")) {
                    try {
                        mobLevel = c.getMethod("getMobLevel", Entity.class);
                    } catch (NoSuchMethodException ex) {
                        mobLevel = null;
                    }
                }
            }
        }
        if (mobLevel == null) {
            return 0;
        }
        try {
            return ((Number) mobLevel.invoke(null, e)).intValue();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            mobLevel = null;
            return 0;
        }
    }

    /** Depois de um reload, procura o AincradRPG de novo. */
    public void resetHooks() {
        looked = false;
        mobLevel = null;
    }
}
