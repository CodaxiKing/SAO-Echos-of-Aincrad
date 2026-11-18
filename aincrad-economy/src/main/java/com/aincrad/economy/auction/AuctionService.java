package com.aincrad.economy.auction;

import com.aincrad.core.AincradCore;
import com.aincrad.core.bus.MessageBus;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.ItemBuilder;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.economy.EconomyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Leilão da rede: anunciar o item da mão com preço e prazo, comprar pelo menu (taxa sai do
 * vendedor), cancelar os próprios anúncios. Vencidos e cancelados vão para a caixa de retirada.
 * Tudo no banco; a compra apaga o anúncio com "DELETE ... WHERE id = ?" e só entrega se apagou
 * (dois compradores ao mesmo tempo, em servidores diferentes: só um leva).
 */
public final class AuctionService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS eco_auctions ({AUTO_ID}, seller VARCHAR(36) NOT NULL, seller_name VARCHAR(16) NOT NULL, "
                    + "item TEXT NOT NULL, price BIGINT NOT NULL, created BIGINT NOT NULL, expires BIGINT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS eco_mailbox ({AUTO_ID}, uuid VARCHAR(36) NOT NULL, item TEXT NOT NULL, "
                    + "reason VARCHAR(32) NOT NULL, created BIGINT NOT NULL);"
                    + "CREATE INDEX eco_auctions_seller ON eco_auctions (seller);"
                    + "CREATE INDEX eco_mailbox_uuid ON eco_mailbox (uuid)");

    /** Aviso de venda: "vendedor|valor recebido|nome do item". */
    public static final String BUS_SOLD = "economy:auction_sold";

    /** Um anúncio. */
    public record Listing(long id, UUID seller, String sellerName, String item, long price, long expires) {
    }

    /** Um item na caixa de retirada. */
    public record Mail(long id, String item, String reason) {
    }

    private final Plugin plugin;
    private final EconomyService eco;
    private final Messages msg;
    private final Database db;

    public AuctionService(Plugin plugin, EconomyService eco, Database db) {
        this.plugin = plugin;
        this.eco = eco;
        this.msg = eco.messages();
        this.db = db;
    }

    // ------------------------------------------------------------------ itens <-> texto

    public static String encode(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    public static ItemStack decode(String s) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Component name(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        Component n = m != null && m.hasDisplayName() ? m.displayName() : null;
        return n != null ? n : Component.translatable(it.translationKey());
    }

    private int maxListings() {
        return eco.config().integer("leilao.max_anuncios", 5, 1, 1000);
    }

    // ------------------------------------------------------------------ anunciar

    /** {@code /leilao vender <preço> [horas]}: anuncia o item da mão. */
    public void sell(Player p, long price, int hours) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg.send(p, "leilao.mao_vazia");
            return;
        }
        long min = eco.config().integer("leilao.preco_minimo", 1, 1, Integer.MAX_VALUE);
        if (price < min) {
            msg.send(p, "leilao.preco_minimo", eco.col(min));
            return;
        }
        int h = AuctionMath.duration(hours, eco.config().integer("leilao.horas_padrao", 24, 1, 720),
                eco.config().integer("leilao.horas_maximo", 72, 1, 720));
        long fee = AuctionMath.listingFee(price, eco.config().decimal("leilao.taxa_anuncio", 0, 0, 1));
        if (!eco.has(p, fee)) {
            msg.send(p, "leilao.sem_saldo_taxa", eco.col(fee));
            return;
        }
        ItemStack item = hand.clone();
        UUID id = p.getUniqueId();
        String name = p.getName();
        long now = System.currentTimeMillis();
        long expires = now + h * 3_600_000L;
        int max = maxListings();
        // Tira da mão já (para não vender o mesmo item duas vezes); devolve se o banco recusar.
        p.getInventory().setItemInMainHand(null);
        db.async(c -> {
            int count = Database.query(c, "SELECT COUNT(*) FROM eco_auctions WHERE seller = ?", rs -> rs.getInt(1), id.toString()).get(0);
            if (count >= max) {
                return false;
            }
            Database.update(c, "INSERT INTO eco_auctions (seller, seller_name, item, price, created, expires) VALUES (?, ?, ?, ?, ?, ?)",
                    id.toString(), name, encode(item), price, now, expires);
            return true;
        }).whenComplete((ok, err) -> AincradCore.get().runSync(() -> {
            if (err != null || !Boolean.TRUE.equals(ok)) {
                giveOrMail(id, item, "devolvido");
                Player online = plugin.getServer().getPlayer(id);
                if (online != null) {
                    msg.send(online, err != null ? "leilao.erro" : "leilao.limite", Messages.vars("max", max));
                }
                return;
            }
            if (fee > 0) {
                eco.withdraw(id, fee, "leilao:anuncio");
            }
            Player online = plugin.getServer().getPlayer(id);
            if (online != null) {
                msg.send(online, "leilao.anunciado", eco.col(price), Messages.vars("horas", h),
                        Placeholder.component("item", name(item)));
            }
        }));
    }

    // ------------------------------------------------------------------ menus

    public void openMarket(Player p) {
        long now = System.currentTimeMillis();
        db.query("SELECT id, seller, seller_name, item, price, expires FROM eco_auctions WHERE expires > ? ORDER BY created DESC LIMIT 270",
                rs -> new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6)), now)
                .whenComplete((list, err) -> AincradCore.get().runSync(() -> {
                    if (err != null) {
                        msg.send(p, "leilao.erro");
                        return;
                    }
                    if (p.isOnline()) {
                        showMarket(p, list);
                    }
                }));
    }

    private void showMarket(Player p, List<Listing> list) {
        Menu menu = Menu.of(msg.get("leilao.titulo")).rows(6);
        menu.floating();
        long now = System.currentTimeMillis();
        for (Listing l : list) {
            ItemStack it = decode(l.item());
            if (it == null) {
                continue;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("leilao.lore_preco", eco.col(l.price())));
            lore.add(msg.get("leilao.lore_vendedor", Messages.vars("jogador", l.sellerName())));
            lore.add(msg.get("leilao.lore_tempo", Messages.vars("tempo", TimeFormat.duration(l.expires() - now))));
            lore.add(msg.get(l.seller().equals(p.getUniqueId()) ? "leilao.lore_seu" : "leilao.lore_comprar"));
            ItemStack icon = it.clone();
            menu.item(Menu.Button.of(icon, name(icon), pl -> {
                if (l.seller().equals(pl.getUniqueId())) {
                    cancel(pl, l.id());
                    return;
                }
                AincradCore.get().menus().confirm(pl, msg.get("leilao.confirmar", eco.col(l.price()),
                        Placeholder.component("item", name(it))), yes -> buy(yes, l), no -> openMarket(no));
            }).lore(lore));
        }
        menu.button(Menu.Button.of(ItemBuilder.of(Material.CHEST).build(), msg.get("leilao.botao_retirar"), this::openMailbox));
        menu.button(Menu.Button.of(ItemBuilder.of(Material.SUNFLOWER).build(), msg.get("leilao.botao_atualizar"), pl -> openMarket(pl)));
        menu.button(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("leilao.fechar"), pl -> { }).closing());
        AincradCore.get().menus().open(p, menu);
    }

    private void buy(Player p, Listing l) {
        if (!eco.has(p, l.price())) {
            msg.send(p, "leilao.sem_saldo", eco.col(l.price()));
            return;
        }
        // Cobra primeiro (na memória, na hora); se o anúncio já sumiu, devolve.
        if (!eco.withdraw(p.getUniqueId(), l.price(), "leilao:compra")) {
            msg.send(p, "leilao.sem_saldo", eco.col(l.price()));
            return;
        }
        UUID buyer = p.getUniqueId();
        long now = System.currentTimeMillis();
        db.update("DELETE FROM eco_auctions WHERE id = ? AND expires > ?", l.id(), now).whenComplete((n, err) -> AincradCore.get().runSync(() -> {
            Player online = plugin.getServer().getPlayer(buyer);
            if (err != null || n == null || n == 0) {
                eco.deposit(buyer, l.price(), "leilao:estorno");
                if (online != null) {
                    msg.send(online, err != null ? "leilao.erro" : "leilao.ja_vendido");
                }
                return;
            }
            ItemStack it = decode(l.item());
            if (it != null) {
                giveOrMail(buyer, it, "compra");
            }
            long got = AuctionMath.proceeds(l.price(), eco.config().decimal("leilao.taxa_venda", 0.05, 0, 1));
            eco.deposit(l.seller(), got, "leilao:venda");
            AincradCore.get().publish(BUS_SOLD, l.seller() + "|" + got + "|"
                    + (it == null ? "?" : PlainTextComponentSerializer.plainText().serialize(name(it))));
            if (online != null) {
                msg.send(online, "leilao.comprou", eco.col(l.price()), Placeholder.component("item", it == null ? Component.text("?") : name(it)));
            }
        }));
    }

    /** Cancela um anúncio próprio: o item vai para a caixa de retirada. */
    public void cancel(Player p, long id) {
        UUID uid = p.getUniqueId();
        long now = System.currentTimeMillis();
        db.async(c -> {
            List<String> items = Database.query(c, "SELECT item FROM eco_auctions WHERE id = ? AND seller = ?", rs -> rs.getString(1), id, uid.toString());
            if (items.isEmpty() || Database.update(c, "DELETE FROM eco_auctions WHERE id = ? AND seller = ?", id, uid.toString()) == 0) {
                return false;
            }
            Database.update(c, "INSERT INTO eco_mailbox (uuid, item, reason, created) VALUES (?, ?, ?, ?)", uid.toString(), items.get(0), "cancelado", now);
            return true;
        }).whenComplete((ok, err) -> AincradCore.get().runSync(() -> {
            Player online = plugin.getServer().getPlayer(uid);
            if (online != null) {
                msg.send(online, Boolean.TRUE.equals(ok) ? "leilao.cancelado" : "leilao.ja_vendido");
            }
        }));
    }

    public void openMailbox(Player p) {
        db.query("SELECT id, item, reason FROM eco_mailbox WHERE uuid = ? ORDER BY id LIMIT 270",
                rs -> new Mail(rs.getLong(1), rs.getString(2), rs.getString(3)), p.getUniqueId().toString())
                .whenComplete((list, err) -> AincradCore.get().runSync(() -> {
                    if (!p.isOnline()) {
                        return;
                    }
                    if (err != null) {
                        msg.send(p, "leilao.erro");
                        return;
                    }
                    Menu menu = Menu.of(msg.get("leilao.retirar_titulo")).rows(6);
                    menu.floating();
                    for (Mail m : list) {
                        ItemStack it = decode(m.item());
                        if (it == null) {
                            continue;
                        }
                        menu.item(Menu.Button.of(it.clone(), name(it), pl -> withdraw(pl, m))
                                .lore(List.of(msg.get("leilao.lore_motivo", Messages.vars("motivo", m.reason())), msg.get("leilao.lore_retirar"))));
                    }
                    if (list.isEmpty()) {
                        menu.item(Menu.Button.of(ItemBuilder.of(Material.BARRIER).build(), msg.get("leilao.caixa_vazia"), null));
                    }
                    menu.button(Menu.Button.of(ItemBuilder.of(Material.ARROW).build(), msg.get("leilao.voltar"), pl -> openMarket(pl)));
                    AincradCore.get().menus().open(p, menu);
                }));
    }

    private void withdraw(Player p, Mail m) {
        UUID uid = p.getUniqueId();
        db.update("DELETE FROM eco_mailbox WHERE id = ? AND uuid = ?", m.id(), uid.toString()).whenComplete((n, err) -> AincradCore.get().runSync(() -> {
            Player online = plugin.getServer().getPlayer(uid);
            if (err != null || n == null || n == 0) {
                if (online != null) {
                    msg.send(online, "leilao.erro");
                }
                return;
            }
            ItemStack it = decode(m.item());
            if (it != null) {
                giveOrMail(uid, it, m.reason());
            }
            if (online != null) {
                openMailbox(online);
            }
        }));
    }

    /** Dá o item a quem está online aqui (o que não couber cai no chão); senão, caixa de retirada. */
    public void giveOrMail(UUID id, ItemStack item, String reason) {
        Player p = plugin.getServer().getPlayer(id);
        if (p != null) {
            for (ItemStack rest : p.getInventory().addItem(item).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
            return;
        }
        db.update("INSERT INTO eco_mailbox (uuid, item, reason, created) VALUES (?, ?, ?, ?)", id.toString(), encode(item), reason, System.currentTimeMillis());
    }

    /** Guarda o item na caixa de retirada, mesmo com o jogador online (ele retira quando quiser). */
    public void mail(UUID id, ItemStack item, String reason) {
        String why = reason == null ? "?" : reason.length() > 32 ? reason.substring(0, 32) : reason;
        db.update("INSERT INTO eco_mailbox (uuid, item, reason, created) VALUES (?, ?, ?, ?)", id.toString(), encode(item), why, System.currentTimeMillis());
    }

    /** Aviso de venda (chega em todos os servidores; só quem tem o vendedor online avisa). */
    public void onSold(MessageBus.Message m) {
        String[] parts = m.payload().split("\\|", 3);
        if (parts.length < 3) {
            return;
        }
        try {
            Player p = plugin.getServer().getPlayer(UUID.fromString(parts[0]));
            if (p != null) {
                msg.send(p, "leilao.vendido", eco.col(Long.parseLong(parts[1])), Messages.vars("item", parts[2]));
            }
        } catch (IllegalArgumentException e) {
            // payload estranho: ignora
        }
    }

    /** A cada minuto: anúncios vencidos vão para a caixa do vendedor. */
    public void expire() {
        long now = System.currentTimeMillis();
        db.async(c -> moveExpired(c, now)).whenComplete((n, err) -> {
            if (err != null) {
                plugin.getLogger().warning("Leilão: falha ao vencer anúncios: " + err.getMessage());
            }
        });
    }

    private static int moveExpired(Connection c, long now) throws SQLException {
        int moved = 0;
        List<Listing> old = Database.query(c, "SELECT id, seller, seller_name, item, price, expires FROM eco_auctions WHERE expires <= ? LIMIT 200",
                rs -> new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6)), now);
        for (Listing l : old) {
            // Apaga primeiro: se outro servidor já moveu, este não duplica.
            if (Database.update(c, "DELETE FROM eco_auctions WHERE id = ?", l.id()) == 1) {
                Database.update(c, "INSERT INTO eco_mailbox (uuid, item, reason, created) VALUES (?, ?, ?, ?)",
                        l.seller().toString(), l.item(), "vencido", now);
                moved++;
            }
        }
        return moved;
    }

    /** Quantos itens o jogador tem para retirar (aviso ao entrar). */
    public void notifyMailbox(Player p) {
        db.query("SELECT COUNT(*) FROM eco_mailbox WHERE uuid = ?", rs -> rs.getInt(1), p.getUniqueId().toString())
                .thenAccept(l -> AincradCore.get().runSync(() -> {
                    if (!l.isEmpty() && l.get(0) > 0 && p.isOnline()) {
                        msg.send(p, "leilao.tem_retirar", Messages.vars("n", l.get(0)));
                    }
                }));
    }
}
