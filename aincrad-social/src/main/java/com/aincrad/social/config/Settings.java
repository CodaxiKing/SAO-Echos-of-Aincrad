package com.aincrad.social.config;

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
    }
}
