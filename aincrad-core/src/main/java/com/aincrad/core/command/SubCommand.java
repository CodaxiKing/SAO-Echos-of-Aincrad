package com.aincrad.core.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Um subcomando de {@code /aincrad}. Cada plugin registra os seus no {@link CommandRegistry}:
 * <pre>
 * core.commands().register(plugin, new SubCommand() { ... name() = "andar" ... });
 * </pre>
 */
public interface SubCommand {

    /** Primeira palavra depois de /aincrad (ex.: "andar", "regiao"). Minúsculas. */
    String name();

    /** Permissão necessária (null = qualquer um). */
    String permission();

    /** Uso curto para a ajuda, sem "/aincrad" (ex.: "andar liberar &lt;n&gt;"). */
    String usage();

    Component description();

    /** {@code args} sem o nome do subcomando. */
    void execute(CommandSender sender, String[] args);

    /** Sugestões para o último argumento de {@code args}. */
    default List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}
