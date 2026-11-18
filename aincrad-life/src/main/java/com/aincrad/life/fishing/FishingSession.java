package com.aincrad.life.fishing;

import com.aincrad.core.AincradCore;
import com.aincrad.core.message.Messages;
import com.aincrad.life.fishing.FishingRegistry.Bait;
import com.aincrad.life.fishing.FishingRegistry.Fish;
import com.aincrad.life.fishing.FishingRegistry.Point;
import com.aincrad.life.fishing.FishingRegistry.Rod;
import com.aincrad.life.fishing.FishingRegistry.Zone;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.station.StationPanel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Uma pescaria num ponto de pesca, do lançamento até o peixe: esperar a mordida, fisgar dentro
 * da janela e cansar o peixe mantendo a tensão da linha na faixa verde. Um holograma sobre a
 * boia acompanha, para quem está por perto ver.
 */
final class FishingSession {

    private enum Phase {
        ESPERANDO, MORDEU, LUTANDO, FIM
    }

    private final FishingService service;
    private final Messages msg;
    private final ProfessionService professions;
    private final Player player;
    private final FishHook hook;
    private final Point point;
    private final Zone zone;
    private final Rod rod;
    private final Bait bait;
    private final FishingService.Settings cfg;
    private final boolean bedrock;
    private Phase phase = Phase.ESPERANDO;
    private int wait;
    private int window;
    private int age;
    private Fish fish;
    private boolean legendary;
    private TensionFight fight;
    private TextDisplay hologram;

    // A arrancada em andamento.
    private int burstLeft;
    private int burstDirection;
    private float burstYaw;
    private boolean burstCountered;

    FishingSession(FishingService service, Player player, FishHook hook, Point point, Zone zone, Rod rod, Bait bait, FishingService.Settings cfg) {
        this.service = service;
        this.msg = service.messages();
        this.professions = service.professions();
        this.player = player;
        this.hook = hook;
        this.point = point;
        this.zone = zone;
        this.rod = rod;
        this.bait = bait;
        this.cfg = cfg;
        this.bedrock = AincradCore.get().isBedrock(player);
        this.wait = biteDelay();
    }

    Player player() {
        return player;
    }

    private double mod(String key) {
        return professions.modifier(player, Profession.PESCA, key);
    }

    /** O que mordeu não é peixe: um baú ou um material (árvore do pescador). */
    private FishingPerks.Treasure treasure;
    private double catalystLuck;

    private int biteDelay() {
        int min = Math.min(cfg.biteMin(), cfg.biteMax());
        int max = Math.max(cfg.biteMin(), cfg.biteMax());
        int base = ThreadLocalRandom.current().nextInt(min, max + 1);
        return (int) Math.max(20, base * Math.max(0.2, 1 + mod("tempo_mordida")));
    }

    // ------------------------------------------------------------------ tempo

    void tick() {
        if (phase == Phase.FIM) {
            return;
        }
        if (!hook.isValid() || !player.isOnline() || player.isDead()) {
            service.end(this);
            return;
        }
        age++;
        switch (phase) {
            case ESPERANDO -> {
                if (hook.getState() == FishHook.HookState.BOBBING && --wait <= 0) {
                    bite();
                }
            }
            case MORDEU -> {
                if (--window <= 0) {
                    // Passou da janela: o peixe foi embora, a linha continua na água.
                    phase = Phase.ESPERANDO;
                    wait = biteDelay();
                    removeHologram();
                    msg.actionBar(player, "pesca.demorou");
                }
            }
            case LUTANDO -> fightTick();
            default -> {
            }
        }
    }

    /** O peixe mordeu: sorteia qual é e abre a janela da fisgada. */
    private void bite() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double proficiency = professions.proficiency(player, Profession.PESCA);
        // O lendário só morde para quem liberou a caça aos lendários na árvore.
        if (professions.useCatalyst(player)) {
            // O Catalisador Universal na outra mão: esta mordida tem mais sorte.
            catalystLuck = service.treeValue("catalisador_sorte", 0.25);
        }
        legendary = mod("desbloqueia_lendario") >= 1 && service.registry().legendaryPossible(point, zone, bait, proficiency)
                && rnd.nextDouble() < point.legendary().chance() * (1 + Math.max(0, mod("chance_lendario")));
        treasure = legendary ? null : service.rollTreasure(player, zone);
        fish = legendary ? service.registry().fish(point.legendary().fish())
                : service.registry().roll(point, zone, service.luck(player, rod, bait, cfg) + catalystLuck, rnd.nextDouble());
        // Isca dourada (técnica): o que morde é um dos peixes raros da zona.
        if (!legendary && bait != null && bait.id().equals(FishingPerks.GOLDEN_BAIT)) {
            List<Fish> rare = FishingPerks.rare(point, zone, service.registry().fishes());
            if (!rare.isEmpty()) {
                fish = rare.get(rnd.nextInt(rare.size()));
            }
        }
        if (fish == null) {
            service.end(this);
            return;
        }
        double w = cfg.rarities().get(fish.rarity()).window() * (1 + Math.max(0, mod("janela_fisgada"))) * (bedrock ? cfg.bedrockWindow() : 1);
        window = (int) Math.max(4, Math.round(w));
        phase = Phase.MORDEU;
        Location at = hook.getLocation();
        hook.setVelocity(new Vector(0, -0.25, 0));
        at.getWorld().spawnParticle(Particle.SPLASH, at, 20, 0.2, 0.1, 0.2, 0.1);
        at.getWorld().playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.8f);
        msg.title(player, "pesca.mordeu", Duration.ofMillis(window * 50L));
        hologram(List.of(msg.get("pesca.holo_mordeu")));
    }

    /**
     * O jogador clicou com a linha na água.
     *
     * @return true se a boia deve ficar (foi a fisgada ou uma puxada); false para recolher a linha
     */
    boolean onReel() {
        switch (phase) {
            case MORDEU -> {
                if (treasure != null) {
                    // Não é peixe: o baú ou o material vem direto, sem cansar nada.
                    phase = Phase.FIM;
                    useBait(true);
                    hook.remove();
                    service.giveTreasure(player, treasure);
                    service.end(this);
                    return true;
                }
                if (!legendary && service.useHarpoon(player)) {
                    caught();
                    return true;
                }
                startFight();
                return true;
            }
            case LUTANDO -> {
                fight.pull();
                return true;
            }
            default -> {
                // Recolheu antes da mordida.
                service.end(this);
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ cansar o peixe

    private void startFight() {
        double proficiency = professions.proficiency(player, Profession.PESCA);
        FishingService.RarityRules r = cfg.rarities().get(fish.rarity());
        double width = Math.min(0.8, cfg.band() + cfg.bandAtMaster() * proficiency / Profession.MAX + mod("faixa_verde"));
        int stamina = (int) Math.max(10, r.stamina() * Math.max(0.2, 1 - mod("cansar_peixe")));
        int snap = (int) Math.max(1, Math.round(rod.snapTicks() * (1 + Math.max(0, mod("resistencia_linha")))));
        double burst = cfg.burst() * Math.max(0, 1 + mod("arrancada"));
        fight = new TensionFight(new TensionFight.Rules(0.5 - width / 2, 0.5 + width / 2, cfg.redFrom(), cfg.looseBelow(),
                cfg.decay(), cfg.pull(), burst, stamina, snap, cfg.looseTicks()));
        phase = Phase.LUTANDO;
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
        render();
    }

    private void fightTick() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (burstLeft > 0) {
            burstLeft--;
            // O peixe arranca para um lado; o pescador vira o olhar para o lado oposto.
            float turned = wrap(player.getLocation().getYaw() - burstYaw) * -burstDirection;
            if (turned >= cfg.burstDegrees()) {
                burstCountered = true;
            }
        } else if (!bedrock && rnd.nextDouble() < cfg.rarities().get(fish.rarity()).burstPerSecond() / 20.0) {
            burstLeft = cfg.burstTicks();
            burstDirection = rnd.nextBoolean() ? 1 : -1;
            burstYaw = player.getLocation().getYaw();
            burstCountered = false;
            player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 0.6f);
        }
        fight.tick(burstLeft > 0 && !burstCountered);
        switch (fight.state()) {
            case PEGOU -> caught();
            case ARREBENTOU -> snapped();
            case ESCAPOU -> escaped();
            default -> {
                if (age % 2 == 0) {
                    render();
                }
            }
        }
    }

    /** Diferença de ângulo entre -180 e 180. */
    private static float wrap(float degrees) {
        float d = degrees % 360f;
        return d >= 180f ? d - 360f : d < -180f ? d + 360f : d;
    }

    private void render() {
        String bar = tensionBar();
        List<Component> lines = new ArrayList<>();
        lines.add(mod("ver_peixe") >= 1 ? msg.get("pesca.holo_peixe", Messages.vars("peixe", fish.name())) : msg.get("pesca.holo_peixe_oculto"));
        lines.add(Messages.parse("<gray>" + msg.raw("pesca.holo_tensao") + " " + bar));
        lines.add(Messages.parse("<gray>" + msg.raw("pesca.holo_peixe_forca") + " " + StationPanel.bar(fight.staminaLeft(), 14)));
        Component hint = null;
        if (burstLeft > 0 && !burstCountered) {
            hint = msg.get(burstDirection > 0 ? "pesca.arrancada_direita" : "pesca.arrancada_esquerda");
            lines.add(hint);
        }
        hologram(lines);
        player.sendActionBar(hint != null ? hint : Messages.parse(bar));
    }

    /** A tensão da linha: faixa verde, vermelho no fim e o ponteiro. */
    private String tensionBar() {
        TensionFight.Rules r = fight.rules();
        int width = 30;
        int pointer = (int) Math.min(width - 1, Math.floor(fight.tension() * width));
        String pointerColor = fight.inRed() ? "<red>" : fight.inGreen() ? "<white>" : "<yellow>";
        StringBuilder sb = new StringBuilder("<dark_gray>[");
        for (int i = 0; i < width; i++) {
            double x = (i + 0.5) / width;
            if (i == pointer) {
                sb.append(pointerColor).append('█');
            } else if (x >= r.redFrom()) {
                sb.append("<red>|");
            } else if (x >= r.low() && x <= r.high()) {
                sb.append("<green>=");
            } else {
                sb.append("<dark_gray>-");
            }
        }
        return sb.append("<dark_gray>]").toString();
    }

    // ------------------------------------------------------------------ fim

    private void useBait(boolean canSpare) {
        if (bait == null) {
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        Bait now = service.baitOf(off);
        if (now == null || !now.id().equals(bait.id())) {
            return;
        }
        if (canSpare && ThreadLocalRandom.current().nextDouble() < mod("isca_poupada")) {
            return;
        }
        off.setAmount(off.getAmount() - 1);
    }

    private void caught() {
        phase = Phase.FIM;
        Location at = hook.getLocation();
        useBait(true);
        hook.remove();
        if (legendary) {
            // O lendário não vem na linha: o Senhor do Lago sai da água.
            service.spawnLord(player, point, at);
        } else {
            int mm = FishingRegistry.sizeMm(fish, ThreadLocalRandom.current().nextDouble());
            FishingService.give(player, service.fishItem(fish, mm, player.getName()));
            boolean best = service.register(player, point, fish, mm);
            professions.practice(player, Profession.PESCA, fish.difficulty(), true);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.2f);
            var vars = Messages.vars("peixe", fish.name(), "tamanho", FishingService.cm(mm),
                    "raridade", msg.get("pesca.raridade." + fish.rarity().key()));
            msg.send(player, best ? "pesca.pegou_recorde" : "pesca.pegou", vars);
        }
        service.end(this);
    }

    private void snapped() {
        phase = Phase.FIM;
        useBait(false);
        hook.remove();
        professions.practice(player, Profession.PESCA, fish.difficulty(), false);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.4f);
        msg.send(player, bait == null ? "pesca.arrebentou" : "pesca.arrebentou_isca");
        service.end(this);
    }

    private void escaped() {
        phase = Phase.FIM;
        hook.remove();
        professions.practice(player, Profession.PESCA, fish.difficulty(), false);
        msg.send(player, "pesca.soltou");
        service.end(this);
    }

    // ------------------------------------------------------------------ holograma

    private void hologram(List<Component> lines) {
        Location at = hook.getLocation().add(0, 0.9, 0);
        if (hologram == null || !hologram.isValid()) {
            hologram = at.getWorld().spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setLineWidth(400);
                d.setBackgroundColor(Color.fromARGB(110, 10, 14, 18));
            });
        } else if (age % 10 == 0) {
            hologram.teleport(at);
        }
        hologram.text(Component.join(JoinConfiguration.newlines(), lines));
    }

    private void removeHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
    }

    /** Tira o que a sessão pôs no mundo. */
    void dispose() {
        phase = Phase.FIM;
        removeHologram();
    }
}
