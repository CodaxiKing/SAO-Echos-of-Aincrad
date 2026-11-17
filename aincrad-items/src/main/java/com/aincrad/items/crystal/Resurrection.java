package com.aincrad.items.crystal;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cristal de Ressurreição. Um golpe fatal não mata na hora: o jogador fica "se desfazendo" por
 * alguns segundos, parado e sem poder agir. Nesse tempo outro jogador pode clicar nele com o
 * cristal e cancelar a morte. Se o tempo acabar (ou ele sair do servidor), morre de verdade.
 */
public final class Resurrection implements Listener {

    /** Mortes que não passam pela janela: vazio do mundo e /kill. */
    private static final Set<EntityDamageEvent.DamageCause> INSTANT = EnumSet.of(
            EntityDamageEvent.DamageCause.VOID, EntityDamageEvent.DamageCause.KILL, EntityDamageEvent.DamageCause.SUICIDE);

    private static final class Dying {
        int ticksLeft;
        /** Quem deu o golpe fatal, se foi um jogador (para a morte contar para ele). */
        UUID killer;
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final CrystalService crystals;
    private final NamespacedKey freezeKey;
    private final Map<UUID, Dying> dying = new HashMap<>();
    /** Jogadores sendo mortos de verdade agora (o dano não deve abrir outra janela). */
    private final Set<UUID> finishing = new java.util.HashSet<>();

    public Resurrection(Plugin plugin, Messages msg, ConfigFile config, CrystalService crystals) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.crystals = crystals;
        this.freezeKey = new NamespacedKey(plugin, "desfazendo");
    }

    public boolean isDying(Player p) {
        return dying.containsKey(p.getUniqueId());
    }

    private int window() {
        return config.integer("ressurreicao.janela_segundos", 10, 0, 300);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        return damager instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
    }

    private boolean someoneNear(Player p) {
        int range = config.integer("ressurreicao.precisa_alguem_a", 24, 0, 256);
        if (range <= 0) {
            return true;
        }
        for (Player o : p.getWorld().getPlayers()) {
            if (!o.equals(p) && !o.isDead() && o.getGameMode() != GameMode.SPECTATOR && !isDying(o)
                    && o.getLocation().distanceSquared(p.getLocation()) <= (double) range * range) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsTotem(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    /** Depois de todos os outros plugins: se o golpe ainda é fatal, abre a janela no lugar da morte. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        if (isDying(p)) {
            event.setCancelled(true);
            return;
        }
        if (window() <= 0 || finishing.contains(p.getUniqueId()) || INSTANT.contains(event.getCause())
                || event.getFinalDamage() < p.getHealth() || holdsTotem(p) || !someoneNear(p)) {
            return;
        }
        event.setCancelled(true);
        Player killer = event instanceof EntityDamageByEntityEvent by ? attacker(by.getDamager()) : null;
        start(p, killer);
    }

    private void start(Player p, Player killer) {
        Dying d = new Dying();
        d.ticksLeft = window() * 20;
        d.killer = killer == null ? null : killer.getUniqueId();
        dying.put(p.getUniqueId(), d);
        p.setHealth(Math.min(1.0, p.getHealth()));
        p.setFireTicks(0);
        freeze(p, true);
        msg.title(p, "ressurreicao.morrendo_titulo", Duration.ofSeconds(3));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        int range = Math.max(16, config.integer("ressurreicao.precisa_alguem_a", 24, 0, 256));
        for (Player o : p.getWorld().getPlayers()) {
            if (!o.equals(p) && o.getLocation().distanceSquared(p.getLocation()) <= (double) range * range) {
                msg.send(o, "ressurreicao.aviso_perto", Messages.vars("jogador", p.getName(), "segundos", window()));
            }
        }
    }

    /** Roda a cada 5 ticks: contagem, partículas e o fim da janela. */
    public void tick() {
        for (Map.Entry<UUID, Dying> e : new ArrayList<>(dying.entrySet())) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            Dying d = e.getValue();
            if (p == null || p.isDead()) {
                dying.remove(e.getKey());
                continue;
            }
            d.ticksLeft -= 5;
            if (d.ticksLeft <= 0) {
                die(p);
                continue;
            }
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 3, 0.3, 0.6, 0.3, 0.02);
            if (d.ticksLeft % 20 == 0) {
                msg.actionBar(p, "ressurreicao.morrendo_barra", Messages.vars("segundos", d.ticksLeft / 20));
            }
        }
    }

    /** O tempo acabou: morre de verdade (os outros plugins veem a morte normal, com o matador certo). */
    private void die(Player p) {
        Dying d = dying.remove(p.getUniqueId());
        freeze(p, false);
        if (d == null) {
            return;
        }
        Player killer = d.killer == null ? null : plugin.getServer().getPlayer(d.killer);
        finishing.add(p.getUniqueId());
        try {
            if (killer != null) {
                p.setKiller(killer);
            }
            p.setHealth(0);
        } finally {
            finishing.remove(p.getUniqueId());
        }
    }

    /** Clique num jogador que está se desfazendo, com o cristal na mão: cancela a morte. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSave(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        CrystalDef def = crystals.registry().of(hand);
        if (def == null || def.type() != CrystalDef.Type.RESSURREICAO) {
            return;
        }
        event.setCancelled(true);
        int reach = config.integer("ressurreicao.alcance", 5, 1, 32);
        if (!isDying(target) || isDying(p) || p.getLocation().distanceSquared(target.getLocation()) > (double) reach * reach) {
            msg.send(p, "ressurreicao.ninguem");
            return;
        }
        if (!crystals.begin(p, def, target)) {
            return;
        }
        dying.remove(target.getUniqueId());
        freeze(target, false);
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double top = max == null ? 20 : max.getValue();
        target.setHealth(Math.max(1, Math.min(top, top * def.amount())));
        crystals.spend(p, def);
        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.3);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        msg.send(target, "ressurreicao.salvo", Messages.vars("salvador", p.getName()));
        msg.send(p, "ressurreicao.salvou", Messages.vars("jogador", target.getName()));
    }

    private void freeze(Player p, boolean on) {
        for (Attribute a : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) {
                continue;
            }
            inst.removeModifier(freezeKey);
            if (on) {
                inst.addTransientModifier(new AttributeModifier(freezeKey, -1.0,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
            }
        }
    }

    // ------------------------------------------------------------------ quem está se desfazendo não age

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isDying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player p = attacker(event.getDamager());
        if (p != null && isDying(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isDying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isDying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Sair do servidor não salva ninguém: morre antes de sair. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (isDying(event.getPlayer())) {
            die(event.getPlayer());
        }
    }

    /** Ao desligar o plugin: quem estava se desfazendo morre (ninguém fica preso sem se mexer). */
    public void shutdown() {
        for (UUID id : new ArrayList<>(dying.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                die(p);
            }
        }
        dying.clear();
    }
}
