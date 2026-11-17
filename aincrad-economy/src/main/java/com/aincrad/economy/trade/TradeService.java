package com.aincrad.economy.trade;

import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.economy.EconomyService;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pedidos de troca e a janela de troca segura: cada um só mexe no seu lado, qualquer mudança
 * derruba as duas confirmações e trava o confirmar por alguns segundos; quando os dois confirmam,
 * itens e Col trocam de dono de uma vez. Fechar, sair ou falhar devolve tudo.
 */
public final class TradeService implements Listener {

    private static final Set<InventoryAction> ALLOWED_TOP = EnumSet.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF, InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME, InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME, InventoryAction.SWAP_WITH_CURSOR);

    private record Request(UUID from, long expires) {
    }

    private final Plugin plugin;
    private final EconomyService eco;
    private final Messages msg;
    /** Pedido pendente: quem recebeu → quem pediu. */
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new HashMap<>();

    public TradeService(Plugin plugin, EconomyService eco) {
        this.plugin = plugin;
        this.eco = eco;
        this.msg = eco.messages();
    }

    public boolean busy(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    /** Valida distância/mundo. */
    public boolean near(Player a, Player b) {
        int max = eco.config().integer("troca.distancia_maxima", 16, 0, 100_000);
        if (!a.getWorld().equals(b.getWorld())) {
            return false;
        }
        return max <= 0 || a.getLocation().distanceSquared(b.getLocation()) <= (double) max * max;
    }

    public void request(Player from, Player to) {
        if (from.equals(to)) {
            msg.send(from, "troca.voce_mesmo");
            return;
        }
        if (busy(to)) {
            msg.send(from, "troca.ocupado", Messages.vars("jogador", to.getName()));
            return;
        }
        if (!near(from, to)) {
            msg.send(from, "troca.longe", Messages.vars("blocos", eco.config().integer("troca.distancia_maxima", 16, 0, 100_000)));
            return;
        }
        long ttl = eco.config().integer("troca.expira_pedido", 60, 5, 3600) * 1000L;
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), System.currentTimeMillis() + ttl));
        msg.send(from, "troca.enviado", Messages.vars("jogador", to.getName()));
        msg.send(to, "troca.recebido", Messages.vars("jogador", from.getName()));
    }

    public void accept(Player to) {
        Request r = requests.remove(to.getUniqueId());
        Player from = r == null ? null : plugin.getServer().getPlayer(r.from());
        if (r == null || from == null) {
            msg.send(to, "troca.sem_pedido");
            return;
        }
        if (r.expires() < System.currentTimeMillis()) {
            msg.send(to, "troca.expirou");
            return;
        }
        if (busy(from) || busy(to)) {
            msg.send(to, "troca.ocupado", Messages.vars("jogador", from.getName()));
            return;
        }
        if (!near(from, to)) {
            msg.send(to, "troca.longe", Messages.vars("blocos", eco.config().integer("troca.distancia_maxima", 16, 0, 100_000)));
            return;
        }
        open(from, to);
    }

    public void deny(Player to) {
        Request r = requests.remove(to.getUniqueId());
        if (r == null) {
            msg.send(to, "troca.sem_pedido");
            return;
        }
        msg.send(to, "troca.recusado");
        Player from = plugin.getServer().getPlayer(r.from());
        if (from != null) {
            msg.send(from, "troca.recusou_voce", Messages.vars("jogador", to.getName()));
        }
    }

    private void open(Player a, Player b) {
        TradeSession s = new TradeSession(a, b, msg.get("troca.titulo", Messages.vars("a", a.getName(), "b", b.getName())));
        sessions.put(a.getUniqueId(), s);
        sessions.put(b.getUniqueId(), s);
        ItemStack sep = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(msg.get("troca.separador")).build();
        for (int row = 0; row < 5; row++) {
            s.getInventory().setItem(row * 9 + 4, sep);
        }
        for (int slot : new int[]{47, 48, 50, 51}) {
            s.getInventory().setItem(slot, sep);
        }
        s.getInventory().setItem(TradeSession.CANCEL, ItemBuilder.of(Material.BARRIER).name(msg.get("troca.cancelar")).build());
        render(s);
        a.openInventory(s.getInventory());
        b.openInventory(s.getInventory());
    }

    private void render(TradeSession s) {
        long step = eco.config().integer("troca.passo_col", 100, 1, Integer.MAX_VALUE);
        for (boolean ofA : new boolean[]{true, false}) {
            Player who = ofA ? s.a() : s.b();
            s.getInventory().setItem(ofA ? TradeSession.A_COL : TradeSession.B_COL,
                    ItemBuilder.of(Material.GOLD_NUGGET).name(msg.get("troca.col_nome", eco.col(s.col(ofA))))
                            .lore(msg.list("troca.col_lore", Messages.vars("passo", eco.format(step)))).build());
            boolean ok = s.confirmed(ofA);
            s.getInventory().setItem(ofA ? TradeSession.A_CONFIRM : TradeSession.B_CONFIRM,
                    ItemBuilder.of(ok ? Material.LIME_WOOL : Material.RED_WOOL)
                            .name(ok ? msg.get("troca.confirmado") : msg.get("troca.confirmar"))
                            .lore(List.of(msg.get("troca.aguardando", Messages.vars("jogador", who.getName())))).build());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof TradeSession s) || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (s.finished()) {
            event.setCancelled(true);
            return;
        }
        boolean isA = s.isA(p);
        int raw = event.getRawSlot();
        InventoryAction action = event.getAction();
        if (raw >= 54) {
            // Inventário do próprio jogador: pode mexer, mas sem mandar coisas para a janela por atalho.
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }
        if (TradeSession.ownArea(isA, raw)) {
            if (!ALLOWED_TOP.contains(action)) {
                event.setCancelled(true);
                return;
            }
            s.changed();
            plugin.getServer().getScheduler().runTask(plugin, () -> render(s));
            return;
        }
        event.setCancelled(true);
        if (raw == TradeSession.CANCEL) {
            cancel(s, true);
            return;
        }
        if (raw == (isA ? TradeSession.A_COL : TradeSession.B_COL)) {
            long step = eco.config().integer("troca.passo_col", 100, 1, Integer.MAX_VALUE);
            if (event.isShiftClick()) {
                step *= 10;
            }
            long v = s.col(isA) + (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT ? -step : step);
            s.col(isA, Math.min(Math.max(0, v), eco.balance(p)));
            render(s);
            return;
        }
        if (raw == (isA ? TradeSession.A_CONFIRM : TradeSession.B_CONFIRM)) {
            long wait = eco.config().integer("troca.espera_confirmar", 2, 0, 60) * 1000L;
            if (!s.confirmed(isA) && s.sinceChange() < wait) {
                msg.send(p, "troca.espere");
                return;
            }
            s.confirm(isA, !s.confirmed(isA));
            render(s);
            if (s.confirmed(true) && s.confirmed(false)) {
                complete(s);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof TradeSession s) || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        boolean isA = s.isA(p);
        for (int raw : event.getRawSlots()) {
            if (raw < 54 && !TradeSession.ownArea(isA, raw)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getRawSlots().stream().anyMatch(r -> r < 54)) {
            s.changed();
            plugin.getServer().getScheduler().runTask(plugin, () -> render(s));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof TradeSession s && !s.finished()) {
            cancel(s, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
        TradeSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null && !s.finished()) {
            cancel(s, true);
        }
    }

    private void complete(TradeSession s) {
        Player a = s.a();
        Player b = s.b();
        long ca = s.col(true);
        long cb = s.col(false);
        if (!eco.has(a, ca) || !eco.has(b, cb)) {
            fail(s);
            return;
        }
        if (ca > 0 && !eco.transfer(a.getUniqueId(), b.getUniqueId(), ca, 0, "troca")) {
            fail(s);
            return;
        }
        if (cb > 0 && !eco.transfer(b.getUniqueId(), a.getUniqueId(), cb, 0, "troca")) {
            if (ca > 0) {
                eco.transfer(b.getUniqueId(), a.getUniqueId(), ca, 0, "troca:estorno");
            }
            fail(s);
            return;
        }
        List<ItemStack> fromA = s.offer(true);
        List<ItemStack> fromB = s.offer(false);
        s.finish();
        s.clearOffers();
        give(b, fromA);
        give(a, fromB);
        end(s);
        for (Player p : new Player[]{a, b}) {
            msg.send(p, "troca.concluida");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        }
    }

    private void fail(TradeSession s) {
        for (Player p : new Player[]{s.a(), s.b()}) {
            msg.send(p, "troca.falhou");
        }
        cancel(s, false);
    }

    /** Devolve a oferta de cada um e fecha para os dois. */
    private void cancel(TradeSession s, boolean notify) {
        if (s.finished()) {
            return;
        }
        List<ItemStack> backA = s.offer(true);
        List<ItemStack> backB = s.offer(false);
        s.finish();
        s.clearOffers();
        give(s.a(), backA);
        give(s.b(), backB);
        end(s);
        if (notify) {
            msg.send(s.a(), "troca.cancelada");
            msg.send(s.b(), "troca.cancelada");
        }
    }

    private void end(TradeSession s) {
        sessions.remove(s.a().getUniqueId());
        sessions.remove(s.b().getUniqueId());
        // Fecha no próximo tick (fechar dentro do evento de clique/fechamento dá problema).
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player p : new Player[]{s.a(), s.b()}) {
                if (p.getOpenInventory().getTopInventory().getHolder(false) == s) {
                    p.closeInventory();
                }
            }
        });
    }

    private static void give(Player p, List<ItemStack> items) {
        for (ItemStack it : items) {
            for (ItemStack rest : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
    }

    /** Ao desligar: cancela todas as trocas (devolve os itens). */
    public void shutdown() {
        for (TradeSession s : List.copyOf(sessions.values())) {
            cancel(s, true);
        }
    }
}
