package com.aincrad.economy.shop;

import com.aincrad.economy.EconomyService;
import com.aincrad.ui.api.AincradUIAPI;
import com.aincrad.ui.api.FlatApi;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liga as lojas às telas planas do AincradUI. Esta classe só é carregada quando o AincradUI está
 * no servidor (ela usa as classes dele); sem ele, a loja abre como menu de baú.
 */
final class ShopFlat {

    private final FlatApi flat;
    private final EconomyService eco;
    private final ShopService shops;
    private final Map<ShopService.Offer, String> names = new HashMap<>();
    private final Map<ShopService.Offer, String> icons = new HashMap<>();

    private ShopFlat(FlatApi flat, EconomyService eco, ShopService shops) {
        this.flat = flat;
        this.eco = eco;
        this.shops = shops;
    }

    /** Null se o AincradUI não oferece telas planas. */
    static ShopFlat hook(Plugin plugin, EconomyService eco, ShopService shops) {
        AincradUIAPI ui = plugin.getServer().getServicesManager().load(AincradUIAPI.class);
        if (ui == null || ui.flat() == null) {
            return null;
        }
        ShopFlat f = new ShopFlat(ui.flat(), eco, shops);
        f.prepare();
        plugin.getLogger().info("Lojas ligadas às telas planas do AincradUI.");
        return f;
    }

    /** Registra no pack as peças e os ícones de todas as lojas (ao ligar e depois de recarregar). */
    void prepare() {
        names.clear();
        icons.clear();
        for (ShopService.Shop shop : shops.shops()) {
            flat.prepare(new ShopScreen(model(shop)));
        }
    }

    /** Abre a loja na tela plana; false se o jogador não tem o pack (use o baú). */
    boolean open(Player p, ShopService.Shop shop) {
        if (!flat.canShow(p)) {
            return false;
        }
        flat.open(p, new ShopScreen(model(shop)));
        return true;
    }

    private String name(ShopService.Offer o) {
        return names.computeIfAbsent(o, k -> {
            if (k.name() != null) {
                return k.name();
            }
            ItemStack it = shops.create(k, 1);
            return it == null ? k.spec() : PlainTextComponentSerializer.plainText().serialize(shops.name(it));
        });
    }

    private String icon(ShopService.Offer o) {
        return icons.computeIfAbsent(o, k -> {
            if (k.icon() != null) {
                return k.icon().endsWith(".png") ? k.icon().substring(0, k.icon().length() - 4) : k.icon();
            }
            ItemStack it = shops.create(k, 1);
            return flat.itemTexture(it == null ? Material.BARRIER : it.getType());
        });
    }

    private List<ShopService.Offer> offers(ShopService.Shop shop, Player viewer, boolean selling) {
        List<ShopService.Offer> out = new ArrayList<>();
        for (ShopService.Offer o : shop.offers()) {
            if (selling ? o.sell() >= 0 && shops.count(viewer, o) > 0 : o.buy() >= 0) {
                out.add(o);
            }
        }
        return out;
    }

    private ShopScreen.Model model(ShopService.Shop shop) {
        String title = PlainTextComponentSerializer.plainText().serialize(com.aincrad.core.message.Messages.parse(shop.title()));
        return new ShopScreen.Model() {
            @Override
            public String title() {
                return title;
            }

            @Override
            public String label(String key) {
                return eco.messages().raw("loja.tela." + key);
            }

            @Override
            public String money(long amount) {
                return eco.format(amount);
            }

            @Override
            public long balance(Player viewer) {
                return eco.balance(viewer);
            }

            @Override
            public List<ShopScreen.Entry> entries(Player viewer, boolean selling) {
                List<ShopScreen.Entry> out = new ArrayList<>();
                for (ShopService.Offer o : offers(shop, viewer, selling)) {
                    out.add(new ShopScreen.Entry(name(o), icon(o), selling ? o.sell() : o.buy(), o.amount(),
                            selling ? shops.count(viewer, o) : 0));
                }
                return out;
            }

            @Override
            public List<String> icons() {
                Set<String> out = new LinkedHashSet<>();
                shop.offers().forEach(o -> out.add(icon(o)));
                return new ArrayList<>(out);
            }

            @Override
            public void trade(Player viewer, boolean selling, int index, int quantity) {
                List<ShopService.Offer> list = offers(shop, viewer, selling);
                if (index < 0 || index >= list.size() || quantity <= 0) {
                    return;
                }
                if (selling) {
                    shops.sell(viewer, list.get(index), quantity);
                } else {
                    shops.buy(viewer, list.get(index), quantity);
                }
            }

            @Override
            public void close(Player viewer) {
                flat.close(viewer);
            }
        };
    }
}
