package com.aincrad.ui.screen;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.api.MenuAction;
import com.aincrad.ui.api.UiList;
import com.aincrad.ui.items.ItemActions;
import com.aincrad.ui.items.ItemCategory;
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
 * Monta as telas. Cada tela descreve as posições do modo cursor (blocos no plano, (0, 0) no
 * centro, y para cima) e os slots do modo baú; o renderizador escolhido usa só o que precisa.
 *
 * <p>Layout do menu principal, como no anime: coluna de ícones em anel no centro; à esquerda, o
 * painel de perfil (silhueta com o equipamento) e uma seta apontando para a coluna; à direita do
 * ícone escolhido, uma seta e as linhas do submenu (ícone + nome sobre fundo branco).
 */
public final class Screens {

    public static final List<String> NAMES = List.of("menu", "itens", "perfil", "detalhes", "confirmar", "opcoes", "ajuda", "icones");

    // Listas
    static final int LIST_PAGE = 6;
    static final double LIST_TOP = 0.51;
    static final int[] LIST_CHEST_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private static final String WINDOW = "window";
    private static final String WINDOW_CHEST = "window_bau";
    private static final String PANEL = "profile_panel";

    // Coluna principal
    static final double COLUMN_ICON = 0.28;
    static final double COLUMN_SPACING = 0.33;
    // Submenu
    static final double ARROW_SIZE = 0.06;
    static final double ROW_HEIGHT = 0.19;
    static final double ROW_SPACING = 0.205;
    static final double ROW_LEFT = 0.25;
    // Painel de perfil (textura 512x600)
    static final double PANEL_HEIGHT = 1.25;
    static final double PANEL_RATIO = 512.0 / 600.0;
    static final double PANEL_GAP = 0.22;
    // Janelas (textura 512x600, linha do título a 64 px do topo)
    static final double WINDOW_HEIGHT = 1.3;

    /**
     * Pontos de equipamento no painel de perfil (pixels da textura 512x600). Os outros pontos
     * do desenho ficam só de enfeite.
     */
    static final Map<EquipmentSlot, int[]> PANEL_NODES = Map.of(
            EquipmentSlot.HEAD, new int[]{255, 138},
            EquipmentSlot.HAND, new int[]{63, 266},
            EquipmentSlot.OFF_HAND, new int[]{446, 266},
            EquipmentSlot.CHEST, new int[]{47, 346},
            EquipmentSlot.LEGS, new int[]{462, 346},
            EquipmentSlot.FEET, new int[]{255, 553});

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
            case "itens" -> items(p, ItemCategory.TUDO, 0);
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
        double spacing = COLUMN_SPACING * sc;
        double size = COLUMN_ICON * sc;
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
                    .at(0, y, size)
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

        if (plugin.settings().menuProfilePanel) {
            double ph = PANEL_HEIGHT * sc;
            double pw = ph * PANEL_RATIO;
            double cx = -(PANEL_GAP * sc + pw / 2.0);
            addProfilePanel(s, p, cx, 0, ph, false);
            // Seta do painel para a coluna, na altura do ícone escolhido.
            double ay = selected != null ? selY : 0;
            ay = Math.max(-ph / 2 + 0.1, Math.min(ph / 2 - 0.1, ay));
            s.add(new ScreenElement("seta_painel").glyph("arrow_right", null).decorative().onlyInCursor()
                    .at(-(PANEL_GAP * sc) + ARROW_SIZE * sc / 2.0, ay, ARROW_SIZE * sc));
        }

        if (selected != null) {
            List<Entry> entries = menu.entriesOf(selected);
            double rsp = ROW_SPACING * sc;
            double rh = ROW_HEIGHT * sc;
            double rtop = selY + (entries.size() - 1) / 2.0 * rsp;
            s.add(new ScreenElement("seta_sub").glyph("arrow_left", null).decorative().onlyInCursor()
                    .at(ROW_LEFT * sc - ARROW_SIZE * sc * 0.7, selY, ARROW_SIZE * sc));
            for (int j = 0; j < entries.size(); j++) {
                Entry e = entries.get(j);
                String eid = e.id();
                s.add(new ScreenElement("e:" + eid)
                        .kind(ScreenElement.Kind.ROW)
                        .glyph(e.icon(), onOf(e.icon()))
                        .label(e.name())
                        .at(ROW_LEFT * sc, rtop - j * rsp, rh)
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

    /** Converte um ponto da textura do painel (512x600) para o plano do menu. */
    static double[] panelPoint(int px, int py, double cx, double cy, double ph) {
        double pw = ph * PANEL_RATIO;
        return new double[]{cx - pw / 2.0 + px / 512.0 * pw, cy + ph / 2.0 - py / 600.0 * ph};
    }

    /**
     * Painel de perfil do modo cursor: desenho com a silhueta, nome do jogador no topo e o
     * equipamento flutuando sobre os pontos (clicar abre os detalhes da peça).
     */
    private void addProfilePanel(Screen s, Player p, double cx, double cy, double ph, boolean stats) {
        double sc = sc();
        s.add(new ScreenElement("painel").glyph(PANEL, null).decorative().onlyInCursor()
                .at(cx, cy, ph).depth(0.08));
        List<Component> title = new ArrayList<>();
        Component badge = plugin.hud().badge(p);
        title.add(badge == null ? Component.text(p.getName()) : badge.append(Component.text(p.getName())));
        double titleScale = 0.5 * sc;
        double titleY = cy + ph / 2.0 - ph * (78.0 / 600.0);
        if (stats) {
            title.add(msg().get("perfil.resumo", statTags(p)));
            titleScale = 0.36 * sc;
        }
        s.label("perfil_nome", title, cx, titleY, titleScale, true, false);

        PlayerInventory inv = p.getInventory();
        double node = 0.11 * sc;
        for (Map.Entry<EquipmentSlot, int[]> en : PANEL_NODES.entrySet()) {
            double[] at = panelPoint(en.getValue()[0], en.getValue()[1], cx, cy, ph);
            ItemStack piece = inv.getItem(en.getKey());
            boolean empty = piece == null || piece.getType().isAir();
            EquipmentSlot slot = en.getKey();
            ScreenElement el = new ScreenElement("no:" + slot.name().toLowerCase(Locale.ROOT))
                    .kind(ScreenElement.Kind.HIT)
                    .onlyInCursor()
                    .at(at[0], at[1], node)
                    .label(empty ? msg().get("perfil.slot." + slotKey(slot)) : itemName(piece));
            if (!empty) {
                // Peça equipada: detalhes, desequipar ou trocar.
                el.item(piece.clone()).action((pl, ss) -> ss.push(equippedWindow(pl, slot)));
            } else {
                // Ponto vazio: escolher na mochila o que equipar ali.
                el.action((pl, ss) -> ss.push(list(pl, equipChooser(pl, slot), 0)));
            }
            s.add(el);
        }
    }

    private static String slotKey(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "cabeca";
            case CHEST -> "peito";
            case LEGS -> "pernas";
            case FEET -> "pes";
            case HAND -> "mao";
            case OFF_HAND -> "mao_secundaria";
            default -> "outro";
        };
    }

    private String onOf(String glyph) {
        String on = glyph + "_on";
        return plugin.glyphs().get(on) != null ? on : null;
    }

    private void runEntry(Player p, MenuSession s, String id) {
        MenuRegistry menu = plugin.menu();
        MenuAction api = menu.apiAction(id);
        if (api != null) {
            // O menu continua aberto: a ação pode abrir uma lista por cima ou fechar o menu.
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
            case "itens" -> s.push(items(p, ItemCategory.TUDO, 0));
            case "inventario" -> {
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
        if (plugin.deathGame()) {
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

    /** Janela branca: fundo, título acima da linha e área de texto logo abaixo dela. */
    private Screen window(String id, Component title) {
        double sc = sc();
        double wh = WINDOW_HEIGHT * sc;
        Screen s = new Screen(id)
                .title(title)
                .background(WINDOW, 0, 0, wh)
                .chestBackground(WINDOW_CHEST)
                .rows(6);
        s.label("titulo", List.of(title), 0, wh / 2.0 - wh * (56.0 / 600.0), 0.55 * sc, true, false);
        return s;
    }

    /** Base do bloco de texto para que ele comece logo abaixo da linha do título. */
    private double bodyBottom(int lines, double scale) {
        double wh = WINDOW_HEIGHT * sc();
        double top = wh / 2.0 - wh * (84.0 / 600.0);
        return top - lines * CursorRenderer.ROW_LINE_PX * CursorRenderer.BLOCKS_PER_PX * scale;
    }

    private Screen body(Screen s, List<Component> lines, double scale) {
        return s.text(lines, 0, bodyBottom(lines.size(), scale), scale);
    }

    private double buttonsY() {
        return -WINDOW_HEIGHT * sc() / 2.0 + 0.2 * sc();
    }

    private ScreenElement backButton(double x, int slot) {
        return new ScreenElement("voltar")
                .glyph("buttons/cancel", onOf("buttons/cancel"))
                .label(msg().get("perfil.botao_voltar"))
                .at(x * sc(), buttonsY(), 0.2 * sc())
                .slot(slot)
                .fallback(Material.ARROW)
                .action((pl, ss) -> plugin.sessions().back(pl));
    }

    private TagResolver statTags(Player p) {
        AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        return TagResolver.resolver(
                Placeholder.unparsed("jogador", p.getName()),
                Placeholder.unparsed("level", String.valueOf(p.getLevel())),
                Placeholder.unparsed("vida", String.valueOf((int) Math.ceil(p.getHealth()))),
                Placeholder.unparsed("vida_max", String.valueOf((int) Math.round(max))));
    }

    /** Perfil: no modo cursor, o painel com a silhueta; no baú, as peças em slots. */
    public Screen profile(Player p) {
        double sc = sc();
        PlayerInventory inv = p.getInventory();
        TagResolver tags = TagResolver.resolver(statTags(p),
                Placeholder.component("cabeca", itemName(inv.getHelmet())),
                Placeholder.component("peito", itemName(inv.getChestplate())),
                Placeholder.component("pernas", itemName(inv.getLeggings())),
                Placeholder.component("pes", itemName(inv.getBoots())),
                Placeholder.component("mao", itemName(inv.getItemInMainHand())));

        Screen s = new Screen("perfil")
                .title(msg().get("perfil.titulo"))
                .chestBackground(WINDOW_CHEST)
                .chestInfo(msg().list("perfil.linhas", tags))
                .info(13, Material.PLAYER_HEAD)
                .rows(6);
        double ph = WINDOW_HEIGHT * sc;
        addProfilePanel(s, p, 0, 0.08 * sc, ph, true);

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

        double by = -ph / 2.0 + 0.08 * sc - 0.16 * sc;
        s.add(new ScreenElement("detalhes")
                .glyph("buttons/details", onOf("buttons/details"))
                .label(msg().get("perfil.botao_detalhes"))
                .at(-0.15 * sc, by, 0.2 * sc)
                .slot(40)
                .fallback(Material.SPYGLASS)
                .action((pl, ss) -> ss.push(itemDetails(pl, pl.getInventory().getItemInMainHand()))));
        s.add(new ScreenElement("voltar")
                .glyph("buttons/cancel", onOf("buttons/cancel"))
                .label(msg().get("perfil.botao_voltar"))
                .at(0.15 * sc, by, 0.2 * sc)
                .slot(44)
                .fallback(Material.ARROW)
                .action((pl, ss) -> plugin.sessions().back(pl)));
        return s;
    }

    public Screen itemDetails(Player p, ItemStack item) {
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
            // No modo cursor o item flutua na janela, acima dos botões.
            s.add(new ScreenElement("item_3d").kind(ScreenElement.Kind.HIT).onlyInCursor().decorative()
                    .at(0, buttonsY() + 0.3 * sc(), 0.28 * sc()).item(item.clone()));
        }
        body(s, lines, 0.45 * sc());
        s.add(backButton(0, 44));
        return s;
    }

    public Screen confirm(Component question, ElementAction yes, ElementAction no) {
        double sc = sc();
        Screen s = window("confirmar", msg().get("confirmar.titulo")).info(13, Material.PAPER);
        body(s, List.of(question), 0.5 * sc);
        s.add(new ScreenElement("sim")
                .glyph("buttons/yes", onOf("buttons/yes"))
                .label(msg().get("confirmar.sim"))
                .at(-0.22 * sc, buttonsY(), 0.24 * sc)
                .slot(29)
                .fallback(Material.LIME_WOOL)
                .action(yes));
        s.add(new ScreenElement("nao")
                .glyph("buttons/no", onOf("buttons/no"))
                .label(msg().get("confirmar.nao"))
                .at(0.22 * sc, buttonsY(), 0.24 * sc)
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
        Screen s = window("opcoes", msg().get("opcoes.titulo")).info(13, Material.REPEATER);
        body(s, msg().list("opcoes.linhas", tags), 0.45 * sc);
        s.add(new ScreenElement("modo")
                .glyph("buttons/list", onOf("buttons/list"))
                .label(msg().get("opcoes.botao_modo"))
                .at(-0.3 * sc, buttonsY(), 0.22 * sc)
                .slot(29)
                .fallback(Material.COMPASS)
                .action((pl, ss) -> toggleMode(pl)));
        s.add(new ScreenElement("hud")
                .glyph(hud ? "buttons/minus" : "buttons/plus", onOf(hud ? "buttons/minus" : "buttons/plus"))
                .label(msg().get("opcoes.botao_hud"))
                .at(0, buttonsY(), 0.22 * sc)
                .slot(31)
                .fallback(Material.REDSTONE)
                .action((pl, ss) -> {
                    plugin.prefs().setHud(pl, !plugin.prefs().hud(pl));
                    plugin.hud().update(pl);
                    ss.replace(options(pl));
                }));
        s.add(backButton(0.3, 33));
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
        Screen s = window("ajuda", msg().get("ajuda.titulo")).info(13, Material.KNOWLEDGE_BOOK);
        body(s, msg().list("ajuda.linhas"), 0.4 * sc());
        s.add(backButton(0, 31));
        return s;
    }

    // ------------------------------------------------------------------ listas

    /** Quantas páginas tem uma lista de {@code rows} linhas (no mínimo 1). */
    static int pages(int rows) {
        return Math.max(1, (rows + LIST_PAGE - 1) / LIST_PAGE);
    }

    /**
     * Lista estilo SAO: título em cima, até 6 linhas centralizadas, botões redondos embaixo e
     * setas de página nas laterais. No baú, as linhas vão para a segunda fileira.
     */
    public Screen list(Player p, UiList list, int page) {
        double sc = sc();
        List<UiList.Row> rows = list.rows();
        int total = pages(rows.size());
        int pg = Math.max(0, Math.min(total - 1, page));
        Component title = total > 1
                ? list.title().append(Component.text("  " + (pg + 1) + "/" + total))
                : list.title();
        Screen s = new Screen("lista").title(title).rows(6).chestBackground(WINDOW_CHEST);
        double rh = ROW_HEIGHT * sc;
        double rsp = ROW_SPACING * sc;
        double rowW = rh * 3.83;
        double top = LIST_TOP * sc;
        s.label("titulo", List.of(list.title().append(total > 1
                        ? Component.text("  " + (pg + 1) + "/" + total) : Component.empty())),
                0, top + rh / 2.0 + 0.04 * sc, 0.6 * sc, false, false);

        int from = pg * LIST_PAGE;
        int to = Math.min(rows.size(), from + LIST_PAGE);
        for (int i = from; i < to; i++) {
            UiList.Row r = rows.get(i);
            int n = i - from;
            ScreenElement e = new ScreenElement("row:" + i)
                    .kind(ScreenElement.Kind.ROW)
                    .glyph(r.icon() != null ? r.icon() : "under_icons/items", null)
                    .label(r.label())
                    .detail(r.detail())
                    .at(-rowW / 2.0, top - n * rsp, rh)
                    .slot(n < LIST_CHEST_SLOTS.length ? LIST_CHEST_SLOTS[n] : -1)
                    .fallback(Material.PAPER);
            if (r.item() != null) {
                e.item(r.item().clone());
            }
            MenuAction action = r.action();
            if (action != null) {
                e.action((pl, ss) -> action.run(pl));
            } else {
                e.decorative();
            }
            s.add(e);
        }
        if (rows.isEmpty()) {
            s.label("vazio", List.of(list.emptyText()), 0, top - rsp, 0.5 * sc, false, false);
            s.chestInfo(List.of(list.emptyText())).info(13, Material.PAPER);
        }

        double by = top - LIST_PAGE * rsp - 0.06 * sc;
        List<UiList.Button> buttons = list.buttons();
        double bsp = 0.3 * sc;
        double bx0 = -(buttons.size() - 1) / 2.0 * bsp;
        for (int i = 0; i < buttons.size(); i++) {
            UiList.Button b = buttons.get(i);
            MenuAction action = b.action();
            s.add(new ScreenElement("btn:" + i)
                    .glyph(b.icon(), onOf(b.icon()))
                    .label(b.label())
                    .at(bx0 + i * bsp, by, 0.22 * sc)
                    .slot(47 + Math.min(i, 4))
                    .fallback(Material.STONE_BUTTON)
                    .action((pl, ss) -> action.run(pl)));
        }
        if (pg > 0) {
            s.add(new ScreenElement("prev").glyph("arrow_left", null)
                    .label(msg().get("lista.anterior"))
                    .at(-rowW / 2.0 - 0.14 * sc, top - (LIST_PAGE - 1) / 2.0 * rsp, 0.1 * sc)
                    .slot(45).fallback(Material.ARROW)
                    .action((pl, ss) -> ss.replace(list(pl, list, pg - 1))));
        }
        if (pg < total - 1) {
            s.add(new ScreenElement("next").glyph("arrow_right", null)
                    .label(msg().get("lista.proxima"))
                    .at(rowW / 2.0 + 0.14 * sc, top - (LIST_PAGE - 1) / 2.0 * rsp, 0.1 * sc)
                    .slot(53).fallback(Material.ARROW)
                    .action((pl, ss) -> ss.replace(list(pl, list, pg + 1))));
        }
        return s;
    }

    /** Janela de texto com botões (API openWindow). */
    public Screen textWindow(Component title, List<Component> lines, List<UiList.Button> buttons) {
        double sc = sc();
        Screen s = window("texto", title).info(13, Material.PAPER);
        body(s, lines, 0.42 * sc);
        double bsp = 0.28 * sc;
        double x0 = -(buttons.size() - 1) / 2.0 * bsp;
        int[] slots = {29, 30, 31, 32, 33};
        for (int i = 0; i < buttons.size(); i++) {
            UiList.Button b = buttons.get(i);
            MenuAction action = b.action();
            s.add(new ScreenElement("btn:" + i)
                    .glyph(b.icon(), onOf(b.icon()))
                    .label(b.label())
                    .at(x0 + i * bsp, buttonsY(), 0.22 * sc)
                    .slot(slots[Math.min(i, 4)])
                    .fallback(Material.STONE_BUTTON)
                    .action((pl, ss) -> action.run(pl)));
        }
        return s;
    }

    // ------------------------------------------------------------------ itens

    /** Itens da mochila e da hotbar, com filtro por categoria. Clicar abre as ações do item. */
    public Screen items(Player p, ItemCategory filter, int page) {
        List<UiList.Row> rows = new ArrayList<>();
        for (ItemActions.Entry e : ItemActions.list(p, filter)) {
            Component detail = e.item().getAmount() > 1 ? Component.text("x" + e.item().getAmount()) : null;
            if (e.slot() == p.getInventory().getHeldItemSlot()) {
                detail = msg().get("itens.na_mao_tag");
            }
            rows.add(UiList.Row.item(e.item(), itemName(e.item()), detail,
                    pl -> session(pl).push(itemWindow(pl, e, filter, page))));
        }
        UiList list = UiList.of(msg().get("itens.titulo",
                        Placeholder.component("filtro", msg().get("itens.categoria." + filter.key()))))
                .rows(rows)
                .empty(msg().get("itens.vazio_lista"))
                .button(new UiList.Button("buttons/list",
                        msg().get("itens.filtro", Placeholder.component("filtro", msg().get("itens.categoria." + filter.next().key()))),
                        pl -> session(pl).replace(items(pl, filter.next(), 0))))
                .button(new UiList.Button("buttons/cancel", msg().get("perfil.botao_voltar"),
                        pl -> plugin.sessions().back(pl)));
        Screen s = list(p, list, page);
        return s;
    }

    private MenuSession session(Player p) {
        MenuSession s = plugin.sessions().get(p);
        if (s == null) {
            throw new IllegalStateException("menu fechado");
        }
        return s;
    }

    private List<Component> detailLines(ItemStack item) {
        List<Component> lines = new ArrayList<>();
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
        return lines;
    }

    /** Janela de um item da mochila: detalhes e as ações Equipar, Segurar e Jogar fora. */
    private Screen itemWindow(Player p, ItemActions.Entry entry, ItemCategory filter, int page) {
        double sc = sc();
        ItemStack item = entry.item();
        Screen s = window("item", msg().get("itens.janela")).info(4, Material.BOOK);
        body(s, detailLines(item), 0.42 * sc);
        s.add(new ScreenElement("item").onlyInChest().decorative().slot(13).item(item.clone()));
        s.add(new ScreenElement("item_3d").kind(ScreenElement.Kind.HIT).onlyInCursor().decorative()
                .at(0, buttonsY() + 0.32 * sc, 0.28 * sc).item(item.clone()));

        List<ScreenElement> buttons = new ArrayList<>();
        if (ItemActions.equipSlot(item) != null) {
            buttons.add(new ScreenElement("equipar").glyph("buttons/start", onOf("buttons/start"))
                    .label(msg().get("itens.acao.equipar")).fallback(Material.IRON_CHESTPLATE)
                    .action((pl, ss) -> afterItemAction(pl, ItemActions.equip(pl, entry.slot(), item), "itens.equipado", filter, page)));
        }
        buttons.add(new ScreenElement("segurar").glyph("buttons/plus", onOf("buttons/plus"))
                .label(msg().get("itens.acao.segurar")).fallback(Material.STICK)
                .action((pl, ss) -> afterItemAction(pl, ItemActions.hold(pl, entry.slot(), item), "itens.na_mao", filter, page)));
        buttons.add(new ScreenElement("jogar").glyph("buttons/minus", onOf("buttons/minus"))
                .label(msg().get("itens.acao.jogar")).fallback(Material.HOPPER)
                .action((pl, ss) -> ss.push(confirm(msg().get("itens.confirmar_jogar", Placeholder.component("item", itemName(item))),
                        (p2, s2) -> {
                            s2.back();
                            afterItemAction(p2, ItemActions.drop(p2, entry.slot(), item), "itens.jogado", filter, page);
                        },
                        (p2, s2) -> plugin.sessions().back(p2)))));
        buttons.add(new ScreenElement("voltar").glyph("buttons/cancel", onOf("buttons/cancel"))
                .label(msg().get("perfil.botao_voltar")).fallback(Material.ARROW)
                .action((pl, ss) -> plugin.sessions().back(pl)));

        double bsp = 0.26 * sc;
        double x0 = -(buttons.size() - 1) / 2.0 * bsp;
        int[] slots = {29, 30, 32, 33};
        for (int i = 0; i < buttons.size(); i++) {
            s.add(buttons.get(i).at(x0 + i * bsp, buttonsY(), 0.2 * sc).slot(slots[Math.min(i, 3)]));
        }
        return s;
    }

    /** Depois de uma ação: mensagem, fecha a janela do item e atualiza a lista. */
    private void afterItemAction(Player p, ItemActions.Result r, String okKey, ItemCategory filter, int page) {
        msg().send(p, r == ItemActions.Result.OK ? okKey : "itens.erro." + r.name().toLowerCase(Locale.ROOT));
        MenuSession s = plugin.sessions().get(p);
        if (s == null) {
            return;
        }
        s.back();
        if (s.current() != null && "lista".equals(s.current().id())) {
            s.replace(items(p, filter, page));
        } else {
            s.replace(s.current() != null && "perfil".equals(s.current().id()) ? profile(p) : s.current());
        }
    }

    /** Peça equipada: detalhes e Desequipar. */
    private Screen equippedWindow(Player p, EquipmentSlot slot) {
        double sc = sc();
        ItemStack item = p.getInventory().getItem(slot);
        Screen s = window("equipado", msg().get("itens.janela")).info(4, Material.BOOK);
        if (item == null || item.getType().isAir()) {
            body(s, List.of(msg().get("perfil.slot." + slotKey(slot))), 0.42 * sc);
        } else {
            body(s, detailLines(item), 0.42 * sc);
            s.add(new ScreenElement("item").onlyInChest().decorative().slot(13).item(item.clone()));
            s.add(new ScreenElement("item_3d").kind(ScreenElement.Kind.HIT).onlyInCursor().decorative()
                    .at(0, buttonsY() + 0.32 * sc, 0.28 * sc).item(item.clone()));
        }
        if (slot != EquipmentSlot.HAND) {
            s.add(new ScreenElement("desequipar").glyph("buttons/minus", onOf("buttons/minus"))
                    .label(msg().get("itens.acao.desequipar"))
                    .at(-0.15 * sc, buttonsY(), 0.2 * sc).slot(30).fallback(Material.HOPPER)
                    .action((pl, ss) -> {
                        ItemActions.Result r = ItemActions.unequip(pl, slot);
                        msg().send(pl, r == ItemActions.Result.OK ? "itens.desequipado" : "itens.erro." + r.name().toLowerCase(Locale.ROOT));
                        ss.back();
                        ss.replace(refreshed(pl, ss.current()));
                    }));
        }
        s.add(new ScreenElement("trocar").glyph("buttons/list", onOf("buttons/list"))
                .label(msg().get("itens.acao.trocar"))
                .at(slot != EquipmentSlot.HAND ? 0.15 * sc : -0.15 * sc, buttonsY(), 0.2 * sc).slot(31).fallback(Material.CHEST)
                .action((pl, ss) -> ss.replace(list(pl, equipChooser(pl, slot), 0))));
        s.add(backButton(slot != EquipmentSlot.HAND ? 0.45 : 0.15, 32));
        return s;
    }

    /** Itens da mochila que servem num slot de equipamento. */
    private UiList equipChooser(Player p, EquipmentSlot slot) {
        List<UiList.Row> rows = new ArrayList<>();
        for (ItemActions.Entry e : ItemActions.candidates(p, slot)) {
            rows.add(UiList.Row.item(e.item(), itemName(e.item()),
                    e.item().getAmount() > 1 ? Component.text("x" + e.item().getAmount()) : null,
                    pl -> {
                        ItemActions.Result r = slot == EquipmentSlot.HAND
                                ? ItemActions.hold(pl, e.slot(), e.item())
                                : ItemActions.equip(pl, e.slot(), e.item());
                        msg().send(pl, r == ItemActions.Result.OK ? "itens.equipado" : "itens.erro." + r.name().toLowerCase(Locale.ROOT));
                        MenuSession ss = session(pl);
                        ss.back();
                        ss.replace(refreshed(pl, ss.current()));
                    }));
        }
        return UiList.of(msg().get("itens.escolher", Placeholder.component("slot", msg().get("perfil.slot_nome." + slotKey(slot)))))
                .rows(rows)
                .empty(msg().get("itens.nada_para_slot"))
                .button(new UiList.Button("buttons/cancel", msg().get("perfil.botao_voltar"), pl -> plugin.sessions().back(pl)));
    }

    /** Recria a tela atual com os dados novos (menu e perfil mostram o equipamento). */
    private Screen refreshed(Player p, Screen current) {
        if (current == null) {
            return main(p, null);
        }
        return switch (current.id()) {
            case "perfil" -> profile(p);
            case "menu" -> main(p, selectedGroup(current));
            default -> current;
        };
    }

    private static String selectedGroup(Screen menu) {
        for (ScreenElement e : menu.elements()) {
            if (e.id().startsWith("g:") && e.selected()) {
                return e.id().substring(2);
            }
        }
        return null;
    }

    /** Ícones dos grupos de glyphs.yml; passar o cursor mostra a versão _on. */
    public Screen iconTest() {
        double sc = sc();
        List<String> ids = new ArrayList<>();
        for (String group : List.of("icons", "under_icons", "buttons", "skills")) {
            ids.addAll(plugin.glyphs().groups().getOrDefault(group, List.of()));
        }
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
