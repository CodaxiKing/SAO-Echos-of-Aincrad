package com.aincrad.effects.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackMergerTest {

    @TempDir
    Path tmp;

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject json(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void fontesJuntamProviders() {
        var a = new PackMerger.Layer("A", Map.of("assets/aincrad/font/space.json", b("{\"providers\":[{\"type\":\"space\"}]}")));
        var c = new PackMerger.Layer("B", Map.of("assets/aincrad/font/space.json", b("{\"providers\":[{\"type\":\"bitmap\"}]}")));
        var r = PackMerger.merge(List.of(a, c));
        assertEquals(2, json(r.files().get("assets/aincrad/font/space.json")).getAsJsonArray("providers").size());
        assertTrue(r.conflicts().isEmpty());
    }

    @Test
    void soundsJuntaChavesEAvisaRepetidas() {
        var a = new PackMerger.Layer("Effects", Map.of("assets/aincrad/sounds.json", b("{\"fx.a\":{\"sounds\":[\"a\"]},\"x\":{\"sounds\":[\"1\"]}}")));
        var c = new PackMerger.Layer("UI", Map.of("assets/aincrad/sounds.json", b("{\"ui.open\":{\"sounds\":[\"b\"]},\"x\":{\"sounds\":[\"2\"]}}")));
        var r = PackMerger.merge(List.of(a, c));
        JsonObject merged = json(r.files().get("assets/aincrad/sounds.json"));
        assertTrue(merged.has("fx.a"));
        assertTrue(merged.has("ui.open"));
        assertEquals("2", merged.getAsJsonObject("x").getAsJsonArray("sounds").get(0).getAsString());
        assertEquals(1, r.conflicts().size());
    }

    @Test
    void arquivoRepetidoVenceOUltimoComAviso() {
        var a = new PackMerger.Layer("A", Map.of("assets/aincrad/textures/x.png", new byte[]{1}));
        var c = new PackMerger.Layer("B", Map.of("assets/aincrad/textures/x.png", new byte[]{2}));
        var r = PackMerger.merge(List.of(a, c));
        assertArrayEquals(new byte[]{2}, r.files().get("assets/aincrad/textures/x.png"));
        assertEquals(1, r.conflicts().size());
    }

    @Test
    void arquivosIguaisNaoSaoConflitoEPackMcmetaEhIgnorado() {
        var a = new PackMerger.Layer("A", Map.of("a.txt", new byte[]{1}, "pack.mcmeta", b("{}")));
        var c = new PackMerger.Layer("B", Map.of("a.txt", new byte[]{1}));
        var r = PackMerger.merge(List.of(a, c));
        assertTrue(r.conflicts().isEmpty());
        assertFalse(r.files().containsKey("pack.mcmeta"));
    }

    @Test
    void lePastaComBarrasNormais() throws IOException {
        Path f = tmp.resolve("assets/aincrad/textures/ui/a.png");
        Files.createDirectories(f.getParent());
        Files.write(f, new byte[]{9});
        var layer = PackMerger.readFolder("UI", tmp);
        assertArrayEquals(new byte[]{9}, layer.files().get("assets/aincrad/textures/ui/a.png"));
    }

    @Test
    void sha1Conhecido() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", PackManager.sha1(b("abc")));
    }
}
