package com.aincrad.effects.engine;

import com.aincrad.effects.config.Messages;
import com.aincrad.effects.image.ImageRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monta os textos dos efeitos para cada espectador:
 * 1) troca {variavel} pelos valores de quem chamou; 2) aplica PlaceholderAPI, se instalado;
 * 3) lê como MiniMessage, com a tag &lt;img:id&gt; (imagem do pack, ou texto reserva sem o pack).
 */
public final class TextRenderer {

    private static final Pattern VAR = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private final ImageRegistry images;
    private final Predicate<Player> hasPack;
    private Method papi;

    public TextRenderer(ImageRegistry images, Predicate<Player> hasPack) {
        this.images = images;
        this.hasPack = hasPack;
        if (Bukkit.getServer() != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> c = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                papi = c.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            } catch (ReflectiveOperationException e) {
                papi = null;
            }
        }
    }

    /** Troca {chave}; chaves sem valor ficam como estão (ajuda a achar erro de digitação). */
    public static String substitute(String raw, Map<String, String> vars) {
        if (raw == null || raw.indexOf('{') < 0) {
            return raw;
        }
        Matcher m = VAR.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v != null ? v : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public Component render(String raw, Map<String, String> vars, Player viewer) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        String text = substitute(raw, vars);
        if (papi != null && viewer != null && text.indexOf('%') >= 0) {
            try {
                text = (String) papi.invoke(null, viewer, text);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // PlaceholderAPI falhou: segue com o texto sem placeholders
            }
        }
        boolean pack = viewer != null && hasPack.test(viewer);
        TagResolver img = TagResolver.resolver("img", (args, ctx) -> {
            String id = args.popOr("<img> precisa do id da imagem").value();
            return Tag.selfClosingInserting(images.render(id, pack));
        });
        return Messages.parse(text, img);
    }

    public ImageRegistry images() {
        return images;
    }

    public boolean hasPack(Player p) {
        return hasPack.test(p);
    }
}
