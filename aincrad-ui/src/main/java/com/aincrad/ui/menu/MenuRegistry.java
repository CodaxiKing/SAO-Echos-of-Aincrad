package com.aincrad.ui.menu;

import com.aincrad.ui.api.MenuAction;
import com.aincrad.ui.config.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Grupos e entradas de menu.yml, mais as entradas ligadas por outros plugins via API. */
public final class MenuRegistry {

    public record Group(String id, Component name, String icon, Material material, List<String> entries) {
    }

    public record Entry(String id, Component name, String icon, Material material, String action) {
    }

    /** Entrada registrada por outro plugin. Sobrevive ao /aincrad ui reload. */
    public record ApiEntry(String id, String icon, Component name, String group, MenuAction action) {
    }

    /** Ações embutidas reconhecidas em menu.yml. */
    public static final List<String> BUILTIN = List.of("itens", "inventario", "perfil", "opcoes", "ajuda", "logout");
    public static final String DEFAULT_API_GROUP = "config";

    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, ApiEntry> api = new ConcurrentHashMap<>();
    private final java.util.function.BooleanSupplier deathGame;

    /** @param deathGame com o jogo da morte valendo, a entrada de logout some do menu */
    public MenuRegistry(java.util.function.BooleanSupplier deathGame) {
        this.deathGame = deathGame;
    }

    public void load(YamlConfiguration cfg) {
        groups.clear();
        entries.clear();
        ConfigurationSection es = cfg.getConfigurationSection("entradas");
        if (es != null) {
            for (String id : es.getKeys(false)) {
                ConfigurationSection e = es.getConfigurationSection(id);
                if (e == null) {
                    continue;
                }
                entries.put(id, new Entry(id,
                        Messages.parse(e.getString("nome", id)),
                        e.getString("icone", "under_icons/" + id),
                        material(e.getString("material"), Material.PAPER),
                        e.getString("acao")));
            }
        }
        ConfigurationSection gs = cfg.getConfigurationSection("grupos");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection g = gs.getConfigurationSection(id);
                if (g == null) {
                    continue;
                }
                groups.put(id, new Group(id,
                        Messages.parse(g.getString("nome", id)),
                        g.getString("icone", "icons/" + id),
                        material(g.getString("material"), Material.PAPER),
                        List.copyOf(g.getStringList("entradas"))));
            }
        }
    }

    private static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return m != null && m.isItem() ? m : fallback;
    }

    public List<Group> groups() {
        return List.copyOf(groups.values());
    }

    public Group group(String id) {
        return groups.get(id);
    }

    /** Entradas do grupo: as da config e, no fim, as novas registradas pela API para esse grupo. */
    public List<Entry> entriesOf(String groupId) {
        Group g = groups.get(groupId);
        if (g == null) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        boolean hideLogout = deathGame.getAsBoolean();
        for (String id : g.entries()) {
            Entry e = resolve(id);
            if (e != null && !(hideLogout && "logout".equals(e.action()))) {
                out.add(e);
            }
        }
        for (ApiEntry a : api.values()) {
            if (!entries.containsKey(a.id()) && groupId.equals(a.group()) && !g.entries().contains(a.id())) {
                out.add(resolve(a.id()));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Junta a entrada da config com o que a API registrou para o mesmo id. */
    public Entry resolve(String id) {
        Entry base = entries.get(id);
        ApiEntry a = api.get(id);
        if (base == null && a == null) {
            return null;
        }
        if (a == null) {
            return base;
        }
        Component name = a.name() != null ? a.name() : base != null ? base.name() : Component.text(id);
        String icon = a.icon() != null ? a.icon() : base != null ? base.icon() : "under_icons/" + id;
        Material mat = base != null ? base.material() : Material.PAPER;
        return new Entry(id, name, icon, mat, base != null ? base.action() : null);
    }

    public boolean available(Entry e) {
        if (api.containsKey(e.id())) {
            return true;
        }
        String action = e.action();
        if (action == null || action.isBlank()) {
            return false;
        }
        String a = action.toLowerCase(Locale.ROOT);
        return BUILTIN.contains(a) || (a.startsWith("comando:") && a.length() > "comando:".length());
    }

    public MenuAction apiAction(String id) {
        ApiEntry a = api.get(id);
        return a == null ? null : a.action();
    }

    public void register(ApiEntry entry) {
        api.put(entry.id(), entry);
    }

    public void unregister(String id) {
        api.remove(id);
    }
}
