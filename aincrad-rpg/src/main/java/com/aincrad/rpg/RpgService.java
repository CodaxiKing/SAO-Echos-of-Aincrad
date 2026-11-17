package com.aincrad.rpg;

import com.aincrad.core.message.Messages;
import com.aincrad.rpg.api.PlayerLevelUpEvent;
import com.aincrad.rpg.api.XpGainEvent;
import com.aincrad.rpg.data.PlayerStore;
import com.aincrad.rpg.model.Progress;
import com.aincrad.rpg.model.Stat;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

/** Regras do RPG com o servidor: ganhar e perder XP, level up, atributos no jogador, barra de XP. */
public final class RpgService {

    private final Plugin plugin;
    private final PlayerStore store;
    private final Messages msg;
    private RpgSettings settings;

    private final NamespacedKey keyHealth;
    private final NamespacedKey keyDamage;
    private final NamespacedKey keyAttackSpeed;
    private final NamespacedKey keySpeed;

    public RpgService(Plugin plugin, PlayerStore store, Messages msg, RpgSettings settings) {
        this.plugin = plugin;
        this.store = store;
        this.msg = msg;
        this.settings = settings;
        keyHealth = new NamespacedKey(plugin, "rpg_vida");
        keyDamage = new NamespacedKey(plugin, "rpg_dano");
        keyAttackSpeed = new NamespacedKey(plugin, "rpg_vel_ataque");
        keySpeed = new NamespacedKey(plugin, "rpg_velocidade");
    }

    public RpgSettings settings() {
        return settings;
    }

    public void settings(RpgSettings s) {
        this.settings = s;
    }

    public Messages messages() {
        return msg;
    }

    public PlayerStore store() {
        return store;
    }

    public Progress progress(Player p) {
        return store.get(p.getUniqueId());
    }

    /**
     * Dá XP (dispara {@link XpGainEvent}, depois {@link PlayerLevelUpEvent} se subiu).
     *
     * @return XP que entrou de fato (0 se cancelado ou no level máximo)
     */
    public long giveXp(Player p, long amount, String reason, boolean notify) {
        if (amount <= 0) {
            return 0;
        }
        XpGainEvent ev = new XpGainEvent(p, amount, reason == null ? "api" : reason);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || ev.getAmount() <= 0) {
            return 0;
        }
        Progress pr = progress(p);
        if (pr.level() >= settings.curve.maxLevel()) {
            return 0;
        }
        int before = pr.level();
        int gained = pr.addXp(ev.getAmount(), settings.curve, settings.pointsPerLevel);
        if (notify) {
            msg.actionBar(p, "xp.ganho", Messages.vars("qtd", ev.getAmount(), "atual", pr.xp(),
                    "proximo", settings.curve.toNext(pr.level())));
        }
        if (gained > 0) {
            onLevelUp(p, before, pr.level());
        }
        refresh(p);
        return ev.getAmount();
    }

    private void onLevelUp(Player p, int before, int after) {
        int pts = (after - before) * settings.pointsPerLevel;
        msg.title(p, "xp.levelup_titulo", Duration.ofSeconds(2), Messages.vars("level", after));
        msg.send(p, "xp.levelup", Messages.vars("level", after, "pontos", pts));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        applyAttributes(p);
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            p.setHealth(hp.getValue());
        }
        plugin.getServer().getPluginManager().callEvent(new PlayerLevelUpEvent(p, before, after));
    }

    /** Tira XP (admin). Pode descer de level quando {@code canLoseLevel}. */
    public long takeXp(Player p, long amount, boolean canLoseLevel) {
        long n = progress(p).removeXp(amount, settings.curve, canLoseLevel, settings.pointsPerLevel);
        applyAttributes(p);
        refresh(p);
        return n;
    }

    public void setLevel(Player p, int level) {
        Progress pr = progress(p);
        int before = pr.level();
        pr.setLevel(level, settings.curve, settings.pointsPerLevel);
        applyAttributes(p);
        refresh(p);
        if (pr.level() > before) {
            plugin.getServer().getPluginManager().callEvent(new PlayerLevelUpEvent(p, before, pr.level()));
        }
    }

    /** Penalidade de morte (não roda nos mundos sem perda). @return XP perdido */
    public long deathPenalty(Player p) {
        if (settings.noLossWorlds.contains(p.getWorld().getName())) {
            return 0;
        }
        return progress(p).deathPenalty(settings.deathLoss);
    }

    // ------------------------------------------------------------------ atributos

    public double bonusHealth(Progress pr) {
        return pr.stat(Stat.VITALIDADE) * settings.healthPerVit + (pr.level() - 1) * settings.healthPerLevel;
    }

    /** Põe os modificadores de level e atributos no jogador (troca os antigos). */
    public void applyAttributes(Player p) {
        Progress pr = progress(p);
        set(p, Attribute.MAX_HEALTH, keyHealth, bonusHealth(pr), AttributeModifier.Operation.ADD_NUMBER);
        set(p, Attribute.ATTACK_DAMAGE, keyDamage, pr.stat(Stat.FORCA) * settings.damagePerStr,
                AttributeModifier.Operation.ADD_NUMBER);
        set(p, Attribute.ATTACK_SPEED, keyAttackSpeed, pr.stat(Stat.AGILIDADE) * settings.attackSpeedPerAgi / 100.0,
                AttributeModifier.Operation.ADD_SCALAR);
        set(p, Attribute.MOVEMENT_SPEED, keySpeed, pr.stat(Stat.AGILIDADE) * settings.speedPerAgi / 100.0,
                AttributeModifier.Operation.ADD_SCALAR);
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && p.getHealth() > hp.getValue()) {
            p.setHealth(hp.getValue());
        }
    }

    private static void set(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(key);
        if (amount != 0) {
            inst.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        }
    }

    /** Tira os modificadores do RPG (ao desligar o plugin). */
    public void clearAttributes(Player p) {
        for (Attribute a : new Attribute[]{Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE, Attribute.ATTACK_SPEED, Attribute.MOVEMENT_SPEED}) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null) {
                inst.removeModifier(keyHealth);
                inst.removeModifier(keyDamage);
                inst.removeModifier(keyAttackSpeed);
                inst.removeModifier(keySpeed);
            }
        }
    }

    /** Barra de XP do Minecraft = level e progresso do RPG. */
    public void refresh(Player p) {
        if (!settings.xpBar) {
            return;
        }
        Progress pr = progress(p);
        p.setLevel(pr.level());
        p.setExp(pr.fraction(settings.curve));
    }
}
