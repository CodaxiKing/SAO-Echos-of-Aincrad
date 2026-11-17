package com.aincrad.effects.engine;

import com.aincrad.effects.image.ImageRegistry;
import com.aincrad.effects.image.SpaceFont;
import com.aincrad.effects.trigger.Region;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineLogicTest {

    @Test
    void publico() {
        assertEquals(Audience.Kind.PLAYER, Audience.parse("jogador", null).kind());
        Audience r = Audience.parse("raio:12.5", null);
        assertEquals(Audience.Kind.RADIUS, r.kind());
        assertEquals(12.5, r.radius());
        assertEquals(Audience.Kind.SERVER, Audience.parse("SERVIDOR", null).kind());
        assertEquals(Audience.Kind.WORLD, Audience.parse("mundo", null).kind());
        assertNull(Audience.parse("raio:-3", null));
        assertNull(Audience.parse("raio:abc", null));
        assertNull(Audience.parse("lua", null));
        assertEquals("raio:16", Audience.radius(16).toString());
    }

    @Test
    void variaveis() {
        Map<String, String> vars = Map.of("jogador", "Kirito", "andar", "1", "item", "$5 \\ espada");
        assertEquals("Kirito venceu o andar 1", TextRenderer.substitute("{jogador} venceu o andar {andar}", vars));
        assertEquals("ganhou $5 \\ espada", TextRenderer.substitute("ganhou {item}", vars));
        assertEquals("{desconhecida}", TextRenderer.substitute("{desconhecida}", vars));
        assertEquals("sem chaves", TextRenderer.substitute("sem chaves", vars));
    }

    @Test
    void limiteDeParticulasPorTick() {
        Limits l = new Limits(100, 2);
        UUID id = UUID.randomUUID();
        assertEquals(60, l.takeParticles(id, 1, 60));
        assertEquals(40, l.takeParticles(id, 1, 60));
        assertEquals(0, l.takeParticles(id, 1, 10));
        assertEquals(10, l.takeParticles(id, 2, 10)); // tick novo, orçamento novo
    }

    @Test
    void limiteDeEntidades() {
        Limits l = new Limits(100, 2);
        UUID id = UUID.randomUUID();
        assertTrue(l.takeEntity(id));
        assertTrue(l.takeEntity(id));
        assertFalse(l.takeEntity(id));
        l.releaseEntity(id);
        assertTrue(l.takeEntity(id));
    }

    @Test
    void ticksESegundos() {
        assertEquals(10, Spec.toTicks(0.5));
        assertEquals(0, Spec.toTicks(-1));
        Spec s = Spec.of(Map.of("em", "1,5", "deslocamento", List.of(1, 2.5, -3), "cor", "#FF0000"), "teste");
        assertEquals(30, s.ticks("em", 0));
        assertEquals(new Vector(1, 2.5, -3), s.vector("deslocamento", new Vector()));
        assertEquals(0xFF0000, s.color("cor", null).asRGB());
    }

    @Test
    void regiaoIncluiBlocoDoCanto() {
        Region r = Region.of("world", new Vector(10, 64, 10), new Vector(0, 60, 0));
        assertTrue(r.contains(0, 60, 0));
        assertTrue(r.contains(10.9, 64.5, 10.2));
        assertFalse(r.contains(11.0, 62, 5));
        assertFalse(r.contains(-0.1, 62, 5));
    }

    @Test
    void espacosSomamExato() {
        for (int px : new int[]{0, 1, -8, 200, -333, 2047}) {
            assertEquals(px, SpaceFont.measure(SpaceFont.shift(px)));
        }
    }

    @Test
    void carregaEfeitosEApontaErros() {
        ImageRegistry images = ImageRegistry.load(new YamlConfiguration(), new File("nao-existe"), null);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("ok.publico", "raio:10");
        cfg.set("ok.passos", List.of(
                Map.of("em", 0, "tipo", "titulo", "titulo", "Oi {jogador}"),
                Map.of("em", 1.5, "tipo", "som", "som", "minecraft:entity.player.levelup", "repetir", 2, "intervalo", 0.5),
                Map.of("em", 0, "tipo", "actionbar", "texto", "x", "duracao", 3)));
        cfg.set("sem_tipo.passos", List.of(Map.of("em", 0)));
        cfg.set("tipo_errado.passos", List.of(Map.of("tipo", "laser")));
        cfg.set("imagem_errada.passos", List.of(Map.of("tipo", "imagem", "imagem", "nao_existe")));
        cfg.set("vazio.publico", "jogador");

        Map<String, Effect> out = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        EffectLoader.load(cfg, "teste.yml", images, out, errors);

        assertEquals(List.of("ok"), List.copyOf(out.keySet()));
        Effect ok = out.get("ok");
        assertEquals(Audience.Kind.RADIUS, ok.audience().kind());
        assertEquals(3, ok.steps().size());
        // som: começa em 30 ticks e repete 2× a cada 10 → termina em 50
        assertEquals(60, ok.length()); // actionbar dura 3 s = 60 ticks
        assertEquals(4, errors.size(), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("laser")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("nao_existe")));
    }
}
