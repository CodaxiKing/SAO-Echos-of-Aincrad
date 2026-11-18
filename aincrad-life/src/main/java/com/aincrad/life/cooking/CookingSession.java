package com.aincrad.life.cooking;

import com.aincrad.core.message.Messages;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Um preparo na bancada de cozinha, sobre o {@link StationPanel}: o cozinheiro põe os
 * ingredientes nos cinco encaixes, clica na bancada e passa pelas etapas da receita (cortar,
 * fogo, mexer, temperar, empratar). A soma dos acertos decide a qualidade do prato.
 */
final class CookingSession {

    private enum State {
        MONTANDO, COZINHANDO, FIM
    }

    /** Pontos de batida usados quando a bancada não define os dela. */
    private static final List<double[]> DEFAULT_SLOTS = List.of(new double[]{-0.9, 1.6, 0}, new double[]{-0.45, 1.85, 0},
            new double[]{0, 1.95, 0}, new double[]{0.45, 1.85, 0}, new double[]{0.9, 1.6, 0});

    private final CookingService service;
    private final Messages msg;
    private final ProfessionService professions;
    private final Player player;
    private final StationService.Placed station;
    private final StationPanel panel;
    private final CookingService.Settings cfg;
    private final Map<SlotKind, Integer> slotIndex = new EnumMap<>(SlotKind.class);
    private final List<Entity> fireLabels = new ArrayList<>();
    private State state = State.MONTANDO;

    // O preparo em andamento.
    private Recipe recipe;
    private boolean discovery;
    private boolean extraSpecial;
    private List<ItemStack> used = List.of();
    private int stepIndex = -1;
    private Step step;
    private int remaining;
    private int delay;
    private int pause;
    private int sequence;
    private int direction = 1;
    private int age;
    private FireStep fire;
    private Component feedback;
    private double scoreSum;
    private double scoreWeight;
    private boolean platingPerfect;
    private boolean burned;

    CookingSession(CookingService service, Player player, StationService.Placed station, StationPanel panel, CookingService.Settings cfg) {
        this.service = service;
        this.msg = service.messages();
        this.professions = service.professions();
        this.player = player;
        this.station = station;
        this.panel = panel;
        this.cfg = cfg;
    }

    Player player() {
        return player;
    }

    StationService.Placed station() {
        return station;
    }

    private double mod(String key) {
        return professions.modifier(player, Profession.CULINARIA, key);
    }

    private String slotName(SlotKind k) {
        return msg.raw("cozinha.encaixe." + k.key());
    }

    // ------------------------------------------------------------------ montar o prato

    /** Monta os encaixes e o holograma. */
    void open() {
        List<double[]> points = station.type.slots().size() >= SlotKind.values().length ? station.type.slots() : DEFAULT_SLOTS;
        for (SlotKind k : SlotKind.values()) {
            slotIndex.put(k, panel.addSlot(slotName(k), points.get(k.ordinal()), k.required(), it -> {
                Ingredient i = service.identify(it);
                return i != null && i.slot() == k;
            }));
        }
        panel.markPoints(station.type.marks());
        panel.onSlotChange(this::render);
        panel.onPointClick(this::pointClick);
        panel.onClose(returned -> {
            if (state == State.COZINHANDO && player.isOnline()) {
                msg.send(player, "cozinha.abandonou");
            }
            state = State.FIM;
            service.ended(this);
        });
        render();
        msg.send(player, "cozinha.aberta");
        if (panel.simple()) {
            slotMenu();
        }
    }

    private void slotMenu() {
        service.panels().openSlotMenu(player, msg.get("cozinha.titulo"), pl -> start());
    }

    /** O que está em cada encaixe agora. */
    private Map<SlotKind, String> combo() {
        Map<SlotKind, String> out = new EnumMap<>(SlotKind.class);
        for (SlotKind k : SlotKind.values()) {
            Ingredient i = service.identify(panel.slots().get(slotIndex.get(k)).item());
            if (i != null) {
                out.put(k, i.id());
            }
        }
        return out;
    }

    private List<String> missing(Map<SlotKind, String> combo) {
        List<String> out = new ArrayList<>();
        for (SlotKind k : SlotKind.values()) {
            if (k.required() && !combo.containsKey(k)) {
                out.add(slotName(k));
            }
        }
        return out;
    }

    /** Sneak + clique na bancada: desiste (antes de começar, os ingredientes voltam). */
    void cancel() {
        boolean before = state == State.MONTANDO;
        panel.close(true);
        if (before) {
            msg.send(player, "cozinha.cancelou");
        }
    }

    /** Clique na bancada com o prato montado: começa a cozinhar. */
    void start() {
        if (state != State.MONTANDO || panel.closed()) {
            return;
        }
        Map<SlotKind, String> combo = combo();
        List<String> missing = missing(combo);
        if (!missing.isEmpty()) {
            msg.send(player, "cozinha.faltam", Messages.vars("encaixes", String.join(", ", missing)));
            if (panel.simple()) {
                slotMenu();
            }
            return;
        }
        Recipe r = service.registry().match(combo);
        Learnable l = r == null ? null : service.learnable(r);
        boolean knows = l != null && professions.knows(player, l);
        double proficiency = professions.proficiency(player, Profession.CULINARIA);
        if (r != null && knows && proficiency < r.produceMin()) {
            msg.send(player, "cozinha.falta_proficiencia", Messages.vars("nome", r.name(), "producao", r.produceMin()));
            return;
        }
        // Receita secreta: quem acerta a combinação (e já teria condição de fazer) descobre cozinhando.
        boolean found = r != null && l != null && !knows && r.secret() && proficiency >= r.produceMin()
                && professions.canLearn(player, l) == Learnable.Check.OK;
        if (r == null || (!knows && !found)) {
            wrongMix();
            return;
        }
        recipe = r;
        discovery = found;
        extraSpecial = combo.containsKey(SlotKind.ESPECIAL) && !r.slots().containsKey(SlotKind.ESPECIAL);
        used = panel.consume();
        panel.lockSlots(true);
        state = State.COZINHANDO;
        service.stations().setActive(station, true);
        player.playSound(panel.base(), Sound.BLOCK_CAMPFIRE_CRACKLE, 1f, 1f);
        nextStep();
    }

    /** A combinação não é nenhuma receita que o cozinheiro saiba fazer. */
    private void wrongMix() {
        if (!cfg.wrongConsumes()) {
            msg.send(player, "cozinha.nao_combina");
            return;
        }
        panel.consume();
        state = State.FIM;
        CookingService.give(player, service.dish(null, Quality.QUEIMADO, player.getName(), 0, 0, 1));
        professions.practice(player, Profession.CULINARIA, 0, false);
        player.playSound(panel.base(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.8f);
        msg.send(player, "cozinha.gororoba_feita");
        panel.close(false);
    }

    // ------------------------------------------------------------------ etapas

    private void nextStep() {
        clearFireLabels();
        stepIndex++;
        if (stepIndex >= recipe.steps().size()) {
            complete();
            return;
        }
        step = recipe.steps().get(stepIndex);
        fire = null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        switch (step) {
            case CORTAR -> {
                remaining = cfg.cutMarks();
                delay = 20;
            }
            case MEXER -> {
                remaining = cfg.stirMarks();
                delay = 20;
                direction = rnd.nextBoolean() ? 1 : -1;
                sequence = direction > 0 ? 0 : Math.max(0, panel.markCount() - 1);
            }
            case TEMPERAR -> {
                remaining = 1;
                delay = rnd.nextInt(Math.min(cfg.seasonMin(), cfg.seasonMax()), Math.max(cfg.seasonMin(), cfg.seasonMax()) + 1);
            }
            case EMPRATAR -> {
                remaining = 1;
                delay = 25;
            }
            case FOGO -> {
                double width = Math.min(0.9, cfg.fireBand() * (1 + mod("faixa_fogo") + mod("bonus_fogo")));
                double center = Math.max(width / 2 + 0.1, Math.min(1 - width / 2, 0.45 + rnd.nextDouble() * 0.2));
                int ticks = (int) Math.max(40, cfg.fireTicks() * Math.max(0.3, 1 + mod("tempo_preparo")));
                fire = new FireStep(center - width / 2, center + width / 2, ticks, cfg.fireDecay(), cfg.fireBurn());
                fireLabels();
            }
        }
        render();
    }

    /** Na etapa do fogo: os rótulos "Abafar" e "Lenha" sobre os pontos de clique. */
    private void fireLabels() {
        List<double[]> marks = station.type.marks();
        if (panel.simple() || marks.size() < 2) {
            return;
        }
        fireLabel(marks.get(0), "cozinha.fogo_abafar");
        fireLabel(marks.get(marks.size() - 1), "cozinha.fogo_lenha");
    }

    private void fireLabel(double[] point, String key) {
        Location at = panel.at(point).add(0, 0.25, 0);
        TextDisplay label = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            d.text(msg.get(key));
        });
        fireLabels.add(label);
        panel.track(label);
    }

    private void clearFireLabels() {
        fireLabels.forEach(Entity::remove);
        fireLabels.clear();
    }

    /** Folga de uma batida, alargada pelos nós da árvore (qualquer bônus dá pelo menos 1 tick). */
    private int widen(int base, String key) {
        return (int) Math.ceil(base * (1 + Math.max(0, mod(key))) - 1e-9);
    }

    private void addScore(double value, double weight) {
        scoreSum += value * weight;
        scoreWeight += weight;
    }

    /** Roda a cada tick. */
    void tick() {
        if (state != State.COZINHANDO || panel.closed()) {
            return;
        }
        age++;
        if (pause > 0) {
            if (--pause == 0) {
                nextStep();
            }
            return;
        }
        if (step == Step.FOGO) {
            fire.tick();
            if (fire.done()) {
                if (fire.burned() && mod("nunca_queima") < 1) {
                    burned = true;
                    complete();
                    return;
                }
                addScore(fire.score(), 3);
                feedback = msg.get(fire.score() >= 0.85 ? "cozinha.fogo_otimo" : fire.score() >= 0.5 ? "cozinha.fogo_bom" : "cozinha.fogo_ruim");
                pause = 14;
                render();
                return;
            }
            if (age % 2 == 0) {
                render();
            }
            return;
        }
        if (panel.markActive() || --delay > 0) {
            return;
        }
        spawnMark();
    }

    private void spawnMark() {
        int count = Math.max(1, panel.markCount());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        switch (step) {
            case CORTAR -> panel.mark(rnd.nextInt(count), cfg.cutRing(), widen(cfg.cutPerfect(), "janela_corte"),
                    widen(cfg.cutGood(), "janela_corte"), this::hit);
            case MEXER -> {
                panel.mark(Math.floorMod(sequence, count), cfg.stirRing(), widen(cfg.stirPerfect(), "janela_mexer"),
                        widen(cfg.stirGood(), "janela_mexer"), this::hit);
                sequence += direction;
            }
            case TEMPERAR -> panel.mark(rnd.nextInt(count), cfg.seasonLit(), widen(cfg.seasonPerfect(), "janela_tempero"),
                    widen(cfg.seasonGood(), "janela_tempero"), this::hit);
            default -> panel.mark(count / 2, cfg.plateRing(), cfg.platePerfect(), cfg.plateGood(), this::hit);
        }
        render();
    }

    private void hit(StationPanel.Hit h) {
        if (state != State.COZINHANDO) {
            return;
        }
        addScore(h == StationPanel.Hit.PERFEITA ? 1 : h == StationPanel.Hit.BOA ? 0.6 : 0, 1);
        if (step == Step.EMPRATAR) {
            platingPerfect = h == StationPanel.Hit.PERFEITA;
        }
        feedback = msg.get("painel.batida_" + h.name().toLowerCase(Locale.ROOT));
        remaining--;
        delay = step == Step.MEXER ? 4 : 8;
        if (remaining <= 0) {
            pause = 12;
        }
        render();
    }

    /** Clique num ponto sem marca: na etapa do fogo, lenha (sobe) ou abafar (desce). */
    private void pointClick(int point) {
        if (state != State.COZINHANDO || step != Step.FOGO || fire == null || pause > 0) {
            return;
        }
        boolean damp = !panel.simple() && point == 0 && panel.markCount() > 1;
        fire.add(damp ? -cfg.fireDamp() : cfg.fireWood());
        player.playSound(panel.base(), damp ? Sound.BLOCK_FIRE_EXTINGUISH : Sound.ITEM_FIRECHARGE_USE, 0.5f, damp ? 1.4f : 1.2f);
        render();
    }

    // ------------------------------------------------------------------ resultado

    private void complete() {
        state = State.FIM;
        clearFireLabels();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double proficiency = professions.proficiency(player, Profession.CULINARIA);
        double score = scoreWeight <= 0 ? 0 : scoreSum / scoreWeight;
        double chance = Quality.excellentChance(cfg.rules(), proficiency, recipe.difficulty(), platingPerfect,
                mod("chance_excelente") + (extraSpecial ? cfg.specialBonus() : 0)
                        + (professions.useCatalyst(player) ? service.treeValue("catalisador_chance", 0.15) : 0));
        Quality q = Quality.decide(cfg.rules(), score, burned, chance, rnd.nextDouble());

        int portions = recipe.portions();
        if (recipe.pot()) {
            portions += (int) mod("porcoes");
            if (mod("porcoes_dobro") >= 1) {
                portions *= 2;
            }
            portions = Math.min(cfg.maxPortions(), portions);
        }
        DishPerks perks = DishPerks.of(recipe, q, this::mod, service.treeValue("segundo_buff_vida", 4), service.treeValue("segundo_buff_forca", 5));
        CookingService.give(player, service.dish(recipe, q, player.getName(), mod("duracao_buff"), DishPerks.strength(recipe, this::mod), portions, perks));
        if (!used.isEmpty() && rnd.nextDouble() < mod("ingrediente_poupado")) {
            ItemStack spared = used.get(rnd.nextInt(used.size()));
            CookingService.give(player, spared);
            msg.send(player, "cozinha.poupou");
        }
        professions.practice(player, Profession.CULINARIA, recipe.difficulty(), q != Quality.QUEIMADO);

        Location at = panel.base().add(0, 1.3, 0);
        var vars = Messages.vars("prato", recipe.name(), "qualidade", msg.get("cozinha.qualidade." + q.key()), "porcoes", portions);
        if (q == Quality.QUEIMADO) {
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 20, 0.2, 0.2, 0.2, 0.01);
            at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.7f);
            msg.send(player, burned ? "cozinha.queimou_no_fogo" : "cozinha.queimou", vars);
        } else {
            at.getWorld().spawnParticle(q == Quality.EXCELENTE ? Particle.TOTEM_OF_UNDYING : Particle.HAPPY_VILLAGER, at, 24, 0.3, 0.3, 0.3, 0.1);
            at.getWorld().playSound(at, q == Quality.EXCELENTE ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.1f);
            msg.send(player, portions > 1 ? "cozinha.pronto_porcoes" : "cozinha.pronto", vars);
            Learnable l = service.learnable(recipe);
            if (discovery && l != null) {
                professions.teach(player, l);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                msg.send(player, "cozinha.descobriu", vars);
            }
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
            lines.add(msg.get("cozinha.titulo"));
            Map<SlotKind, String> combo = combo();
            List<String> missing = missing(combo);
            if (combo.isEmpty()) {
                lines.add(msg.get("cozinha.holo_vazio"));
            } else if (!missing.isEmpty()) {
                lines.add(msg.get("cozinha.holo_faltam", Messages.vars("encaixes", String.join(", ", missing))));
            } else {
                Recipe r = service.registry().match(combo);
                Learnable l = r == null ? null : service.learnable(r);
                if (r != null && l != null && professions.knows(player, l)) {
                    lines.add(msg.get("cozinha.holo_receita", Messages.vars("nome", r.name())));
                    lines.add(msg.get("cozinha.holo_etapas", Messages.vars("etapas", stepNames(r))));
                    lines.add(professions.canProduce(player, l) ? msg.get("cozinha.holo_comecar")
                            : msg.get("cozinha.holo_falta_proficiencia", Messages.vars("producao", r.produceMin())));
                } else {
                    lines.add(msg.get("cozinha.holo_desconhecido"));
                    lines.add(msg.get("cozinha.holo_tentar"));
                }
            }
        } else if (state == State.COZINHANDO && step != null) {
            lines.add(discovery ? msg.get("cozinha.holo_desconhecido") : msg.get("cozinha.holo_receita", Messages.vars("nome", recipe.name())));
            lines.add(msg.get("cozinha.holo_etapa", Messages.vars("n", stepIndex + 1, "total", recipe.steps().size(),
                    "etapa", msg.raw("cozinha.etapa." + step.key()))));
            if (step == Step.FOGO && fire != null) {
                lines.add(msg.get(panel.simple() ? "cozinha.dica.fogo_simples" : "cozinha.dica.fogo"));
                lines.add(Messages.parse(fireBar()));
                lines.add(Messages.parse("<gray>" + msg.raw("cozinha.holo_tempo") + " " + StationPanel.bar(fire.progress(), 16)));
            } else {
                String tip = step == Step.MEXER ? "cozinha.dica.mexer_" + (direction > 0 ? "direita" : "esquerda") : "cozinha.dica." + step.key();
                lines.add(msg.get(tip));
                if (step == Step.CORTAR || step == Step.MEXER) {
                    lines.add(msg.get("cozinha.holo_marcas", Messages.vars("faltam", Math.max(0, remaining))));
                }
            }
            if (feedback != null) {
                lines.add(feedback);
            }
        }
        panel.hologram(lines);
    }

    private String stepNames(Recipe r) {
        List<String> out = new ArrayList<>();
        r.steps().forEach(s -> out.add(msg.raw("cozinha.etapa." + s.key())));
        return String.join(" · ", out);
    }

    /** A chama: a faixa certa em verde e o ponteiro onde o fogo está. */
    private String fireBar() {
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
