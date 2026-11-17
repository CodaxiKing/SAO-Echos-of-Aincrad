package com.aincrad.items.crystal;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.Cooldowns;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.items.api.CrystalUseEvent;
import com.aincrad.items.hook.FloorsBridge;
import com.aincrad.items.hook.Hooks;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O uso dos cristais: clique direito com o cristal na mão. Confere a permissão, as áreas
 * anti-cristal e a recarga, dispara o {@link CrystalUseEvent} e aplica o efeito do tipo. A
 * ressurreição (usada em outro jogador) fica em {@link Resurrection}.
 */
public final class CrystalService implements Listener {

    /** Level que o AincradRPG grava no mob. */
    private static final NamespacedKey RPG_LEVEL = NamespacedKey.fromString("aincradrpg:mob_level");

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final CrystalRegistry registry;
    private final Hooks hooks;
    private final Cooldowns cooldowns = new Cooldowns();
    /** Teleportes em espera. */
    private final Map<UUID, BukkitTask> teleports = new HashMap<>();
    /** Quem está digitando a mensagem de um cristal de gravação → até quando (ms). */
    private final Map<UUID, Long> recording = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public CrystalService(Plugin plugin, Messages msg, ConfigFile config, CrystalRegistry registry, Hooks hooks) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.registry = registry;
        this.hooks = hooks;
    }

    public CrystalRegistry registry() {
        return registry;
    }

    public Messages messages() {
        return msg;
    }

    // ------------------------------------------------------------------ regras comuns

    /**
     * Pode usar agora? Confere permissão, área anti-cristal e recarga, e dispara o evento.
     * Não gasta nada: chame {@link #spend} quando o efeito acontecer.
     */
    public boolean begin(Player p, CrystalDef def, Player target) {
        if (!p.hasPermission("aincrad.items.use")) {
            msg.send(p, "geral.sem_permissao");
            return false;
        }
        if (hooks.blocked(p.getLocation())) {
            msg.send(p, "cristal.bloqueado");
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 0.5f);
            return false;
        }
        long left = cooldowns.remaining(p.getUniqueId(), def.cooldownGroup());
        if (left > 0) {
            msg.send(p, "cristal.recarga", Messages.vars("segundos", (left + 999) / 1000));
            return false;
        }
        CrystalUseEvent ev = new CrystalUseEvent(p, def.id(), def.type().name().toLowerCase(Locale.ROOT), target);
        plugin.getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    /** Marca a recarga e gasta um cristal (da mão principal, ou de onde estiver no inventário). */
    public void spend(Player p, CrystalDef def) {
        if (def.cooldown() > 0) {
            cooldowns.set(p.getUniqueId(), def.cooldownGroup(), def.cooldown() * 1000L);
        }
        if (!def.consumed()) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (def.equals(registry.of(hand)) && registry.recording(hand) == null) {
            hand.setAmount(hand.getAmount() - 1);
            return;
        }
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (def.equals(registry.of(it)) && registry.recording(it) == null) {
                it.setAmount(it.getAmount() - 1);
                return;
            }
        }
    }

    private boolean has(Player p, CrystalDef def) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (def.equals(registry.of(it))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ clique

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        CrystalDef def = registry.of(hand);
        if (def == null) {
            return;
        }
        event.setCancelled(true);
        // Um clique pode chegar duas vezes (no ar e no bloco): vale um a cada 250 ms.
        long now = System.currentTimeMillis();
        if (now - lastClick.getOrDefault(p.getUniqueId(), 0L) < 250) {
            return;
        }
        lastClick.put(p.getUniqueId(), now);
        switch (def.type()) {
            case CURA -> heal(p, def);
            case ANTIDOTO -> antidote(p, def);
            case TELEPORTE -> teleportMenu(p, def);
            case RESSURREICAO -> msg.send(p, "ressurreicao.ninguem");
            case GRAVACAO -> record(p, def, hand);
        }
    }

    private void heal(Player p, CrystalDef def) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double top = max == null ? 20 : max.getValue();
        if (p.getHealth() >= top) {
            msg.send(p, "cura.cheia");
            return;
        }
        if (!begin(p, def, p)) {
            return;
        }
        p.setHealth(Math.min(top, p.getHealth() + top * def.amount()));
        spend(p, def);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.2, 0), 8, 0.4, 0.4, 0.4, 0);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        msg.send(p, "cura.usado");
    }

    private void antidote(Player p, CrystalDef def) {
        List<PotionEffectType> found = new ArrayList<>();
        for (String name : def.remove()) {
            PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            if (t != null && p.hasPotionEffect(t)) {
                found.add(t);
            }
        }
        if (found.isEmpty()) {
            msg.send(p, "antidoto.nada");
            return;
        }
        if (!begin(p, def, p)) {
            return;
        }
        found.forEach(p::removePotionEffect);
        spend(p, def);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 12, 0.4, 0.5, 0.4, 0);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.9f);
        msg.send(p, "antidoto.usado");
    }

    // ------------------------------------------------------------------ teleporte

    private void teleportMenu(Player p, CrystalDef def) {
        if (!hooks.hasFloors()) {
            msg.send(p, "teleporte.sem_andares");
            return;
        }
        if (!begin(p, def, p)) {
            return;
        }
        Menu menu = Menu.of(msg.get("teleporte.titulo")).rows(6);
        int here = FloorsBridge.current(p);
        FloorsBridge.destinations(p).forEach((n, name) -> {
            if (n == here) {
                menu.item(Menu.Button.of(ItemBuilder.of(Material.LODESTONE).amount(Math.min(64, n)).build(),
                        msg.get("teleporte.aqui", Messages.vars("andar", name)), null));
                return;
            }
            menu.item(Menu.Button.of(ItemBuilder.of(Material.ENDER_EYE).amount(Math.min(64, n)).build(),
                    msg.get("teleporte.andar", Messages.vars("andar", name)), pl -> warmup(pl, def, n, name))
                    .lore(List.of(msg.get("teleporte.andar_lore"))).closing());
        });
        menu.button(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("teleporte.fechar"), pl -> { }).closing());
        AincradCore.get().menus().open(p, menu);
    }

    private void warmup(Player p, CrystalDef def, int floor, String name) {
        cancelTeleport(p.getUniqueId(), false);
        if (def.waitSeconds() <= 0) {
            finishTeleport(p, def, floor);
            return;
        }
        Location start = p.getLocation();
        msg.send(p, "teleporte.espera", Messages.vars("andar", name, "segundos", def.waitSeconds()));
        teleports.put(p.getUniqueId(), plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int left = def.waitSeconds() * 4;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancelTeleport(p.getUniqueId(), false);
                    return;
                }
                if (!p.getWorld().equals(start.getWorld()) || p.getLocation().distanceSquared(start) > 0.25) {
                    cancelTeleport(p.getUniqueId(), true);
                    return;
                }
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0.01);
                if (--left <= 0) {
                    cancelTeleport(p.getUniqueId(), false);
                    finishTeleport(p, def, floor);
                }
            }
        }, 5L, 5L));
    }

    private void finishTeleport(Player p, CrystalDef def, int floor) {
        // O cristal pode ter saído do inventário durante a espera, ou a área ter mudado.
        if (!has(p, def) || hooks.blocked(p.getLocation()) || !FloorsBridge.teleport(p, floor)) {
            msg.send(p, "teleporte.falhou");
            return;
        }
        spend(p, def);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
    }

    private void cancelTeleport(UUID id, boolean tell) {
        BukkitTask t = teleports.remove(id);
        if (t == null) {
            return;
        }
        t.cancel();
        Player p = plugin.getServer().getPlayer(id);
        if (tell && p != null) {
            msg.send(p, "teleporte.cancelado");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            cancelTeleport(p.getUniqueId(), true);
        }
    }

    // ------------------------------------------------------------------ gravação

    private void record(Player p, CrystalDef def, ItemStack hand) {
        CrystalRegistry.Recording r = registry.recording(hand);
        if (r != null) {
            // Ouvir não gasta nem tem recarga, mas respeita as áreas anti-cristal.
            if (hooks.blocked(p.getLocation())) {
                msg.send(p, "cristal.bloqueado");
                return;
            }
            msg.sendRaw(p, "gravacao.ouvir_cabecalho", Messages.vars("autor", r.author(), "data", r.date()));
            msg.sendRaw(p, "gravacao.ouvir_texto", Messages.vars("texto", r.text()));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            return;
        }
        if (!begin(p, def, p)) {
            return;
        }
        recording.put(p.getUniqueId(), System.currentTimeMillis() + 60_000);
        msg.send(p, "gravacao.pedir", Messages.vars("maximo", def.maxLength()));
    }

    /** A próxima mensagem do chat de quem está gravando vira o conteúdo do cristal (não vai para o chat). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Long until = recording.remove(id);
        if (until == null || until < System.currentTimeMillis()) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> finishRecording(event.getPlayer(), text));
    }

    private void finishRecording(Player p, String text) {
        if (!p.isOnline()) {
            return;
        }
        if (text.strip().equalsIgnoreCase("cancelar")) {
            msg.send(p, "gravacao.cancelado");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        CrystalDef def = registry.of(hand);
        if (def == null || def.type() != CrystalDef.Type.GRAVACAO || registry.recording(hand) != null) {
            msg.send(p, "gravacao.sem_cristal");
            return;
        }
        ItemStack recorded = registry.record(hand, def, new CrystalRegistry.Recording(def.clip(text), p.getName(),
                new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())));
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().addItem(recorded).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        } else {
            p.getInventory().setItemInMainHand(recorded);
        }
        spend(p, def);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
        msg.send(p, "gravacao.gravado");
    }

    // ------------------------------------------------------------------ drops

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null || !(event.getEntity() instanceof Mob) || !config.bool("drops", true)) {
            return;
        }
        Integer lv = RPG_LEVEL == null ? null : event.getEntity().getPersistentDataContainer().get(RPG_LEVEL, PersistentDataType.INTEGER);
        int level = lv == null ? 0 : lv;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (CrystalDef def : registry.all()) {
            if (def.dropChance() > 0 && level >= def.dropMobLevel() && rnd.nextDouble() < def.dropChance()) {
                event.getDrops().add(registry.create(def, 1));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancelTeleport(id, false);
        recording.remove(id);
        lastClick.remove(id);
        // A recarga fica: sair e entrar de novo não zera.
    }
}
