package com.aincrad.ui.hud;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.api.PartyHudProvider;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.flat.FlatHud;
import com.aincrad.ui.flat.FlatOverlay;
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
    private final Map<UUID, List<BossBar>> memberBars = new HashMap<>();
    private PartyHudProvider partyProvider;
    private java.util.function.Function<Player, Component> badgeProvider;
    private java.util.function.Function<Player, Component> trackerProvider;
    private final Map<UUID, BossBar> trackerBars = new HashMap<>();
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

        boolean flat = st.hudFlat && plugin.packStatus().hasPack(p);
        if (flat) {
            // HUD em barras: desenhada pelas telas planas, numa BossBar só.
            hideBars(p);
            int[] energy = plugin.flat().energy(p);
            FlatHud.Data data = new FlatHud.Data(p.getName(), (int) Math.ceil(health), (int) Math.round(max), ratio,
                    colorOf(state, blinkOff).value(), energy[0], energy[1], FlatHud.xpText(p), FlatHud.level(p), partyData(p, st));
            // Cada jogador acerta a posição nas Opções: as duas bordas andam juntas, para dentro ou para fora.
            int dx = plugin.prefs().hudDx(p);
            FlatOverlay.Data extra = overlay(p, st);
            plugin.flat().hud(p, plugin.flat().paint(d -> {
                FlatHud.paint(d, data, -st.flatHudX + dx);
                FlatOverlay.paint(d, extra, st.flatHudX - dx);
            }));
            alert(p, state, st);
            updateTracker(p);
            return;
        }
        plugin.flat().hud(p, null);
        if (plugin.flat().isOpen(p)) {
            // Uma tela plana precisa ser a primeira BossBar: a HUD do anime sai enquanto ela está aberta.
            hideBars(p);
            return;
        }
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
            bar.name(packTitle(p.getName(), badge(p), ratio, state, blinkOff, numbers, level, frame, fills, st));
            bar.color(BossBar.Color.WHITE);
            bar.progress(1f);
        } else {
            TextColor c = colorOf(state, blinkOff);
            Component b = badge(p);
            Component title = (b == null ? Component.empty() : b).append(Component.text(p.getName(), NamedTextColor.WHITE))
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

        alert(p, state, st);
        updateParty(p, st);
        updateTracker(p);
    }

    public void setTrackerProvider(java.util.function.Function<Player, Component> provider) {
        this.trackerProvider = provider;
    }

    /** A linha de rastreio (a quest em andamento): uma BossBar só de texto, por último. */
    private void updateTracker(Player p) {
        Component text = null;
        if (trackerProvider != null) {
            try {
                text = trackerProvider.apply(p);
            } catch (RuntimeException | LinkageError e) {
                text = null;
            }
        }
        BossBar bar = trackerBars.get(p.getUniqueId());
        if (text == null) {
            if (bar != null) {
                p.hideBossBar(trackerBars.remove(p.getUniqueId()));
            }
            return;
        }
        if (bar == null) {
            // Barra branca: com o pack ela fica transparente e sobra só o texto.
            bar = BossBar.bossBar(text, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            trackerBars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(text);
        }
    }

    private void hideTracker(Player p) {
        BossBar bar = trackerBars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }

    private void alert(Player p, HealthState state, UiSettings st) {
        if (state != HealthState.CRITICAL) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(p.getUniqueId());
        if (last == null || now - last >= st.hudAlertSeconds * 1000L) {
            lastAlert.put(p.getUniqueId(), now);
            plugin.sounds().play(p, UiSound.CRITICAL);
        }
    }

    private static final java.time.format.DateTimeFormatter CLOCK = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    private static final java.time.format.DateTimeFormatter DATE = java.time.format.DateTimeFormatter.ofPattern("dd/MM");

    /** O alvo, os avisos, as recargas, o relógio e a bússola de um jogador. */
    private FlatOverlay.Data overlay(Player p, UiSettings st) {
        FlatOverlay.Target target = st.targetEnabled ? plugin.targets().of(p, st) : null;
        List<FlatOverlay.Cooldown> cds = st.cooldownsEnabled ? plugin.flat().cooldowns(p) : List.of();
        String clock = null;
        String date = null;
        if (st.clockEnabled) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            clock = now.format(CLOCK);
            date = worldDate();
            if (date == null) {
                date = now.format(DATE);
            }
        }
        Float yaw = null;
        List<FlatOverlay.Marker> markers = new ArrayList<>();
        if (st.compassEnabled) {
            yaw = p.getLocation().getYaw();
            for (Player m : partyOf(p, st)) {
                if (!m.getWorld().equals(p.getWorld()) || m.isDead()) {
                    continue;
                }
                double dx = m.getLocation().getX() - p.getLocation().getX();
                double dz = m.getLocation().getZ() - p.getLocation().getZ();
                // O yaw que aponta para o colega, e quanto ele está fora da frente do jogador.
                float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
                markers.add(new FlatOverlay.Marker(com.aincrad.ui.flat.FlatManager.angleDelta(yaw, bearing), 0x5BE37D));
            }
        }
        return new FlatOverlay.Data(target, plugin.toasts().active(p), cds, clock, date, yaw, markers);
    }

    private boolean worldLooked;
    private java.lang.reflect.Method worldSeason;
    private java.lang.reflect.Method worldDay;

    /** "primavera, dia 12" pelo calendário do AincradWorld (opcional, por reflexão); null sem ele. */
    private String worldDate() {
        if (!worldLooked) {
            worldLooked = true;
            org.bukkit.plugin.Plugin world = plugin.getServer().getPluginManager().getPlugin("AincradWorld");
            if (world != null && world.isEnabled()) {
                try {
                    Class<?> api = Class.forName("com.aincrad.world.api.AincradWorldAPI", true, world.getClass().getClassLoader());
                    worldSeason = api.getMethod("getSeason");
                    worldDay = api.getMethod("getSeasonDay");
                } catch (ReflectiveOperationException | LinkageError e) {
                    worldSeason = null;
                }
            }
        }
        if (worldSeason == null) {
            return null;
        }
        try {
            Object season = worldSeason.invoke(null);
            Object day = worldDay.invoke(null);
            return season == null || String.valueOf(season).isEmpty() ? null : season + ", dia " + day;
        } catch (ReflectiveOperationException | RuntimeException e) {
            worldSeason = null;
            return null;
        }
    }

    /** Depois de um reload, procura os outros plugins de novo. */
    public void resetHooks() {
        worldLooked = false;
        worldSeason = null;
    }

    /** Membros da party para a HUD em barras. */
    private List<FlatHud.Member> partyData(Player p, UiSettings st) {
        List<FlatHud.Member> out = new ArrayList<>();
        for (Player m : partyOf(p, st)) {
            AttributeInstance a = m.getAttribute(Attribute.MAX_HEALTH);
            double max = a != null ? Math.max(1.0, a.getValue()) : 20.0;
            double ratio = m.isDead() ? 0 : Math.min(1.0, Math.max(0, m.getHealth()) / max);
            out.add(new FlatHud.Member(m.getName(), ratio, colorOf(HealthState.of(ratio, st.hudYellow, st.hudRed), false).value()));
        }
        return out;
    }

    private List<Player> partyOf(Player p, UiSettings st) {
        List<Player> members = List.of();
        if (partyProvider != null) {
            try {
                members = partyProvider.members(p);
            } catch (RuntimeException e) {
                members = List.of();
            }
        }
        return members.stream()
                .filter(m -> m != null && m.isOnline() && !m.equals(p))
                .limit(st.hudPartyMax)
                .toList();
    }

    // ------------------------------------------------------------------ party

    public void setBadgeProvider(java.util.function.Function<Player, Component> provider) {
        this.badgeProvider = provider;
    }

    /** Símbolo antes do nome (emblema da guilda), ou null. Usado também pelo painel de perfil. */
    public Component badge(Player p) {
        if (badgeProvider == null) {
            return null;
        }
        try {
            return badgeProvider.apply(p);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Largura aproximada do símbolo: letras comuns pela tabela, símbolos como 9 px. */
    static int badgeWidth(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += c < 128 ? TextWidth.of(c) : 9;
        }
        return w;
    }

    public void setPartyProvider(PartyHudProvider provider) {
        this.partyProvider = provider;
    }

    /** Uma BossBar menor por membro da party, embaixo da barra do próprio jogador. */
    private void updateParty(Player p, UiSettings st) {
        List<Player> shown = partyOf(p, st);
        List<BossBar> list = memberBars.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
        while (list.size() > shown.size()) {
            p.hideBossBar(list.remove(list.size() - 1));
        }
        while (list.size() < shown.size()) {
            BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            p.showBossBar(b);
            list.add(b);
        }
        Glyph mini = plugin.glyphs().get("hud_mini");
        List<Glyph> fills = groupGlyphs("hud_mini_fill");
        boolean pack = plugin.packStatus().hasPack(p) && mini != null && mini.present() && !fills.isEmpty();
        for (int i = 0; i < shown.size(); i++) {
            Player m = shown.get(i);
            AttributeInstance a = m.getAttribute(Attribute.MAX_HEALTH);
            double max = a != null ? Math.max(1.0, a.getValue()) : 20.0;
            double ratio = m.isDead() ? 0 : Math.min(1.0, Math.max(0, m.getHealth()) / max);
            HealthState state = HealthState.of(ratio, st.hudYellow, st.hudRed);
            BossBar bar = list.get(i);
            if (pack) {
                bar.name(memberTitle(m.getName(), ratio, state, mini, fills, st));
                bar.color(BossBar.Color.WHITE);
                bar.progress(1f);
            } else {
                bar.name(Component.text(m.getName(), NamedTextColor.WHITE));
                bar.color(switch (state) {
                    case NORMAL -> BossBar.Color.GREEN;
                    case WARNING -> BossBar.Color.YELLOW;
                    case CRITICAL -> BossBar.Color.RED;
                });
                bar.progress((float) ratio);
            }
        }
    }

    /** Barra pequena + nome, alinhada um pouco para dentro da moldura principal. */
    private Component memberTitle(String name, double ratio, HealthState state, Glyph mini, List<Glyph> fills, UiSettings st) {
        LineBuilder line = new LineBuilder();
        line.add(mini, mini.component());
        line.moveTo(0);
        int filled = HealthState.filledSegments(ratio, fills.size());
        TextColor color = colorOf(state, false);
        for (int i = 0; i < filled; i++) {
            Glyph g = fills.get(i);
            line.add(g, g.component(color));
            line.shift(-1);
        }
        line.moveTo(mini.advance() + 3);
        line.add(Component.text(name, NamedTextColor.WHITE), TextWidth.of(name));
        int indent = st.hudPartyIndent;
        return line.build().append(SpaceFont.component(trailingShift(line.x(), st.hudOffsetX - indent)));
    }

    /** Fatias da barra (grupo hud_fill de glyphs.yml), só se todas existirem. */
    private List<Glyph> fillGlyphs() {
        return groupGlyphs("hud_fill");
    }

    private List<Glyph> groupGlyphs(String group) {
        List<Glyph> out = new ArrayList<>();
        for (String id : plugin.glyphs().groups().getOrDefault(group, List.of())) {
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
    private Component packTitle(String name, Component badge, double ratio, HealthState state, boolean blinkOff,
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

        line.moveTo(st.hudNameX);
        int maxName = st.hudNameMaxPx;
        if (badge != null) {
            int bw = badgeWidth(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(badge));
            line.add(badge, bw);
            maxName = Math.max(12, maxName - bw);
        }
        String shown = fit(name, maxName);
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
        hideBars(p);
        plugin.flat().hud(p, null);
    }

    private void hideBars(Player p) {
        hideTracker(p);
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
        List<BossBar> members = memberBars.remove(p.getUniqueId());
        if (members != null) {
            members.forEach(p::hideBossBar);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        bars.remove(id);
        memberBars.remove(id);
        trackerBars.remove(id);
        lastAlert.remove(id);
        forcedUntil.remove(id);
    }
}
