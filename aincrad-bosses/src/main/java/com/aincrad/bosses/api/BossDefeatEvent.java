package com.aincrad.bosses.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** O chefe foi derrotado. Traz o resultado inteiro da luta (para a tela de "Congratulations"). */
public final class BossDefeatEvent extends BossEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID lastAttack;
    private final List<UUID> participants;
    private final Map<UUID, Double> damage;
    private final Map<UUID, List<ItemStack>> items;
    private final Map<UUID, Long> xp;
    private final Map<UUID, Long> col;
    private final ItemStack lastAttackItem;
    private final boolean firstKill;

    public BossDefeatEvent(String bossId, String bossName, int floor, LivingEntity entity, UUID lastAttack, List<UUID> participants,
                           Map<UUID, Double> damage, Map<UUID, List<ItemStack>> items, Map<UUID, Long> xp, Map<UUID, Long> col,
                           ItemStack lastAttackItem, boolean firstKill) {
        super(bossId, bossName, floor, entity);
        this.lastAttack = lastAttack;
        this.participants = List.copyOf(participants);
        this.damage = Map.copyOf(damage);
        this.items = Map.copyOf(items);
        this.xp = Map.copyOf(xp);
        this.col = Map.copyOf(col);
        this.lastAttackItem = lastAttackItem;
        this.firstKill = firstKill;
    }

    /** Quem deu o golpe final (null se o chefe morreu de outra coisa). */
    public UUID getLastAttack() {
        return lastAttack;
    }

    /** Quem causou o dano mínimo, do maior dano para o menor. */
    public List<UUID> getParticipants() {
        return participants;
    }

    /** Dano de todos os que acertaram o chefe. */
    public Map<UUID, Double> getDamage() {
        return damage;
    }

    /** Itens que cada um recebeu (o de Last Attack incluído). */
    public Map<UUID, List<ItemStack>> getItems() {
        return items;
    }

    /** XP que cada um recebeu. */
    public Map<UUID, Long> getXp() {
        return xp;
    }

    /** Col que cada um recebeu (vazio sem o AincradEconomy). */
    public Map<UUID, Long> getCol() {
        return col;
    }

    /** O item de Last Attack entregue a {@link #getLastAttack()} (também está em {@link #getItems()}); null se não houve. */
    public ItemStack getLastAttackItem() {
        return lastAttackItem;
    }

    /** Primeira vitória da rede sobre este chefe (a que libera o próximo andar). */
    public boolean isFirstKill() {
        return firstKill;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
