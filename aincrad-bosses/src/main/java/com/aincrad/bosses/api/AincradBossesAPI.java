package com.aincrad.bosses.api;

import com.aincrad.bosses.data.BossStateStore;
import com.aincrad.bosses.data.RoomStore;
import com.aincrad.bosses.fight.BossFight;
import com.aincrad.bosses.fight.FightManager;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * API do AincradBosses. Métodos estáticos e a {@link #instance()} registrada no ServicesManager.
 * Use na thread principal. Eventos: {@link BossFightStartEvent}, {@link BossDamageEvent},
 * {@link BossPhaseChangeEvent}, {@link BossDefeatEvent} e {@link BossFightEndEvent}.
 */
public final class AincradBossesAPI {

    /**
     * Um chefe, para placas e menus.
     *
     * @param door       o meio do vão da porta, logo acima dele (null se não marcada ou em mundo não carregado)
     * @param room       centro da sala, no chão (null sem sala)
     * @param defeated   já foi derrotado na rede
     * @param defeatedBy quem deu o Last Attack na primeira vitória (null se ainda não derrotado)
     * @param fighting   há uma luta agora neste servidor
     */
    public record BossInfo(String id, String name, int level, int floor, Location door, Location room, boolean defeated,
                           String defeatedBy, boolean fighting) {
    }

    private static final AincradBossesAPI INSTANCE = new AincradBossesAPI();
    private static FightManager fights;

    private AincradBossesAPI() {
    }

    public static void init(FightManager manager) {
        fights = manager;
    }

    public static AincradBossesAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return fights != null;
    }

    public static List<BossInfo> getBosses() {
        List<BossInfo> out = new ArrayList<>();
        if (fights == null) {
            return out;
        }
        for (BossDef d : fights.registry().all()) {
            RoomStore.Room r = fights.rooms().get(d.id());
            Cuboid box = r == null ? null : r.box;
            World w = box == null ? null : Bukkit.getWorld(box.world());
            Location door = null;
            if (w != null && !r.door.isEmpty()) {
                double x = 0;
                double z = 0;
                int top = Integer.MIN_VALUE;
                for (int[] b : r.door) {
                    x += b[0] + 0.5;
                    z += b[2] + 0.5;
                    top = Math.max(top, b[1]);
                }
                door = new Location(w, x / r.door.size(), top + 1, z / r.door.size());
            }
            Location room = w == null ? null : new Location(w, box.centerX(), box.minY(), box.centerZ());
            BossStateStore.State s = fights.states().get(d.id());
            out.add(new BossInfo(d.id(), d.name(), d.level(), d.floor(), door, room, s != null && s.kills() > 0,
                    s == null ? null : s.lastAttack(), fights.fight(d.id()) != null));
        }
        return out;
    }

    /** O chefe deste andar já foi derrotado? (sem chefe cadastrado: false) */
    public static boolean isDefeated(int floor) {
        if (fights == null) {
            return false;
        }
        for (BossDef d : fights.registry().all()) {
            if (d.floor() == floor && fights.states().defeated(d.id())) {
                return true;
            }
        }
        return false;
    }

    /** O lugar está dentro da sala de algum chefe? */
    public static boolean isBossRoom(Location location) {
        return fights != null && fights.isBossRoom(location);
    }

    /** O lugar fica numa sala de chefe marcada como anti-cristal (o AincradItems não deixa usar cristais lá)? */
    public static boolean isCrystalBlocked(Location location) {
        if (fights == null || location == null) {
            return false;
        }
        for (BossDef d : fights.registry().all()) {
            RoomStore.Room r = fights.rooms().get(d.id());
            if (d.antiCrystal() && r != null && r.box != null && r.box.contains(location)) {
                return true;
            }
        }
        return false;
    }

    /** O jogador está numa luta de chefe agora? */
    public static boolean isFighting(Player player) {
        return fights != null && fights.fightOf(player) != null;
    }

    /** Id do chefe com quem o jogador está lutando, ou null. */
    public static String getFight(Player player) {
        BossFight f = fights == null ? null : fights.fightOf(player);
        return f == null ? null : f.def().id();
    }
}
