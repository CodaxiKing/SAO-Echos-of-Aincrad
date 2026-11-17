package com.aincrad.ui.screen;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.api.MenuAction;
import com.aincrad.ui.config.Messages;
import com.aincrad.ui.config.UiSettings.DrawMode;
import com.aincrad.ui.menu.MenuRegistry;
import com.aincrad.ui.menu.MenuRegistry.Entry;
import com.aincrad.ui.menu.MenuRegistry.Group;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monta as telas. Cada tela descreve as posições do modo cursor (blocos no plano) e os slots do
 * modo baú; o renderizador escolhido usa só o que precisa.
 */
public final class Screens {

    public static final List<String> NAMES = List.of("menu", "perfil", "detalhes", "confirmar", "opcoes", "ajuda", "icones");

    private static final String WINDOW = "window";
    private static final String WINDOW_CHEST = "window_bau";

    private final AincradUIPlugin plugin;

    public Screens(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private double sc() {
        return plugin.settings().menuScale;
    }

    /** Tela por nome, para /aincrad ui teste e a API openScreen. Null se não existir. */
    public Screen byName(Player p, String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "menu" -> main(p, null);
            case "perfil" -> profile(p);
            case "detalhes" -> itemDetails(p, p.getInventory().getItemInMainHand());
            case "opcoes" -> options(p);
            case "ajuda" -> help(p);
            case "icones" -> iconTest();
            case "confirmar" -> confirm(msg().get("admin.teste_pergunta"),
                    (pl, s) -> {
                        msg().send(pl, "admin.teste_sim");
                        plugin.sessions().back(pl);
                    },
                    (pl, s) -> {
                        msg().send(pl, "admin.teste_nao");
                        plugin.sessions().back(pl);
                    });
            default -> null;
        };
    }

    // ------------------------------------------------------------------ menu principal

    /** Coluna de grupos; com {@code selected}, o submenu do grupo aparece ao lado. */
    public Screen main(Player p, String selected) {
        MenuRegistry menu = plugin.menu();
        List<Group> groups = menu.groups();
        double sc = sc();
        double spacing = 0.42 * sc;
        double size = 0.36 * sc;
        double gx = -0.3 * sc;
        double top = (groups.size() - 1) / 2.0 * spacing;

        Screen s = new Screen("menu").title(msg().get("menu.titulo")).rows(6);
        double selY = 0;
        int selRow = 0;
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            double y = top - i * spacing;
            boolean isSel = g.id().equals(selected);
            int row = Math.min(i, 5);
            if (isSel) {
                selY = y;
                selRow = row;
            }
            String gid = g.id();
            s.add(new ScreenElement("g:" + gid)
                    .glyph(g.icon(), onOf(g.icon()))
                    .label(g.name())
                    .at(gx, y, size)
                    .slot(row * 9 + 1)
                    .fallback(g.material())
                    .selected(isSel)
                    .action((pl, ss) -> {
                        if (gid.equals(selected)) {
                            ss.replace(main(pl, null));
                        } else if (selected != null) {
                            ss.replace(main(pl, gid));
                        } else {
                            ss.push(main(pl, gid));
                        }
                    }));
        }

        if (selected != null) {
            List<Entry> entries = menu.entriesOf(selected);
            double esp = 0.36 * sc;
            double esize = 0.30 * sc;
            double ex = 0.18 * sc;
            double etop = selY + (entries.size() - 1) / 2.0 * esp;
            for (int j = 0; j < entries.size(); j++) {
                Entry e = entries.get(j);
                String eid = e.id();
                s.add(new ScreenElement("e:" + eid)
                        .glyph(e.icon(), onOf(e.icon()))
                        .label(e.name())
                        .at(ex, etop - j * esp, esize)
                        .slot(submenuSlot(selRow, j))
                        .fallback(e.material())
                        .locked(!menu.available(e))
                        .action((pl, ss) -> runEntry(pl, ss, eid)));
            }
        }
        return s;
    }

    /** Submenu no baú: da coluna 3 em diante, na linha do grupo, quebrando para a linha de baixo. */
    static int submenuSlot(int row, int index) {
        int r = Math.min(5, row + index / 6);
        int col = 3 + index % 6;
        return r * 9 + col;
    }

    private String onOf(String glyph) {
        String on = glyph + "_on";
        return plugin.glyphs().get(on) != null ? on : null;
    }

    private void runEntry(Player p, MenuSession s, String id) {
        MenuRegistry menu = plugin.menu();
        MenuAction api = menu.apiAction(id);
        if (api != null) {
            plugin.sessions().close(p, false);
            api.run(p);
            return;
        }
        Entry e = menu.resolve(id);
        if (e == null || e.action() == null) {
            return;
        }
        String action = e.action().trim();
        String lower = action.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "itens" -> {
                plugin.sessions().close(p, false);
                p.openInventory(p.getInventory());
            }
            case "perfil" -> s.push(profile(p));
            case "opcoes" -> s.push(options(p));
            case "ajuda" -> s.push(help(p));
            case "logout" -> logout(p, s);
            default -> {
                if (lower.startsWith("comando:")) {
                    String cmd = action.substring("comando:".length()).trim();
                    if (cmd.startsWith("/")) {
                        cmd = cmd.substring(1);
                    }
                    plugin.sessions().close(p, false);
                    p.performCommand(cmd);
                }
            }
        }
    }

    private void logout(Player p, MenuSession s) {
        if (plugin.settings().logoutDeathGame) {
            msg().send(p, "logout.death_game");
            return;
        }
        s.push(confirm(msg().get("logout.pergunta"),
                (pl, ss) -> {
                    plugin.sessions().close(pl, false);
                    pl.kick(msg().get("logout.kick"));
                },
                (pl, ss) -> plugin.sessions().back(pl)));
    }

    // ------------------------------------------------------------------ janelas

    private Screen window(String id, Component title) {
        return new Screen(id)
                .title(title)
                .background(WINDOW, 0, 0, 1.5 * sc())
                .chestBackground(WINDOW_CHEST)
                .rows(6);
    }

    private ScreenElement backButton(double x, double y, int slot) {
        return new ScreenElement("voltar")
                .glyph("buttons/cancel", onOf("buttons/cancel"))
                .label(msg().get("perfil.botao_voltar"))
                .at(x * sc(), y * sc(), 0.2 * sc())
                .slot(slot)
                .fallback(Material.ARROW)
                .action((pl, ss) -> plugin.sessions().back(pl));
    }

    public Screen profile(Player p) {
        double sc = sc();
        PlayerInventory inv = p.getInventory();
        AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        TagResolver tags = TagResolver.resolver(
                Placeholder.unparsed("jogador", p.getName()),
                Placeholder.unparsed("level", String.valueOf(p.getLevel())),
                Placeholder.unparsed("vida", String.valueOf((int) Math.ceil(p.getHealth()))),
                Placeholder.unparsed("vida_max", String.valueOf((int) Math.round(max))),
                Placeholder.component("cabeca", itemName(inv.getHelmet())),
                Placeholder.component("peito", itemName(inv.getChestplate())),
                Placeholder.component("pernas", itemName(inv.getLeggings())),
                Placeholder.component("pes", itemName(inv.getBoots())),
                Placeholder.component("mao", itemName(inv.getItemInMainHand())));

        Screen s = window("perfil", msg().get("perfil.titulo"))
                .text(msg().list("perfil.linhas", tags), 0.22 * sc, -0.38 * sc, 0.42 * sc)
                .info(13, Material.PLAYER_HEAD);

        s.add(new ScreenElement("pessoa").glyph("person", null).decorative().onlyInCursor()
                .at(-0.42 * sc, 0.12 * sc, 0.8 * sc));

        Map<EquipmentSlot, Integer> slots = Map.of(
                EquipmentSlot.HEAD, 20, EquipmentSlot.CHEST, 29, EquipmentSlot.LEGS, 38,
                EquipmentSlot.FEET, 47, EquipmentSlot.HAND, 31, EquipmentSlot.OFF_HAND, 33);
        for (Map.Entry<EquipmentSlot, Integer> en : slots.entrySet()) {
            ItemStack piece = inv.getItem(en.getKey());
            boolean empty = piece == null || piece.getType().isAir();
            ScreenElement el = new ScreenElement("equip:" + en.getKey().name().toLowerCase(Locale.ROOT))
                    .onlyInChest()
                    .slot(en.getValue())
                    .label(msg().get("perfil.vazio"))
                    .fallback(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            if (!empty) {
                ItemStack copy = piece.clone();
                el.item(copy).action((pl, ss) -> ss.push(itemDetails(pl, copy)));
            } else {
                el.decorative();
            }
            s.add(el);
        }

        s.add(new ScreenElement("detalhes")
                .glyph("buttons/details", onOf("buttons/details"))
                .label(msg().get("perfil.botao_detalhes"))
                .at(0.05 * sc, -0.6 * sc, 0.2 * sc)
                .slot(40)
                .fallback(Material.SPYGLASS)
                .action((pl, ss) -> ss.push(itemDetails(pl, pl.getInventory().getItemInMainHand()))));
        s.add(backButton(0.4, -0.6, 44));
        return s;
    }

    public Screen itemDetails(Player p, ItemStack item) {
        double sc = sc();
        List<Component> lines = new ArrayList<>();
        Screen s = window("detalhes", msg().get("detalhes.titulo")).info(22, Material.BOOK);
        if (item == null || item.getType().isAir()) {
            lines.add(msg().get("detalhes.nada_na_mao"));
        } else {
            lines.add(itemName(item));
            lines.add(msg().get("detalhes.quantidade", Placeholder.unparsed("qtd", String.valueOf(item.getAmount()))));
            ItemMeta meta = item.getItemMeta();
            int maxDur = item.getType().getMaxDurability();
            if (maxDur > 0 && meta instanceof Damageable d) {
                lines.add(msg().get("detalhes.durabilidade",
                        Placeholder.unparsed("atual", String.valueOf(maxDur - d.getDamage())),
                        Placeholder.unparsed("max", String.valueOf(maxDur))));
            }
            for (Map.Entry<Enchantment, Integer> en : item.getEnchantments().entrySet()) {
                lines.add(en.getKey().displayName(en.getValue()));
            }
            if (meta != null && meta.hasLore() && meta.lore() != null) {
                lines.addAll(meta.lore());
            }
            s.add(new ScreenElement("item").onlyInChest().decorative().slot(13).item(item.clone()));
        }
        s.text(lines, 0, -0.35 * sc, 0.5 * sc);
        s.add(backButton(0.45, -0.6, 44));
        return s;
    }

    public Screen confirm(Component question, ElementAction yes, ElementAction no) {
        double sc = sc();
        Screen s = window("confirmar", msg().get("confirmar.titulo"))
                .text(List.of(question), 0, 0.05 * sc, 0.6 * sc)
                .info(13, Material.PAPER);
        s.add(new ScreenElement("sim")
                .glyph("buttons/yes", onOf("buttons/yes"))
                .label(msg().get("confirmar.sim"))
                .at(-0.3 * sc, -0.35 * sc, 0.28 * sc)
                .slot(29)
                .fallback(Material.LIME_WOOL)
                .action(yes));
        s.add(new ScreenElement("nao")
                .glyph("buttons/no", onOf("buttons/no"))
                .label(msg().get("confirmar.nao"))
                .at(0.3 * sc, -0.35 * sc, 0.28 * sc)
                .slot(33)
                .fallback(Material.RED_WOOL)
                .action(no));
        return s;
    }

    public Screen options(Player p) {
        double sc = sc();
        DrawMode mode = plugin.prefs().mode(p);
        boolean hud = plugin.prefs().hud(p);
        TagResolver tags = TagResolver.resolver(
                Placeholder.component("modo", msg().get(mode == DrawMode.CURSOR ? "opcoes.modo_cursor" : "opcoes.modo_bau")),
                Placeholder.component("hud", msg().get(hud ? "opcoes.ligado" : "opcoes.desligado")));
        Screen s = window("opcoes", msg().get("opcoes.titulo"))
                .text(msg().list("opcoes.linhas", tags), 0, 0.0, 0.5 * sc)
                .info(13, Material.REPEATER);
        s.add(new ScreenElement("modo")
                .glyph("buttons/list", onOf("buttons/list"))
                .label(msg().get("opcoes.botao_modo"))
                .at(-0.35 * sc, -0.4 * sc, 0.24 * sc)
                .slot(29)
                .fallback(Material.COMPASS)
                .action((pl, ss) -> toggleMode(pl)));
        s.add(new ScreenElement("hud")
                .glyph(hud ? "buttons/minus" : "buttons/plus", onOf(hud ? "buttons/minus" : "buttons/plus"))
                .label(msg().get("opcoes.botao_hud"))
                .at(0, -0.4 * sc, 0.24 * sc)
                .slot(31)
                .fallback(Material.REDSTONE)
                .action((pl, ss) -> {
                    plugin.prefs().setHud(pl, !plugin.prefs().hud(pl));
                    plugin.hud().update(pl);
                    ss.replace(options(pl));
                }));
        s.add(backButton(0.35, -0.4, 33));
        return s;
    }

    private void toggleMode(Player p) {
        DrawMode next = plugin.prefs().mode(p) == DrawMode.CURSOR ? DrawMode.BAU : DrawMode.CURSOR;
        plugin.prefs().setMode(p, next);
        msg().send(p, "opcoes.modo_alterado", Placeholder.component("modo",
                msg().get(next == DrawMode.CURSOR ? "opcoes.modo_cursor" : "opcoes.modo_bau")));
        if (next == DrawMode.CURSOR && !plugin.packStatus().hasPack(p)) {
            msg().send(p, "opcoes.sem_pack");
        }
        // Reabre no modo novo.
        plugin.sessions().close(p, false);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                plugin.sessions().open(p, options(p));
            }
        });
    }

    public Screen help(Player p) {
        Screen s = window("ajuda", msg().get("ajuda.titulo"))
                .text(msg().list("ajuda.linhas"), 0, -0.3 * sc(), 0.45 * sc())
                .info(13, Material.KNOWLEDGE_BOOK);
        s.add(backButton(0.45, -0.6, 31));
        return s;
    }

    /** Todos os ícones dos grupos de glyphs.yml; passar o cursor mostra a versão _on. */
    public Screen iconTest() {
        double sc = sc();
        List<String> ids = new ArrayList<>();
        plugin.glyphs().groups().values().forEach(ids::addAll);
        int cols = 8;
        double spacing = 0.3 * sc;
        double size = 0.24 * sc;
        int rowsCount = (ids.size() + cols - 1) / cols;
        double left = -(cols - 1) / 2.0 * spacing;
        double top = (rowsCount - 1) / 2.0 * spacing;
        Screen s = new Screen("icones").title(Component.text("Ícones")).rows(6);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            ScreenElement e = new ScreenElement("i:" + id)
                    .glyph(id, onOf(id))
                    .label(Component.text(id))
                    .at(left + (i % cols) * spacing, top - (i / cols) * spacing, size);
            if (i < 54) {
                e.slot(i);
            }
            s.add(e);
        }
        return s;
    }

    // ------------------------------------------------------------------ util

    private Component itemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return msg().get("perfil.vazio");
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return meta.displayName();
        }
        return Component.translatable(item.translationKey());
    }
}
