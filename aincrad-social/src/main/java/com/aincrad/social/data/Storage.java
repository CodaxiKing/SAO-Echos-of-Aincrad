package com.aincrad.social.data;

import com.aincrad.social.guild.GuildService;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Onde o AincradSocial guarda os dados. Dois jeitos: {@link YamlStorage} (arquivos, um servidor
 * só) e {@code CoreStorage} (banco do AincradCore, o mesmo para a rede inteira).
 */
public interface Storage {

    /** Amigos, pedidos, caixa de entrada e nomes. */
    SocialStore store();

    /** Lê tudo ao ligar. */
    void load(GuildService guilds) throws Exception;

    /** Amigos, pedidos, caixa ou nomes mudaram. */
    void saveSoon();

    /** Alguma guilda mudou. */
    void saveGuildsSoon();

    /** Grava o que falta e fecha (plugin desligando). */
    void shutdown();

    /** Os dados valem para a rede inteira (banco) ou só para este servidor (arquivo)? */
    boolean network();

    // ------------------------------------------------------------------ cofre da guilda

    /**
     * Pede o cofre para este servidor. Em rede, só um servidor por vez abre o cofre de uma guilda.
     *
     * @param ready chamado na thread principal: (conseguiu, itens gravados — null para usar os que já estão na memória)
     */
    void lockVault(UUID guild, BiConsumer<Boolean, String> ready);

    /** O cofre aberto mudou. */
    void saveVault(UUID guild, String data);

    /** O último jogador fechou o cofre: grava e libera para os outros servidores. */
    void unlockVault(UUID guild, String data);

    // ------------------------------------------------------------------ rede

    /**
     * Roda algo que só um servidor da rede deve fazer por vez (a manutenção das guildas).
     *
     * @param run chamado na thread principal com true se é a vez deste servidor
     */
    void exclusive(String name, Consumer<Boolean> run);

    /** Avisa os outros servidores da rede (num servidor só, não faz nada). */
    void publish(String channel, String payload);

    /** Recebe os avisos dos outros servidores, na thread principal. */
    void subscribe(String channel, Consumer<String> handler);

    /** Repassa uma linha de chat de guilda (ou de aliança) aos membros que estão em outros servidores. */
    void publishChat(Collection<UUID> guilds, Component line);
}
