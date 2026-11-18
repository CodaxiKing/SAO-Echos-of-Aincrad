package com.aincrad.social;

import com.aincrad.social.config.Messages;
import com.aincrad.social.data.InboxMessage;
import com.aincrad.social.service.PartyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /amigo, /party, /mensagem e /caixa. Fazem o mesmo que o menu SAO (via {@link SocialActions}). */
public final class SocialCommands implements CommandExecutor, TabCompleter {

    private static final List<String> FRIEND_SUBS = List.of("lista", "adicionar", "aceitar", "recusar", "remover", "pedidos", "ir");
    private static final List<String> PARTY_SUBS = List.of("convidar", "aceitar", "recusar", "sair", "expulsar", "lider", "desfazer", "lista", "chat");

    private final AincradSocialPlugin plugin;

    public SocialCommands(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private SocialActions act() {
        return plugin.actions();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg().send(sender, "geral.so_jogador");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "amigo" -> friend(p, args);
            case "party" -> party(p, args);
            case "mensagem" -> message(p, args);
            case "caixa" -> inbox(p, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Resolve o nome do argumento ou avisa que não achou. */
    private UUID target(Player p, String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        UUID id = plugin.names().resolve(args[index]);
        if (id == null) {
            msg().send(p, "geral.jogador_nao_encontrado", Placeholder.unparsed("jogador", args[index]));
        }
        return id;
    }

    private void friend(Player p, String[] args) {
        String sub = args.length == 0 ? "lista" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "lista" -> {
                List<UUID> friends = new ArrayList<>(plugin.friends().friends(p.getUniqueId()));
                if (friends.isEmpty()) {
                    msg().send(p, "amigo.lista_vazia");
                    return;
                }
                List<Component> names = friends.stream()
                        .<Component>map(id -> Component.text(plugin.names().name(id),
                                Names.online(id) != null ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                        .toList();
                msg().send(p, "amigo.lista", Placeholder.unparsed("total", String.valueOf(friends.size())),
                        Placeholder.component("amigos", Component.join(JoinConfiguration.commas(true), names)));
            }
            case "pedidos" -> {
                List<String> from = plugin.friends().incoming(p.getUniqueId()).stream().map(plugin.names()::name).toList();
                if (from.isEmpty()) {
                    msg().send(p, "amigo.sem_pedidos");
                } else {
                    msg().send(p, "amigo.pedidos", Placeholder.unparsed("pedidos", String.join(", ", from)));
                }
            }
            case "adicionar", "add" -> run(p, args, "uso.amigo", id -> act().addFriend(p, id));
            case "aceitar" -> run(p, args, "uso.amigo", id -> act().acceptFriend(p, id));
            case "recusar" -> run(p, args, "uso.amigo", id -> act().declineFriend(p, id));
            case "remover" -> run(p, args, "uso.amigo", id -> act().removeFriend(p, id));
            case "ir" -> run(p, args, "uso.amigo", id -> act().visit(p, id));
            default -> msg().send(p, "uso.amigo");
        }
    }

    private void run(Player p, String[] args, String usage, java.util.function.Consumer<UUID> action) {
        if (args.length < 2) {
            msg().send(p, usage);
            return;
        }
        UUID id = target(p, args, 1);
        if (id != null) {
            action.accept(id);
        }
    }

    private void party(Player p, String[] args) {
        String sub = args.length == 0 ? "lista" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "lista" -> {
                PartyService.Party party = plugin.parties().partyOf(p.getUniqueId());
                if (party == null) {
                    msg().send(p, "party.nao_esta");
                    return;
                }
                List<Component> names = new ArrayList<>();
                for (UUID id : party.members()) {
                    Component c = Component.text(plugin.names().name(id), Names.online(id) != null ? NamedTextColor.GREEN : NamedTextColor.GRAY);
                    if (id.equals(party.leader())) {
                        c = c.append(msg().get("party.lider_tag"));
                    }
                    names.add(c);
                }
                msg().send(p, "party.lista", Placeholder.unparsed("total", String.valueOf(party.size())),
                        Placeholder.unparsed("max", String.valueOf(plugin.parties().maxSize())),
                        Placeholder.component("membros", Component.join(JoinConfiguration.commas(true), names)));
            }
            case "convidar", "invite" -> run(p, args, "uso.party", id -> act().invite(p, id));
            case "aceitar" -> run(p, args, "uso.party", id -> act().acceptInvite(p, id));
            case "recusar" -> run(p, args, "uso.party", id -> act().declineInvite(p, id));
            case "expulsar" -> run(p, args, "uso.party", id -> act().kick(p, id));
            case "lider" -> run(p, args, "uso.party", id -> act().promote(p, id));
            case "sair" -> act().leave(p);
            case "desfazer" -> act().disband(p);
            case "chat" -> {
                if (args.length < 2) {
                    msg().send(p, "uso.party");
                } else {
                    act().partyChat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
            }
            default -> msg().send(p, "uso.party");
        }
    }

    private void message(Player p, String[] args) {
        if (args.length < 1) {
            msg().send(p, "uso.mensagem");
            return;
        }
        UUID id = target(p, args, 0);
        if (id == null) {
            return;
        }
        if (args.length < 2) {
            act().askMessage(p, id);
        } else {
            act().sendMessage(p, id, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        }
    }

    private void inbox(Player p, String[] args) {
        List<InboxMessage> box = plugin.inbox().inbox(p.getUniqueId());
        if (args.length >= 2 && (args[0].equalsIgnoreCase("ler") || args[0].equalsIgnoreCase("apagar"))) {
            int n;
            try {
                n = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                msg().send(p, "uso.caixa");
                return;
            }
            if (n < 1 || n > box.size()) {
                msg().send(p, "mensagem.nao_existe");
                return;
            }
            InboxMessage m = box.get(n - 1);
            if (args[0].equalsIgnoreCase("apagar")) {
                plugin.inbox().delete(p.getUniqueId(), m.id());
                msg().send(p, "mensagem.apagada");
            } else {
                plugin.inbox().markRead(p.getUniqueId(), m.id());
                msg().send(p, "mensagem.recebida", Placeholder.unparsed("jogador", m.fromName()), Placeholder.unparsed("texto", m.text()));
            }
            plugin.saveSoon();
            return;
        }
        if (plugin.openInboxMenu(p)) {
            return;
        }
        if (box.isEmpty()) {
            msg().send(p, "mensagem.caixa_vazia");
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
        for (int i = 0; i < box.size(); i++) {
            InboxMessage m = box.get(i);
            p.sendMessage(msg().get("mensagem.caixa_linha",
                    Placeholder.unparsed("n", String.valueOf(i + 1)),
                    Placeholder.unparsed("jogador", m.fromName()),
                    Placeholder.unparsed("quando", fmt.format(new Date(m.time()))),
                    Placeholder.component("nova", m.read() ? Component.empty() : msg().get("mensagem.nova_tag")),
                    Placeholder.unparsed("texto", m.text())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return switch (cmd) {
                case "amigo" -> filter(FRIEND_SUBS, args[0]);
                case "party" -> filter(PARTY_SUBS, args[0]);
                case "mensagem" -> filter(players(sender), args[0]);
                case "caixa" -> filter(List.of("ler", "apagar"), args[0]);
                default -> List.of();
            };
        }
        if (args.length == 2 && (cmd.equals("amigo") || cmd.equals("party")) && !args[0].equalsIgnoreCase("chat")) {
            return filter(players(sender), args[1]);
        }
        return List.of();
    }

    private static List<String> players(CommandSender sender) {
        List<String> out = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(sender)) {
                out.add(o.getName());
            }
        }
        return out;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
