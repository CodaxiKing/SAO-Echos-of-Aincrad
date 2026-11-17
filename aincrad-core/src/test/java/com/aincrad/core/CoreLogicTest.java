package com.aincrad.core;

import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.region.Region;
import com.aincrad.core.region.RegionIndex;
import com.aincrad.core.util.Cooldowns;
import com.aincrad.core.util.TimeFormat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreLogicTest {

    @Test
    void regiaoContemOsBlocosDosCantos() {
        Region r = Region.of("sala", "world", 10, 64, 10, 0, 60, 0, "admin");
        assertEquals(0, r.minX());
        assertEquals(64, r.maxY());
        assertTrue(r.contains("world", 0, 60, 0));
        assertTrue(r.contains("world", 10.99, 64.5, 10.2));
        assertFalse(r.contains("world", 11.0, 62, 5));
        assertFalse(r.contains("nether", 5, 62, 5));
        assertEquals(11L * 5 * 11, r.volume());
        assertTrue(Region.validName("sala_chefe-1"));
        assertFalse(Region.validName("Sala Chefe"));
    }

    @Test
    void indiceAchaRegioesPequenasEGrandes() {
        RegionIndex idx = new RegionIndex();
        idx.put(Region.of("cidade", "world", -50, 0, -50, 50, 255, 50, "floors"));
        idx.put(Region.of("praca", "world", -5, 60, -5, 5, 80, 5, "admin"));
        idx.put(Region.of("andar1", "world", -5000, -64, -5000, 5000, 320, 5000, "floors")); // grande
        List<Region> at = idx.at("world", 0, 65, 0);
        assertEquals(List.of("praca", "cidade", "andar1"), at.stream().map(Region::name).toList(), "menor primeiro");
        assertEquals(List.of("cidade", "andar1"), idx.at("world", 30, 65, 30).stream().map(Region::name).toList());
        assertEquals(List.of("andar1"), idx.at("world", 3000, 65, -3000).stream().map(Region::name).toList());
        assertTrue(idx.at("world_nether", 0, 65, 0).isEmpty());
        idx.remove("praca");
        assertEquals(List.of("cidade", "andar1"), idx.at("world", 0, 65, 0).stream().map(Region::name).toList());
        idx.put(Region.of("cidade", "world", 100, 0, 100, 110, 10, 110, "floors")); // substitui
        assertEquals(List.of("andar1"), idx.at("world", 0, 65, 0).stream().map(Region::name).toList());
        assertEquals(2, idx.all().size());
    }

    @Test
    void indiceComCoordenadasNegativas() {
        RegionIndex idx = new RegionIndex();
        idx.put(Region.of("neg", "world", -20, 0, -20, -1, 10, -1, "admin"));
        assertEquals(1, idx.at("world", -0.5, 5, -0.5).size());
        assertEquals(1, idx.at("world", -19.9, 5, -16.1).size());
        assertTrue(idx.at("world", 0.1, 5, -0.5).isEmpty());
    }

    @Test
    void tempoEmPortugues() {
        assertEquals("agora", TimeFormat.duration(500));
        assertEquals("45 s", TimeFormat.duration(45_000));
        assertEquals("2 h 5 min", TimeFormat.duration(7_500_000));
        assertEquals("2 h", TimeFormat.duration(7_200_000));
        assertEquals("3 d 4 h", TimeFormat.duration(3 * 86_400_000L + 4 * 3_600_000L + 59_000));
        assertEquals("1 min", TimeFormat.duration(61_000, 1));
        assertEquals(9_000_000, TimeFormat.parse("2h30m"));
        assertEquals(90_000, TimeFormat.parse("90"));
        assertEquals(86_400_000, TimeFormat.parse("1d"));
        assertEquals(-1, TimeFormat.parse("2x"));
        assertEquals(-1, TimeFormat.parse("h"));
    }

    @Test
    void recargas() {
        AtomicLong now = new AtomicLong(0);
        Cooldowns c = new Cooldowns(now::get);
        UUID p = UUID.randomUUID();
        assertTrue(c.tryUse(p, "skill", 1000));
        assertFalse(c.tryUse(p, "skill", 1000));
        assertEquals(1000, c.remaining(p, "skill"));
        now.set(600);
        assertEquals(400, c.remaining(p, "skill"));
        now.set(1000);
        assertTrue(c.ready(p, "skill"));
        c.set(p, "outra", 50);
        c.clear(p);
        assertTrue(c.ready(p, "outra"));
    }

    @Test
    void configGanhaChavesNovasSemMexerNasDoDono() {
        YamlConfiguration user = new YamlConfiguration();
        user.set("banco.tipo", "mysql");
        user.set("removida", "valor do dono");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("banco.tipo", "sqlite");
        defaults.set("banco.conexoes", 8);
        defaults.set("novo.recurso.ativo", true);
        List<String> added = ConfigFile.merge(user, defaults);
        assertEquals(List.of("banco.conexoes", "novo.recurso.ativo"), added);
        assertEquals("mysql", user.getString("banco.tipo"));
        assertEquals(8, user.getInt("banco.conexoes"));
        assertTrue(user.getBoolean("novo.recurso.ativo"));
        assertEquals("valor do dono", user.getString("removida"));
        assertTrue(ConfigFile.merge(user, defaults).isEmpty());
    }
}
