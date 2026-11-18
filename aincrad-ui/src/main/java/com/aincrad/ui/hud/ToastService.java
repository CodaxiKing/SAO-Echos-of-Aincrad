package com.aincrad.ui.hud;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.flat.FlatOverlay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Avisos na tela: janelinhas que aparecem no canto e somem sozinhas (item recebido, level novo,
 * quest concluída, conquista). Quem está sem a HUD em barras recebe o aviso no action bar.
 *
 * <p>Os avisos de level, quest e conquista vêm dos eventos dos outros plugins Aincrad, ouvidos
 * por reflexão: nenhum deles precisa saber que o AincradUI existe.
 */
public final class ToastService implements Listener {

    private record Entry(String title, String detail, String icon, long until) {
    }

    private final AincradUIPlugin plugin;
    private final Map<UUID, Deque<Entry>> queues = new HashMap<>();

    public ToastService(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    private static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** O jogador vê avisos na tela? (HUD em barras ligada e com o pack) */
    public boolean onScreen(Player p) {
        return plugin.settings().toastsEnabled && plugin.settings().hudEnabled && plugin.settings().hudFlat
                && plugin.prefs().hud(p) && plugin.packStatus().hasPack(p);
    }

    /**
     * Mostra um aviso.
     *
     * @return false se o jogador não vê avisos na tela (quem chamou decide o que fazer)
     */
    public boolean push(Player p, Component title, Component detail) {
        return push(p, title, detail, "quests");
    }

    /** @param icon um dos ícones de {@link FlatOverlay#TOAST_ICONS} (items, skills, quests...) */
    public boolean push(Player p, Component title, Component detail, String icon) {
        if (!onScreen(p)) {
            return false;
        }
        Deque<Entry> q = queues.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        // Vários avisos de uma vez (uma pilha de drops): os mais antigos saem.
        while (q.size() >= 6) {
            q.pollFirst();
        }
        q.addLast(new Entry(plain(title), plain(detail), icon, System.currentTimeMillis() + plugin.settings().toastSeconds * 1000L));
        return true;
    }

    /** Aviso para quem tem a tela; os outros recebem no action bar. */
    public void show(Player p, Component title, Component detail) {
        show(p, title, detail, "quests");
    }

    public void show(Player p, Component title, Component detail, String icon) {
        if (!push(p, title, detail, icon)) {
            p.sendActionBar(detail == null ? title : title.append(Component.text("  ")).append(detail));
        }
    }

    /** Os avisos ainda na tela, do mais novo para o mais antigo. */
    public List<FlatOverlay.Toast> active(Player p) {
        Deque<Entry> q = queues.get(p.getUniqueId());
        if (q == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        q.removeIf(e -> e.until() < now);
        if (q.isEmpty()) {
            queues.remove(p.getUniqueId());
            return List.of();
        }
        List<FlatOverlay.Toast> out = new ArrayList<>();
        Iterator<Entry> it = q.descendingIterator();
        while (it.hasNext() && out.size() < FlatOverlay.MAX_TOASTS) {
            Entry e = it.next();
            out.add(new FlatOverlay.Toast(e.title(), e.detail(), e.icon()));
        }
        return out;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        queues.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ de onde os avisos vêm

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!plugin.settings().toastItems || !(event.getEntity() instanceof Player p) || !onScreen(p)) {
            return;
        }
        ItemStack it = event.getItem().getItemStack();
        Component name = it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                ? it.getItemMeta().displayName() : Component.translatable(it.translationKey());
        // Nome traduzível não vira texto no servidor: usa o do material.
        String text = plain(name);
        if (text.isEmpty() || text.equals(it.translationKey())) {
            text = pretty(it.getType().name());
        }
        push(p, plugin.messages().get("avisos.item"), Component.text(text + (it.getAmount() > 1 ? " x" + it.getAmount() : "")), "items");
    }

    private static String pretty(String material) {
        String s = material.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Liga os avisos aos eventos dos outros plugins Aincrad que estiverem instalados. */
    public void hookPlugins() {
        hook("AincradRPG", "com.aincrad.rpg.api.PlayerLevelUpEvent", (p, e) ->
                show(p, plugin.messages().get("avisos.level"), Component.text(String.valueOf(call(e, "getNewLevel"))), "skills"));
        hook("AincradQuests", "com.aincrad.quests.api.QuestCompleteEvent", (p, e) ->
                show(p, plugin.messages().get("avisos.quest"), Component.text(String.valueOf(call(e, "getName")))));
        hook("AincradReputation", "com.aincrad.reputation.api.AchievementGrantEvent", (p, e) ->
                show(p, plugin.messages().get("avisos.conquista"), Component.text(String.valueOf(call(e, "getName"))), "guild"));
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    private void hook(String pluginName, String eventClass, BiConsumer<Player, Event> handler) {
        Plugin other = Bukkit.getPluginManager().getPlugin(pluginName);
        if (other == null || !other.isEnabled()) {
            return;
        }
        try {
            Class<? extends Event> type = Class.forName(eventClass, true, other.getClass().getClassLoader()).asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.MONITOR, (listener, event) -> {
                if (!type.isInstance(event)) {
                    return;
                }
                Object who = call(event, "getPlayer");
                if (who instanceof Player p && p.isOnline()) {
                    handler.accept(p, event);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            plugin.getLogger().fine("Avisos: não foi possível ouvir " + eventClass + " (" + e + ")");
        }
    }
}
