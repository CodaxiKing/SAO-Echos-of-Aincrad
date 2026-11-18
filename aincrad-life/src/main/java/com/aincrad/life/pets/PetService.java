package com.aincrad.life.pets;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.life.api.PetTameEvent;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.pets.PetRules.Ability;
import com.aincrad.life.pets.PetRules.Mode;
import com.aincrad.life.pets.PetRules.Species;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Familiares: um mob pequeno domado com a comida favorita, que segue o dono, ganha level com o
 * XP dele, tem uma habilidade por espécie e um vínculo que cai sem comida. Se morre, deixa o
 * Coração, e o dono tem alguns dias para revivê-lo. Um por jogador; os dados ficam no banco do
 * core, então o familiar acompanha o dono entre os servidores da rede.
 */
public final class PetService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_pets (owner VARCHAR(36) NOT NULL PRIMARY KEY, species VARCHAR(64) NOT NULL, "
                    + "name VARCHAR(32) NOT NULL, level INT NOT NULL, xp BIGINT NOT NULL, bond INT NOT NULL, mode VARCHAR(12) NOT NULL, "
                    + "died BIGINT NOT NULL)");

    /** O familiar de um jogador. {@code died}: quando morreu (0 = vivo). */
    public static final class Pet {
        final UUID owner;
        final String species;
        String name;
        int level;
        long xp;
        int bond;
        Mode mode;
        long died;
        boolean dirty;
        LivingEntity entity;

        Pet(UUID owner, String species, String name, int level, long xp, int bond, Mode mode, long died) {
            this.owner = owner;
            this.species = species;
            this.name = name;
            this.level = level;
            this.xp = xp;
            this.bond = bond;
            this.mode = mode;
            this.died = died;
        }

        public boolean alive() {
            return died <= 0;
        }

        public String name() {
            return name;
        }

        public int level() {
            return level;
        }
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final Database db;
    private final NamespacedKey keyPet;
    private final NamespacedKey keyAttacked;
    private final NamespacedKey keyHeart;
    private final NamespacedKey keyHeartDied;
    private final NamespacedKey keyFlower;
    private final Map<UUID, Pet> pets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextHeal = new HashMap<>();
    private final Map<UUID, Long> bubbleReady = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private Map<String, Species> species = Map.of();

    public PetService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.db = AincradCore.get().database();
        this.keyPet = new NamespacedKey(plugin, "familiar");
        this.keyAttacked = new NamespacedKey(plugin, "atacado_por");
        this.keyHeart = new NamespacedKey(plugin, "coracao");
        this.keyHeartDied = new NamespacedKey(plugin, "coracao_morte");
        this.keyFlower = new NamespacedKey(plugin, "flor_do_familiar");
    }

    /** Lê pets.yml e carrega os familiares de quem já está online. */
    public void load() {
        File f = new File(plugin.getDataFolder(), "pets.yml");
        if (!f.exists()) {
            plugin.saveResource("pets.yml", false);
        }
        List<String> errors = new ArrayList<>();
        species = PetRules.parse(YamlConfiguration.loadConfiguration(f), errors);
        for (Species s : species.values()) {
            if (type(s) == null) {
                errors.add("pets.yml: " + s.id() + ": o mob " + s.mob() + " não existe ou não é um ser vivo.");
            }
            if (Material.matchMaterial(s.food()) == null) {
                errors.add("pets.yml: " + s.id() + ": comida desconhecida " + s.food() + ".");
            }
        }
        errors.forEach(e -> plugin.getLogger().warning(e));
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                loadBlocking(p.getUniqueId());
                summon(p);
            } catch (SQLException e) {
                plugin.getLogger().warning("Não foi possível carregar o familiar de " + p.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Familiares: " + species.size() + " espécies.");
    }

    public Map<String, Species> species() {
        return species;
    }

    private static EntityType type(Species s) {
        try {
            EntityType t = EntityType.valueOf(s.mob());
            return t.isAlive() && t.isSpawnable() ? t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Species speciesOf(EntityType t) {
        for (Species s : species.values()) {
            if (s.mob().equals(t.name())) {
                return s;
            }
        }
        return null;
    }

    public Pet pet(UUID owner) {
        return pets.get(owner);
    }

    // ------------------------------------------------------------------ banco

    private void loadBlocking(UUID id) throws SQLException {
        List<Pet> l = db.sync(c -> Database.query(c, "SELECT species, name, level, xp, bond, mode, died FROM life_pets WHERE owner = ?",
                rs -> new Pet(id, rs.getString(1), rs.getString(2), Math.max(1, rs.getInt(3)), rs.getLong(4), rs.getInt(5),
                        Mode.parse(rs.getString(6)), rs.getLong(7)), id.toString()));
        if (l.isEmpty()) {
            pets.remove(id);
        } else {
            pets.put(id, l.get(0));
        }
    }

    private void save(Pet p) {
        p.dirty = false;
        db.update(db.upsert("life_pets", List.of("owner"), List.of("species", "name", "level", "xp", "bond", "mode", "died")),
                p.owner.toString(), p.species, p.name, p.level, p.xp, p.bond, p.mode.name(), p.died);
    }

    private void delete(UUID owner) {
        Pet p = pets.remove(owner);
        if (p != null) {
            despawn(p);
        }
        db.update("DELETE FROM life_pets WHERE owner = ?", owner.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            loadBlocking(event.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("Não foi possível carregar o familiar de " + event.getName() + ": " + e.getMessage());
        }
    }

    /** Ao entrar (também vindo de outro servidor da rede): o familiar aparece ao lado do dono. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> summon(event.getPlayer()), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Pet p = pets.remove(id);
        if (p != null) {
            despawn(p);
            save(p);
        }
        nextHeal.remove(id);
        bubbleReady.remove(id);
        lastAlert.remove(id);
    }

    /** Ao desligar o módulo: guarda tudo e tira os familiares do mundo. */
    public void shutdown() {
        for (Pet p : pets.values()) {
            despawn(p);
            save(p);
        }
        pets.clear();
    }

    // ------------------------------------------------------------------ no mundo

    private UUID ownerOf(Entity e) {
        String raw = e.getPersistentDataContainer().get(keyPet, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void despawn(Pet p) {
        if (p.entity != null) {
            p.entity.remove();
            p.entity = null;
        }
    }

    /** Faz o familiar aparecer ao lado do dono, se está vivo, não está guardado e o lugar permite. */
    private void summon(Player owner) {
        Pet p = owner.isOnline() ? pets.get(owner.getUniqueId()) : null;
        if (p == null || !p.alive() || p.mode == Mode.GUARDADO || (p.entity != null && p.entity.isValid()) || blocked(owner.getLocation())) {
            return;
        }
        Species s = species.get(p.species);
        EntityType t = s == null ? null : type(s);
        if (t == null) {
            return;
        }
        Entity e = owner.getWorld().spawnEntity(owner.getLocation(), t);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return;
        }
        le.setPersistent(false);
        le.setRemoveWhenFarAway(false);
        le.getPersistentDataContainer().set(keyPet, PersistentDataType.STRING, owner.getUniqueId().toString());
        if (le instanceof Tameable tame) {
            tame.setTamed(true);
            tame.setOwner(owner);
        }
        p.entity = le;
        refresh(p, true);
    }

    /** Sala de chefe: o familiar não entra (config → familiares.permitir_em_sala_de_chefe). */
    private boolean blocked(Location l) {
        return !config.bool("familiares.permitir_em_sala_de_chefe", false) && hooks.inBossRoom(l);
    }

    /** Põe nome, vida máxima e postura (sentado ou não) de acordo com os dados. */
    private void refresh(Pet p, boolean fullHealth) {
        LivingEntity e = p.entity;
        Species s = species.get(p.species);
        if (e == null || !e.isValid() || s == null) {
            return;
        }
        e.customName(msg.get("familiar.nome_no_mundo", Messages.vars("nome", p.name, "level", p.level)));
        e.setCustomNameVisible(true);
        AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(PetRules.maxHealth(s.health(), p.level, config.decimal("familiares.level.vida_por_level", 1, 0, 100)));
            if (fullHealth) {
                e.setHealth(hp.getValue());
            }
        }
        if (e instanceof Sittable sit) {
            sit.setSitting(p.mode == Mode.FICAR);
        }
        if (e instanceof Mob mob && p.mode == Mode.FICAR) {
            mob.getPathfinder().stopPathfinding();
        }
    }

    /** Roda duas vezes por segundo: o familiar acompanha o dono (ou some, onde não pode entrar). */
    public void follow() {
        for (Pet p : pets.values()) {
            Player owner = Bukkit.getPlayer(p.owner);
            if (owner == null || !p.alive() || p.mode == Mode.GUARDADO) {
                continue;
            }
            if (blocked(owner.getLocation())) {
                despawn(p);
                continue;
            }
            if (p.entity == null || !p.entity.isValid()) {
                p.entity = null;
                if (!owner.isDead()) {
                    summon(owner);
                }
                continue;
            }
            if (p.mode != Mode.SEGUIR) {
                continue;
            }
            boolean sameWorld = p.entity.getWorld().equals(owner.getWorld());
            double far = sameWorld ? p.entity.getLocation().distanceSquared(owner.getLocation()) : Double.MAX_VALUE;
            if (far > 20 * 20) {
                p.entity.teleport(owner.getLocation());
            } else if (far > 4 * 4 && p.entity instanceof Mob mob) {
                mob.getPathfinder().moveTo(owner, 1.3);
            }
        }
    }

    private boolean near(Pet p, Player owner) {
        return p.entity != null && p.entity.isValid() && p.entity.getWorld().equals(owner.getWorld())
                && p.entity.getLocation().distanceSquared(owner.getLocation()) <= 16 * 16;
    }

    private boolean abilityWorks(Pet p, Player owner) {
        return p.alive() && near(p, owner) && PetRules.abilityWorks(p.bond, config.integer("familiares.vinculo.minimo_para_habilidade", 30, 0, 100));
    }

    // ------------------------------------------------------------------ habilidades

    /** Roda uma vez por segundo: cura, alerta e busca. (A bolha age quando o dono leva o golpe.) */
    public void second() {
        long now = System.currentTimeMillis();
        for (Pet p : pets.values()) {
            Player owner = Bukkit.getPlayer(p.owner);
            Species s = species.get(p.species);
            if (owner == null || s == null || owner.isDead() || !abilityWorks(p, owner)) {
                continue;
            }
            switch (s.ability()) {
                case CURA -> {
                    if (now < nextHeal.getOrDefault(p.owner, 0L)) {
                        break;
                    }
                    nextHeal.put(p.owner, now + config.integer("familiares.cura.segundos", 30, 1, 3600) * 1000L);
                    AttributeInstance max = owner.getAttribute(Attribute.MAX_HEALTH);
                    double top = max == null ? 20 : max.getValue();
                    if (owner.getHealth() < top) {
                        owner.setHealth(Math.min(top, owner.getHealth() + PetRules.heal(config.decimal("familiares.cura.vida", 2, 0, 100), p.level,
                                config.decimal("familiares.cura.vida_por_level", 0.1, 0, 10))));
                        owner.getWorld().spawnParticle(Particle.HEART, owner.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3, 0);
                        owner.playSound(owner.getLocation(), Sound.ENTITY_PARROT_AMBIENT, 0.6f, 1.6f);
                    }
                }
                case ALERTA -> {
                    if (now - lastAlert.getOrDefault(p.owner, 0L) < config.integer("familiares.alerta.intervalo_segundos", 10, 1, 600) * 1000L) {
                        break;
                    }
                    double r = config.decimal("familiares.alerta.raio", 12, 1, 64);
                    long hostile = owner.getNearbyEntities(r, r, r).stream().filter(e -> e instanceof Monster).count();
                    if (hostile > 0) {
                        lastAlert.put(p.owner, now);
                        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.8f);
                        msg.actionBar(owner, "familiar.alerta", Messages.vars("nome", p.name, "qtd", hostile));
                    }
                }
                case BUSCA -> {
                    double r = config.decimal("familiares.busca.raio", 6, 1, 32);
                    for (Entity e : p.entity.getNearbyEntities(r, r, r)) {
                        if (e instanceof Item item && item.getPickupDelay() <= 0 && item.getLocation().distanceSquared(owner.getLocation()) > 4) {
                            item.teleport(owner.getLocation());
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    /** A bolha: com ela pronta, o familiar absorve o golpe que o dono levaria. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOwnerHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player owner)) {
            return;
        }
        Pet p = pets.get(owner.getUniqueId());
        Species s = p == null ? null : species.get(p.species);
        long now = System.currentTimeMillis();
        if (s == null || s.ability() != Ability.BOLHA || !abilityWorks(p, owner) || now < bubbleReady.getOrDefault(p.owner, 0L)
                || event.getFinalDamage() <= 0) {
            return;
        }
        bubbleReady.put(p.owner, now + config.integer("familiares.bolha.recarga_segundos", 60, 1, 3600) * 1000L);
        event.setCancelled(true);
        owner.getWorld().spawnParticle(Particle.BUBBLE_POP, owner.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.02);
        owner.playSound(owner.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 1f, 1f);
        msg.actionBar(owner, "familiar.bolha", Messages.vars("nome", p.name));
    }

    // ------------------------------------------------------------------ vínculo e level

    /** Roda a cada minuto: o vínculo cai sem comida, os dados são gravados e o Coração vencido libera o dono. */
    public void minute() {
        long now = System.currentTimeMillis();
        int every = config.integer("familiares.vinculo.cai_1_a_cada_minutos", 10, 1, 10_000);
        int days = config.integer("familiares.coracao.dias", 3, 1, 365);
        for (Pet p : new ArrayList<>(pets.values())) {
            if (!p.alive()) {
                if (!PetRules.canRevive(p.died, now, days)) {
                    // O prazo passou: o Coração vira lembrança e o jogador pode domar outro.
                    Player owner = Bukkit.getPlayer(p.owner);
                    delete(p.owner);
                    if (owner != null) {
                        msg.send(owner, "familiar.prazo_acabou", Messages.vars("nome", p.name));
                    }
                }
                continue;
            }
            if (p.mode != Mode.GUARDADO && (now / 60_000L) % every == 0 && p.bond > 0) {
                p.bond = PetRules.bond(p.bond, -1);
                p.dirty = true;
                Player owner = Bukkit.getPlayer(p.owner);
                if (owner != null && p.bond == config.integer("familiares.vinculo.minimo_para_habilidade", 30, 0, 100) - 1) {
                    msg.send(owner, "familiar.vinculo_baixo", Messages.vars("nome", p.name));
                }
            }
            if (p.dirty) {
                save(p);
            }
        }
    }

    /** O familiar ganha uma parte do XP do dono. */
    public void addXp(Player owner, long ownerXp) {
        Pet p = pets.get(owner.getUniqueId());
        if (p == null || !p.alive() || p.mode == Mode.GUARDADO) {
            return;
        }
        long gain = PetRules.share(ownerXp, config.decimal("familiares.level.parte_do_xp", 0.2, 0, 10));
        if (gain <= 0) {
            return;
        }
        long[] after = PetRules.addXp(p.level, p.xp, gain, config.decimal("familiares.level.xp_base", 50, 1, 1_000_000),
                config.decimal("familiares.level.expoente", 1.5, 0.1, 5), config.integer("familiares.level.maximo", 50, 1, 1000));
        boolean up = after[0] > p.level;
        p.level = (int) after[0];
        p.xp = after[1];
        p.dirty = true;
        if (up) {
            refresh(p, true);
            save(p);
            owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            msg.send(owner, "familiar.subiu", Messages.vars("nome", p.name, "level", p.level));
        }
    }

    /** Sem o AincradRPG, o familiar ganha XP pelos mobs que o dono mata. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && !hooks.hasRpg() && ownerOf(event.getEntity()) == null) {
            addXp(killer, config.integer("familiares.level.xp_por_mob_sem_rpg", 10, 0, 100_000));
        }
    }

    // ------------------------------------------------------------------ domar e alimentar

    /** Guarda no mob quem o atacou: ele não aceita ser domado por essa pessoa. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWildHurt(EntityDamageByEntityEvent event) {
        Entity by = event.getDamager();
        Player attacker = by instanceof Player pl ? pl : by instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target) || speciesOf(target.getType()) == null || ownerOf(target) != null) {
            return;
        }
        String now = target.getPersistentDataContainer().getOrDefault(keyAttacked, PersistentDataType.STRING, "");
        String me = attacker.getUniqueId().toString();
        if (!now.contains(me)) {
            target.getPersistentDataContainer().set(keyAttacked, PersistentDataType.STRING, now.isEmpty() ? me : now + "," + me);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickMob(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof LivingEntity target)) {
            return;
        }
        Player p = event.getPlayer();
        UUID owner = ownerOf(target);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (owner != null) {
            // O familiar de alguém: só o dono mexe, e só para alimentar.
            event.setCancelled(true);
            if (owner.equals(p.getUniqueId())) {
                feed(p, pets.get(owner), hand);
            }
            return;
        }
        Species s = speciesOf(target.getType());
        if (s == null || hand.getType().isAir() || !hand.getType().name().equals(s.food()) || (target instanceof Tameable t && t.isTamed())) {
            return;
        }
        event.setCancelled(true);
        boolean attacked = target.getPersistentDataContainer().getOrDefault(keyAttacked, PersistentDataType.STRING, "").contains(p.getUniqueId().toString());
        switch (PetRules.canTame(pets.containsKey(p.getUniqueId()), attacked, true)) {
            case JA_TEM -> msg.send(p, "familiar.ja_tem");
            case ATACOU -> msg.send(p, "familiar.atacou", Messages.vars("especie", s.name()));
            default -> {
                hand.setAmount(hand.getAmount() - 1);
                Location at = target.getLocation().add(0, target.getHeight(), 0);
                if (!PetRules.tamed(s.chance(), config.decimal("familiares.multiplicador_de_chance", 1.0, 0, 1000), ThreadLocalRandom.current().nextDouble())) {
                    at.getWorld().spawnParticle(Particle.SMOKE, at, 8, 0.2, 0.2, 0.2, 0.01);
                    msg.actionBar(p, "familiar.nao_domou", Messages.vars("especie", s.name()));
                    return;
                }
                // Domou: o mob selvagem dá lugar ao familiar.
                target.remove();
                Pet pet = new Pet(p.getUniqueId(), s.id(), s.name(), 1, 0, config.integer("familiares.vinculo.inicial", 60, 0, 100), Mode.SEGUIR, 0);
                pets.put(p.getUniqueId(), pet);
                save(pet);
                summon(p);
                at.getWorld().spawnParticle(Particle.HEART, at, 10, 0.3, 0.3, 0.3, 0);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
                msg.send(p, "familiar.domou", Messages.vars("especie", s.name()));
                Bukkit.getPluginManager().callEvent(new PetTameEvent(p, s.id()));
            }
        }
    }

    private void feed(Player p, Pet pet, ItemStack hand) {
        Species s = pet == null ? null : species.get(pet.species);
        if (s == null || !hand.getType().name().equals(s.food())) {
            return;
        }
        if (pet.bond >= 100 && pet.entity != null && pet.entity.getHealth() >= healthTop(pet.entity)) {
            msg.actionBar(p, "familiar.sem_fome", Messages.vars("nome", pet.name));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        pet.bond = PetRules.bond(pet.bond, config.integer("familiares.vinculo.por_comida", 15, 1, 100));
        pet.dirty = true;
        if (pet.entity != null && pet.entity.isValid()) {
            pet.entity.setHealth(Math.min(healthTop(pet.entity), pet.entity.getHealth() + config.decimal("familiares.vinculo.cura_por_comida", 4, 0, 100)));
            pet.entity.getWorld().spawnParticle(Particle.HEART, pet.entity.getLocation().add(0, pet.entity.getHeight(), 0), 4, 0.2, 0.2, 0.2, 0);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.2f);
        msg.actionBar(p, "familiar.alimentou", Messages.vars("nome", pet.name, "vinculo", pet.bond));
    }

    private static double healthTop(LivingEntity e) {
        AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
        return hp == null ? e.getHealth() : hp.getValue();
    }

    // ------------------------------------------------------------------ proteção

    /** O dono não fere o próprio familiar; os outros jogadores, só se a config deixar. Familiar não ataca ninguém. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetHurt(EntityDamageByEntityEvent event) {
        UUID owner = ownerOf(event.getEntity());
        if (owner == null) {
            if (ownerOf(event.getDamager()) != null) {
                event.setCancelled(true);
            }
            return;
        }
        Entity by = event.getDamager();
        Player attacker = by instanceof Player pl ? pl : by instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
        if (attacker != null && (attacker.getUniqueId().equals(owner) || !config.bool("familiares.outros_jogadores_ferem", false))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (ownerOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ morte e ressurreição

    /** O familiar morreu: deixa o Coração, com o nome dele. O dono tem alguns dias para revivê-lo. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPetDeath(EntityDeathEvent event) {
        UUID owner = ownerOf(event.getEntity());
        Pet p = owner == null ? null : pets.get(owner);
        if (p == null || p.entity != event.getEntity()) {
            if (owner != null) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        p.entity = null;
        p.died = System.currentTimeMillis();
        save(p);
        int days = config.integer("familiares.coracao.dias", 3, 1, 365);
        event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), heart(p, days));
        Player pl = Bukkit.getPlayer(owner);
        if (pl != null) {
            pl.playSound(pl.getLocation(), Sound.ENTITY_ALLAY_DEATH, 1f, 0.8f);
            msg.send(pl, "familiar.morreu", Messages.vars("nome", p.name, "dias", days));
        }
    }

    private ItemStack heart(Pet p, int days) {
        Material m = Material.matchMaterial(config.string("familiares.coracao.item", "HEART_OF_THE_SEA"));
        ItemStack it = new ItemStack(m == null ? Material.HEART_OF_THE_SEA : m);
        ItemMeta meta = it.getItemMeta();
        var vars = Messages.vars("nome", p.name, "level", p.level, "dias", days);
        meta.displayName(msg.get("familiar.coracao_nome", vars));
        meta.lore(msg.list("familiar.coracao_lore", vars));
        meta.getPersistentDataContainer().set(keyHeart, PersistentDataType.STRING, p.owner.toString());
        meta.getPersistentDataContainer().set(keyHeartDied, PersistentDataType.LONG, p.died);
        it.setItemMeta(meta);
        return it;
    }

    /** A flor que revive um familiar (vem de quest, de um andar, ou de /aincrad familiar flor). */
    public ItemStack flower(int amount) {
        Material m = Material.matchMaterial(config.string("familiares.flor.item", "ALLIUM"));
        ItemStack it = new ItemStack(m == null ? Material.ALLIUM : m, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(msg.get("familiar.flor_nome"));
        meta.lore(msg.list("familiar.flor_lore"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyFlower, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    /** Usar a flor com o Coração no inventário: dentro do prazo, o familiar volta com o mesmo nome e level. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUseFlower(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!hand.hasItemMeta() || !hand.getItemMeta().getPersistentDataContainer().has(keyFlower)) {
            return;
        }
        event.setCancelled(true);
        Pet pet = pets.get(p.getUniqueId());
        if (pet == null || pet.alive()) {
            msg.send(p, "familiar.flor_sem_uso");
            return;
        }
        if (!PetRules.canRevive(pet.died, System.currentTimeMillis(), config.integer("familiares.coracao.dias", 3, 1, 365))) {
            msg.send(p, "familiar.prazo_acabou", Messages.vars("nome", pet.name));
            return;
        }
        // O Coração precisa ser o deste familiar, desta morte.
        ItemStack heart = null;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.hasItemMeta() && p.getUniqueId().toString().equals(it.getItemMeta().getPersistentDataContainer().get(keyHeart, PersistentDataType.STRING))
                    && it.getItemMeta().getPersistentDataContainer().getOrDefault(keyHeartDied, PersistentDataType.LONG, 0L) == pet.died) {
                heart = it;
                break;
            }
        }
        if (heart == null) {
            msg.send(p, "familiar.sem_coracao", Messages.vars("nome", pet.name));
            return;
        }
        heart.setAmount(heart.getAmount() - 1);
        hand.setAmount(hand.getAmount() - 1);
        pet.died = 0;
        pet.mode = Mode.SEGUIR;
        pet.bond = Math.max(pet.bond, config.integer("familiares.vinculo.ao_reviver", 50, 0, 100));
        save(pet);
        summon(p);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.2);
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
        msg.send(p, "familiar.reviveu", Messages.vars("nome", pet.name, "level", pet.level));
    }

    // ------------------------------------------------------------------ painel e comandos

    private void setMode(Player p, Pet pet, Mode mode) {
        if (!pet.alive()) {
            msg.send(p, "familiar.esta_morto", Messages.vars("nome", pet.name));
            return;
        }
        pet.mode = mode;
        save(pet);
        if (mode == Mode.GUARDADO) {
            despawn(pet);
        } else {
            summon(p);
            refresh(pet, false);
        }
        msg.send(p, "familiar.modo." + mode.key(), Messages.vars("nome", pet.name));
    }

    /** O painel do familiar: level, vínculo, habilidade e os comandos. */
    public void panel(Player p) {
        Pet pet = pets.get(p.getUniqueId());
        if (pet == null) {
            msg.send(p, "familiar.nao_tem");
            return;
        }
        Species s = species.get(pet.species);
        long need = PetRules.xpToNext(pet.level, config.decimal("familiares.level.xp_base", 50, 1, 1_000_000), config.decimal("familiares.level.expoente", 1.5, 0.1, 5));
        int min = config.integer("familiares.vinculo.minimo_para_habilidade", 30, 0, 100);
        List<Component> lore = new ArrayList<>();
        lore.add(msg.get("familiar.painel_especie", Messages.vars("especie", s == null ? pet.species : s.name())));
        lore.add(msg.get("familiar.painel_level", Messages.vars("level", pet.level, "xp", pet.xp, "falta", need)));
        lore.add(msg.get("familiar.painel_vinculo", Messages.vars("vinculo", pet.bond)));
        if (s != null) {
            lore.add(msg.get("familiar.habilidade." + s.ability().key()));
            lore.add(msg.get(PetRules.abilityWorks(pet.bond, min) ? "familiar.painel_habilidade_ativa" : "familiar.painel_habilidade_parada",
                    Messages.vars("minimo", min)));
        }
        if (pet.alive()) {
            lore.add(msg.get("familiar.painel_modo", Messages.vars("modo", msg.raw("familiar.modo_nome." + pet.mode.key()))));
        } else {
            long left = pet.died + config.integer("familiares.coracao.dias", 3, 1, 365) * 86_400_000L - System.currentTimeMillis();
            lore.add(msg.get("familiar.painel_morto", Messages.vars("tempo", TimeFormat.duration(Math.max(0, left), 2))));
        }
        Menu menu = Menu.of(msg.get("familiar.painel_titulo", Messages.vars("nome", pet.name))).rows(3);
        menu.item(Menu.Button.of(new ItemStack(Material.NAME_TAG), Messages.parse("<white><nome>", Messages.vars("nome", pet.name)),
                pl -> msg.send(pl, "familiar.como_renomear")).lore(lore));
        menu.item(Menu.Button.of(new ItemStack(Material.LEAD), msg.get("familiar.botao_seguir"), pl -> act(pl, Mode.SEGUIR)).closing());
        menu.item(Menu.Button.of(new ItemStack(Material.OAK_FENCE), msg.get("familiar.botao_ficar"), pl -> act(pl, Mode.FICAR)).closing());
        menu.item(Menu.Button.of(new ItemStack(Material.CHEST), msg.get("familiar.botao_guardar"), pl -> act(pl, Mode.GUARDADO)).closing());
        menu.item(Menu.Button.of(new ItemStack(Material.BARRIER), msg.get("familiar.botao_libertar"), pl ->
                AincradCore.get().menus().confirm(pl, msg.get("familiar.confirmar_libertar", Messages.vars("nome", pet.name)), yes -> release(yes), no -> { }))
                .closing().lore(msg.list("familiar.botao_libertar_lore")));
        AincradCore.get().menus().open(p, menu);
    }

    private void act(Player p, Mode mode) {
        Pet pet = pets.get(p.getUniqueId());
        if (pet != null) {
            setMode(p, pet, mode);
        }
    }

    private void release(Player p) {
        Pet pet = pets.get(p.getUniqueId());
        if (pet != null) {
            delete(p.getUniqueId());
            msg.send(p, "familiar.libertou", Messages.vars("nome", pet.name));
        }
    }

    /** /familiar [seguir | ficar | guardar | nome &lt;nome&gt; | libertar] */
    public void command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        Pet pet = pets.get(p.getUniqueId());
        if (op.isEmpty()) {
            panel(p);
            return;
        }
        if (pet == null) {
            msg.send(p, "familiar.nao_tem");
            return;
        }
        switch (op) {
            case "seguir" -> setMode(p, pet, Mode.SEGUIR);
            case "ficar" -> setMode(p, pet, Mode.FICAR);
            case "guardar" -> setMode(p, pet, Mode.GUARDADO);
            case "nome" -> {
                String name = args.length > 1 ? String.join(" ", List.of(args).subList(1, args.length)).trim() : "";
                if (!name.matches("[\\p{L}\\p{N} ]{2,16}")) {
                    msg.send(p, "familiar.nome_invalido");
                    return;
                }
                pet.name = name;
                save(pet);
                refresh(pet, false);
                msg.send(p, "familiar.renomeou", Messages.vars("nome", name));
            }
            case "libertar" -> AincradCore.get().menus().confirm(p, msg.get("familiar.confirmar_libertar", Messages.vars("nome", pet.name)),
                    this::release, no -> { });
            default -> msg.send(p, "familiar.uso");
        }
    }

    // ------------------------------------------------------------------ admin

    /** Admin: dá um familiar da espécie ao jogador (troca o que ele tinha). */
    public boolean adminGive(Player target, String speciesId) {
        Species s = species.get(speciesId.toLowerCase(Locale.ROOT));
        if (s == null) {
            return false;
        }
        Pet old = pets.get(target.getUniqueId());
        if (old != null) {
            despawn(old);
        }
        Pet pet = new Pet(target.getUniqueId(), s.id(), s.name(), 1, 0, config.integer("familiares.vinculo.inicial", 60, 0, 100), Mode.SEGUIR, 0);
        pets.put(target.getUniqueId(), pet);
        save(pet);
        summon(target);
        return true;
    }

    /** Admin: tira o familiar do jogador. @return false se ele não tinha */
    public boolean adminRemove(Player target) {
        if (!pets.containsKey(target.getUniqueId())) {
            return false;
        }
        delete(target.getUniqueId());
        return true;
    }

    /** Admin: define o level do familiar do jogador. @return false se ele não tem familiar */
    public boolean adminLevel(Player target, int level) {
        Pet pet = pets.get(target.getUniqueId());
        if (pet == null) {
            return false;
        }
        pet.level = Math.max(1, Math.min(config.integer("familiares.level.maximo", 50, 1, 1000), level));
        pet.xp = 0;
        save(pet);
        refresh(pet, true);
        return true;
    }
}
