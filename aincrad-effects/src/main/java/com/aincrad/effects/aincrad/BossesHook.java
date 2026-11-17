package com.aincrad.effects.aincrad;

import com.aincrad.bosses.api.BossDamageEvent;
import com.aincrad.bosses.api.BossDefeatEvent;
import com.aincrad.bosses.api.BossFightStartEvent;
import com.aincrad.bosses.api.BossPhaseChangeEvent;
import com.aincrad.effects.AincradEffectsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Efeitos dos chefes: entrada, números de dano, aviso de fase e vitória. Só carrega com o AincradBosses. */
final class BossesHook implements Listener {

    private final AincradEffectsPlugin plugin;
    private final AincradIntegration fx;
    private final ResultWindow window;
    /** Chefe → {segundo atual, números já mostrados nele}. */
    private final Map<String, long[]> shown = new HashMap<>();

    BossesHook(AincradEffectsPlugin plugin, AincradIntegration fx, ResultWindow window) {
        this.plugin = plugin;
        this.fx = fx;
        this.window = window;
    }

    private static List<Player> near(Location at, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= radius * radius) {
                out.add(p);
            }
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStart(BossFightStartEvent event) {
        Map<String, String> vars = Map.of("chefe", event.getBossName(), "andar", String.valueOf(event.getFloor()),
                "level", levelOf(event.getBossId()));
        for (Player p : event.getPlayers()) {
            fx.play("chefe_entrada", p, vars);
        }
    }

    private static String levelOf(String bossId) {
        for (var b : com.aincrad.bosses.api.AincradBossesAPI.getBosses()) {
            if (b.id().equals(bossId)) {
                return String.valueOf(b.level());
            }
        }
        return "?";
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(BossDamageEvent event) {
        LivingEntity boss = event.getEntity();
        if (boss == null || event.getDamage() < 0.5) {
            return;
        }
        // Limite por chefe e por segundo, para uma chuva de golpes não pesar.
        long second = System.currentTimeMillis() / 1000;
        long[] count = shown.computeIfAbsent(event.getBossId(), k -> new long[2]);
        if (count[0] != second) {
            count[0] = second;
            count[1] = 0;
        }
        if (++count[1] > fx.config().getInt("numeros_dano.max_por_segundo", 12)) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location at = boss.getLocation().add(rnd.nextDouble(-0.7, 0.7), boss.getHeight() * 0.6 + rnd.nextDouble(0, 0.5), rnd.nextDouble(-0.7, 0.7));
        fx.playAt(event.isCritical() ? "dano_critico" : "dano", at, near(at, fx.config().getDouble("numeros_dano.raio", 32)),
                Map.of("dano", String.valueOf(Math.round(event.getDamage())), "chefe", event.getBossName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPhase(BossPhaseChangeEvent event) {
        if (event.getEntity() == null) {
            return;
        }
        Map<String, String> vars = Map.of("fase", String.valueOf(event.getPhase()), "nome_fase", event.getPhaseName(),
                "chefe", event.getBossName());
        for (Player p : near(event.getEntity().getLocation(), 48)) {
            fx.play("fase", p, vars);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDefeat(BossDefeatEvent event) {
        shown.remove(event.getBossId());
        Set<UUID> who = new LinkedHashSet<>(event.getParticipants());
        who.addAll(event.getDamage().keySet());
        who.addAll(event.getItems().keySet());
        Player last = event.getLastAttack() == null ? null : Bukkit.getPlayer(event.getLastAttack());
        String lastName = last != null ? last.getName()
                : event.getLastAttack() == null ? "?" : String.valueOf(Bukkit.getOfflinePlayer(event.getLastAttack()).getName());
        double total = event.getEntity() == null ? 0 : event.getEntity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 0
                : event.getEntity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        Map<String, String> vars = Map.of("chefe", event.getBossName(), "andar", String.valueOf(event.getFloor()), "jogador", lastName);
        long delay = Math.max(1, Math.round(fx.config().getDouble("janela_resultado.atraso_segundos", 2.5) * 20));
        for (UUID id : who) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            fx.play("congratulations", p, vars);
            if (!fx.config().getBoolean("janela_resultado.ativo", true)) {
                continue;
            }
            boolean isLast = id.equals(event.getLastAttack());
            List<ItemStack> items = new ArrayList<>(event.getItems().getOrDefault(id, List.of()));
            ItemStack bonus = isLast ? event.getLastAttackItem() : null;
            if (bonus != null) {
                // O item de Last Attack aparece em destaque, não na lista comum.
                items.removeIf(it -> it == bonus);
            }
            double damage = event.getDamage().getOrDefault(id, 0.0);
            ResultWindow.Result result = new ResultWindow.Result(event.getBossName(), event.getFloor(), event.isFirstKill(),
                    event.getXp().getOrDefault(id, 0L), event.getCol().getOrDefault(id, 0L), Math.round(damage),
                    total <= 0 ? 0 : Math.round(damage / total * 100), event.getParticipants().contains(id), items, bonus, lastName);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !p.isDead()) {
                    window.open(p, result);
                }
            }, delay);
        }
    }
}
