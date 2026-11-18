package com.aincrad.social.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Ligações opcionais com outros plugins, por reflexão (nenhum é obrigatório para compilar ou
 * rodar). Cada método diz o que vale quando o plugin não existe.
 *
 * <ul>
 *   <li>Col: API do AincradEconomy (com.aincrad.economy.api.AincradEconomyAPI) ou Vault;</li>
 *   <li>andar liberado: AincradFloors (com.aincrad.floors.api.AincradFloorsAPI#isUnlocked);</li>
 *   <li>quest concluída: AincradQuests (com.aincrad.quests.api.AincradQuestsAPI#hasCompleted);</li>
 *   <li>level: AincradRPG (com.aincrad.rpg.api.AincradRPGAPI#getLevel) ou o level de XP;</li>
 *   <li>cursor vermelho: AincradReputation (com.aincrad.reputation.api.AincradReputationAPI#getCursorColor).</li>
 * </ul>
 */
public final class Hooks {

    private final Logger log;
    private Object economy;
    private Method ecoBalance;
    private Method ecoWithdraw;
    private Method ecoDeposit;
    private boolean vault;
    private Object floors;
    private Object quests;
    private Object rpg;
    private Object reputation;

    public Hooks(Logger log) {
        this.log = log;
    }

    /** Procura os plugins (chamar depois que todos ligaram). */
    public void detect() {
        economy = service("com.aincrad.economy.api.AincradEconomyAPI");
        vault = false;
        try {
            if (economy != null) {
                Class<?> t = economy.getClass();
                ecoBalance = find(t, "getBalance", Player.class);
                ecoWithdraw = find(t, "withdraw", Player.class, long.class, String.class);
                ecoDeposit = find(t, "deposit", Player.class, long.class, String.class);
            } else {
                economy = service("net.milkbowl.vault.economy.Economy");
                if (economy != null) {
                    Class<?> t = Class.forName("net.milkbowl.vault.economy.Economy", false, economy.getClass().getClassLoader());
                    ecoBalance = t.getMethod("getBalance", OfflinePlayer.class);
                    ecoWithdraw = t.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                    ecoDeposit = t.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                    vault = true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warning("Economia encontrada, mas incompatível: " + e.getMessage());
            economy = null;
        }
        floors = service("com.aincrad.floors.api.AincradFloorsAPI");
        quests = service("com.aincrad.quests.api.AincradQuestsAPI");
        rpg = service("com.aincrad.rpg.api.AincradRPGAPI");
        reputation = service("com.aincrad.reputation.api.AincradReputationAPI");
        log.info("Guilda — economia: " + (economy == null ? "nenhuma" : vault ? "Vault" : "AincradEconomy")
                + ", andares: " + (floors != null) + ", quests: " + (quests != null)
                + ", rpg: " + (rpg != null) + ", reputação: " + (reputation != null));
    }

    private static Object service(String className) {
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals(className)) {
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                return r == null ? null : r.getProvider();
            }
        }
        return null;
    }

    private static Method find(Class<?> t, String name, Class<?>... params) throws NoSuchMethodException {
        return t.getMethod(name, params);
    }

    private Object call(Object target, String name, Class<?>[] params, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(name, params).invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Col

    public boolean hasEconomy() {
        return economy != null;
    }

    public long balance(Player p) {
        if (economy == null) {
            return 0;
        }
        try {
            Object r = ecoBalance.invoke(economy, p);
            return r instanceof Number n ? (long) Math.floor(n.doubleValue()) : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** Tira Col do jogador. Sem economia, sempre dá certo (não cobra). */
    public boolean withdraw(Player p, long amount, String reason) {
        if (economy == null || amount <= 0) {
            return true;
        }
        try {
            Object r = vault ? ecoWithdraw.invoke(economy, p, (double) amount) : ecoWithdraw.invoke(economy, p, amount, reason);
            return success(r);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public boolean deposit(Player p, long amount, String reason) {
        if (economy == null || amount <= 0) {
            return economy != null || amount <= 0;
        }
        try {
            Object r = vault ? ecoDeposit.invoke(economy, p, (double) amount) : ecoDeposit.invoke(economy, p, amount, reason);
            return success(r);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Vault devolve EconomyResponse; a API do Aincrad, boolean. */
    private static boolean success(Object r) {
        if (r instanceof Boolean b) {
            return b;
        }
        if (r == null) {
            return false;
        }
        try {
            return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ requisitos e reputação

    /** Andar liberado? Sem AincradFloors, considera que sim. */
    public boolean floorUnlocked(int floor) {
        Object r = call(floors, "isUnlocked", new Class<?>[]{int.class}, floor);
        return r == null || Boolean.TRUE.equals(r);
    }

    /** Quest concluída? Sem AincradQuests (ou sem quest configurada), considera que sim. */
    public boolean questDone(Player p, String quest) {
        if (quest == null || quest.isBlank()) {
            return true;
        }
        Object r = call(quests, "hasCompleted", new Class<?>[]{Player.class, String.class}, p, quest);
        return r == null || Boolean.TRUE.equals(r);
    }

    public int level(Player p) {
        Object r = call(rpg, "getLevel", new Class<?>[]{Player.class}, p);
        return r instanceof Number n ? n.intValue() : p.getLevel();
    }

    /** Cursor vermelho no AincradReputation? Sem ele, nunca. */
    public boolean redCursor(Player p) {
        Object r = call(reputation, "getCursorColor", new Class<?>[]{Player.class}, p);
        return r != null && String.valueOf(r).equalsIgnoreCase("RED");
    }

    /** Dá XP do AincradRPG (passa pelo XpGainEvent dele, com o motivo dado). Sem o RPG, nada. */
    public void addXp(Player p, long amount, String reason) {
        call(rpg, "addXp", new Class<?>[]{Player.class, long.class, String.class}, p, amount, reason);
    }

    /**
     * Duelo: até a hora dada (milissegundos; 0 encerra), os dois podem se ferir dentro de zona
     * segura (AincradFloors) e sem mudar a cor do cursor (AincradReputation).
     */
    public void exemptPvp(java.util.UUID a, java.util.UUID b, long until) {
        Class<?>[] sig = {java.util.UUID.class, java.util.UUID.class, long.class};
        call(floors, "exemptPvp", sig, a, b, until);
        call(reputation, "exemptPvp", sig, a, b, until);
    }

    public boolean hasFloors() {
        return floors != null;
    }

    public boolean hasQuests() {
        return quests != null;
    }
}
