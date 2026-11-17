package com.aincrad.core.profile;

import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valores de um jogador, separados por plugin (chave-valor em texto). Carregado ao entrar e
 * gravado ao sair e de tempos em tempos. Ex.: level, andar atual, preferências.
 */
public final class PlayerProfile {

    private final UUID id;
    /** "plugin\u0000chave" → valor */
    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Set<String> removed = ConcurrentHashMap.newKeySet();

    PlayerProfile(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    static String key(String owner, String key) {
        return owner + '\u0000' + key;
    }

    void loadValue(String owner, String key, String value) {
        values.put(key(owner, key), value);
    }

    public String get(Plugin owner, String key) {
        return get(owner.getName(), key);
    }

    public String get(String owner, String key) {
        return values.get(key(owner, key));
    }

    public String get(Plugin owner, String key, String def) {
        String v = get(owner, key);
        return v == null ? def : v;
    }

    public int getInt(Plugin owner, String key, int def) {
        try {
            String v = get(owner, key);
            return v == null ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public long getLong(Plugin owner, String key, long def) {
        try {
            String v = get(owner, key);
            return v == null ? def : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(Plugin owner, String key, boolean def) {
        String v = get(owner, key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    public void set(Plugin owner, String key, Object value) {
        set(owner.getName(), key, value);
    }

    public void set(String owner, String key, Object value) {
        String k = key(owner, key);
        if (value == null) {
            if (values.remove(k) != null) {
                removed.add(k);
                dirty.remove(k);
            }
            return;
        }
        String v = String.valueOf(value);
        if (!v.equals(values.put(k, v))) {
            dirty.add(k);
            removed.remove(k);
        }
    }

    public void remove(Plugin owner, String key) {
        set(owner, key, null);
    }

    /** Todos os valores de um plugin. */
    public Map<String, String> all(Plugin owner) {
        String prefix = owner.getName() + '\u0000';
        Map<String, String> out = new HashMap<>();
        values.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                out.put(k.substring(prefix.length()), v);
            }
        });
        return out;
    }

    public boolean isDirty() {
        return !dirty.isEmpty() || !removed.isEmpty();
    }

    /** Mudanças pendentes, para gravar; limpa as marcas. */
    Changes takeChanges() {
        Map<String, String> upserts = new HashMap<>();
        for (String k : new HashSet<>(dirty)) {
            dirty.remove(k);
            String v = values.get(k);
            if (v != null) {
                upserts.put(k, v);
            }
        }
        Set<String> deletes = new HashSet<>(removed);
        removed.removeAll(deletes);
        return new Changes(upserts, deletes);
    }

    /** Devolve mudanças que não foram gravadas (erro no banco). */
    void restoreChanges(Changes c) {
        dirty.addAll(c.upserts().keySet());
        removed.addAll(c.deletes());
    }

    record Changes(Map<String, String> upserts, Set<String> deletes) {
        boolean empty() {
            return upserts.isEmpty() && deletes.isEmpty();
        }
    }
}
