package com.aincrad.life.station;

import com.aincrad.core.AincradCore;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Cuida dos painéis de estação abertos: liga os cliques nas entidades aos encaixes e às marcas,
 * conta o tempo das batidas e fecha o painel de quem sai ou se afasta. Um painel por jogador.
 */
public final class PanelManager implements Listener {

    enum Kind {
        SLOT, MARK
    }

    private record Bound(StationPanel panel, Kind kind, int index) {
    }

    /** Distância (blocos) da bancada em que o painel fecha sozinho. */
    private static final double MAX_DISTANCE = 8;

    private final Plugin plugin;
    private final Messages msg;
    private final Map<UUID, Bound> entities = new HashMap<>();
    private final Map<UUID, StationPanel> panels = new HashMap<>();
    private long tick;

    public PanelManager(Plugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
    }

    long now() {
        return tick;
    }

    void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }

    /**
     * Abre um painel para o jogador sobre uma bancada (fecha o que ele tinha aberto).
     *
     * @param base     centro da base da bancada
     * @param yaw      para onde a bancada está virada
     * @param hologram ponto de ancoragem do holograma
     */
    public StationPanel open(Player owner, Location base, float yaw, double[] hologram) {
        close(owner, true);
        StationPanel panel = new StationPanel(this, owner, base, yaw, hologram, AincradCore.get().isBedrock(owner));
        panels.put(owner.getUniqueId(), panel);
        return panel;
    }

    public StationPanel panel(Player p) {
        return panels.get(p.getUniqueId());
    }

    public void close(Player p, boolean returnItems) {
        StationPanel panel = panels.get(p.getUniqueId());
        if (panel != null) {
            panel.close(returnItems);
        }
    }

    public void closeAll() {
        for (StationPanel p : new ArrayList<>(panels.values())) {
            p.close(true);
        }
    }

    void bind(Interaction e, StationPanel panel, Kind kind, int index) {
        entities.put(e.getUniqueId(), new Bound(panel, kind, index));
    }

    void unbind(StationPanel panel) {
        entities.values().removeIf(b -> b.panel() == panel);
        panels.remove(panel.owner().getUniqueId(), panel);
    }

    /** Roda a cada tick. */
    public void tick() {
        tick++;
        for (StationPanel panel : new ArrayList<>(panels.values())) {
            Player p = panel.owner();
            Location base = panel.base();
            if (!p.isOnline() || p.isDead() || !p.getWorld().equals(base.getWorld())
                    || p.getLocation().distanceSquared(base) > MAX_DISTANCE * MAX_DISTANCE) {
                panel.close(true);
                if (p.isOnline()) {
                    msg.send(p, "painel.fechou_longe");
                }
                continue;
            }
            panel.tick();
        }
    }

    private boolean click(Player p, Entity e) {
        Bound b = entities.get(e.getUniqueId());
        if (b == null) {
            return false;
        }
        // Só o dono mexe no painel; os outros só assistem.
        if (b.panel().owner().equals(p)) {
            if (b.kind() == Kind.SLOT) {
                b.panel().clickSlot(b.index());
            } else {
                b.panel().clickMark(b.index());
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && click(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClick(PrePlayerAttackEntityEvent event) {
        if (click(event.getPlayer(), event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    /** Modo simples (Bedrock): qualquer batida no ar conta, só pelo tempo. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        StationPanel panel = panels.get(event.getPlayer().getUniqueId());
        if (panel != null && panel.simple() && event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            panel.clickMark(-1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer(), true);
    }

    /**
     * Os encaixes como menu (formulário no Bedrock): escolher um encaixe põe nele o item da mão,
     * ou devolve o que está lá. {@code after} roda quando o menu é fechado pelo botão.
     */
    public void openSlotMenu(Player p, Component title, Consumer<Player> after) {
        StationPanel panel = panels.get(p.getUniqueId());
        if (panel == null) {
            return;
        }
        Menu menu = Menu.of(title).rows(3);
        List<StationPanel.Slot> slots = panel.slots();
        for (int i = 0; i < slots.size(); i++) {
            int index = i;
            StationPanel.Slot s = slots.get(i);
            ItemStack icon = s.item() != null ? s.item().clone() : new ItemStack(s.required() ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
            menu.item(Menu.Button.of(icon, msg.get(s.item() == null ? "painel.encaixe_vazio" : "painel.encaixe_cheio",
                    Messages.vars("encaixe", s.label())), pl -> {
                StationPanel now = panels.get(pl.getUniqueId());
                if (now == panel && !panel.closed()) {
                    panel.clickSlot(index);
                    openSlotMenu(pl, title, after);
                }
            }).lore(List.of(msg.get(s.required() ? "painel.obrigatorio" : "painel.opcional"))));
        }
        menu.button(Menu.Button.of(new ItemStack(Material.LIME_DYE), msg.get("painel.pronto"), after).closing());
        AincradCore.get().menus().open(p, menu);
    }
}
