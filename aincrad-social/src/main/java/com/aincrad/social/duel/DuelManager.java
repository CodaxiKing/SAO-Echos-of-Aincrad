package com.aincrad.social.duel;

import com.aincrad.social.AincradSocialPlugin;
import com.aincrad.social.api.event.DuelEndEvent;
import com.aincrad.social.api.event.DuelStartEvent;
import com.aincrad.social.config.Messages;
import com.aincrad.social.config.Settings;
import com.aincrad.social.duel.DuelService.Challenge;
import com.aincrad.social.duel.DuelService.Duel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Os duelos no servidor: desafio, contagem, golpes e fim. Durante o duelo os dois podem se ferir
 * dentro de zona segura (AincradFloors) e sem mudar a cor do cursor (AincradReputation); ninguém
 * de fora acerta os duelistas, nem eles acertam alguém de fora.
 */
public final class DuelManager implements Listener {

    private final AincradSocialPlugin plugin;
    private final DuelService duels = new DuelService(System::currentTimeMillis);
    /** Centro de cada duelo: quem se afasta demais perde. */
    private final Map<Duel, Location> centers = new HashMap<>();
    /** Último número da contagem já mostrado. */
    private final Map<Duel, Integer> shown = new HashMap<>();

    public DuelManager(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    public DuelService service() {
        return duels;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Settings cfg() {
        return plugin.settings();
    }

    private Component modeName(DuelMode m) {
        return msg().get("duelo.modo." + m.key());
    }

    private static TagResolver who(Player p) {
        return Placeholder.unparsed("jogador", p.getName());
    }

    // ------------------------------------------------------------------ desafio

    /** {@code mode} null usa o modo padrão da config. */
    public void challenge(Player from, Player to, DuelMode mode) {
        if (!cfg().duelEnabled) {
            msg().send(from, "duelo.desligado");
            return;
        }
        DuelMode m = mode == null ? cfg().duelDefaultMode : mode;
        if (m.lethal() && !cfg().duelAllowTotal) {
            msg().send(from, "duelo.total_desligado");
            return;
        }
        if (!near(from, to, cfg().duelChallengeDistance)) {
            msg().send(from, "duelo.longe", who(to));
            return;
        }
        if (from.isDead() || to.isDead()) {
            return;
        }
        switch (duels.challenge(from.getUniqueId(), to.getUniqueId(), m, cfg().duelInviteSeconds * 1000L)) {
            case SELF -> msg().send(from, "duelo.si_mesmo");
            case BUSY -> msg().send(from, "duelo.voce_em_duelo");
            case TARGET_BUSY -> msg().send(from, "duelo.alvo_em_duelo", who(to));
            case ALREADY -> msg().send(from, "duelo.ja_desafiou", who(to));
            case SENT -> {
                msg().send(from, "duelo.enviado", who(to), Placeholder.component("modo", modeName(m)));
                msg().send(to, m.lethal() ? "duelo.recebido_total" : "duelo.recebido", who(from),
                        Placeholder.component("modo", modeName(m)),
                        Placeholder.unparsed("segundos", String.valueOf(cfg().duelInviteSeconds)));
                Component yes = msg().get("geral.aceitar").clickEvent(ClickEvent.runCommand("/duelo aceitar " + from.getName()))
                        .hoverEvent(HoverEvent.showText(msg().get("geral.dica_aceitar")));
                Component no = msg().get("geral.recusar").clickEvent(ClickEvent.runCommand("/duelo recusar " + from.getName()))
                        .hoverEvent(HoverEvent.showText(msg().get("geral.dica_recusar")));
                to.sendMessage(Component.text("  ").append(yes).append(Component.text("  ")).append(no));
                to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            }
        }
    }

    /** {@code from} null aceita o desafio mais recente. */
    public void accept(Player me, UUID from) {
        Challenge c = duels.pending(me.getUniqueId(), from);
        if (c == null) {
            msg().send(me, "duelo.sem_desafio");
            return;
        }
        Player other = Bukkit.getPlayer(c.from());
        if (other == null || !near(me, other, cfg().duelChallengeDistance) || other.isDead() || me.isDead()) {
            duels.decline(me.getUniqueId(), c.from());
            msg().send(me, "duelo.desafiante_longe");
            return;
        }
        Duel d = duels.begin(c, cfg().duelCountdownSeconds * 1000L, cfg().duelLimitSeconds * 1000L);
        if (d == null) {
            msg().send(me, "duelo.alvo_em_duelo", who(other));
            return;
        }
        centers.put(d, other.getLocation().add(me.getLocation()).multiply(0.5));
        // Zona segura e cor do cursor: liberados para os dois até o duelo acabar (com folga).
        plugin.hooks().exemptPvp(d.a(), d.b(), d.endAt() + 5_000L);
        for (Player p : new Player[]{other, me}) {
            msg().send(p, "duelo.aceito", who(p == me ? other : me), Placeholder.component("modo", modeName(d.mode())));
        }
        Bukkit.getPluginManager().callEvent(new DuelStartEvent(other, me, d.mode().key()));
    }

    public void decline(Player me, UUID from) {
        Challenge c = duels.decline(me.getUniqueId(), from);
        if (c == null) {
            msg().send(me, "duelo.sem_desafio");
            return;
        }
        msg().send(me, "duelo.recusado");
        Player other = Bukkit.getPlayer(c.from());
        if (other != null) {
            msg().send(other, "duelo.recusou", who(me));
        }
    }

    /** Desiste do duelo em andamento: o outro vence. */
    public void surrender(Player me) {
        Duel d = duels.duelOf(me.getUniqueId());
        if (d == null) {
            msg().send(me, "duelo.nao_esta");
            return;
        }
        finish(d, d.opponent(me.getUniqueId()), "desistiu");
    }

    private static boolean near(Player a, Player b, double max) {
        return a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation()) <= max * max;
    }

    // ------------------------------------------------------------------ andamento

    /** A cada meio segundo: contagem, tempo limite e quem saiu da área. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Duel d : new ArrayList<>(duels.duels())) {
            Player a = Bukkit.getPlayer(d.a());
            Player b = Bukkit.getPlayer(d.b());
            if (a == null || b == null) {
                finish(d, a == null ? d.b() : d.a(), "saiu");
                continue;
            }
            if (!d.started(now)) {
                int left = (int) Math.ceil((d.startAt() - now) / 1000.0);
                if (shown.getOrDefault(d, -1) != left) {
                    shown.put(d, left);
                    title(a, b, Component.text(left), msg().get("duelo.contagem_sub", Placeholder.component("modo", modeName(d.mode()))));
                    sound(a, b, Sound.BLOCK_NOTE_BLOCK_HAT, 1f);
                }
                continue;
            }
            if (shown.remove(d) != null) {
                title(a, b, msg().get("duelo.lutem"), Component.empty());
                sound(a, b, Sound.BLOCK_NOTE_BLOCK_PLING, 2f);
            }
            Location c = centers.get(d);
            double r = cfg().duelRadius;
            boolean aOut = c != null && (!a.getWorld().equals(c.getWorld()) || a.getLocation().distanceSquared(c) > r * r);
            boolean bOut = c != null && (!b.getWorld().equals(c.getWorld()) || b.getLocation().distanceSquared(c) > r * r);
            if (aOut || bOut) {
                finish(d, aOut && bOut ? null : aOut ? d.b() : d.a(), "fugiu");
                continue;
            }
            if (d.timedOut(now)) {
                int w = DuelService.byHealth(a.getHealth(), maxHealth(a), b.getHealth(), maxHealth(b));
                finish(d, w == 0 ? null : w > 0 ? d.a() : d.b(), "tempo");
            }
        }
    }

    private static double maxHealth(Player p) {
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        return hp == null ? 20 : hp.getValue();
    }

    private static void title(Player a, Player b, Component main, Component sub) {
        Title t = Title.title(main, sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200)));
        a.showTitle(t);
        b.showTitle(t);
    }

    private static void sound(Player a, Player b, Sound s, float pitch) {
        a.playSound(a.getLocation(), s, 1f, pitch);
        b.playSound(b.getLocation(), s, 1f, pitch);
    }

    /** Encerra o duelo. {@code winner} null é empate. */
    private void finish(Duel d, UUID winner, String reason) {
        if (!duels.end(d)) {
            return;
        }
        Location center = centers.remove(d);
        shown.remove(d);
        plugin.hooks().exemptPvp(d.a(), d.b(), 0L);
        Player a = Bukkit.getPlayer(d.a());
        Player b = Bukkit.getPlayer(d.b());
        String nameA = plugin.names().name(d.a());
        String nameB = plugin.names().name(d.b());
        Component line;
        if (winner == null) {
            line = msg().get("duelo.fim.empate", Placeholder.unparsed("a", nameA), Placeholder.unparsed("b", nameB));
        } else {
            boolean aWon = winner.equals(d.a());
            line = msg().get("duelo.fim." + reason, Placeholder.unparsed("vencedor", aWon ? nameA : nameB),
                    Placeholder.unparsed("perdedor", aWon ? nameB : nameA));
        }
        Component full = msg().get("prefixo").append(line);
        // Os dois e quem assistia por perto ficam sabendo.
        if (center != null && center.getWorld() != null) {
            double r = cfg().duelAnnounceRadius;
            for (Player p : center.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(center) <= r * r || d.has(p.getUniqueId())) {
                    p.sendMessage(full);
                }
            }
        } else {
            for (Player p : new Player[]{a, b}) {
                if (p != null) {
                    p.sendMessage(full);
                }
            }
        }
        for (Player p : new Player[]{a, b}) {
            if (p == null) {
                continue;
            }
            boolean won = winner != null && winner.equals(p.getUniqueId());
            p.showTitle(Title.title(msg().get(winner == null ? "duelo.titulo_empate" : won ? "duelo.titulo_vitoria" : "duelo.titulo_derrota"),
                    Component.empty(), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1f);
            p.setFireTicks(0);
        }
        Bukkit.getPluginManager().callEvent(new DuelEndEvent(d.a(), d.b(), winner, d.mode().key(), reason));
    }

    /** Encerra tudo sem vencedor (plugin desligando). */
    public void shutdown() {
        for (Duel d : new ArrayList<>(duels.duels())) {
            finish(d, null, "tempo");
        }
    }

    // ------------------------------------------------------------------ golpes

    private static Player attacker(Entity by) {
        if (by instanceof Player p) {
            return p;
        }
        return by instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
    }

    /** Antes de todos: golpes na contagem e golpes entre duelista e alguém de fora não valem. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHitEarly(EntityDamageByEntityEvent event) {
        Player by = attacker(event.getDamager());
        if (by == null || !(event.getEntity() instanceof Player victim) || by.equals(victim)) {
            return;
        }
        Duel mine = duels.duelOf(by.getUniqueId());
        Duel theirs = duels.duelOf(victim.getUniqueId());
        if (mine == null && theirs == null) {
            return;
        }
        if (mine != theirs) {
            event.setCancelled(true);
            by.sendActionBar(msg().get(mine == null ? "duelo.nao_interfira" : "duelo.so_o_oponente"));
        } else if (!mine.started(System.currentTimeMillis())) {
            event.setCancelled(true);
        }
    }

    /** Depois de todas as proteções: o golpe valeu. Decide o primeiro golpe e a metade da vida. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player by = attacker(event.getDamager());
        if (by == null || !(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) {
            return;
        }
        Duel d = duels.duelOf(by.getUniqueId());
        if (d == null || !d.has(victim.getUniqueId()) || d.mode().lethal()) {
            return;
        }
        double after = victim.getHealth() - event.getFinalDamage();
        if (!d.mode().endsOnHit(after, maxHealth(victim))) {
            return;
        }
        // Ninguém morre nestes modos: o golpe que decide não tira vida a mais.
        if (d.mode() == DuelMode.PRIMEIRO_GOLPE || after <= 0) {
            event.setDamage(0);
        }
        Bukkit.getScheduler().runTask(plugin, () -> finish(d, by.getUniqueId(), d.mode() == DuelMode.PRIMEIRO_GOLPE ? "golpe" : "metade"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Duel d = duels.duelOf(event.getEntity().getUniqueId());
        if (d != null) {
            finish(d, d.opponent(event.getEntity().getUniqueId()), "morte");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        duels.forget(id);
        Duel d = duels.duelOf(id);
        if (d != null) {
            finish(d, d.opponent(id), "saiu");
        }
    }
}
