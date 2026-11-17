package com.aincrad.effects.step;

import com.aincrad.effects.engine.EffectParseException;
import com.aincrad.effects.engine.Playback;
import com.aincrad.effects.engine.Spec;
import com.aincrad.effects.image.ImageRegistry;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Passos que desenham na tela do espectador: título, imagem, actionbar e BossBar. */
public final class ScreenSteps {

    private ScreenSteps() {
    }

    private static Title.Times times(int in, int stay, int out) {
        return Title.Times.times(Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L));
    }

    /** tipo: titulo — titulo, subtitulo, entrada, fica, saida (segundos). */
    public static final class TitleStep extends Step {
        private final String title;
        private final String subtitle;
        private final int in;
        private final int stay;
        private final int out;

        public TitleStep(Spec s) {
            super(s);
            title = s.str("titulo", "");
            subtitle = s.str("subtitulo", "");
            in = s.ticks("entrada", 0.25);
            stay = s.ticks("fica", 2);
            out = s.ticks("saida", 0.5);
        }

        @Override
        public int duration() {
            return in + stay + out;
        }

        @Override
        public void run(Playback pb) {
            for (Player p : pb.viewers()) {
                p.showTitle(Title.title(pb.text(title, p), pb.text(subtitle, p), times(in, stay, out)));
            }
        }
    }

    /**
     * tipo: imagem — imagem do pack no centro da tela. Quem está sem o pack vê o texto "reserva"
     * (do passo ou de images.yml).
     */
    public static final class ImageStep extends Step {
        private final String image;
        private final String fallback;
        private final String subtitle;
        private final int in;
        private final int stay;
        private final int out;

        public ImageStep(Spec s, ImageRegistry images) {
            super(s);
            image = s.required("imagem");
            if (images.get(image) == null) {
                throw new EffectParseException(s.where() + ": imagem \"" + image + "\" não existe em images.yml");
            }
            fallback = s.str("reserva", null);
            subtitle = s.str("subtitulo", "");
            in = s.ticks("entrada", 0.25);
            stay = s.ticks("fica", 2);
            out = s.ticks("saida", 0.5);
        }

        @Override
        public int duration() {
            return in + stay + out;
        }

        @Override
        public void run(Playback pb) {
            ImageRegistry images = pb.plugin().images();
            ImageRegistry.Image img = images.get(image);
            for (Player p : pb.viewers()) {
                Component main;
                if (img != null && img.present() && pb.hasPack(p)) {
                    main = img.glyph();
                } else if (fallback != null) {
                    main = pb.text(fallback, p);
                } else {
                    main = images.render(image, false);
                }
                p.showTitle(Title.title(main, pb.text(subtitle, p), times(in, stay, out)));
            }
        }
    }

    /** tipo: actionbar — texto, duracao (segundos; reenviado a cada segundo para durar). */
    public static final class ActionBarStep extends Step {
        private final String text;
        private final int length;

        public ActionBarStep(Spec s) {
            super(s);
            text = s.required("texto");
            length = s.ticks("duracao", 2);
        }

        @Override
        public int duration() {
            return length;
        }

        @Override
        public void run(Playback pb) {
            int resend = Math.max(0, length - 40);
            pb.animate(20, resend, elapsed -> {
                for (Player p : pb.viewers()) {
                    p.sendActionBar(pb.text(text, p));
                }
            });
        }
    }

    /** tipo: bossbar — texto, cor, estilo, progresso_inicial, progresso_final, duracao. */
    public static final class BossBarStep extends Step {
        private final String text;
        private final BossBar.Color color;
        private final BossBar.Overlay overlay;
        private final float from;
        private final float to;
        private final int length;

        public BossBarStep(Spec s) {
            super(s);
            text = s.str("texto", "");
            String c = s.str("cor", "PINK").toUpperCase(Locale.ROOT);
            String o = s.str("estilo", "PROGRESS").toUpperCase(Locale.ROOT);
            try {
                color = BossBar.Color.valueOf(c);
                overlay = BossBar.Overlay.valueOf(o);
            } catch (IllegalArgumentException e) {
                throw new EffectParseException(s.where() + ": cor/estilo de BossBar inválido (" + c + "/" + o
                        + "). Cores: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE. Estilos: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20");
            }
            from = (float) clamp(s.dbl("progresso_inicial", 1));
            to = (float) clamp(s.dbl("progresso_final", 0));
            length = Math.max(1, s.ticks("duracao", 3));
        }

        private static double clamp(double v) {
            return Math.max(0, Math.min(1, v));
        }

        @Override
        public int duration() {
            return length;
        }

        @Override
        public void run(Playback pb) {
            List<Player> viewers = pb.viewers();
            List<BossBar> bars = new ArrayList<>();
            for (Player p : viewers) {
                BossBar bar = BossBar.bossBar(pb.text(text, p), from, color, overlay);
                p.showBossBar(bar);
                bars.add(bar);
            }
            Runnable hide = () -> {
                for (int i = 0; i < viewers.size(); i++) {
                    viewers.get(i).hideBossBar(bars.get(i));
                }
            };
            pb.onStop(hide);
            pb.animate(2, length, elapsed -> {
                float t = (float) elapsed / length;
                float progress = from + (to - from) * t;
                for (BossBar bar : bars) {
                    bar.progress(Math.max(0f, Math.min(1f, progress)));
                }
            });
            pb.later(length, hide);
        }
    }
}
