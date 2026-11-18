package com.aincrad.bosses.command;

import com.aincrad.bosses.data.GuideStore;
import com.aincrad.bosses.fight.FightManager;
import com.aincrad.bosses.model.BossDef;
import com.aincrad.bosses.model.GuideData;
import com.aincrad.core.message.Messages;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /chefe guia <id>}: abre o livro do chefe. Começa em branco; cada fase e cada habilidade
 * só aparece depois de vista em alguma tentativa do servidor.
 */
public final class GuideCommand implements TabExecutor {

    private final FightManager fights;
    private final GuideStore guides;
    private final Messages msg;
    private final com.aincrad.bosses.fight.InformantService informants;

    public GuideCommand(FightManager fights, GuideStore guides, com.aincrad.bosses.fight.InformantService informants) {
        this.fights = fights;
        this.guides = guides;
        this.msg = fights.messages();
        this.informants = informants;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("informante")) {
            informants.shop(p);
            return true;
        }
        if ((sub.equals("comprar") || sub.equals("pagina")) && args.length >= 3) {
            BossDef target = fights.registry().get(args[1]);
            int phase = target == null ? -1 : com.aincrad.bosses.model.InformantRules.phaseIndex(args[2], target.phases().size());
            if (target == null || phase < 0) {
                msg.send(p, "informante.uso");
            } else if (sub.equals("comprar")) {
                informants.buy(p, target, phase);
            } else {
                informants.copy(p, target, phase);
            }
            return true;
        }
        if (args.length < 2 || !sub.equals("guia")) {
            List<String> ids = new ArrayList<>();
            fights.registry().all().forEach(d -> ids.add(d.id()));
            msg.send(p, "guia.uso", Messages.vars("chefes", ids.isEmpty() ? "—" : String.join(", ", ids)));
            return true;
        }
        BossDef def = fights.registry().get(args[1]);
        if (def == null) {
            msg.send(p, "admin.chefe_desconhecido", Messages.vars("id", args[1]));
            return true;
        }
        p.openBook(book(def, guides.get(def.id()), p));
        return true;
    }

    private static Component page(List<Component> lines) {
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** Monta o livro: capa com os números e uma página por fase (as não vistas ficam como "???"). */
    Book book(BossDef def, GuideData g, Player reader) {
        List<Component> pages = new ArrayList<>();
        var boss = Messages.vars("chefe", def.name(), "level", def.level(), "andar", def.floor(),
                "tentativas", g.attempts(), "fase", g.attempts() == 0 ? 0 : g.bestPhase() + 1, "fases", def.phases().size(),
                "vitorias", fights.states().get(def.id()) == null ? 0 : fights.states().get(def.id()).kills());
        pages.add(page(msg.list("guia.capa", boss)));
        for (int i = 0; i < def.phases().size(); i++) {
            BossDef.Phase ph = def.phases().get(i);
            var vars = Messages.vars("n", i + 1, "nome", ph.name().isBlank() ? "—" : ph.name(), "vida", Math.round(ph.at()),
                    "dano", Math.round(ph.damageTimes() * 100), "velocidade", Math.round(ph.speedTimes() * 100));
            var view = informants.view(reader, def, g, i);
            if (view != com.aincrad.bosses.model.InformantRules.View.ABERTA) {
                // Fase que ninguém viu, ou que o jogador ainda não comprou do informante.
                pages.add(page(informants.closedPage(def, i, view)));
                continue;
            }
            List<Component> lines = new ArrayList<>(msg.list("guia.fase", vars));
            for (BossDef.Skill s : ph.skills()) {
                lines.add(g.skillSeen(i, s.type())
                        ? msg.get("guia.habilidade", Messages.vars("texto", msg.raw("guia.tipo." + s.type().name().toLowerCase(Locale.ROOT)),
                                "recarga", Math.round(s.cooldown())))
                        : msg.get("guia.habilidade_oculta"));
            }
            pages.add(page(lines));
        }
        return Book.book(Component.text(def.name()), Component.text("Aincrad"), pages);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("guia", "informante", "comprar", "pagina");
        }
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            for (BossDef d : fights.registry().all()) {
                if (d.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(d.id());
                }
            }
        }
        return out;
    }
}
