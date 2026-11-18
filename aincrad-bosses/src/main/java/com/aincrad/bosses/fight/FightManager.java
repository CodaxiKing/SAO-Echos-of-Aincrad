package com.aincrad.bosses.fight;

import com.aincrad.bosses.api.BossDamageEvent;
import com.aincrad.bosses.api.BossDefeatEvent;
import com.aincrad.bosses.api.BossFightEndEvent;
import com.aincrad.bosses.api.BossFightStartEvent;
import com.aincrad.bosses.api.BossPhaseChangeEvent;
import com.aincrad.bosses.data.BossStateStore;
import com.aincrad.bosses.data.RoomStore;
import com.aincrad.bosses.hook.Hooks;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.BossRegistry;
import com.aincrad.bosses.model.DamageLedger;
import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.message.Messages;
import com.aincrad.floors.api.AincradFloorsAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * As lutas de chefe: começa quando alguém pisa na sala, fecha a porta, cuida das fases, das
 * habilidades e da barra de vida, e resolve o fim (vitória, derrota, tempo ou reset).
 */
public final class FightManager implements Listener {

    public static final String BUS_STATE = "bosses:state";
    /** De quantos em quantos ticks as lutas são atualizadas. */
    public static final int STEP = 5;
    private static final String ADMIN = "aincrad.admin";

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final RoomStore rooms;
    private final BossStateStore states;
    private final Skills skills;
    private final MythicLink mythic = new MythicLink();
    private final NamespacedKey bossKey;
    private final NamespacedKey minionKey;
    private BossRegistry registry;
    private com.aincrad.bosses.data.GuideStore guide;
    private com.aincrad.bosses.raid.RaidService raids;
    /** Uma luta por chefe. */
    private final Map<String, BossFight> fights = new HashMap<>();
    /** Quando cada chefe pode voltar depois de uma derrota dos jogadores (milissegundos). */
    private final Map<String, Long> rearm = new HashMap<>();
    private long tick;

    public FightManager(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, RoomStore rooms, BossStateStore states, BossRegistry registry) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.rooms = rooms;
        this.states = states;
        this.registry = registry;
        this.bossKey = new NamespacedKey(plugin, "chefe");
        this.minionKey = new NamespacedKey(plugin, "lacaio");
        this.skills = new Skills(plugin, this, hooks);
    }

    public void guide(com.aincrad.bosses.data.GuideStore g) {
        this.guide = g;
    }

    public void raids(com.aincrad.bosses.raid.RaidService r) {
        this.raids = r;
    }

    void sawSkill(BossFight f, BossDef.Skill s) {
        if (guide != null) {
            guide.skill(f.def.id(), f.phase, s.type());
        }
    }

    /** Ticks parado (sem andar nem acertar o chefe) para contar como ausente; 0 = desligado. */
    private long idleLimit() {
        return config.integer("abuso.ausente_segundos", 60, 0, 3600) * 20L;
    }

    private boolean idle(BossFight f, UUID id) {
        long limit = idleLimit();
        return limit > 0 && tick - f.lastActive.getOrDefault(id, f.stageStart) >= limit;
    }

    public void registry(BossRegistry r) {
        this.registry = r;
    }

    public BossRegistry registry() {
        return registry;
    }

    public RoomStore rooms() {
        return rooms;
    }

    public BossStateStore states() {
        return states;
    }

    public Messages messages() {
        return msg;
    }

    public long now() {
        return tick;
    }

    public BossFight fight(String id) {
        return fights.get(id);
    }

    /** A luta de que a entidade é o chefe, ou null. */
    public BossFight fightOf(Entity e) {
        String id = e.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        BossFight f = id == null ? null : fights.get(id);
        return f != null && f.entity != null && f.entity.getUniqueId().equals(e.getUniqueId()) ? f : null;
    }

    /** A luta em que o jogador está lutando, ou null. */
    public BossFight fightOf(Player p) {
        for (BossFight f : fights.values()) {
            if (f.isFighter(p)) {
                return f;
            }
        }
        return null;
    }

    /** O lugar está dentro da sala de algum chefe? */
    public boolean isBossRoom(Location l) {
        for (RoomStore.Room r : rooms.all().values()) {
            if (r.box != null && r.box.contains(l)) {
                return true;
            }
        }
        return false;
    }

    NamespacedKey minionKey() {
        return minionKey;
    }

    // ------------------------------------------------------------------ começar

    /** O chefe pode aparecer agora? (andar liberado, sem luta, fora da recarga) */
    public boolean armed(BossDef def) {
        if (fights.containsKey(def.id()) || !AincradFloorsAPI.isUnlocked(def.floor())) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (rearm.getOrDefault(def.id(), 0L) > now) {
            return false;
        }
        BossStateStore.State s = states.get(def.id());
        if (s == null || s.kills() == 0) {
            return true;
        }
        // Já derrotado: volta só como desafio, de tempos em tempos.
        return def.respawnHours() > 0 && now - s.lastAt() >= (long) (def.respawnHours() * 3_600_000L);
    }

    /** Alguém entrou na sala: começa a contagem para a porta fechar. */
    private void arm(BossDef def, RoomStore.Room room) {
        Location spawn = RoomStore.location(room.spawn);
        if (spawn == null) {
            World w = Bukkit.getWorld(room.box.world());
            if (w == null) {
                return;
            }
            spawn = new Location(w, room.box.centerX(), room.box.minY() + 1, room.box.centerZ());
        }
        BossFight f = new BossFight(def, room, spawn, newBar(def), tick);
        fights.put(def.id(), f);
        int wait = config.integer("sala.espera_segundos", 10, 0, 300);
        for (Player p : playersIn(f)) {
            msg.send(p, "luta.porta_fecha", Messages.vars("segundos", wait, "chefe", def.name()));
        }
    }

    /**
     * Invoca o chefe para teste: na sala dele, se existir (fecha a porta na hora); senão, solto no
     * lugar indicado, com quem estiver por perto.
     *
     * @return false se já há uma luta deste chefe
     */
    public boolean summon(BossDef def, Location where) {
        if (fights.containsKey(def.id())) {
            return false;
        }
        RoomStore.Room room = rooms.get(def.id());
        BossFight f;
        if (room != null && room.ready()) {
            arm(def, room);
            f = fights.get(def.id());
            if (f == null) {
                return false;
            }
        } else {
            f = new BossFight(def, null, where.clone(), newBar(def), tick);
            fights.put(def.id(), f);
        }
        begin(f);
        return fights.containsKey(def.id());
    }

    /**
     * Faz um chefe de campo ou de evento aparecer solto. Quem chegar perto entra na luta; ele não
     * some por falta de jogadores e não libera andar.
     *
     * @param despawnAt quando some, em milissegundos (0 = não some)
     * @return false se já há uma luta deste chefe ou ele não pôde nascer
     */
    public boolean summonRoaming(BossDef def, Location where, long despawnAt) {
        if (fights.containsKey(def.id())) {
            return false;
        }
        BossFight f = new BossFight(def, null, where.clone(), newBar(def), tick);
        f.roaming = true;
        f.despawnAt = despawnAt;
        fights.put(def.id(), f);
        begin(f);
        return fights.containsKey(def.id());
    }

    private BossBar newBar(BossDef def) {
        return BossBar.bossBar(Component.text(def.name()), 0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    }

    private List<Player> playersIn(BossFight f) {
        List<Player> out = new ArrayList<>();
        for (Player p : f.spawn.getWorld().getPlayers()) {
            if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && f.inArena(p.getLocation())) {
                out.add(p);
            }
        }
        return out;
    }

    /** Fecha a porta, registra quem está dentro e faz o chefe aparecer (parado, na introdução). */
    private void begin(BossFight f) {
        List<Player> inside = playersIn(f);
        for (Player p : inside) {
            f.fighters.add(p.getUniqueId());
            f.started.add(p.getUniqueId());
            f.lastActive.put(p.getUniqueId(), tick);
        }
        if (guide != null) {
            guide.attempt(f.def.id());
        }
        door(f, true);
        f.entity = spawnBoss(f);
        if (f.entity == null) {
            door(f, false);
            fights.remove(f.def.id());
            return;
        }
        f.stage = BossFight.Stage.INTRO;
        f.stageStart = tick;
        applyPhase(f, 0, false);
        for (Player p : inside) {
            msg.title(p, "luta.titulo", Duration.ofSeconds(3), vars(f));
        }
        plugin.getServer().getPluginManager().callEvent(new BossFightStartEvent(f.def.id(), f.def.name(), f.def.floor(), f.entity, inside, f.def.introSeconds()));
    }

    private LivingEntity spawnBoss(BossFight f) {
        BossDef def = f.def;
        if (def.isMythic()) {
            // O corpo vem do MythicMobs (modelo, animações, skills); vida e fases continuam as do YAML.
            Entity made = mythic.spawn(def.mythicId(), f.spawn, def.level());
            if (!(made instanceof LivingEntity le)) {
                if (made != null) {
                    made.remove();
                }
                plugin.getLogger().warning("Chefe " + def.id() + ": o MythicMobs não está ligado ou não conhece o mob \"" + def.mythicId() + "\".");
                return null;
            }
            le.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, def.id());
            le.setRemoveWhenFarAway(false);
            le.setPersistent(false);
            le.setAI(false);
            le.setInvulnerable(true);
            base(le, Attribute.MAX_HEALTH, def.health());
            base(le, Attribute.FOLLOW_RANGE, 64);
            le.setHealth(def.health());
            return le;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(def.mob());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Chefe " + def.id() + ": o mob \"" + def.mob() + "\" não existe.");
            return null;
        }
        if (!type.isAlive() || !type.isSpawnable()) {
            plugin.getLogger().warning("Chefe " + def.id() + ": \"" + def.mob() + "\" não é um mob.");
            return null;
        }
        // Tudo é ajustado antes de o mob entrar no mundo: os outros plugins já o veem com nome.
        Entity e = f.spawn.getWorld().spawnEntity(f.spawn, type, CreatureSpawnEvent.SpawnReason.CUSTOM, raw -> {
            LivingEntity le = (LivingEntity) raw;
            le.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, def.id());
            le.customName(msg.get("luta.nome", Messages.vars("chefe", def.name(), "level", def.level())));
            le.setCustomNameVisible(true);
            le.setRemoveWhenFarAway(false);
            le.setPersistent(false);
            le.setAI(false);
            le.setInvulnerable(true);
            base(le, Attribute.MAX_HEALTH, def.health());
            base(le, Attribute.ATTACK_DAMAGE, def.damage());
            base(le, Attribute.ARMOR, def.armor());
            base(le, Attribute.MOVEMENT_SPEED, def.speed());
            base(le, Attribute.KNOCKBACK_RESISTANCE, def.knockbackResistance());
            base(le, Attribute.SCALE, def.scale());
            base(le, Attribute.FOLLOW_RANGE, 64);
            le.setHealth(def.health());
            equip(le, def.equipment());
        });
        return (LivingEntity) e;
    }

    private static void base(LivingEntity e, Attribute a, double v) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) {
            inst.setBaseValue(v);
        }
    }

    private void equip(LivingEntity e, Map<String, String> equipment) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }
        equipment.forEach((slotName, material) -> {
            EquipmentSlot slot = switch (slotName) {
                case "mao" -> EquipmentSlot.HAND;
                case "mao_secundaria" -> EquipmentSlot.OFF_HAND;
                case "cabeca" -> EquipmentSlot.HEAD;
                case "peito" -> EquipmentSlot.CHEST;
                case "pernas" -> EquipmentSlot.LEGS;
                case "pes" -> EquipmentSlot.FEET;
                default -> null;
            };
            Material m = Material.matchMaterial(material);
            if (slot == null || m == null) {
                plugin.getLogger().warning("Equipamento de chefe ignorado: " + slotName + " = " + material);
                return;
            }
            eq.setItem(slot, m.isAir() ? null : new ItemStack(m));
            eq.setDropChance(slot, 0f);
        });
    }

    // ------------------------------------------------------------------ porta

    private void door(BossFight f, boolean close) {
        if (f.room == null) {
            return;
        }
        f.doorClosed = close;
        setDoor(f.room, close);
    }

    /** Fecha (material da config) ou abre (ar) o vão da porta. Só mexe em ar ou no próprio material. */
    public void setDoor(RoomStore.Room room, boolean close) {
        if (room.box == null) {
            return;
        }
        World w = Bukkit.getWorld(room.box.world());
        Material mat = Material.matchMaterial(config.string("sala.porta_material", "IRON_BARS"));
        if (w == null || mat == null || !mat.isBlock()) {
            return;
        }
        for (int[] b : room.door) {
            Block block = w.getBlockAt(b[0], b[1], b[2]);
            if (close && block.getType().isAir()) {
                block.setType(mat, false);
            } else if (!close && block.getType() == mat) {
                block.setType(Material.AIR, false);
            }
        }
    }

    // ------------------------------------------------------------------ o relógio

    public void tick() {
        tick += STEP;
        for (BossFight f : new ArrayList<>(fights.values())) {
            try {
                step(f);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Erro na luta do chefe " + f.def.id() + "; a luta foi encerrada.", e);
                end(f, BossFightEndEvent.Reason.RESET);
            }
        }
    }

    private void step(BossFight f) {
        long elapsed = tick - f.stageStart;
        switch (f.stage) {
            case ESPERA -> {
                if (playersIn(f).isEmpty()) {
                    fights.remove(f.def.id());
                } else if (elapsed >= config.integer("sala.espera_segundos", 10, 0, 300) * 20L) {
                    begin(f);
                }
            }
            case INTRO -> {
                long intro = f.def.introSeconds() * 20L;
                if (!f.entity.isValid()) {
                    end(f, BossFightEndEvent.Reason.RESET);
                    return;
                }
                f.bar.progress(intro <= 0 ? 1f : Math.min(1f, (float) elapsed / intro));
                showBar(f);
                if (elapsed >= intro) {
                    f.stage = BossFight.Stage.LUTA;
                    f.stageStart = tick;
                    f.nextSkill = tick + 60;
                    f.entity.setAI(true);
                    f.entity.setInvulnerable(false);
                }
            }
            case LUTA -> fightStep(f, elapsed);
        }
    }

    private void fightStep(BossFight f, long elapsed) {
        if (!f.entity.isValid() || f.entity.isDead()) {
            // Sumiu sem morrer (chunk descarregado, outro plugin): encerra sem prêmio.
            end(f, BossFightEndEvent.Reason.RESET);
            return;
        }
        if (f.roaming) {
            // Chefe de campo: quem chega perto entra na luta (menos quem deslogou no meio dela).
            for (Player p : playersIn(f)) {
                if (!f.quit.contains(p.getUniqueId()) && f.fighters.add(p.getUniqueId())) {
                    f.started.add(p.getUniqueId());
                    f.lastActive.put(p.getUniqueId(), tick);
                }
            }
        }
        // Quem morreu, saiu do servidor ou da sala não luta mais (e não volta).
        f.fighters.removeIf(id -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                // Deslogou no meio da luta: conta como morto, não volta e não recebe recompensa.
                f.quit.add(id);
                return true;
            }
            return p.isDead() || !f.inArena(p.getLocation());
        });
        long limit = idleLimit();
        if (limit > 0) {
            for (Player p : fighters(f)) {
                long idleFor = tick - f.lastActive.getOrDefault(p.getUniqueId(), f.stageStart);
                if (idleFor >= limit * 3 / 4) {
                    msg.actionBar(p, idleFor >= limit ? "luta.ausente" : "luta.ausente_aviso");
                }
            }
        }
        if (f.roaming) {
            // Fica no campo mesmo sem ninguém; o de evento some quando a janela dele acaba.
            if (f.despawnAt > 0 && System.currentTimeMillis() >= f.despawnAt) {
                end(f, BossFightEndEvent.Reason.TEMPO);
                return;
            }
        } else {
            if (f.fighters.isEmpty()) {
                end(f, BossFightEndEvent.Reason.DERROTA);
                return;
            }
            if (f.def.timeLimitSeconds() > 0 && elapsed >= f.def.timeLimitSeconds() * 20L) {
                end(f, BossFightEndEvent.Reason.TEMPO);
                return;
            }
        }
        if (!f.inArena(f.entity.getLocation())) {
            f.entity.teleport(f.spawn);
        }
        f.minions.removeIf(m -> !m.isValid());
        threatStep(f);
        double max = f.def.health();
        double pct = f.entity.getHealth() / max * 100.0;
        int phase = f.def.phaseAt(pct);
        if (phase > f.phase) {
            applyPhase(f, phase, true);
        }
        f.bar.progress((float) Math.max(0, Math.min(1, f.entity.getHealth() / max)));
        f.bar.name(msg.get("luta.barra", vars(f)));
        showBar(f);
        if (!f.fighters.isEmpty()) {
            skills.tick(f);
        }
    }

    private TagResolver vars(BossFight f) {
        return Messages.vars("chefe", f.def.name(), "level", f.def.level(), "fase", f.currentPhase().name(), "andar", f.def.floor());
    }

    /** A barra aparece para quem está na sala e some para quem saiu. */
    private void showBar(BossFight f) {
        List<Player> inside = playersIn(f);
        f.viewers.removeIf(id -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                return true;
            }
            if (!inside.contains(p)) {
                p.hideBossBar(f.bar);
                return true;
            }
            return false;
        });
        for (Player p : inside) {
            if (f.viewers.add(p.getUniqueId())) {
                p.showBossBar(f.bar);
            }
        }
    }

    private void applyPhase(BossFight f, int index, boolean announce) {
        f.phase = index;
        if (guide != null) {
            guide.phase(f.def.id(), index);
        }
        BossDef.Phase ph = f.currentPhase();
        f.ready.clear();
        f.nextSkill = tick + 40;
        if (!f.def.isMythic()) {
            equip(f.entity, ph.equipment());
            base(f.entity, Attribute.MOVEMENT_SPEED, f.def.speed() * ph.speedTimes());
        }
        publish(f, phaseState(f), "");
        if (!announce) {
            return;
        }
        if (!ph.message().isBlank()) {
            Component line = Messages.parse(ph.message(), vars(f));
            say(f, line);
        }
        plugin.getServer().getPluginManager().callEvent(new BossPhaseChangeEvent(f.def.id(), f.def.name(), f.def.floor(), f.entity, index + 1, ph.name()));
    }

    /** Mostra uma linha para todos na sala. */
    void say(BossFight f, Component line) {
        for (Player p : playersIn(f)) {
            p.sendMessage(line);
        }
    }

    /** Jogadores que estão lutando e online. */
    List<Player> fighters(BossFight f) {
        List<Player> out = new ArrayList<>();
        for (UUID id : f.fighters) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead()) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ fim

    /** Encerra sem vitória: o chefe some, a porta abre. */
    public void end(BossFight f, BossFightEndEvent.Reason reason) {
        if (f.over) {
            return;
        }
        f.over = true;
        fights.remove(f.def.id());
        cleanup(f);
        if (f.entity != null) {
            f.entity.remove();
        }
        if (reason != BossFightEndEvent.Reason.RESET) {
            rearm.put(f.def.id(), System.currentTimeMillis() + config.integer("sala.recarga_derrota_segundos", 60, 0, 86_400) * 1000L);
        }
        String key = switch (reason) {
            case DERROTA -> "luta.derrota";
            case TEMPO -> "luta.tempo";
            case RESET -> "luta.reset";
        };
        for (UUID id : f.started) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg.send(p, key, vars(f));
            }
        }
        if (f.stage != BossFight.Stage.ESPERA) {
            plugin.getServer().getPluginManager().callEvent(new BossFightEndEvent(f.def.id(), f.def.name(), f.def.floor(), f.entity, reason));
        }
    }

    private void cleanup(BossFight f) {
        door(f, false);
        f.minions.forEach(Entity::remove);
        f.minions.clear();
        for (UUID id : f.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(f.bar);
            }
        }
        f.viewers.clear();
    }

    public void endAll() {
        for (BossFight f : new ArrayList<>(fights.values())) {
            end(f, BossFightEndEvent.Reason.RESET);
        }
    }

    /** Abre todas as portas (ao ligar: se o servidor caiu no meio de uma luta, a porta ficou fechada). */
    public void openAllDoors() {
        rooms.all().values().forEach(r -> setDoor(r, false));
    }

    private void victory(BossFight f, Player killer) {
        f.over = true;
        fights.remove(f.def.id());
        cleanup(f);
        BossDef def = f.def;
        BossDef.Rewards rw = def.rewards();
        List<UUID> eligible = new ArrayList<>(f.ledger.eligible(rw.minDamagePercent(), def.health()));
        // Proteção contra abuso: quem deslogou ou ficou parado não conta como participante.
        eligible.removeIf(id -> f.quit.contains(id) || idle(f, id));
        UUID last = killer != null ? killer.getUniqueId() : f.ledger.lastAttacker();
        // Chefe de campo ou de evento não tem "primeira vitória": não libera andar nem entra no estado dos andares.
        boolean first = !f.roaming && !states.defeated(def.id());

        Map<UUID, List<ItemStack>> items = new LinkedHashMap<>();
        Map<UUID, Long> xp = new LinkedHashMap<>();
        Map<UUID, Long> col = new LinkedHashMap<>();
        ItemStack lastItem = null;
        DamageLedger.split(rw.items(), eligible).forEach((id, map) -> map.forEach((spec, amount) -> {
            // Itens que não empilham (os do RPG) saem um a um, cada qual com seus bônus sorteados.
            int left = amount;
            while (left > 0) {
                ItemStack it = hooks.item(spec, left);
                if (it == null) {
                    plugin.getLogger().warning("Chefe " + def.id() + ": recompensa \"" + spec + "\" não existe (o AincradRPG está ligado?).");
                    break;
                }
                items.computeIfAbsent(id, k -> new ArrayList<>()).add(it);
                left -= Math.max(1, it.getAmount());
            }
        }));
        if (last != null && rw.lastAttack() != null) {
            ItemStack bonus = hooks.item(rw.lastAttack(), 1);
            if (bonus != null) {
                lastItem = bonus;
                items.computeIfAbsent(last, k -> new ArrayList<>()).add(bonus);
            } else {
                plugin.getLogger().warning("Chefe " + def.id() + ": item de Last Attack \"" + rw.lastAttack() + "\" não existe.");
            }
        }
        Player lastPlayer = last == null ? null : Bukkit.getPlayer(last);
        String lastName = lastPlayer != null ? lastPlayer.getName() : last == null ? "?" : String.valueOf(Bukkit.getOfflinePlayer(last).getName());
        List<String> names = new ArrayList<>();
        for (UUID id : eligible) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            names.add(p.getName());
            long gained = hooks.giveXp(p, rw.xp());
            if (gained > 0) {
                xp.put(id, gained);
            }
            long paid = hooks.giveCol(p, rw.col());
            if (paid > 0) {
                col.put(id, paid);
            }
        }
        items.forEach((id, list) -> {
            Player p = Bukkit.getPlayer(id);
            for (ItemStack it : list) {
                if (p != null) {
                    p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                } else {
                    // Saiu antes do fim: o item cai onde o chefe morreu.
                    f.entity.getWorld().dropItemNaturally(f.entity.getLocation(), it);
                }
            }
        });

        var vars = TagResolver.resolver(vars(f), Messages.vars("jogador", lastName, "jogadores", String.join(", ", names)));
        for (UUID id : f.started) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            msg.title(p, "luta.vitoria_titulo", Duration.ofSeconds(4), vars);
            msg.sendRaw(p, "luta.vitoria", vars);
            msg.sendRaw(p, "luta.last_attack", vars);
            double share = def.health() <= 0 ? 0 : f.ledger.of(id) / def.health() * 100.0;
            msg.sendRaw(p, eligible.contains(id) ? "luta.seu_dano" : "luta.seu_dano_pouco", Messages.vars(
                    "dano", Math.round(f.ledger.of(id)), "pct", Math.round(share), "minimo", Math.round(rw.minDamagePercent())));
        }

        if (!f.roaming) {
            states.recordKill(def.id(), System.currentTimeMillis(), lastName, String.join(", ", names))
                    .thenRun(() -> AincradCore.get().runSync(() -> AincradCore.get().publish(BUS_STATE, def.id())));
        }
        plugin.getServer().getPluginManager().callEvent(new BossDefeatEvent(def.id(), def.name(), def.floor(), f.entity, last, eligible,
                f.ledger.all(), items, xp, col, lastItem, first));
        hooks.recordBossKill(eligible, def.floor(), last);
        if (first && def.floor() < AincradFloorsAPI.getFloorCount()) {
            AincradFloorsAPI.unlockFloor(def.floor() + 1, eligible);
        }
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (enter(event.getPlayer(), from, to)) {
            event.setCancelled(true);
            return;
        }
        BossFight mine = fightOf(event.getPlayer());
        if (mine != null) {
            mine.lastActive.put(event.getPlayer().getUniqueId(), tick);
        }
    }

    /** Quem entra no servidor dentro de uma sala em luta (deslogou lá) é posto para fora. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        for (Map.Entry<String, RoomStore.Room> e : rooms.all().entrySet()) {
            RoomStore.Room room = e.getValue();
            BossFight f = fights.get(e.getKey());
            if (room.box == null || f == null || f.stage == BossFight.Stage.ESPERA || !room.box.contains(p.getLocation())
                    || p.hasPermission(ADMIN)) {
                continue;
            }
            Location out = RoomStore.location(room.entry);
            if (out == null) {
                out = AincradFloorsAPI.getSpawn(f.def.floor());
            }
            if (out == null) {
                out = p.getWorld().getSpawnLocation();
            }
            Location to = out;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.teleportAsync(to);
                msg.send(p, "luta.voltou_fora", Messages.vars("chefe", f.def.name()));
            });
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (enter(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    /** O jogador vai entrar numa sala. @return true se a entrada deve ser barrada */
    private boolean enter(Player p, Location from, Location to) {
        for (Map.Entry<String, RoomStore.Room> e : rooms.all().entrySet()) {
            RoomStore.Room room = e.getValue();
            if (room.box == null || !room.box.contains(to) || room.box.contains(from)) {
                continue;
            }
            if (raids != null && raids.blocks(p, e.getKey())) {
                msg.actionBar(p, "raid.sala_reservada");
                return true;
            }
            BossFight f = fights.get(e.getKey());
            if (f != null && f.stage != BossFight.Stage.ESPERA) {
                // Luta em andamento: ninguém entra (quem saiu não volta).
                if (p.hasPermission(ADMIN) || p.getGameMode() == GameMode.SPECTATOR) {
                    return false;
                }
                msg.actionBar(p, "luta.em_andamento", Messages.vars("chefe", f.def.name()));
                return true;
            }
            BossDef def = registry.get(e.getKey());
            if (f == null && def != null && p.getGameMode() != GameMode.SPECTATOR && armed(def)) {
                // Um tick depois, para o jogador já contar como dentro da sala.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!fights.containsKey(def.id()) && armed(def)) {
                        arm(def, room);
                    }
                });
            }
        }
        return false;
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        return damager instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        BossFight f = fightOf(event.getEntity());
        if (f == null) {
            return;
        }
        // Parado na introdução, e sem morrer de queda (salto) ou sufocado na parede.
        if (f.stage != BossFight.Stage.LUTA || event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent by) {
            Player p = attacker(by.getDamager());
            if (p != null && !f.isFighter(p)) {
                event.setCancelled(true);
                return;
            }
            // Janela de punição: quem lê o aviso e ataca em vez de só recuar causa mais dano.
            double times = config.decimal("punicao.multiplicador", 1.5, 1, 10);
            if (p != null && times > 1 && tick < f.telegraphUntil) {
                event.setDamage(event.getDamage() * times);
                msg.actionBar(p, "luta.punicao", Messages.vars("pct", Math.round((times - 1) * 100)));
            }
        }
    }

    /** O chefe bate com o dano do YAML (vezes a fase e a fúria), não com o do mob. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBossHit(EntityDamageByEntityEvent event) {
        BossFight f = fightOf(event.getDamager());
        if (f != null && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            event.setDamage(f.def.damage() * f.damageTimes(tick));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        BossFight f = fightOf(event.getEntity());
        Player p = f == null ? null : attacker(event.getDamager());
        if (p == null) {
            return;
        }
        double dealt = Math.min(event.getFinalDamage(), f.entity.getHealth());
        f.ledger.add(p.getUniqueId(), dealt);
        f.threat.add(p.getUniqueId(), dealt * config.decimal("ameaca.por_dano", 1, 0, 100), System.currentTimeMillis());
        f.lastActive.put(p.getUniqueId(), tick);
        plugin.getServer().getPluginManager().callEvent(new BossDamageEvent(f.def.id(), f.def.name(), f.def.floor(), f.entity, p, dealt, event.isCritical()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.getPersistentDataContainer().has(minionKey)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        BossFight f = fightOf(e);
        if (f == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        victory(f, e.getKiller());
    }

    /** O chefe só mira quem está lutando. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        BossFight f = fightOf(event.getEntity());
        if (f == null) {
            return;
        }
        if (!(event.getTarget() instanceof Player p) || !f.isFighter(p)) {
            event.setCancelled(true);
            return;
        }
        // Com a ameaça ligada, o mob não troca de alvo sozinho: quem decide é a tabela.
        if (f.target != null && !p.getUniqueId().equals(f.target)
                && event.getReason() != EntityTargetLivingEntityEvent.TargetReason.CUSTOM && Bukkit.getPlayer(f.target) != null) {
            event.setCancelled(true);
        }
    }

    /** Chefe morto-vivo não pega fogo no sol. */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(bossKey) && !(event instanceof org.bukkit.event.entity.EntityCombustByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ ameaça, aviso e MythicMobs

    MythicLink mythic() {
        return mythic;
    }

    private static String phaseState(BossFight f) {
        return "PHASE_" + (f.phase + 1);
    }

    /** Grava o estado nas variáveis do mob do Mythic (não faz nada num chefe do Minecraft). */
    void publish(BossFight f, String state, String attack) {
        if (f.def.isMythic() && f.entity != null) {
            mythic.state(f.entity, state, f.phase + 1, attack);
        }
    }

    /**
     * O chefe começa a avisar um golpe: fica parado, abre a janela de punição e o Mythic recebe
     * {@code boss_state = TELEGRAPH}. No fim do aviso volta a andar, e o estado volta ao da fase
     * (a não ser que o golpe seja do Mythic, que passa antes por {@code HEAVY_ATTACK}).
     */
    void telegraph(BossFight f, int ticks, String attack, boolean backToPhase) {
        if (ticks <= 0) {
            return;
        }
        f.telegraphUntil = tick + ticks;
        publish(f, "TELEGRAPH", attack);
        boolean freeze = config.bool("punicao.congelar_no_aviso", true);
        if (freeze) {
            f.entity.setAI(false);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (f.over || f.entity == null || !f.entity.isValid() || f.stage != BossFight.Stage.LUTA) {
                return;
            }
            if (freeze) {
                f.entity.setAI(true);
            }
            if (backToPhase) {
                publish(f, phaseState(f), "");
            }
        }, ticks);
    }

    /** A cada passo: a ameaça de quem parou de bater cai, o alvo é escolhido e o painel aparece. */
    private void threatStep(BossFight f) {
        if (!config.bool("ameaca.ativo", true)) {
            f.target = null;
            return;
        }
        long now = System.currentTimeMillis();
        java.util.function.Predicate<UUID> valid = id -> {
            Player p = f.fighters.contains(id) ? Bukkit.getPlayer(id) : null;
            return p != null && !p.isDead();
        };
        f.threat.prune(valid);
        f.threat.decay(config.decimal("ameaca.perda_por_segundo", 2.5, 0, 100), STEP / 20.0,
                config.decimal("ameaca.carencia_segundos", 3, 0, 600), now);
        f.target = f.threat.selectTarget(f.target, config.decimal("ameaca.margem_de_troca", 1.15, 1, 10), valid).orElse(null);
        Player p = f.target == null ? null : Bukkit.getPlayer(f.target);
        if (p != null && f.entity instanceof Mob mob && tick >= f.telegraphUntil && !p.equals(mob.getTarget())) {
            mob.setTarget(p);
        }
        if (config.bool("ameaca.painel", true) && tick >= f.nextHud && !f.threat.isEmpty()) {
            f.nextHud = tick + 20;
            Map<UUID, Integer> shares = f.threat.shares();
            for (Player viewer : fighters(f)) {
                StringBuilder line = new StringBuilder();
                int shown = 0;
                for (Map.Entry<UUID, Integer> e : shares.entrySet()) {
                    Player who = Bukkit.getPlayer(e.getKey());
                    if (who == null || shown++ >= 3) {
                        continue;
                    }
                    line.append(line.length() > 0 ? " <dark_gray>· " : "")
                            .append(msg.raw(who.equals(viewer) ? "luta.ameaca_voce" : "luta.ameaca_outro")
                                    .replace("<nome>", who.getName()).replace("<pct>", String.valueOf(e.getValue())));
                }
                msg.actionBar(viewer, "luta.ameaca", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("linha", Messages.parse(line.toString())));
            }
        }
    }

    /** Em quem o chefe usa a habilidade: quem tem mais ameaça; sem ameaça ainda, o mais perto. */
    Player target(BossFight f) {
        Player p = f.target == null ? null : Bukkit.getPlayer(f.target);
        return p != null && !p.isDead() && f.fighters.contains(p.getUniqueId()) ? p : nearest(f);
    }

    /** Alvo para o chefe: o lutador mais perto. */
    Player nearest(BossFight f) {
        Player best = null;
        double dist = Double.MAX_VALUE;
        for (Player p : fighters(f)) {
            double d = p.getLocation().distanceSquared(f.entity.getLocation());
            if (d < dist) {
                dist = d;
                best = p;
            }
        }
        if (best != null && f.entity instanceof Mob mob && mob.getTarget() == null) {
            mob.setTarget(best);
        }
        return best;
    }

    /** Nome do material da porta, para as mensagens do admin. */
    public String doorMaterial() {
        return config.string("sala.porta_material", "IRON_BARS").toUpperCase(Locale.ROOT);
    }
}
