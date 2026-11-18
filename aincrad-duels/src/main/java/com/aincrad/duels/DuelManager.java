package com.aincrad.duels;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.duels.api.DuelEndEvent;
import com.aincrad.duels.api.DuelStartEvent;
import com.aincrad.duels.hook.Hooks;
import com.aincrad.duels.model.DuelMode;
import com.aincrad.duels.model.DuelScore;
import com.aincrad.duels.model.DuelService;
import com.aincrad.duels.model.DuelService.Challenge;
import com.aincrad.duels.model.DuelService.Duel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Os duelos no servidor: desafio, aposta, contagem, golpes e fim. Durante o duelo os dois podem
 * se ferir dentro de zona segura (AincradFloors) e sem mudar a cor do cursor
 * (AincradReputation); ninguém de fora acerta os duelistas, nem eles acertam alguém de fora.
 */
public final class DuelManager implements Listener {

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final ScoreStore scores;
    private final DuelService duels = new DuelService(System::currentTimeMillis);
    /** Centro de cada duelo: quem se afasta demais perde. */
    private final Map<Duel, Location> centers = new HashMap<>();
    /** Último número da contagem já mostrado. */
    private final Map<Duel, Integer> shown = new HashMap<>();

    public DuelManager(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, ScoreStore scores) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.scores = scores;
    }

    public DuelService service() {
        return duels;
    }

    private Component modeName(DuelMode m) {
        return msg.get("modo." + m.key());
    }

    public DuelMode defaultMode() {
        DuelMode m = DuelMode.parse(config.string("modo_padrao", "primeiro_golpe"));
        return m == null || (m.lethal() && !allowTotal()) ? DuelMode.PRIMEIRO_GOLPE : m;
    }

    public boolean allowTotal() {
        return config.bool("modo_total", true);
    }

    public long maxStake() {
        return hooks.hasEconomy() ? config.integer("aposta.maximo", 100_000, 0, Integer.MAX_VALUE) : 0;
    }

    private static boolean near(Player a, Player b, double max) {
        return a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation()) <= max * max;
    }

    private double challengeDistance() {
        return config.decimal("distancia_desafio", 16, 2, 256);
    }

    // ------------------------------------------------------------------ desafio

    /**
     * @param mode  null usa o modo padrão da config
     * @param stake quanto cada um aposta, em Col (0 = sem aposta)
     */
    public void challenge(Player from, Player to, DuelMode mode, long stake) {
        challenge(from, to, mode, stake, false);
    }

    /** @param itemBet cada um aposta o item que tem na mão principal (o de quem aceita, na hora de aceitar) */
    public void challenge(Player from, Player to, DuelMode mode, long stake, boolean itemBet) {
        if (itemBet && !config.bool("aposta.itens", true)) {
            msg.send(from, "aposta.itens_desligada");
            return;
        }
        if (itemBet && from.getInventory().getItemInMainHand().getType().isAir()) {
            msg.send(from, "aposta.item_mao_vazia");
            return;
        }
        if (!config.bool("ativo", true)) {
            msg.send(from, "desligado");
            return;
        }
        DuelMode m = mode == null ? defaultMode() : mode;
        if (m.lethal() && !allowTotal()) {
            msg.send(from, "total_desligado");
            return;
        }
        if (stake < 0 || stake > maxStake()) {
            msg.send(from, "aposta.invalida", Messages.vars("maximo", hooks.format(maxStake())));
            return;
        }
        if (stake > 0 && hooks.balance(from) < stake) {
            msg.send(from, "aposta.sem_saldo", Messages.vars("valor", hooks.format(stake)));
            return;
        }
        if (!near(from, to, challengeDistance())) {
            msg.send(from, "longe", Messages.vars("jogador", to.getName()));
            return;
        }
        if (from.isDead() || to.isDead()) {
            return;
        }
        long ttl = config.integer("convite_expira_segundos", 30, 5, 600);
        switch (duels.challenge(from.getUniqueId(), to.getUniqueId(), m, ttl * 1000L, stake, itemBet)) {
            case SELF -> msg.send(from, "si_mesmo");
            case BUSY -> msg.send(from, "voce_em_duelo");
            case TARGET_BUSY -> msg.send(from, "alvo_em_duelo", Messages.vars("jogador", to.getName()));
            case ALREADY -> msg.send(from, "ja_desafiou", Messages.vars("jogador", to.getName()));
            case SENT -> {
                Component bet = itemBet ? msg.get("aposta.linha_item", Messages.vars("item", itemName(from.getInventory().getItemInMainHand())))
                        : stake > 0 ? msg.get("aposta.linha", Messages.vars("valor", hooks.format(stake))) : Component.empty();
                msg.send(from, "enviado", Messages.vars("jogador", to.getName(), "modo", modeName(m), "aposta", bet));
                msg.send(to, m.lethal() ? "recebido_total" : "recebido", Messages.vars("jogador", from.getName(),
                        "modo", modeName(m), "segundos", ttl, "aposta", bet));
                to.sendMessage(Component.text("  ")
                        .append(msg.get("botao_aceitar").clickEvent(ClickEvent.runCommand("/duelo aceitar " + from.getName())))
                        .append(Component.text("  "))
                        .append(msg.get("botao_recusar").clickEvent(ClickEvent.runCommand("/duelo recusar " + from.getName()))));
                to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                // Com a interface, o desafio abre a janela de aceitar ou recusar na frente do desafiado
                // (o aviso do chat fica, para quem está com um menu aberto ou sem o resource pack).
                UUID challenger = from.getUniqueId();
                AincradCore.get().ui().prompt(to, msg.get(m.lethal() ? "recebido_total" : "recebido", Messages.vars("jogador", from.getName(),
                        "modo", modeName(m), "segundos", ttl, "aposta", bet)), pl -> accept(pl, challenger), pl -> decline(pl, challenger));
            }
        }
    }

    /** {@code from} null aceita o desafio mais recente. */
    public void accept(Player me, UUID from) {
        Challenge c = duels.pending(me.getUniqueId(), from);
        if (c == null) {
            msg.send(me, "sem_desafio");
            return;
        }
        Player other = Bukkit.getPlayer(c.from());
        if (other == null || !near(me, other, challengeDistance()) || other.isDead() || me.isDead()) {
            duels.decline(me.getUniqueId(), c.from());
            msg.send(me, "desafiante_longe");
            return;
        }
        // Aposta de item: os dois precisam estar com um item na mão agora; eles saem do inventário.
        byte[] itemA = null;
        byte[] itemB = null;
        if (c.itemBet()) {
            ItemStack a = other.getInventory().getItemInMainHand();
            ItemStack b = me.getInventory().getItemInMainHand();
            if (a.getType().isAir() || b.getType().isAir()) {
                duels.decline(me.getUniqueId(), c.from());
                for (Player p : new Player[]{other, me}) {
                    msg.send(p, "aposta.item_faltou", Messages.vars("jogador", (a.getType().isAir() ? other : me).getName()));
                }
                return;
            }
            itemA = a.serializeAsBytes();
            itemB = b.serializeAsBytes();
        }
        // A aposta dos dois vai para a custódia antes de qualquer golpe.
        String holdA = null;
        String holdB = null;
        if (c.stake() > 0) {
            holdA = hooks.hold(other, c.stake());
            holdB = holdA == null ? null : hooks.hold(me, c.stake());
            if (holdA == null || holdB == null) {
                hooks.refund(holdA);
                duels.decline(me.getUniqueId(), c.from());
                Player broke = holdA == null ? other : me;
                for (Player p : new Player[]{other, me}) {
                    msg.send(p, "aposta.alguem_sem_saldo", Messages.vars("jogador", broke.getName(), "valor", hooks.format(c.stake())));
                }
                return;
            }
        }
        long countdown = config.integer("contagem_segundos", 5, 1, 60) * 1000L;
        long limit = config.integer("tempo_limite_segundos", 180, 10, 3600) * 1000L;
        Duel d = duels.begin(c, countdown, limit);
        if (d == null) {
            hooks.refund(holdA);
            hooks.refund(holdB);
            msg.send(me, "alvo_em_duelo", Messages.vars("jogador", other.getName()));
            return;
        }
        d.holds(holdA, holdB);
        if (itemA != null) {
            String server = AincradCore.get().server();
            other.getInventory().setItemInMainHand(null);
            me.getInventory().setItemInMainHand(null);
            d.items(itemA, itemB);
            scores.itemHeld(d.a(), itemA, server);
            scores.itemHeld(d.b(), itemB, server);
        }
        if (holdA != null) {
            String server = AincradCore.get().server();
            scores.betOpened(holdA, server);
            scores.betOpened(holdB, server);
        }
        centers.put(d, other.getLocation().add(me.getLocation()).multiply(0.5));
        // Zona segura e cor do cursor: liberados para os dois até o duelo acabar (com folga).
        hooks.exemptPvp(d.a(), d.b(), d.endAt() + 5_000L);
        Component bet = d.stake() > 0 ? msg.get("aposta.linha", Messages.vars("valor", hooks.format(d.stake()))) : Component.empty();
        for (Player p : new Player[]{other, me}) {
            msg.send(p, "aceito", Messages.vars("jogador", (p == me ? other : me).getName(), "modo", modeName(d.mode()), "aposta", bet));
        }
        Bukkit.getPluginManager().callEvent(new DuelStartEvent(other, me, d.mode().key()));
    }

    public void decline(Player me, UUID from) {
        Challenge c = duels.decline(me.getUniqueId(), from);
        if (c == null) {
            msg.send(me, "sem_desafio");
            return;
        }
        msg.send(me, "recusado");
        Player other = Bukkit.getPlayer(c.from());
        if (other != null) {
            msg.send(other, "recusou", Messages.vars("jogador", me.getName()));
        }
    }

    /** Desiste do duelo em andamento: o outro vence. */
    public void surrender(Player me) {
        Duel d = duels.duelOf(me.getUniqueId());
        if (d == null) {
            msg.send(me, "nao_esta");
            return;
        }
        finish(d, d.opponent(me.getUniqueId()), "desistiu");
    }

    // ------------------------------------------------------------------ andamento

    /** A cada meio segundo: contagem, tempo limite e quem saiu da área. */
    public void tick() {
        long now = System.currentTimeMillis();
        double r = config.decimal("raio", 30, 5, 256);
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
                    title(a, b, Component.text(left), msg.get("contagem_sub", Messages.vars("modo", modeName(d.mode()))));
                    sound(a, b, Sound.BLOCK_NOTE_BLOCK_HAT, 1f);
                }
                continue;
            }
            if (shown.remove(d) != null) {
                title(a, b, msg.get("lutem"), Component.empty());
                sound(a, b, Sound.BLOCK_NOTE_BLOCK_PLING, 2f);
            }
            Location c = centers.get(d);
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

    private static String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return p.getName();
        }
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    /** Encerra o duelo. {@code winner} null é empate. */
    private void finish(Duel d, UUID winner, String reason) {
        if (!duels.end(d)) {
            return;
        }
        Location center = centers.remove(d);
        shown.remove(d);
        hooks.exemptPvp(d.a(), d.b(), 0L);
        String nameA = nameOf(d.a());
        String nameB = nameOf(d.b());
        // A aposta: o vencedor leva as duas; no empate, cada um recebe a sua de volta.
        if (d.holdA() != null) {
            if (winner == null) {
                hooks.refund(d.holdA());
                hooks.refund(d.holdB());
            } else {
                hooks.release(d.holdA(), winner, d.stake());
                hooks.release(d.holdB(), winner, d.stake());
            }
            scores.betClosed(d.holdA());
            scores.betClosed(d.holdB());
        }
        // Aposta de item: o vencedor leva os dois; no empate, cada um recebe o seu.
        if (d.itemA() != null) {
            deliver(winner == null ? d.a() : winner, d.itemA());
            deliver(winner == null ? d.b() : winner, d.itemB());
            scores.itemsSettled(d.a(), d.b(), AincradCore.get().server());
        }
        Component line;
        if (winner == null) {
            line = msg.get("fim.empate", Messages.vars("a", nameA, "b", nameB));
            scores.record(d.a(), nameA, DuelScore::drew);
            scores.record(d.b(), nameB, DuelScore::drew);
        } else {
            boolean aWon = winner.equals(d.a());
            line = msg.get("fim." + reason, Messages.vars("vencedor", aWon ? nameA : nameB, "perdedor", aWon ? nameB : nameA));
            scores.record(d.a(), nameA, aWon ? DuelScore::won : DuelScore::lost);
            scores.record(d.b(), nameB, aWon ? DuelScore::lost : DuelScore::won);
        }
        Component full = msg.prefix().append(line);
        if (d.itemA() != null) {
            full = full.append(Component.space()).append(msg.get(winner == null ? "aposta.itens_devolvidos" : "aposta.levou_itens"));
        }
        if (d.stake() > 0) {
            full = full.append(Component.space()).append(msg.get(winner == null ? "aposta.devolvida" : "aposta.levou",
                    Messages.vars("valor", hooks.format(d.stake() * 2))));
        }
        Player a = Bukkit.getPlayer(d.a());
        Player b = Bukkit.getPlayer(d.b());
        // Os dois e quem assistia por perto ficam sabendo.
        double hear = config.decimal("raio_aviso", 32, 0, 256);
        if (center != null && center.getWorld() != null) {
            for (Player p : center.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(center) <= hear * hear || d.has(p.getUniqueId())) {
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
            p.showTitle(Title.title(msg.get(winner == null ? "titulo_empate" : won ? "titulo_vitoria" : "titulo_derrota"),
                    Component.empty(), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1f);
            p.setFireTicks(0);
        }
        Bukkit.getPluginManager().callEvent(new DuelEndEvent(d.a(), d.b(), winner, d.mode().key(), reason, d.stake()));
    }

    /** Encerra tudo sem vencedor (plugin desligando): as apostas voltam. */
    public void shutdown() {
        for (Duel d : new ArrayList<>(duels.duels())) {
            if (duels.end(d)) {
                hooks.exemptPvp(d.a(), d.b(), 0L);
                hooks.refund(d.holdA());
                hooks.refund(d.holdB());
                scores.betClosed(d.holdA());
                scores.betClosed(d.holdB());
                if (d.itemA() != null) {
                    deliver(d.a(), d.itemA());
                    deliver(d.b(), d.itemB());
                    scores.itemsSettled(d.a(), d.b(), AincradCore.get().server());
                }
            }
        }
        centers.clear();
        shown.clear();
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
            msg.actionBar(by, mine == null ? "nao_interfira" : "so_o_oponente");
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scores.load(event.getPlayer().getUniqueId(), s -> { });
        claim(event.getPlayer());
    }

    // ------------------------------------------------------------------ aposta de item

    static Component itemName(ItemStack it) {
        Component name = it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? it.getItemMeta().displayName() : Component.translatable(it.translationKey());
        return it.getAmount() > 1 ? name.append(Component.text(" x" + it.getAmount())) : name;
    }

    /** Entrega um item apostado: na hora a quem está online; a quem não está, ao entrar (em qualquer servidor). */
    private void deliver(UUID to, byte[] item) {
        Player p = Bukkit.getPlayer(to);
        if (p == null) {
            scores.itemOwed(to, item, AincradCore.get().server());
            return;
        }
        give(p, ItemStack.deserializeBytes(item));
    }

    private static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    /** Itens de apostas que esperavam o jogador (ganhos enquanto ele estava fora, ou de um duelo interrompido). */
    public void claim(Player p) {
        scores.claimItems(p.getUniqueId(), items -> {
            if (items.isEmpty() || !p.isOnline()) {
                // Saiu antes de receber: os itens voltam para a fila.
                items.forEach(i -> scores.itemOwed(p.getUniqueId(), i, AincradCore.get().server()));
                return;
            }
            for (byte[] i : items) {
                give(p, ItemStack.deserializeBytes(i));
            }
            msg.send(p, "aposta.itens_recebidos", Messages.vars("qtd", items.size()));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        duels.forget(id);
        Duel d = duels.duelOf(id);
        if (d != null) {
            finish(d, d.opponent(id), "saiu");
        }
        scores.forget(id);
    }
}
