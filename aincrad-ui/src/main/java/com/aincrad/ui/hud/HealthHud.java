package com.aincrad.ui.hud;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.glyph.Glyph;
import com.aincrad.ui.glyph.LineBuilder;
import com.aincrad.ui.glyph.SpaceFont;
import com.aincrad.ui.glyph.TextWidth;
import com.aincrad.ui.glyph.UiFonts;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HUD de vida estilo SAO no alto da tela, desenhada no título de uma BossBar.
 * Com o pack: a moldura e as fatias da barra do SAO_Life_Gauge, deslocadas para a esquerda com a
 * fonte aincrad:space, e a barra branca da BossBar fica transparente. Sem o pack: BossBar comum
 * colorida com nome, vida e level.
 */
public final class HealthHud implements Listener {

    private static final TextColor GREEN = TextColor.color(0x7BD23A);
    private static final TextColor YELLOW = TextColor.color(0xF2C83D);
    private static final TextColor RED = TextColor.color(0xE8413C);
    private static final TextColor RED_DARK = TextColor.color(0x7A1A1A);
    private static final TextColor TEXT = TextColor.color(0x3C3C3C);

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
        Glyph frame = plugin.glyphs().get("hud_frame");
        List<Glyph> fills = fillGlyphs();

        if (plugin.packStatus().hasPack(p) && frame != null && frame.present() && !fills.isEmpty()) {
            bar.name(packTitle(p.getName(), ratio, state, blinkOff, numbers, level, frame, fills, st));
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

    /** Fatias da barra (grupo hud_fill de glyphs.yml), só se todas existirem. */
    private List<Glyph> fillGlyphs() {
        List<Glyph> out = new ArrayList<>();
        for (String id : plugin.glyphs().groups().getOrDefault("hud_fill", List.of())) {
            Glyph g = plugin.glyphs().get(id);
            if (g == null || !g.present()) {
                return List.of();
            }
            out.add(g);
        }
        return out;
    }

    /**
     * HUD estilo SAO: moldura com o nome à esquerda, barra com as fatias pintadas na cor da vida
     * e, embaixo da barra, "vida/máx LV:n". Tudo numa linha: cada parte é posicionada com a fonte
     * de espaços a partir do início da moldura.
     *
     * <p>O título da BossBar é centralizado. Para a moldura começar a {@code hudOffsetX} pixels à
     * esquerda do centro, soma-se no fim um espaço que deixa a largura total em 2 × deslocamento.
     */
    private Component packTitle(String name, double ratio, HealthState state, boolean blinkOff,
                                String numbers, String level, Glyph frame, List<Glyph> fills, UiSettings st) {
        LineBuilder line = new LineBuilder();
        line.add(frame, frame.component());

        line.moveTo(st.hudFillX);
        TextColor color = colorOf(state, blinkOff);
        int filled = HealthState.filledSegments(ratio, fills.size());
        for (int i = 0; i < filled; i++) {
            Glyph g = fills.get(i);
            // As fatias se encostam: o jogo soma 1 px depois de cada glifo, que é desfeito aqui.
            line.add(g, g.component(color));
            line.shift(-1);
        }

        String shown = fit(name, st.hudNameMaxPx);
        line.moveTo(st.hudNameX);
        line.add(Component.text(shown, TEXT).font(UiFonts.SAO), UiFonts.saoWidth(shown));

        line.moveTo(st.hudNumbersX);
        if (!numbers.isEmpty()) {
            line.add(Component.text(numbers, TEXT).font(UiFonts.HUD_LOW), TextWidth.of(numbers));
            line.shift(6);
        }
        line.add(Component.text(level, TEXT).font(UiFonts.HUD_LOW), TextWidth.of(level));

        line.moveTo(frame.advance());
        return line.build().append(SpaceFont.component(trailingShift(frame.advance(), st.hudOffsetX)));
    }

    /** Corta o nome para caber na área da moldura. */
    private static String fit(String name, int maxPx) {
        String s = name;
        while (s.length() > 1 && UiFonts.saoWidth(s) > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s.equals(name) ? s : s.substring(0, Math.max(1, s.length() - 1)) + ".";
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
