package com.aincrad.rpg.command;

import com.aincrad.core.AincradCore;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.region.Region;
import com.aincrad.core.region.RegionManager;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.rpg.mob.SpawnService;
import com.aincrad.rpg.model.SpawnEdit;
import com.aincrad.rpg.model.SpawnRules;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code /aincrad spawn}: marcar uma área, dizer quais mobs nascem nela e com que level, sem
 * abrir o spawns.yml. Sem argumentos, abre o menu. Cada mudança é gravada no arquivo e vale na hora.
 */
public final class SpawnCommand implements SubCommand {

    // O menu cobre tudo o que os comandos fazem; os comandos ficam para quem prefere digitar.
    private static final List<String> SUBS = List.of("varinha", "criar", "area", "mob", "level", "peso", "nome", "periodo",
            "remover", "maximo", "ver", "aqui", "lista", "menu");

    private final Plugin plugin;
    private final Messages msg;
    private final SpawnService spawns;
    private final ConfigFile file;

    private final com.aincrad.rpg.item.ItemService items;

    public SpawnCommand(Plugin plugin, Messages msg, SpawnService spawns, ConfigFile file, com.aincrad.rpg.item.ItemService items) {
        this.items = items;
        this.plugin = plugin;
        this.msg = msg;
        this.spawns = spawns;
        this.file = file;
    }

    @Override
    public String name() {
        return "spawn";
    }

    @Override
    public String permission() {
        return "aincrad.rpg.admin";
    }

    @Override
    public String usage() {
        return "spawn [varinha|criar|mob|level|peso|nome|periodo|remover|maximo|ver|aqui|lista]";
    }

    @Override
    public Component description() {
        return msg.get("spawn.descricao");
    }

    // ------------------------------------------------------------------ área nova, pelo menu

    /** Um nome livre para uma área nova: area_1, area_2... */
    private String freeName() {
        for (int n = 1; ; n++) {
            String name = "area_" + n;
            if (AincradCore.get().regions().get(name) == null && SpawnEdit.regionKey(current(), name) == null) {
                return name;
            }
        }
    }

    /**
     * Botão "Marcar área nova". Sem os dois cantos marcados, entrega a varinha. Com eles, abre a
     * escolha do nome: as áreas do arquivo que ainda não foram marcadas no mapa, ou um nome novo.
     */
    private void newArea(Player p, boolean exact) {
        RegionManager.Selection sel = AincradCore.get().regions().selection(p);
        if (!sel.complete() || !sel.a().getWorld().equals(sel.b().getWorld())) {
            p.closeInventory();
            p.getInventory().addItem(AincradCore.get().regions().wand());
            msg.send(p, "spawn.menu_sem_selecao");
            return;
        }
        Menu menu = Menu.of(msg.get("spawn.menu.titulo_nome")).rows(6);
        String fresh = freeName();
        menu.item(button(Material.LIME_CONCRETE, "nome_novo", pl -> {
            create(pl, fresh, false, exact);
            openRegion(pl, fresh);
        }, "regiao", fresh));
        // Um item renomeado na bigorna (uma etiqueta, por exemplo) dá o nome da área.
        ItemStack hand = p.getInventory().getItemInMainHand();
        Component custom = hand.hasItemMeta() ? hand.getItemMeta().displayName() : null;
        if (custom != null) {
            String typed = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(custom)
                    .toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
            if (Region.validName(typed) && AincradCore.get().regions().get(typed) == null) {
                menu.item(button(Material.NAME_TAG, "nome_da_etiqueta", pl -> {
                    create(pl, typed, false, exact);
                    openRegion(pl, typed);
                }, "regiao", typed));
            }
        }
        // Áreas que já têm mobs no arquivo (um pacote de andar, por exemplo) mas não estão no mapa.
        var sec = current().getConfigurationSection("regioes");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                String id = k.toLowerCase(Locale.ROOT);
                if (AincradCore.get().regions().get(id) == null) {
                    menu.item(button(Material.FILLED_MAP, "nome_do_arquivo", pl -> {
                        create(pl, id, true, exact);
                        openRegion(pl, id);
                    }, "regiao", id));
                }
            }
        }
        menu.button(button(Material.ARROW, "voltar", this::openRegions));
        open(p, menu);
    }

    /** Escolher um level clicando no número. {@code from} é o menor que aparece. */
    private void pickLevel(Player p, String titleKey, int from, Consumer<Integer> chosen, Runnable back) {
        Menu menu = Menu.of(msg.get("spawn.menu." + titleKey)).rows(6);
        for (int lv = from; lv <= 100; lv++) {
            int level = lv;
            menu.item(Menu.Button.of(ItemBuilder.of(Material.EXPERIENCE_BOTTLE).amount(Math.min(64, lv)).hideAll().build(),
                    msg.get("spawn.menu.level_numero", Messages.vars("level", lv)), pl -> chosen.accept(level)));
        }
        menu.button(button(Material.ARROW, "voltar", pl -> back.run()));
        open(p, menu);
    }

    // ------------------------------------------------------------------ gravar

    /** Lê o spawns.yml do disco, aplica a mudança, grava e recarrega os mobs. */
    private boolean edit(CommandSender who, java.util.function.Predicate<YamlConfiguration> change) {
        File f = new File(plugin.getDataFolder(), "spawns.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (!change.test(y)) {
            return false;
        }
        try {
            y.save(f);
        } catch (IOException e) {
            msg.send(who, "spawn.erro_gravar", Messages.vars("erro", String.valueOf(e.getMessage())));
            return false;
        }
        file.reload();
        spawns.reload(file);
        Bukkit.getPluginManager().callEvent(new com.aincrad.core.AincradAdminActionEvent(who, "rpg", "rpg.spawn", "spawns.yml alterado"));
        return true;
    }

    private YamlConfiguration current() {
        return file.yaml();
    }

    private static Integer number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ comando

    @Override
    public void execute(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        switch (sub) {
            case "menu" -> {
                if (p == null) {
                    msg.send(sender, "spawn.uso");
                } else {
                    openRegions(p);
                }
            }
            case "varinha" -> {
                if (p == null) {
                    msg.send(sender, "geral.so_jogador");
                    return;
                }
                p.getInventory().addItem(AincradCore.get().regions().wand());
                msg.send(p, "spawn.varinha");
            }
            case "criar", "area" -> {
                if (p == null) {
                    msg.send(sender, "geral.so_jogador");
                } else if (args.length < 2) {
                    msg.send(p, "spawn.uso_criar");
                } else {
                    create(p, args[1].toLowerCase(Locale.ROOT), sub.equals("area"), args.length > 2 && args[2].equalsIgnoreCase("exato"));
                }
            }
            case "mob" -> addMob(sender, args);
            case "level", "peso", "nome", "periodo" -> field(sender, sub, args);
            case "remover" -> remove(sender, args);
            case "maximo" -> {
                Integer n = args.length > 2 ? number(args[2]) : null;
                if (n == null || n < 1 || n > 200) {
                    msg.send(sender, "spawn.uso_maximo");
                } else if (edit(sender, y -> SpawnEdit.setRegion(y, args[1], "maximo", n))) {
                    msg.send(sender, "spawn.maximo", Messages.vars("regiao", args[1].toLowerCase(Locale.ROOT), "maximo", n));
                } else {
                    msg.send(sender, "spawn.sem_regiao", Messages.vars("regiao", args[1]));
                }
            }
            case "ver" -> {
                Region r = args.length > 1 ? AincradCore.get().regions().get(args[1].toLowerCase(Locale.ROOT)) : null;
                if (p == null || r == null) {
                    msg.send(sender, "spawn.sem_regiao", Messages.vars("regiao", args.length > 1 ? args[1] : "?"));
                } else {
                    outline(p, r);
                    msg.send(p, "spawn.vendo", Messages.vars("regiao", r.name()));
                }
            }
            case "aqui" -> {
                if (p == null) {
                    msg.send(sender, "geral.so_jogador");
                    return;
                }
                List<String> here = new ArrayList<>();
                for (Region r : AincradCore.get().regions().at(p.getLocation())) {
                    here.add(spawns.areas().containsKey(r.name()) ? r.name() + " ✔" : r.name());
                }
                msg.send(p, "spawn.aqui", Messages.vars("regioes", here.isEmpty() ? "—" : String.join(", ", here)));
            }
            case "lista" -> {
                if (spawns.areas().isEmpty()) {
                    msg.send(sender, "spawn.nenhuma");
                }
                for (SpawnRules.Area a : spawns.areas().values()) {
                    msg.sendRaw(sender, "spawn.linha_regiao", Messages.vars("regiao", a.region(), "vivos", spawns.alive(a.region()),
                            "maximo", a.max(), "mobs", a.mobs().size()));
                }
            }
            default -> msg.send(sender, "spawn.uso");
        }
    }

    /**
     * Cria a região a partir da seleção da varinha e a põe no spawns.yml. Com {@code redo}, só
     * refaz os limites de uma região que já existe.
     */
    private void create(Player p, String name, boolean redo, boolean exact) {
        RegionManager regions = AincradCore.get().regions();
        if (!Region.validName(name) || name.contains(".")) {
            msg.send(p, "spawn.nome_invalido");
            return;
        }
        Region old = regions.get(name);
        if (old == null || redo) {
            RegionManager.Selection s = regions.selection(p);
            if (!s.complete() || !s.a().getWorld().equals(s.b().getWorld())) {
                msg.send(p, "spawn.sem_selecao");
                return;
            }
            Location a = s.a();
            Location b = s.b();
            World w = a.getWorld();
            // Dois cliques no chão marcam só o contorno: a área vai do fundo ao topo do mundo,
            // a não ser que se peça "exato" (para separar os andares de um labirinto).
            int y1 = exact ? a.getBlockY() : w.getMinHeight();
            int y2 = exact ? b.getBlockY() : w.getMaxHeight() - 1;
            Region r = Region.of(name, w.getName(), a.getBlockX(), y1, a.getBlockZ(), b.getBlockX(), y2, b.getBlockZ(), "admin");
            regions.save(r);
            msg.send(p, "spawn.area_marcada", Messages.vars("regiao", name,
                    "largura", r.maxX() - r.minX() + 1, "comprimento", r.maxZ() - r.minZ() + 1, "altura", r.maxY() - r.minY() + 1));
            outline(p, r);
        }
        if (edit(p, y -> SpawnEdit.addRegion(y, name))) {
            msg.send(p, "spawn.criada", Messages.vars("regiao", name));
        } else if (!redo) {
            msg.send(p, "spawn.ja_existe", Messages.vars("regiao", name));
        }
    }

    /** /aincrad spawn mob &lt;região&gt; &lt;mob&gt; &lt;level&gt; [peso] */
    private void addMob(CommandSender sender, String[] args) {
        SpawnRules.MobRef ref = args.length > 2 ? SpawnRules.MobRef.parse(args[2]) : null;
        int[] lv = args.length > 3 ? SpawnRules.levels(args[3]) : null;
        Integer weight = args.length > 4 ? number(args[4]) : Integer.valueOf(1);
        if (ref == null || lv == null || lv[0] < 1 || weight == null || weight < 1) {
            msg.send(sender, "spawn.uso_mob");
            return;
        }
        if (ref.kind() == SpawnRules.Kind.VANILLA && !vanilla(ref.id())) {
            msg.send(sender, "spawn.mob_invalido", Messages.vars("mob", ref.id()));
            return;
        }
        String region = args[1].toLowerCase(Locale.ROOT);
        if (edit(sender, y -> SpawnEdit.addMob(y, region, ref.text(), lv[0], lv[1], weight) >= 0)) {
            msg.send(sender, "spawn.mob_posto", Messages.vars("regiao", region, "mob", ref.text(),
                    "level", String.valueOf(SpawnEdit.levelValue(lv[0], lv[1]))));
        } else {
            msg.send(sender, "spawn.sem_regiao", Messages.vars("regiao", region));
        }
    }

    private static boolean vanilla(String id) {
        try {
            EntityType t = EntityType.valueOf(id);
            return t.isSpawnable() && t.isAlive();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** /aincrad spawn level|peso|nome|periodo &lt;região&gt; &lt;mob&gt; &lt;valor&gt; */
    private void field(CommandSender sender, String sub, String[] args) {
        if (args.length < 4) {
            msg.send(sender, "spawn.uso_campo");
            return;
        }
        String region = args[1].toLowerCase(Locale.ROOT);
        int index = SpawnEdit.find(current(), region, args[2]);
        if (index < 0) {
            msg.send(sender, "spawn.sem_mob", Messages.vars("regiao", region, "mob", args[2]));
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        Object value;
        String field = sub;
        switch (sub) {
            case "level" -> {
                int[] lv = SpawnRules.levels(args[3]);
                if (lv == null || lv[0] < 1) {
                    msg.send(sender, "spawn.uso_campo");
                    return;
                }
                value = SpawnEdit.levelValue(lv[0], lv[1]);
            }
            case "peso" -> {
                Integer n = number(args[3]);
                if (n == null || n < 1) {
                    msg.send(sender, "spawn.uso_campo");
                    return;
                }
                value = n;
            }
            case "periodo" -> {
                String v = args[3].toLowerCase(Locale.ROOT);
                if (!List.of("dia", "noite", "sempre").contains(v)) {
                    msg.send(sender, "spawn.uso_campo");
                    return;
                }
                value = v.equals("sempre") ? null : v;
            }
            default -> value = text.equalsIgnoreCase("nenhum") ? null : text;
        }
        if (edit(sender, y -> SpawnEdit.set(y, region, index, field, value))) {
            msg.send(sender, "spawn.mudou", Messages.vars("regiao", region, "mob", args[2], "campo", sub, "valor", value == null ? "—" : value.toString()));
        }
    }

    /** /aincrad spawn remover &lt;região&gt; [mob] */
    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg.send(sender, "spawn.uso");
            return;
        }
        String region = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            // A área marcada continua existindo no core; só deixa de ter mobs.
            msg.send(sender, edit(sender, y -> SpawnEdit.removeRegion(y, region)) ? "spawn.removida" : "spawn.sem_regiao",
                    Messages.vars("regiao", region));
            return;
        }
        int index = SpawnEdit.find(current(), region, args[2]);
        if (index >= 0 && edit(sender, y -> SpawnEdit.removeMob(y, region, index))) {
            msg.send(sender, "spawn.mob_tirado", Messages.vars("regiao", region, "mob", args[2]));
        } else {
            msg.send(sender, "spawn.sem_mob", Messages.vars("regiao", region, "mob", args[2]));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) {
            out.addAll(SUBS);
        } else if (args.length == 2 && !List.of("varinha", "aqui", "lista", "menu", "criar").contains(sub)) {
            out.addAll(spawns.areas().keySet());
        } else if (args.length == 3 && sub.equals("mob")) {
            for (EntityType t : EntityType.values()) {
                if (t.isSpawnable() && t.isAlive()) {
                    out.add(t.name());
                }
            }
        } else if (args.length == 3 && List.of("level", "peso", "nome", "periodo", "remover").contains(sub)) {
            for (Map<String, Object> l : SpawnEdit.lines(current(), args[1])) {
                out.add(SpawnEdit.key(l));
            }
        } else if (args.length == 4 && sub.equals("periodo")) {
            out.addAll(List.of("dia", "noite", "sempre"));
        } else if (args.length == 3 && sub.equals("criar")) {
            out.add("exato");
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    // ------------------------------------------------------------------ contorno

    /** Mostra, só para o jogador e por 15 s, as bordas da área na altura dele. */
    private void outline(Player p, Region r) {
        new BukkitRunnable() {
            private int runs;

            @Override
            public void run() {
                if (++runs > 30 || !p.isOnline() || !p.getWorld().getName().equals(r.world())) {
                    cancel();
                    return;
                }
                Location at = p.getLocation();
                double y = Math.max(r.minY(), Math.min(r.maxY() + 1, at.getY() + 1));
                for (int x = r.minX(); x <= r.maxX() + 1; x++) {
                    dot(p, at, x, y, r.minZ());
                    dot(p, at, x, y, r.maxZ() + 1);
                }
                for (int z = r.minZ(); z <= r.maxZ() + 1; z++) {
                    dot(p, at, r.minX(), y, z);
                    dot(p, at, r.maxX() + 1, y, z);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private static void dot(Player p, Location at, double x, double y, double z) {
        double dx = x - at.getX();
        double dz = z - at.getZ();
        if (dx * dx + dz * dz <= 48 * 48) {
            p.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ menus

    private static ItemStack icon(Material m) {
        return ItemBuilder.of(m).hideAll().build();
    }

    private Menu.Button button(Material m, String key, Consumer<Player> action, Object... vars) {
        return Menu.Button.of(icon(m), msg.get("spawn.menu." + key, Messages.vars(vars)), action);
    }

    private void open(Player p, Menu menu) {
        AincradCore.get().menus().open(p, menu);
    }

    private static Material egg(String mob) {
        Material m = Material.matchMaterial(mob + "_SPAWN_EGG");
        return m != null && m.isItem() ? m : Material.NAME_TAG;
    }

    /** As regiões com mobs. */
    private void openRegions(Player p) {
        Menu menu = Menu.of(msg.get("spawn.menu.titulo")).rows(6);
        for (SpawnRules.Area a : spawns.areas().values()) {
            menu.item(Menu.Button.of(icon(Material.GRASS_BLOCK), msg.get("spawn.menu.regiao", Messages.vars("regiao", a.region())),
                    pl -> openRegion(pl, a.region())).lore(msg.list("spawn.menu.regiao_lore",
                    Messages.vars("vivos", spawns.alive(a.region()), "maximo", a.max(), "mobs", a.mobs().size()))));
        }
        // Regiões que estão no arquivo mas ainda sem mob nenhum não entram em areas().
        var sec = current().getConfigurationSection("regioes");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                if (!spawns.areas().containsKey(k.toLowerCase(Locale.ROOT))) {
                    menu.item(Menu.Button.of(icon(Material.COARSE_DIRT), msg.get("spawn.menu.regiao_vazia", Messages.vars("regiao", k)),
                            pl -> openRegion(pl, k)));
                }
            }
        }
        menu.button(button(Material.GRASS_BLOCK, "nova_area", pl -> newArea(pl, false)));
        menu.button(button(Material.LADDER, "nova_area_exata", pl -> newArea(pl, true)));
        menu.button(button(Material.BLAZE_ROD, "varinha", pl -> {
            pl.getInventory().addItem(AincradCore.get().regions().wand());
            msg.send(pl, "spawn.varinha");
        }).closing());
        open(p, menu);
    }

    /** Uma região: os mobs dela e os botões de máximo, contorno e excluir. */
    private void openRegion(Player p, String region) {
        YamlConfiguration y = current();
        String key = SpawnEdit.regionKey(y, region);
        if (key == null) {
            openRegions(p);
            return;
        }
        int max = y.getInt("regioes." + key + ".maximo", 10);
        Menu menu = Menu.of(msg.get("spawn.menu.titulo_regiao", Messages.vars("regiao", key))).rows(6);
        List<Map<String, Object>> lines = SpawnEdit.lines(y, key);
        for (int i = 0; i < lines.size(); i++) {
            Map<String, Object> l = lines.get(i);
            int index = i;
            int[] lv = SpawnEdit.level(l);
            menu.item(Menu.Button.of(icon(egg(String.valueOf(l.get("mob")))),
                    msg.get("spawn.menu.mob", Messages.vars("mob", SpawnEdit.key(l))), pl -> openMob(pl, key, index))
                    .lore(msg.list("spawn.menu.mob_lore", Messages.vars("tipo", String.valueOf(l.get("mob")),
                            "level", String.valueOf(SpawnEdit.levelValue(lv[0], lv[1])), "peso", String.valueOf(l.getOrDefault("peso", 1)),
                            "periodo", String.valueOf(l.getOrDefault("periodo", "sempre"))))));
        }
        menu.button(button(Material.EGG, "novo_mob", pl -> openPicker(pl, key)));
        menu.button(button(Material.RED_DYE, "maximo_menos", pl -> change(pl, key, yy -> SpawnEdit.setRegion(yy, key, "maximo", Math.max(1, max - 2))), "maximo", max));
        menu.button(button(Material.LIME_DYE, "maximo_mais", pl -> change(pl, key, yy -> SpawnEdit.setRegion(yy, key, "maximo", Math.min(200, max + 2))), "maximo", max));
        menu.button(button(Material.END_ROD, "ver", pl -> {
            Region r = AincradCore.get().regions().get(key.toLowerCase(Locale.ROOT));
            if (r == null) {
                msg.send(pl, "spawn.sem_area", Messages.vars("regiao", key));
            } else {
                outline(pl, r);
                msg.send(pl, "spawn.vendo", Messages.vars("regiao", key));
            }
        }).closing());
        menu.button(button(Material.BLAZE_ROD, "refazer", pl -> {
            create(pl, key.toLowerCase(Locale.ROOT), true, false);
            openRegion(pl, key);
        }));
        menu.button(button(Material.BARRIER, "excluir", pl -> AincradCore.get().menus().confirm(pl,
                msg.get("spawn.menu.confirmar_excluir", Messages.vars("regiao", key)), yes -> {
                    edit(yes, yy -> SpawnEdit.removeRegion(yy, key));
                    openRegions(yes);
                }, no -> openRegion(no, key))));
        menu.button(button(Material.ARROW, "voltar", this::openRegions));
        open(p, menu);
    }

    private void change(Player p, String region, java.util.function.Predicate<YamlConfiguration> change) {
        edit(p, change);
        openRegion(p, region);
    }

    /** Escolher o tipo de um mob novo. */
    private void openPicker(Player p, String region) {
        Menu menu = Menu.of(msg.get("spawn.menu.titulo_escolher")).rows(6);
        // Os mobs do MythicMobs vêm primeiro: é por eles que entram os modelos 3D.
        for (String mm : com.aincrad.rpg.mob.Packs.mythicMobs()) {
            menu.item(button(Material.DRAGON_HEAD, "mob_mythic", pl -> {
                int[] index = {-1};
                edit(pl, y -> (index[0] = SpawnEdit.addMob(y, region, "mythic:" + mm, 1, 1, 1)) >= 0);
                if (index[0] >= 0) {
                    openMob(pl, region, index[0]);
                } else {
                    openRegion(pl, region);
                }
            }, "mob", mm));
        }
        for (EntityType t : EntityType.values()) {
            if (!t.isSpawnable() || !t.isAlive() || egg(t.name()) == Material.NAME_TAG) {
                continue;
            }
            menu.item(Menu.Button.of(icon(egg(t.name())), Component.translatable(t.translationKey()), pl -> {
                int[] index = {-1};
                edit(pl, y -> (index[0] = SpawnEdit.addMob(y, region, t.name(), 1, 1, 1)) >= 0);
                if (index[0] >= 0) {
                    openMob(pl, region, index[0]);
                } else {
                    openRegion(pl, region);
                }
            }));
        }
        menu.button(button(Material.ARROW, "voltar", pl -> openRegion(pl, region)));
        open(p, menu);
    }

    /** Um mob da região: level, peso, período, testar e tirar. */
    private void openMob(Player p, String region, int index) {
        List<Map<String, Object>> lines = SpawnEdit.lines(current(), region);
        if (index < 0 || index >= lines.size()) {
            openRegion(p, region);
            return;
        }
        Map<String, Object> l = lines.get(index);
        int[] lv = SpawnEdit.level(l);
        int weight = l.get("peso") instanceof Number n ? n.intValue() : 1;
        String period = String.valueOf(l.getOrDefault("periodo", "sempre"));
        String mob = String.valueOf(l.get("mob"));
        Menu menu = Menu.of(msg.get("spawn.menu.titulo_mob", Messages.vars("mob", SpawnEdit.key(l)))).rows(3);
        menu.item(Menu.Button.of(icon(egg(mob)), msg.get("spawn.menu.mob", Messages.vars("mob", SpawnEdit.key(l))), pl -> { })
                .lore(msg.list("spawn.menu.mob_lore", Messages.vars("tipo", mob, "level", String.valueOf(SpawnEdit.levelValue(lv[0], lv[1])),
                        "peso", weight, "periodo", period))));
        level(menu, region, index, lv, "min_menos", Material.RED_DYE, -1, 0);
        level(menu, region, index, lv, "min_mais", Material.LIME_DYE, 1, 0);
        level(menu, region, index, lv, "max_menos", Material.RED_DYE, 0, -1);
        level(menu, region, index, lv, "max_mais", Material.LIME_DYE, 0, 1);
        menu.item(button(Material.IRON_NUGGET, "peso_menos", pl -> mobChange(pl, region, index, "peso", Math.max(1, weight - 1)), "peso", weight));
        menu.item(button(Material.GOLD_NUGGET, "peso_mais", pl -> mobChange(pl, region, index, "peso", Math.min(100, weight + 1)), "peso", weight));
        // Escolher o level clicando: primeiro o mínimo, depois o máximo.
        menu.item(button(Material.EXPERIENCE_BOTTLE, "escolher_level", pl -> pickLevel(pl, "titulo_level_min", 1,
                min -> pickLevel(pl, "titulo_level_max", min,
                        max -> mobChange(pl, region, index, "level", SpawnEdit.levelValue(min, max)),
                        () -> openMob(pl, region, index)),
                () -> openMob(pl, region, index)), "level", String.valueOf(SpawnEdit.levelValue(lv[0], lv[1]))));
        String next = period.equals("sempre") ? "dia" : period.equals("dia") ? "noite" : "sempre";
        menu.item(button(Material.CLOCK, "periodo", pl -> mobChange(pl, region, index, "periodo", next.equals("sempre") ? null : next), "periodo", period));
        if (mob.equalsIgnoreCase("WOLF")) {
            boolean angry = Boolean.TRUE.equals(l.get("agressivo"));
            menu.item(button(Material.BONE, angry ? "agressivo_sim" : "agressivo_nao", pl -> mobChange(pl, region, index, "agressivo", angry ? null : Boolean.TRUE)));
        }
        boolean mythic = mob.toLowerCase(Locale.ROOT).startsWith("mythic:");
        menu.item(button(Material.CHEST, "drops", pl -> openDrops(pl, region, index), "qtd", SpawnEdit.drops(l).size()));
        if (!mythic) {
            // Nome, equipamento e modelo de um mob do MythicMobs vêm do arquivo dele.
            menu.item(button(Material.NAME_TAG, "nome_da_mao", pl -> {
                ItemStack hand = pl.getInventory().getItemInMainHand();
                Component custom = hand.hasItemMeta() ? hand.getItemMeta().displayName() : null;
                if (custom == null) {
                    msg.send(pl, "spawn.sem_nome_na_mao");
                    openMob(pl, region, index);
                } else {
                    mobChange(pl, region, index, "nome", net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(custom));
                }
            }, "nome", String.valueOf(l.getOrDefault("nome", "—"))));
            if (l.containsKey("nome")) {
                menu.item(button(Material.SHEARS, "nome_tirar", pl -> mobChange(pl, region, index, "nome", null)));
            }
            menu.item(button(Material.IRON_CHESTPLATE, "equipamento", pl -> openEquipment(pl, region, index), "qtd", SpawnEdit.equipment(l).size()));
            menu.item(button(Material.ARMOR_STAND, "modelo", pl -> openModels(pl, region, index), "modelo", String.valueOf(l.getOrDefault("modelo", "—"))));
        }
        menu.button(button(Material.SPAWNER, "testar", pl -> {
            SpawnRules.MobRef ref = SpawnRules.MobRef.parse(mob);
            if (ref != null) {
                spawns.spawn(new SpawnRules.Entry(ref, 1, lv[0], lv[1], null, null, null), pl.getLocation(), "/aincrad spawn");
            }
        }).closing());
        menu.button(button(Material.BARRIER, "tirar", pl -> {
            edit(pl, y -> SpawnEdit.removeMob(y, region, index));
            openRegion(pl, region);
        }));
        menu.button(button(Material.ARROW, "voltar", pl -> openRegion(pl, region)));
        open(p, menu);
    }

    // ------------------------------------------------------------------ item na mão

    /** Como o item da mão é escrito no arquivo: rpg:&lt;id&gt;, ia:&lt;namespace:id&gt; ou o material. Null = mão vazia. */
    private String specOf(ItemStack hand) {
        if (hand == null || hand.getType().isAir()) {
            return null;
        }
        String rpgId = items.id(hand);
        if (rpgId != null) {
            return "rpg:" + rpgId;
        }
        String ia = com.aincrad.rpg.mob.Packs.itemsAdderId(hand);
        return ia != null ? "ia:" + ia : hand.getType().name();
    }

    /** O ícone de um item do arquivo; papel se o item não existe (plugin desligado, id errado). */
    private ItemStack iconOf(String spec) {
        ItemStack it = spawns.itemOf(spec, 1);
        return it == null || it.getType().isAir() ? new ItemStack(Material.PAPER) : it.clone();
    }

    // ------------------------------------------------------------------ equipamento

    private static final Material[] SLOT_ICONS = {Material.IRON_SWORD, Material.SHIELD, Material.IRON_HELMET,
            Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS};

    /** O equipamento do mob: clicar num espaço põe nele o item que o admin tem na mão (mão vazia tira). */
    private void openEquipment(Player p, String region, int index) {
        List<Map<String, Object>> lines = SpawnEdit.lines(current(), region);
        if (index < 0 || index >= lines.size()) {
            openRegion(p, region);
            return;
        }
        Map<String, Object> eq = SpawnEdit.equipment(lines.get(index));
        Menu menu = Menu.of(msg.get("spawn.menu.titulo_equipamento")).rows(3);
        for (int i = 0; i < SpawnRules.SLOTS.size(); i++) {
            String slot = SpawnRules.SLOTS.get(i);
            Object now = eq.get(slot);
            ItemStack icon = now == null ? icon(SLOT_ICONS[i]) : iconOf(now.toString());
            menu.item(Menu.Button.of(icon, msg.get("spawn.menu.espaco_" + slot, Messages.vars("item", now == null ? "—" : now.toString())), pl -> {
                String spec = specOf(pl.getInventory().getItemInMainHand());
                Map<String, Object> next = SpawnEdit.equipment(SpawnEdit.lines(current(), region).get(index));
                if (spec == null) {
                    next.remove(slot);
                } else {
                    next.put(slot, spec);
                }
                edit(pl, y -> SpawnEdit.set(y, region, index, "equipamento", next.isEmpty() ? null : next));
                openEquipment(pl, region, index);
            }).lore(msg.list("spawn.menu.espaco_lore")));
        }
        menu.button(button(Material.ARMOR_STAND, "copiar_de_mim", pl -> {
            org.bukkit.inventory.PlayerInventory inv = pl.getInventory();
            ItemStack[] worn = {inv.getItemInMainHand(), inv.getItemInOffHand(), inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
            Map<String, Object> next = new java.util.LinkedHashMap<>();
            for (int i = 0; i < worn.length; i++) {
                String spec = specOf(worn[i]);
                if (spec != null) {
                    next.put(SpawnRules.SLOTS.get(i), spec);
                }
            }
            edit(pl, y -> SpawnEdit.set(y, region, index, "equipamento", next.isEmpty() ? null : next));
            openEquipment(pl, region, index);
        }));
        menu.button(button(Material.ARROW, "voltar", pl -> openMob(pl, region, index)));
        open(p, menu);
    }

    // ------------------------------------------------------------------ modelo (ModelEngine)

    private void openModels(Player p, String region, int index) {
        Menu menu = Menu.of(msg.get("spawn.menu.titulo_modelo")).rows(6);
        List<String> models = com.aincrad.rpg.mob.Packs.models();
        for (String model : models) {
            menu.item(button(Material.ARMOR_STAND, "modelo_item", pl -> mobChange(pl, region, index, "modelo", model), "modelo", model));
        }
        if (models.isEmpty()) {
            menu.item(button(Material.BARRIER, "modelo_nenhum_carregado", pl -> { }));
        }
        menu.button(button(Material.SHEARS, "modelo_tirar", pl -> mobChange(pl, region, index, "modelo", null)));
        menu.button(button(Material.ARROW, "voltar", pl -> openMob(pl, region, index)));
        open(p, menu);
    }

    // ------------------------------------------------------------------ drops

    private static final double[] CHANCES = {0.01, 0.02, 0.05, 0.1, 0.25, 0.5, 0.75, 1.0};

    private static String percent(double chance) {
        double pct = chance * 100;
        return (pct == Math.rint(pct) ? String.valueOf((long) pct) : String.valueOf(pct)) + "%";
    }

    private void saveDrops(Player p, String region, int index, List<Map<String, Object>> drops) {
        edit(p, y -> SpawnEdit.set(y, region, index, "drops", drops.isEmpty() ? null : drops));
    }

    /** Os drops do mob. "Adicionar" usa o item que o admin tem na mão. */
    private void openDrops(Player p, String region, int index) {
        List<Map<String, Object>> lines = SpawnEdit.lines(current(), region);
        if (index < 0 || index >= lines.size()) {
            openRegion(p, region);
            return;
        }
        List<Map<String, Object>> drops = SpawnEdit.drops(lines.get(index));
        Menu menu = Menu.of(msg.get("spawn.menu.titulo_drops")).rows(4);
        for (int i = 0; i < drops.size(); i++) {
            Map<String, Object> d = drops.get(i);
            int di = i;
            int[] amount = SpawnEdit.amount(d);
            String spec = String.valueOf(d.get("item"));
            menu.item(Menu.Button.of(iconOf(spec), msg.get("spawn.menu.drop", Messages.vars("item", spec)), pl -> openDrop(pl, region, index, di))
                    .lore(msg.list("spawn.menu.drop_lore", Messages.vars("chance", percent(SpawnEdit.chance(d)),
                            "quantidade", String.valueOf(SpawnEdit.levelValue(amount[0], amount[1]))))));
        }
        menu.button(button(Material.HOPPER, "drop_da_mao", pl -> {
            ItemStack hand = pl.getInventory().getItemInMainHand();
            String spec = specOf(hand);
            if (spec == null) {
                msg.send(pl, "spawn.mao_vazia");
                openDrops(pl, region, index);
                return;
            }
            List<Map<String, Object>> next = SpawnEdit.drops(SpawnEdit.lines(current(), region).get(index));
            Map<String, Object> d = new java.util.LinkedHashMap<>();
            d.put("item", spec);
            d.put("chance", 1.0);
            d.put("quantidade", hand.getAmount());
            next.add(d);
            saveDrops(pl, region, index, next);
            openDrop(pl, region, index, next.size() - 1);
        }));
        menu.button(button(Material.ARROW, "voltar", pl -> openMob(pl, region, index)));
        open(p, menu);
    }

    /** Um drop: a chance (clicando na porcentagem), a quantidade e tirar. */
    private void openDrop(Player p, String region, int index, int drop) {
        List<Map<String, Object>> lines = SpawnEdit.lines(current(), region);
        List<Map<String, Object>> drops = index < 0 || index >= lines.size() ? List.of() : SpawnEdit.drops(lines.get(index));
        if (drop < 0 || drop >= drops.size()) {
            openDrops(p, region, index);
            return;
        }
        Map<String, Object> d = drops.get(drop);
        String spec = String.valueOf(d.get("item"));
        double now = SpawnEdit.chance(d);
        int[] amount = SpawnEdit.amount(d);
        Menu menu = Menu.of(msg.get("spawn.menu.titulo_drop", Messages.vars("item", spec))).rows(3);
        for (double c : CHANCES) {
            boolean current = Math.abs(c - now) < 1e-9;
            menu.item(button(current ? Material.LIME_DYE : Material.GRAY_DYE, "drop_chance", pl -> {
                d.put("chance", c);
                saveDrops(pl, region, index, drops);
                openDrop(pl, region, index, drop);
            }, "chance", percent(c)));
        }
        int[][] steps = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        String[] keys = {"qtd_min_menos", "qtd_min_mais", "qtd_max_menos", "qtd_max_mais"};
        for (int i = 0; i < steps.length; i++) {
            int[] to = SpawnEdit.shift(amount, steps[i][0], steps[i][1]);
            to[0] = Math.min(64, to[0]);
            to[1] = Math.min(64, to[1]);
            menu.item(button(steps[i][0] + steps[i][1] < 0 ? Material.RED_DYE : Material.LIME_DYE, keys[i], pl -> {
                d.put("quantidade", SpawnEdit.levelValue(to[0], to[1]));
                saveDrops(pl, region, index, drops);
                openDrop(pl, region, index, drop);
            }, "min", amount[0], "max", amount[1]));
        }
        menu.button(button(Material.BARRIER, "drop_tirar", pl -> {
            drops.remove(drop);
            saveDrops(pl, region, index, drops);
            openDrops(pl, region, index);
        }));
        menu.button(button(Material.ARROW, "voltar", pl -> openDrops(pl, region, index)));
        open(p, menu);
    }

    private void level(Menu menu, String region, int index, int[] lv, String key, Material m, int dMin, int dMax) {
        int[] to = SpawnEdit.shift(lv, dMin, dMax);
        menu.item(button(m, key, pl -> mobChange(pl, region, index, "level", SpawnEdit.levelValue(to[0], to[1])),
                "min", lv[0], "max", lv[1]));
    }

    private void mobChange(Player p, String region, int index, String field, Object value) {
        edit(p, y -> SpawnEdit.set(y, region, index, field, value));
        openMob(p, region, index);
    }
}
