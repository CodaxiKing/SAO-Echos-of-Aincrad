package com.aincrad.reputation.hook;

import com.aincrad.bosses.api.AincradBossesAPI;
import org.bukkit.entity.Player;

/** Usa a API do AincradBosses. Só é carregada quando ele está ligado (ver {@link Hooks}). */
final class BossesBridge {

    private BossesBridge() {
    }

    /** O id do chefe contra quem o jogador está lutando, ou null. */
    static String fight(Player p) {
        return AincradBossesAPI.isFighting(p) ? AincradBossesAPI.getFight(p) : null;
    }
}
