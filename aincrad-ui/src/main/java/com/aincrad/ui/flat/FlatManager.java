package com.aincrad.ui.flat;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.api.FlatApi;
import com.aincrad.ui.api.FlatDraw;
import com.aincrad.ui.api.FlatScreen;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.sound.UiSound;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Telas planas: desenhadas no título de uma BossBar (a primeira do jogador, de cor branca, que o
 * pack deixa invisível). Duas camadas na mesma linha: a HUD em barras e a tela aberta (loja...).
 *
 * <p>Com uma tela aberta o jogador fica parado e o movimento da câmera move um cursor desenhado
 * na tela; clique esquerdo aciona o botão embaixo do cursor; agachar fecha.
 */
public final class FlatManager implements Listener, FlatApi {

    /** Área do cursor (x a partir do centro; y a partir do topo). */
    public static final int MIN_X = -236;
    public static final int MAX_X = 236;
    public static final int MIN_Y = 2;
    public static final int MAX_Y = 252;
    private static final String CURSOR = "aincrad:flat/cursor";
    private static final int CURSOR_SIZE = 8;
    private static final long CLICK_GRACE_TICKS = 6;

    private static final class Session {
        final FlatScreen screen;
        float lastYaw;
        float lastPitch;
        double cx;
        double cy = 120;
        long openedAt;
        long lastClick;
        boolean redraw = true;
        FlatCanvas canvas;

        Session(FlatScreen screen) {
            this.screen = screen;
        }
    }

    private static final class View {
        BossBar bar;
        Component hud;
        Component screen;
    }

    private final AincradUIPlugin plugin;
    private final FlatAtlas atlas = new FlatAtlas();
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final NamespacedKey freezeKey;
    private Function<Player, int[]> energy;
    private Function<Player, java.util.List<FlatOverlay.Cooldown>> cooldowns;
    private BukkitTask task;
    private BukkitTask rebuild;
    private long tick;
    private boolean recentering;
    private boolean warnedMissing;

    public FlatManager(AincradUIPlugin plugin) {
        this.plugin = plugin;
        this.freezeKey = new NamespacedKey(plugin, "tela_plana");
    }

    public FlatAtlas atlas() {
        return atlas;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        closeAll();
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            View v = views.remove(p.getUniqueId());
            if (v != null && v.bar != null) {
                p.hideBossBar(v.bar);
            }
        }
        views.clear();
    }

    /** Peças fixas do motor: o cursor em todas as alturas e a HUD. Chamado antes de gerar o pack. */
    public void prepareBuiltins() {
        FlatHud.resetHooks();
        boolean art = plugin.settings().flatArt;
        prepareLayer(d -> FlatHud.paint(d, FlatHud.sample(plugin.settings().hudPartyMax), -plugin.settings().flatHudX, art));
        prepareLayer(d -> FlatOverlay.prepare(d, plugin.settings().flatHudX, art));
        FlatCanvas c = new FlatCanvas(atlas, true, 0, 0);
        for (int y = MIN_Y; y <= MAX_Y; y++) {
            c.icon(0, y, CURSOR_SIZE, CURSOR);
        }
    }

    // ------------------------------------------------------------------ API

    @Override
    public boolean canShow(Player player) {
        return plugin.settings().flatEnabled && plugin.packStatus().hasPack(player);
    }

    @Override
    public void prepare(FlatScreen screen) {
        try {
            screen.draw(new FlatCanvas(atlas, true, 0, 0), null);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao preparar a tela " + screen.getClass().getSimpleName(), e);
        }
        scheduleRebuild();
    }

    /** Peças novas entraram: refaz o pack uma vez só, no próximo tick. */
    public void scheduleRebuild() {
        if (!atlas.dirty() || rebuild != null || !plugin.isEnabled()) {
            return;
        }
        rebuild = plugin.getServer().getScheduler().runTask(plugin, () -> {
            rebuild = null;
            if (atlas.dirty()) {
                plugin.getLogger().info("Telas planas: peças novas (" + atlas.size() + " no total), refazendo o resource pack.");
                plugin.rebuildPack();
            }
        });
    }

    @Override
    public String itemTexture(org.bukkit.Material material) {
        return atlas.itemTexture(material.getKey().getKey());
    }

    @Override
    public void setEnergyProvider(Function<Player, int[]> provider) {
        this.energy = provider;
    }

    /** As recargas mostradas na HUD (as Sword Skills equipadas). */
    public void setCooldownProvider(Function<Player, java.util.List<FlatOverlay.Cooldown>> provider) {
        this.cooldowns = provider;
    }

    public java.util.List<FlatOverlay.Cooldown> cooldowns(Player p) {
        if (cooldowns == null) {
            return java.util.List.of();
        }
        try {
            java.util.List<FlatOverlay.Cooldown> out = cooldowns.apply(p);
            return out == null ? java.util.List.of() : out;
        } catch (RuntimeException e) {
            return java.util.List.of();
        }
    }

    /** {atual, máximo} da barra azul: o provedor registrado ou a fome. */
    public int[] energy(Player p) {
        if (energy != null) {
            try {
                int[] e = energy.apply(p);
                if (e != null && e.length >= 2) {
                    return e;
                }
            } catch (RuntimeException ex) {
                // provedor com erro: cai na fome
            }
        }
        return new int[]{p.getFoodLevel(), 20};
    }

    @Override
    public boolean isOpen(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    @Override
    public void open(Player player, FlatScreen screen) {
        close(player);
        plugin.sessions().close(player, false);
        player.closeInventory();
        Session s = new Session(screen);
        s.openedAt = tick;
        Location l = player.getLocation();
        // Olhando muito para cima ou para baixo o cursor não teria para onde ir: nivela a câmera.
        if (Math.abs(l.getPitch()) > 40) {
            level(player, l);
        }
        s.lastYaw = l.getYaw();
        s.lastPitch = Math.abs(l.getPitch()) > 40 ? 0 : l.getPitch();
        sessions.put(player.getUniqueId(), s);
        freeze(player, true);
        plugin.hud().update(player);
        plugin.sounds().play(player, UiSound.OPEN);
        draw(player, s);
    }

    @Override
    public void close(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        freeze(player, false);
        View v = views.get(player.getUniqueId());
        if (v != null) {
            v.screen = null;
            refresh(player, v);
        }
        try {
            s.screen.onClose(player);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao fechar a tela " + s.screen.getClass().getSimpleName(), e);
        }
        if (player.isOnline()) {
            plugin.sounds().play(player, UiSound.CLOSE);
        }
    }

    public void closeAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                close(p);
            } else {
                sessions.remove(id);
            }
        }
    }

    // ------------------------------------------------------------------ camadas

    /** Camada da HUD (null tira). Usada pela HUD em barras. */
    public void hud(Player p, Component hud) {
        View v = views.computeIfAbsent(p.getUniqueId(), k -> new View());
        v.hud = hud;
        refresh(p, v);
    }

    /** O jogador tem alguma camada plana na tela (a HUD antiga se esconde enquanto isso). */
    public boolean showing(Player p) {
        View v = views.get(p.getUniqueId());
        return v != null && v.bar != null;
    }

    private void refresh(Player p, View v) {
        if (v.hud == null && v.screen == null) {
            if (v.bar != null) {
                p.hideBossBar(v.bar);
                v.bar = null;
            }
            return;
        }
        Component name = v.hud == null ? v.screen : v.screen == null ? v.hud : Component.text().append(v.hud).append(v.screen).build();
        if (v.bar == null) {
            v.bar = BossBar.bossBar(name, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            p.showBossBar(v.bar);
        } else {
            v.bar.name(name);
        }
    }

    /** Desenha uma camada sem cursor (a HUD) e devolve a linha pronta. */
    public Component paint(java.util.function.Consumer<FlatDraw> painter) {
        FlatCanvas c = new FlatCanvas(atlas, false, Integer.MIN_VALUE, Integer.MIN_VALUE);
        painter.accept(c);
        noteMissing(c);
        return c.build();
    }

    /** Passada de preparação de uma camada (HUD). */
    public void prepareLayer(java.util.function.Consumer<FlatDraw> painter) {
        painter.accept(new FlatCanvas(atlas, true, 0, 0));
    }

    private void noteMissing(FlatCanvas c) {
        if (c.missing() > 0 && !warnedMissing) {
            warnedMissing = true;
            plugin.getLogger().warning("Telas planas: " + c.missing() + " peça(s) pedida(s) ainda não estão no resource pack "
                    + "(a tela desenhou algo que não desenhou na preparação). O pack será refeito; quem já está online só vê depois de receber o pack novo.");
            scheduleRebuild();
        }
    }

    // ------------------------------------------------------------------ desenho e cursor

    private void draw(Player p, Session s) {
        s.redraw = false;
        int cx = (int) Math.round(s.cx);
        int cy = (int) Math.round(s.cy);
        FlatCanvas c = new FlatCanvas(atlas, false, cx, cy);
        try {
            s.screen.draw(c, p);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao desenhar a tela " + s.screen.getClass().getSimpleName(), e);
            close(p);
            return;
        }
        c.icon(cx, cy, CURSOR_SIZE, CURSOR);
        noteMissing(c);
        s.canvas = c;
        View v = views.computeIfAbsent(p.getUniqueId(), k -> new View());
        v.screen = c.build();
        refresh(p, v);
    }

    private void tick() {
        tick++;
        for (Map.Entry<UUID, Session> e : new ArrayList<>(sessions.entrySet())) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null || p.isDead()) {
                if (p != null) {
                    close(p);
                } else {
                    sessions.remove(e.getKey());
                }
                continue;
            }
            Session s = e.getValue();
            // Sem movimento, redesenha de vez em quando (saldo, estoque e afins mudam sozinhos).
            if (s.redraw || tick % 20 == 0) {
                draw(p, s);
            }
        }
    }

    private void level(Player p, Location l) {
        Location to = l.clone();
        to.setPitch(0);
        recentering = true;
        try {
            p.teleport(to);
        } finally {
            recentering = false;
        }
    }

    private void freeze(Player player, boolean on) {
        for (Attribute a : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = player.getAttribute(a);
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

    /** Diferença de ângulo no caminho mais curto (-180 a 180). */
    public static float angleDelta(float from, float to) {
        float d = (to - from) % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null) {
            return;
        }
        Location to = event.getTo();
        float dyaw = angleDelta(s.lastYaw, to.getYaw());
        float dpitch = to.getPitch() - s.lastPitch;
        s.lastYaw = to.getYaw();
        s.lastPitch = to.getPitch();
        // Salto grande = a câmera foi reposicionada (nivelamento): não é movimento do mouse.
        if (Math.abs(dyaw) > 30 || Math.abs(dpitch) > 30 || (dyaw == 0 && dpitch == 0)) {
            return;
        }
        UiSettings st = plugin.settings();
        double sens = st.flatSensitivity;
        s.cx = clamp(s.cx + dyaw * sens, MIN_X, MAX_X);
        s.cy = clamp(s.cy + dpitch * sens, MIN_Y, MAX_Y);
        s.redraw = true;
        if (Math.abs(to.getPitch()) > 70) {
            Player p = event.getPlayer();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (sessions.get(p.getUniqueId()) == s && p.isOnline()) {
                    level(p, p.getLocation());
                }
            });
        }
    }

    private void click(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || tick - s.openedAt < CLICK_GRACE_TICKS || tick - s.lastClick < 2 || s.canvas == null) {
            return;
        }
        s.lastClick = tick;
        FlatCanvas.Hit hit = s.canvas.hitAt((int) Math.round(s.cx), (int) Math.round(s.cy));
        if (hit == null || hit.action() == null) {
            return;
        }
        plugin.sounds().play(p, UiSound.CONFIRM);
        try {
            hit.action().run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Erro no botão da tela " + s.screen.getClass().getSimpleName(), e);
        }
        if (sessions.get(p.getUniqueId()) == s) {
            s.redraw = true;
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            click(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isOpen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isOpen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && isOpen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isOpen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isOpen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (isOpen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            close(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && plugin.settings().closeOnDamage) {
            close(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!recentering) {
            close(event.getPlayer());
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        close(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer());
        views.remove(event.getPlayer().getUniqueId());
    }
}
