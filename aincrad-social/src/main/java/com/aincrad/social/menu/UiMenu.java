package com.aincrad.social.menu;

import com.aincrad.social.AincradSocialPlugin;
import com.aincrad.social.Names;
import com.aincrad.social.SocialActions;
import com.aincrad.social.config.Messages;
import com.aincrad.social.data.InboxMessage;
import com.aincrad.social.service.PartyService;
import com.aincrad.ui.api.AincradUIAPI;
import com.aincrad.ui.api.UiList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Amigos, party e mensagens dentro do menu SAO do AincradUI: liga as entradas "party", "amigos",
 * "caixa" e "enviar_mensagem" de menu.yml e mostra os membros da party embaixo da barra de vida.
 * Esta classe só é carregada quando o AincradUI está instalado.
 */
public final class UiMenu implements MenuHook {

    private final AincradSocialPlugin plugin;
    private final AincradUIAPI ui;

    private UiMenu(AincradSocialPlugin plugin, AincradUIAPI ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    /** Liga ao AincradUI, se ele estiver ativo. */
    public static MenuHook connect(AincradSocialPlugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AincradUI")) {
            return MenuHook.NONE;
        }
        AincradUIAPI api = Bukkit.getServicesManager().load(AincradUIAPI.class);
        if (api == null) {
            return MenuHook.NONE;
        }
        UiMenu menu = new UiMenu(plugin, api);
        menu.register();
        plugin.getLogger().info("Ligado ao menu do AincradUI (party, amigos, caixa, escrever).");
        return menu;
    }

    private void register() {
        ui.registerMenuEntry("party", null, this::openParty);
        ui.registerMenuEntry("amigos", null, this::openFriends);
        ui.registerMenuEntry("caixa", null, this::openInbox);
        ui.registerMenuEntry("enviar_mensagem", null, this::openWriteTo);
        GuildUi guilds = new GuildUi(plugin, ui);
        ui.registerMenuEntry("guilda", null, guilds::open);
        // Emblema da guilda antes do nome na barra de vida e no painel de perfil.
        ui.setHudBadgeProvider(plugin.guildManager()::badge);
        ui.setPartyHudProvider(viewer -> {
            PartyService.Party party = plugin.parties().partyOf(viewer.getUniqueId());
            if (party == null) {
                return List.of();
            }
            List<Player> out = new ArrayList<>();
            for (UUID id : party.members()) {
                Player m = Names.online(id);
                if (m != null && !id.equals(viewer.getUniqueId())) {
                    out.add(m);
                }
            }
            return out;
        });
    }

    @Override
    public void close(Player p) {
        if (ui.isMenuOpen(p)) {
            ui.closeMenu(p);
        }
    }

    @Override
    public void shutdown() {
        for (String id : List.of("party", "amigos", "caixa", "enviar_mensagem", "guilda")) {
            ui.unregisterMenuEntry(id);
        }
        ui.setPartyHudProvider(null);
        ui.setHudBadgeProvider(null);
    }

    private Messages msg() {
        return plugin.messages();
    }

    private SocialActions act() {
        return plugin.actions();
    }

    private String name(UUID id) {
        return plugin.names().name(id);
    }

    private Component online(UUID id) {
        return msg().get(Names.online(id) != null ? "menu.online" : "menu.offline");
    }

    private UiList.Button back() {
        return new UiList.Button("buttons/cancel", msg().get("menu.voltar"), ui::back);
    }

    /** Executa a ação e, se der certo, troca a lista atual pela versão atualizada. */
    private void thenRefresh(Player p, boolean ok, Consumer<Player> refresh) {
        if (ok) {
            refresh.accept(p);
        }
    }

    // ------------------------------------------------------------------ party

    private UiList partyList(Player p) {
        PartyService.Party party = plugin.parties().partyOf(p.getUniqueId());
        UiList list = UiList.of(msg().get("menu.party_titulo")).empty(msg().get("menu.party_vazia"));
        boolean leader = party == null || party.leader().equals(p.getUniqueId());
        if (party != null) {
            for (UUID id : party.members()) {
                Player m = Names.online(id);
                Component detail;
                if (id.equals(party.leader())) {
                    detail = msg().get("menu.lider");
                } else if (m != null) {
                    AttributeInstance a = m.getAttribute(Attribute.MAX_HEALTH);
                    detail = msg().get("menu.party_membro_detalhe",
                            Placeholder.unparsed("vida", String.valueOf((int) Math.ceil(m.getHealth()))),
                            Placeholder.unparsed("max", String.valueOf(a == null ? 20 : (int) Math.round(a.getValue()))));
                } else {
                    detail = msg().get("menu.offline");
                }
                list.row(UiList.Row.item(Names.head(id), Component.text(name(id)), detail,
                        id.equals(p.getUniqueId()) ? null : pl -> openMember(pl, id)));
            }
        }
        if (leader) {
            list.button(new UiList.Button("buttons/plus", msg().get("menu.convidar"), this::openInviteChooser));
        }
        list.button(new UiList.Button("buttons/list", msg().get("menu.convites"), this::openInvites));
        if (party != null) {
            list.button(new UiList.Button("buttons/minus", msg().get("menu.sair_party"), pl ->
                    ui.openConfirm(pl, msg().get("menu.confirmar_sair"),
                            p2 -> thenRefresh(p2, act().leave(p2), p3 -> ui.replaceList(p3, partyList(p3))), null)));
        }
        list.button(back());
        return list;
    }

    public void openParty(Player p) {
        ui.openList(p, partyList(p));
    }

    /** Opções sobre um membro: expulsar, tornar líder, mandar mensagem. */
    private void openMember(Player p, UUID member) {
        PartyService.Party party = plugin.parties().partyOf(p.getUniqueId());
        UiList list = UiList.of(Component.text(name(member)));
        if (party != null && party.leader().equals(p.getUniqueId())) {
            list.row(UiList.Row.icon("under_icons/logout", msg().get("menu.expulsar"), null, pl -> {
                if (act().kick(pl, member)) {
                    ui.back(pl);
                    ui.replaceList(pl, partyList(pl));
                }
            }));
            list.row(UiList.Row.icon("under_icons/player", msg().get("menu.tornar_lider"), null, pl -> {
                if (act().promote(pl, member)) {
                    ui.back(pl);
                    ui.replaceList(pl, partyList(pl));
                }
            }));
        }
        list.row(UiList.Row.icon("under_icons/call", msg().get("menu.enviar_mensagem"), null, pl -> act().askMessage(pl, member)));
        duelRow(list, member);
        if (!plugin.friends().areFriends(p.getUniqueId(), member)) {
            list.row(UiList.Row.icon("under_icons/invite", msg().get("menu.adicionar_amigo"), null, pl -> act().addFriend(pl, member)));
        }
        list.button(back());
        ui.openList(p, list);
    }

    /** Jogadores online para convidar (os mais perto primeiro). */
    private void openInviteChooser(Player p) {
        UiList list = UiList.of(msg().get("menu.convidar_titulo")).empty(msg().get("menu.ninguem"));
        for (Player o : nearbyFirst(p)) {
            if (plugin.parties().partyOf(o.getUniqueId()) != null) {
                continue;
            }
            UUID id = o.getUniqueId();
            list.row(UiList.Row.item(Names.head(id), Component.text(o.getName()), distance(p, o), pl -> act().invite(pl, id)));
        }
        list.button(back());
        ui.openList(p, list);
    }

    private List<Player> nearbyFirst(Player p) {
        List<Player> all = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p) && p.canSee(o)) {
                all.add(o);
            }
        }
        all.sort(Comparator.comparingDouble(o -> o.getWorld().equals(p.getWorld())
                ? o.getLocation().distanceSquared(p.getLocation()) : Double.MAX_VALUE));
        return all;
    }

    private Component distance(Player p, Player o) {
        if (!o.getWorld().equals(p.getWorld())) {
            return Component.text(o.getWorld().getName());
        }
        double d = o.getLocation().distance(p.getLocation());
        return d <= plugin.settings().nearbyRadius ? Component.text((int) d + "m") : null;
    }

    private void openInvites(Player p) {
        ui.openList(p, invitesList(p));
    }

    private UiList invitesList(Player p) {
        UiList list = UiList.of(msg().get("menu.convites_titulo")).empty(msg().get("menu.sem_convites"));
        for (UUID from : plugin.parties().invitesFor(p.getUniqueId())) {
            list.row(UiList.Row.item(Names.head(from), Component.text(name(from)), null, pl ->
                    ui.openConfirm(pl, msg().get("menu.convite_de", Placeholder.unparsed("jogador", name(from))),
                            p2 -> {
                                if (act().acceptInvite(p2, from)) {
                                    ui.replaceList(p2, partyList(p2));
                                }
                            },
                            p2 -> {
                                act().declineInvite(p2, from);
                                ui.replaceList(p2, invitesList(p2));
                            })));
        }
        list.button(back());
        return list;
    }

    // ------------------------------------------------------------------ amigos

    private UiList friendsList(Player p) {
        UiList list = UiList.of(msg().get("menu.amigos_titulo")).empty(msg().get("menu.amigos_vazia"));
        List<UUID> friends = new ArrayList<>(plugin.friends().friends(p.getUniqueId()));
        // Online primeiro, depois por nome.
        friends.sort(Comparator.comparing((UUID id) -> Names.online(id) == null).thenComparing(this::name, String.CASE_INSENSITIVE_ORDER));
        for (UUID id : friends) {
            list.row(UiList.Row.item(Names.head(id), Component.text(name(id)), online(id), pl -> openFriend(pl, id)));
        }
        list.button(new UiList.Button("buttons/plus", msg().get("menu.adicionar"), this::openAddFriend));
        int pending = plugin.friends().incoming(p.getUniqueId()).size();
        list.button(new UiList.Button("buttons/list",
                pending > 0 ? msg().get("menu.pedidos").append(Component.text(" (" + pending + ")")) : msg().get("menu.pedidos"),
                this::openRequests));
        list.button(back());
        return list;
    }

    public void openFriends(Player p) {
        ui.openList(p, friendsList(p));
    }

    /** Opções sobre um amigo: convidar para a party, mandar mensagem, remover. */
    private void openFriend(Player p, UUID friend) {
        UiList list = UiList.of(Component.text(name(friend)));
        if (Names.online(friend) != null && plugin.parties().partyOf(friend) == null) {
            list.row(UiList.Row.icon("under_icons/player", msg().get("menu.convidar_party"), null, pl -> act().invite(pl, friend)));
        }
        list.row(UiList.Row.icon("under_icons/call", msg().get("menu.enviar_mensagem"), null, pl -> act().askMessage(pl, friend)));
        duelRow(list, friend);
        if (Names.online(friend) != null) {
            list.row(UiList.Row.icon("under_icons/map", msg().get("menu.ir_ate"), null, pl -> {
                if (act().visit(pl, friend)) {
                    ui.closeMenu(pl);
                }
            }));
        }
        list.row(UiList.Row.icon("under_icons/logout", msg().get("menu.remover_amigo"), null, pl ->
                ui.openConfirm(pl, msg().get("menu.confirmar_remover", Placeholder.unparsed("jogador", name(friend))),
                        p2 -> {
                            if (act().removeFriend(p2, friend)) {
                                ui.back(p2);
                                ui.replaceList(p2, friendsList(p2));
                            }
                        }, null)));
        list.button(back());
        ui.openList(p, list);
    }

    /** "Duelar": só aparece para quem está online; abre a escolha do modo. */
    private void duelRow(UiList list, UUID other) {
        if (Names.online(other) == null || !plugin.settings().duelEnabled) {
            return;
        }
        list.row(UiList.Row.icon("under_icons/player", msg().get("menu.duelar"), null, pl -> {
            UiList modes = UiList.of(msg().get("menu.duelar_titulo", Placeholder.unparsed("jogador", name(other))));
            for (com.aincrad.social.duel.DuelMode m : com.aincrad.social.duel.DuelMode.values()) {
                if (m.lethal() && !plugin.settings().duelAllowTotal) {
                    continue;
                }
                modes.row(UiList.Row.icon("under_icons/player", msg().get("duelo.modo." + m.key()), msg().get("menu.duelo_dica." + m.key()), p2 -> {
                    Player target = Names.online(other);
                    if (target != null) {
                        plugin.duels().challenge(p2, target, m);
                    }
                    ui.closeMenu(p2);
                }));
            }
            modes.button(back());
            ui.openList(pl, modes);
        }));
    }

    private void openAddFriend(Player p) {
        UiList list = UiList.of(msg().get("menu.adicionar_titulo"));
        list.row(UiList.Row.icon("under_icons/help", msg().get("menu.digitar_nome"), null, act()::askFriendName));
        for (Player o : nearbyFirst(p)) {
            UUID id = o.getUniqueId();
            if (plugin.friends().areFriends(p.getUniqueId(), id)) {
                continue;
            }
            list.row(UiList.Row.item(Names.head(id), Component.text(o.getName()), distance(p, o), pl -> act().addFriend(pl, id)));
        }
        list.button(back());
        ui.openList(p, list);
    }

    private UiList requestsList(Player p) {
        UiList list = UiList.of(msg().get("menu.pedidos_titulo")).empty(msg().get("menu.sem_pedidos"));
        for (UUID from : plugin.friends().incoming(p.getUniqueId())) {
            list.row(UiList.Row.item(Names.head(from), Component.text(name(from)), online(from), pl ->
                    ui.openConfirm(pl, msg().get("menu.pedido_de", Placeholder.unparsed("jogador", name(from))),
                            p2 -> {
                                act().acceptFriend(p2, from);
                                ui.replaceList(p2, requestsList(p2));
                            },
                            p2 -> {
                                act().declineFriend(p2, from);
                                ui.replaceList(p2, requestsList(p2));
                            })));
        }
        list.button(back());
        return list;
    }

    private void openRequests(Player p) {
        ui.openList(p, requestsList(p));
    }

    // ------------------------------------------------------------------ mensagens

    private static String when(long time) {
        return new SimpleDateFormat("dd/MM HH:mm").format(new Date(time));
    }

    private UiList inboxList(Player p) {
        UiList list = UiList.of(msg().get("menu.caixa_titulo")).empty(msg().get("menu.caixa_vazia"));
        for (InboxMessage m : plugin.inbox().inbox(p.getUniqueId())) {
            Component detail = m.read() ? Component.text(when(m.time())) : msg().get("menu.nova");
            list.row(UiList.Row.item(Names.head(m.from()), Component.text(m.fromName()), detail, pl -> openMessage(pl, m.id())));
        }
        list.button(back());
        return list;
    }

    @Override
    public boolean openInbox(Player p) {
        ui.openList(p, inboxList(p));
        return true;
    }

    private void openMessage(Player p, UUID id) {
        InboxMessage m = plugin.inbox().find(p.getUniqueId(), id);
        if (m == null) {
            return;
        }
        plugin.inbox().markRead(p.getUniqueId(), id);
        plugin.saveSoon();
        List<Component> lines = new ArrayList<>();
        lines.add(msg().get("menu.mensagem_de", Placeholder.unparsed("jogador", m.fromName())));
        lines.add(msg().get("menu.mensagem_quando", Placeholder.unparsed("quando", when(m.time()))));
        lines.add(Component.empty());
        lines.addAll(wrap(m.text(), 32));
        ui.openWindow(p, msg().get("menu.mensagem_titulo"), lines, List.of(
                new UiList.Button("buttons/start", msg().get("menu.responder"), pl -> act().askMessage(pl, m.from())),
                new UiList.Button("buttons/minus", msg().get("menu.apagar"), pl -> {
                    plugin.inbox().delete(pl.getUniqueId(), id);
                    plugin.saveSoon();
                    ui.back(pl);
                    ui.replaceList(pl, inboxList(pl));
                }),
                back()));
    }

    /** Quebra o texto em linhas para caber na janela. */
    static List<Component> wrap(String text, int width) {
        List<Component> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(Component.text(line.toString()));
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(Component.text(line.toString()));
        }
        return out;
    }

    public void openWriteTo(Player p) {
        UiList list = UiList.of(msg().get("menu.escrever_titulo")).empty(msg().get("menu.sem_amigos_escrever"));
        for (UUID id : plugin.friends().friends(p.getUniqueId())) {
            list.row(UiList.Row.item(Names.head(id), Component.text(name(id)), online(id), pl -> act().askMessage(pl, id)));
        }
        list.button(back());
        ui.openList(p, list);
    }
}
