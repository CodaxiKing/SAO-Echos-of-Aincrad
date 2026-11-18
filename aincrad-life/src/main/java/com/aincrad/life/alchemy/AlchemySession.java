package com.aincrad.life.alchemy;

import com.aincrad.core.message.Messages;
import com.aincrad.life.alchemy.AlchemyMath.Purity;
import com.aincrad.life.alchemy.AlchemyRegistry.Formula;
import com.aincrad.life.alchemy.AlchemyRegistry.Infusion;
import com.aincrad.life.alchemy.AlchemyRegistry.Reagent;
import com.aincrad.life.alchemy.AlchemyRegistry.Slot;
import com.aincrad.life.cooking.FireStep;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.station.StationPanel;
import com.aincrad.life.station.StationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Um preparo na mesa de alquimia: frasco, base, reagente principal, secundário e catalisador nos
 * encaixes; depois moer (ritmo), infusão (cada reagente na cor certa do líquido, na ordem da
 * fórmula), destilar (temperatura na faixa; quente demais explode) e engarrafar (no brilho). A
 * soma dá a pureza do produto.
 */
final class AlchemySession {

    private enum State {
        MONTANDO, MOER, INFUSAO, DESTILAR, ENGARRAFAR, FIM
    }

    private static final List<double[]> DEFAULT_SLOTS = List.of(new double[]{-0.9, 1.6, 0}, new double[]{-0.45, 1.85, 0},
            new double[]{0, 1.95, 0}, new double[]{0.45, 1.85, 0}, new double[]{0.9, 1.6, 0});

    private final AlchemyService service;
    private final Messages msg;
    private final ProfessionService professions;
    private final Player player;
    private final StationService.Placed station;
    private final StationPanel panel;
    private final AlchemyService.Settings cfg;
    private final Map<Slot, Integer> slotIndex = new EnumMap<>(Slot.class);
    private final List<Entity> labels = new ArrayList<>();
    private final List<String> cycle = new ArrayList<>(AlchemyRegistry.COLORS);
    private State state = State.MONTANDO;

    // O preparo em andamento.
    private Formula formula;
    private Reagent flask;
    private List<Infusion> steps = List.of();
    private List<ItemStack> used = List.of();
    private double points;
    private boolean perfectBottle;
    private int age;
    private int pause;
    private Runnable afterPause;
    private int remaining;
    private int delay;
    private double grindSum;
    private int grindCount;
    private int stepIndex;
    private int colorTick;
    private FireStep fire;
    private Component feedback;

    AlchemySession(AlchemyService service, Player player, StationService.Placed station, StationPanel panel, AlchemyService.Settings cfg) {
        this.service = service;
        this.msg = service.messages();
        this.professions = service.professions();
        this.player = player;
        this.station = station;
        this.panel = panel;
        this.cfg = cfg;
        Collections.shuffle(cycle);
    }

    Player player() {
        return player;
    }

    StationService.Placed station() {
        return station;
    }

    private double mod(String key) {
        return service.mod(player, key);
    }

    private String slotName(Slot s) {
        return msg.raw("alquimia.encaixe." + s.key());
    }

    private boolean working() {
        return state != State.MONTANDO && state != State.FIM;
    }

    // ------------------------------------------------------------------ encaixes

    void open() {
        List<double[]> at = station.type.slots().size() >= Slot.values().length ? station.type.slots() : DEFAULT_SLOTS;
        for (Slot s : Slot.values()) {
            slotIndex.put(s, panel.addSlot(slotName(s), at.get(s.ordinal()), s.required(), it -> {
                Reagent r = service.identify(it);
                return r != null && r.slot() == s;
            }));
        }
        panel.markPoints(station.type.marks());
        panel.onSlotChange(this::render);
        panel.onPointClick(this::pointClick);
        panel.onClose(returned -> {
            if (working() && player.isOnline()) {
                msg.send(player, "alquimia.abandonou");
            }
            state = State.FIM;
            service.ended(this);
        });
        render();
        msg.send(player, "alquimia.aberta");
        if (panel.simple()) {
            slotMenu();
        }
    }

    private void slotMenu() {
        service.panels().openSlotMenu(player, msg.get("alquimia.titulo"), pl -> start());
    }

    private Reagent in(Slot s) {
        return service.identify(panel.slots().get(slotIndex.get(s)).item());
    }

    private List<String> missing() {
        List<String> out = new ArrayList<>();
        for (Slot s : Slot.values()) {
            if (s.required() && in(s) == null) {
                out.add(slotName(s));
            }
        }
        return out;
    }

    private Formula matched() {
        Reagent base = in(Slot.BASE);
        Reagent principal = in(Slot.PRINCIPAL);
        Reagent secondary = in(Slot.SECUNDARIO);
        return base == null || principal == null || secondary == null ? null
                : service.registry().match(base.id(), principal.id(), secondary.id());
    }

    /** Agachado + clique na mesa: desiste (antes de começar, os reagentes voltam). */
    void cancel() {
        boolean before = state == State.MONTANDO;
        panel.close(true);
        if (before) {
            msg.send(player, "alquimia.cancelou");
        }
    }

    /** Clique na mesa com os encaixes cheios: começa o preparo. */
    void start() {
        if (state != State.MONTANDO || panel.closed()) {
            return;
        }
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            msg.send(player, "alquimia.faltam", Messages.vars("encaixes", String.join(", ", missing)));
            if (panel.simple()) {
                slotMenu();
            }
            return;
        }
        Formula f = matched();
        Learnable l = f == null ? null : service.learnable(f);
        boolean knows = l != null && professions.knows(player, l);
        if (f != null && knows && !professions.canProduce(player, l)) {
            msg.send(player, "alquimia.falta_proficiencia", Messages.vars("nome", f.name(), "producao", f.produce()));
            return;
        }
        if (f == null || !knows) {
            wrongMix();
            return;
        }
        String refusal = service.deliveryRefusal(f);
        if (refusal != null) {
            msg.send(player, refusal, Messages.vars("nome", f.name()));
            return;
        }
        formula = f;
        flask = in(Slot.FRASCO);
        steps = AlchemyRegistry.steps(f, in(Slot.CATALISADOR) != null);
        used = panel.consume();
        panel.lockSlots(true);
        service.stations().setActive(station, true);
        // Moer: marcas em ritmo regular sobre o almofariz.
        state = State.MOER;
        remaining = cfg.grindMarks();
        delay = 20;
        player.playSound(panel.base(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.2f);
        render();
    }

    /** A combinação não é nenhuma fórmula que o alquimista conheça. */
    private void wrongMix() {
        if (!cfg.wrongConsumes()) {
            msg.send(player, "alquimia.nao_combina");
            return;
        }
        panel.consume();
        state = State.FIM;
        Location at = panel.base().add(0, 1.3, 0);
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 20, 0.2, 0.2, 0.2, 0.01);
        at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.7f);
        professions.practice(player, Profession.ALQUIMIA, 0, false);
        msg.send(player, "alquimia.mistura_perdida");
        panel.close(false);
    }

    // ------------------------------------------------------------------ etapas

    private void pauseThen(Runnable next) {
        pause = 14;
        afterPause = next;
        render();
    }

    void tick() {
        if (!working() || panel.closed()) {
            return;
        }
        age++;
        if (pause > 0) {
            if (--pause == 0 && afterPause != null) {
                Runnable r = afterPause;
                afterPause = null;
                r.run();
            }
            return;
        }
        switch (state) {
            case MOER -> {
                if (!panel.markActive() && --delay <= 0) {
                    int count = Math.max(1, panel.markCount());
                    panel.mark(count / 2, cfg.grindRing(), widen(cfg.grindPerfect(), "janela_moer"), widen(cfg.grindGood(), "janela_moer"), this::grindHit);
                }
            }
            case INFUSAO -> {
                colorTick++;
                if (age % 2 == 0) {
                    render();
                }
            }
            case DESTILAR -> {
                fire.tick();
                if (fire.done()) {
                    if (fire.burned() && mod("explosao_perdoada") < 1) {
                        explode();
                        return;
                    }
                    points += AlchemyMath.distill(cfg.rules(), fire.score());
                    feedback = msg.get(fire.score() >= 0.85 ? "alquimia.destilou_otimo" : fire.score() >= 0.5 ? "alquimia.destilou_bom" : "alquimia.destilou_ruim");
                    clearLabels();
                    pauseThen(this::toBottle);
                    return;
                }
                if (age % 2 == 0) {
                    render();
                }
            }
            case ENGARRAFAR -> {
                if (!panel.markActive() && --delay <= 0) {
                    int count = Math.max(1, panel.markCount());
                    panel.mark(count / 2, cfg.bottleRing(), cfg.bottlePerfect(), cfg.bottleGood(), this::bottleHit);
                    render();
                }
            }
            default -> {
            }
        }
    }

    private int widen(int base, String key) {
        return (int) Math.ceil(base * (1 + Math.max(0, mod(key))) - 1e-9);
    }

    private void grindHit(StationPanel.Hit h) {
        if (state != State.MOER) {
            return;
        }
        grindSum += h == StationPanel.Hit.PERFEITA ? 1 : h == StationPanel.Hit.BOA ? 0.6 : 0;
        grindCount++;
        feedback = msg.get("painel.batida_" + h.name().toLowerCase(Locale.ROOT));
        remaining--;
        delay = cfg.grindGap();
        if (remaining <= 0) {
            points = AlchemyMath.grind(cfg.rules(), grindSum / Math.max(1, grindCount));
            pauseThen(this::toInfusion);
        } else {
            render();
        }
    }

    private void toInfusion() {
        state = State.INFUSAO;
        stepIndex = 0;
        colorTick = 0;
        feedback = null;
        List<double[]> marks = station.type.marks();
        if (!panel.simple() && marks.size() >= 3) {
            label(marks.get(0), msg.get("alquimia.rotulo_principal"));
            label(marks.get(1), msg.get("alquimia.rotulo_secundario"));
            if (steps.size() > 2) {
                label(marks.get(2), msg.get("alquimia.rotulo_catalisador"));
            }
        }
        render();
    }

    private String color() {
        return AlchemyMath.colorAt(cycle, colorTick, cfg.colorTicks());
    }

    /** Clique num ponto sem marca: adicionar um reagente (infusão) ou mexer na temperatura (destilar). */
    private void pointClick(int point) {
        if (pause > 0) {
            return;
        }
        if (state == State.DESTILAR && fire != null) {
            boolean cool = !panel.simple() && point == 0 && panel.markCount() > 1;
            fire.add(cool ? -cfg.cool() : cfg.heat());
            player.playSound(panel.base(), cool ? Sound.BLOCK_FIRE_EXTINGUISH : Sound.ITEM_FIRECHARGE_USE, 0.5f, cool ? 1.4f : 1.2f);
            render();
            return;
        }
        if (state != State.INFUSAO || stepIndex >= steps.size()) {
            return;
        }
        Infusion expected = steps.get(stepIndex);
        // Esquerda: principal; centro: secundário; direita: catalisador. No modo simples, a ordem é automática.
        Slot clicked = panel.simple() || panel.markCount() < 3 ? expected.reagent()
                : point == 0 ? Slot.PRINCIPAL : point == 1 ? Slot.SECUNDARIO : Slot.CATALISADOR;
        if (clicked == Slot.CATALISADOR && steps.size() <= 2) {
            return;
        }
        if (clicked != expected.reagent()) {
            ruin();
            return;
        }
        boolean right = color().equals(expected.color());
        points += AlchemyMath.infusion(cfg.rules(), right);
        feedback = msg.get(right ? "alquimia.cor_certa" : "alquimia.cor_errada");
        Location at = panel.base().add(0, 1.3, 0);
        at.getWorld().spawnParticle(right ? Particle.HAPPY_VILLAGER : Particle.SMOKE, at, 10, 0.2, 0.2, 0.2, 0.01);
        player.playSound(at, right ? Sound.BLOCK_BREWING_STAND_BREW : Sound.BLOCK_LAVA_EXTINGUISH, 0.7f, right ? 1.4f : 0.8f);
        stepIndex++;
        if (stepIndex >= steps.size()) {
            clearLabels();
            pauseThen(this::toDistill);
        } else {
            render();
        }
    }

    private void toDistill() {
        state = State.DESTILAR;
        feedback = null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double width = Math.min(0.9, cfg.band() * (1 + mod("faixa_temperatura")));
        double center = Math.max(width / 2 + 0.1, Math.min(1 - width / 2, 0.45 + rnd.nextDouble() * 0.2));
        int ticks = (int) Math.max(40, cfg.distillTicks() * Math.max(0.3, 1 + mod("tempo_preparo")));
        fire = new FireStep(center - width / 2, center + width / 2, ticks, cfg.decay(), cfg.explodeTicks());
        List<double[]> marks = station.type.marks();
        if (!panel.simple() && marks.size() >= 2) {
            label(marks.get(0), msg.get("alquimia.rotulo_esfriar"));
            label(marks.get(marks.size() - 1), msg.get("alquimia.rotulo_aquecer"));
        }
        render();
    }

    private void toBottle() {
        state = State.ENGARRAFAR;
        feedback = null;
        delay = ThreadLocalRandom.current().nextInt(20, 61);
        render();
    }

    private void bottleHit(StationPanel.Hit h) {
        if (state != State.ENGARRAFAR) {
            return;
        }
        perfectBottle = h == StationPanel.Hit.PERFEITA;
        AlchemyMath.Rules r = cfg.rules();
        points += h == StationPanel.Hit.PERFEITA ? r.bottlePerfect() : h == StationPanel.Hit.BOA ? r.bottleGood() : r.bottleBad();
        finish();
    }

    // ------------------------------------------------------------------ rótulos

    private void label(double[] point, Component text) {
        Location at = panel.at(point).add(0, 0.25, 0);
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.setPersistent(false);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            e.text(text);
        });
        labels.add(d);
        panel.track(d);
    }

    private void clearLabels() {
        labels.forEach(Entity::remove);
        labels.clear();
    }

    // ------------------------------------------------------------------ fim

    /** Reagente fora de ordem: a mistura estragou. */
    private void ruin() {
        state = State.FIM;
        Location at = panel.base().add(0, 1.3, 0);
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 25, 0.2, 0.2, 0.2, 0.01);
        at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
        professions.practice(player, Profession.ALQUIMIA, formula.difficulty(), false);
        msg.send(player, "alquimia.ordem_errada", Messages.vars("nome", formula.name()));
        panel.close(false);
    }

    /** Quente demais por tempo demais: a mistura explode, fere quem está perto e os reagentes se perdem. */
    private void explode() {
        state = State.FIM;
        Location at = panel.base().add(0, 1.2, 0);
        at.getWorld().spawnParticle(Particle.EXPLOSION, at, 3, 0.3, 0.3, 0.3, 0);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        if (cfg.explodeDamage() > 0) {
            for (Player near : at.getNearbyPlayers(cfg.explodeRadius())) {
                near.damage(cfg.explodeDamage());
            }
        }
        professions.practice(player, Profession.ALQUIMIA, formula.difficulty(), false);
        msg.send(player, "alquimia.explodiu", Messages.vars("nome", formula.name()));
        panel.close(false);
    }

    private void finish() {
        state = State.FIM;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double proficiency = professions.proficiency(player, Profession.ALQUIMIA);
        double total = Math.max(0, Math.min(100, points + AlchemyMath.proficiency(cfg.rules(), proficiency, formula.difficulty())
                + 100 * mod("pureza")));
        Purity purity = AlchemyMath.purity(cfg.rules(), total, perfectBottle, flask.cap());
        int doses = AlchemyMath.doses(flask.doses(), purity, (int) mod("doses")) + (rnd.nextDouble() < mod("dose_extra") ? 1 : 0);
        int delivered = service.deliver(player, formula, purity, doses);
        if (!used.isEmpty() && rnd.nextDouble() < mod("reagente_poupado")) {
            AlchemyService.give(player, used.get(rnd.nextInt(used.size())));
            msg.send(player, "alquimia.poupou");
        }
        professions.practice(player, Profession.ALQUIMIA, formula.difficulty(), true);
        Location at = panel.base().add(0, 1.3, 0);
        at.getWorld().spawnParticle(purity == Purity.PERFEITA ? Particle.TOTEM_OF_UNDYING : Particle.HAPPY_VILLAGER, at, 24, 0.3, 0.3, 0.3, 0.1);
        at.getWorld().playSound(at, Sound.ITEM_BOTTLE_FILL, 1f, 1f);
        at.getWorld().playSound(at, purity == Purity.PERFEITA ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
        msg.send(player, "alquimia.pronto", Messages.vars("nome", formula.name(), "qtd", delivered,
                "pureza", msg.get("alquimia.pureza." + purity.key()), "pontos", Math.round(total)));
        panel.close(false);
    }

    // ------------------------------------------------------------------ holograma

    private void render() {
        if (panel.closed()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (state == State.MONTANDO) {
            lines.add(msg.get("alquimia.titulo"));
            List<String> missing = missing();
            if (!missing.isEmpty()) {
                lines.add(msg.get("alquimia.holo_faltam", Messages.vars("encaixes", String.join(", ", missing))));
            } else {
                Formula f = matched();
                Learnable l = f == null ? null : service.learnable(f);
                Reagent fl = in(Slot.FRASCO);
                if (f != null && l != null && professions.knows(player, l)) {
                    lines.add(msg.get("alquimia.holo_formula", Messages.vars("nome", f.name())));
                    lines.add(msg.get("alquimia.holo_frasco", Messages.vars("doses", fl == null ? 1 : fl.doses(),
                            "teto", msg.get("alquimia.pureza." + (fl == null ? Purity.PERFEITA : fl.cap()).key()))));
                    lines.add(professions.canProduce(player, l) ? msg.get("alquimia.holo_comecar")
                            : msg.get("alquimia.holo_falta_proficiencia", Messages.vars("producao", f.produce())));
                } else {
                    lines.add(msg.get("alquimia.holo_desconhecida"));
                    lines.add(msg.get("alquimia.holo_tentar"));
                }
            }
        } else if (formula != null) {
            lines.add(msg.get("alquimia.holo_formula", Messages.vars("nome", formula.name())));
            int n = state == State.MOER ? 1 : state == State.INFUSAO ? 2 : state == State.DESTILAR ? 3 : 4;
            lines.add(msg.get("alquimia.holo_etapa", Messages.vars("n", n, "etapa", msg.raw("alquimia.etapa." + state.name().toLowerCase(Locale.ROOT)))));
            switch (state) {
                case MOER -> lines.add(msg.get("alquimia.dica.moer", Messages.vars("faltam", Math.max(0, remaining))));
                case INFUSAO -> {
                    lines.add(msg.get("alquimia.holo_liquido", Messages.vars("cor", msg.get("alquimia.cor." + color()))));
                    lines.add(msg.get(panel.simple() ? "alquimia.dica.infusao_simples" : "alquimia.dica.infusao",
                            Messages.vars("feitos", stepIndex, "total", steps.size())));
                }
                case DESTILAR -> {
                    lines.add(msg.get(panel.simple() ? "alquimia.dica.destilar_simples" : "alquimia.dica.destilar"));
                    lines.add(Messages.parse(temperatureBar()));
                    lines.add(Messages.parse("<gray>" + msg.raw("alquimia.holo_tempo") + " " + StationPanel.bar(fire.progress(), 16)));
                }
                case ENGARRAFAR -> lines.add(msg.get("alquimia.dica.engarrafar"));
                default -> {
                }
            }
            lines.add(Messages.parse("<gray>" + msg.raw("alquimia.holo_pureza") + " " + StationPanel.bar(Math.max(0, Math.min(100, points)) / 100.0, 12)));
            if (feedback != null) {
                lines.add(feedback);
            }
        }
        panel.hologram(lines);
    }

    /** A temperatura: a faixa certa em verde e o ponteiro onde ela está. */
    private String temperatureBar() {
        int width = 24;
        int pointer = (int) Math.min(width - 1, Math.floor(fire.heat() * width));
        String pointerColor = fire.inBand() ? "<white>" : fire.heat() > fire.high() ? "<red>" : "<aqua>";
        StringBuilder sb = new StringBuilder("<dark_gray>[");
        for (int i = 0; i < width; i++) {
            double x = (i + 0.5) / width;
            if (i == pointer) {
                sb.append(pointerColor).append('█');
            } else if (x >= fire.low() && x <= fire.high()) {
                sb.append("<green>=");
            } else {
                sb.append("<dark_gray>-");
            }
        }
        return sb.append("<dark_gray>]").toString();
    }
}
