package com.aincrad.core.menu;

import com.aincrad.core.util.Bedrock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Menus para quem joga no Bedrock (Geyser + Floodgate): um formulário nativo, com um botão por
 * item, no lugar do baú. Usa a API de formulários do Floodgate (Cumulus) por reflexão; se ela
 * não estiver no servidor ou mudar, o jogador cai no menu de baú.
 */
public final class BedrockMenuRenderer implements MenuRenderer {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Plugin core;
    private boolean broken;

    public BedrockMenuRenderer(Plugin core) {
        this.core = core;
    }

    @Override
    public String id() {
        return "bedrock";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean supports(Player player) {
        return !broken && Bedrock.isBedrock(player);
    }

    /** O texto de um botão: o nome e, embaixo, até duas linhas da descrição. */
    static String label(Menu.Button b) {
        StringBuilder sb = new StringBuilder(LEGACY.serialize(b.name()));
        int lines = 0;
        for (Component line : b.lore()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (plain.isEmpty()) {
                continue;
            }
            sb.append('\n').append("§8").append(plain);
            if (++lines == 2) {
                break;
            }
        }
        return sb.toString();
    }

    @Override
    public void open(Player player, Menu menu) {
        List<Menu.Button> all = new ArrayList<>(menu.items());
        all.addAll(menu.buttons());
        List<String> labels = new ArrayList<>();
        for (Menu.Button b : all) {
            labels.add(label(b));
        }
        boolean sent = send(player, LEGACY.serialize(menu.title()), "", labels, index -> {
            if (index < 0 || index >= all.size()) {
                return;
            }
            Menu.Button b = all.get(index);
            if (b.action() != null) {
                b.action().accept(player);
            } else {
                // Linha só de leitura: o formulário fecha ao clicar, então volta ao mesmo menu.
                open(player, menu);
            }
        });
        if (!sent) {
            fallback(player, menu);
        }
    }

    @Override
    public boolean confirm(Player player, Component question, Consumer<Player> yes, Consumer<Player> no) {
        if (!supports(player)) {
            return false;
        }
        return send(player, "", LEGACY.serialize(question), List.of("§2✔", "§4✖"), index -> {
            if (index == 0) {
                yes.accept(player);
            } else if (no != null) {
                no.accept(player);
            }
        });
    }

    private void fallback(Player player, Menu menu) {
        broken = true;
        core.getLogger().warning("Formulários do Floodgate indisponíveis: os jogadores de Bedrock vão ver os menus de baú.");
        com.aincrad.core.AincradCore.get().menus().open(player, menu);
    }

    /**
     * Monta e envia um SimpleForm. A resposta chega fora da thread principal: a ação é passada
     * para ela.
     *
     * @return false se a API não respondeu como esperado
     */
    private boolean send(Player player, String title, String content, List<String> buttons, Consumer<Integer> clicked) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            ClassLoader loader = api.getClass().getClassLoader();
            Class<?> formClass = Class.forName("org.geysermc.cumulus.form.SimpleForm", true, loader);
            Class<?> builderClass = Class.forName("org.geysermc.cumulus.form.SimpleForm$Builder", true, loader);
            Object builder = formClass.getMethod("builder").invoke(null);
            builder = builderClass.getMethod("title", String.class).invoke(builder, title);
            builder = builderClass.getMethod("content", String.class).invoke(builder, content);
            Method button = builderClass.getMethod("button", String.class);
            for (String b : buttons) {
                builder = button.invoke(builder, b);
            }
            Consumer<Object> handler = response -> {
                int index;
                try {
                    Class<?> responseClass = Class.forName("org.geysermc.cumulus.response.SimpleFormResponse", true, loader);
                    index = ((Number) responseClass.getMethod("clickedButtonId").invoke(response)).intValue();
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return;
                }
                core.getServer().getScheduler().runTask(core, () -> {
                    if (player.isOnline()) {
                        clicked.accept(index);
                    }
                });
            };
            builder = builderClass.getMethod("validResultHandler", Consumer.class).invoke(builder, handler);
            Object form = builderClass.getMethod("build").invoke(builder);
            for (Method m : apiClass.getMethods()) {
                if (m.getName().equals("sendForm") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == UUID.class
                        && m.getParameterTypes()[1].isInstance(form)) {
                    m.invoke(api, player.getUniqueId(), form);
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            core.getLogger().log(Level.FINE, "Formulário do Floodgate falhou", e);
            return false;
        }
    }
}
