package com.aincrad.life.smith;

import com.aincrad.core.message.Messages;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.smith.ForgedItems.Upgrade;
import com.aincrad.life.station.StationPanel;
import com.aincrad.life.station.StationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Um aprimoramento sobre a bigorna: o item fica na outra mão do ferreiro; materiais extras do
 * atributo vão nos encaixes e aumentam a chance; cinco batidas, e cada perfeita soma mais um
 * pouco. Cada tentativa, com sucesso ou não, gasta uma das tentativas do item. O item nunca é
 * destruído.
 */
final class UpgradeSession implements SmithService.Session {

    private enum State {
        MONTANDO, BATENDO, FIM
    }

    private final SmithService service;
    private final Messages msg;
    private final ProfessionService professions;
    private final ForgedItems forged;
    private final Player player;
    private final StationService.Placed station;
    private final StationPanel panel;
    private final Upgrade upgrade;
    private final SmithService.Settings cfg;
    private final ItemStack snapshot;
    private final Material extra;
    private State state = State.MONTANDO;
    private int materials;
    private int remaining;
    private int perfects;
    private int delay;
    private Component feedback;

    UpgradeSession(SmithService service, Player player, StationService.Placed station, StationPanel panel, Upgrade upgrade,
                   SmithService.Settings cfg) {
        this.service = service;
        this.msg = service.messages();
        this.professions = service.professions();
        this.forged = service.forged();
        this.player = player;
        this.station = station;
        this.panel = panel;
        this.upgrade = upgrade;
        this.cfg = cfg;
        this.snapshot = player.getInventory().getItemInOffHand().clone();
        this.extra = service.upgradeMaterial(upgrade);
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public StationService.Placed station() {
        return station;
    }

    void open() {
        List<double[]> points = SmithService.slotPoints(station, Math.max(1, cfg.upSlots()));
        for (int i = 0; i < cfg.upSlots(); i++) {
            panel.addSlot(msg.raw("forja.up_encaixe"), points.get(i), false, it -> it.getType() == extra && !it.hasItemMeta());
        }
        panel.markPoints(station.type.marks());
        panel.onSlotChange(this::render);
        panel.onClose(returned -> {
            if (state == State.BATENDO && player.isOnline()) {
                msg.send(player, "forja.up_abandonou");
            }
            state = State.FIM;
            service.ended(this);
        });
        render();
        msg.send(player, "forja.up_aberta", Messages.vars("atributo", msg.raw("forja.atributo." + upgrade.key()),
                "material", extra.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        if (panel.simple()) {
            service.panelManager().openSlotMenu(player, msg.get("forja.up_holo_titulo",
                    Messages.vars("atributo", msg.raw("forja.atributo." + upgrade.key()))), pl -> start());
        }
    }

    private int filled() {
        int n = 0;
        for (StationPanel.Slot s : panel.slots()) {
            if (s.item() != null) {
                n++;
            }
        }
        return n;
    }

    private boolean sameItem() {
        return player.getInventory().getItemInOffHand().equals(snapshot);
    }

    private double chance(int mats, int perfectHits) {
        double bonus = service.mod(player, "chance_aprimoramento") + (upgrade == Upgrade.AFIACAO ? service.mod(player, "chance_afiacao") : 0);
        return ForgeMath.upgradeChance(cfg.upBase(), cfg.upAtMaster(), professions.proficiency(player, Profession.FERREIRO),
                forged.level(snapshot, upgrade), cfg.upPerLevel(), mats, cfg.upPerMaterial(), perfectHits, cfg.upPerHit(), bonus, cfg.upCap());
    }

    @Override
    public void click(boolean sneaking) {
        if (state != State.MONTANDO) {
            return;
        }
        if (sneaking) {
            panel.close(true);
            msg.send(player, "forja.cancelou");
        } else {
            start();
        }
    }

    private void start() {
        if (state != State.MONTANDO || panel.closed()) {
            return;
        }
        if (!sameItem()) {
            msg.send(player, "forja.item_mudou");
            panel.close(true);
            return;
        }
        String refusal = service.upgradeRefusal(player, snapshot, upgrade);
        if (refusal != null) {
            msg.send(player, refusal, Messages.vars("limite", service.upgradeLimit(player)));
            panel.close(true);
            return;
        }
        materials = filled();
        panel.consume();
        panel.lockSlots(true);
        remaining = cfg.upHits();
        delay = 20;
        state = State.BATENDO;
        service.stations().setActive(station, true);
        render();
    }

    @Override
    public void tick() {
        if (state != State.BATENDO || panel.closed() || panel.markActive() || --delay > 0) {
            return;
        }
        int perfect = cfg.perfect() + (int) (professions.proficiency(player, Profession.FERREIRO) / cfg.perfectPer());
        panel.mark(ThreadLocalRandom.current().nextInt(Math.max(1, panel.markCount())), cfg.upRing(), perfect, Math.max(perfect, cfg.good()), this::hit);
    }

    private void hit(StationPanel.Hit h) {
        if (state != State.BATENDO) {
            return;
        }
        if (h == StationPanel.Hit.PERFEITA) {
            perfects++;
        }
        feedback = msg.get("painel.batida_" + h.name().toLowerCase(Locale.ROOT));
        remaining--;
        delay = cfg.hitGap();
        if (remaining <= 0) {
            roll();
        } else {
            render();
        }
    }

    @Override
    public void bellows() {
        // O aprimoramento não usa o fole.
    }

    @Override
    public void quench() {
        // Nem o barril.
    }

    private void roll() {
        state = State.FIM;
        ItemStack item = player.getInventory().getItemInOffHand();
        if (!sameItem()) {
            // O item saiu da outra mão no meio das batidas: nada acontece com ele.
            msg.send(player, "forja.item_mudou");
            panel.close(false);
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int tries = forged.tries(item);
        if (tries < 0) {
            tries = cfg.triesBase();
        }
        int level = forged.level(item, upgrade);
        double chance = chance(materials, perfects);
        // Fio Lendário (Afiador): uma vez por arma, a afiação dá certo com certeza.
        if (upgrade == Upgrade.AFIACAO && service.mod(player, "tecnica_fio_lendario") >= 1 && !forged.marked(item, "fio_lendario")) {
            chance = 1.0;
            forged.mark(item, "fio_lendario");
            msg.send(player, "forja.tecnica_fio_lendario");
        }
        ForgeMath.Outcome outcome = ForgeMath.upgrade(chance, rnd.nextDouble(), rnd.nextDouble(), level,
                service.mod(player, "falha_sem_queda") >= 1);
        Upgrade other = upgrade;
        if (outcome == ForgeMath.Outcome.DESVIOU) {
            // O +1 vai para outro atributo que ainda tenha espaço; sem nenhum, só os materiais se perdem.
            List<Upgrade> free = new ArrayList<>();
            for (Upgrade u : Upgrade.values()) {
                if (u != upgrade && forged.level(item, u) < service.upgradeLimit(player)) {
                    free.add(u);
                }
            }
            if (free.isEmpty()) {
                outcome = ForgeMath.Outcome.SO_MATERIAIS;
            } else {
                other = free.get(rnd.nextInt(free.size()));
            }
        }
        forged.setTries(item, tries - 1);
        switch (outcome) {
            case SUCESSO -> forged.addLevel(item, upgrade, 1);
            case CAIU -> forged.addLevel(item, upgrade, -1);
            case DESVIOU -> forged.addLevel(item, other, 1);
            default -> {
            }
        }
        player.getInventory().setItemInOffHand(item);
        boolean ok = outcome == ForgeMath.Outcome.SUCESSO;
        professions.practice(player, Profession.FERREIRO, Math.min(1000, 300 + level * 60), ok);

        Location at = panel.base().add(0, 1.3, 0);
        at.getWorld().spawnParticle(ok ? Particle.ELECTRIC_SPARK : Particle.SMOKE, at, 30, 0.3, 0.3, 0.3, 0.05);
        at.getWorld().playSound(at, ok ? Sound.BLOCK_ANVIL_USE : Sound.BLOCK_ANVIL_LAND, 1f, ok ? 1.4f : 0.6f);
        msg.send(player, "forja.up_resultado." + outcome.name().toLowerCase(Locale.ROOT), Messages.vars(
                "atributo", msg.raw("forja.atributo." + upgrade.key()), "outro", msg.raw("forja.atributo." + other.key()),
                "nivel", forged.level(item, upgrade), "tentativas", tries - 1, "chance", Math.round(chance * 100)));
        panel.close(false);
    }

    private void render() {
        if (panel.closed()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        int level = forged.level(snapshot, upgrade);
        lines.add(msg.get("forja.up_holo_titulo", Messages.vars("atributo", msg.raw("forja.atributo." + upgrade.key()))));
        lines.add(msg.get("forja.up_holo_nivel", Messages.vars("de", level, "para", level + 1)));
        int mats = state == State.MONTANDO ? filled() : materials;
        lines.add(msg.get("forja.up_holo_chance", Messages.vars("chance", Math.round(chance(mats, perfects) * 100))));
        if (state == State.MONTANDO) {
            lines.add(msg.get("forja.holo_comecar"));
        } else {
            lines.add(msg.get("forja.up_holo_batidas", Messages.vars("faltam", Math.max(0, remaining))));
            if (feedback != null) {
                lines.add(feedback);
            }
        }
        panel.hologram(lines);
    }
}
