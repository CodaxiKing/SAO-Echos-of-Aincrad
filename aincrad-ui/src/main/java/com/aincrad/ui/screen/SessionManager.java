package com.aincrad.ui.screen;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.UiSettings.DrawMode;
import com.aincrad.ui.sound.UiSound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Guarda os menus abertos e liga cliques, cursor e "voltar" às telas. */
public final class SessionManager {

    /** Cliques logo depois de abrir são ignorados (o próprio clique do gesto). */
    private static final long CLICK_GRACE_TICKS = 5;

    private final AincradUIPlugin plugin;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();
    private final NamespacedKey freezeKey;
    private BukkitTask task;
    private long tick;

    public SessionManager(AincradUIPlugin plugin) {
        this.plugin = plugin;
        this.freezeKey = new NamespacedKey(plugin, "menu_aberto");
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
    }

    public MenuSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean isOpen(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void openMain(Player player) {
        open(player, plugin.screens().main(player, null));
    }

    /** Abre a tela; se o menu já estiver aberto, empilha por cima. */
    public void open(Player player, Screen screen) {
        MenuSession existing = get(player);
        if (existing != null) {
            existing.push(screen);
            return;
        }
        DrawMode mode = plugin.prefs().mode(player);
        if (mode == DrawMode.CURSOR && !plugin.packStatus().hasPack(player)) {
            mode = DrawMode.BAU;
        }
        Renderer renderer = mode == DrawMode.CURSOR
                ? new CursorRenderer(plugin, player)
                : new ChestRenderer(plugin, player);
        MenuSession session = new MenuSession(player, mode, renderer, tick);
        sessions.put(player.getUniqueId(), session);
        if (mode == DrawMode.CURSOR) {
            freeze(player);
        }
        session.push(screen);
        plugin.sounds().play(player, UiSound.OPEN);
        if (mode == DrawMode.CURSOR) {
            player.sendActionBar(plugin.messages().get("menu.dica_cursor"));
        }
    }

    /**
     * No modo cursor o menu fica parado no mundo: o jogador não anda nem pula, só gira a câmera.
     * Modificadores transitórios não são salvos, então nada fica preso se o servidor cair.
     */
    private void freeze(Player player) {
        addModifier(player, Attribute.MOVEMENT_SPEED);
        addModifier(player, Attribute.JUMP_STRENGTH);
    }

    private void unfreeze(Player player) {
        for (Attribute a : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = player.getAttribute(a);
            if (inst != null) {
                inst.removeModifier(freezeKey);
            }
        }
    }

    private void addModifier(Player player, Attribute attribute) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null || inst.getModifier(freezeKey) != null) {
            return;
        }
        inst.addTransientModifier(new AttributeModifier(freezeKey, -1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    /** Volta uma tela; na primeira, fecha o menu. */
    public void back(Player player) {
        MenuSession s = get(player);
        if (s == null) {
            return;
        }
        if (s.pop()) {
            plugin.sounds().play(player, UiSound.BACK);
        } else {
            close(player, true);
        }
    }

    public void close(Player player, boolean sound) {
        MenuSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        s.markClosed();
        s.renderer().destroy();
        unfreeze(player);
        if (sound && player.isOnline()) {
            plugin.sounds().play(player, UiSound.CLOSE);
        }
    }

    public void closeAll() {
        for (MenuSession s : new ArrayList<>(sessions.values())) {
            close(s.player(), false);
        }
    }

    /** Clique no modo cursor: escolhe o ícone sob o cursor. */
    public void click(Player player) {
        MenuSession s = get(player);
        if (s == null || s.mode() != DrawMode.CURSOR || tick - s.openedAt() < CLICK_GRACE_TICKS) {
            return;
        }
        ScreenElement e = s.renderer().hovered();
        if (e != null) {
            activate(s, e);
        }
    }

    /** Clique num slot do modo baú. */
    public void clickSlot(Player player, int slot) {
        MenuSession s = get(player);
        if (s == null) {
            return;
        }
        ScreenElement e = s.renderer().atSlot(slot);
        if (e != null) {
            activate(s, e);
        }
    }

    private void activate(MenuSession s, ScreenElement e) {
        Player p = s.player();
        if (!e.interactive()) {
            return;
        }
        if (e.locked()) {
            plugin.sounds().play(p, UiSound.BACK);
            plugin.messages().send(p, "menu.em_breve", Placeholder.component("nome", e.label()));
            return;
        }
        plugin.sounds().play(p, UiSound.CONFIRM);
        if (e.action() == null) {
            return;
        }
        try {
            e.action().run(p, s);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Erro na ação do elemento " + e.id(), ex);
        }
    }

    private void tick() {
        tick++;
        for (MenuSession s : new ArrayList<>(sessions.values())) {
            if (!s.player().isOnline() || s.player().isDead()) {
                close(s.player(), false);
                continue;
            }
            s.renderer().tick();
        }
    }
}
