package com.aincrad.skills;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.skills.api.SkillCastEvent;
import com.aincrad.skills.hook.Hooks;
import com.aincrad.skills.model.SkillDef;
import com.aincrad.skills.model.SkillRules;
import com.aincrad.skills.model.WeaponType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O motor das Sword Skills: reconhece a arma, sobe a proficiência com o uso, executa a skill
 * (golpes, recarga e a pausa depois) e cuida dos atalhos (troca de mão + número da hotbar).
 */
public final class SkillEngine implements Listener {

    /** Por que uma skill não saiu. */
    public enum Fail { OK, SEM_DADOS, TRAVADO, ARMA, UNICA, PROFICIENCIA, ZONA_SEGURA, RECARGA, CANCELADA }

    /** Arquivos de skill que vêm no jar. */
    public static final List<String> BUNDLED = List.of("espada", "florete", "adaga", "machado", "lanca", "duas_maos", "duas_espadas");

    private static final long ARMED_MS = 3000;

    private final Plugin plugin;
    private final Messages msg;
    private final SkillStore store;
    private final UniqueService unique;
    private final Hooks hooks;

    private Map<String, SkillDef> skills = Map.of();
    private List<String> errors = List.of();
    private Map<WeaponType, WeaponType.Match> weapons = Map.of();
    private double gainBase;
    private double gainMin;
    private double profBonus;
    private boolean swapActivation;
    private boolean blockSafeZone;

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> frozen = new HashMap<>();
    private final Map<UUID, Long> armed = new HashMap<>();
    private final Set<UUID> casting = new HashSet<>();

    public SkillEngine(Plugin plugin, Messages msg, SkillStore store, UniqueService unique, Hooks hooks) {
        this.plugin = plugin;
        this.msg = msg;
        this.store = store;
        this.unique = unique;
        this.hooks = hooks;
    }

    // ------------------------------------------------------------------ config

    public void reload(ConfigFile config) {
        gainBase = config.decimal("proficiencia.chance_por_golpe", 0.5, 0, 1);
        gainMin = config.decimal("proficiencia.chance_minima", 0.05, 0, 1);
        profBonus = config.decimal("proficiencia.bonus_de_dano_por_ponto", 0.0005, 0, 1);
        swapActivation = config.bool("ativacao.troca_de_mao", true);
        blockSafeZone = config.bool("bloquear_em_zona_segura", true);

        Map<WeaponType, WeaponType.Match> w = new EnumMap<>(WeaponType.class);
        ConfigurationSection sec = config.yaml().getConfigurationSection("armas");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                WeaponType t = WeaponType.parse(key);
                if (t != null && t != WeaponType.DUAS_ESPADAS) {
                    w.put(t, new WeaponType.Match(sec.getStringList(key + ".nomes"), sec.getStringList(key + ".materiais")));
                }
            }
        }
        weapons = w;

        File dir = new File(plugin.getDataFolder(), "skills");
        for (String name : BUNDLED) {
            if (!new File(dir, name + ".yml").exists()) {
                plugin.saveResource("skills/" + name + ".yml", false);
            }
        }
        Map<String, SkillDef> all = new LinkedHashMap<>();
        List<String> errs = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                all.putAll(SkillDef.parse(YamlConfiguration.loadConfiguration(f), f.getName(), errs));
            }
        }
        skills = all;
        errors = List.copyOf(errs);
        errs.forEach(e -> plugin.getLogger().warning("skills/" + e));
        plugin.getLogger().info(skills.size() + " Sword Skills carregadas.");
    }

    public Map<String, SkillDef> skills() {
        return skills;
    }

    public List<String> errors() {
        return errors;
    }

    public SkillDef skill(String id) {
        return id == null ? null : skills.get(id.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ arma

    private WeaponType type(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return null;
        }
        String name = null;
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component c = meta.displayName();
            name = c == null ? null : PlainTextComponentSerializer.plainText().serialize(c);
        }
        return WeaponType.detect(it.getType().name(), name, weapons);
    }

    /** O tipo da arma na mão. Uma espada em cada mão vale Duas Espadas, para o dono da skill única. */
    public WeaponType weapon(Player p) {
        WeaponType main = type(p.getInventory().getItemInMainHand());
        if (main == WeaponType.ESPADA && unique.has(p, UniqueService.DUAL)
                && type(p.getInventory().getItemInOffHand()) == WeaponType.ESPADA) {
            return WeaponType.DUAS_ESPADAS;
        }
        return main;
    }

    // ------------------------------------------------------------------ usar

    public boolean frozen(Player p) {
        Long until = frozen.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long cooldown(Player p, String skill) {
        Map<String, Long> m = cooldowns.get(p.getUniqueId());
        Long until = m == null ? null : m.get(skill);
        return until == null ? 0 : SkillRules.remaining(until, System.currentTimeMillis());
    }

    /** O jogador pode equipar a skill? (proficiência e skill única) */
    public boolean canLearn(Player p, SkillStore.Data d, SkillDef def) {
        return d.prof(def.weapon()) >= def.minProf() && (def.unique() == null || unique.has(p, def.unique()));
    }

    /** Usa a skill. Avisa o jogador do motivo quando não sai. */
    public Fail cast(Player p, SkillDef def) {
        Fail f = tryCast(p, def);
        switch (f) {
            case OK -> {
                msg.actionBar(p, "skill.usou", Messages.vars("skill", def.name()));
                store.count(p, com.aincrad.skills.model.UniqueRules.CASTS, 1);
            }
            case RECARGA -> msg.actionBar(p, "skill.recarga", Messages.vars("skill", def.name(),
                    "tempo", String.format(Locale.ROOT, "%.1f", cooldown(p, def.id()) / 1000.0)));
            case ARMA -> msg.actionBar(p, "skill.arma", Messages.vars("skill", def.name(), "arma", msg.raw("armas." + def.weapon().key())));
            case PROFICIENCIA -> msg.actionBar(p, "skill.proficiencia", Messages.vars("skill", def.name(), "valor", def.minProf()));
            case ZONA_SEGURA -> msg.actionBar(p, "skill.zona_segura");
            case UNICA -> msg.actionBar(p, "skill.unica");
            case SEM_DADOS -> msg.actionBar(p, "skill.carregando");
            case TRAVADO, CANCELADA -> {
            }
        }
        return f;
    }

    private Fail tryCast(Player p, SkillDef def) {
        SkillStore.Data d = store.data(p);
        if (d == null) {
            return Fail.SEM_DADOS;
        }
        UUID id = p.getUniqueId();
        if (casting.contains(id) || frozen(p) || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
            return Fail.TRAVADO;
        }
        if (weapon(p) != def.weapon()) {
            return Fail.ARMA;
        }
        if (def.unique() != null && !unique.has(p, def.unique())) {
            return Fail.UNICA;
        }
        int prof = d.prof(def.weapon());
        if (prof < def.minProf()) {
            return Fail.PROFICIENCIA;
        }
        if (blockSafeZone && hooks.safeZone(p.getLocation())) {
            return Fail.ZONA_SEGURA;
        }
        if (cooldown(p, def.id()) > 0) {
            return Fail.RECARGA;
        }
        SkillCastEvent event = new SkillCastEvent(p, def.id(), def.weapon().key());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Fail.CANCELADA;
        }
        cooldowns.computeIfAbsent(id, k -> new HashMap<>()).put(def.id(), System.currentTimeMillis() + def.cooldownMs());
        casting.add(id);
        if (def.dash() > 0) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() > 1e-6) {
                p.setVelocity(dir.normalize().multiply(def.dash() * 0.35).setY(0.12));
            }
        }
        AttributeInstance attr = p.getAttribute(Attribute.ATTACK_DAMAGE);
        double damage = SkillRules.hitDamage(attr == null ? 1 : attr.getValue(), def.damage(), prof, profBonus);
        Particle particle = particle(def.particle());

        new BukkitRunnable() {
            private int done;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || weapon(p) != def.weapon()) {
                    finish(false);
                    return;
                }
                for (LivingEntity t : targets(p, def)) {
                    t.setNoDamageTicks(0);
                    t.damage(damage, p);
                    Location at = t.getLocation().add(0, t.getHeight() * 0.6, 0);
                    if (particle != null) {
                        t.getWorld().spawnParticle(particle, at, 1, 0.2, 0.2, 0.2, 0);
                    }
                    t.getWorld().spawnParticle(Particle.CRIT, at, 6, 0.3, 0.3, 0.3, 0.1);
                }
                p.getWorld().playSound(p.getLocation(), def.sound(), 1f, 0.9f + done * 0.04f);
                p.swingMainHand();
                if (++done >= def.hits()) {
                    finish(true);
                }
            }

            private void finish(boolean complete) {
                cancel();
                casting.remove(id);
                // A pausa depois do uso: travado, sem atacar nem usar outra skill.
                if (complete && def.freezeMs() > 0 && p.isOnline()) {
                    frozen.put(id, System.currentTimeMillis() + def.freezeMs());
                    int ticks = (int) Math.max(1, def.freezeMs() / 50);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, false));
                }
            }
        }.runTaskTimer(plugin, 0L, def.hitTicks());
        return Fail.OK;
    }

    private static Particle particle(String name) {
        try {
            Particle p = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            // Partículas que pedem um dado extra (cor, bloco) ficam de fora.
            return p.getDataType() == Void.class ? p : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean valid(Player p, Entity e) {
        return e instanceof LivingEntity le && !e.equals(p) && !(e instanceof ArmorStand) && !le.isDead() && !le.isInvulnerable()
                && !(e instanceof Tameable t && p.equals(t.getOwner()))
                && !(e instanceof Player o && (o.getGameMode() == GameMode.SPECTATOR || o.getGameMode() == GameMode.CREATIVE));
    }

    private static List<LivingEntity> targets(Player p, SkillDef def) {
        List<LivingEntity> out = new ArrayList<>();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        switch (def.shape()) {
            case ALVO -> {
                RayTraceResult r = p.getWorld().rayTraceEntities(eye, dir, def.range(), 0.6, e -> valid(p, e));
                if (r != null && r.getHitEntity() instanceof LivingEntity le && p.hasLineOfSight(le)) {
                    out.add(le);
                }
            }
            case LINHA -> {
                for (Entity e : p.getNearbyEntities(def.range(), def.range(), def.range())) {
                    Location c = e.getLocation().add(0, e.getHeight() / 2, 0);
                    if (valid(p, e) && SkillRules.inLine(eye.getX(), eye.getY(), eye.getZ(), dir.getX(), dir.getY(), dir.getZ(),
                            c.getX(), c.getY(), c.getZ(), def.range(), 1.2 + e.getWidth() / 2) && p.hasLineOfSight(e)) {
                        out.add((LivingEntity) e);
                    }
                }
            }
            case AREA -> {
                double max = def.range() * def.range();
                for (Entity e : p.getNearbyEntities(def.range(), def.range(), def.range())) {
                    if (valid(p, e) && e.getLocation().distanceSquared(p.getLocation()) <= max && p.hasLineOfSight(e)) {
                        out.add((LivingEntity) e);
                    }
                }
            }
        }
        return out;
    }

    public void forget(UUID id) {
        cooldowns.remove(id);
        frozen.remove(id);
        armed.remove(id);
        casting.remove(id);
    }

    // ------------------------------------------------------------------ proficiência e pausa

    /** Travado depois de uma skill: o golpe comum não sai. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onFrozenAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && frozen(p) && !casting.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Cada golpe que acerta com a arma pode render um ponto de proficiência nela. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || !(event.getEntity() instanceof LivingEntity target)
                || target instanceof ArmorStand || event.getFinalDamage() <= 0) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        SkillStore.Data d = store.data(p);
        WeaponType t = d == null ? null : weapon(p);
        if (t == null) {
            return;
        }
        store.count(p, com.aincrad.skills.model.UniqueRules.HITS, 1);
        int prof = d.prof(t);
        if (ThreadLocalRandom.current().nextDouble() < SkillRules.gainChance(prof, gainBase, gainMin)) {
            store.setProf(p.getUniqueId(), d, t, prof + 1);
            store.progressed(p);
            String weapon = msg.raw("armas." + t.key());
            if ((prof + 1) % 50 == 0) {
                msg.send(p, "proficiencia.marco", Messages.vars("arma", weapon, "valor", prof + 1));
            } else if (!casting.contains(p.getUniqueId())) {
                msg.actionBar(p, "proficiencia.subiu", Messages.vars("arma", weapon, "valor", prof + 1));
            }
        }
    }

    // ------------------------------------------------------------------ atalhos

    /** Troca de mão (F) com a arma na mão arma os atalhos; agachado, a troca acontece de verdade. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        SkillStore.Data d = store.data(p);
        if (!swapActivation || p.isSneaking() || d == null || weapon(p) == null) {
            return;
        }
        int[] keys = SkillRules.keys(p.getInventory().getHeldItemSlot());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < SkillRules.ACTIVE_SLOTS; i++) {
            SkillDef def = skill(d.active(i));
            if (def != null) {
                parts.add(msg.raw("atalho.item").replace("<tecla>", String.valueOf(keys[i] + 1)).replace("<skill>", def.name()));
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        armed.put(p.getUniqueId(), System.currentTimeMillis() + ARMED_MS);
        p.sendActionBar(Messages.parse(String.join(msg.raw("atalho.separador"), parts)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        Long until = armed.remove(p.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            return;
        }
        int slot = SkillRules.slotOfKey(event.getPreviousSlot(), event.getNewSlot());
        SkillStore.Data d = store.data(p);
        SkillDef def = slot < 0 || d == null ? null : skill(d.active(slot));
        if (def != null) {
            event.setCancelled(true);
            cast(p, def);
        }
    }
}
