package com.aincrad.rpg.command;

import com.aincrad.core.AincradCore;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.rpg.RpgService;
import com.aincrad.rpg.RpgSettings;
import com.aincrad.rpg.model.Progress;
import com.aincrad.rpg.model.Stat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /status}: menu com level, XP e os três atributos (clique gasta um ponto).
 * {@code /status <atributo> [qtd]} gasta pelo chat; {@code /status resetar} devolve os pontos.
 */
public final class StatusCommand implements TabExecutor {

    private final RpgService rpg;

    public StatusCommand(RpgService rpg) {
        this.rpg = rpg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = rpg.messages();
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return true;
        }
        if (args.length == 0) {
            open(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("resetar")) {
            if (!p.hasPermission("aincrad.rpg.resetar")) {
                msg.send(p, "geral.sem_permissao");
                return true;
            }
            reset(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("texto")) {
            for (var line : msg.list("status.texto", vars(rpg.progress(p)))) {
                p.sendMessage(line);
            }
            return true;
        }
        Stat s = Stat.parse(args[0]);
        if (s == null) {
            msg.send(p, "status.atributo_invalido");
            return true;
        }
        int n = 1;
        if (args.length > 1) {
            try {
                n = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                msg.send(p, "admin.numero_invalido", Messages.vars("valor", args[1]));
                return true;
            }
        }
        spend(p, s, n);
        return true;
    }

    private void spend(Player p, Stat s, int n) {
        Progress pr = rpg.progress(p);
        int done = 0;
        while (done < n && pr.spend(s)) {
            done++;
        }
        if (done == 0) {
            rpg.messages().send(p, "status.sem_pontos");
            return;
        }
        rpg.applyAttributes(p);
        rpg.messages().send(p, "status.gastou", Messages.vars("qtd", done, "nome", rpg.messages().raw("status." + s.key())));
    }

    private void reset(Player p) {
        Progress pr = rpg.progress(p);
        pr.resetStats();
        rpg.applyAttributes(p);
        rpg.messages().send(p, "status.resetou", Messages.vars("pontos", pr.points()));
    }

    private TagResolver vars(Progress pr) {
        return Messages.vars("level", pr.level(), "atual", pr.xp(), "proximo", rpg.settings().curve.toNext(pr.level()),
                "pontos", pr.points(), "forca", pr.stat(Stat.FORCA), "agilidade", pr.stat(Stat.AGILIDADE),
                "vitalidade", pr.stat(Stat.VITALIDADE), "atributo", "forca|agilidade|vitalidade");
    }

    /** Abre o menu de status (também pelo AincradUI, via API). */
    public void open(Player p) {
        Messages msg = rpg.messages();
        Progress pr = rpg.progress(p);
        RpgSettings s = rpg.settings();
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        String vida = hp == null ? "?" : String.valueOf(Math.round(hp.getValue() * 10) / 10.0);

        Menu menu = Menu.of(msg.get("status.titulo", Messages.vars("level", pr.level()))).rows(3);
        menu.item(Menu.Button.of(ItemBuilder.of(Material.EXPERIENCE_BOTTLE).build(),
                msg.get("status.info_nome", Messages.vars("level", pr.level())), null)
                .lore(msg.list("status.info_lore", Messages.vars("atual", pr.xp(), "proximo", s.curve.toNext(pr.level()),
                        "pontos", pr.points(), "vida", vida))));
        menu.item(stat(p, Stat.FORCA, Material.IRON_SWORD, Messages.vars("v", s.damagePerStr)));
        menu.item(stat(p, Stat.AGILIDADE, Material.FEATHER, Messages.vars("v", s.attackSpeedPerAgi, "v2", s.speedPerAgi)));
        menu.item(stat(p, Stat.VITALIDADE, Material.GOLDEN_APPLE, Messages.vars("v", s.healthPerVit)));
        if (p.hasPermission("aincrad.rpg.resetar")) {
            menu.button(Menu.Button.of(ItemBuilder.of(Material.BARRIER).build(), msg.get("status.resetar"), pl -> {
                reset(pl);
                open(pl);
            }));
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("status.fechar"), pl -> { }).closing());
        AincradCore.get().menus().open(p, menu);
    }

    private Menu.Button stat(Player p, Stat s, Material icon, TagResolver effectVars) {
        Messages msg = rpg.messages();
        int value = rpg.progress(p).stat(s);
        TagResolver v = TagResolver.resolver(Messages.vars("nome", msg.raw("status." + s.key()), "valor", value),
                Placeholder.component("efeito", Messages.parse(msg.raw("status.efeito_" + s.key()), effectVars)));
        List<Component> lore = msg.list("status.atributo_lore", v);
        return Menu.Button.of(ItemBuilder.of(icon).hideAll().build(), msg.get("status.atributo_nome", v), pl -> {
            spend(pl, s, 1);
            open(pl);
        }).lore(lore);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String o : List.of("forca", "agilidade", "vitalidade", "resetar", "texto")) {
            if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add(o);
            }
        }
        return out;
    }
}
