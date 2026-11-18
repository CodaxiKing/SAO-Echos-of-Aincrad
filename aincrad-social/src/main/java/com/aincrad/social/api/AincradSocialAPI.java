package com.aincrad.social.api;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * API pública do AincradSocial, registrada no ServicesManager do Bukkit.
 * Os eventos de guilda ficam em {@code com.aincrad.social.api.event}. Os duelos têm o plugin deles,
 * o AincradDuels ({@code com.aincrad.duels.api.AincradDuelsAPI}).
 *
 * <pre>
 * AincradSocialAPI social = Bukkit.getServicesManager().load(AincradSocialAPI.class);
 * social.recordBossKill(participantes, 1, quemDeuOLastAttack);   // AincradBosses
 * GuildInfo g = social.getGuild(jogador.getUniqueId());          // AincradFloors (anúncio)
 * </pre>
 */
public interface AincradSocialAPI {

    /** Resumo de uma guilda para outros plugins. {@code rank} é o cargo de quem foi consultado. */
    record GuildInfo(UUID id, String name, String tag, String emblem, int level, String rank, boolean red) {
    }

    /** Membros da party do jogador (com ele), ou lista vazia. */
    List<UUID> getParty(UUID player);

    /** Guilda do jogador, ou null. */
    GuildInfo getGuild(UUID player);

    /** Mesma party, mesma guilda ou guildas aliadas (para não se ferirem, dividir loot etc.). */
    boolean areAllies(UUID a, UUID b);

    /** Soma XP à guilda do jogador. */
    void addGuildXp(UUID player, long amount, String reason);

    /**
     * Um chefe de andar foi derrotado: cada guilda com membros entre os participantes ganha a
     * vitória e o XP de chefe; a guilda de quem deu o Last Attack ganha também o bônus.
     */
    void recordBossKill(Collection<UUID> participants, int floor, UUID lastAttacker);

    /** Um membro concluiu uma quest: XP de quest para a guilda. */
    void questCompleted(UUID player);

    /**
     * Taxa da guilda sobre Col ganho caçando (chamado pelo plugin de economia antes de pagar).
     * @return quanto vai para o tesouro (descontar do que o jogador recebe)
     */
    long taxIncome(Player player, long income);
}
