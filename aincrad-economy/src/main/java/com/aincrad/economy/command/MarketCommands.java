package com.aincrad.economy.command;

import com.aincrad.core.command.SubCommand;
import com.aincrad.core.message.Messages;
import com.aincrad.economy.EcoMath;
import com.aincrad.economy.EconomyService;
import com.aincrad.economy.auction.AuctionService;
import com.aincrad.economy.house.House;
import com.aincrad.economy.house.HouseService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /leilao}, {@code /casa} e {@code /aincrad casa}. */
public final class MarketCommands implements TabExecutor {

    private final EconomyService eco;
    private final AuctionService auction;
    private final HouseService houses;
    private com.aincrad.economy.house.HouseAuctionService houseAuctions;

    public void houseAuctions(com.aincrad.economy.house.HouseAuctionService service) {
        houseAuctions = service;
    }

    public MarketCommands(EconomyService eco, AuctionService auction, HouseService houses) {
        this.eco = eco;
        this.auction = auction;
        this.houses = houses;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = eco.messages();
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return true;
        }
        if (command.getName().equalsIgnoreCase("leilao")) {
            auction(p, args);
        } else {
            house(p, args);
        }
        return true;
    }

    private void auction(Player p, String[] args) {
        Messages msg = eco.messages();
        if (args.length == 0) {
            auction.openMarket(p);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "vender", "anunciar" -> {
                long price = args.length > 1 ? EcoMath.parse(args[1]) : -1;
                if (price <= 0) {
                    msg.send(p, "leilao.uso");
                    return;
                }
                int hours = 0;
                if (args.length > 2) {
                    try {
                        hours = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        msg.send(p, "leilao.uso");
                        return;
                    }
                }
                auction.sell(p, price, hours);
            }
            case "retirar", "caixa" -> auction.openMailbox(p);
            default -> msg.send(p, "leilao.uso");
        }
    }

    private void house(Player p, String[] args) {
        Messages msg = eco.messages();
        String op = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (op.equals("comprar")) {
            houses.buy(p);
            return;
        }
        if ((op.equals("lance") || op.equals("leilao")) && houseAuctions != null) {
            House here = houses.at(p.getLocation());
            if (here == null) {
                msg.send(p, "casa.fora");
            } else if (op.equals("leilao") || args.length < 2) {
                houseAuctions.info(p, here);
            } else {
                long amount = EcoMath.parse(args[1]);
                if (amount <= 0) {
                    msg.send(p, "casa.uso");
                } else {
                    houseAuctions.bid(p, here, amount);
                }
            }
            return;
        }
        if (op.equals("info")) {
            House here = houses.at(p.getLocation());
            if (here != null) {
                info(p, here);
            }
            List<House> mine = houses.ownedBy(p.getUniqueId());
            if (mine.isEmpty() && here == null) {
                msg.send(p, "casa.nenhuma");
            }
            for (House h : mine) {
                if (h != here) {
                    info(p, h);
                }
            }
            return;
        }
        List<House> mine = houses.ownedBy(p.getUniqueId());
        if (mine.isEmpty()) {
            msg.send(p, "casa.nenhuma");
            return;
        }
        // Com várias casas, vale a que o jogador está dentro; senão a primeira.
        House here = houses.at(p.getLocation());
        House h = here != null && mine.contains(here) ? here : mine.get(0);
        switch (op) {
            case "ir" -> {
                Location l = HouseService.spawnLocation(h.spawn());
                if (l == null) {
                    msg.send(p, "casa.sem_spawn");
                    return;
                }
                p.teleportAsync(l);
                msg.send(p, "casa.teleportado", Messages.vars("casa", h.name()));
            }
            case "convidar", "remover" -> {
                if (args.length < 2) {
                    msg.send(p, "casa.uso");
                    return;
                }
                OfflinePlayer t = EconomyService.find(args[1]);
                if (t == null) {
                    msg.send(p, "col.desconhecido", Messages.vars("jogador", args[1]));
                    return;
                }
                if (op.equals("convidar")) {
                    houses.invite(p, h, t);
                } else {
                    houses.uninvite(p, h, t);
                }
            }
            case "definirspawn", "spawn" -> houses.setSpawn(p, h);
            case "vender" -> confirmSell(p, h);
            default -> msg.send(p, "casa.uso");
        }
    }

    private void confirmSell(Player p, House h) {
        long back = (long) Math.floor(h.price() * eco.config().decimal("casas.reembolso", 0.5, 0, 1));
        com.aincrad.core.AincradCore.get().menus().confirm(p,
                eco.messages().get("casa.confirmar_venda", Messages.vars("casa", h.name()), eco.col(back)),
                yes -> {
                    if (yes.getUniqueId().equals(h.owner())) {
                        houses.sell(yes, h);
                    }
                }, no -> { });
    }

    private void info(Player p, House h) {
        Messages msg = eco.messages();
        List<String> names = new ArrayList<>();
        for (var u : h.members()) {
            String n = p.getServer().getOfflinePlayer(u).getName();
            names.add(n == null ? u.toString().substring(0, 8) : n);
        }
        msg.send(p, h.forSale() ? "casa.info_venda" : "casa.info", Messages.vars("casa", h.name(), "andar", h.floor(),
                "dono", String.valueOf(h.ownerName()), "convidados", names.isEmpty() ? "—" : String.join(", ", names)), eco.col(h.price()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> opts = command.getName().equalsIgnoreCase("leilao")
                    ? List.of("vender", "retirar")
                    : List.of("info", "comprar", "lance", "leilao", "ir", "convidar", "remover", "definirspawn", "vender");
            return filter(opts, args[0]);
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("casa")
                && (args[0].equalsIgnoreCase("convidar") || args[0].equalsIgnoreCase("remover"))) {
            return null; // jogadores online
        }
        return List.of();
    }

    private static List<String> filter(List<String> opts, String last) {
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.startsWith(last.toLowerCase(Locale.ROOT))) {
                out.add(o);
            }
        }
        return out;
    }

    /** /aincrad casa criar|remover|despejar|listar. */
    public SubCommand admin() {
        return new SubCommand() {
            @Override public String name() { return "casa"; }
            @Override public String permission() { return "aincrad.economy.admin"; }
            @Override public String usage() { return "casa criar <nome> <preço> [andar], remover|despejar <nome>, listar"; }
            @Override public Component description() { return eco.messages().get("admin.descricao_casa"); }

            @Override
            public void execute(CommandSender sender, String[] args) {
                Messages msg = eco.messages();
                String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                switch (op) {
                    case "criar" -> {
                        if (!(sender instanceof Player p)) {
                            msg.send(sender, "geral.so_jogador");
                            return;
                        }
                        long price = args.length > 2 ? EcoMath.parse(args[2]) : -1;
                        if (price < 0) {
                            msg.send(sender, "admin.uso_casa");
                            return;
                        }
                        int floor = 1;
                        if (args.length > 3) {
                            try {
                                floor = Integer.parseInt(args[3]);
                            } catch (NumberFormatException e) {
                                msg.send(sender, "admin.uso_casa");
                                return;
                            }
                        }
                        houses.create(p, args[1], price, floor);
                    }
                    case "leilao" -> {
                        // /aincrad casa leilao <nome> <lance mínimo> <horas>  |  /aincrad casa leilao <nome> tirar
                        House h = args.length > 2 ? houses.byName(args[1]) : null;
                        if (h == null || houseAuctions == null) {
                            msg.send(sender, "admin.uso_casa");
                            return;
                        }
                        if (args[2].equalsIgnoreCase("tirar")) {
                            houseAuctions.remove(sender, h.name());
                            return;
                        }
                        long min = EcoMath.parse(args[2]);
                        int hours;
                        try {
                            hours = args.length > 3 ? Integer.parseInt(args[3]) : 48;
                        } catch (NumberFormatException e) {
                            hours = -1;
                        }
                        if (min <= 0 || hours < 1 || hours > 24 * 60) {
                            msg.send(sender, "admin.uso_casa");
                            return;
                        }
                        houseAuctions.start(sender, h, min, hours);
                    }
                    case "remover", "despejar" -> {
                        if (args.length < 2) {
                            msg.send(sender, "admin.uso_casa");
                            return;
                        }
                        boolean ok = op.equals("remover") ? houses.delete(args[1]) : houses.evict(args[1]);
                        msg.send(sender, ok ? "admin.casa_feito" : "casa.desconhecida", Messages.vars("casa", args[1]));
                    }
                    case "listar" -> {
                        List<String> out = new ArrayList<>();
                        for (House h : houses.all()) {
                            out.add(h.name() + (h.forSale() ? " (" + houses.priceText(h) + ")" : " [" + h.ownerName() + "]"));
                        }
                        msg.send(sender, "admin.casa_lista", Messages.vars("casas", out.isEmpty() ? "—" : String.join(", ", out)));
                    }
                    default -> msg.send(sender, "admin.uso_casa");
                }
            }

            @Override
            public List<String> complete(CommandSender sender, String[] args) {
                if (args.length == 1) {
                    return filter(List.of("criar", "remover", "despejar", "leilao", "listar"), args[0]);
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("remover") || args[0].equalsIgnoreCase("despejar") || args[0].equalsIgnoreCase("leilao"))) {
                    List<String> names = new ArrayList<>();
                    houses.all().forEach(h -> names.add(h.name()));
                    return filter(names, args[1]);
                }
                return List.of();
            }
        };
    }
}
