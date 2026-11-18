package com.aincrad.life.orders;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.orders.OrderRules.State;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Pedidos de material: o artesão de um contrato pede o que falta, com preço por unidade e prazo,
 * no quadro público ou só para a guilda dele. O Col do pedido inteiro fica em custódia. Um
 * aventureiro reserva uma parte (por um tempo), busca o material e entrega; o pagamento cai na
 * hora, por unidade, e o material vai para a caixa do contrato — não para o inventário do artesão.
 */
public final class MaterialService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_material_requests ({AUTO_ID}, order_id BIGINT NOT NULL, requester VARCHAR(36) NOT NULL, "
                    + "requester_name VARCHAR(32) NOT NULL, material VARCHAR(64) NOT NULL, quantity INT NOT NULL, delivered INT NOT NULL, "
                    + "unit_price BIGINT NOT NULL, hold VARCHAR(36) NOT NULL, expires BIGINT NOT NULL, guild VARCHAR(36), "
                    + "guild_name VARCHAR(64), state VARCHAR(12) NOT NULL, created BIGINT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS life_reservations ({AUTO_ID}, request_id BIGINT NOT NULL, supplier VARCHAR(36) NOT NULL, "
                    + "supplier_name VARCHAR(32) NOT NULL, quantity INT NOT NULL, delivered INT NOT NULL, expires BIGINT NOT NULL)");

    private static final String COLUMNS = "id, order_id, requester, requester_name, material, quantity, delivered, unit_price, hold, "
            + "expires, guild, guild_name, state";

    /** Um pedido de material. {@code guild}: só os membros dessa guilda veem e entregam (null = público). */
    private record Request(long id, long orderId, UUID requester, String requesterName, String material, int quantity, int delivered,
                           long unitPrice, String hold, long expires, String guild, String guildName, boolean open) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final Database db;
    private final OrderService orders;

    public MaterialService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, OrderService orders) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.db = AincradCore.get().database();
        this.orders = orders;
    }

    private static Request request(ResultSet rs) throws SQLException {
        return new Request(rs.getLong(1), rs.getLong(2), OrderService.uuid(rs.getString(3)), rs.getString(4), rs.getString(5), rs.getInt(6),
                rs.getInt(7), rs.getLong(8), rs.getString(9), rs.getLong(10), rs.getString(11), rs.getString(12), "ABERTO".equals(rs.getString(13)));
    }

    private static Request find(Connection c, long id) throws SQLException {
        List<Request> l = Database.query(c, "SELECT " + COLUMNS + " FROM life_material_requests WHERE id = ?", MaterialService::request, id);
        return l.isEmpty() ? null : l.get(0);
    }

    /** A soma das reservas ativas de um pedido: {dos outros, deste fornecedor}. */
    private static int[] reserved(Connection c, long requestId, String supplier, long now) throws SQLException {
        int others = 0;
        int own = 0;
        for (Object[] r : Database.query(c, "SELECT supplier, quantity - delivered FROM life_reservations WHERE request_id = ? AND expires > ? "
                + "AND quantity > delivered", rs -> new Object[]{rs.getString(1), rs.getInt(2)}, requestId, now)) {
            if (r[0].equals(supplier)) {
                own += (Integer) r[1];
            } else {
                others += (Integer) r[1];
            }
        }
        return new int[]{others, own};
    }

    private static String name(String material) {
        return material.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private boolean visible(Request r, Player p) {
        if (r.guild() == null) {
            return true;
        }
        String[] mine = hooks.guild(p.getUniqueId());
        return mine != null && mine[0].equals(r.guild());
    }

    // ------------------------------------------------------------------ pedir

    /**
     * O artesão de um contrato em produção pede material. O Col do pedido inteiro (quantidade ×
     * preço por unidade) vai para a custódia na hora.
     *
     * @param guildOnly só para a guilda do artesão (precisa do AincradSocial e de ele ter guilda)
     */
    public void ask(Player p, long orderId, Material material, int quantity, long unitPrice, int days, boolean guildOnly) {
        int maxDays = config.integer("encomendas.prazo_maximo_dias", 14, 1, 365);
        if (material == null || !material.isItem() || material.isAir() || quantity < 1 || quantity > 2304 || unitPrice <= 0 || days < 1 || days > maxDays) {
            msg.send(p, "material.valores", Messages.vars("dias", maxDays));
            return;
        }
        String[] guild = guildOnly ? hooks.guild(p.getUniqueId()) : null;
        if (guildOnly && guild == null) {
            msg.send(p, "material.sem_guilda");
            return;
        }
        orders.run(p, c -> OrderService.find(c, orderId), o -> {
            if (o == null || o.state() != State.CONTRATADO || !p.getUniqueId().equals(o.artisan())) {
                msg.send(p, "encomenda.nao_e_seu_contrato");
                return;
            }
            long total = MaterialRules.total(quantity, unitPrice);
            String hold = p.isOnline() ? hooks.hold(p, total, "life:pedido_material") : null;
            if (hold == null) {
                msg.send(p, "encomenda.sem_col", Messages.vars("preco", orders.col(total)));
                return;
            }
            long now = System.currentTimeMillis();
            db.async(c -> {
                Database.update(c, "INSERT INTO life_material_requests (order_id, requester, requester_name, material, quantity, delivered, "
                                + "unit_price, hold, expires, guild, guild_name, state, created) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, 'ABERTO', ?)",
                        orderId, p.getUniqueId().toString(), p.getName(), material.name(), quantity, unitPrice, hold, now + days * 86_400_000L,
                        guild == null ? null : guild[0], guild == null ? null : guild[1], now);
                return Database.query(c, "SELECT id FROM life_material_requests WHERE hold = ?", rs -> rs.getLong(1), hold).get(0);
            }).whenComplete((id, err) -> AincradCore.get().runSync(() -> {
                if (err != null) {
                    plugin.getLogger().log(Level.WARNING, "Pedidos de material: não foi possível gravar o pedido", err);
                    hooks.refund(hold, "life:pedido_material_estorno");
                    msg.send(p, "geral.erro");
                    return;
                }
                msg.send(p, guild == null ? "material.pedido_aberto" : "material.pedido_aberto_guilda", Messages.vars("id", id, "qtd", quantity,
                        "material", name(material.name()), "preco", orders.col(unitPrice), "total", orders.col(total),
                        "guilda", guild == null ? "" : guild[1]));
            }));
        });
    }

    // ------------------------------------------------------------------ quadro

    /** O quadro de coleta: os pedidos públicos e os da guilda do jogador. */
    public void board(Player p) {
        long now = System.currentTimeMillis();
        String me = p.getUniqueId().toString();
        orders.run(p, c -> {
            List<Object[]> out = new ArrayList<>();
            for (Request r : Database.query(c, "SELECT " + COLUMNS + " FROM life_material_requests WHERE state = 'ABERTO' AND expires > ? "
                    + "ORDER BY created DESC LIMIT 90", MaterialService::request, now)) {
                out.add(new Object[]{r, reserved(c, r.id(), me, now)});
            }
            return out;
        }, list -> {
            Menu menu = Menu.of(msg.get("material.quadro_titulo")).rows(6);
            menu.floating();
            int shown = 0;
            for (Object[] row : list) {
                Request r = (Request) row[0];
                int[] res = (int[]) row[1];
                if (!visible(r, p)) {
                    continue;
                }
                shown++;
                Material m = Material.matchMaterial(r.material());
                boolean mine = p.getUniqueId().equals(r.requester());
                List<Component> lore = new ArrayList<>();
                lore.add(msg.get("material.linha_preco", Messages.vars("preco", orders.col(r.unitPrice()), "artesao", r.requesterName())));
                lore.add(msg.get("material.linha_aberto", Messages.vars("faltam", MaterialRules.open(r.quantity(), r.delivered()), "total", r.quantity(),
                        "livres", MaterialRules.reservable(r.quantity(), r.delivered(), res[0] + res[1]))));
                lore.add(msg.get("material.linha_prazo", Messages.vars("tempo", TimeFormat.duration(Math.max(0, r.expires() - now), 2))));
                if (r.guild() != null) {
                    lore.add(msg.get("material.linha_guilda", Messages.vars("guilda", r.guildName() == null ? "?" : r.guildName())));
                }
                if (res[1] > 0) {
                    lore.add(msg.get("material.linha_sua_reserva", Messages.vars("qtd", res[1])));
                }
                lore.add(msg.get(mine ? "material.clique_seu" : "material.clique"));
                menu.item(Menu.Button.of(new ItemStack(m == null || !m.isItem() ? Material.PAPER : m),
                        msg.get("material.item", Messages.vars("id", r.id(), "qtd", MaterialRules.open(r.quantity(), r.delivered()),
                                "material", name(r.material()))), pl -> msg.send(pl, mine ? "material.como_cancelar" : "material.como_entregar",
                                Messages.vars("id", r.id()))).closing().lore(lore));
            }
            if (shown == 0) {
                msg.send(p, "material.quadro_vazio");
                return;
            }
            AincradCore.get().menus().open(p, menu);
        });
    }

    // ------------------------------------------------------------------ reservar e entregar

    /** Reserva uma parte do pedido por um tempo. Reserva vencida sem entrega libera a quantidade. */
    public void reserve(Player p, long id, int quantity) {
        long now = System.currentTimeMillis();
        long until = now + config.integer("encomendas.material.reserva_horas", 24, 1, 720) * 3_600_000L;
        String me = p.getUniqueId().toString();
        if (quantity < 1) {
            msg.send(p, "material.uso");
            return;
        }
        // A guilda é conferida antes, na thread principal.
        String[] guild = hooks.guild(p.getUniqueId());
        orders.run(p, c -> {
            Request r = find(c, id);
            if (r == null || !r.open() || r.expires() < now || p.getUniqueId().equals(r.requester())
                    || (r.guild() != null && (guild == null || !guild[0].equals(r.guild())))) {
                return -1;
            }
            int[] res = reserved(c, id, me, now);
            int free = MaterialRules.reservable(r.quantity(), r.delivered(), res[0] + res[1]);
            int n = Math.min(quantity, free);
            if (n <= 0) {
                return 0;
            }
            Database.update(c, "INSERT INTO life_reservations (request_id, supplier, supplier_name, quantity, delivered, expires) VALUES (?, ?, ?, ?, 0, ?)",
                    id, me, p.getName(), n, Math.min(until, r.expires()));
            return n;
        }, n -> {
            if (n < 0) {
                msg.send(p, "material.nao_aberto");
            } else if (n == 0) {
                msg.send(p, "material.tudo_reservado");
            } else {
                msg.send(p, "material.reservou", Messages.vars("id", id, "qtd", n,
                        "horas", config.integer("encomendas.material.reserva_horas", 24, 1, 720)));
            }
        });
    }

    /**
     * Entrega o material que está na mão: o que o fornecedor reservou e o que estiver livre. Pode
     * ser uma parte só. O pagamento cai na hora, por unidade; o material vai para a caixa do contrato.
     */
    public void deliver(Player p, long id) {
        long now = System.currentTimeMillis();
        String me = p.getUniqueId().toString();
        ItemStack hand = p.getInventory().getItemInMainHand();
        // Só o item comum serve: um item com nome ou marca (de outro plugin) não é "o material".
        if (hand.getType().isAir() || hand.hasItemMeta()) {
            msg.send(p, "material.mao_errada");
            return;
        }
        Material type = hand.getType();
        int inHand = hand.getAmount();
        String[] guild = hooks.guild(p.getUniqueId());
        orders.run(p, c -> {
            Request r = find(c, id);
            if (r == null || !r.open() || r.expires() < now || p.getUniqueId().equals(r.requester())
                    || (r.guild() != null && (guild == null || !guild[0].equals(r.guild())))) {
                return null;
            }
            if (!r.material().equals(type.name())) {
                return new Object[]{r, -1};
            }
            int[] res = reserved(c, id, me, now);
            int n = MaterialRules.deliverable(r.quantity(), r.delivered(), inHand, res[1], res[0]);
            // Atômico: só conta se o pedido ainda está aberto e cabe.
            if (n <= 0 || Database.update(c, "UPDATE life_material_requests SET delivered = delivered + ? WHERE id = ? AND state = 'ABERTO' "
                    + "AND delivered + ? <= quantity", n, id, n) == 0) {
                return new Object[]{r, 0};
            }
            // Abate das reservas do fornecedor, da mais antiga para a mais nova.
            int left = n;
            for (Object[] row : Database.query(c, "SELECT id, quantity - delivered FROM life_reservations WHERE request_id = ? AND supplier = ? "
                    + "AND expires > ? AND quantity > delivered ORDER BY id", rs -> new Object[]{rs.getLong(1), rs.getInt(2)}, id, me, now)) {
                int take = Math.min(left, (Integer) row[1]);
                if (take <= 0) {
                    break;
                }
                Database.update(c, "UPDATE life_reservations SET delivered = delivered + ? WHERE id = ?", take, row[0]);
                left -= take;
            }
            Database.update(c, "UPDATE life_material_requests SET state = 'FECHADO' WHERE id = ? AND delivered >= quantity", id);
            OrderService.boxAdd(c, r.orderId(), r.requester(), new ItemStack(type, n));
            return new Object[]{r, n};
        }, data -> {
            if (data == null) {
                msg.send(p, "material.nao_aberto");
                return;
            }
            Request r = (Request) data[0];
            int n = (Integer) data[1];
            if (n < 0) {
                msg.send(p, "material.material_errado", Messages.vars("material", name(r.material())));
                return;
            }
            if (n == 0) {
                msg.send(p, "material.tudo_reservado");
                return;
            }
            // O item só sai da mão depois de o banco confirmar: nada se perde se o banco falhar.
            ItemStack now2 = p.getInventory().getItemInMainHand();
            int taken = now2.getType() == type && !now2.hasItemMeta() ? Math.min(n, now2.getAmount()) : 0;
            if (taken > 0) {
                now2.setAmount(now2.getAmount() - taken);
            }
            if (taken < n) {
                // O jogador trocou o item da mão no meio: tira o resto do inventário.
                p.getInventory().removeItem(new ItemStack(type, n - taken));
            }
            long pay = MaterialRules.payment(n, r.unitPrice());
            hooks.release(r.hold(), p.getUniqueId(), pay, "life:material_entregue");
            hooks.guildXp(p.getUniqueId(), r.guild() == null ? 0 : (long) n * config.integer("encomendas.material.xp_de_guilda_por_unidade", 1, 0, 1000),
                    "material");
            int open = MaterialRules.open(r.quantity(), r.delivered() + n);
            msg.send(p, "material.entregou", Messages.vars("id", id, "qtd", n, "material", name(r.material()), "preco", orders.col(pay), "faltam", open));
            orders.tell(r.requester(), "material.aviso_entrega", "id", id, "qtd", n, "material", name(r.material()), "fornecedor", p.getName(),
                    "faltam", open, "contrato", r.orderId());
        });
    }

    // ------------------------------------------------------------------ cancelar e prazo

    /** O artesão fecha o pedido: o Col do que não foi entregue volta para ele. */
    public void cancel(Player p, long id) {
        orders.run(p, c -> {
            Request r = find(c, id);
            if (r == null || !r.open() || !p.getUniqueId().equals(r.requester())) {
                return null;
            }
            return Database.update(c, "UPDATE life_material_requests SET state = 'FECHADO' WHERE id = ? AND state = 'ABERTO'", id) > 0 ? r : null;
        }, r -> {
            if (r == null) {
                msg.send(p, "material.nao_aberto");
                return;
            }
            hooks.refund(r.hold(), "life:pedido_material_fechado");
            msg.send(p, "material.cancelou", Messages.vars("id", id));
        });
    }

    /** Roda a cada minuto: pedido que passou do prazo fecha e o Col do que faltou volta ao artesão. */
    public void minute() {
        long now = System.currentTimeMillis();
        db.async(c -> {
            List<Request> out = new ArrayList<>();
            for (Request r : Database.query(c, "SELECT " + COLUMNS + " FROM life_material_requests WHERE state = 'ABERTO' AND expires < ?",
                    MaterialService::request, now)) {
                if (Database.update(c, "UPDATE life_material_requests SET state = 'FECHADO' WHERE id = ? AND state = 'ABERTO'", r.id()) > 0) {
                    out.add(r);
                }
            }
            return out;
        }).whenComplete((list, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Pedidos de material: não foi possível conferir os prazos", err);
                return;
            }
            for (Request r : list) {
                hooks.refund(r.hold(), "life:pedido_material_prazo");
                orders.tell(r.requester(), "material.aviso_venceu", "id", r.id(), "material", name(r.material()),
                        "faltam", MaterialRules.open(r.quantity(), r.delivered()));
            }
        }));
    }

    // ------------------------------------------------------------------ reputação de fornecedor

    /** A taxa de entrega do jogador: o que entregou sobre o que reservou, nas reservas que já venceram ou foram cumpridas. */
    public void rate(Player p) {
        long now = System.currentTimeMillis();
        orders.run(p, c -> Database.query(c, "SELECT COALESCE(SUM(quantity), 0), COALESCE(SUM(delivered), 0) FROM life_reservations "
                + "WHERE supplier = ? AND (expires < ? OR delivered >= quantity)", rs -> new int[]{rs.getInt(1), rs.getInt(2)},
                p.getUniqueId().toString(), now).get(0), sums -> msg.send(p, "material.taxa", Messages.vars("reservou", sums[0], "entregou", sums[1],
                "taxa", Math.round(MaterialRules.rate(sums[0], sums[1]) * 100))));
    }

    // ------------------------------------------------------------------ comando

    private static long number(String s) {
        try {
            return Long.parseLong(s.replace(".", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * /material [pedir &lt;contrato&gt; &lt;material&gt; &lt;qtd&gt; &lt;preço por unidade&gt; &lt;dias&gt; [guilda] |
     * reservar &lt;id&gt; &lt;qtd&gt; | entregar &lt;id&gt; | cancelar &lt;id&gt; | taxa]
     */
    public void command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        if (!hooks.hasEconomy()) {
            msg.send(p, "encomenda.sem_economia");
            return;
        }
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        long id = args.length > 1 ? number(args[1]) : -1;
        switch (op) {
            case "" -> board(p);
            case "taxa" -> rate(p);
            case "pedir" -> {
                if (args.length < 6 || id < 0) {
                    msg.send(p, "material.uso_pedir");
                    return;
                }
                ask(p, id, Material.matchMaterial(args[2]), (int) Math.max(-1, Math.min(10_000, number(args[3]))), number(args[4]),
                        (int) Math.max(-1, Math.min(1000, number(args[5]))), args.length > 6 && args[6].equalsIgnoreCase("guilda"));
            }
            case "reservar" -> {
                if (args.length < 3 || id < 0) {
                    msg.send(p, "material.uso");
                } else {
                    reserve(p, id, (int) Math.max(-1, Math.min(10_000, number(args[2]))));
                }
            }
            case "entregar", "cancelar" -> {
                if (id < 0) {
                    msg.send(p, "material.uso");
                } else if (op.equals("entregar")) {
                    deliver(p, id);
                } else {
                    cancel(p, id);
                }
            }
            default -> msg.send(p, "material.uso");
        }
    }
}
