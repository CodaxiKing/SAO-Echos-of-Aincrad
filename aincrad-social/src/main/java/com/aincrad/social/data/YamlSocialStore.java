package com.aincrad.social.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** {@link SocialStore} gravado em dados.yml (gravação atômica: arquivo temporário e troca). */
public final class YamlSocialStore extends SocialStore {

    private final File file;
    private final Logger log;

    public YamlSocialStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        readSets(c.getConfigurationSection("amigos"), friends);
        readSets(c.getConfigurationSection("pedidos"), requests);
        ConfigurationSection n = c.getConfigurationSection("nomes");
        if (n != null) {
            for (String k : n.getKeys(false)) {
                UUID id = uuid(k);
                if (id != null) {
                    names.put(id, n.getString(k));
                }
            }
        }
        ConfigurationSection in = c.getConfigurationSection("caixa");
        if (in != null) {
            for (String k : in.getKeys(false)) {
                UUID owner = uuid(k);
                if (owner == null) {
                    continue;
                }
                List<InboxMessage> list = new ArrayList<>();
                for (Map<?, ?> m : in.getMapList(k)) {
                    UUID id = uuid(String.valueOf(m.get("id")));
                    UUID from = uuid(String.valueOf(m.get("de")));
                    if (id == null || from == null) {
                        continue;
                    }
                    Object time = m.get("quando");
                    list.add(new InboxMessage(id, from, String.valueOf(m.get("nome")), String.valueOf(m.get("texto")),
                            time instanceof Number num ? num.longValue() : 0L, Boolean.TRUE.equals(m.get("lida"))));
                }
                if (!list.isEmpty()) {
                    inbox.put(owner, list);
                }
            }
        }
        clean();
    }

    private static void readSets(ConfigurationSection sec, Map<UUID, Set<UUID>> into) {
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            UUID key = uuid(k);
            if (key == null) {
                continue;
            }
            Set<UUID> set = new LinkedHashSet<>();
            for (String v : sec.getStringList(k)) {
                UUID id = uuid(v);
                if (id != null) {
                    set.add(id);
                }
            }
            if (!set.isEmpty()) {
                into.put(key, set);
            }
        }
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /** Monta o YAML na thread principal (cópia dos dados) para gravar em outra thread. */
    public String snapshot() {
        YamlConfiguration c = new YamlConfiguration();
        friends.forEach((k, v) -> c.set("amigos." + k, v.stream().map(UUID::toString).toList()));
        requests.forEach((k, v) -> c.set("pedidos." + k, v.stream().map(UUID::toString).toList()));
        names.forEach((k, v) -> c.set("nomes." + k, v));
        inbox.forEach((k, v) -> c.set("caixa." + k, v.stream().map(m -> Map.of(
                "id", m.id().toString(), "de", m.from().toString(), "nome", m.fromName(),
                "texto", m.text(), "quando", m.time(), "lida", m.read())).toList()));
        clean();
        return c.saveToString();
    }

    public void write(String yaml) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), yaml);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.severe("Falha ao gravar " + file.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public void save() {
        write(snapshot());
    }
}
