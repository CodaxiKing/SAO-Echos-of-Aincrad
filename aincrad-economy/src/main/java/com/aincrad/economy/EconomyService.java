package com.aincrad.economy;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.economy.api.TransactionEvent;
import com.aincrad.economy.data.Accounts;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Regras do Col: conferir saldo, disparar o {@link TransactionEvent}, aplicar. Tudo na thread
 * principal. Saque só de quem está online neste servidor (o saldo dele está em memória);
 * depósito vale para qualquer um, online ou não, em qualquer servidor da rede.
 */
public final class EconomyService {

    private final Plugin plugin;
    private final Accounts accounts;
    private final Messages msg;
    private ConfigFile config;
    private String singular = "Col";
    private String plural = "Col";

    public EconomyService(Plugin plugin, Accounts accounts, Messages msg, ConfigFile config) {
        this.plugin = plugin;
        this.accounts = accounts;
        this.msg = msg;
        reload(config);
    }

    public void reload(ConfigFile c) {
        this.config = c;
        singular = c.string("moeda.singular", "Col");
        plural = c.string("moeda.plural", "Col");
        accounts.startBalance(c.integer("saldo_inicial", 100, 0, Integer.MAX_VALUE));
    }

    public ConfigFile config() {
        return config;
    }

    public Messages messages() {
        return msg;
    }

    public Accounts accounts() {
        return accounts;
    }

    public String currency(long amount) {
        return amount == 1 ? singular : plural;
    }

    /** "1.250 Col". */
    public String format(long amount) {
        return EcoMath.format(amount) + " " + currency(amount);
    }

    /** Variável {@code <col>} já formatada. */
    public TagResolver col(long amount) {
        return Messages.vars("col", format(amount));
    }

    /** Saldo de quem está online aqui (0 se não carregado). */
    public long balance(Player p) {
        return Math.max(0, accounts.cached(p.getUniqueId()));
    }

    public long balance(UUID id) {
        return Math.max(0, accounts.cached(id));
    }

    public boolean has(Player p, long amount) {
        return amount <= 0 || balance(p) >= amount;
    }

    /** Tira Col. @return false se não está online aqui, não tem saldo ou alguém cancelou */
    public boolean withdraw(UUID id, long amount, String reason) {
        if (amount <= 0) {
            return amount == 0;
        }
        long bal = accounts.cached(id);
        if (bal < amount) {
            return false;
        }
        if (!fire(id, -amount, reason)) {
            return false;
        }
        accounts.apply(id, -amount, reason);
        return true;
    }

    /** Põe Col (online ou offline, em qualquer servidor). @return false se cancelado */
    public boolean deposit(UUID id, long amount, String reason) {
        if (amount <= 0) {
            return amount == 0;
        }
        if (!fire(id, amount, reason)) {
            return false;
        }
        accounts.apply(id, amount, reason);
        return true;
    }

    /** Define o saldo de quem está online aqui (admin). */
    public boolean set(UUID id, long value, String reason) {
        long bal = accounts.cached(id);
        if (bal < 0 || value < 0) {
            return false;
        }
        long delta = value - bal;
        return delta == 0 || (delta > 0 ? deposit(id, delta, reason) : withdraw(id, -delta, reason));
    }

    /** Move Col de um jogador online aqui para qualquer outro, tudo ou nada. */
    public boolean transfer(UUID from, UUID to, long amount, long tax, String reason) {
        if (!withdraw(from, amount, reason)) {
            return false;
        }
        if (!deposit(to, amount - tax, reason)) {
            accounts.apply(from, amount, reason + ":estorno");
            return false;
        }
        return true;
    }

    private boolean fire(UUID id, long amount, String reason) {
        TransactionEvent ev = new TransactionEvent(id, amount, reason == null ? "api" : reason);
        plugin.getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    /** Jogador pelo nome: online, ou offline que já entrou (cache do servidor). Null se nunca entrou. */
    public static OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        return off != null && (off.hasPlayedBefore() || off.isOnline()) ? off : null;
    }
}
