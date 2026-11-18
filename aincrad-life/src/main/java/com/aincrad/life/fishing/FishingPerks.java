package com.aincrad.life.fishing;

import com.aincrad.life.fishing.FishingRegistry.Bait;
import com.aincrad.life.fishing.FishingRegistry.Fish;
import com.aincrad.life.fishing.FishingRegistry.Point;
import com.aincrad.life.fishing.FishingRegistry.Rarity;
import com.aincrad.life.fishing.FishingRegistry.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** As regras da árvore do pescador: zona funda, tesouros e produção de iscas. Sem servidor. */
public final class FishingPerks {

    /** A isca da técnica do Criador de iscas: garante um peixe raro. */
    public static final String GOLDEN_BAIT = "isca_dourada";

    /** O que veio na linha no lugar de um peixe. */
    public enum Treasure { BAU, MATERIAL }

    private FishingPerks() {
    }

    /** Sem o nó das Águas profundas, o lançamento mais forte cai na zona do meio. */
    public static Zone allowed(Zone zone, ToDoubleFunction<String> mod) {
        return zone == Zone.FUNDO && mod.applyAsDouble("desbloqueia_fundo") < 1 ? Zone.MEIO : zone;
    }

    /**
     * A chance de vir um tesouro nesta zona: só para quem liberou os tesouros, no fundo (e no
     * meio, com o nó certo).
     */
    public static double treasureChance(Zone zone, double base, ToDoubleFunction<String> mod) {
        if (mod.applyAsDouble("desbloqueia_tesouros") < 1) {
            return 0;
        }
        boolean here = zone == Zone.FUNDO || (zone == Zone.MEIO && mod.applyAsDouble("tesouro_no_meio") >= 1);
        return here ? Math.max(0, Math.min(1, base + mod.applyAsDouble("chance_tesouro"))) : 0;
    }

    /** Quantos materiais vêm numa pescaria de material. */
    public static int materialAmount(ToDoubleFunction<String> mod, boolean vein) {
        int n = 1 + (int) Math.max(0, mod.applyAsDouble("materiais_extras"));
        return vein && mod.applyAsDouble("tecnica_veio") >= 1 ? n * 3 : n;
    }

    /** Quantos itens vêm num baú. */
    public static int chestItems(int base, ToDoubleFunction<String> mod) {
        return Math.max(1, base + (int) Math.max(0, mod.applyAsDouble("itens_bau")));
    }

    // ------------------------------------------------------------------ iscas

    /** O grau de uma isca: 0 (qualquer pescador), 1 (rank B) ou 2 (rank A, a de lendário e a dourada). */
    public static int baitTier(Bait bait) {
        if (bait.legendary() || bait.id().equals(GOLDEN_BAIT) || bait.luck() > 0.25) {
            return 2;
        }
        return bait.luck() > 0.1 ? 1 : 0;
    }

    /** O peixe mais simples que serve de matéria-prima para uma isca do grau. */
    public static Rarity fishFor(int tier) {
        return tier >= 2 ? Rarity.RARO : tier == 1 ? Rarity.INCOMUM : Rarity.COMUM;
    }

    /** Por que o pescador não pode fazer a isca: "no" (falta o nó), "peixe" (peixe simples demais) ou null (pode). */
    public static String baitRefusal(Bait bait, Fish fish, ToDoubleFunction<String> mod) {
        int tier = baitTier(bait);
        boolean golden = bait.id().equals(GOLDEN_BAIT);
        if ((golden && mod.applyAsDouble("tecnica_isca_dourada") < 1)
                || (!golden && tier == 2 && mod.applyAsDouble("desbloqueia_iscas_a") < 1)
                || (tier == 1 && mod.applyAsDouble("desbloqueia_iscas_b") < 1)) {
            return "no";
        }
        return fish == null || fish.rarity().ordinal() < fishFor(tier).ordinal() ? "peixe" : null;
    }

    /** Quantas iscas saem de um peixe. */
    public static int baitsPerFish(int base, ToDoubleFunction<String> mod) {
        return Math.max(1, base + (int) Math.max(0, mod.applyAsDouble("iscas_extras")));
    }

    /** Os peixes raros (ou melhores, menos o lendário) da tabela da zona: é o que a isca dourada traz. */
    public static List<Fish> rare(Point point, Zone zone, Map<String, Fish> fishes) {
        List<Fish> out = new ArrayList<>();
        Map<String, Integer> table = point.tables().get(zone);
        if (table != null) {
            for (String id : table.keySet()) {
                Fish f = fishes.get(id);
                if (f != null && f.rarity() == Rarity.RARO) {
                    out.add(f);
                }
            }
        }
        return out;
    }
}
