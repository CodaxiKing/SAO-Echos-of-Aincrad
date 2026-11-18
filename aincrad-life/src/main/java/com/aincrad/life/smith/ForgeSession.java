package com.aincrad.life.smith;

import com.aincrad.core.message.Messages;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.smith.ForgeRegistry.Blueprint;
import com.aincrad.life.smith.ForgeRegistry.Mat;
import com.aincrad.life.smith.ForgeRegistry.Named;
import com.aincrad.life.smith.ForgeRegistry.Rarity;
import com.aincrad.life.smith.ForgeRegistry.Role;
import com.aincrad.life.station.StationPanel;
import com.aincrad.life.station.StationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Uma forja sobre a bigorna: o ferreiro põe os materiais nos encaixes do projeto, clica na
 * bigorna e bate — mira na marca e bate quando o anel fecha, com o lingote na faixa de calor
 * (o fole da fornalha reaquece). Três trincas quebram a peça. Itens raros passam pela têmpera
 * no barril. A qualidade final decide a raridade, dentro do teto do material base.
 */
final class ForgeSession implements SmithService.Session {

    private enum State {
        MONTANDO, FORJANDO, TEMPERA, FIM
    }

    private final SmithService service;
    private final Messages msg;
    private final ProfessionService professions;
    private final Player player;
    private final StationService.Placed station;
    private final StationPanel panel;
    private final Blueprint blueprint;
    private final SmithService.Settings cfg;
    private final List<Integer> slotIndex = new ArrayList<>();
    private State state = State.MONTANDO;

    // A peça em andamento.
    private List<Mat> mats = List.of();
    private List<ItemStack> used = List.of();
    private Named named;
    private Rarity cap = Rarity.COMUM;
    private int rank = 1;
    private ForgeMath.Work work;
    private double heat;
    private int delay;
    private int age;
    private int temperLeft;
    private Component feedback;

    ForgeSession(SmithService service, Player player, StationService.Placed station, StationPanel panel, Blueprint blueprint,
                 SmithService.Settings cfg) {
        this.service = service;
        this.msg = service.messages();
        this.professions = service.professions();
        this.player = player;
        this.station = station;
        this.panel = panel;
        this.blueprint = blueprint;
        this.cfg = cfg;
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public StationService.Placed station() {
        return station;
    }

    private double mod(String key) {
        return service.mod(player, key);
    }

    // ------------------------------------------------------------------ encaixes

    void open() {
        List<Role> roles = blueprint.type().slots();
        List<double[]> points = SmithService.slotPoints(station, roles.size());
        for (int i = 0; i < roles.size(); i++) {
            Role role = roles.get(i);
            slotIndex.add(panel.addSlot(msg.raw("forja.encaixe." + role.key()), points.get(i), !role.optional(),
                    it -> role.accepts(service.identify(it))));
        }
        panel.markPoints(station.type.marks());
        panel.onSlotChange(this::render);
        panel.onClose(returned -> {
            if ((state == State.FORJANDO || state == State.TEMPERA) && player.isOnline()) {
                msg.send(player, "forja.abandonou");
            }
            state = State.FIM;
            service.ended(this);
        });
        render();
        msg.send(player, "forja.aberta", Messages.vars("projeto", blueprint.name()));
        if (panel.simple()) {
            slotMenu();
        }
    }

    private void slotMenu() {
        // No modo simples (Bedrock) os encaixes viram um menu; "Pronto" começa a forja.
        service.panelManager().openSlotMenu(player, msg.get("forja.titulo", Messages.vars("projeto", blueprint.name())), pl -> start());
    }

    /** O material de cada encaixe, na ordem do tipo (null = vazio). */
    private List<Mat> placed() {
        List<Mat> out = new ArrayList<>();
        for (int index : slotIndex) {
            out.add(service.identify(panel.slots().get(index).item()));
        }
        return out;
    }

    private List<String> missing(List<Mat> placed) {
        List<String> out = new ArrayList<>();
        List<Role> roles = blueprint.type().slots();
        for (int i = 0; i < roles.size(); i++) {
            if (!roles.get(i).optional() && placed.get(i) == null) {
                out.add(msg.raw("forja.encaixe." + roles.get(i).key()));
            }
        }
        return out;
    }

    @Override
    public void click(boolean sneaking) {
        switch (state) {
            case MONTANDO -> {
                if (sneaking) {
                    panel.close(true);
                    msg.send(player, "forja.cancelou");
                } else {
                    start();
                }
            }
            // Sem fornalha por perto, agachar e clicar na bigorna faz as vezes do fole.
            case FORJANDO -> {
                if (sneaking) {
                    bellows();
                }
            }
            default -> {
            }
        }
    }

    private void start() {
        if (state != State.MONTANDO || panel.closed()) {
            return;
        }
        List<Mat> placed = placed();
        List<String> missing = missing(placed);
        if (!missing.isEmpty()) {
            msg.send(player, "forja.faltam", Messages.vars("encaixes", String.join(", ", missing)));
            if (panel.simple()) {
                slotMenu();
            }
            return;
        }
        Learnable l = professions.learnable(SmithService.KIND, blueprint.id());
        if (l == null || !professions.canProduce(player, l)) {
            msg.send(player, "forja.nao_pode");
            return;
        }
        mats = placed;
        named = service.registry().named(blueprint, placed);
        rank = ForgeMath.rank(blueprint.type(), placed);
        cap = ForgeMath.cap(blueprint.type(), placed);
        used = panel.consume();
        panel.lockSlots(true);
        work = new ForgeMath.Work(ForgeMath.hits(blueprint.type(), rank, mod("batidas")), cfg.maxCracks() + (int) Math.max(0, mod("trincas_extras")));
        heat = cfg.heatStart();
        delay = 25;
        state = State.FORJANDO;
        service.stations().setActive(station, true);
        player.playSound(panel.base(), Sound.BLOCK_FIRE_AMBIENT, 1f, 1f);
        render();
    }

    // ------------------------------------------------------------------ batidas

    private boolean heatOk() {
        return heat >= cfg.heatLow() && heat <= cfg.heatHigh();
    }

    @Override
    public void tick() {
        if (panel.closed()) {
            return;
        }
        age++;
        if (state == State.FORJANDO) {
            // O lingote esfria com o tempo (e mais um pouco a cada batida).
            heat = Math.max(0, heat - cfg.heatDecay() * Math.max(0.1, 1 - mod("calor_estavel")));
            if (!panel.markActive() && --delay <= 0) {
                int perfect = cfg.perfect() + (int) (professions.proficiency(player, Profession.FERREIRO) / cfg.perfectPer());
                int good = Math.max(perfect, cfg.good());
                panel.mark(ThreadLocalRandom.current().nextInt(Math.max(1, panel.markCount())),
                        ForgeMath.ringTicks(cfg.ringBase(), rank, cfg.ringPerRank(), cfg.ringMin()),
                        widen(perfect, "janela_perfeita"), widen(good, "janela_boa"), this::hit);
            }
            if (age % 4 == 0) {
                render();
            }
        } else if (state == State.TEMPERA && --temperLeft <= 0) {
            // Atrasou a têmpera: a peça perde qualidade.
            work.lose(cfg.temperPenalty());
            msg.send(player, "forja.tempera_atrasada");
            finish(false);
        }
    }

    private int widen(int base, String key) {
        return (int) Math.ceil(base * (1 + Math.max(0, mod(key))) - 1e-9);
    }

    private void hit(StationPanel.Hit h) {
        if (state != State.FORJANDO) {
            return;
        }
        boolean ok = heatOk();
        StationPanel.Hit real = work.strike(h, ok);
        heat = Math.max(0, heat - cfg.heatPerHit());
        feedback = !ok ? msg.get(heat < cfg.heatLow() ? "forja.batida_fria" : "forja.batida_quente")
                : msg.get("painel.batida_" + real.name().toLowerCase(Locale.ROOT));
        if (work.broken()) {
            fail();
        } else if (work.finished()) {
            afterHits();
        } else {
            delay = cfg.hitGap();
            render();
        }
    }

    @Override
    public void bellows() {
        if (state != State.FORJANDO) {
            return;
        }
        heat = Math.min(1, heat + cfg.bellows());
        Location at = panel.base().add(0, 1.2, 0);
        at.getWorld().spawnParticle(Particle.FLAME, at, 6, 0.15, 0.1, 0.15, 0.01);
        player.playSound(at, Sound.ITEM_FIRECHARGE_USE, 0.5f, 0.8f);
        render();
    }

    // ------------------------------------------------------------------ fim

    /** Três trincas: a peça quebrou e parte dos materiais se perde. */
    private void fail() {
        state = State.FIM;
        double keep = Math.max(0, Math.min(1, 1 - (cfg.failLoss() + mod("materiais_na_falha"))));
        List<ItemStack> back = new ArrayList<>(used);
        Collections.shuffle(back);
        int n = (int) Math.round(back.size() * keep);
        for (int i = 0; i < n; i++) {
            SmithService.give(player, back.get(i));
        }
        professions.practice(player, Profession.FERREIRO, blueprint.difficulty(), false);
        Location at = panel.base().add(0, 1.2, 0);
        at.getWorld().playSound(at, Sound.ENTITY_ITEM_BREAK, 1f, 0.7f);
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 15, 0.2, 0.2, 0.2, 0.01);
        msg.send(player, "forja.quebrou", Messages.vars("projeto", blueprint.name(), "voltaram", n, "total", back.size()));
        panel.close(false);
    }

    /** As batidas acabaram: item raro (ou melhor) passa pela têmpera, se houver barril por perto. */
    private void afterHits() {
        Rarity now = ForgeMath.rarity(work.quality(), cfg.thresholds(), cap);
        if (now.ordinal() >= cfg.temperFrom().ordinal() && service.quenchNear(panel.base())) {
            state = State.TEMPERA;
            // O pó de têmpera do alquimista (um por peça) alarga a janela.
            double powder = service.takeTemperPowder(player);
            temperLeft = (int) Math.round(cfg.temperTicks() * (1 + Math.max(0, mod("janela_tempera")) + powder));
            if (powder > 0) {
                msg.send(player, "forja.po_usado");
            }
            player.playSound(panel.base(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.4f);
            render();
        } else {
            finish(false);
        }
    }

    @Override
    public void quench() {
        if (state != State.TEMPERA) {
            return;
        }
        Location at = player.getLocation();
        at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
        at.getWorld().spawnParticle(Particle.CLOUD, at.add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.02);
        msg.send(player, "forja.tempera_certa");
        finish(true);
    }

    private void finish(boolean tempered) {
        state = State.FIM;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double quality = Math.max(0, Math.min(100, work.quality()
                + 100 * mod(blueprint.type().armor() ? "qualidade_armaduras" : "qualidade_armas")));
        Rarity rarity = ForgeMath.rarity(quality, cfg.thresholds(), cap);
        int tries = ForgeMath.tries(cfg.triesBase(), quality, cfg.triesExtraFrom(), cfg.triesExtra(), (int) mod("tentativas_extras"));
        Map<String, Double> stats = ForgeMath.stats(blueprint.type(), mats, service.registry().dominant(), quality,
                mod("atributo_dominante"), named == null ? null : named.bonus());
        if (tempered) {
            stats.merge("durabilidade", cfg.temperBonus(), Double::sum);
        }
        ItemStack item = service.build(blueprint, rarity, named, stats, (int) Math.round(quality), tries, player);
        SmithService.give(player, item);
        if (!used.isEmpty() && rnd.nextDouble() < mod("material_poupado")) {
            SmithService.give(player, used.get(rnd.nextInt(used.size())));
            msg.send(player, "forja.poupou");
        }
        professions.practice(player, Profession.FERREIRO, blueprint.difficulty(), true);

        Location at = panel.base().add(0, 1.3, 0);
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 40, 0.3, 0.3, 0.3, 0.1);
        at.getWorld().playSound(at, Sound.BLOCK_ANVIL_USE, 1f, 0.9f);
        at.getWorld().playSound(at, rarity.ordinal() >= Rarity.RARO.ordinal() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
        var vars = Messages.vars("item", named == null ? blueprint.name() : named.name(), "qualidade", Math.round(quality),
                "raridade", Messages.parse(msg.raw("forja.raridade_cor." + rarity.key()) + msg.raw("forja.raridade." + rarity.key())),
                "tentativas", tries);
        msg.send(player, "forja.pronto", vars);
        if (named != null) {
            msg.send(player, "forja.nomeada", vars);
        }
        panel.close(false);
    }

    // ------------------------------------------------------------------ holograma

    private void render() {
        if (panel.closed()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (state == State.MONTANDO) {
            lines.add(msg.get("forja.titulo", Messages.vars("projeto", blueprint.name())));
            List<Mat> placed = placed();
            List<String> missing = missing(placed);
            if (!missing.isEmpty()) {
                lines.add(msg.get("forja.holo_faltam", Messages.vars("encaixes", String.join(", ", missing))));
            } else {
                int r = ForgeMath.rank(blueprint.type(), placed);
                Rarity c = ForgeMath.cap(blueprint.type(), placed);
                String dom = ForgeMath.dominant(blueprint.type(), placed);
                lines.add(msg.get("forja.holo_previa", Messages.vars("batidas", ForgeMath.hits(blueprint.type(), r, mod("batidas")),
                        "teto", Messages.parse(msg.raw("forja.raridade_cor." + c.key()) + msg.raw("forja.raridade." + c.key())),
                        "dominante", dom == null ? "—" : msg.raw("forja.dominante." + dom))));
                lines.add(msg.get("forja.holo_comecar"));
            }
        } else if (work != null) {
            lines.add(msg.get("forja.titulo", Messages.vars("projeto", blueprint.name())));
            lines.add(msg.get("forja.holo_batidas", Messages.vars("feitas", work.done(), "total", work.needed())));
            String heatWord = msg.raw(heat < cfg.heatLow() ? "forja.calor_frio" : heat > cfg.heatHigh() ? "forja.calor_quente" : "forja.calor_ideal");
            lines.add(Messages.parse("<gray>" + msg.raw("forja.holo_calor") + " " + StationPanel.bar(heat, 10) + " " + heatWord));
            lines.add(Messages.parse("<gray>" + msg.raw("forja.holo_qualidade") + " " + StationPanel.bar(work.quality() / 100.0, 10)
                    + " <white>" + Math.round(work.quality()) + "%"));
            if (work.cracks() > 0) {
                lines.add(msg.get("forja.holo_trincas", Messages.vars("trincas", work.cracks(), "maximo", work.maxCracks())));
            }
            if (state == State.TEMPERA) {
                lines.add(msg.get("forja.holo_tempera", Messages.vars("segundos", Math.max(1, (temperLeft + 19) / 20))));
            } else if (feedback != null) {
                lines.add(feedback);
            }
        }
        panel.hologram(lines);
    }
}
