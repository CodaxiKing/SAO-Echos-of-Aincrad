package com.aincrad.life.hook;

import com.aincrad.economy.api.AincradEconomyAPI;
import com.aincrad.economy.api.TransactionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.function.BiFunction;

/**
 * As lojas de NPC do AincradEconomy vistas pelas árvores de profissão: o Provisionador compra
 * ingredientes mais barato e o Peixeiro vende peixe mais caro. Só é carregada quando o
 * AincradEconomy está ligado.
 *
 * <p>O motivo da transação é "loja:compra:&lt;item&gt;" ou "loja:venda:&lt;item&gt;". O ajuste é
 * um segundo depósito, logo depois (com motivo próprio, que este ouvinte ignora).
 */
public final class EcoShopListener implements Listener {

    private final Plugin plugin;
    /** (jogador, item comprado) → fração devolvida (0.1 = 10% de desconto). */
    private final BiFunction<Player, String, Double> buyDiscount;
    /** (jogador, item vendido) → fração a mais (0.1 = +10%; 1 = o dobro). */
    private final BiFunction<Player, String, Double> sellBonus;

    public EcoShopListener(Plugin plugin, BiFunction<Player, String, Double> buyDiscount, BiFunction<Player, String, Double> sellBonus) {
        this.plugin = plugin;
        this.buyDiscount = buyDiscount;
        this.sellBonus = sellBonus;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransaction(TransactionEvent event) {
        String reason = event.getReason();
        if (reason == null || !reason.startsWith("loja:")) {
            return;
        }
        Player p = Bukkit.getPlayer(event.getPlayerId());
        if (p == null) {
            return;
        }
        boolean buying = reason.startsWith("loja:compra:");
        if (!buying && !reason.startsWith("loja:venda:")) {
            return;
        }
        String item = reason.substring(reason.indexOf(':', 5) + 1);
        double fraction = Math.max(0, buying ? buyDiscount.apply(p, item) : sellBonus.apply(p, item));
        long extra = Math.round(Math.abs(event.getAmount()) * fraction);
        if (extra <= 0) {
            return;
        }
        // No tick seguinte: a transação da loja ainda está acontecendo.
        Bukkit.getScheduler().runTask(plugin, () -> AincradEconomyAPI.deposit(p, extra, buying ? "life:desconto" : "life:bonus_venda"));
    }
}
