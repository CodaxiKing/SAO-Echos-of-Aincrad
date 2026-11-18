package com.aincrad.life.alchemy;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.life.alchemy.AlchemyMath.Purity;
import com.aincrad.life.alchemy.AlchemyRegistry.Formula;
import com.aincrad.life.alchemy.AlchemyRegistry.Kind;
import com.aincrad.life.alchemy.AlchemyRegistry.Product;
import com.aincrad.life.api.PoisonAttackEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * O que os produtos da alquimia fazem: poção de cura (aos poucos, com recarga compartilhada),
 * antídoto, poção de buff (uma ativa por vez), óleo e veneno aplicados na arma (com contagem de
 * golpes gravada no item) e frascos arremessáveis de área. O efeito de cada poção fica gravado
 * nela, já com a pureza e os bônus de quem preparou.
 */
public final class PotionService implements Listener {

    private static final String PROFILE_BUFF = "pocao";

    /** Uma cura em andamento. */
    private static final class Healing {
        double perStep;
        int steps;
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final NamespacedKey keyFormula;
    private final NamespacedKey keyKind;
    private final NamespacedKey keyName;
    private final NamespacedKey keyEffect;
    private final NamespacedKey keyValue;
    private final NamespacedKey keySeconds;
    private final NamespacedKey keyHits;
    private final NamespacedKey keyRadius;
    private final NamespacedKey keySide;
    private final NamespacedKey keyPurity;
    private final NamespacedKey keyOilDamage;
    private final NamespacedKey keyOilHits;
    private final NamespacedKey keyVenom;
    private final NamespacedKey keyVenomLevel;
    private final NamespacedKey keyVenomSeconds;
    private final NamespacedKey keyVenomHits;
    private final NamespacedKey keyVenomId;
    private final Map<UUID, Healing> healing = new HashMap<>();
    private final Map<UUID, Long> healCooldown = new HashMap<>();
    private long tick;

    public PotionService(Plugin plugin, Messages msg, ConfigFile config) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        keyFormula = new NamespacedKey(plugin, "pocao");
        keyKind = new NamespacedKey(plugin, "pocao_tipo");
        keyName = new NamespacedKey(plugin, "pocao_nome");
        keyEffect = new NamespacedKey(plugin, "pocao_efeito");
        keyValue = new NamespacedKey(plugin, "pocao_valor");
        keySeconds = new NamespacedKey(plugin, "pocao_segundos");
        keyHits = new NamespacedKey(plugin, "pocao_golpes");
        keyRadius = new NamespacedKey(plugin, "pocao_raio");
        keySide = new NamespacedKey(plugin, "pocao_colateral");
        keyPurity = new NamespacedKey(plugin, "pocao_pureza");
        keyOilDamage = new NamespacedKey(plugin, "arma_oleo_dano");
        keyOilHits = new NamespacedKey(plugin, "arma_oleo_golpes");
        keyVenom = new NamespacedKey(plugin, "arma_veneno");
        keyVenomLevel = new NamespacedKey(plugin, "arma_veneno_nivel");
        keyVenomSeconds = new NamespacedKey(plugin, "arma_veneno_segundos");
        keyVenomHits = new NamespacedKey(plugin, "arma_veneno_golpes");
        keyVenomId = new NamespacedKey(plugin, "arma_veneno_formula");
    }

    /** O efeito de poção do Minecraft com esse nome (ex.: "strength"), ou null. */
    public static PotionEffectType effect(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(key);
    }

    /** É um produto da alquimia? (uma poção feita na mesa não serve de "água" para outra) */
    public boolean isProduct(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(keyFormula);
    }

    /** Id da fórmula de um produto da alquimia (null: não é). */
    public String formulaId(ItemStack it) {
        return isProduct(it) ? it.getItemMeta().getPersistentDataContainer().get(keyFormula, PersistentDataType.STRING) : null;
    }

    /** A pureza de um produto da alquimia (null: não é). */
    public Purity purity(ItemStack it) {
        return isProduct(it) ? Purity.parse(it.getItemMeta().getPersistentDataContainer().get(keyPurity, PersistentDataType.STRING)) : null;
    }

    // ------------------------------------------------------------------ o item

    /**
     * A poção, o óleo, o veneno ou o frasco pronto.
     *
     * @param strength fração a mais no valor (nós da árvore de quem preparou)
     * @param duration fração a mais na duração
     * @param hits     golpes a mais (óleo e veneno)
     * @param noSide   nó "sem colateral": a mistura impura não dá o efeito ruim
     */
    public ItemStack item(Formula f, Purity purity, int amount, double strength, double duration, int hits, double radius, boolean noSide) {
        Product p = f.product();
        ItemStack it = new ItemStack(p.kind() == Kind.ARREMESSAVEL ? Material.SPLASH_POTION : Material.POTION);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof PotionMeta pm) {
            try {
                pm.setColor(Color.fromRGB(Integer.parseInt(f.tint().replace("#", ""), 16) & 0xFFFFFF));
            } catch (NumberFormatException e) {
                pm.setColor(Color.fromRGB(0x3F76E4));
            }
        }
        // A pureza escala o que mede a força de cada produto: vida, dano ou tempo.
        double factor = purity.factor();
        double value = p.value();
        double seconds = p.seconds() * (1 + Math.max(0, duration));
        switch (p.kind()) {
            case CURA -> value = value * factor * (1 + Math.max(0, strength));
            case OLEO -> value = value * factor * (1 + Math.max(0, strength));
            default -> seconds = seconds * factor;
        }
        value = Math.round(value * 10) / 10.0;
        int totalHits = p.hits() + (p.hits() > 0 ? Math.max(0, hits) : 0);
        double totalRadius = p.radius() * (1 + Math.max(0, radius));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyFormula, PersistentDataType.STRING, f.id());
        pdc.set(keyKind, PersistentDataType.STRING, p.kind().name());
        pdc.set(keyName, PersistentDataType.STRING, f.name());
        pdc.set(keyEffect, PersistentDataType.STRING, p.effect());
        pdc.set(keyValue, PersistentDataType.DOUBLE, value);
        pdc.set(keySeconds, PersistentDataType.DOUBLE, seconds);
        pdc.set(keyHits, PersistentDataType.INTEGER, totalHits);
        pdc.set(keyRadius, PersistentDataType.DOUBLE, totalRadius);
        pdc.set(keyPurity, PersistentDataType.STRING, purity.name());
        boolean side = purity == Purity.IMPURA && !noSide;
        if (side) {
            pdc.set(keySide, PersistentDataType.BYTE, (byte) 1);
        }
        Component pureza = msg.get("alquimia.pureza." + purity.key());
        var vars = Messages.vars("nome", f.name(), "pureza", pureza, "valor", number(value), "segundos", Math.round(seconds),
                "minutos", Math.max(1, Math.round(seconds / 60)), "golpes", totalHits, "raio", number(totalRadius),
                "efeito", effectName(p.effect()));
        meta.displayName(msg.get("alquimia.pocao_nome", vars));
        java.util.List<Component> lore = new java.util.ArrayList<>(msg.list("alquimia.pocao_lore." + p.kind().key(), vars));
        lore.add(msg.get("alquimia.pocao_pureza", vars));
        if (side) {
            lore.add(msg.get("alquimia.pocao_colateral"));
        }
        meta.lore(lore);
        meta.setMaxStackSize(16);
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, Math.min(16, amount)));
        return it;
    }

    private NamespacedKey flag(String name) {
        return new NamespacedKey(plugin, "pocao_" + name);
    }

    private boolean has(PersistentDataContainer pdc, String name) {
        return pdc.has(flag(name), PersistentDataType.BYTE);
    }

    /**
     * Grava na poção o que a árvore do alquimista põe a mais (vale para quem a usar).
     *
     * @param healSeconds segundos a mais (negativo = menos) para a cura acontecer
     * @param instant     Elixir: cura na hora
     * @param quick       aplicar na arma com um clique, sem beber
     * @param deep        Peçonha: o veneno entra um nível mais forte
     * @param fireEdge    Fio de Fogo: o óleo também queima quem está em volta do alvo
     * @param farther     o frasco voa mais longe (0.2 = +20%)
     * @param storm       Tempestade: o frasco se divide em três
     */
    public void tune(ItemStack it, double healSeconds, boolean instant, boolean quick, boolean deep, boolean fireEdge, double farther, boolean storm) {
        if (it == null || !it.hasItemMeta()) {
            return;
        }
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        java.util.List<Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
        if (healSeconds != 0) {
            pdc.set(keySeconds, PersistentDataType.DOUBLE, Math.max(1, pdc.getOrDefault(keySeconds, PersistentDataType.DOUBLE, 10.0) + healSeconds));
        }
        java.util.Map<String, Boolean> marks = new java.util.LinkedHashMap<>();
        marks.put("elixir", instant);
        marks.put("rapido", quick);
        marks.put("peconha", deep);
        marks.put("fio_de_fogo", fireEdge);
        marks.put("tempestade", storm);
        marks.forEach((name, on) -> {
            if (on) {
                pdc.set(flag(name), PersistentDataType.BYTE, (byte) 1);
                lore.add(msg.get("alquimia.marca." + name));
            }
        });
        if (farther > 0) {
            pdc.set(new NamespacedKey(plugin, "pocao_alcance"), PersistentDataType.DOUBLE, farther);
        }
        meta.lore(lore);
        it.setItemMeta(meta);
    }

    private String effectName(String effect) {
        String first = effect.contains(",") ? effect.substring(0, effect.indexOf(',')) : effect;
        return msg.has("alquimia.efeito." + first) ? msg.raw("alquimia.efeito." + first) : first.replace('_', ' ');
    }

    static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    // ------------------------------------------------------------------ beber

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack it = event.getItem();
        if (!isProduct(it)) {
            return;
        }
        Player p = event.getPlayer();
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        Kind kind = Kind.parse(pdc.get(keyKind, PersistentDataType.STRING));
        String name = pdc.getOrDefault(keyName, PersistentDataType.STRING, "?");
        String effect = pdc.getOrDefault(keyEffect, PersistentDataType.STRING, "");
        double value = pdc.getOrDefault(keyValue, PersistentDataType.DOUBLE, 0.0);
        double seconds = pdc.getOrDefault(keySeconds, PersistentDataType.DOUBLE, 0.0);
        int hits = pdc.getOrDefault(keyHits, PersistentDataType.INTEGER, 0);
        if (kind == null) {
            return;
        }
        var vars = Messages.vars("nome", name);
        switch (kind) {
            case CURA -> {
                long now = System.currentTimeMillis();
                long ready = healCooldown.getOrDefault(p.getUniqueId(), 0L);
                if (ready > now) {
                    event.setCancelled(true);
                    msg.actionBar(p, "alquimia.cura_recarga", Messages.vars("segundos", (ready - now + 999) / 1000));
                    return;
                }
                healCooldown.put(p.getUniqueId(), now + config.integer("alquimia.cura_recarga_segundos", 5, 0, 3600) * 1000L);
                if (has(pdc, "elixir")) {
                    // Elixir: a poção perfeita do mestre cura na hora, como um cristal.
                    org.bukkit.attribute.AttributeInstance max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + value));
                    msg.actionBar(p, "alquimia.bebeu_elixir", Messages.vars("nome", name, "vida", number(value)));
                    break;
                }
                // A vida volta aos poucos, duas vezes por segundo, e não de uma vez.
                Healing h = new Healing();
                h.steps = (int) Math.max(1, Math.round(seconds * 2));
                h.perStep = value / h.steps;
                healing.put(p.getUniqueId(), h);
                msg.actionBar(p, "alquimia.bebeu_cura", Messages.vars("nome", name, "vida", number(value), "segundos", Math.round(seconds)));
            }
            case ANTIDOTO -> {
                for (String e : effect.split(",")) {
                    PotionEffectType t = effect(e);
                    if (t != null) {
                        p.removePotionEffect(t);
                    }
                }
                msg.actionBar(p, "alquimia.bebeu_antidoto", vars);
            }
            case BUFF -> {
                PotionEffectType t = effect(effect);
                if (t == null) {
                    return;
                }
                // Só um buff de poção por vez: o anterior sai.
                String before = AincradCore.get().profile(p).get(plugin, PROFILE_BUFF);
                if (before != null) {
                    PotionEffectType old = effect(before.split(";")[0]);
                    if (old != null) {
                        p.removePotionEffect(old);
                    }
                }
                int amp = (int) Math.max(0, Math.round(value) - 1);
                long ticks = Math.round(seconds * 20);
                AincradCore.get().profile(p).set(plugin, PROFILE_BUFF, effect + ";" + amp + ";" + (System.currentTimeMillis() + ticks * 50));
                p.addPotionEffect(new PotionEffect(t, (int) ticks, amp, false, true, true));
                msg.actionBar(p, "alquimia.bebeu_buff", vars);
            }
            case OLEO, VENENO -> {
                // "Beber" um óleo ou veneno é aplicar na arma que está na outra mão.
                EquipmentSlot weaponHand = event.getHand() == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
                if (!apply(p, pdc, kind, weaponHand)) {
                    event.setCancelled(true);
                    return;
                }
            }
            default -> {
                return;
            }
        }
        if (pdc.has(keySide)) {
            PotionEffectType bad = effect(config.string("alquimia.colateral.efeito", "slowness"));
            if (bad != null) {
                p.addPotionEffect(new PotionEffect(bad, config.integer("alquimia.colateral.segundos", 5, 1, 600) * 20, 0, true, false, true));
            }
            msg.send(p, "alquimia.colateral");
        }
    }

    /** Põe o óleo ou o veneno na arma da mão dada. @return false se não há arma ali */
    private boolean apply(Player p, PersistentDataContainer pdc, Kind kind, EquipmentSlot weaponHand) {
        String name = pdc.getOrDefault(keyName, PersistentDataType.STRING, "?");
        double value = pdc.getOrDefault(keyValue, PersistentDataType.DOUBLE, 0.0);
        int hits = pdc.getOrDefault(keyHits, PersistentDataType.INTEGER, 0);
        ItemStack weapon = p.getInventory().getItem(weaponHand);
        if (weapon.getType().getMaxDurability() <= 0 || weapon.getAmount() != 1) {
            msg.send(p, "alquimia.arma_na_outra_mao");
            return false;
        }
        ItemMeta meta = weapon.getItemMeta();
        PersistentDataContainer w = meta.getPersistentDataContainer();
        if (kind == Kind.OLEO) {
            w.set(keyOilDamage, PersistentDataType.DOUBLE, value);
            w.set(keyOilHits, PersistentDataType.INTEGER, hits);
            if (has(pdc, "fio_de_fogo")) {
                w.set(flag("fio_de_fogo"), PersistentDataType.BYTE, (byte) 1);
            } else {
                w.remove(flag("fio_de_fogo"));
            }
        } else {
            // Peçonha: o veneno perfeito do mestre entra um nível mais forte.
            int level = (int) Math.max(1, Math.round(value)) + (has(pdc, "peconha") ? 1 : 0);
            w.set(keyVenom, PersistentDataType.STRING, pdc.getOrDefault(keyEffect, PersistentDataType.STRING, ""));
            w.set(keyVenomLevel, PersistentDataType.INTEGER, level);
            w.set(keyVenomSeconds, PersistentDataType.DOUBLE, pdc.getOrDefault(keySeconds, PersistentDataType.DOUBLE, 0.0));
            w.set(keyVenomHits, PersistentDataType.INTEGER, hits);
            w.set(keyVenomId, PersistentDataType.STRING, pdc.getOrDefault(keyFormula, PersistentDataType.STRING, ""));
        }
        weapon.setItemMeta(meta);
        p.getInventory().setItem(weaponHand, weapon);
        p.playSound(p.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 0.8f, 1.2f);
        msg.send(p, "alquimia.aplicou", Messages.vars("nome", name, "golpes", hits));
        return true;
    }

    /** Aplicar rápido (árvore): o óleo ou veneno marcado vai para a arma com um clique, sem beber. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onQuickApply(org.bukkit.event.player.PlayerInteractEvent event) {
        ItemStack it = event.getItem();
        if (event.getHand() == null || !event.getAction().isRightClick() || !isProduct(it)) {
            return;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        Kind kind = Kind.parse(pdc.get(keyKind, PersistentDataType.STRING));
        if ((kind != Kind.OLEO && kind != Kind.VENENO) || !has(pdc, "rapido")) {
            return;
        }
        event.setCancelled(true);
        EquipmentSlot weaponHand = event.getHand() == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        if (apply(event.getPlayer(), pdc, kind, weaponHand)) {
            it.setAmount(it.getAmount() - 1);
        }
    }

    /** Evita que o dano em área do Fio de Fogo e os frascos da Tempestade chamem a si mesmos. */
    private boolean busy;

    /** O frasco arremessado: voa mais longe e, com a Tempestade, se divide em três. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrow(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (busy || !(event.getEntity() instanceof org.bukkit.entity.ThrownPotion potion) || !(potion.getShooter() instanceof Player p)) {
            return;
        }
        ItemStack it = potion.getItem();
        if (!isProduct(it)) {
            return;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        double farther = pdc.getOrDefault(new NamespacedKey(plugin, "pocao_alcance"), PersistentDataType.DOUBLE, 0.0);
        if (farther > 0) {
            potion.setVelocity(potion.getVelocity().multiply(1 + farther));
        }
        if (has(pdc, "tempestade")) {
            busy = true;
            try {
                for (double angle : new double[]{-0.3, 0.3}) {
                    org.bukkit.entity.ThrownPotion extra = p.launchProjectile(org.bukkit.entity.ThrownPotion.class, potion.getVelocity().clone().rotateAroundY(angle));
                    extra.setItem(it.clone());
                }
            } finally {
                busy = false;
            }
        }
    }

    // ------------------------------------------------------------------ arma com óleo ou veneno

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (busy) {
            return; // o dano em área do próprio óleo
        }
        ItemStack weapon = p.getInventory().getItemInMainHand();
        if (!weapon.hasItemMeta()) {
            return;
        }
        ItemMeta meta = weapon.getItemMeta();
        PersistentDataContainer w = meta.getPersistentDataContainer();
        boolean changed = false;
        Integer oil = w.get(keyOilHits, PersistentDataType.INTEGER);
        if (oil != null) {
            event.setDamage(event.getDamage() + w.getOrDefault(keyOilDamage, PersistentDataType.DOUBLE, 0.0));
            if (!busy && w.has(flag("fio_de_fogo"), PersistentDataType.BYTE)) {
                // Fio de Fogo: metade do dano do óleo em quem está em volta do alvo.
                double splash = w.getOrDefault(keyOilDamage, PersistentDataType.DOUBLE, 0.0) * 0.5;
                busy = true;
                try {
                    for (LivingEntity near : target.getWorld().getNearbyLivingEntities(target.getLocation(), 2.5)) {
                        if (near != target && near != p && !(near instanceof Player)) {
                            near.damage(splash, p);
                            near.setFireTicks(Math.max(near.getFireTicks(), 40));
                        }
                    }
                } finally {
                    busy = false;
                }
            }
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, target.getHeight() * 0.6, 0), 4, 0.2, 0.2, 0.2, 0.01);
            if (oil <= 1) {
                w.remove(keyOilHits);
                w.remove(keyOilDamage);
                msg.actionBar(p, "alquimia.oleo_acabou");
            } else {
                w.set(keyOilHits, PersistentDataType.INTEGER, oil - 1);
            }
            changed = true;
        }
        Integer venom = w.get(keyVenomHits, PersistentDataType.INTEGER);
        if (venom != null) {
            PotionEffectType t = effect(w.get(keyVenom, PersistentDataType.STRING));
            if (t != null) {
                int ticks = (int) Math.round(w.getOrDefault(keyVenomSeconds, PersistentDataType.DOUBLE, 5.0) * 20);
                target.addPotionEffect(new PotionEffect(t, ticks, w.getOrDefault(keyVenomLevel, PersistentDataType.INTEGER, 1) - 1));
                if (target instanceof Player victim) {
                    // Veneno em jogador conta como ataque: o AincradReputation ouve este evento.
                    Bukkit.getPluginManager().callEvent(new PoisonAttackEvent(p, victim, w.getOrDefault(keyVenomId, PersistentDataType.STRING, "")));
                }
            }
            if (venom <= 1) {
                w.remove(keyVenom);
                w.remove(keyVenomLevel);
                w.remove(keyVenomSeconds);
                w.remove(keyVenomHits);
                w.remove(keyVenomId);
                msg.actionBar(p, "alquimia.veneno_acabou");
            } else {
                w.set(keyVenomHits, PersistentDataType.INTEGER, venom - 1);
            }
            changed = true;
        }
        if (changed) {
            weapon.setItemMeta(meta);
        }
    }

    // ------------------------------------------------------------------ frascos de área

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ItemStack it = event.getPotion().getItem();
        if (!isProduct(it)) {
            return;
        }
        event.setCancelled(true);
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        PotionEffectType t = effect(pdc.get(keyEffect, PersistentDataType.STRING));
        double radius = pdc.getOrDefault(keyRadius, PersistentDataType.DOUBLE, 3.0);
        int ticks = (int) Math.round(pdc.getOrDefault(keySeconds, PersistentDataType.DOUBLE, 5.0) * 20);
        int amp = (int) Math.max(0, Math.round(pdc.getOrDefault(keyValue, PersistentDataType.DOUBLE, 1.0)) - 1);
        Location at = event.getPotion().getLocation();
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, (int) (30 * radius), radius / 2, 0.6, radius / 2, 0.02);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 0.7f);
        if (t == null) {
            return;
        }
        Object thrower = event.getPotion().getShooter();
        for (LivingEntity e : at.getWorld().getNearbyLivingEntities(at, radius)) {
            if (e != thrower) {
                e.addPotionEffect(new PotionEffect(t, ticks, amp));
            }
        }
    }

    // ------------------------------------------------------------------ tempo

    /** Roda a cada tick: as curas em andamento (duas vezes por segundo). */
    public void tick() {
        tick++;
        if (tick % 10 != 0 || healing.isEmpty()) {
            return;
        }
        healing.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            Healing h = e.getValue();
            if (p == null || p.isDead()) {
                return true;
            }
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + h.perStep));
            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 1, 0.2, 0.1, 0.2, 0);
            return --h.steps <= 0;
        });
    }

    /** Quem trocou de servidor com um buff de poção ativo recebe o que resta dele. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String raw = AincradCore.get().profile(p).get(plugin, PROFILE_BUFF);
        if (raw == null) {
            return;
        }
        String[] parts = raw.split(";");
        try {
            PotionEffectType t = effect(parts[0]);
            long left = (Long.parseLong(parts[2]) - System.currentTimeMillis()) / 50;
            if (t == null || left <= 0) {
                AincradCore.get().profile(p).remove(plugin, PROFILE_BUFF);
            } else if (!p.hasPotionEffect(t)) {
                p.addPotionEffect(new PotionEffect(t, (int) left, Integer.parseInt(parts[1]), false, true, true));
            }
        } catch (RuntimeException e) {
            AincradCore.get().profile(p).remove(plugin, PROFILE_BUFF);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        healing.remove(event.getPlayer().getUniqueId());
        healCooldown.remove(event.getPlayer().getUniqueId());
    }
}
