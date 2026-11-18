package com.aincrad.ui.core;

import com.aincrad.core.AincradCore;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.menu.MenuRenderer;
import com.aincrad.core.ui.UiService;
import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.api.AincradUIAPI;
import com.aincrad.ui.api.UiList;
import com.aincrad.ui.flat.FlatOverlay;
import com.aincrad.ui.screen.ChestRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A ligação com o AincradCore. Faz os menus dele marcados como flutuantes ({@code Menu.floating()})
 * abrirem no estilo SAO — uma lista do AincradUI, com cursor, abas e ordenação —, as confirmações
 * e as escolhas de quantidade virarem janelas, e atende o que os plugins pedem pelo
 * {@link UiService} do core: entradas do menu, avisos na tela, recargas das skills e o fundo dos
 * baús. Quem está sem o resource pack continua vendo o baú. Só é carregada com o AincradCore.
 */
public final class CoreMenuBridge implements MenuRenderer, UiService.Backend {

    /** O filtro e a ordem que o jogador deixou em cada menu (pelo título). */
    private record View(String title, int category, int sort) {
    }

    private final AincradUIPlugin plugin;
    private final AincradUIAPI ui;
    private final Map<UUID, View> views = new HashMap<>();

    private CoreMenuBridge(AincradUIPlugin plugin, AincradUIAPI ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    public static void register(AincradUIPlugin plugin, AincradUIAPI ui) {
        CoreMenuBridge bridge = new CoreMenuBridge(plugin, ui);
        AincradCore core = AincradCore.get();
        core.menus().register(bridge);
        core.ui().attach(bridge);
        // A barra azul e as recargas vêm do que os plugins registraram no core (o AincradSkills).
        plugin.flat().setEnergyProvider(p -> {
            double[] e = core.ui().energy(p);
            return e == null || e.length < 2 ? null : new int[]{(int) Math.round(e[0]), (int) Math.round(e[1])};
        });
        plugin.flat().setCooldownProvider(p -> {
            List<FlatOverlay.Cooldown> out = new ArrayList<>();
            for (UiService.Bar b : core.ui().skillBars(p)) {
                // O ícone vem da posição da skill entre as equipadas.
                out.add(new FlatOverlay.Cooldown(b.label(), b.remaining(), out.size()));
            }
            return out;
        });
    }

    public static void unregister() {
        AincradCore.get().menus().unregister("cursor");
        AincradCore.get().ui().detach();
    }

    @Override
    public String id() {
        return "cursor";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(Player player) {
        return false; // só os menus marcados como flutuantes
    }

    @Override
    public boolean supports(Player player, Menu menu) {
        return menu.isFloating() && plugin.packStatus().hasPack(player);
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Override
    public void open(Player player, Menu menu) {
        String title = plain(menu.title());
        View v = views.get(player.getUniqueId());
        // Outro menu: o filtro e a ordem voltam ao começo.
        if (v == null || !v.title().equals(title)) {
            v = new View(title, 0, 0);
            views.put(player.getUniqueId(), v);
        }
        show(player, menu, v);
    }

    private void show(Player player, Menu menu, View v) {
        Set<String> categories = new LinkedHashSet<>();
        Set<String> sorts = new LinkedHashSet<>();
        for (Menu.Button b : menu.items()) {
            if (b.category() != null) {
                categories.add(b.category());
            }
            sorts.addAll(b.sorts().keySet());
        }
        List<String> cats = new ArrayList<>(categories);
        List<String> keys = new ArrayList<>(sorts);
        // 0 = todas as categorias; 0 = a ordem em que o plugin mandou.
        int cat = cats.size() < 2 ? 0 : Math.floorMod(v.category(), cats.size() + 1);
        int sort = keys.isEmpty() ? 0 : Math.floorMod(v.sort(), keys.size() + 1);

        List<Menu.Button> shown = new ArrayList<>();
        for (Menu.Button b : menu.items()) {
            if (cat == 0 || cats.get(cat - 1).equals(b.category())) {
                shown.add(b);
            }
        }
        if (sort > 0) {
            String key = keys.get(sort - 1);
            // Quem não tem o valor vai para o fim; a ordem original desempata.
            shown.sort(Comparator.comparingDouble(b -> b.sorts().getOrDefault(key, Double.MAX_VALUE)));
        }

        UiList list = UiList.of(menu.title());
        for (Menu.Button b : shown) {
            list.row(UiList.Row.item(b.icon(), b.name(), detail(b.lore()), pl -> click(pl, b)).lore(b.lore()));
        }
        if (cats.size() >= 2) {
            Component label = plugin.messages().get("lista.filtro", Placeholder.unparsed("valor",
                    cat == 0 ? plain(plugin.messages().get("lista.todos")) : cats.get(cat - 1)));
            list.button(new UiList.Button("buttons/list", label, pl -> {
                View next = new View(v.title(), cat + 1, sort);
                views.put(pl.getUniqueId(), next);
                show(pl, menu, next);
            }));
        }
        if (!keys.isEmpty()) {
            Component label = plugin.messages().get("lista.ordem", Placeholder.unparsed("valor",
                    sort == 0 ? plain(plugin.messages().get("lista.padrao")) : keys.get(sort - 1)));
            list.button(new UiList.Button("buttons/details", label, pl -> {
                View next = new View(v.title(), cat, sort + 1);
                views.put(pl.getUniqueId(), next);
                show(pl, menu, next);
            }));
        }
        for (Menu.Button b : menu.buttons()) {
            list.button(new UiList.Button("buttons/list", b.name(), pl -> click(pl, b)));
        }
        // Um menu aberto a partir de outra lista troca a lista; a partir do menu principal, entra
        // por cima (agachar volta para os ícones).
        if (ui.isMenuOpen(player) && plugin.sessions().get(player) != null
                && "lista".equals(plugin.sessions().get(player).current().id())) {
            ui.replaceList(player, list);
        } else {
            ui.openList(player, list);
        }
    }

    /** A confirmação do core vira a janela de alerta do SAO, para quem tem o resource pack. */
    @Override
    public boolean confirm(Player player, Component question, Consumer<Player> yes, Consumer<Player> no) {
        if (!plugin.packStatus().hasPack(player)) {
            return false;
        }
        ui.openConfirm(player, question, yes == null ? null : yes::accept, no == null ? null : no::accept);
        return true;
    }

    /** A escolha de quantidade do core vira a janela com − e +. */
    @Override
    public boolean amount(Player player, Component title, long min, long max, long start, long step, Consumer<Long> chosen) {
        if (!plugin.packStatus().hasPack(player)) {
            return false;
        }
        ui.openAmount(player, title, min, max, start, step, chosen::accept);
        return true;
    }

    private void click(Player p, Menu.Button b) {
        if (b.closeOnClick()) {
            ui.closeMenu(p);
        }
        if (b.action() != null) {
            b.action().accept(p);
        }
    }

    /**
     * A linha mostra um detalhe curto: as duas primeiras linhas da descrição, juntas. A descrição
     * inteira aparece ao lado quando o cursor está na linha.
     */
    private static Component detail(List<Component> lore) {
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        return Component.join(JoinConfiguration.separator(Component.text(" · ")), lore.subList(0, Math.min(2, lore.size())));
    }

    // ------------------------------------------------------------------ o que os plugins pedem pelo core

    @Override
    public void menuEntry(UiService.Entry e) {
        ui.registerMenuEntry(e.id(), e.icon(), e.name(), e.group(), p -> e.action().accept(p));
    }

    @Override
    public boolean toast(Player player, Component title, Component detail) {
        return plugin.toasts().push(player, title, detail);
    }

    @Override
    public boolean prompt(Player player, Component question, Consumer<Player> yes, Consumer<Player> no) {
        // Só para quem está livre: com um menu ou uma tela aberta, o convite fica no chat.
        if (!plugin.settings().promptsEnabled || !plugin.packStatus().hasPack(player) || ui.isMenuOpen(player)
                || plugin.flat().isOpen(player) || player.isDead()) {
            return false;
        }
        ui.openConfirm(player, question, yes == null ? null : yes::accept, no == null ? null : no::accept);
        return true;
    }

    @Override
    public boolean banner(Player player, String id) {
        return ui.banner(player, id);
    }

    @Override
    public Component chestTitle(Player player, Component title, int rows) {
        return rows == 6 && plugin.packStatus().hasPack(player) ? ChestRenderer.skinned(plugin, title) : title;
    }
}
