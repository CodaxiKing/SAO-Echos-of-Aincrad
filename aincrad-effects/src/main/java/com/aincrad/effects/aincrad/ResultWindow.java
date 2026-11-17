package com.aincrad.effects.aincrad;

import com.aincrad.effects.AincradEffectsPlugin;
import com.aincrad.effects.engine.TextRenderer;
import com.aincrad.effects.image.ImageRegistry;
import com.aincrad.effects.image.SpaceFont;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Janela de resultado da luta: XP, Col, dano, itens recebidos e o Last Attack em destaque. É só
 * uma vitrine (os itens já foram entregues pelo AincradBosses): nada sai dela.
 */
public final class ResultWindow implements Listener {

    /** O resultado de um jogador. */
    public record Result(String boss, int floor, boolean firstKill, long xp, long col, long damage, long percent,
                         boolean rewarded, List<ItemStack> items, ItemStack lastAttackItem, String lastAttacker) {
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** O título do inventário começa 8 px à direita da borda. */
    private static final int TITLE_OFFSET = 8;
    private static final int CLOSE_SLOT = 49;
    /** Onde os itens comuns aparecem: duas fileiras centrais. */
    private static final int[] ITEM_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int LAST_ATTACK_SLOT = 40;

    private final AincradEffectsPlugin plugin;
    private final AincradIntegration fx;

    ResultWindow(AincradEffectsPlugin plugin, AincradIntegration fx) {
        this.plugin = plugin;
        this.fx = fx;
    }

    private Component text(String key, Map<String, String> vars, Player viewer) {
        TextRenderer r = new TextRenderer(plugin.images(), plugin.packStatus()::hasPack);
        return r.render(fx.config().getString("janela_resultado." + key, ""), vars, viewer)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private List<Component> lines(String key, Map<String, String> vars, Player viewer) {
        TextRenderer r = new TextRenderer(plugin.images(), plugin.packStatus()::hasPack);
        List<Component> out = new ArrayList<>();
        for (String raw : fx.config().getStringList("janela_resultado." + key)) {
            out.add(r.render(raw, vars, viewer).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }

    private static ItemStack icon(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p, Result r) {
        Map<String, String> vars = new HashMap<>();
        vars.put("chefe", r.boss());
        vars.put("andar", String.valueOf(r.floor()));
        vars.put("xp", String.valueOf(r.xp()));
        vars.put("col", String.valueOf(r.col()));
        vars.put("dano", String.valueOf(r.damage()));
        vars.put("pct", String.valueOf(r.percent()));
        vars.put("jogador", r.lastAttacker());

        Component title = text("titulo", vars, p);
        ImageRegistry.Image bg = plugin.images().get(fx.config().getString("janela_resultado.fundo", "janela_resultado"));
        if (bg != null && bg.present() && plugin.packStatus().hasPack(p)) {
            title = Component.empty().append(SpaceFont.component(-TITLE_OFFSET)).append(bg.glyph())
                    .append(SpaceFont.component(-bg.advance() + TITLE_OFFSET)).append(title);
        }
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inv;

        List<Component> bossLore = lines("chefe_lore", vars, p);
        if (r.firstKill()) {
            bossLore.add(text("primeira_vitoria", vars, p));
        }
        inv.setItem(4, icon(Material.NETHER_STAR, text("chefe", vars, p), bossLore));
        if (r.xp() > 0) {
            inv.setItem(2, icon(Material.EXPERIENCE_BOTTLE, text("xp", vars, p), List.of()));
        }
        if (r.col() > 0) {
            inv.setItem(6, icon(Material.GOLD_NUGGET, text("col", vars, p), List.of()));
        }
        inv.setItem(13, icon(Material.IRON_SWORD, text("dano", vars, p),
                r.rewarded() ? List.of() : List.of(text("sem_recompensa", vars, p))));
        for (int i = 0; i < r.items().size() && i < ITEM_SLOTS.length; i++) {
            inv.setItem(ITEM_SLOTS[i], r.items().get(i).clone());
        }
        if (r.lastAttackItem() != null) {
            ItemStack star = r.lastAttackItem().clone();
            ItemMeta meta = star.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(text("last_attack", vars, p));
            lore.addAll(lines("last_attack_lore", vars, p));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            star.setItemMeta(meta);
            inv.setItem(LAST_ATTACK_SLOT, star);
        } else {
            inv.setItem(LAST_ATTACK_SLOT, icon(Material.NAME_TAG, text("last_attack_outro", vars, p), List.of()));
        }
        inv.setItem(CLOSE_SLOT, icon(Material.BARRIER, text("fechar", vars, p), List.of()));
        p.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() == CLOSE_SLOT && event.getWhoClicked() instanceof Player p) {
            plugin.getServer().getScheduler().runTask(plugin, () -> p.closeInventory());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
