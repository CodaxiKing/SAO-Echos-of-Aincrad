package com.aincrad.core.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Tempos de recarga por jogador e por chave ("skill:investida", "comando:casa"...). */
public final class Cooldowns {

    private final Map<String, Long> until = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public Cooldowns() {
        this(System::currentTimeMillis);
    }

    public Cooldowns(LongSupplier clock) {
        this.clock = clock;
    }

    private static String k(UUID p, String key) {
        return p + "|" + key;
    }

    public void set(UUID p, String key, long millis) {
        until.put(k(p, key), clock.getAsLong() + millis);
    }

    /** Quanto falta (0 = pronto). */
    public long remaining(UUID p, String key) {
        Long u = until.get(k(p, key));
        if (u == null) {
            return 0;
        }
        long left = u - clock.getAsLong();
        if (left <= 0) {
            until.remove(k(p, key));
            return 0;
        }
        return left;
    }

    public boolean ready(UUID p, String key) {
        return remaining(p, key) == 0;
    }

    /** Se está pronto, já marca a recarga e devolve true. */
    public boolean tryUse(UUID p, String key, long millis) {
        if (!ready(p, key)) {
            return false;
        }
        set(p, key, millis);
        return true;
    }

    public void clear(UUID p) {
        String prefix = p + "|";
        until.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
