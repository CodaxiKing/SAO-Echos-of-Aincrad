package com.aincrad.core.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regiões indexadas por mundo e por chunk (16x16), para responder rápido "em que regiões está
 * este ponto" a cada passo dos jogadores. Regiões enormes (andares inteiros) ficam numa lista
 * separada, checada sempre, para não lotar o índice de chunks.
 */
public final class RegionIndex {

    /** Acima disso de chunks, a região vai para a lista de grandes. */
    static final long MAX_INDEXED_CHUNKS = 4096;

    private final Map<String, Region> byName = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<Region>>> chunks = new HashMap<>();
    private final Map<String, List<Region>> large = new HashMap<>();

    public synchronized void put(Region r) {
        remove(r.name());
        byName.put(r.name(), r);
        int cx1 = r.minX() >> 4;
        int cz1 = r.minZ() >> 4;
        int cx2 = r.maxX() >> 4;
        int cz2 = r.maxZ() >> 4;
        long count = (long) (cx2 - cx1 + 1) * (cz2 - cz1 + 1);
        if (count > MAX_INDEXED_CHUNKS) {
            large.computeIfAbsent(r.world(), k -> new ArrayList<>()).add(r);
            return;
        }
        Map<Long, List<Region>> w = chunks.computeIfAbsent(r.world(), k -> new HashMap<>());
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                w.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(r);
            }
        }
    }

    public synchronized Region remove(String name) {
        Region r = byName.remove(name);
        if (r == null) {
            return null;
        }
        List<Region> l = large.get(r.world());
        if (l != null) {
            l.remove(r);
        }
        Map<Long, List<Region>> w = chunks.get(r.world());
        if (w != null) {
            w.values().forEach(list -> list.remove(r));
            w.values().removeIf(List::isEmpty);
        }
        return r;
    }

    public synchronized void clear() {
        byName.clear();
        chunks.clear();
        large.clear();
    }

    public synchronized Region get(String name) {
        return byName.get(name);
    }

    public synchronized Collection<Region> all() {
        return List.copyOf(byName.values());
    }

    /** Regiões que contêm o ponto (a menor primeiro: a mais específica). */
    public synchronized List<Region> at(String world, double x, double y, double z) {
        List<Region> out = new ArrayList<>();
        Map<Long, List<Region>> w = chunks.get(world);
        if (w != null) {
            List<Region> list = w.get(key((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4));
            if (list != null) {
                for (Region r : list) {
                    if (r.contains(world, x, y, z)) {
                        out.add(r);
                    }
                }
            }
        }
        for (Region r : large.getOrDefault(world, Collections.emptyList())) {
            if (r.contains(world, x, y, z)) {
                out.add(r);
            }
        }
        out.sort((a, b) -> Long.compare(a.volume(), b.volume()));
        return out;
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
