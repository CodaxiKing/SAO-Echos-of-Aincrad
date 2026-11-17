package com.aincrad.effects.step;

import com.aincrad.effects.engine.EffectParseException;
import com.aincrad.effects.engine.Playback;
import com.aincrad.effects.engine.Spec;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Passos que acontecem no mundo: som, partículas, texto (Text Display), fogos e poção. */
public final class WorldSteps {

    /** Marca nos fogos dos efeitos: o dano da explosão deles é cancelado. */
    public static final String FIREWORK_TAG = "efeito_fogos";

    private WorldSteps() {
    }

    /** tipo: som — som, volume, tom, categoria, no_jogador (true = toca na posição de cada espectador). */
    public static final class SoundStep extends Step {
        private final Sound sound;
        private final boolean atViewer;

        public SoundStep(Spec s) {
            super(s);
            String key = s.required("som").toLowerCase(Locale.ROOT);
            Sound.Source source;
            try {
                source = Sound.Source.valueOf(s.str("categoria", "MASTER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new EffectParseException(s.where() + ": categoria de som inválida (MASTER, PLAYER, AMBIENT, BLOCK, HOSTILE, NEUTRAL, MUSIC, RECORD, WEATHER, VOICE)");
            }
            Key k;
            try {
                k = Key.key(key);
            } catch (Exception e) {
                throw new EffectParseException(s.where() + ": som inválido \"" + key + "\" (ex.: minecraft:entity.player.levelup)");
            }
            sound = Sound.sound(k, source, (float) s.dbl("volume", 1), (float) s.dbl("tom", 1));
            atViewer = s.bool("no_jogador", false);
        }

        @Override
        public void run(Playback pb) {
            if (atViewer) {
                for (Player p : pb.viewers()) {
                    p.playSound(sound, Sound.Emitter.self());
                }
                return;
            }
            Location o = pb.origin();
            for (Player p : pb.viewersNear(o)) {
                p.playSound(sound, o.getX(), o.getY(), o.getZ());
            }
        }
    }

    /**
     * tipo: particulas — particula, forma (ponto, circulo, coluna, espiral, explosao, linha), raio,
     * altura, voltas, comprimento, direcao, quantidade, espalhar [x,y,z], velocidade, deslocamento,
     * e para partículas com dados: cor, cor_final, tamanho, bloco, item.
     */
    public static final class ParticleStep extends Step {
        private final Particle particle;
        private final Object data;
        private final Shapes.Shape shape;
        private final List<Vector> points;
        private final Vector spread;
        private final double speed;
        private final Vector offset;
        private final double length;
        private final Vector direction;
        private final int count;

        public ParticleStep(Spec s) {
            super(s);
            String name = s.required("particula").toUpperCase(Locale.ROOT);
            try {
                particle = Particle.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new EffectParseException(s.where() + ": partícula desconhecida \"" + name + "\" (ex.: END_ROD, DUST, FLAME, BLOCK)");
            }
            data = particleData(particle, s);
            try {
                shape = Shapes.Shape.parse(s.str("forma", "ponto"));
            } catch (IllegalArgumentException e) {
                throw new EffectParseException(s.where() + ": " + e.getMessage());
            }
            count = Math.max(1, Math.min(2000, s.integer("quantidade", 20)));
            double r = s.dbl("raio", 1);
            double h = s.dbl("altura", 2);
            spread = s.vector("espalhar", new Vector());
            speed = s.dbl("velocidade", shape == Shapes.Shape.EXPLOSAO ? 0.15 : 0);
            offset = s.vector("deslocamento", new Vector());
            length = s.dbl("comprimento", 5);
            direction = s.has("direcao") ? s.vector("direcao", new Vector(0, 0, 1)) : null;
            points = switch (shape) {
                case PONTO -> List.of(new Vector());
                case CIRCULO -> Shapes.circle(r, count);
                case COLUNA -> Shapes.column(r, h, count);
                case ESPIRAL -> Shapes.spiral(r, h, s.dbl("voltas", 2), count);
                case EXPLOSAO -> Shapes.sphereDirections(count);
                case LINHA -> null; // depende da direção da origem
            };
        }

        private static Object particleData(Particle particle, Spec s) {
            Class<?> type = particle.getDataType();
            if (type == Void.class) {
                return null;
            }
            Color color = s.color("cor", Color.fromRGB(0x5BC8FF));
            float size = (float) s.dbl("tamanho", 1.0);
            if (type == Particle.DustOptions.class) {
                return new Particle.DustOptions(color, size);
            }
            if (type == Particle.DustTransition.class) {
                return new Particle.DustTransition(color, s.color("cor_final", Color.WHITE), size);
            }
            if (type == Color.class) {
                return color;
            }
            if (type == BlockData.class) {
                Material m = Material.matchMaterial(s.str("bloco", "LIGHT_BLUE_STAINED_GLASS"));
                if (m == null || !m.isBlock()) {
                    throw new EffectParseException(s.where() + ": \"bloco\" precisa ser um bloco (ex.: LIGHT_BLUE_STAINED_GLASS)");
                }
                return m.createBlockData();
            }
            if (type == ItemStack.class) {
                Material m = Material.matchMaterial(s.str("item", "DIAMOND"));
                if (m == null || !m.isItem()) {
                    throw new EffectParseException(s.where() + ": \"item\" precisa ser um item (ex.: DIAMOND)");
                }
                return new ItemStack(m);
            }
            if (type == Float.class) {
                return (float) s.dbl("valor", 0);
            }
            if (type == Integer.class) {
                return s.integer("valor", 0);
            }
            throw new EffectParseException(s.where() + ": a partícula " + particle + " precisa de dados que o motor ainda não suporta");
        }

        @Override
        public void run(Playback pb) {
            Location base = pb.origin().add(offset);
            List<Vector> pts = points;
            if (pts == null) {
                Vector dir = direction != null ? direction : pb.origin().getDirection();
                pts = Shapes.line(dir, length, count);
            }
            boolean directional = shape == Shapes.Shape.EXPLOSAO;
            int perPoint = shape == Shapes.Shape.PONTO ? count : 1;
            int total = pts.size() * perPoint;
            for (Player p : pb.viewersNear(base)) {
                int allowed = pb.limits().takeParticles(p, total);
                if (allowed <= 0) {
                    continue;
                }
                if (shape == Shapes.Shape.PONTO) {
                    p.spawnParticle(particle, base, allowed, spread.getX(), spread.getY(), spread.getZ(), speed, data);
                    continue;
                }
                for (int i = 0; i < pts.size() && i < allowed; i++) {
                    Vector v = pts.get(i);
                    if (directional) {
                        p.spawnParticle(particle, base, 0, v.getX(), v.getY(), v.getZ(), speed, data);
                    } else {
                        p.spawnParticle(particle, base.clone().add(v), 1,
                                spread.getX(), spread.getY(), spread.getZ(), speed, data);
                    }
                }
            }
        }
    }

    /**
     * tipo: texto_mundo — Text Display no mundo: texto (aceita &lt;img:id&gt;), deslocamento, escala,
     * escala_final, subir, duracao, fade_entrada, fade_saida, billboard, sombra, fundo (#AARRGGBB).
     * Cada espectador recebe o seu (variáveis, idioma e pack podem ser diferentes).
     */
    public static final class TextDisplayStep extends Step {
        private final String text;
        private final Vector offset;
        private final float scale;
        private final float scaleEnd;
        private final float rise;
        private final int length;
        private final int fadeIn;
        private final int fadeOut;
        private final Display.Billboard billboard;
        private final boolean shadow;
        private final Color background;

        public TextDisplayStep(Spec s) {
            super(s);
            text = s.required("texto");
            offset = s.vector("deslocamento", new Vector(0, 2, 0));
            scale = (float) s.dbl("escala", 1);
            scaleEnd = (float) s.dbl("escala_final", scale);
            rise = (float) s.dbl("subir", 0);
            length = Math.max(1, s.ticks("duracao", 2));
            fadeIn = Math.min(length / 2, s.ticks("fade_entrada", 0.25));
            fadeOut = Math.min(length / 2, s.ticks("fade_saida", 0.5));
            try {
                billboard = Display.Billboard.valueOf(s.str("billboard", "CENTER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new EffectParseException(s.where() + ": billboard inválido (FIXED, VERTICAL, HORIZONTAL, CENTER)");
            }
            shadow = s.bool("sombra", true);
            background = argb(s.str("fundo", "#00000000"), s.where());
        }

        private static Color argb(String raw, String where) {
            String h = raw.trim().startsWith("#") ? raw.trim().substring(1) : raw.trim();
            try {
                if (h.length() == 8) {
                    return Color.fromARGB((int) Long.parseLong(h, 16));
                }
                if (h.length() == 6) {
                    return Color.fromARGB(0xFF000000 | Integer.parseInt(h, 16));
                }
            } catch (NumberFormatException ignored) {
                // cai no erro abaixo
            }
            throw new EffectParseException(where + ": fundo inválido (use #AARRGGBB)");
        }

        @Override
        public int duration() {
            return length;
        }

        /** Opacidade (0–255) no tick {@code t} de um texto com fade de entrada e saída. */
        static int opacityAt(int t, int length, int fadeIn, int fadeOut) {
            double a = 1;
            if (fadeIn > 0 && t < fadeIn) {
                a = (double) t / fadeIn;
            } else if (fadeOut > 0 && t > length - fadeOut) {
                a = (double) (length - t) / fadeOut;
            }
            a = Math.max(0, Math.min(1, a));
            return (int) Math.round(16 + a * (255 - 16));
        }

        private static Transformation transform(float s, float dy) {
            return new Transformation(new Vector3f(0, dy, 0), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
        }

        @Override
        public void run(Playback pb) {
            Location at = pb.origin().add(offset);
            List<TextDisplay> spawned = new ArrayList<>();
            for (Player p : pb.viewersNear(at)) {
                TextDisplay d = pb.spawnFor(at, TextDisplay.class, t -> {
                    t.text(pb.text(text, p));
                    t.setBillboard(billboard);
                    t.setShadowed(shadow);
                    t.setDefaultBackground(false);
                    t.setBackgroundColor(background);
                    t.setSeeThrough(false);
                    t.setLineWidth(300);
                    t.setBrightness(new Display.Brightness(15, 15));
                    t.setTransformation(transform(scale, 0));
                    t.setTextOpacity((byte) opacityAt(0, length, fadeIn, fadeOut));
                }, List.of(p), length);
                if (d != null) {
                    spawned.add(d);
                }
            }
            if (spawned.isEmpty()) {
                return;
            }
            boolean moves = rise != 0 || scaleEnd != scale;
            if (moves) {
                // No tick seguinte o cliente interpola sozinho até o estado final.
                pb.later(1, () -> {
                    for (TextDisplay d : spawned) {
                        if (d.isValid()) {
                            d.setInterpolationDelay(0);
                            d.setInterpolationDuration(Math.max(1, length - 1));
                            d.setTransformation(transform(scaleEnd, rise));
                        }
                    }
                });
            }
            if (fadeIn > 0 || fadeOut > 0) {
                pb.animate(2, length, t -> {
                    byte op = (byte) opacityAt(t, length, fadeIn, fadeOut);
                    for (TextDisplay d : spawned) {
                        if (d.isValid()) {
                            d.setTextOpacity(op);
                        }
                    }
                });
            }
        }
    }

    /**
     * tipo: fogos — forma_fogo (BALL, BALL_LARGE, STAR, BURST, CREEPER), cores, cores_fade, rastro,
     * brilho, potencia, quantidade, espalhar (raio), deslocamento, explodir (true = explode na hora).
     */
    public static final class FireworkStep extends Step {
        private final FireworkEffect effect;
        private final int power;
        private final int amount;
        private final double spreadRadius;
        private final Vector offset;
        private final boolean detonate;

        public FireworkStep(Spec s) {
            super(s);
            FireworkEffect.Type type;
            try {
                type = FireworkEffect.Type.valueOf(s.str("forma_fogo", "BALL").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new EffectParseException(s.where() + ": forma_fogo inválida (BALL, BALL_LARGE, STAR, BURST, CREEPER)");
            }
            List<Color> colors = s.colors("cores", List.of(Color.fromRGB(0x5BC8FF), Color.WHITE));
            FireworkEffect.Builder b = FireworkEffect.builder().with(type).withColor(colors)
                    .trail(s.bool("rastro", false)).flicker(s.bool("brilho", true));
            List<Color> fade = s.colors("cores_fade", List.of());
            if (!fade.isEmpty()) {
                b.withFade(fade);
            }
            effect = b.build();
            power = Math.max(0, Math.min(3, s.integer("potencia", 1)));
            amount = Math.max(1, Math.min(20, s.integer("quantidade", 1)));
            spreadRadius = s.dbl("espalhar", 0);
            offset = s.vector("deslocamento", new Vector(0, 1.5, 0));
            detonate = s.bool("explodir", true);
        }

        @Override
        public int duration() {
            return detonate ? 2 : 40;
        }

        @Override
        public void run(Playback pb) {
            NamespacedKey tag = new NamespacedKey(pb.plugin(), FIREWORK_TAG);
            for (int i = 0; i < amount; i++) {
                double a = 2 * Math.PI * i / amount;
                Location at = pb.origin().add(offset).add(Math.cos(a) * spreadRadius, 0, Math.sin(a) * spreadRadius);
                Firework fw = pb.spawnFor(at, Firework.class, f -> {
                    FireworkMeta meta = f.getFireworkMeta();
                    meta.addEffect(effect);
                    meta.setPower(power);
                    f.setFireworkMeta(meta);
                    f.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
                }, pb.viewersNear(at), 60);
                if (fw != null && detonate) {
                    pb.later(1, () -> {
                        if (fw.isValid()) {
                            fw.detonate();
                        }
                    });
                }
            }
        }
    }

    /** tipo: pocao — efeito (ex.: blindness), duracao, nivel, particulas, alvo (espectadores | jogador). */
    public static final class PotionStep extends Step {
        private final PotionEffectType type;
        private final int length;
        private final int amplifier;
        private final boolean particles;
        private final boolean onlyTarget;

        public PotionStep(Spec s) {
            super(s);
            String raw = s.required("efeito").toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.fromString(raw.contains(":") ? raw : "minecraft:" + raw);
            type = key == null ? null : Registry.EFFECT.get(key);
            if (type == null) {
                throw new EffectParseException(s.where() + ": efeito de poção desconhecido \"" + raw + "\" (ex.: blindness, slowness, glowing)");
            }
            length = Math.max(1, s.ticks("duracao", 1));
            amplifier = Math.max(0, s.integer("nivel", 1) - 1);
            particles = s.bool("particulas", false);
            onlyTarget = s.str("alvo", "espectadores").equalsIgnoreCase("jogador");
        }

        @Override
        public void run(Playback pb) {
            List<Player> who = onlyTarget
                    ? (pb.target() != null && pb.target().isOnline() ? List.of(pb.target()) : List.of())
                    : pb.viewers();
            for (Player p : who) {
                p.addPotionEffect(new PotionEffect(type, length, amplifier, true, particles, false));
            }
        }
    }
}
