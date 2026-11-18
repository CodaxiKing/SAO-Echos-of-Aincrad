package com.aincrad.economy.house;

import com.aincrad.core.AincradCore;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.economy.EconomyService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O corretor: {@code /casas} lista as casas por andar e deixa visitar por alguns segundos as que
 * estão à venda. A visita leva o jogador até a casa e o traz de volta ao fim do tempo, ou assim
 * que ele sai dela.
 */
public final class HouseRealty implements Listener {

    /** Uma visita em andamento: de onde o jogador veio e quando acaba. */
    private record Visit(Location back, String house, long endsAt, boolean arrived) {
    }

    private final Plugin plugin;
    private final EconomyService eco;
    private final Messages msg;
    private final HouseService houses;
    private final HouseAuctionService auctions;
    private final Map<UUID, Visit> visits = new ConcurrentHashMap<>();

    public HouseRealty(Plugin plugin, EconomyService eco, HouseService houses, HouseAuctionService auctions) {
        this.plugin = plugin;
        this.eco = eco;
        this.msg = eco.messages();
        this.houses = houses;
        this.auctions = auctions;
    }

    // ------------------------------------------------------------------ menus

    private Map<Integer, List<House>> byFloor() {
        Map<Integer, List<House>> out = new TreeMap<>();
        for (House h : houses.all()) {
            out.computeIfAbsent(h.floor(), k -> new ArrayList<>()).add(h);
        }
        return out;
    }

    /** {@code /casas}: os andares que têm casas. Com um andar só, abre direto a lista dele. */
    public void open(Player p) {
        Map<Integer, List<House>> floors = byFloor();
        if (floors.isEmpty()) {
            msg.send(p, "corretor.vazio");
            return;
        }
        if (floors.size() == 1) {
            openFloor(p, floors.keySet().iterator().next(), false);
            return;
        }
        Menu menu = Menu.of(msg.get("corretor.titulo")).rows(Math.min(6, 2 + (floors.size() + 8) / 9));
        floors.forEach((floor, list) -> {
            int free = HouseRules.free(list.stream().map(House::forSale).toList());
            boolean can = canEnter(p, floor);
            ItemStack icon = new ItemStack(!can ? Material.BARRIER : free > 0 ? Material.LIME_BED : Material.RED_BED);
            icon.setAmount(Math.max(1, Math.min(64, floor)));
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("corretor.andar_lore", Messages.vars("livres", free, "total", list.size())));
            lore.add(msg.get(can ? "corretor.andar_abrir" : "corretor.andar_fechado"));
            menu.item(Menu.Button.of(icon, msg.get("corretor.andar", Messages.vars("andar", floor)), pl -> openFloor(pl, floor, true)).lore(lore));
        });
        AincradCore.get().menus().open(p, menu);
    }

    private void openFloor(Player p, int floor, boolean back) {
        List<House> list = new ArrayList<>(byFloor().getOrDefault(floor, List.of()));
        // À venda primeiro, as mais baratas na frente.
        list.sort(Comparator.comparing((House h) -> !h.forSale()).thenComparingLong(House::price).thenComparing(House::name));
        Menu menu = Menu.of(msg.get("corretor.titulo_andar", Messages.vars("andar", floor)));
        int seconds = seconds();
        for (House h : list) {
            HouseRules.Sign kind = HouseRules.sign(h.forSale(), auctions != null && auctions.auctioned(h.name()));
            List<Component> lore = new ArrayList<>();
            Material icon;
            switch (kind) {
                case VENDA -> {
                    icon = Material.LIME_BED;
                    lore.add(msg.get("corretor.venda", eco.col(h.price())));
                }
                case LEILAO -> {
                    icon = Material.PURPLE_BED;
                    HouseAuctionService.Auction a = auctions.of(h.name());
                    lore.add(msg.get("corretor.leilao", eco.col(a == null ? 0 : Math.max(a.bid(), a.minBid()))));
                }
                default -> {
                    icon = Material.RED_BED;
                    lore.add(msg.get("corretor.ocupada", Messages.vars("dono", String.valueOf(h.ownerName()))));
                }
            }
            boolean visit = h.forSale() && seconds > 0;
            if (visit) {
                lore.add(msg.get("corretor.visitar", Messages.vars("segundos", seconds)));
            }
            Menu.Button b = Menu.Button.of(new ItemStack(icon), msg.get("corretor.casa", Messages.vars("casa", h.name())),
                    visit ? pl -> visit(pl, h.name()) : null).lore(lore);
            menu.item(visit ? b.closing() : b);
        }
        if (back) {
            menu.button(Menu.Button.of(new ItemStack(Material.ARROW), msg.get("corretor.voltar"), this::open));
        }
        AincradCore.get().menus().open(p, menu);
    }

    // ------------------------------------------------------------------ visita

    private int seconds() {
        return eco.config().integer("casas.visita.segundos", 30, 0, 600);
    }

    /** O jogador pode ir a esse andar? Sem o AincradFloors, pode. */
    private boolean canEnter(Player p, int floor) {
        Plugin floors = Bukkit.getPluginManager().getPlugin("AincradFloors");
        if (floors == null || !floors.isEnabled()) {
            return true;
        }
        try {
            Class<?> api = Class.forName("com.aincrad.floors.api.AincradFloorsAPI", true, floors.getClass().getClassLoader());
            return Boolean.TRUE.equals(api.getMethod("canEnter", Player.class, int.class).invoke(null, p, floor));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private void visit(Player p, String name) {
        House h = houses.byName(name);
        int seconds = seconds();
        if (h == null || !h.forSale() || seconds <= 0) {
            msg.send(p, "corretor.indisponivel");
            return;
        }
        if (visits.containsKey(p.getUniqueId())) {
            msg.send(p, "corretor.ja_visitando");
            return;
        }
        if (!canEnter(p, h.floor())) {
            msg.send(p, "corretor.andar_bloqueado", Messages.vars("andar", h.floor()));
            return;
        }
        Location to = HouseService.spawnLocation(h.spawn());
        if (to == null) {
            msg.send(p, "casa.sem_spawn");
            return;
        }
        UUID id = p.getUniqueId();
        Visit v = new Visit(p.getLocation(), h.name(), System.currentTimeMillis() + seconds * 1000L, false);
        visits.put(id, v);
        p.teleportAsync(to).thenAccept(ok -> AincradCore.get().runSync(() -> {
            if (!ok) {
                visits.remove(id);
                return;
            }
            visits.computeIfPresent(id, (k, old) -> new Visit(old.back(), old.house(), old.endsAt(), true));
            msg.send(p, "corretor.visitando", Messages.vars("casa", h.name(), "segundos", seconds));
        }));
    }

    private void end(Player p, Visit v) {
        visits.remove(p.getUniqueId());
        p.teleportAsync(v.back());
        msg.send(p, "corretor.fim");
    }

    /** A cada segundo: mostra o tempo que falta e encerra a visita no fim ou quando o jogador sai da casa. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Visit> e : visits.entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            Visit v = e.getValue();
            if (p == null) {
                visits.remove(e.getKey());
                continue;
            }
            if (!v.arrived()) {
                continue;
            }
            House here = houses.at(p.getLocation());
            if (HouseRules.visitOver(now, v.endsAt(), here != null && here.name().equals(v.house()))) {
                end(p, v);
            } else {
                msg.actionBar(p, "corretor.tempo", Messages.vars("segundos", Math.max(1, (v.endsAt() - now + 999) / 1000)));
            }
        }
    }

    /** Quem sai no meio da visita volta para onde estava: não fica dentro da casa. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Visit v = visits.remove(event.getPlayer().getUniqueId());
        if (v != null) {
            event.getPlayer().teleport(v.back());
        }
    }

    public void shutdown() {
        visits.forEach((id, v) -> {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                p.teleport(v.back());
            }
        });
        visits.clear();
    }
}
