package com.aincrad.social.config;

import com.aincrad.social.duel.DuelMode;
import com.aincrad.social.party.PartyRules;
import org.bukkit.configuration.file.YamlConfiguration;

/** Valores de config.yml. Recriado a cada reload. */
public final class Settings {

    public final int maxFriends;
    public final boolean announceFriends;
    public final int partyMax;
    public final int inviteSeconds;
    public final boolean friendlyFire;
    public final String partyChatPrefix;
    public final boolean messagesFriendsOnly;
    public final int inboxMax;
    public final double nearbyRadius;
    public final int typeSeconds;
    /** Onde guardar os dados: auto (banco do core se ele existir), banco ou arquivo. */
    public final String storageMode;
    public final boolean partyShareXp;
    public final double partyXpRadius;
    public final double partyXpBonus;
    public final PartyRules.DropRule partyDrops;
    public final boolean duelEnabled;
    public final int duelInviteSeconds;
    public final int duelCountdownSeconds;
    public final int duelLimitSeconds;
    public final double duelChallengeDistance;
    public final double duelRadius;
    public final double duelAnnounceRadius;
    public final boolean duelAllowTotal;
    public final DuelMode duelDefaultMode;

    public Settings(YamlConfiguration c) {
        maxFriends = Math.max(1, c.getInt("amigos.maximo", 100));
        announceFriends = c.getBoolean("amigos.avisar_entrada", true);
        partyMax = Math.max(2, c.getInt("party.tamanho_maximo", 8));
        inviteSeconds = Math.max(5, c.getInt("party.convite_expira_segundos", 60));
        friendlyFire = c.getBoolean("party.fogo_amigo", false);
        partyChatPrefix = c.getString("party.chat_prefixo", "@");
        messagesFriendsOnly = c.getBoolean("mensagens.so_amigos", true);
        inboxMax = Math.max(1, c.getInt("mensagens.caixa_maxima", 50));
        nearbyRadius = Math.max(1, c.getDouble("menu.raio_perto", 32));
        typeSeconds = Math.max(5, c.getInt("menu.tempo_digitar_segundos", 60));
        storageMode = String.valueOf(c.getString("dados.onde", "auto")).toLowerCase(java.util.Locale.ROOT);
        partyShareXp = c.getBoolean("party.xp.dividir", true);
        partyXpRadius = Math.max(1, c.getDouble("party.xp.raio", 32));
        partyXpBonus = Math.max(0, c.getDouble("party.xp.bonus_por_membro", 0.1));
        partyDrops = PartyRules.DropRule.parse(c.getString("party.drops", "livre"));
        duelEnabled = c.getBoolean("duelo.ativo", true);
        duelInviteSeconds = Math.max(5, c.getInt("duelo.convite_expira_segundos", 30));
        duelCountdownSeconds = Math.max(1, c.getInt("duelo.contagem_segundos", 5));
        duelLimitSeconds = Math.max(10, c.getInt("duelo.tempo_limite_segundos", 180));
        duelChallengeDistance = Math.max(2, c.getDouble("duelo.distancia_desafio", 16));
        duelRadius = Math.max(5, c.getDouble("duelo.raio", 30));
        duelAnnounceRadius = Math.max(0, c.getDouble("duelo.raio_aviso", 32));
        duelAllowTotal = c.getBoolean("duelo.modo_total", true);
        DuelMode def = DuelMode.parse(c.getString("duelo.modo_padrao", "primeiro_golpe"));
        duelDefaultMode = def == null || (def.lethal() && !duelAllowTotal) ? DuelMode.PRIMEIRO_GOLPE : def;
    }
}
