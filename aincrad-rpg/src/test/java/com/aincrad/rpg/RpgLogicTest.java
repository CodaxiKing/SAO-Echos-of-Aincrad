package com.aincrad.rpg;

import com.aincrad.rpg.item.ItemRoll;
import com.aincrad.rpg.item.Rarity;
import com.aincrad.rpg.model.MobRewards;
import com.aincrad.rpg.model.Progress;
import com.aincrad.rpg.model.Stat;
import com.aincrad.rpg.model.XpCurve;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpgLogicTest {

    private final XpCurve curve = XpCurve.formula(100, 100, 1.5);

    @Test
    void curvaFormulaETabela() {
        assertEquals(100, curve.toNext(1));
        assertEquals(283, curve.toNext(2)); // 100 × 2^1.5 = 282.8
        assertEquals(0, curve.toNext(100));
        assertEquals(383, curve.totalFor(3));
        XpCurve t = XpCurve.table(5, List.of(10L, 20L));
        assertEquals(10, t.toNext(1));
        assertEquals(20, t.toNext(2));
        assertEquals(20, t.toNext(4)); // repete o último
        assertEquals(0, t.toNext(5));
    }

    @Test
    void sobeVariosLevelsDeUmaVez() {
        Progress p = new Progress();
        int gained = p.addXp(100 + 283 + 50, curve, 3);
        assertEquals(2, gained);
        assertEquals(3, p.level());
        assertEquals(50, p.xp());
        assertEquals(6, p.points());
        assertTrue(p.dirty());
    }

    @Test
    void levelMaximoParaDeJuntar() {
        XpCurve small = XpCurve.formula(3, 10, 1);
        Progress p = new Progress();
        p.addXp(1000, small, 1);
        assertEquals(3, p.level());
        assertEquals(0, p.xp());
        assertEquals(0, p.addXp(50, small, 1));
    }

    @Test
    void morteNuncaDesceLevel() {
        Progress p = Progress.of(5, 40, 0, 0, 0, 0);
        assertEquals(4, p.deathPenalty(0.1));
        assertEquals(36, p.xp());
        assertEquals(5, p.level());
        Progress zero = Progress.of(5, 0, 0, 0, 0, 0);
        assertEquals(0, zero.deathPenalty(0.5));
        assertEquals(5, zero.level());
    }

    @Test
    void tirarXpPodeDescerETiraPontos() {
        Progress p = Progress.of(3, 10, 1, 2, 0, 0);
        long removed = p.removeXp(10 + 283 + 5, curve, true, 3);
        assertEquals(298, removed);
        assertEquals(1, p.level());
        assertEquals(95, p.xp());
        assertEquals(0, p.points());
        assertEquals(0, p.stat(Stat.FORCA)); // 1 livre + 2 de força = os 3 do level 3; depois mais 3 do 2
        Progress keep = Progress.of(3, 10, 0, 0, 0, 0);
        assertEquals(10, keep.removeXp(500, curve, false, 3));
        assertEquals(3, keep.level());
    }

    @Test
    void gastarEResetarPontos() {
        Progress p = Progress.of(2, 0, 2, 0, 0, 0);
        assertTrue(p.spend(Stat.FORCA));
        assertTrue(p.spend(Stat.VITALIDADE));
        assertFalse(p.spend(Stat.AGILIDADE));
        p.resetStats();
        assertEquals(2, p.points());
        assertEquals(0, p.stat(Stat.FORCA));
        p.setLevel(10, curve, 3);
        assertEquals(27, p.points());
    }

    @Test
    void xpDeMobComReducao() {
        assertEquals(30, MobRewards.xp(6, 5, 5, 3, 0.15, 0.1));
        assertEquals(30, MobRewards.xp(6, 5, 8, 3, 0.15, 0.1));   // 3 acima: livre
        assertEquals(26, MobRewards.xp(6, 5, 9, 3, 0.15, 0.1));   // 4 acima: -15%
        assertEquals(3, MobRewards.xp(6, 5, 50, 3, 0.15, 0.1));   // muito acima: mínimo 10%
        assertEquals(1.0, MobRewards.scale(1, 0.12), 1e-9);
        assertEquals(1.48, MobRewards.scale(5, 0.12), 1e-9);
    }

    @Test
    void sorteioDeLevelFicaNaFaixa() {
        assertEquals(1, MobRewards.pick(1, 4, 0));
        assertEquals(4, MobRewards.pick(1, 4, 0.9999));
        assertEquals(4, MobRewards.pick(4, 1, 1.0));
        assertEquals(3, MobRewards.pick(3, 3, 0.5));
    }

    @Test
    void raridadeEBonus() {
        Map<Rarity, Integer> w = new EnumMap<>(Rarity.class);
        w.put(Rarity.LENDARIO, 1);
        assertEquals(Rarity.LENDARIO, ItemRoll.rarity(w, new Random(1)));
        assertEquals(Rarity.COMUM, ItemRoll.rarity(Map.of(), new Random(1)));
        assertEquals(Rarity.EPICO, Rarity.parse("épico"));

        Map<String, Double> rolled = ItemRoll.roll(Map.of("dano", new ItemRoll.Range(5, 3)), 1.5, new Random(7));
        double v = rolled.get("dano");
        assertTrue(v >= 4.5 && v <= 7.5, "dano " + v);

        Map<Rarity, Integer> all = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) {
            all.put(r, 10);
        }
        Random rnd = new Random(42);
        EnumMap<Rarity, Integer> seen = new EnumMap<>(Rarity.class);
        for (int i = 0; i < 1000; i++) {
            seen.merge(ItemRoll.rarity(all, rnd), 1, Integer::sum);
        }
        assertEquals(5, seen.size());
    }
}
