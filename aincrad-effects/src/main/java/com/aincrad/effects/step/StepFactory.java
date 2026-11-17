package com.aincrad.effects.step;

import com.aincrad.effects.engine.EffectParseException;
import com.aincrad.effects.engine.Spec;
import com.aincrad.effects.image.ImageRegistry;

import java.util.List;
import java.util.Locale;

/** Cria o passo certo a partir do campo "tipo". */
public final class StepFactory {

    public static final List<String> TYPES = List.of(
            "titulo", "imagem", "actionbar", "bossbar", "som", "particulas", "texto_mundo", "fogos", "pocao", "menu");

    private StepFactory() {
    }

    public static Step create(Spec s, ImageRegistry images) {
        String type = s.required("tipo").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "titulo", "título" -> new ScreenSteps.TitleStep(s);
            case "imagem" -> new ScreenSteps.ImageStep(s, images);
            case "actionbar" -> new ScreenSteps.ActionBarStep(s);
            case "bossbar" -> new ScreenSteps.BossBarStep(s);
            case "som" -> new WorldSteps.SoundStep(s);
            case "particulas", "partículas" -> new WorldSteps.ParticleStep(s);
            case "texto_mundo", "texto" -> new WorldSteps.TextDisplayStep(s);
            case "fogos" -> new WorldSteps.FireworkStep(s);
            case "pocao", "poção" -> new WorldSteps.PotionStep(s);
            case "menu" -> new MenuStep(s, images);
            default -> throw new EffectParseException(s.where() + ": tipo desconhecido \"" + type + "\" (tipos: "
                    + String.join(", ", TYPES) + ")");
        };
    }
}
