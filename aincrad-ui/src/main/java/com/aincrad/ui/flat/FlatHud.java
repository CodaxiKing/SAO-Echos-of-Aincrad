package com.aincrad.ui.flat;

import com.aincrad.ui.api.FlatDraw;
import com.aincrad.ui.api.FlatDraw.Font;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * HUD "em barras" (estilo do vídeo de referência): barra de vida com o nome, barra azul de
 * energia, caixas de XP e level, e as barras da party embaixo. Tudo em retângulos e letras, sem
 * arte: as cores vêm do código.
 */
public final class FlatHud {

    private static final int DARK = 0x0E1216;
    private static final int WHITE = 0xFFFFFF;
    private static final int SOFT = 0xD8DEE4;
    private static final int BLUE = 0x3FA9F5;
    private static final int GOLD = 0xF5A623;

    /** Um membro da party. */
    public record Member(String name, double ratio, int color) {
    }

    /** O que a HUD mostra. {@code xpNext} 0 = sem RPG (mostra {@code xpText}). */
    public record Data(String name, int health, int max, double ratio, int color, int energy, int energyMax,
                       String xpText, int level, List<Member> party) {
    }

    private FlatHud() {
    }

    private static final int INK = 0x3C3C3C;

    /**
     * Desenha a HUD com a borda esquerda em {@code x0} (negativo: à esquerda do centro).
     *
     * @param art usa as artes do pack: a moldura do anime com a barra em fatias
     */
    public static void paint(FlatDraw d, Data v, int x0, boolean art) {
        if (art) {
            paintArt(d, v, x0);
        } else {
            paint(d, v, x0);
        }
    }

    /**
     * A HUD com as artes do pack: a moldura (ui/hud/frame, cortada em três porque um glifo não
     * passa de 256 px), as 20 fatias da barra pintadas na cor da vida, e as barras pequenas da party.
     */
    private static void paintArt(FlatDraw d, Data v, int x0) {
        for (int i = 0; i < 3; i++) {
            d.icon(x0 + 62 * i, 2, 24, "aincrad:ui/hud/frame_" + i);
        }
        // Com vida, pelo menos uma fatia aparece.
        double ratio = v.health() > 0 ? Math.max(0.05, clamp(v.ratio())) : 0;
        FlatOverlay.slices(d, x0 + 59, 2, 24, 6, ratio, "aincrad:ui/hud/fill_", v.color());
        d.text(x0 + 11, 9, Font.NEGRITO, d.fit(Font.NEGRITO, v.name(), 42), INK);
        d.text(x0 + 100, 16, Font.PEQUENA, v.health() + "/" + v.max() + "  LV " + v.level(), INK);
        // Energia (a pausa depois da skill; sem o AincradSkills, a fome) e XP.
        double er = v.energyMax() <= 0 ? 0 : clamp(v.energy() / (double) v.energyMax());
        d.rect(x0 + 8, 30, 82, 5, DARK, 70);
        d.rect(x0 + 9, 31, (int) Math.round(80 * er), 3, BLUE, 100);
        d.rect(x0 + 96, 28, 60, 9, DARK, 70);
        d.textCenter(x0 + 126, 28, Font.PEQUENA, v.xpText(), SOFT);
        int y = 41;
        for (Member m : v.party()) {
            d.icon(x0 + 8, y, 8, "aincrad:ui/hud/mini");
            FlatOverlay.slices(d, x0 + 9, y, 8, 3, m.ratio(), "aincrad:ui/hud/mini_", m.color());
            d.text(x0 + 74, y, Font.PEQUENA, d.fit(Font.PEQUENA, m.name(), 80), WHITE);
            y += 11;
        }
    }

    /** A HUD só com retângulos e letras. */
    public static void paint(FlatDraw d, Data v, int x0) {
        // Vida: fundo escuro, barra na cor do estado, nome e números por cima.
        d.rect(x0, 4, 152, 11, DARK, 70);
        d.rect(x0 + 1, 5, (int) Math.round(150 * clamp(v.ratio())), 9, v.color(), 100);
        d.text(x0 + 4, 5, Font.NEGRITO, d.fit(Font.NEGRITO, v.name(), 90), WHITE);
        d.textRight(x0 + 148, 5, Font.PEQUENA, v.health() + " / " + v.max(), WHITE);

        // Energia (Sword Skills; por enquanto, a fome).
        double er = v.energyMax() <= 0 ? 0 : clamp(v.energy() / (double) v.energyMax());
        d.rect(x0 + 8, 18, 82, 5, DARK, 70);
        d.rect(x0 + 9, 19, (int) Math.round(80 * er), 3, BLUE, 100);
        d.text(x0 + 94, 16, Font.PEQUENA, v.energy() + " / " + v.energyMax(), SOFT);

        // Caixas: XP e level.
        d.rect(x0 + 130, 16, 56, 9, DARK, 70);
        d.textCenter(x0 + 158, 16, Font.PEQUENA, v.xpText(), SOFT);
        d.rect(x0 + 188, 16, 34, 9, DARK, 70);
        d.textCenter(x0 + 205, 16, Font.NEGRITO, "LVL " + v.level(), GOLD);

        // Party: uma linha por membro.
        int y = 29;
        for (Member m : v.party()) {
            d.rect(x0 + 8, y + 2, 62, 5, DARK, 70);
            d.rect(x0 + 9, y + 3, (int) Math.round(60 * clamp(m.ratio())), 3, m.color(), 100);
            d.text(x0 + 74, y, Font.PEQUENA, d.fit(Font.PEQUENA, m.name(), 80), WHITE);
            y += 10;
        }
    }

    /** Dados de exemplo com todas as linhas, para a passada de preparação. */
    public static Data sample(int partyRows) {
        List<Member> party = new ArrayList<>();
        for (int i = 0; i < partyRows; i++) {
            party.add(new Member("Jogador", 1, WHITE));
        }
        return new Data("Jogador", 20, 20, 1, WHITE, 20, 20, "0 / 100", 1, party);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    // ------------------------------------------------------------------ AincradRPG (opcional)

    private static boolean looked;
    private static Method getLevel;
    private static Method getXp;
    private static Method getXpToNext;

    /** Esquece o AincradRPG (depois de um reload ele pode ter ligado ou desligado). */
    public static void resetHooks() {
        looked = false;
        getLevel = null;
    }

    private static void look() {
        if (looked) {
            return;
        }
        looked = true;
        for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
            if (!c.getName().equals("com.aincrad.rpg.api.AincradRPGAPI")) {
                continue;
            }
            RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
            if (r == null) {
                return;
            }
            try {
                getLevel = c.getMethod("getLevel", Player.class);
                getXp = c.getMethod("getXp", Player.class);
                getXpToNext = c.getMethod("getXpToNext", Player.class);
            } catch (NoSuchMethodException e) {
                getLevel = null;
            }
            return;
        }
    }

    /** Level do AincradRPG, ou o level de XP do Minecraft. */
    public static int level(Player p) {
        look();
        if (getLevel != null) {
            try {
                return ((Number) getLevel.invoke(null, p)).intValue();
            } catch (ReflectiveOperationException | RuntimeException e) {
                getLevel = null;
            }
        }
        return p.getLevel();
    }

    /** "120 / 283" (AincradRPG), "MAX" no level máximo, ou a % da barra de XP do Minecraft. */
    public static String xpText(Player p) {
        look();
        if (getLevel != null) {
            try {
                long next = ((Number) getXpToNext.invoke(null, p)).longValue();
                return next <= 0 ? "MAX" : getXp.invoke(null, p) + " / " + next;
            } catch (ReflectiveOperationException | RuntimeException e) {
                getLevel = null;
            }
        }
        return Math.round(p.getExp() * 100) + "%";
    }
}
