package com.aincrad.bosses.editor;

import com.aincrad.bosses.api.BossEditedEvent;
import com.aincrad.bosses.fight.FightManager;
import com.aincrad.bosses.hook.Hooks;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.core.AincradAdminActionEvent;
import com.aincrad.core.AincradCore;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Editor de chefes dentro do jogo ({@code /aincrad chefe editar <id>}): menus com − e + para
 * vida, dano, level, armadura, velocidade, fases, habilidades e recompensas, e um baú onde o admin
 * arrasta os itens de drop. Cada mudança é gravada no YAML do chefe e vale na próxima luta.
 */
public final class BossEditor implements Listener {

    /** O baú de itens de recompensa. */
    private static final class DropsHolder implements InventoryHolder {
        final String bossId;
        Inventory inventory;

        DropsHolder(String bossId) {
            this.bossId = bossId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int LAST_ATTACK_SLOT = 49;
    private static final List<String> SKILL_TYPES = List.of("investida", "area", "lacaios", "projetil", "salto", "furia", "sopro");

    private final Plugin plugin;
    private final FightManager fights;
    private final Hooks hooks;
    private final Messages msg;
    private final Runnable reload;

    public BossEditor(Plugin plugin, FightManager fights, Hooks hooks, Runnable reload) {
        this.plugin = plugin;
        this.fights = fights;
        this.hooks = hooks;
        this.msg = fights.messages();
        this.reload = reload;
    }

    // ------------------------------------------------------------------ YAML

    private File file(String id) {
        return fights.registry().source(id);
    }

    private YamlConfiguration yaml(String id) {
        return YamlConfiguration.loadConfiguration(file(id));
    }

    /** Grava o YAML, recarrega os chefes e avisa os outros plugins. */
    private void save(Player editor, String id, YamlConfiguration y, String field, Object value) {
        try {
            y.save(file(id));
        } catch (IOException e) {
            msg.send(editor, "geral.erro");
            plugin.getLogger().warning("Não foi possível gravar " + file(id).getName() + ": " + e.getMessage());
            return;
        }
        reload.run();
        BossDef def = fights.registry().get(id);
        if (def != null) {
            Bukkit.getPluginManager().callEvent(new BossEditedEvent(id, def.name(), def.floor(), editor, field, String.valueOf(value)));
        }
        Bukkit.getPluginManager().callEvent(new AincradAdminActionEvent(editor, "bosses", "chefe.editar", id + " " + field + " = " + value));
    }

    private static Object tidy(double v) {
        double r = Math.round(v * 100) / 100.0;
        return r == Math.rint(r) ? (Object) (long) r : (Object) r;
    }

    private static String show(double v) {
        return String.valueOf(tidy(v));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        }
        return out;
    }

    private static double num(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    // ------------------------------------------------------------------ peças de menu

    private static ItemStack icon(Material m) {
        return ItemBuilder.of(m).hideAll().build();
    }

    /**
     * Uma linha do menu: o valor e quatro botões (− grande, − pequeno, + pequeno, + grande),
     * completada com vidro até o fim da fileira.
     */
    private void row(Menu menu, Material icon, String label, double value, double small, double big, double min, double max,
                     java.util.function.BiConsumer<Player, Double> set) {
        menu.item(Menu.Button.of(icon(icon), msg.get("editor.campo", Messages.vars("campo", label, "valor", show(value))), null));
        double[] steps = {-big, -small, small, big};
        Material[] mats = {Material.RED_CONCRETE, Material.RED_STAINED_GLASS, Material.LIME_STAINED_GLASS, Material.LIME_CONCRETE};
        for (int i = 0; i < 4; i++) {
            double step = steps[i];
            double next = Math.max(min, Math.min(max, value + step));
            menu.item(Menu.Button.of(icon(mats[i]), msg.get("editor.passo", Messages.vars("passo", (step > 0 ? "+" : "") + show(step),
                    "valor", show(next))), p -> set.accept(p, next)));
        }
        for (int i = 0; i < 4; i++) {
            menu.item(Menu.Button.of(icon(Material.GRAY_STAINED_GLASS_PANE), Component.empty(), null));
        }
    }

    private Menu.Button back(Consumer<Player> to) {
        return Menu.Button.of(icon(Material.ARROW), msg.get("editor.voltar"), to);
    }

    // ------------------------------------------------------------------ menu principal

    public void open(Player p, String id) {
        BossDef def = fights.registry().get(id);
        if (def == null || file(id) == null) {
            msg.send(p, "admin.chefe_desconhecido", Messages.vars("id", id));
            return;
        }
        YamlConfiguration y = yaml(id);
        Menu menu = Menu.of(msg.get("editor.titulo", Messages.vars("chefe", def.name()))).rows(6);
        Object[][] fields = {
                {"vida", Material.RED_DYE, 10.0, 100.0, 1.0, 1_000_000.0, def.health()},
                {"dano", Material.IRON_SWORD, 1.0, 5.0, 0.0, 10_000.0, def.damage()},
                {"level", Material.EXPERIENCE_BOTTLE, 1.0, 5.0, 1.0, 1000.0, (double) def.level()},
                {"armadura", Material.IRON_CHESTPLATE, 1.0, 5.0, 0.0, 30.0, def.armor()},
                {"velocidade", Material.FEATHER, 0.01, 0.05, 0.05, 1.0, def.speed()},
        };
        for (Object[] f : fields) {
            String key = (String) f[0];
            row(menu, (Material) f[1], msg.raw("editor.campos." + key), (Double) f[6], (Double) f[2], (Double) f[3], (Double) f[4], (Double) f[5],
                    (pl, v) -> {
                        y.set(id + "." + key, tidy(v));
                        save(pl, id, y, key, tidy(v));
                        open(pl, id);
                    });
        }
        menu.button(Menu.Button.of(icon(Material.BOOK), msg.get("editor.fases"), pl -> phases(pl, id)));
        menu.button(Menu.Button.of(icon(Material.CHEST), msg.get("editor.recompensas"), pl -> rewards(pl, id)));
        menu.button(Menu.Button.of(icon(Material.OAK_DOOR), msg.get("editor.fechar"), pl -> { }).closing());
        AincradCore.get().menus().open(p, menu);
    }

    // ------------------------------------------------------------------ fases

    private void phases(Player p, String id) {
        YamlConfiguration y = yaml(id);
        List<Map<String, Object>> list = maps(y.getList(id + ".fases"));
        Menu menu = Menu.of(msg.get("editor.fases")).rows(4);
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            Map<String, Object> ph = list.get(i);
            menu.item(Menu.Button.of(icon(Material.PAPER), msg.get("editor.fase", Messages.vars("n", i + 1,
                    "nome", String.valueOf(ph.getOrDefault("nome", "—")), "vida", show(num(ph.get("vida"), 100)))), pl -> phase(pl, id, index)));
        }
        menu.button(Menu.Button.of(icon(Material.LIME_DYE), msg.get("editor.fase_nova"), pl -> {
            Map<String, Object> ph = new LinkedHashMap<>();
            ph.put("vida", 50);
            ph.put("nome", "Fase " + (list.size() + 1));
            list.add(ph);
            y.set(id + ".fases", list);
            save(pl, id, y, "fases", "nova fase aos 50%");
            phases(pl, id);
        }));
        menu.button(back(pl -> open(pl, id)));
        AincradCore.get().menus().open(p, menu);
    }

    private void phase(Player p, String id, int index) {
        YamlConfiguration y = yaml(id);
        List<Map<String, Object>> list = maps(y.getList(id + ".fases"));
        if (index >= list.size()) {
            phases(p, id);
            return;
        }
        Map<String, Object> ph = list.get(index);
        Menu menu = Menu.of(msg.get("editor.fase", Messages.vars("n", index + 1, "nome", String.valueOf(ph.getOrDefault("nome", "—")),
                "vida", show(num(ph.get("vida"), 100))))).rows(6);
        Object[][] fields = {
                {"vida", Material.RED_DYE, 5.0, 25.0, 1.0, 100.0, 100.0},
                {"dano_x", Material.IRON_SWORD, 0.1, 0.5, 0.1, 10.0, 1.0},
                {"velocidade_x", Material.FEATHER, 0.1, 0.5, 0.1, 5.0, 1.0},
        };
        for (Object[] f : fields) {
            String key = (String) f[0];
            row(menu, (Material) f[1], msg.raw("editor.campos." + key), num(ph.get(key), (Double) f[6]), (Double) f[2], (Double) f[3],
                    (Double) f[4], (Double) f[5], (pl, v) -> {
                        ph.put(key, tidy(v));
                        y.set(id + ".fases", list);
                        save(pl, id, y, "fases." + (index + 1) + "." + key, tidy(v));
                        phase(pl, id, index);
                    });
        }
        List<Map<String, Object>> skills = maps(ph.get("habilidades"));
        for (int i = 0; i < skills.size(); i++) {
            int si = i;
            Map<String, Object> s = skills.get(i);
            menu.item(Menu.Button.of(icon(Material.BLAZE_POWDER), msg.get("editor.habilidade", Messages.vars(
                    "tipo", String.valueOf(s.get("tipo")), "recarga", show(num(s.get("recarga"), 15)))), pl -> skill(pl, id, index, si)));
        }
        menu.button(Menu.Button.of(icon(Material.LIME_DYE), msg.get("editor.habilidade_nova"), pl -> skillType(pl, id, index)));
        if (list.size() > 1) {
            menu.button(Menu.Button.of(icon(Material.BARRIER), msg.get("editor.fase_remover"), pl -> {
                list.remove(index);
                y.set(id + ".fases", list);
                save(pl, id, y, "fases", "fase " + (index + 1) + " removida");
                phases(pl, id);
            }));
        }
        menu.button(back(pl -> phases(pl, id)));
        AincradCore.get().menus().open(p, menu);
    }

    private void skillType(Player p, String id, int index) {
        Menu menu = Menu.of(msg.get("editor.habilidade_nova")).rows(2);
        for (String type : SKILL_TYPES) {
            menu.item(Menu.Button.of(icon(Material.BLAZE_POWDER), msg.get("editor.tipo", Messages.vars("tipo", type)), pl -> {
                YamlConfiguration y = yaml(id);
                List<Map<String, Object>> list = maps(y.getList(id + ".fases"));
                if (index < list.size()) {
                    List<Map<String, Object>> skills = maps(list.get(index).get("habilidades"));
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("tipo", type);
                    s.put("recarga", 15);
                    skills.add(s);
                    list.get(index).put("habilidades", skills);
                    y.set(id + ".fases", list);
                    save(pl, id, y, "fases." + (index + 1) + ".habilidades", "+" + type);
                }
                phase(pl, id, index);
            }));
        }
        menu.button(back(pl -> phase(pl, id, index)));
        AincradCore.get().menus().open(p, menu);
    }

    private void skill(Player p, String id, int index, int si) {
        YamlConfiguration y = yaml(id);
        List<Map<String, Object>> list = maps(y.getList(id + ".fases"));
        List<Map<String, Object>> skills = index < list.size() ? maps(list.get(index).get("habilidades")) : List.of();
        if (si >= skills.size()) {
            phase(p, id, index);
            return;
        }
        Map<String, Object> s = skills.get(si);
        Menu menu = Menu.of(msg.get("editor.habilidade", Messages.vars("tipo", String.valueOf(s.get("tipo")),
                "recarga", show(num(s.get("recarga"), 15))))).rows(5);
        Object[][] fields = {
                {"recarga", Material.CLOCK, 1.0, 5.0, 1.0, 600.0, 15.0},
                {"dano", Material.IRON_SWORD, 1.0, 5.0, 0.0, 10_000.0, 6.0},
                {"raio", Material.ENDER_PEARL, 0.5, 2.0, 0.5, 32.0, 3.0},
        };
        for (Object[] f : fields) {
            String key = (String) f[0];
            row(menu, (Material) f[1], msg.raw("editor.campos." + key), num(s.get(key), (Double) f[6]), (Double) f[2], (Double) f[3],
                    (Double) f[4], (Double) f[5], (pl, v) -> {
                        s.put(key, tidy(v));
                        list.get(index).put("habilidades", skills);
                        y.set(id + ".fases", list);
                        save(pl, id, y, "fases." + (index + 1) + ".habilidades." + (si + 1) + "." + key, tidy(v));
                        skill(pl, id, index, si);
                    });
        }
        menu.button(Menu.Button.of(icon(Material.BARRIER), msg.get("editor.habilidade_remover"), pl -> {
            skills.remove(si);
            list.get(index).put("habilidades", skills);
            y.set(id + ".fases", list);
            save(pl, id, y, "fases." + (index + 1) + ".habilidades", "-" + s.get("tipo"));
            phase(pl, id, index);
        }));
        menu.button(back(pl -> phase(pl, id, index)));
        AincradCore.get().menus().open(p, menu);
    }

    // ------------------------------------------------------------------ recompensas

    private void rewards(Player p, String id) {
        BossDef def = fights.registry().get(id);
        if (def == null) {
            return;
        }
        YamlConfiguration y = yaml(id);
        Menu menu = Menu.of(msg.get("editor.recompensas")).rows(4);
        Object[][] fields = {
                {"xp", Material.EXPERIENCE_BOTTLE, 50.0, 500.0, 0.0, 1e9, (double) def.rewards().xp()},
                {"col", Material.GOLD_NUGGET, 50.0, 500.0, 0.0, 1e9, (double) def.rewards().col()},
                {"dano_minimo_pct", Material.IRON_SWORD, 1.0, 5.0, 0.0, 100.0, def.rewards().minDamagePercent()},
        };
        for (Object[] f : fields) {
            String key = (String) f[0];
            row(menu, (Material) f[1], msg.raw("editor.campos." + key), (Double) f[6], (Double) f[2], (Double) f[3], (Double) f[4], (Double) f[5],
                    (pl, v) -> {
                        y.set(id + ".recompensas." + key, tidy(v));
                        save(pl, id, y, "recompensas." + key, tidy(v));
                        rewards(pl, id);
                    });
        }
        menu.button(Menu.Button.of(icon(Material.CHEST), msg.get("editor.itens"), pl -> drops(pl, id)).closing());
        menu.button(back(pl -> open(pl, id)));
        AincradCore.get().menus().open(p, menu);
    }

    /** Baú para arrastar os itens: as cinco fileiras de cima são os drops; o espaço do meio de baixo, o Last Attack. */
    private void drops(Player p, String id) {
        BossDef def = fights.registry().get(id);
        if (def == null) {
            return;
        }
        DropsHolder holder = new DropsHolder(id);
        Inventory inv = Bukkit.createInventory(holder, 54, msg.get("editor.itens_titulo", Messages.vars("chefe", def.name())));
        holder.inventory = inv;
        int slot = 0;
        for (BossDef.Drop d : def.rewards().items()) {
            int left = d.amount();
            while (left > 0 && slot < 45) {
                ItemStack it = hooks.item(d.item(), left);
                if (it == null) {
                    break;
                }
                inv.setItem(slot++, it);
                left -= Math.max(1, it.getAmount());
            }
        }
        ItemStack pane = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(msg.get("editor.itens_borda")).build();
        for (int s = 45; s < 54; s++) {
            if (s != LAST_ATTACK_SLOT) {
                inv.setItem(s, pane);
            }
        }
        if (def.rewards().lastAttack() != null) {
            inv.setItem(LAST_ATTACK_SLOT, hooks.item(def.rewards().lastAttack(), 1));
        }
        // No próximo tick: o menu anterior ainda está fechando.
        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(inv));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof DropsHolder
                && event.getRawSlot() >= 45 && event.getRawSlot() < 54 && event.getRawSlot() != LAST_ATTACK_SLOT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof DropsHolder) {
            for (int s : event.getRawSlots()) {
                if (s >= 45 && s < 54 && s != LAST_ATTACK_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /** Ao fechar o baú: o que ficou nele vira as recompensas do chefe. Os itens não voltam ao admin (são cópias de modelo). */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof DropsHolder holder) || !(event.getPlayer() instanceof Player p)) {
            return;
        }
        String id = holder.bossId;
        if (file(id) == null) {
            return;
        }
        // Itens iguais em espaços diferentes somam.
        Map<String, Integer> total = new LinkedHashMap<>();
        for (int s = 0; s < 45; s++) {
            ItemStack it = event.getInventory().getItem(s);
            if (it != null && !it.getType().isAir()) {
                total.merge(hooks.spec(it), it.getAmount(), Integer::sum);
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        total.forEach((spec, amount) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", spec);
            m.put("quantidade", amount);
            items.add(m);
        });
        ItemStack last = event.getInventory().getItem(LAST_ATTACK_SLOT);
        YamlConfiguration y = yaml(id);
        y.set(id + ".recompensas.itens", items);
        y.set(id + ".recompensas.last_attack", last == null || last.getType().isAir() ? null : hooks.spec(last));
        save(p, id, y, "recompensas.itens", items.size() + " tipo(s) de item");
        msg.send(p, "editor.itens_salvos", Messages.vars("n", items.size(), "id", id.toLowerCase(Locale.ROOT)));
    }
}
