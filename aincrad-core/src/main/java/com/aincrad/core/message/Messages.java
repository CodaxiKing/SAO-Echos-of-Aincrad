package com.aincrad.core.message;

import com.aincrad.core.config.ConfigFile;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * messages.yml de um plugin, em MiniMessage, com prefixo comum do Aincrad. Um método para cada
 * lugar: chat ({@link #send}), actionbar e título.
 *
 * <pre>
 * Messages msg = AincradCore.get().messages(this);
 * msg.send(player, "andar.liberado", Messages.vars("andar", 5));
 * </pre>
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ConfigFile file;
    private final Messages fallback;

    /**
     * @param fallback mensagens do core, usadas quando a chave não existe no plugin (prefixo,
     *                 textos comuns); pode ser null
     */
    public Messages(Plugin plugin, Messages fallback) {
        this.file = new ConfigFile(plugin, "messages.yml");
        this.fallback = fallback;
    }

    public void reload() {
        file.reload();
    }

    /** Variáveis {@code <nome>} para as mensagens (valores viram texto, sem tags). */
    public static TagResolver vars(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("vars precisa de pares nome, valor");
        }
        List<TagResolver> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = String.valueOf(pairs[i]);
            Object v = pairs[i + 1];
            list.add(v instanceof Component c ? Placeholder.component(key, c) : Placeholder.unparsed(key, String.valueOf(v)));
        }
        return TagResolver.resolver(list);
    }

    public static TagResolver vars(Map<String, ?> map) {
        List<Object> pairs = new ArrayList<>();
        map.forEach((k, v) -> {
            pairs.add(k);
            pairs.add(v);
        });
        return vars(pairs.toArray());
    }

    public String raw(String path) {
        String v = file.yaml().getString(path);
        if (v == null && fallback != null) {
            return fallback.raw(path);
        }
        return v != null ? v : "<red>[" + path + "]";
    }

    public boolean has(String path) {
        return file.yaml().isSet(path) || (fallback != null && fallback.has(path));
    }

    /** Texto pronto (sem itálico, para itens e títulos). */
    public Component get(String path, TagResolver... tags) {
        return parse(raw(path), tags);
    }

    public List<Component> list(String path, TagResolver... tags) {
        List<String> lines = file.yaml().getStringList(path);
        if (lines.isEmpty() && fallback != null) {
            return fallback.list(path, tags);
        }
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) {
            out.add(parse(l, tags));
        }
        return out;
    }

    public Component prefix() {
        return get("prefixo");
    }

    /** No chat, com o prefixo. */
    public void send(Audience to, String path, TagResolver... tags) {
        to.sendMessage(prefix().append(get(path, tags)));
    }

    /** No chat, sem prefixo. */
    public void sendRaw(Audience to, String path, TagResolver... tags) {
        to.sendMessage(get(path, tags));
    }

    public void actionBar(Audience to, String path, TagResolver... tags) {
        to.sendActionBar(get(path, tags));
    }

    /**
     * Título; {@code path} é a chave do título e {@code path + "_sub"} a do subtítulo (se existir).
     */
    public void title(Audience to, String path, Duration stay, TagResolver... tags) {
        Component sub = has(path + "_sub") ? get(path + "_sub", tags) : Component.empty();
        to.showTitle(Title.title(get(path, tags), sub,
                Title.Times.times(Duration.ofMillis(300), stay, Duration.ofMillis(600))));
    }

    public static Component parse(String raw, TagResolver... tags) {
        return MM.deserialize(raw, tags).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
