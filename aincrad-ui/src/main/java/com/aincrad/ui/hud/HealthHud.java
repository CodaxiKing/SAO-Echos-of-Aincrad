package com.aincrad.ui.hud;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.glyph.Glyph;
import com.aincrad.ui.glyph.SpaceFont;
import com.aincrad.ui.glyph.TextWidth;
import com.aincrad.ui.sound.UiSound;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HUD de vida estilo SAO no alto da tela, desenhada no título de uma BossBar.
 * Com o pack: barra de segmentos (glifo hud_segment), deslocada para a esquerda com a fonte
 * aincrad:space, e a barra branca da BossBar fica transparente. Sem o pack: BossBar comum
 * colorida com nome, vida e level.
 */
public final class HealthHud implements Listener {

    private static final TextColor GREEN = TextColor.color(0x5CE65C);
    private static final TextColor YELLOW = TextColor.color(0xF2D43D);
    private static final TextColor RED = TextColor.color(0xE8413C);
    private static final TextColor RED_DARK = TextColor.color(0x5A1414);
    private static final TextColor EMPTY = TextColor.color(0x3A3F47);
    private static final int GAP = 4;

    private final AincradUIPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final Map<UUID, Long> forcedUntil = new HashMap<>();
    private BukkitTask task;
    private long tick;

    public HealthHud(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stopTask();
        int interval = plugin.settings().hudIntervalTicks;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tick += interval;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                update(p);
            }
        }, interval, interval);
    }

    public void stop() {
        stopTask();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            hide(p);
        }
        bars.clear();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Simula vida crítica por alguns segundos (/aincrad ui teste critico). */
    public void forceCritical(Player p, int seconds) {
        forcedUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        update(p);
    }

    public void update(Player p) {
        UiSettings st = plugin.settings();
        if (!st.hudEnabled || !plugin.prefs().hud(p) || p.isDead()) {
            hide(p);
            return;
        }
        AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? Math.max(1.0, maxAttr.getValue()) : 20.0;
        double health = Math.max(0, p.getHealth());

        Long forced = forcedUntil.get(p.getUniqueId());
        if (forced != null) {
            if (forced > System.currentTimeMillis()) {
                health = Math.min(health, max * Math.max(0.01, st.hudRed * 0.6));
            } else {
                forcedUntil.remove(p.getUniqueId());
            }
        }
        double ratio = Math.min(1.0, health / max);
        HealthState state = HealthState.of(ratio, st.hudYellow, st.hudRed);
        boolean blinkOff = state == HealthState.CRITICAL && (tick / st.hudBlinkTicks) % 2 == 1;

        BossBar bar = bars.computeIfAbsent(p.getUniqueId(), id -> {
            BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            p.showBossBar(b);
            return b;
        });

        String numbers = st.hudNumbers ? (int) Math.ceil(health) + "/" + (int) Math.round(max) : "";
        String level = st.hudLevelText + p.getLevel();
        Glyph seg = plugin.glyphs().get("hud_segment");

        if (plugin.packStatus().hasPack(p) && seg != null && seg.present()) {
            bar.name(packTitle(p.getName(), ratio, state, blinkOff, numbers, level, seg, st));
            bar.color(BossBar.Color.WHITE);
            bar.progress(1f);
        } else {
            TextColor c = colorOf(state, blinkOff);
            Component title = Component.text(p.getName(), NamedTextColor.WHITE)
                    .append(Component.text("  " + numbers, c))
                    .append(Component.text("  " + level, NamedTextColor.AQUA));
            bar.name(title);
            bar.color(switch (state) {
                case NORMAL -> BossBar.Color.GREEN;
                case WARNING -> BossBar.Color.YELLOW;
                case CRITICAL -> BossBar.Color.RED;
            });
            bar.progress((float) ratio);
        }

        if (state == HealthState.CRITICAL) {
            long now = System.currentTimeMillis();
            Long last = lastAlert.get(p.getUniqueId());
            if (last == null || now - last >= st.hudAlertSeconds * 1000L) {
                lastAlert.put(p.getUniqueId(), now);
                plugin.sounds().play(p, UiSound.CRITICAL);
            }
        }
    }

    /**
     * O título da BossBar é centralizado. Para a barra começar a {@code hudOffsetX} pixels à esquerda
     * do centro, soma-se no fim um espaço que deixa a largura total em 2 × deslocamento.
     */
    private Component packTitle(String name, double ratio, HealthState state, boolean blinkOff,
                                String numbers, String level, Glyph seg, UiSettings st) {
        int segments = st.hudSegments;
        int filled = HealthState.filledSegments(ratio, segments);
        String segChar = String.valueOf(seg.ch());

        Component content = Component.text(name, NamedTextColor.WHITE)
                .append(SpaceFont.component(GAP))
                .append(Component.text(segChar.repeat(filled)).font(seg.component().font()).color(colorOf(state, blinkOff)))
                .append(Component.text(segChar.repeat(segments - filled)).font(seg.component().font()).color(EMPTY))
                .append(SpaceFont.component(GAP))
                .append(Component.text(numbers, NamedTextColor.WHITE))
                .append(SpaceFont.component(GAP))
                .append(Component.text(level, NamedTextColor.AQUA));

        int width = TextWidth.of(name) + GAP + segments * seg.advance() + GAP + TextWidth.of(numbers) + GAP + TextWidth.of(level);
        return content.append(SpaceFont.component(trailingShift(width, st.hudOffsetX)));
    }

    /** Espaço final para que um texto de largura {@code width} comece {@code offset} px à esquerda do centro. */
    public static int trailingShift(int width, int offset) {
        return 2 * offset - width;
    }

    private static TextColor colorOf(HealthState state, boolean blinkOff) {
        return switch (state) {
            case NORMAL -> GREEN;
            case WARNING -> YELLOW;
            case CRITICAL -> blinkOff ? RED_DARK : RED;
        };
    }

    public void hide(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        bars.remove(id);
        lastAlert.remove(id);
        forcedUntil.remove(id);
    }
}
