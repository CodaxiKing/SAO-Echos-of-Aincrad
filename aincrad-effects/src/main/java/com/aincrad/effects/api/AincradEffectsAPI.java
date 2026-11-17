package com.aincrad.effects.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * API pública do AincradEffects, registrada no ServicesManager do Bukkit.
 *
 * <pre>
 * AincradEffectsAPI fx = Bukkit.getServicesManager().load(AincradEffectsAPI.class);
 * fx.play("congratulations", player, Map.of("item", "Coat of Midnight"));
 * </pre>
 *
 * Todos os métodos devem ser chamados na thread principal.
 */
public interface AincradEffectsAPI {

    /**
     * Toca um efeito com o jogador como alvo: os passos no mundo acontecem na posição dele,
     * {jogador} vira o nome dele e o público é o do YAML do efeito.
     *
     * @return false se o efeito não existir
     */
    boolean play(String efeito, Player alvo, Map<String, String> variaveis);

    /**
     * Toca um efeito num local para quem estiver a até {@code raio} blocos.
     * Com raio &lt;= 0, usa o público do YAML do efeito.
     */
    boolean play(String efeito, Location local, double raio, Map<String, String> variaveis);

    /** Toca um efeito num local só para os espectadores dados. */
    boolean play(String efeito, Location local, Collection<? extends Player> espectadores, Map<String, String> variaveis);

    /** Para os efeitos em que o jogador é alvo ou espectador. @return quantos pararam */
    int stop(Player jogador);

    /** Para todos os efeitos em andamento. @return quantos pararam */
    int stopAll();

    boolean exists(String efeito);

    Set<String> effects();

    /**
     * Coloca uma pasta no resource pack único do servidor. A pasta tem a estrutura de um pack
     * (assets/...), sem pack.mcmeta. Chamar de novo com o mesmo dono troca a pasta e reconstrói o pack.
     */
    void registerPackFolder(Plugin dono, File pasta);

    /** O jogador está com o resource pack carregado? */
    boolean hasPack(Player jogador);
}
