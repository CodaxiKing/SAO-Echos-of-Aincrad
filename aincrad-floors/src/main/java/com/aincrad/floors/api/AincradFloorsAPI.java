package com.aincrad.floors.api;

import com.aincrad.floors.FloorService;
import com.aincrad.floors.model.Floor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * API do AincradFloors. Métodos estáticos (dá para chamar por reflexão) e a {@link #instance()}
 * registrada no ServicesManager do Bukkit. Use na thread principal. Sem o plugin ligado, os
 * métodos devolvem valores neutros.
 *
 * <p>Eventos: {@link FloorUnlockEvent} (cancelável), {@link PlayerEnterFloorEvent} e
 * {@link GateTeleportEvent} (cancelável).
 */
public final class AincradFloorsAPI {

    private static final AincradFloorsAPI INSTANCE = new AincradFloorsAPI();
    private static FloorService floors;
    private static com.aincrad.floors.DeathGameService game;

    private AincradFloorsAPI() {
    }

    public static void init(FloorService service) {
        floors = service;
    }

    public static AincradFloorsAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return floors != null;
    }

    /** O andar está liberado na rede? (o andar 1 sempre está) */
    public static boolean isUnlocked(int floor) {
        return floors != null && floors.isUnlocked(floor);
    }

    /** A linha de frente: o maior andar liberado em sequência. */
    public static int getFrontline() {
        return floors == null ? 1 : floors.frontline();
    }

    /** Quantos andares existem em floors.yml. */
    public static int getFloorCount() {
        return floors == null ? 0 : floors.registry().size();
    }

    /** Número do andar deste servidor que contém o lugar (0 = nenhum). */
    public static int getFloorAt(Location location) {
        Floor f = floors == null ? null : floors.floorAt(location);
        return f == null ? 0 : f.number();
    }

    /** Andar em que o jogador está (0 = nenhum). */
    public static int getFloor(Player player) {
        return floors == null ? 0 : floors.currentFloor(player);
    }

    public static String getFloorName(int floor) {
        return floors == null ? "Andar " + floor : floors.name(floor);
    }

    /**
     * Libera o andar (o AincradBosses chama quando o chefe morre). Dispara o
     * {@link FloorUnlockEvent}, grava, dá acesso aos participantes e anuncia na rede.
     *
     * @param participants quem participou da vitória (pode ser vazio)
     * @return true se liberou agora (false: já estava, não existe ou foi cancelado)
     */
    public static boolean unlockFloor(int floor, Collection<UUID> participants) {
        // O chefe do andar anterior caiu. Se era o andar final, o jogo acaba aqui e nada é liberado.
        if (game != null && game.cleared(floor - 1, participants == null ? List.of() : participants)) {
            return false;
        }
        return floors != null && floors.unlock(floor, participants == null ? List.of() : participants) == FloorService.UnlockResult.OK;
    }

    /** Chamado pelo plugin ao ligar (e com null ao desligar). */
    public static void game(com.aincrad.floors.DeathGameService service) {
        game = service;
    }

    private static com.aincrad.floors.MapService map;

    /** Chamado pelo plugin ao ligar (e com null ao desligar). */
    public static void map(com.aincrad.floors.MapService service) {
        map = service;
    }

    /** O jogador já explorou (a pé, ou com dados de mapa) o setor onde o lugar fica? */
    public static boolean isExplored(Player player, Location location) {
        return map != null && map.explored(player, location);
    }

    /** O dia do anúncio já aconteceu e o jogo ainda não foi vencido (o logout está escondido). */
    public static boolean isDeathGame() {
        return game != null && game.deathGame();
    }

    /** O chefe do andar final já caiu. */
    public static boolean isGameWon() {
        return game != null && game.ended();
    }

    public static boolean lockFloor(int floor) {
        return floors != null && floors.lock(floor);
    }

    /** O jogador pode pisar no andar (liberado, modo e bypass)? */
    public static boolean canEnter(Player player, int floor) {
        return floors != null && floors.canEnter(player, floor);
    }

    /**
     * Um combate combinado (duelo): até a hora dada (milissegundos; 0 encerra), os dois podem se
     * ferir mesmo dentro de zona segura. O AincradSocial chama isto ao começar e ao acabar um duelo.
     */
    public static void exemptPvp(UUID a, UUID b, long until) {
        com.aincrad.floors.listener.SafeZoneListener.exempt(a, b, until);
    }

    /** O lugar é uma cidade (zona segura)? */
    public static boolean isSafeZone(Location location) {
        return floors != null && floors.isSafeZone(location);
    }

    /** Ponto de chegada do andar, se ele fica neste servidor e já foi definido; senão null. */
    public static Location getSpawn(int floor) {
        Floor f = floors == null ? null : floors.floor(floor);
        return f == null || !f.isOn(floors.server()) ? null : FloorService.parseSpawn(f.spawn());
    }

    /** Leva o jogador ao andar (como um Teleport Gate). @return true se o teleporte começou */
    public static boolean teleport(Player player, int floor) {
        return floors != null && floors.teleport(player, floor);
    }
}
