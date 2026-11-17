package com.aincrad.social.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/** Mensagens de messages.yml, em MiniMessage. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final YamlConfiguration cfg;

    public Messages(YamlConfiguration cfg) {
        this.cfg = cfg;
    }

    public Component get(String path, TagResolver... resolvers) {
        return parse(cfg.getString(path, "<red>[" + path + "]"), resolvers);
    }

    public java.util.List<Component> list(String path, TagResolver... resolvers) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        for (String line : cfg.getStringList(path)) {
            out.add(parse(line, resolvers));
        }
        return out;
    }

    public void send(CommandSender to, String path, TagResolver... resolvers) {
        to.sendMessage(get("prefixo").append(get(path, resolvers)));
    }

    public static Component parse(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
