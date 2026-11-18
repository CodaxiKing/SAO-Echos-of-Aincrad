package com.aincrad.life.cooking;

import com.aincrad.core.AincradCore;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * O buff de comida de cada jogador: um por vez, gravado no perfil do core (vale depois de
 * relogar e nos outros servidores da rede). Os efeitos são aplicados aqui e tirados quando o
 * tempo acaba.
 */
public final class FoodBuffService implements Listener {

    private static final String PROFILE = "comida";
    /** O segundo buff (Banquete Real) e a bebida que não ocupa o lugar da comida (Néctar). */
    private static final String PROFILE_EXTRA = "comida_extra";
    private static final List<Attribute> ATTRIBUTES = List.of(Attribute.ATTACK_DAMAGE, Attribute.MOVEMENT_SPEED, Attribute.MAX_HEALTH);

    private final Plugin plugin;
    private final Messages msg;
    private final NamespacedKey key;
    private final NamespacedKey keyExtra;

    public FoodBuffService(Plugin plugin, Messages msg) {
        this.plugin = plugin;
        this.msg = msg;
        this.key = new NamespacedKey(plugin, "comida");
        this.keyExtra = new NamespacedKey(plugin, "comida_extra");
    }

    private FoodBuff stored(Player p) {
        return FoodBuff.decode(AincradCore.get().profile(p).get(plugin, PROFILE));
    }

    /** O buff ativo do jogador, ou null. */
    public FoodBuff current(Player p) {
        FoodBuff b = stored(p);
        return b != null && b.active(System.currentTimeMillis()) ? b : null;
    }

    /**
     * Dá o buff de um prato. Troca o que estava ativo.
     *
     * @return o buff que foi trocado, ou null
     */
    public FoodBuff apply(Player p, FoodBuff buff) {
        FoodBuff before = current(p);
        AincradCore.get().profile(p).set(plugin, PROFILE, buff.encode());
        refresh(p);
        return before;
    }

    /** O buff do lugar extra, ou null. */
    public FoodBuff currentExtra(Player p) {
        FoodBuff b = FoodBuff.decode(AincradCore.get().profile(p).get(plugin, PROFILE_EXTRA));
        return b != null && b.active(System.currentTimeMillis()) ? b : null;
    }

    /** Dá um buff no lugar extra, sem tirar o da comida. @return o que estava lá, ou null */
    public FoodBuff applyExtra(Player p, FoodBuff buff) {
        FoodBuff before = currentExtra(p);
        AincradCore.get().profile(p).set(plugin, PROFILE_EXTRA, buff.encode());
        refresh(p);
        return before;
    }

    public void clear(Player p) {
        AincradCore.get().profile(p).remove(plugin, PROFILE);
        refresh(p);
    }

    /** Põe os efeitos do jogador de acordo com o buff gravado. */
    public void refresh(Player p) {
        strip(p);
        put(p, current(p), key);
        put(p, currentExtra(p), keyExtra);
    }

    private void put(Player p, FoodBuff b, NamespacedKey key) {
        if (b == null) {
            return;
        }
        switch (b.type()) {
            case FORCA -> modify(p, Attribute.ATTACK_DAMAGE, b.value() / 100.0, AttributeModifier.Operation.ADD_SCALAR, key);
            case VELOCIDADE -> modify(p, Attribute.MOVEMENT_SPEED, b.value() / 100.0, AttributeModifier.Operation.ADD_SCALAR, key);
            case VIDA_MAXIMA -> modify(p, Attribute.MAX_HEALTH, b.value(), AttributeModifier.Operation.ADD_NUMBER, key);
            case REGENERACAO -> regenerate(p, b);
            default -> {
                // XP e sorte na pesca são lidos por quem usa (xpBonus e fishingLuck).
            }
        }
    }

    private void modify(Player p, Attribute a, double amount, AttributeModifier.Operation op, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst != null && amount != 0) {
            inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        }
    }

    private void strip(Player p) {
        for (Attribute a : ATTRIBUTES) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null) {
                inst.removeModifier(key);
                inst.removeModifier(keyExtra);
            }
        }
    }

    private void regenerate(Player p, FoodBuff b) {
        int level = (int) Math.max(1, Math.round(b.value()));
        // Renovado a cada segundo: some sozinho logo depois que o buff acaba.
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, level - 1, true, false, true));
    }

    /** Fração de XP a mais (0.05 = +5%). */
    public double xpBonus(Player p) {
        return of(current(p), FoodBuff.Type.XP) + of(currentExtra(p), FoodBuff.Type.XP);
    }

    /** Fração de sorte a mais na pesca (0.1 = +10%). */
    public double fishingLuck(Player p) {
        return of(current(p), FoodBuff.Type.SORTE_PESCA) + of(currentExtra(p), FoodBuff.Type.SORTE_PESCA);
    }

    private static double of(FoodBuff b, FoodBuff.Type type) {
        return b != null && b.type() == type ? b.value() / 100.0 : 0;
    }

    /** Texto "Força +8% por 12 min" do buff ativo, para mensagens e menus. */
    public String describe(FoodBuff b) {
        return msg.raw("cozinha.buff_valor." + b.type().key()).replace("<valor>", number(b.value()))
                + " — " + TimeFormat.duration(Math.max(0, b.expires() - System.currentTimeMillis()));
    }

    static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    /** Roda uma vez por segundo: tira o buff vencido e renova a regeneração. */
    public void second() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            FoodBuff extra = FoodBuff.decode(AincradCore.get().profile(p).get(plugin, PROFILE_EXTRA));
            if (extra != null && !extra.active(now)) {
                AincradCore.get().profile(p).remove(plugin, PROFILE_EXTRA);
                refresh(p);
            } else if (extra != null && extra.type() == FoodBuff.Type.REGENERACAO) {
                regenerate(p, extra);
            }
            FoodBuff b = stored(p);
            if (b == null) {
                continue;
            }
            if (!b.active(now)) {
                AincradCore.get().profile(p).remove(plugin, PROFILE);
                strip(p);
                msg.send(p, "cozinha.buff_acabou", Messages.vars("prato", b.dish()));
            } else if (b.type() == FoodBuff.Type.REGENERACAO) {
                regenerate(p, b);
            }
        }
    }

    /** Ao religar os módulos: reaplica em quem está online. */
    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    /** Ao desligar o módulo: tira os efeitos (o buff continua gravado). */
    public void stripAll() {
        Bukkit.getOnlinePlayers().forEach(this::strip);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }
}
