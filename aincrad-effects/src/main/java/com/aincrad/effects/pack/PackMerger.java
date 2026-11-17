package com.aincrad.effects.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Junta várias pastas de resource pack (a do Effects e as registradas por outros plugins) num só.
 *
 * <ul>
 *   <li>font/*.json: junta as listas "providers";</li>
 *   <li>atlases/*.json: junta as listas "sources";</li>
 *   <li>sounds.json e lang/*.json: junta as chaves (a camada mais nova vence numa chave repetida);</li>
 *   <li>qualquer outro arquivo repetido: a camada mais nova vence, e o conflito é avisado.</li>
 * </ul>
 * pack.mcmeta das camadas é ignorado: o Effects escreve o seu.
 */
public final class PackMerger {

    public record Layer(String name, Map<String, byte[]> files) {
    }

    public record Result(Map<String, byte[]> files, List<String> conflicts) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern FONT = Pattern.compile("assets/[^/]+/font/.+\\.json");
    private static final Pattern ATLAS = Pattern.compile("assets/[^/]+/atlases/.+\\.json");
    private static final Pattern KEYED = Pattern.compile("assets/[^/]+/(sounds\\.json|lang/.+\\.json)");

    private PackMerger() {
    }

    public static Result merge(List<Layer> layers) {
        Map<String, byte[]> out = new TreeMap<>();
        Map<String, String> owner = new TreeMap<>();
        List<String> conflicts = new ArrayList<>();
        for (Layer layer : layers) {
            for (Map.Entry<String, byte[]> e : new TreeMap<>(layer.files()).entrySet()) {
                String path = e.getKey();
                if (path.equals("pack.mcmeta")) {
                    continue;
                }
                byte[] incoming = e.getValue();
                byte[] existing = out.get(path);
                if (existing == null) {
                    out.put(path, incoming);
                    owner.put(path, layer.name());
                    continue;
                }
                if (Arrays.equals(existing, incoming)) {
                    continue;
                }
                byte[] merged = mergeJson(path, existing, incoming, conflicts, owner.get(path), layer.name());
                if (merged != null) {
                    out.put(path, merged);
                } else {
                    conflicts.add(path + " (" + owner.get(path) + " → " + layer.name() + ")");
                    out.put(path, incoming);
                }
                owner.put(path, layer.name());
            }
        }
        return new Result(out, conflicts);
    }

    private static byte[] mergeJson(String path, byte[] a, byte[] b, List<String> conflicts, String ownerA, String ownerB) {
        String listKey = FONT.matcher(path).matches() ? "providers" : ATLAS.matcher(path).matches() ? "sources" : null;
        boolean keyed = KEYED.matcher(path).matches();
        if (listKey == null && !keyed) {
            return null;
        }
        JsonObject ja;
        JsonObject jb;
        try {
            ja = JsonParser.parseString(new String(a, StandardCharsets.UTF_8)).getAsJsonObject();
            jb = JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException ex) {
            return null;
        }
        if (listKey != null) {
            JsonArray list = new JsonArray();
            addAll(list, ja.get(listKey));
            addAll(list, jb.get(listKey));
            JsonObject res = ja.deepCopy();
            res.add(listKey, list);
            return GSON.toJson(res).getBytes(StandardCharsets.UTF_8);
        }
        JsonObject res = ja.deepCopy();
        for (Map.Entry<String, JsonElement> en : jb.entrySet()) {
            if (res.has(en.getKey()) && !res.get(en.getKey()).equals(en.getValue())) {
                conflicts.add(path + "#" + en.getKey() + " (" + ownerA + " → " + ownerB + ")");
            }
            res.add(en.getKey(), en.getValue());
        }
        return GSON.toJson(res).getBytes(StandardCharsets.UTF_8);
    }

    private static void addAll(JsonArray to, JsonElement from) {
        if (from != null && from.isJsonArray()) {
            to.addAll(from.getAsJsonArray());
        }
    }

    /** Lê todos os arquivos de uma pasta como camada (caminhos com "/"). */
    public static Layer readFolder(String name, Path root) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    files.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                }
            }
        }
        return new Layer(name, files);
    }

    public static byte[] json(JsonObject obj) {
        return GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
    }
}
