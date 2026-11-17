package com.aincrad.life.station;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Painel flutuante preso a uma bancada: encaixes clicáveis onde o material fica visível, um
 * holograma de progresso e marcas de batida (um anel que se fecha sobre um ponto; o artesão mira
 * no ponto e clica quando o anel fecha). Todos por perto veem o trabalho acontecer.
 *
 * <p>É a peça comum das sessões de forja, cozinha, pesca e alquimia. No modo simples (Bedrock, ou
 * quando a sessão pedir), a batida conta só o tempo, sem a mira, e os encaixes podem ser
 * preenchidos por um menu.
 */
public final class StationPanel {

    /** Resultado de uma batida. */
    public enum Hit {
        PERFEITA, BOA, RUIM
    }

    /** Um encaixe de material. */
    public static final class Slot {
        final String label;
        final boolean required;
        final Predicate<ItemStack> accepts;
        final Location at;
        ItemStack item;
        ItemDisplay display;
        TextDisplay text;
        Interaction click;

        Slot(String label, boolean required, Predicate<ItemStack> accepts, Location at) {
            this.label = label;
            this.required = required;
            this.accepts = accepts;
            this.at = at;
        }

        public ItemStack item() {
            return item;
        }

        public String label() {
            return label;
        }

        public boolean required() {
            return required;
        }
    }

    private final PanelManager manager;
    private final Player owner;
    private final Location base;
    private final float yaw;
    private final List<Slot> slots = new ArrayList<>();
    private final List<Location> markPoints = new ArrayList<>();
    private final List<Interaction> markClicks = new ArrayList<>();
    private final List<Entity> extras = new ArrayList<>();
    private TextDisplay hologram;
    private Runnable onSlotChange = () -> { };
    private Consumer<Boolean> onClose = returned -> { };
    private boolean simple;
    private boolean closed;
    private boolean slotsLocked;

    // A marca em andamento.
    private TextDisplay ring;
    private TextDisplay target;
    private int markPoint = -1;
    private long markCloses;
    private int perfectTicks;
    private int goodTicks;
    private Consumer<Hit> markResult;
    private IntConsumer pointClick = point -> { };

    StationPanel(PanelManager manager, Player owner, Location base, float yaw, double[] hologramAt, boolean simple) {
        this.manager = manager;
        this.owner = owner;
        this.base = base;
        this.yaw = yaw;
        this.simple = simple;
        Location h = at(hologramAt);
        this.hologram = h.getWorld().spawn(h, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setLineWidth(300);
            d.setBackgroundColor(Color.fromARGB(120, 10, 14, 18));
        });
    }

    public Player owner() {
        return owner;
    }

    public boolean simple() {
        return simple;
    }

    public boolean closed() {
        return closed;
    }

    public Location base() {
        return base.clone();
    }

    /** Um ponto de ancoragem (em blocos a partir do centro da base, frente no +Z) no mundo. */
    public Location at(double[] point) {
        double[] r = StationType.rotate(point, yaw);
        return base.clone().add(r[0], r[1], r[2]);
    }

    // ------------------------------------------------------------------ encaixes

    /**
     * Cria um encaixe. Clicar com o material na mão coloca uma unidade; clicar de mão vazia num
     * encaixe cheio devolve.
     *
     * @return o índice do encaixe
     */
    public int addSlot(String label, double[] point, boolean required, Predicate<ItemStack> accepts) {
        Location at = at(point);
        Slot s = new Slot(label, required, accepts, at);
        s.display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setTransformation(scaled(0.35f));
            d.setItemStack(placeholder(required));
        });
        s.text = at.getWorld().spawn(at.clone().add(0, 0.28, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setTransformation(scaled(0.45f));
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.text(Component.text(label));
        });
        int index = slots.size();
        s.click = at.getWorld().spawn(at.clone().add(0, -0.2, 0), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(0.45f);
            i.setInteractionHeight(0.45f);
            i.setResponsive(true);
        });
        manager.bind(s.click, this, PanelManager.Kind.SLOT, index);
        slots.add(s);
        return index;
    }

    private static ItemStack placeholder(boolean required) {
        return new ItemStack(required ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
    }

    private static Transformation scaled(float s) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    public List<Slot> slots() {
        return slots;
    }

    /** Todos os encaixes obrigatórios estão cheios? */
    public boolean ready() {
        for (Slot s : slots) {
            if (s.required && s.item == null) {
                return false;
            }
        }
        return true;
    }

    /** Trava os encaixes (o trabalho começou: nada mais entra nem sai). */
    public void lockSlots(boolean locked) {
        this.slotsLocked = locked;
    }

    /** Chamado quando um encaixe enche ou esvazia. */
    public void onSlotChange(Runnable r) {
        this.onSlotChange = r;
    }

    /** O dono clicou num encaixe (ou escolheu no menu, no modo simples). */
    void clickSlot(int index) {
        if (closed || slotsLocked || index < 0 || index >= slots.size()) {
            return;
        }
        Slot s = slots.get(index);
        ItemStack hand = owner.getInventory().getItemInMainHand();
        if (s.item != null) {
            // Devolve o que está no encaixe.
            give(s.item);
            s.item = null;
            s.display.setItemStack(placeholder(s.required));
            owner.playSound(s.at, Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.8f);
            onSlotChange.run();
            return;
        }
        if (hand.getType().isAir() || !s.accepts.test(hand)) {
            owner.playSound(s.at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return;
        }
        ItemStack one = hand.clone();
        one.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);
        s.item = one;
        s.display.setItemStack(one);
        owner.playSound(s.at, Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 0.8f, 1.2f);
        onSlotChange.run();
    }

    /** Tira os materiais dos encaixes sem devolver (foram consumidos na produção). */
    public List<ItemStack> consume() {
        List<ItemStack> out = new ArrayList<>();
        for (Slot s : slots) {
            if (s.item != null) {
                out.add(s.item);
                s.item = null;
                s.display.setItemStack(placeholder(s.required));
            }
        }
        return out;
    }

    private void give(ItemStack it) {
        if (owner.isOnline()) {
            owner.getInventory().addItem(it).values().forEach(rest -> owner.getWorld().dropItemNaturally(owner.getLocation(), rest));
        } else {
            base.getWorld().dropItemNaturally(base.clone().add(0, 1, 0), it);
        }
    }

    // ------------------------------------------------------------------ holograma

    /** Troca as linhas do holograma de progresso. */
    public void hologram(List<Component> lines) {
        if (!closed) {
            hologram.text(Component.join(JoinConfiguration.newlines(), lines));
        }
    }

    /** Barra de texto: "[#######---]" com as cores do MiniMessage. */
    public static String bar(double fraction, int width) {
        int full = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "<dark_gray>[<green>" + "#".repeat(full) + "<gray>" + "-".repeat(width - full) + "<dark_gray>]";
    }

    // ------------------------------------------------------------------ marcas de batida

    /** Define os pontos onde as marcas podem aparecer (esquerda, centro, direita...). */
    public void markPoints(List<double[]> points) {
        markClicks.forEach(Entity::remove);
        markClicks.clear();
        markPoints.clear();
        for (int i = 0; i < points.size(); i++) {
            Location at = at(points.get(i));
            markPoints.add(at);
            Interaction click = at.getWorld().spawn(at.clone().add(0, -0.15, 0), Interaction.class, e -> {
                e.setPersistent(false);
                e.setInteractionWidth(0.3f);
                e.setInteractionHeight(0.3f);
                e.setResponsive(true);
            });
            manager.bind(click, this, PanelManager.Kind.MARK, i);
            markClicks.add(click);
        }
    }

    public int markCount() {
        return markPoints.size();
    }

    public boolean markActive() {
        return markResult != null;
    }

    /**
     * Cliques num ponto de batida quando não há marca em andamento (ex.: lenha e abafar, na etapa
     * do fogo). Recebe o índice do ponto, ou -1 no modo simples (clique sem mira).
     */
    public void onPointClick(IntConsumer handler) {
        this.pointClick = handler == null ? point -> { } : handler;
    }

    /** Tira a marca em andamento, sem resultado. */
    public void cancelMark() {
        clearMark();
    }

    /**
     * Mostra uma marca: um ponto brilhante e um anel que se fecha sobre ele em {@code ticks}. O
     * artesão clica no ponto quando o anel fecha.
     *
     * @param perfect folga (em ticks, para mais ou para menos) da batida perfeita
     * @param good    folga da batida boa; fora dela, ou no ponto errado, é ruim
     */
    public void mark(int point, int ticks, int perfect, int good, Consumer<Hit> result) {
        if (closed || markPoints.isEmpty()) {
            return;
        }
        clearMark();
        markPoint = Math.floorMod(point, markPoints.size());
        markCloses = manager.now() + ticks;
        perfectTicks = Math.max(0, perfect);
        goodTicks = Math.max(perfectTicks, good);
        markResult = result;
        Location at = markPoints.get(markPoint);
        target = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTransformation(scaled(0.7f));
            d.text(Component.text("●", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        });
        ring = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTransformation(scaled(3.2f));
            d.text(Component.text("○", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        });
        // Dois ticks depois (o cliente precisa conhecer a entidade antes de interpolar): fecha o anel.
        TextDisplay r = ring;
        manager.later(2, () -> {
            if (r.isValid()) {
                r.setInterpolationDelay(0);
                r.setInterpolationDuration(Math.max(1, ticks - 2));
                r.setTransformation(scaled(0.7f));
            }
        });
    }

    private void clearMark() {
        if (ring != null) {
            ring.remove();
            ring = null;
        }
        if (target != null) {
            target.remove();
            target = null;
        }
        markResult = null;
        markPoint = -1;
    }

    /** O quanto um clique no tick {@code now} acertou uma marca que fecha em {@code closes}. */
    static Hit judge(long now, long closes, int perfect, int good, boolean rightPoint) {
        if (!rightPoint) {
            return Hit.RUIM;
        }
        long off = Math.abs(now - closes);
        return off <= perfect ? Hit.PERFEITA : off <= good ? Hit.BOA : Hit.RUIM;
    }

    /** O dono clicou num ponto de batida ({@code point} -1 = clique sem mira, do modo simples). */
    void clickMark(int point) {
        if (closed) {
            return;
        }
        if (markResult == null) {
            // Sem marca em andamento: clique livre (ex.: pôr lenha na etapa do fogo).
            pointClick.accept(point);
            return;
        }
        boolean right = simple || point == markPoint;
        finishMark(judge(manager.now(), markCloses, perfectTicks, goodTicks, right));
    }

    private void finishMark(Hit hit) {
        Consumer<Hit> cb = markResult;
        Location at = markPoint >= 0 ? markPoints.get(markPoint) : base;
        clearMark();
        switch (hit) {
            case PERFEITA -> {
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 12, 0.1, 0.1, 0.1, 0.05);
                at.getWorld().playSound(at, Sound.BLOCK_ANVIL_USE, 0.7f, 1.8f);
            }
            case BOA -> at.getWorld().playSound(at, Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
            case RUIM -> at.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, 0.4f, 0.6f);
        }
        if (cb != null) {
            cb.accept(hit);
        }
    }

    /** Chamado a cada tick pelo gerenciador: marca que passou do tempo conta como ruim. */
    void tick() {
        if (!closed && markResult != null && manager.now() > markCloses + goodTicks) {
            finishMark(Hit.RUIM);
        }
    }

    // ------------------------------------------------------------------ fim

    /** Roda quando o painel fecha; recebe true se os materiais foram devolvidos. */
    public void onClose(Consumer<Boolean> r) {
        this.onClose = r;
    }

    /** Guarda uma entidade extra da sessão (some junto com o painel). */
    public void track(Entity e) {
        extras.add(e);
    }

    /** Fecha o painel. {@code returnItems}: devolve ao dono o que está nos encaixes. */
    public void close(boolean returnItems) {
        if (closed) {
            return;
        }
        closed = true;
        clearMark();
        for (Slot s : slots) {
            if (returnItems && s.item != null) {
                give(s.item);
            }
            s.item = null;
            s.display.remove();
            s.text.remove();
            s.click.remove();
        }
        markClicks.forEach(Entity::remove);
        extras.forEach(Entity::remove);
        hologram.remove();
        manager.unbind(this);
        onClose.accept(returnItems);
    }
}
