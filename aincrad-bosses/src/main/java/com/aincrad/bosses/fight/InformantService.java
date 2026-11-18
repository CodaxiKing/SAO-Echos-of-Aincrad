package com.aincrad.bosses.fight;

import com.aincrad.bosses.api.BossFightStartEvent;
import com.aincrad.bosses.api.BossPhaseChangeEvent;
import com.aincrad.bosses.data.GuideStore;
import com.aincrad.bosses.hook.Hooks;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.GuideData;
import com.aincrad.bosses.model.InformantRules;
import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.floors.api.AincradFloorsAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Os informantes: o guia dos chefes vendido em páginas. Quem estava na luta quando a fase
 * apareceu ganha a página; os outros compram do informante (nas cidades) ou de outro jogador, que
 * copia a página dele num item.
 */
public final class InformantService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS boss_pages (uuid VARCHAR(36) NOT NULL, page VARCHAR(80) NOT NULL, PRIMARY KEY (uuid, page))");

    private final Plugin plugin;
    private final Messages msg;
    private final FightManager fights;
    private final GuideStore guide;
    private final Hooks hooks;
    private final Database db;
    private final NamespacedKey keyPage;
    private final Map<UUID, Set<String>> pages = new ConcurrentHashMap<>();
    private boolean enabled;
    private boolean cityOnly;
    private long base;
    private long perFloor;
    private long perPhase;
    private double copyFraction;

    public InformantService(Plugin plugin, Messages msg, FightManager fights, GuideStore guide, Hooks hooks, Database db) {
        this.plugin = plugin;
        this.msg = msg;
        this.fights = fights;
        this.guide = guide;
        this.hooks = hooks;
        this.db = db;
        keyPage = new NamespacedKey(plugin, "pagina_guia");
    }

    public void reload(ConfigFile c) {
        enabled = c.bool("informantes.ativo", true);
        cityOnly = c.bool("informantes.so_na_cidade", true);
        base = c.integer("informantes.preco_base", 200, 0, 100_000_000);
        perFloor = c.integer("informantes.preco_por_andar", 50, 0, 100_000_000);
        perPhase = c.integer("informantes.preco_por_fase", 100, 0, 100_000_000);
        copyFraction = c.decimal("informantes.taxa_de_copia", 0.25, 0, 1);
    }

    /** Os informantes estão ligados? Desligados, o guia é aberto a todos, como antes. */
    public boolean enabled() {
        return enabled;
    }

    public long price(BossDef def, int phase) {
        // Sem o AincradEconomy não há como cobrar: as páginas saem de graça.
        return hooks.hasEconomy() ? InformantRules.price(base, perFloor, perPhase, def.floor(), phase) : 0;
    }

    // ------------------------------------------------------------------ páginas de cada jogador

    public void load(UUID id) {
        db.query("SELECT page FROM boss_pages WHERE uuid = ?", rs -> rs.getString(1), id.toString())
                .thenAccept(list -> AincradCore.get().runSync(() -> {
                    if (Bukkit.getPlayer(id) != null) {
                        Set<String> set = ConcurrentHashMap.newKeySet();
                        set.addAll(list);
                        // Páginas ganhas enquanto carregava não se perdem.
                        Set<String> before = pages.put(id, set);
                        if (before != null) {
                            set.addAll(before);
                        }
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível carregar as páginas do guia de " + id, ex);
                    return null;
                });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pages.remove(event.getPlayer().getUniqueId());
    }

    public boolean owns(Player p, String boss, int phase) {
        Set<String> s = pages.get(p.getUniqueId());
        return s != null && s.contains(InformantRules.key(boss, phase));
    }

    /** @return true se o jogador ainda não tinha a página */
    public boolean grant(Player p, String boss, int phase) {
        String key = InformantRules.key(boss, phase);
        if (!pages.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(key)) {
            return false;
        }
        String id = p.getUniqueId().toString();
        db.async(c -> {
            if (Database.query(c, "SELECT page FROM boss_pages WHERE uuid = ? AND page = ?", rs -> rs.getString(1), id, key).isEmpty()) {
                Database.update(c, "INSERT INTO boss_pages (uuid, page) VALUES (?, ?)", id, key);
            }
            return null;
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Não foi possível gravar a página " + key + " de " + p.getName(), ex);
            return null;
        });
        return true;
    }

    public InformantRules.View view(Player p, BossDef def, GuideData g, int phase) {
        return InformantRules.view(enabled, g.phaseSeen(phase), owns(p, def.id(), phase));
    }

    // ------------------------------------------------------------------ quem viu ganha a página

    private void witness(String bossId, int phase) {
        BossFight f = fights.fight(bossId);
        if (f == null) {
            return;
        }
        for (UUID id : new ArrayList<>(f.fighters)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && grant(p, bossId, phase) && enabled) {
                msg.send(p, "informante.viu", Messages.vars("chefe", f.def.name(), "n", phase + 1));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStart(BossFightStartEvent event) {
        witness(event.getBossId(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPhase(BossPhaseChangeEvent event) {
        witness(event.getBossId(), event.getPhase());
    }

    // ------------------------------------------------------------------ comprar do informante

    private boolean inCity(Player p) {
        if (!cityOnly) {
            return true;
        }
        try {
            return AincradFloorsAPI.isSafeZone(p.getLocation());
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** {@code /chefe informante}: as páginas que o servidor já descobriu e o jogador ainda não tem. */
    public void shop(Player p) {
        if (!enabled) {
            msg.send(p, "informante.desligado");
            return;
        }
        if (!inCity(p)) {
            msg.send(p, "informante.so_na_cidade");
            return;
        }
        Menu menu = Menu.of(msg.get("informante.menu")).rows(6);
        menu.floating();
        for (BossDef def : fights.registry().all()) {
            GuideData g = guide.get(def.id());
            for (int i = 0; i < def.phases().size(); i++) {
                if (view(p, def, g, i) != InformantRules.View.A_VENDA) {
                    continue;
                }
                int phase = i;
                long price = price(def, phase);
                menu.item(Menu.Button.of(new ItemStack(Material.PAPER),
                        msg.get("informante.item", Messages.vars("chefe", def.name(), "n", phase + 1)),
                        pl -> buy(pl, def, phase)).closing().lore(List.of(
                        msg.get("informante.item_andar", Messages.vars("andar", def.floor(), "level", def.level())),
                        msg.get("informante.item_preco", Messages.vars("preco", price)))));
            }
        }
        if (menu.items().isEmpty()) {
            msg.send(p, "informante.nada");
            return;
        }
        AincradCore.get().menus().open(p, menu);
    }

    public void buy(Player p, BossDef def, int phase) {
        if (!enabled) {
            msg.send(p, "informante.desligado");
            return;
        }
        if (!inCity(p)) {
            msg.send(p, "informante.so_na_cidade");
            return;
        }
        var vars = Messages.vars("chefe", def.name(), "n", phase + 1, "preco", price(def, phase));
        switch (view(p, def, guide.get(def.id()), phase)) {
            case DESCONHECIDA -> msg.send(p, "informante.desconhecida", vars);
            case ABERTA -> msg.send(p, "informante.ja_tem", vars);
            case A_VENDA -> {
                long price = price(def, phase);
                if (price > 0 && !hooks.takeCol(p, price, "guia: " + def.id() + " fase " + (phase + 1))) {
                    msg.send(p, "informante.sem_col", vars);
                    return;
                }
                grant(p, def.id(), phase);
                msg.send(p, "informante.comprou", vars);
            }
        }
    }

    // ------------------------------------------------------------------ vender a outro jogador

    /** {@code /chefe pagina <id> <fase>}: copia uma página que o jogador tem num item, para vender ou dar. */
    public void copy(Player p, BossDef def, int phase) {
        if (!enabled) {
            msg.send(p, "informante.desligado");
            return;
        }
        if (!owns(p, def.id(), phase)) {
            msg.send(p, "informante.nao_tem", Messages.vars("chefe", def.name(), "n", phase + 1));
            return;
        }
        if (!p.getInventory().containsAtLeast(new ItemStack(Material.PAPER), 1)) {
            msg.send(p, "informante.sem_papel");
            return;
        }
        long fee = InformantRules.copyFee(price(def, phase), copyFraction);
        if (fee > 0 && !hooks.takeCol(p, fee, "cópia do guia: " + def.id())) {
            msg.send(p, "informante.sem_col", Messages.vars("chefe", def.name(), "n", phase + 1, "preco", fee));
            return;
        }
        p.getInventory().removeItem(new ItemStack(Material.PAPER, 1));
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("informante.pagina_nome", Messages.vars("chefe", def.name(), "n", phase + 1)));
        meta.lore(List.of(msg.get("informante.pagina_lore", Messages.vars("jogador", p.getName())),
                msg.get("informante.pagina_usar")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyPage, PersistentDataType.STRING, InformantRules.key(def.id(), phase));
        it.setItemMeta(meta);
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        msg.send(p, "informante.copiou", Messages.vars("chefe", def.name(), "n", phase + 1, "preco", fee));
    }

    /** Botão direito com a página na mão: o jogador aprende e o item é gasto. */
    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        ItemStack it = event.getItem();
        if (event.getHand() != EquipmentSlot.HAND || it == null || !it.hasItemMeta()
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        String key = it.getItemMeta().getPersistentDataContainer().get(keyPage, PersistentDataType.STRING);
        if (key == null) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        int cut = key.lastIndexOf(':');
        BossDef def = cut < 0 ? null : fights.registry().get(key.substring(0, cut));
        int phase;
        try {
            phase = Integer.parseInt(key.substring(cut + 1));
        } catch (NumberFormatException e) {
            phase = -1;
        }
        if (def == null || phase < 0 || phase >= def.phases().size()) {
            msg.send(p, "informante.pagina_invalida");
            return;
        }
        if (!grant(p, def.id(), phase)) {
            msg.send(p, "informante.ja_tem", Messages.vars("chefe", def.name(), "n", phase + 1, "preco", 0));
            return;
        }
        it.setAmount(it.getAmount() - 1);
        msg.send(p, "informante.aprendeu", Messages.vars("chefe", def.name(), "n", phase + 1));
    }

    /** As páginas do chefe que o jogador tem (índices das fases). */
    public Set<Integer> owned(Player p, BossDef def) {
        Set<Integer> out = new HashSet<>();
        for (int i = 0; i < def.phases().size(); i++) {
            if (owns(p, def.id(), i)) {
                out.add(i);
            }
        }
        return out;
    }

    /** O texto de uma página que o jogador não pode ler. */
    public List<Component> closedPage(BossDef def, int phase, InformantRules.View view) {
        return msg.list(view == InformantRules.View.A_VENDA ? "guia.fase_a_venda" : "guia.fase_oculta",
                Messages.vars("n", phase + 1, "preco", price(def, phase), "id", def.id()));
    }
}
