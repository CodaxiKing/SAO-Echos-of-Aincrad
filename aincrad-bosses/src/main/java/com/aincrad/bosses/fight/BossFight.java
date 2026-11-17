package com.aincrad.bosses.fight;

import com.aincrad.bosses.data.RoomStore;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.DamageLedger;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** O estado de uma luta em andamento. As regras ficam no {@link FightManager}. */
public final class BossFight {

    /** Em que pé a luta está. */
    public enum Stage {
        /** Alguém entrou na sala: contagem para a porta fechar (outros ainda entram). */
        ESPERA,
        /** Porta fechada, chefe parado e invulnerável. */
        INTRO,
        LUTA
    }

    final BossDef def;
    /** Sala (null = chefe invocado solto, sem sala). */
    final RoomStore.Room room;
    /** Onde o chefe nasce (e para onde volta se sair da sala). */
    final Location spawn;
    Stage stage = Stage.ESPERA;
    LivingEntity entity;
    /** Tick do gerenciador em que a etapa atual começou. */
    long stageStart;
    /** Quem está lutando (vivo e na sala). Quem sai não volta. */
    final Set<UUID> fighters = new HashSet<>();
    /** Todos os que começaram a luta. */
    final Set<UUID> started = new HashSet<>();
    final DamageLedger ledger = new DamageLedger();
    /** Último tick em que cada lutador andou ou acertou o chefe (para saber quem está ausente). */
    final Map<UUID, Long> lastActive = new HashMap<>();
    /** Quem saiu do servidor no meio da luta: não volta e não recebe recompensa. */
    final Set<UUID> quit = new HashSet<>();
    int phase;
    /** Índice da habilidade na fase → tick em que fica pronta. */
    final Map<Integer, Long> ready = new HashMap<>();
    long nextSkill;
    long furyUntil;
    double furyTimes = 1;
    final List<LivingEntity> minions = new ArrayList<>();
    final BossBar bar;
    final Set<UUID> viewers = new HashSet<>();
    boolean doorClosed;
    boolean over;

    BossFight(BossDef def, RoomStore.Room room, Location spawn, BossBar bar, long now) {
        this.def = def;
        this.room = room;
        this.spawn = spawn;
        this.bar = bar;
        this.stageStart = now;
    }

    public BossDef def() {
        return def;
    }

    public Stage stage() {
        return stage;
    }

    public LivingEntity entity() {
        return entity;
    }

    public boolean isFighter(Player p) {
        return fighters.contains(p.getUniqueId());
    }

    /** O lugar faz parte da luta? (dentro da sala; sem sala, até 48 blocos do ponto do chefe) */
    public boolean inArena(Location l) {
        if (room != null && room.box != null) {
            return room.box.contains(l);
        }
        return l != null && l.getWorld() != null && l.getWorld().equals(spawn.getWorld()) && l.distanceSquared(spawn) <= 48 * 48;
    }

    BossDef.Phase currentPhase() {
        return def.phases().get(phase);
    }

    /** Multiplicador de dano do chefe agora (fase × fúria). */
    double damageTimes(long now) {
        return currentPhase().damageTimes() * (now < furyUntil ? furyTimes : 1);
    }
}
