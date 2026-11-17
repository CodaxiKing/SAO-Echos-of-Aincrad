package com.aincrad.floors;

import com.aincrad.core.AincradCore;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.floors.data.FloorStore;
import com.aincrad.floors.model.Floor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Teleport Gates (blocos marcados pelo admin, guardados no banco) e os menus de andares: o do
 * Gate e o do /andares são o mesmo menu, mudando só se o clique teleporta.
 */
public final class GateService implements Listener {

    private final FloorService floors;
    private final Messages msg;

    public GateService(FloorService floors) {
        this.floors = floors;
        this.msg = floors.messages();
    }

    /** Chave do bloco na rede: servidor + mundo + coordenadas. */
    public String ref(Block b) {
        return floors.server() + ":" + b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public void create(Block b, int floor) {
        floors.store().setGate(ref(b), floor).thenRun(this::announce);
    }

    public boolean remove(Block b) {
        String ref = ref(b);
        if (floors.store().gate(ref) == null) {
            return false;
        }
        floors.store().removeGate(ref).thenRun(this::announce);
        return true;
    }

    private void announce() {
        AincradCore core = AincradCore.get();
        core.runSync(() -> core.publish(FloorService.BUS_STATE, "gates"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (floors.store().gate(ref(event.getClickedBlock())) == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().hasPermission("aincrad.floors.tp")) {
            open(event.getPlayer(), true);
        }
    }

    /** O bloco do Gate só quebra pelo comando de remover. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (floors.store().gate(ref(event.getBlock())) != null) {
            event.setCancelled(true);
        }
    }

    /** Abre a lista de andares. {@code teleport}: clicar num andar disponível leva até ele. */
    public void open(Player p, boolean teleport) {
        Menu menu = Menu.of(teleport ? msg.get("gate.titulo")
                : msg.get("andares.titulo", Messages.vars("linha", floors.frontline()))).rows(6);
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy");
        int here = floors.currentFloor(p);
        for (Floor f : floors.registry().all()) {
            int n = f.number();
            boolean unlocked = floors.isUnlocked(n);
            boolean enter = floors.canEnter(p, n);
            boolean offered = floors.gateOffers(p, n);
            List<Component> lore = new ArrayList<>();
            FloorStore.Unlock u = floors.store().unlock(n);
            if (!unlocked) {
                lore.add(msg.get("andares.lore_bloqueado"));
            } else {
                lore.add(u == null ? msg.get("andares.lore_sempre")
                        : msg.get("andares.lore_liberado", Messages.vars("data", date.format(new Date(u.at())))));
                if (u != null && !u.participants().isEmpty()) {
                    lore.add(msg.get("andares.lore_por", Messages.vars("jogadores", u.participants())));
                }
                if (!enter) {
                    lore.add(msg.get("andares.lore_sem_acesso"));
                } else if (!offered) {
                    lore.add(msg.get("andares.lore_nao_visitado"));
                }
            }
            boolean go = teleport && offered && n != here;
            if (n == here) {
                lore.add(msg.get("andares.lore_aqui"));
            } else if (go) {
                lore.add(msg.get("andares.lore_ir"));
            }
            Material icon = !unlocked ? Material.IRON_BARS : n == here ? Material.LODESTONE : offered ? Material.ENDER_EYE : Material.ENDER_PEARL;
            Component name = msg.get(unlocked ? "andares.liberado" : "andares.bloqueado", Messages.vars("andar", f.name()));
            Menu.Button b = Menu.Button.of(ItemBuilder.of(icon).amount(Math.min(64, n)).build(), name,
                    go ? pl -> floors.teleport(pl, n) : null).lore(lore);
            menu.item(go ? b.closing() : b);
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("andares.fechar"), pl -> { }).closing());
        AincradCore.get().menus().open(p, menu);
    }
}
