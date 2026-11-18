package com.aincrad.social;

import com.aincrad.social.config.Messages;
import com.aincrad.social.data.InboxMessage;
import com.aincrad.social.service.FriendService;
import com.aincrad.social.service.MessageService;
import com.aincrad.social.service.PartyService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Tudo que um jogador faz em amigos, party e mensagens, com as mensagens para cada lado.
 * Usado pelos comandos e pelo menu SAO, para os dois fazerem exatamente a mesma coisa.
 * Cada método devolve true quando a ação aconteceu.
 */
public final class SocialActions {

    private static final Sound NOTIFY = Sound.sound(Key.key("minecraft:block.note_block.chime"), Sound.Source.MASTER, 0.8f, 1.6f);
    private static final Sound POSITIVE = Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.MASTER, 0.7f, 1.3f);

    private final AincradSocialPlugin plugin;

    public SocialActions(AincradSocialPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private String name(UUID id) {
        return plugin.names().name(id);
    }

    private static TagResolver who(String name) {
        return Placeholder.unparsed("jogador", name);
    }

    /** Botões clicáveis [Aceitar] [Recusar] no chat. */
    private Component buttons(String acceptCmd, String declineCmd) {
        return Component.space()
                .append(msg().get("geral.aceitar")
                        .clickEvent(ClickEvent.runCommand(acceptCmd))
                        .hoverEvent(HoverEvent.showText(msg().get("geral.dica_aceitar"))))
                .append(Component.space())
                .append(msg().get("geral.recusar")
                        .clickEvent(ClickEvent.runCommand(declineCmd))
                        .hoverEvent(HoverEvent.showText(msg().get("geral.dica_recusar"))));
    }

    private void notifyPlayer(Player p, Component line) {
        p.sendMessage(msg().get("prefixo").append(line));
        p.playSound(NOTIFY, Sound.Emitter.self());
    }

    // ------------------------------------------------------------------ amigos

    public boolean addFriend(Player p, UUID target) {
        String tn = name(target);
        FriendService.RequestResult r = plugin.friends().request(p.getUniqueId(), target);
        switch (r) {
            case SENT -> {
                msg().send(p, "amigo.enviado", who(tn));
                Player t = Names.online(target);
                if (t != null) {
                    notifyPlayer(t, msg().get("amigo.recebido", who(p.getName()))
                            .append(buttons("/amigo aceitar " + p.getName(), "/amigo recusar " + p.getName())));
                }
            }
            case ACCEPTED -> friendsMade(p, target);
            case ALREADY_FRIENDS -> msg().send(p, "amigo.ja_amigos");
            case ALREADY_SENT -> msg().send(p, "amigo.ja_enviado", who(tn));
            case SELF -> msg().send(p, "amigo.si_mesmo");
            case LIMIT_SELF -> msg().send(p, "amigo.limite_voce");
            case LIMIT_OTHER -> msg().send(p, "amigo.limite_outro", who(tn));
        }
        plugin.saveSoon();
        return r == FriendService.RequestResult.SENT || r == FriendService.RequestResult.ACCEPTED;
    }

    private void friendsMade(Player p, UUID other) {
        msg().send(p, "amigo.aceito", who(name(other)));
        p.playSound(POSITIVE, Sound.Emitter.self());
        Player o = Names.online(other);
        if (o != null) {
            msg().send(o, "amigo.aceito_outro", who(p.getName()));
            o.playSound(POSITIVE, Sound.Emitter.self());
        }
    }

    public boolean acceptFriend(Player p, UUID from) {
        FriendService.AcceptResult r = plugin.friends().accept(p.getUniqueId(), from);
        String fn = name(from);
        switch (r) {
            case OK -> friendsMade(p, from);
            case NO_REQUEST -> msg().send(p, "amigo.sem_pedido", who(fn));
            case LIMIT_SELF -> msg().send(p, "amigo.limite_voce");
            case LIMIT_OTHER -> msg().send(p, "amigo.limite_outro", who(fn));
        }
        plugin.saveSoon();
        return r == FriendService.AcceptResult.OK;
    }

    public boolean declineFriend(Player p, UUID from) {
        boolean ok = plugin.friends().decline(p.getUniqueId(), from);
        msg().send(p, ok ? "amigo.recusado" : "amigo.sem_pedido", who(name(from)));
        plugin.saveSoon();
        return ok;
    }

    /**
     * Vai até um amigo. Só dentro de zona segura: os dois precisam estar numa cidade (com o
     * AincradFloors), neste servidor, e fora de duelo.
     */
    public boolean visit(Player p, UUID other) {
        Player target = Names.online(other);
        String name = plugin.names().name(other);
        if (!plugin.friends().areFriends(p.getUniqueId(), other)) {
            plugin.messages().send(p, "amigo.nao_e_amigo", Placeholder.unparsed("jogador", name));
            return false;
        }
        if (target == null) {
            plugin.messages().send(p, "amigo.ir_offline", Placeholder.unparsed("jogador", name));
            return false;
        }
        if (plugin.duels().service().duelOf(p.getUniqueId()) != null
                || !plugin.hooks().safeZone(p.getLocation()) || !plugin.hooks().safeZone(target.getLocation())) {
            plugin.messages().send(p, "amigo.ir_fora_da_cidade", Placeholder.unparsed("jogador", name));
            return false;
        }
        p.teleportAsync(target.getLocation());
        plugin.messages().send(p, "amigo.ir_foi", Placeholder.unparsed("jogador", name));
        plugin.messages().send(target, "amigo.ir_chegou", Placeholder.unparsed("jogador", p.getName()));
        return true;
    }

    public boolean removeFriend(Player p, UUID other) {
        boolean ok = plugin.friends().remove(p.getUniqueId(), other);
        msg().send(p, ok ? "amigo.removido" : "amigo.nao_amigo", who(name(other)));
        plugin.saveSoon();
        return ok;
    }

    // ------------------------------------------------------------------ party

    private void tellParty(List<UUID> members, UUID except, String key, TagResolver... tags) {
        for (UUID m : members) {
            Player o = Names.online(m);
            if (o != null && !m.equals(except)) {
                msg().send(o, key, tags);
            }
        }
    }

    public boolean invite(Player p, UUID target) {
        String tn = name(target);
        PartyService.InviteResult r = plugin.parties().invite(p.getUniqueId(), target);
        switch (r) {
            case SENT -> {
                Player t = Names.online(target);
                if (t == null && plugin.partyNet() != null && plugin.partyNet().onlineElsewhere(target)) {
                    // Está em outro servidor da rede: o convite vai até lá.
                    plugin.partyNet().sendInvite(p, target);
                    msg().send(p, "party.convite_enviado", who(tn));
                    return true;
                }
                if (t == null) {
                    plugin.parties().decline(target, p.getUniqueId());
                    msg().send(p, "geral.jogador_nao_encontrado", who(tn));
                    return false;
                }
                msg().send(p, "party.convite_enviado", who(tn));
                notifyPlayer(t, msg().get("party.convite_recebido", who(p.getName()),
                                Placeholder.unparsed("segundos", String.valueOf(plugin.settings().inviteSeconds)))
                        .append(buttons("/party aceitar " + p.getName(), "/party recusar " + p.getName())));
            }
            case SELF -> msg().send(p, "party.si_mesmo");
            case NOT_LEADER -> msg().send(p, "party.nao_lider");
            case ALREADY_IN_YOUR_PARTY -> msg().send(p, "party.ja_na_sua", who(tn));
            case TARGET_IN_PARTY -> msg().send(p, "party.alvo_em_party", who(tn));
            case FULL -> msg().send(p, "party.cheia", Placeholder.unparsed("max", String.valueOf(plugin.parties().maxSize())));
            case ALREADY_INVITED -> msg().send(p, "party.ja_convidado", who(tn));
        }
        return r == PartyService.InviteResult.SENT;
    }

    /** Mostra a um jogador deste servidor o convite que veio de outro. */
    public void showInvite(Player t, String fromName) {
        notifyPlayer(t, msg().get("party.convite_recebido", who(fromName),
                        Placeholder.unparsed("segundos", String.valueOf(plugin.settings().inviteSeconds)))
                .append(buttons("/party aceitar " + fromName, "/party recusar " + fromName)));
    }

    public boolean acceptInvite(Player p, UUID from) {
        String fn = name(from);
        PartyService.JoinResult r = plugin.parties().accept(p.getUniqueId(), from);
        switch (r) {
            case JOINED -> {
                msg().send(p, "party.voce_entrou", who(fn));
                p.playSound(POSITIVE, Sound.Emitter.self());
                tellParty(plugin.parties().partyOf(p.getUniqueId()).members(), p.getUniqueId(), "party.entrou", who(p.getName()));
            }
            case NO_INVITE -> msg().send(p, "party.sem_convite", who(fn));
            case EXPIRED -> msg().send(p, "party.expirado", who(fn));
            case ALREADY_IN_PARTY -> msg().send(p, "party.ja_em_party");
            case FULL -> msg().send(p, "party.cheia", Placeholder.unparsed("max", String.valueOf(plugin.parties().maxSize())));
        }
        return r == PartyService.JoinResult.JOINED;
    }

    public boolean declineInvite(Player p, UUID from) {
        boolean ok = plugin.parties().decline(p.getUniqueId(), from);
        msg().send(p, ok ? "party.recusado" : "party.sem_convite", who(name(from)));
        Player f = Names.online(from);
        if (ok && f != null) {
            msg().send(f, "party.recusou", who(p.getName()));
        }
        return ok;
    }

    public boolean leave(Player p) {
        return leave(p.getUniqueId(), p.getName(), true);
    }

    /** Também usado quando o jogador sai do servidor ({@code tellSelf} false). */
    public boolean leave(UUID id, String playerName, boolean tellSelf) {
        PartyService.Leave l = plugin.parties().leave(id);
        Player self = Names.online(id);
        switch (l.kind()) {
            case NOT_IN_PARTY -> {
                if (tellSelf && self != null) {
                    msg().send(self, "party.nao_esta");
                }
                return false;
            }
            case LEFT -> {
                tellParty(l.remaining(), null, "party.saiu", who(playerName));
                if (l.newLeader() != null) {
                    tellParty(l.remaining(), null, "party.novo_lider", who(name(l.newLeader())));
                }
            }
            case DISBANDED -> tellParty(l.remaining(), null, "party.desfeita");
        }
        if (tellSelf && self != null) {
            msg().send(self, "party.voce_saiu");
        }
        return true;
    }

    public boolean kick(Player p, UUID target) {
        String tn = name(target);
        PartyService.Party party = plugin.parties().partyOf(p.getUniqueId());
        List<UUID> before = party == null ? List.of() : party.members();
        PartyService.KickResult r = plugin.parties().kick(p.getUniqueId(), target);
        switch (r) {
            case KICKED -> {
                tellParty(before, target, "party.expulso", who(tn));
                Player t = Names.online(target);
                if (t != null) {
                    msg().send(t, "party.voce_expulso");
                }
                if (plugin.parties().partyOf(p.getUniqueId()) == null) {
                    tellParty(before, target, "party.desfeita");
                }
            }
            case NOT_IN_PARTY -> msg().send(p, "party.nao_esta");
            case NOT_LEADER -> msg().send(p, "party.nao_lider");
            case NOT_MEMBER -> msg().send(p, "party.nao_membro", who(tn));
            case SELF -> msg().send(p, "party.si_mesmo");
        }
        return r == PartyService.KickResult.KICKED;
    }

    public boolean promote(Player p, UUID target) {
        if (!plugin.parties().promote(p.getUniqueId(), target)) {
            msg().send(p, plugin.parties().partyOf(p.getUniqueId()) == null ? "party.nao_esta" : "party.nao_lider");
            return false;
        }
        tellParty(plugin.parties().partyOf(p.getUniqueId()).members(), null, "party.novo_lider", who(name(target)));
        return true;
    }

    public boolean disband(Player p) {
        List<UUID> members = plugin.parties().disband(p.getUniqueId());
        if (members.isEmpty()) {
            msg().send(p, plugin.parties().partyOf(p.getUniqueId()) == null ? "party.nao_esta" : "party.nao_lider");
            return false;
        }
        tellParty(members, null, "party.desfeita");
        return true;
    }

    public void partyChat(Player p, String text) {
        PartyService.Party party = plugin.parties().partyOf(p.getUniqueId());
        if (party == null) {
            msg().send(p, "party.nao_esta");
            return;
        }
        Component line = msg().get("party.chat", who(p.getName()), Placeholder.unparsed("mensagem", MessageService.clean(text)));
        for (UUID m : party.members()) {
            Player o = Names.online(m);
            if (o != null) {
                o.sendMessage(line);
            }
        }
        if (plugin.partyNet() != null) {
            plugin.partyNet().chat(party.id(), line);
        }
        plugin.getLogger().info("[Party] " + p.getName() + ": " + text);
    }

    // ------------------------------------------------------------------ mensagens

    public boolean sendMessage(Player p, UUID to, String text) {
        String tn = name(to);
        String clean = MessageService.clean(text);
        if (clean.isEmpty()) {
            msg().send(p, "mensagem.vazia");
            return false;
        }
        if (plugin.settings().messagesFriendsOnly && !plugin.friends().areFriends(p.getUniqueId(), to)) {
            msg().send(p, "mensagem.so_amigos");
            return false;
        }
        InboxMessage m = plugin.inbox().send(p.getUniqueId(), p.getName(), to, clean);
        msg().send(p, "mensagem.enviada", who(tn));
        Player t = Names.online(to);
        if (t != null) {
            notifyPlayer(t, msg().get("mensagem.recebida", who(p.getName()), Placeholder.unparsed("texto", m.text()))
                    .append(Component.space())
                    .append(msg().get("geral.ler")
                            .clickEvent(ClickEvent.runCommand("/caixa"))
                            .hoverEvent(HoverEvent.showText(msg().get("geral.dica_ler")))));
        }
        plugin.saveSoon();
        return true;
    }

    /** Fecha o menu (se houver) e espera o texto da mensagem no chat. */
    public void askMessage(Player p, UUID to) {
        plugin.menu().close(p);
        msg().send(p, "mensagem.digite", who(name(to)));
        plugin.chatInput().ask(p, text -> sendMessage(p, to, text));
    }

    /** Fecha o menu (se houver) e espera um nome no chat para mandar pedido de amizade. */
    public void askFriendName(Player p) {
        plugin.menu().close(p);
        msg().send(p, "amigo.digite_nome");
        plugin.chatInput().ask(p, name -> {
            UUID id = plugin.names().resolve(name);
            if (id == null) {
                msg().send(p, "geral.jogador_nao_encontrado", who(name));
                return;
            }
            addFriend(p, id);
        });
    }
}
