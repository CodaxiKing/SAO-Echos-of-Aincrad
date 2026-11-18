package com.aincrad.life.cooking;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * O que a árvore do cozinheiro põe a mais num prato, além do buff da receita. Decidido na hora
 * em que o prato fica pronto e gravado nele: vale para quem comer, seja quem for. Sem servidor.
 *
 * <p>As receitas não têm categoria; o tipo do buff diz o que o prato é: força e vida máxima são
 * pratos de carne, velocidade e XP são doces e bebidas; prato de panela é festim.
 *
 * @param minSeconds  duração mínima do buff (Ragu: uma hora), 0 = a da receita
 * @param regen       dá também regeneração por alguns segundos (festins)
 * @param second      segundo buff, ao mesmo tempo (Banquete Real), ou null
 * @param secondValue valor do segundo buff
 * @param shareRadius quem está a até tantos blocos de quem come ganha o buff também (0 = ninguém)
 * @param instant     pode ser comido na hora, com um clique (Rancho)
 * @param twoBites    pode ser comido duas vezes (Despensa)
 * @param extraSlot   não ocupa o lugar do buff de comida (Néctar)
 * @param stack64     empilha até 64 (rações)
 * @param dayFood     tira a fome por um dia do Minecraft (Pão do Viajante)
 */
public record DishPerks(int minSeconds, boolean regen, FoodBuff.Type second, double secondValue, int shareRadius,
                        boolean instant, boolean twoBites, boolean extraSlot, boolean stack64, boolean dayFood) {

    public static final DishPerks NONE = new DishPerks(0, false, null, 0, 0, false, false, false, false, false);

    public static boolean meat(Recipe r) {
        return r.buff().type() == FoodBuff.Type.FORCA || r.buff().type() == FoodBuff.Type.VIDA_MAXIMA;
    }

    public static boolean sweet(Recipe r) {
        return r.buff().type() == FoodBuff.Type.VELOCIDADE || r.buff().type() == FoodBuff.Type.XP;
    }

    /** Quanto o buff sai mais forte: o bônus geral mais o do ramo (carnes, ou doces e bebidas). */
    public static double strength(Recipe r, ToDoubleFunction<String> mod) {
        return mod.applyAsDouble("forca_buff") + (meat(r) ? mod.applyAsDouble("forca_buff_carne") : 0)
                + (sweet(r) ? mod.applyAsDouble("forca_buff_doce") : 0);
    }

    /**
     * @param mod         a soma de um efeito nos nós que o cozinheiro comprou
     * @param secondLife  vida máxima do segundo buff do Banquete Real
     * @param secondForce força (%) do segundo buff, quando o prato já dá vida máxima
     */
    public static DishPerks of(Recipe r, Quality q, ToDoubleFunction<String> mod, double secondLife, double secondForce) {
        if (q == Quality.QUEIMADO) {
            return NONE;
        }
        boolean pot = r.pot();
        boolean excellent = q == Quality.EXCELENTE;
        boolean banquet = pot && mod.applyAsDouble("tecnica_banquete_real") >= 1;
        FoodBuff.Type second = !banquet ? null : r.buff().type() == FoodBuff.Type.VIDA_MAXIMA ? FoodBuff.Type.FORCA : FoodBuff.Type.VIDA_MAXIMA;
        return new DishPerks(
                meat(r) && excellent && mod.applyAsDouble("tecnica_ragu") >= 1 ? 3600 : 0,
                pot && mod.applyAsDouble("festim_regeneracao") >= 1,
                second, second == null ? 0 : second == FoodBuff.Type.FORCA ? secondForce : secondLife,
                pot ? (int) Math.max(0, mod.applyAsDouble("alcance_buff")) : 0,
                mod.applyAsDouble("tecnica_rancho") >= 1,
                !pot && excellent && mod.applyAsDouble("tecnica_despensa") >= 1,
                sweet(r) && excellent && mod.applyAsDouble("tecnica_nectar") >= 1,
                !pot && mod.applyAsDouble("empilha") >= 1,
                !pot && mod.applyAsDouble("tecnica_pao_do_viajante") >= 1);
    }

    /** As marcas do prato, para a descrição dele (chaves de cozinha.perk.*). */
    public List<String> labels() {
        List<String> out = new ArrayList<>();
        if (minSeconds > 0) {
            out.add("ragu");
        }
        if (second != null) {
            out.add("banquete_real");
        }
        if (regen) {
            out.add("regeneracao");
        }
        if (shareRadius > 0) {
            out.add("compartilha");
        }
        if (extraSlot) {
            out.add("nectar");
        }
        if (twoBites) {
            out.add("despensa");
        }
        if (instant) {
            out.add("rancho");
        }
        if (dayFood) {
            out.add("pao_do_viajante");
        }
        return out;
    }
}
