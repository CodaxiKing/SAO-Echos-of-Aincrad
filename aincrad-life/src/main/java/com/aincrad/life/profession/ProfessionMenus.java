package com.aincrad.life.profession;

import com.aincrad.core.AincradCore;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.model.TreeDef;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Os menus das profissões, pelo construtor de menus do core (baú no Java, formulário no Bedrock):
 * {@code /profissoes} com as barras e o rank, o que o jogador conhece (o resto aparece como "???")
 * e a árvore de especialização, uma página por nível.
 */
public final class ProfessionMenus {

    private static final Material[] ICONS = {Material.CAKE, Material.FISHING_ROD, Material.ANVIL, Material.BREWING_STAND};

    private final ProfessionService service;
    private final Messages msg;

    public ProfessionMenus(ProfessionService service) {
        this.service = service;
        this.msg = service.messages();
    }

    private static ItemStack icon(Material m) {
        return ItemBuilder.of(m).hideAll().build();
    }

    private Menu.Button back(Consumer<Player> to) {
        return Menu.Button.of(icon(Material.ARROW), msg.get("menu.voltar"), to);
    }

    private Menu.Button close() {
        return Menu.Button.of(icon(Material.OAK_DOOR), msg.get("menu.fechar"), pl -> { }).closing();
    }

    /** Barra de 20 quadrados: "■■■■■□□□". */
    static String bar(double fraction) {
        int full = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 20);
        return "<green>" + "■".repeat(full) + "<dark_gray>" + "■".repeat(20 - full);
    }

    // ------------------------------------------------------------------ /profissoes

    public void open(Player p) {
        Menu menu = Menu.of(msg.get("menu.titulo")).rows(3).floating();
        for (Profession prof : Profession.values()) {
            double value = service.proficiency(p, prof);
            Profession.Rank rank = Profession.Rank.of(value);
            Profession.Rank next = rank.next();
            List<Component> lore = new ArrayList<>();
            lore.add(Messages.parse(bar(value / Profession.MAX)));
            lore.add(msg.get("menu.proficiencia", Messages.vars("valor", (int) value, "rank", msg.raw("rank." + rank.key()))));
            lore.add(next == null ? msg.get("menu.rank_maximo")
                    : msg.get("menu.proximo", Messages.vars("rank", msg.raw("rank." + next.key()), "falta", next.from() - (int) value)));
            lore.add(msg.get("menu.pontos", Messages.vars("livres", service.freePoints(p, prof))));
            lore.add(msg.get("menu.clique"));
            menu.item(Menu.Button.of(icon(ICONS[prof.ordinal()]), msg.get("menu.profissao", Messages.vars("profissao", service.name(prof))),
                    pl -> profession(pl, prof)).lore(lore));
        }
        menu.button(close());
        AincradCore.get().menus().open(p, menu);
    }

    private void profession(Player p, Profession prof) {
        Menu menu = Menu.of(msg.get("menu.profissao", Messages.vars("profissao", service.name(prof)))).rows(3).floating();
        menu.item(Menu.Button.of(icon(Material.KNOWLEDGE_BOOK), msg.get("menu.conhecimentos"), pl -> knowledge(pl, prof)));
        menu.item(Menu.Button.of(icon(Material.OAK_SAPLING), msg.get("menu.arvore"), pl -> tree(pl, prof)));
        menu.button(back(this::open));
        AincradCore.get().menus().open(p, menu);
    }

    /** O que existe para aprender nesta profissão: o que o jogador não conhece aparece como "???". */
    private void knowledge(Player p, Profession prof) {
        Menu menu = Menu.of(msg.get("menu.conhecimentos")).rows(6).floating();
        double value = service.proficiency(p, prof);
        boolean any = false;
        for (Learnable l : service.learnables()) {
            if (l.profession() != prof) {
                continue;
            }
            any = true;
            var vars = Messages.vars("nome", l.name(), "categoria", l.category(), "estudo", l.studyMin(), "producao", l.produceMin());
            if (!service.knows(p, l)) {
                menu.item(Menu.Button.of(icon(Material.GRAY_DYE), msg.get("menu.desconhecido", vars), null)
                        .lore(List.of(msg.get("menu.desconhecido_lore", vars))));
                continue;
            }
            boolean can = value >= l.produceMin();
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get(can ? "menu.sabe_fazer" : "menu.proficiencia_insuficiente", vars));
            if (!l.unique() && Profession.Rank.of(value).ordinal() >= Profession.Rank.MESTRE.ordinal()) {
                lore.add(msg.get("menu.transcrever"));
            }
            menu.item(Menu.Button.of(icon(can ? Material.ENCHANTED_BOOK : Material.BOOK), msg.get("menu.conhecido", vars), pl -> {
                if (!l.unique() && service.rank(pl, prof).ordinal() >= Profession.Rank.MESTRE.ordinal()) {
                    service.transcribe(pl, l);
                }
            }).lore(lore));
        }
        if (!any) {
            menu.item(Menu.Button.of(icon(Material.PAPER), msg.get("menu.nada_para_aprender"), null));
        }
        menu.button(back(pl -> profession(pl, prof)));
        AincradCore.get().menus().open(p, menu);
    }

    // ------------------------------------------------------------------ árvore

    /** Página inicial da árvore: tronco comum, a escolha do caminho e a da especialização. */
    public void tree(Player p, Profession prof) {
        TreeDef tree = service.tree(prof);
        Set<String> owned = service.owned(p, prof);
        double value = service.proficiency(p, prof);
        String path = TreeDef.pathOf(owned);
        String spec = TreeDef.specOf(owned);
        Menu menu = Menu.of(msg.get("arvore.titulo", Messages.vars("profissao", service.name(prof),
                "livres", service.freePoints(p, prof)))).rows(6);
        menu.floating();
        Consumer<Player> here = pl -> tree(pl, prof);

        tree.level(null, null).forEach(n -> menu.item(node(p, prof, n, here)));

        for (TreeDef.Path pa : tree.paths()) {
            boolean mine = pa.id().equals(path);
            boolean closedPath = path != null && !mine;
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("arvore.exclusivo", Messages.vars("texto", pa.exclusive())));
            lore.add(msg.get("arvore.especializacoes", Messages.vars("lista", String.join(", ", pa.specs().values()))));
            lore.add(msg.get(mine ? "arvore.seu_caminho" : closedPath ? "arvore.fechado" : value < TreeDef.PATH_AT ? "arvore.caminho_aos" : "arvore.escolher"));
            Material m = mine ? Material.ORANGE_BANNER : closedPath ? Material.BARRIER : value < TreeDef.PATH_AT ? Material.IRON_BARS : Material.WHITE_BANNER;
            menu.item(Menu.Button.of(icon(m), msg.get("arvore.caminho", Messages.vars("nome", pa.name())), pl -> {
                if (mine) {
                    branch(pl, prof, pa.id(), null);
                } else if (path == null) {
                    confirmPath(pl, prof, pa);
                }
            }).lore(lore));
        }
        if (path != null) {
            TreeDef.Path pa = tree.path(path);
            pa.specs().forEach((id, name) -> {
                boolean mine = id.equals(spec);
                boolean closedSpec = spec != null && !mine;
                Material m = mine ? Material.ORANGE_DYE : closedSpec ? Material.BARRIER : value < TreeDef.SPEC_AT ? Material.IRON_BARS : Material.WHITE_DYE;
                menu.item(Menu.Button.of(icon(m), msg.get("arvore.especializacao", Messages.vars("nome", name)), pl -> {
                    if (mine) {
                        branch(pl, prof, path, id);
                    } else if (spec == null) {
                        confirmSpec(pl, prof, id, name);
                    }
                }).lore(List.of(msg.get(mine ? "arvore.sua_especializacao" : closedSpec ? "arvore.fechado"
                        : value < TreeDef.SPEC_AT ? "arvore.especializacao_aos" : "arvore.escolher"))));
            });
        }
        menu.button(back(pl -> profession(pl, prof)));
        AincradCore.get().menus().open(p, menu);
    }

    /** Os nós de um caminho (spec null) ou de uma especialização. */
    private void branch(Player p, Profession prof, String path, String spec) {
        TreeDef tree = service.tree(prof);
        TreeDef.Path pa = tree.path(path);
        String name = spec == null ? pa.name() : pa.specs().get(spec);
        Menu menu = Menu.of(msg.get("arvore.ramo", Messages.vars("nome", name, "livres", service.freePoints(p, prof)))).rows(4).floating();
        Consumer<Player> here = pl -> branch(pl, prof, path, spec);
        tree.level(path, spec).forEach(n -> menu.item(node(p, prof, n, here)));
        menu.button(back(pl -> tree(pl, prof)));
        AincradCore.get().menus().open(p, menu);
    }

    /** Um nó, no estado certo: comprado (laranja), disponível, bloqueado (cadeado) ou de caminho fechado. */
    private Menu.Button node(Player p, Profession prof, TreeDef.Node n, Consumer<Player> reopen) {
        TreeDef tree = service.tree(prof);
        Set<String> owned = service.owned(p, prof);
        TreeDef.Deny state = tree.canBuy(n.id(), service.proficiency(p, prof), owned);
        Material m = switch (state) {
            case JA_TEM -> Material.ORANGE_STAINED_GLASS_PANE;
            case OK -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case CAMINHO_FECHADO -> Material.BARRIER;
            default -> Material.IRON_BARS;
        };
        List<Component> lore = new ArrayList<>();
        if (!n.description().isBlank()) {
            lore.add(Messages.parse("<gray>" + n.description()));
        }
        lore.add(msg.get("arvore.no_custo", Messages.vars("custo", n.cost(), "proficiencia", n.proficiency(),
                "tipo", msg.raw("arvore.tipo." + n.kind()))));
        lore.add(state == TreeDef.Deny.OK ? msg.get("arvore.comprar") : state == TreeDef.Deny.JA_TEM ? msg.get("arvore.comprado")
                : service.deny(state));
        return Menu.Button.of(icon(m), msg.get("arvore.no", Messages.vars("nome", n.name())), pl -> {
            if (service.tree(prof).canBuy(n.id(), service.proficiency(pl, prof), service.owned(pl, prof)) != TreeDef.Deny.OK) {
                reopen.accept(pl);
                return;
            }
            AincradCore.get().menus().confirm(pl, msg.get("arvore.confirmar_no", Messages.vars("nome", n.name(), "custo", n.cost())), yes -> {
                TreeDef.Deny d = service.buyNode(yes, prof, n.id());
                if (d == TreeDef.Deny.OK) {
                    msg.send(yes, "arvore.no_comprado", Messages.vars("nome", n.name()));
                } else {
                    yes.sendMessage(service.deny(d));
                }
                reopen.accept(yes);
            }, reopen);
        }).lore(lore);
    }

    /** Escolha sem volta: duas confirmações, com o aviso do que será fechado. */
    private void confirmPath(Player p, Profession prof, TreeDef.Path pa) {
        TreeDef tree = service.tree(prof);
        TreeDef.Deny d = tree.canChoosePath(pa.id(), service.proficiency(p, prof), service.owned(p, prof));
        if (d != TreeDef.Deny.OK) {
            p.sendMessage(service.deny(d));
            return;
        }
        List<String> others = new ArrayList<>();
        tree.paths().forEach(o -> {
            if (!o.id().equals(pa.id())) {
                others.add(o.name());
            }
        });
        var vars = Messages.vars("nome", pa.name(), "fecha", String.join(", ", others));
        Consumer<Player> cancel = pl -> tree(pl, prof);
        AincradCore.get().menus().confirm(p, msg.get("arvore.confirmar_caminho", vars), first ->
                AincradCore.get().menus().confirm(first, msg.get("arvore.confirmar_de_novo", vars), yes -> {
                    TreeDef.Deny r = service.choosePath(yes, prof, pa.id());
                    if (r == TreeDef.Deny.OK) {
                        msg.send(yes, "arvore.caminho_escolhido", vars);
                    } else {
                        yes.sendMessage(service.deny(r));
                    }
                    tree(yes, prof);
                }, cancel), cancel);
    }

    private void confirmSpec(Player p, Profession prof, String spec, String name) {
        TreeDef tree = service.tree(prof);
        TreeDef.Deny d = tree.canChooseSpec(spec, service.proficiency(p, prof), service.owned(p, prof));
        if (d != TreeDef.Deny.OK) {
            p.sendMessage(service.deny(d));
            return;
        }
        List<String> others = new ArrayList<>();
        tree.path(TreeDef.pathOf(service.owned(p, prof))).specs().forEach((id, n) -> {
            if (!id.equals(spec)) {
                others.add(n);
            }
        });
        var vars = Messages.vars("nome", name, "fecha", String.join(", ", others));
        Consumer<Player> cancel = pl -> tree(pl, prof);
        AincradCore.get().menus().confirm(p, msg.get("arvore.confirmar_caminho", vars), first ->
                AincradCore.get().menus().confirm(first, msg.get("arvore.confirmar_de_novo", vars), yes -> {
                    TreeDef.Deny r = service.chooseSpec(yes, prof, spec);
                    if (r == TreeDef.Deny.OK) {
                        msg.send(yes, "arvore.caminho_escolhido", vars);
                    } else {
                        yes.sendMessage(service.deny(r));
                    }
                    tree(yes, prof);
                }, cancel), cancel);
    }
}
