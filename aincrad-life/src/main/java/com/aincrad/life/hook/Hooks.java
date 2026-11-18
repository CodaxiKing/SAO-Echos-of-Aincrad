package com.aincrad.life.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Pontes para os plugins opcionais. {@link EcoBridge} e {@link FloorsBridge} usam as APIs deles
 * e só são tocadas quando estão ligados; o AincradEffects vai por reflexão.
 */
public final class Hooks {

    private final Plugin plugin;

    public Hooks(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasEconomy() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradEconomy");
    }

    public boolean hasFloors() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradFloors");
    }

    public boolean hasRpg() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradRPG");
    }

    public boolean hasItems() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradItems");
    }

    /** Dá cristais do AincradItems ("cura", "antidoto"...) ao jogador. @return false sem o plugin ou com tipo desconhecido */
    public boolean giveCrystal(Player p, String type, int amount) {
        Plugin items = Bukkit.getPluginManager().getPlugin("AincradItems");
        if (items == null || !items.isEnabled()) {
            return false;
        }
        try {
            Class<?> api = items.getClass().getClassLoader().loadClass("com.aincrad.items.api.AincradItemsAPI");
            return Boolean.TRUE.equals(api.getMethod("giveCrystal", Player.class, String.class, int.class).invoke(null, p, type, amount));
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("AincradItems incompatível (dar cristal): " + e);
            return false;
        }
    }

    public boolean hasEffects() {
        return Bukkit.getPluginManager().isPluginEnabled("AincradEffects");
    }

    /**
     * Cobra um preço: em Col, com a economia; sem ela, em itens ({@code fallback} × quantidade, um
     * item a cada {@code colPerItem} Col, no mínimo 1).
     *
     * @return true se cobrou
     */
    public boolean charge(Player p, long col, Material fallback, long colPerItem, String reason) {
        if (col <= 0) {
            return true;
        }
        if (hasEconomy()) {
            try {
                return EcoBridge.withdraw(p, col, reason);
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("AincradEconomy incompatível (cobrança): " + e);
                return false;
            }
        }
        int items = (int) Math.max(1, Math.ceil(col / (double) Math.max(1, colPerItem)));
        if (!p.getInventory().containsAtLeast(new ItemStack(fallback), items)) {
            return false;
        }
        p.getInventory().removeItem(new ItemStack(fallback, items));
        return true;
    }

    /** Texto do preço: "500 Col" ou "5x Lingote de ouro" (sem economia). */
    public String price(long col, Material fallback, long colPerItem) {
        if (hasEconomy()) {
            try {
                return EcoBridge.format(col);
            } catch (LinkageError | RuntimeException e) {
                return col + " Col";
            }
        }
        return (int) Math.max(1, Math.ceil(col / (double) Math.max(1, colPerItem))) + "x " + fallback.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    /** Manda um item para a caixa de retirada do AincradEconomy. @return false sem a economia */
    public boolean mail(UUID player, ItemStack item, String reason) {
        if (!hasEconomy()) {
            return false;
        }
        try {
            return EcoBridge.mail(player, item, reason);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (caixa de retirada): " + e);
            return false;
        }
    }

    /** Deposita Col (o jogador pode estar offline). @return false sem a economia */
    public boolean pay(UUID player, long col, String reason) {
        if (col <= 0 || !hasEconomy()) {
            return false;
        }
        try {
            return EcoBridge.deposit(player, col, reason);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (depósito): " + e);
            return false;
        }
    }

    /** Abre (ou fecha) as casas de um jogador para outro (AincradEconomy). Sem a economia, não faz nada. */
    public void houseAccess(UUID owner, UUID member, boolean allow) {
        if (!hasEconomy()) {
            return;
        }
        try {
            EcoBridge.houseAccess(owner, member, allow);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (acesso à casa): " + e);
        }
    }

    /** O lugar é zona segura do AincradFloors? (sem o plugin: false) */
    public boolean safeZone(org.bukkit.Location l) {
        if (!hasFloors()) {
            return false;
        }
        try {
            return FloorsBridge.safeZone(l);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** O andar onde o jogador está (0 sem o AincradFloors). */
    public int floorOf(Player p) {
        if (!hasFloors()) {
            return 0;
        }
        try {
            return FloorsBridge.floorOf(p);
        } catch (LinkageError | RuntimeException e) {
            return 0;
        }
    }

    /** O level do jogador no AincradRPG (0 sem o plugin). */
    public int rpgLevel(Player p) {
        if (!hasRpg()) {
            return 0;
        }
        try {
            return RpgBridge.level(p);
        } catch (LinkageError | RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ menu do AincradUI

    private Object ui() {
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals("com.aincrad.ui.api.AincradUIAPI")) {
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                return r == null ? null : r.getProvider();
            }
        }
        return null;
    }

    /**
     * Põe uma entrada no menu do AincradUI. Ao clicar, o menu fecha e {@code action} roda.
     *
     * @param icon id de glifo do AincradUI (ex.: "under_icons/friends")
     * @return false sem o AincradUI (ou se ele mudou de API)
     */
    public boolean uiEntry(String id, String icon, net.kyori.adventure.text.Component name, java.util.function.Consumer<Player> action) {
        Object api = ui();
        if (api == null) {
            return false;
        }
        try {
            Class<?> menuAction = api.getClass().getClassLoader().loadClass("com.aincrad.ui.api.MenuAction");
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(menuAction.getClassLoader(), new Class<?>[]{menuAction}, (self, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        default -> "AincradLife:" + id;
                    };
                }
                if (args != null && args.length > 0 && args[0] instanceof Player p) {
                    try {
                        api.getClass().getMethod("closeMenu", Player.class).invoke(api, p);
                    } catch (ReflectiveOperationException e) {
                        // sem closeMenu: a tela do plugin abre por cima
                    }
                    action.accept(p);
                }
                return null;
            });
            api.getClass().getMethod("registerMenuEntry", String.class, String.class, net.kyori.adventure.text.Component.class, String.class, menuAction)
                    .invoke(api, id, icon, name, "config", proxy);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("AincradUI incompatível (entrada de menu " + id + "): " + e);
            return false;
        }
    }

    /** Tira uma entrada do menu do AincradUI. */
    public void uiRemove(String id) {
        Object api = ui();
        if (api == null) {
            return;
        }
        try {
            api.getClass().getMethod("unregisterMenuEntry", String.class).invoke(api, id);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // o AincradUI já desligou
        }
    }

    /** O dono da casa do AincradEconomy onde está o lugar. Null se não há casa ali (ou sem a economia). */
    public UUID houseOwner(org.bukkit.Location l) {
        if (!hasEconomy()) {
            return null;
        }
        try {
            return EcoBridge.houseOwner(l);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (casas): " + e);
            return null;
        }
    }

    // ------------------------------------------------------------------ custódia (encomendas)

    /** Põe Col do jogador em custódia. @return o id da custódia, ou null sem saldo ou sem a economia */
    public String hold(Player p, long col, String reason) {
        if (!hasEconomy()) {
            return null;
        }
        try {
            return EcoBridge.hold(p, col, reason);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (custódia): " + e);
            return null;
        }
    }

    /** Libera parte da custódia para alguém. O resultado chega na thread principal. */
    public CompletableFuture<Boolean> release(String hold, UUID to, long col, String reason) {
        try {
            return hasEconomy() ? EcoBridge.release(hold, to, col, reason) : CompletableFuture.completedFuture(false);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (custódia): " + e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /** Devolve ao dono o que resta na custódia. @return o valor devolvido */
    public CompletableFuture<Long> refund(String hold, String reason) {
        try {
            return hasEconomy() ? EcoBridge.refund(hold, reason) : CompletableFuture.completedFuture(0L);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (custódia): " + e);
            return CompletableFuture.completedFuture(0L);
        }
    }

    /** Tira de circulação parte da custódia (a taxa de uma entrega de um contrato que continua). */
    public CompletableFuture<Boolean> burn(String hold, long col) {
        try {
            return hasEconomy() ? EcoBridge.burn(hold, col) : CompletableFuture.completedFuture(false);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (custódia): " + e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /** Tira de circulação o que resta na custódia (a taxa do quadro). */
    public CompletableFuture<Long> burn(String hold) {
        try {
            return hasEconomy() ? EcoBridge.burn(hold) : CompletableFuture.completedFuture(0L);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradEconomy incompatível (custódia): " + e);
            return CompletableFuture.completedFuture(0L);
        }
    }

    /** Dá um level do AincradRPG a um mob (vida e dano escalados por ele). Sem o RPG, não faz nada. */
    public void mobLevel(org.bukkit.entity.LivingEntity e, int level) {
        if (!hasRpg()) {
            return;
        }
        try {
            RpgBridge.setMobLevel(e, level);
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("AincradRPG incompatível (level do mob): " + ex);
        }
    }

    /**
     * Cria um item de um modelo do AincradRPG (items.yml), na raridade dada.
     *
     * @return o item, ou null sem o RPG ou se o modelo não existe
     */
    public ItemStack rpgItem(String id, String rarity) {
        if (!hasRpg()) {
            return null;
        }
        try {
            return RpgBridge.createItem(id, rarity);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradRPG incompatível (criar item): " + e);
            return null;
        }
    }

    /** Leva o jogador ao andar 1 (a cidade inicial), neste servidor ou em outro. @return false sem o AincradFloors */
    public boolean toFirstFloor(Player p) {
        if (!hasFloors()) {
            return false;
        }
        try {
            return FloorsBridge.teleport(p, 1);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("AincradFloors incompatível (retorno): " + e);
            return false;
        }
    }

    /** O lugar está numa sala de chefe do AincradBosses? (sem o plugin: false) */
    public boolean inBossRoom(org.bukkit.Location l) {
        Plugin bosses = Bukkit.getPluginManager().getPlugin("AincradBosses");
        if (bosses == null || !bosses.isEnabled() || l == null) {
            return false;
        }
        try {
            Class<?> api = bosses.getClass().getClassLoader().loadClass("com.aincrad.bosses.api.AincradBossesAPI");
            return Boolean.TRUE.equals(api.getMethod("isBossRoom", org.bukkit.Location.class).invoke(null, l));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Uma raid marcada no quadro do AincradBosses: o líder, a hora e os inscritos (com o líder). */
    public record Raid(UUID leader, long at, java.util.List<UUID> members) {
    }

    /** A raid com esse número. Null se não existe, já passou, ou o AincradBosses não está no servidor. */
    public Raid raid(long id) {
        Plugin bosses = Bukkit.getPluginManager().getPlugin("AincradBosses");
        if (bosses == null || !bosses.isEnabled()) {
            return null;
        }
        try {
            Class<?> api = bosses.getClass().getClassLoader().loadClass("com.aincrad.bosses.api.AincradBossesAPI");
            Object info = api.getMethod("getRaid", long.class).invoke(null, id);
            if (info == null) {
                return null;
            }
            java.util.List<UUID> members = new java.util.ArrayList<>();
            for (Object o : (java.util.List<?>) info.getClass().getMethod("members").invoke(info)) {
                if (o instanceof UUID u) {
                    members.add(u);
                }
            }
            return new Raid((UUID) info.getClass().getMethod("leader").invoke(info), (Long) info.getClass().getMethod("at").invoke(info), members);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("AincradBosses incompatível (raid): " + e);
            return null;
        }
    }

    private Object social() {
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals("com.aincrad.social.api.AincradSocialAPI")) {
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                return r == null ? null : r.getProvider();
            }
        }
        return null;
    }

    /** A guilda do jogador no AincradSocial: {id, nome}. Null sem guilda ou sem o plugin. */
    public String[] guild(UUID player) {
        Object api = social();
        if (api == null) {
            return null;
        }
        try {
            Object info = api.getClass().getMethod("getGuild", UUID.class).invoke(api, player);
            if (info == null) {
                return null;
            }
            return new String[]{String.valueOf(info.getClass().getMethod("id").invoke(info)), String.valueOf(info.getClass().getMethod("name").invoke(info))};
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Soma XP à guilda do jogador (AincradSocial). Sem o plugin, não faz nada. */
    public void guildXp(UUID player, long amount, String reason) {
        Object api = social();
        if (api == null || amount <= 0) {
            return;
        }
        try {
            api.getClass().getMethod("addGuildXp", UUID.class, long.class, String.class).invoke(api, player, amount, reason);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("AincradSocial incompatível (XP de guilda): " + e);
        }
    }

    private Object effects() {
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (c.getName().equals("com.aincrad.effects.api.AincradEffectsAPI")) {
                RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                return r == null ? null : r.getProvider();
            }
        }
        return null;
    }

    /** O jogador carregou o resource pack do servidor? (sem o AincradEffects: false) */
    public boolean hasPack(Player p) {
        Object api = effects();
        if (api == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(api.getClass().getMethod("hasPack", Player.class).invoke(api, p));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Entrega a pasta resourcepack/ ao pack único do AincradEffects. */
    public boolean registerPack(File folder) {
        Object api = effects();
        if (api == null) {
            return false;
        }
        try {
            api.getClass().getMethod("registerPackFolder", Plugin.class, File.class).invoke(api, plugin, folder);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Não foi possível registrar os modelos no AincradEffects: " + e);
            return false;
        }
    }
}
