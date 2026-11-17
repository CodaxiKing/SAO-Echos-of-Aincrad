package com.aincrad.social.menu;

import com.aincrad.social.AincradSocialPlugin;
import com.aincrad.social.Names;
import com.aincrad.social.config.Messages;
import com.aincrad.social.guild.Guild;
import com.aincrad.social.guild.GuildManager;
import com.aincrad.social.guild.GuildPerm;
import com.aincrad.social.guild.GuildRank;
import com.aincrad.social.guild.GuildService;
import com.aincrad.ui.api.AincradUIAPI;
import com.aincrad.ui.api.UiList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A guilda no menu SAO (entrada "Guilda" do grupo Party). Sem guilda: criar, convites e guildas
 * abertas. Com guilda: um painel com Membros, Cofre, Tesouro, Candidaturas, Alianças, Ranking,
 * Casa, Uniforme, Opções e Sair. Só é carregada quando o AincradUI está instalado.
 */
final class GuildUi {

    private final AincradSocialPlugin plugin;
    private final AincradUIAPI ui;

    GuildUi(AincradSocialPlugin plugin, AincradUIAPI ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private GuildService svc() {
        return plugin.guilds();
    }

    private GuildManager gm() {
        return plugin.guildManager();
    }

    private String name(UUID id) {
        return plugin.names().name(id);
    }

    private static TagResolver g(Guild g) {
        return TagResolver.resolver(Placeholder.unparsed("guilda", g.name()), Placeholder.unparsed("tag", g.tag()));
    }

    private UiList.Button back() {
        return new UiList.Button("buttons/cancel", msg().get("menu.voltar"), ui::back);
    }

    private UiList.Row row(String icon, String key, Component detail, com.aincrad.ui.api.MenuAction action, TagResolver... tags) {
        return UiList.Row.icon(icon, msg().get("menu_guilda." + key, tags), detail, action);
    }

    /** Fecha o menu e pede um texto no chat (o menu SAO não tem teclado). */
    private void ask(Player p, String key, Consumer<String> then, TagResolver... tags) {
        plugin.menu().close(p);
        msg().send(p, key, tags);
        plugin.chatInput().ask(p, then);
    }

    // ------------------------------------------------------------------ entrada

    void open(Player p) {
        ui.openList(p, root(p));
    }

    private UiList root(Player p) {
        Guild guild = svc().guildOf(p.getUniqueId());
        return guild == null ? noGuild(p) : hub(p, guild);
    }

    private void refresh(Player p) {
        ui.replaceList(p, root(p));
    }

    // ------------------------------------------------------------------ sem guilda

    private UiList noGuild(Player p) {
        UiList list = UiList.of(msg().get("menu_guilda.titulo_sem"));
        String missing = gm().missingRequirement(p);
        Component reqDetail = missing == null ? msg().get("menu_guilda.requisitos_ok")
                : msg().get("menu_guilda.requisitos_faltam");
        list.row(row("under_icons/guild", "criar", reqDetail, this::createFlow));
        for (Guild inv : svc().invitesFor(p.getUniqueId())) {
            list.row(UiList.Row.icon("under_icons/invite", msg().get("menu_guilda.convite_de", g(inv)), null, pl ->
                    ui.openConfirm(pl, msg().get("menu_guilda.aceitar_convite", g(inv)),
                            p2 -> {
                                gm().acceptInvite(p2, inv);
                                refresh(p2);
                            },
                            p2 -> {
                                gm().declineInvite(p2, inv);
                                refresh(p2);
                            })));
        }
        for (Guild open : svc().all()) {
            if (open.recruit() == Guild.Recruit.CONVITE) {
                continue;
            }
            Component detail = msg().get("menu_guilda.membros_detalhe",
                    Placeholder.unparsed("membros", String.valueOf(open.size())),
                    Placeholder.unparsed("vagas", String.valueOf(svc().slots(open))));
            list.row(UiList.Row.icon("under_icons/friends", Component.text("[" + open.tag() + "] " + open.name()), detail,
                    pl -> ui.openConfirm(pl, msg().get(open.recruit() == Guild.Recruit.ABERTA
                                    ? "menu_guilda.entrar_aberta" : "menu_guilda.candidatar", g(open)),
                            p2 -> {
                                gm().apply(p2, open);
                                refresh(p2);
                            }, null)));
        }
        list.row(row("under_icons/dungeon", "ranking", null, this::openRanking));
        list.button(back());
        return list;
    }

    /** Criar: nome no chat, depois a tag. */
    private void createFlow(Player p) {
        String missing = gm().missingRequirement(p);
        if (missing != null) {
            msg().send(p, missing, gm().requirementTags());
            return;
        }
        ask(p, "menu_guilda.digite_nome", name ->
                ask(p, "menu_guilda.digite_tag", tag -> gm().create(p, name, tag)));
    }

    // ------------------------------------------------------------------ painel da guilda

    private UiList hub(Player p, Guild guild) {
        UUID me = p.getUniqueId();
        UiList list = UiList.of(msg().get("menu_guilda.titulo", g(guild),
                Placeholder.unparsed("emblema", guild.emblem()), Placeholder.unparsed("nivel", String.valueOf(guild.level()))));
        list.row(row("under_icons/friends", "membros", msg().get("menu_guilda.membros_detalhe",
                Placeholder.unparsed("membros", String.valueOf(guild.size())),
                Placeholder.unparsed("vagas", String.valueOf(svc().slots(guild)))), pl -> openMembers(pl)));
        if (plugin.guildVault().canOpen(p)) {
            list.row(row("under_icons/items", "cofre", null, pl -> {
                plugin.menu().close(pl);
                plugin.guildVault().open(pl);
            }));
        }
        list.row(row("under_icons/quests", "tesouro", Component.text(guild.treasury() + " Col"), this::openTreasury));
        if (svc().has(me, GuildPerm.CONVIDAR)) {
            list.row(row("under_icons/invite", "convidar", null, this::openInviteChooser));
            int apps = guild.applications().size();
            list.row(row("under_icons/call", "candidaturas", apps > 0 ? Component.text(String.valueOf(apps)) : null, this::openApplications));
        }
        list.row(row("under_icons/guild", "aliancas", Component.text(String.valueOf(guild.allies().size())), this::openAlliances));
        list.row(row("under_icons/dungeon", "ranking", null, this::openRanking));
        if (guild.home() != null) {
            list.row(row("under_icons/map", "casa", null, gm()::home));
        }
        if (guild.level() >= svc().rules().uniformMinLevel()) {
            list.row(row("under_icons/equipment", "uniforme", null, gm()::uniform));
        }
        if (svc().has(me, GuildPerm.EDITAR)) {
            list.row(row("under_icons/options", "opcoes", null, this::openOptions));
        }
        list.row(row("under_icons/logout", guild.rankOf(me) == GuildRank.LIDER ? "desfazer" : "sair", null, pl -> {
            boolean leader = guild.rankOf(pl.getUniqueId()) == GuildRank.LIDER;
            ui.openConfirm(pl, msg().get(leader ? "menu_guilda.confirmar_desfazer" : "menu_guilda.confirmar_sair", g(guild)),
                    p2 -> {
                        if (leader ? gm().disband(p2) : gm().leave(p2)) {
                            refresh(p2);
                        }
                    }, null);
        }));
        list.button(back());
        return list;
    }

    // ------------------------------------------------------------------ membros

    private UiList membersList(Player p) {
        Guild guild = svc().guildOf(p.getUniqueId());
        UiList list = UiList.of(msg().get("menu_guilda.membros_titulo", g(guild)));
        for (GuildRank r : GuildRank.values()) {
            for (UUID m : guild.withRank(r)) {
                Component detail = GuildManager.rankName(msg(), r)
                        .append(Component.text(Names.online(m) != null ? " ●" : ""));
                list.row(UiList.Row.item(Names.head(m), Component.text(name(m)), detail,
                        m.equals(p.getUniqueId()) ? null : pl -> openMember(pl, m)));
            }
        }
        list.button(back());
        return list;
    }

    private void openMembers(Player p) {
        if (svc().guildOf(p.getUniqueId()) != null) {
            ui.openList(p, membersList(p));
        }
    }

    private void afterMember(Player p, boolean ok) {
        if (ok && svc().guildOf(p.getUniqueId()) != null) {
            ui.back(p);
            ui.replaceList(p, membersList(p));
        }
    }

    private void openMember(Player p, UUID m) {
        UUID me = p.getUniqueId();
        Guild guild = svc().guildOf(me);
        if (guild == null || !guild.has(m)) {
            return;
        }
        UiList list = UiList.of(Component.text(name(m)).append(Component.text(" · ")).append(GuildManager.rankName(msg(), guild.rankOf(m))));
        if (svc().has(me, GuildPerm.PROMOVER)) {
            list.row(row("under_icons/invite", "promover", null, pl -> afterMember(pl, gm().promote(pl, m, true))));
            list.row(row("under_icons/options", "rebaixar", null, pl -> afterMember(pl, gm().promote(pl, m, false))));
        }
        if (svc().has(me, GuildPerm.EXPULSAR)) {
            list.row(row("under_icons/logout", "expulsar", null, pl ->
                    ui.openConfirm(pl, msg().get("menu_guilda.confirmar_expulsar", Placeholder.unparsed("jogador", name(m))),
                            p2 -> afterMember(p2, gm().kick(p2, m)), null)));
        }
        if (guild.rankOf(me) == GuildRank.LIDER) {
            list.row(row("under_icons/player", "passar_lideranca", null, pl ->
                    ui.openConfirm(pl, msg().get("menu_guilda.confirmar_lideranca", Placeholder.unparsed("jogador", name(m))),
                            p2 -> afterMember(p2, gm().transfer(p2, m)), null)));
        }
        list.row(row("under_icons/call", "mensagem", null, pl -> plugin.actions().askMessage(pl, m)));
        list.button(back());
        ui.openList(p, list);
    }

    private void openInviteChooser(Player p) {
        UiList list = UiList.of(msg().get("menu_guilda.convidar_titulo")).empty(msg().get("menu.ninguem"));
        for (Player o : p.getServer().getOnlinePlayers()) {
            if (o.equals(p) || svc().guildOf(o.getUniqueId()) != null || !p.canSee(o)) {
                continue;
            }
            UUID id = o.getUniqueId();
            list.row(UiList.Row.item(Names.head(id), Component.text(o.getName()), null, pl -> gm().invite(pl, id)));
        }
        list.button(back());
        ui.openList(p, list);
    }

    private UiList applicationsList(Player p) {
        Guild guild = svc().guildOf(p.getUniqueId());
        UiList list = UiList.of(msg().get("menu_guilda.candidaturas_titulo")).empty(msg().get("guilda.sem_candidaturas"));
        if (guild != null) {
            for (UUID a : guild.applications().keySet()) {
                list.row(UiList.Row.item(Names.head(a), Component.text(name(a)), null, pl ->
                        ui.openConfirm(pl, msg().get("menu_guilda.aceitar_candidatura", Placeholder.unparsed("jogador", name(a))),
                                p2 -> {
                                    gm().answerApplication(p2, a, true);
                                    ui.replaceList(p2, applicationsList(p2));
                                },
                                p2 -> {
                                    gm().answerApplication(p2, a, false);
                                    ui.replaceList(p2, applicationsList(p2));
                                })));
            }
        }
        list.button(back());
        return list;
    }

    private void openApplications(Player p) {
        ui.openList(p, applicationsList(p));
    }

    // ------------------------------------------------------------------ tesouro

    private void openTreasury(Player p) {
        Guild guild = svc().guildOf(p.getUniqueId());
        if (guild == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(msg().get("guilda.tesouro", Placeholder.unparsed("valor", String.valueOf(guild.treasury())),
                Placeholder.unparsed("taxa", String.valueOf(guild.taxPercent()))));
        lines.add(Component.empty());
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
        List<Guild.Ledger> l = guild.ledger();
        for (int i = l.size() - 1; i >= Math.max(0, l.size() - 6); i--) {
            Guild.Ledger e = l.get(i);
            lines.add(msg().get("guilda.extrato_linha", Placeholder.unparsed("quando", fmt.format(new Date(e.time()))),
                    Placeholder.unparsed("jogador", e.whoName()), Placeholder.component("tipo", msg().get("guilda.extrato." + e.type())),
                    Placeholder.unparsed("valor", e.detail().isEmpty() ? String.valueOf(e.amount()) : e.detail())));
        }
        List<UiList.Button> buttons = new ArrayList<>();
        buttons.add(new UiList.Button("buttons/plus", msg().get("menu_guilda.depositar"), pl ->
                ask(pl, "menu_guilda.digite_valor", v -> amount(pl, v, true))));
        if (svc().has(p.getUniqueId(), GuildPerm.TESOURO)) {
            buttons.add(new UiList.Button("buttons/minus", msg().get("menu_guilda.sacar"), pl ->
                    ask(pl, "menu_guilda.digite_valor", v -> amount(pl, v, false))));
        }
        buttons.add(back());
        ui.openWindow(p, msg().get("menu_guilda.tesouro_titulo"), lines, buttons);
    }

    private void amount(Player p, String text, boolean deposit) {
        long v;
        try {
            v = Long.parseLong(text.replace(".", "").replace("Col", "").trim());
        } catch (NumberFormatException e) {
            msg().send(p, "guilda.erro.invalid");
            return;
        }
        if (deposit) {
            gm().deposit(p, v);
        } else {
            gm().withdraw(p, v);
        }
    }

    // ------------------------------------------------------------------ alianças e ranking

    private UiList alliancesList(Player p) {
        Guild guild = svc().guildOf(p.getUniqueId());
        UiList list = UiList.of(msg().get("menu_guilda.aliancas_titulo")).empty(msg().get("menu_guilda.sem_aliancas"));
        if (guild == null) {
            return list;
        }
        boolean can = svc().has(p.getUniqueId(), GuildPerm.ALIANCAS);
        for (UUID id : guild.allies()) {
            Guild a = svc().get(id);
            if (a != null) {
                list.row(UiList.Row.icon("under_icons/guild", Component.text("[" + a.tag() + "] " + a.name()), msg().get("menu_guilda.aliada"),
                        !can ? null : pl -> ui.openConfirm(pl, msg().get("menu_guilda.confirmar_desfazer_alianca", g(a)),
                                p2 -> {
                                    gm().breakAlliance(p2, a);
                                    ui.replaceList(p2, alliancesList(p2));
                                }, null)));
            }
        }
        for (UUID id : guild.allyProposals()) {
            Guild a = svc().get(id);
            if (a != null) {
                list.row(UiList.Row.icon("under_icons/invite", Component.text("[" + a.tag() + "] " + a.name()), msg().get("menu_guilda.proposta"),
                        !can ? null : pl -> ui.openConfirm(pl, msg().get("menu_guilda.aceitar_alianca", g(a)),
                                p2 -> {
                                    gm().ally(p2, a, true);
                                    ui.replaceList(p2, alliancesList(p2));
                                }, null)));
            }
        }
        if (can) {
            list.button(new UiList.Button("buttons/plus", msg().get("menu_guilda.propor_alianca"), this::openProposeAlliance));
        }
        list.button(back());
        return list;
    }

    private void openAlliances(Player p) {
        ui.openList(p, alliancesList(p));
    }

    private void openProposeAlliance(Player p) {
        Guild guild = svc().guildOf(p.getUniqueId());
        UiList list = UiList.of(msg().get("menu_guilda.propor_alianca")).empty(msg().get("menu_guilda.sem_guildas"));
        for (Guild o : svc().all()) {
            if (guild == null || o == guild || guild.allies().contains(o.id())) {
                continue;
            }
            list.row(UiList.Row.icon("under_icons/guild", Component.text("[" + o.tag() + "] " + o.name()),
                    Component.text("Nv " + o.level()), pl -> gm().ally(pl, o, false)));
        }
        list.button(back());
        ui.openList(p, list);
    }

    private void openRanking(Player p) {
        UiList list = UiList.of(msg().get("menu_guilda.ranking_titulo")).empty(msg().get("guilda.ranking_vazio"));
        int pos = 0;
        for (Guild o : svc().ranking()) {
            if (gm().isRed(o)) {
                continue;
            }
            pos++;
            list.row(UiList.Row.icon("under_icons/dungeon", Component.text(pos + ". [" + o.tag() + "] " + o.name()),
                    Component.text(o.bossKills() + " · " + o.lastAttacks() + " · " + o.highestFloor()), null));
        }
        list.button(back());
        ui.openList(p, list);
    }

    // ------------------------------------------------------------------ opções (líder)

    private UiList optionsList(Player p) {
        Guild guild = svc().guildOf(p.getUniqueId());
        UiList list = UiList.of(msg().get("menu_guilda.opcoes_titulo"));
        if (guild == null) {
            return list;
        }
        UUID me = p.getUniqueId();
        Guild.Recruit next = Guild.Recruit.values()[(guild.recruit().ordinal() + 1) % Guild.Recruit.values().length];
        list.row(row("under_icons/invite", "recrutamento",
                msg().get("guilda.recrutamento." + guild.recruit().name().toLowerCase(Locale.ROOT)), pl -> {
                    gm().edit(pl, svc().setRecruit(me, next), "guilda.config_recrutamento",
                            Placeholder.component("modo", msg().get("guilda.recrutamento." + next.name().toLowerCase(Locale.ROOT))));
                    ui.replaceList(pl, optionsList(pl));
                }));
        list.row(row("under_icons/quests", "taxa", Component.text(guild.taxPercent() + "%"), pl ->
                ask(pl, "menu_guilda.digite_taxa", v -> {
                    try {
                        int t = Integer.parseInt(v.replace("%", "").trim());
                        gm().edit(pl, svc().setTax(me, t), "guilda.config_taxa", Placeholder.unparsed("taxa", String.valueOf(t)));
                    } catch (NumberFormatException e) {
                        msg().send(pl, "guilda.erro.invalid");
                    }
                }, Placeholder.unparsed("max", String.valueOf(svc().rules().maxTaxPercent())))));
        list.row(row("under_icons/skills", "emblema", Component.text(guild.emblem()), this::openEmblems));
        list.row(row("under_icons/equipment", "cores", Component.text(guild.color1() + " " + guild.color2()), pl ->
                ask(pl, "menu_guilda.digite_cores", v -> {
                    String[] c = v.trim().split("\\s+");
                    if (c.length != 2) {
                        msg().send(pl, "guilda.erro.invalid");
                        return;
                    }
                    gm().edit(pl, svc().setColors(me, c[0], c[1]), "guilda.config_cores");
                })));
        list.row(row("under_icons/map", "definir_casa", null, pl -> {
            plugin.menu().close(pl);
            gm().setHome(pl);
        }));
        list.button(back());
        return list;
    }

    private void openOptions(Player p) {
        ui.openList(p, optionsList(p));
    }

    private void openEmblems(Player p) {
        UiList list = UiList.of(msg().get("menu_guilda.emblema_titulo"));
        for (String e : svc().rules().emblems()) {
            list.row(UiList.Row.icon("under_icons/skills", Component.text(e), null, pl -> {
                gm().edit(pl, svc().setEmblem(pl.getUniqueId(), e), "guilda.config_emblema", Placeholder.unparsed("emblema", e));
                ui.back(pl);
                ui.replaceList(pl, optionsList(pl));
            }));
        }
        list.button(back());
        ui.openList(p, list);
    }
}
