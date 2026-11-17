package com.aincrad.ui.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Mensagens de messages.yml, em MiniMessage. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final YamlConfiguration cfg;

    public Messages(YamlConfiguration cfg) {
        this.cfg = cfg;
    }

    public String raw(String path) {
        return cfg.getString(path, "<red>[" + path + "]");
    }

    /** Mensagem sem itálico (itens e janelas aplicam itálico por padrão). */
    public Component get(String path, TagResolver... resolvers) {
        return parse(raw(path), resolvers);
    }

    public List<Component> list(String path, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : cfg.getStringList(path)) {
            out.add(parse(line, resolvers));
        }
        return out;
    }

    public void send(CommandSender to, String path, TagResolver... resolvers) {
        to.sendMessage(parse(raw("prefixo")).append(get(path, resolvers)));
    }

    public static Component parse(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
