package com.aincrad.effects.step;

import com.aincrad.effects.engine.EffectParseException;
import com.aincrad.effects.engine.Playback;
import com.aincrad.effects.engine.Spec;
import com.aincrad.effects.image.ImageRegistry;
import com.aincrad.effects.image.SpaceFont;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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
 * tipo: menu — inventário com fundo do pack: titulo, fundo (id de images.yml), linhas (1–6) e itens
 * [{slot, material, nome, lore, modelo, quantidade, comando, fechar}]. Sem o pack, o título fica simples.
 */
public final class MenuStep extends Step {

    /** O título do inventário começa 8 px à direita da borda. */
    private static final int TITLE_OFFSET = 8;

    record MenuItem(int slot, Material material, String name, List<String> lore, String model,
                    int amount, String command, boolean close) {
    }

    /** Marca os menus de efeito para cancelar cliques e rodar as ações. */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, MenuItem> items = new HashMap<>();
        private final Map<String, String> vars;

        Holder(Map<String, String> vars) {
            this.vars = vars;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        /** Executa a ação do slot. @return true se o menu deve fechar */
        public boolean click(Player p, int slot) {
            MenuItem it = items.get(slot);
            if (it == null) {
                return false;
            }
            if (it.command() != null && !it.command().isBlank()) {
                String cmd = com.aincrad.effects.engine.TextRenderer.substitute(it.command(), vars)
                        .replace("{espectador}", p.getName());
                p.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
            return it.close();
        }
    }

    private final String title;
    private final String background;
    private final int rows;
    private final List<MenuItem> items = new ArrayList<>();

    public MenuStep(Spec s, ImageRegistry images) {
        super(s);
        title = s.str("titulo", "");
        background = s.str("fundo", null);
        if (background != null && images.get(background) == null) {
            throw new EffectParseException(s.where() + ": fundo \"" + background + "\" não existe em images.yml");
        }
        rows = Math.max(1, Math.min(6, s.integer("linhas", 3)));
        int i = 0;
        for (Map<?, ?> raw : s.maps("itens")) {
            Spec it = Spec.of(raw, s.where() + " item " + (++i));
            int slot = it.integer("slot", -1);
            if (slot < 0 || slot >= rows * 9) {
                throw new EffectParseException(it.where() + ": slot fora do menu (0 a " + (rows * 9 - 1) + ")");
            }
            Material m = Material.matchMaterial(it.str("material", "PAPER"));
            if (m == null || !m.isItem()) {
                throw new EffectParseException(it.where() + ": material inválido");
            }
            items.add(new MenuItem(slot, m, it.str("nome", null), it.strings("lore"), it.str("modelo", null),
                    Math.max(1, Math.min(64, it.integer("quantidade", 1))), it.str("comando", null), it.bool("fechar", false)));
        }
    }

    @Override
    public void run(Playback pb) {
        ImageRegistry images = pb.plugin().images();
        for (Player p : pb.viewers()) {
            Holder holder = new Holder(pb.vars());
            Component t = pb.text(title, p);
            ImageRegistry.Image bg = background == null ? null : images.get(background);
            if (bg != null && bg.present() && pb.hasPack(p)) {
                t = Component.empty()
                        .append(SpaceFont.component(-TITLE_OFFSET))
                        .append(bg.glyph())
                        .append(SpaceFont.component(-bg.advance() + TITLE_OFFSET))
                        .append(t);
            }
            Inventory inv = Bukkit.createInventory(holder, rows * 9, t);
            holder.inventory = inv;
            for (MenuItem it : items) {
                ItemStack stack = new ItemStack(it.material(), it.amount());
                ItemMeta meta = stack.getItemMeta();
                if (it.name() != null) {
                    meta.displayName(noItalic(pb.text(it.name(), p)));
                }
                if (!it.lore().isEmpty()) {
                    meta.lore(it.lore().stream().map(l -> noItalic(pb.text(l, p))).toList());
                }
                if (it.model() != null && pb.hasPack(p)) {
                    NamespacedKey key = NamespacedKey.fromString(it.model());
                    if (key != null) {
                        meta.setItemModel(key);
                    }
                }
                meta.addItemFlags(ItemFlag.values());
                stack.setItemMeta(meta);
                inv.setItem(it.slot(), stack);
                holder.items.put(it.slot(), it);
            }
            p.openInventory(inv);
        }
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
