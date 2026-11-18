package com.aincrad.life.orders;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.life.alchemy.AlchemyMath;
import com.aincrad.life.alchemy.AlchemyService;
import com.aincrad.life.cooking.CookingService;
import com.aincrad.life.cooking.Quality;
import com.aincrad.life.fishing.FishingRegistry;
import com.aincrad.life.fishing.FishingService;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.model.Learnable;
import com.aincrad.life.model.Profession;
import com.aincrad.life.model.TreeDef;
import com.aincrad.life.orders.OrderRules.Check;
import com.aincrad.life.orders.OrderRules.Kind;
import com.aincrad.life.orders.OrderRules.State;
import com.aincrad.life.orders.OrderRules.Stats;
import com.aincrad.life.profession.ProfessionService;
import com.aincrad.life.smith.SmithService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * O quadro de encomendas: o cliente abre um pedido (o orçamento vai para a custódia do
 * AincradEconomy), os artesãos que sabem fazer o item mandam propostas, o cliente aceita uma
 * (a diferença do orçamento volta) e o artesão entrega. O item é conferido com as exigências:
 * se atende, vai para a caixa de retirada do cliente e o artesão recebe a mão de obra menos a
 * taxa do quadro. Cliente e artesão nunca trocam Col ou itens direto.
 *
 * <p>Tudo fica no banco do core e vale para a rede inteira. Cada mudança de estado é uma
 * atualização atômica ("muda só se ainda está no estado esperado"), então dois servidores nunca
 * pagam ou devolvem a mesma encomenda duas vezes.
 */
public final class OrderService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_orders ({AUTO_ID}, client VARCHAR(36) NOT NULL, client_name VARCHAR(32) NOT NULL, "
                    + "profession VARCHAR(16) NOT NULL, product VARCHAR(64) NOT NULL, quantity INT NOT NULL, min_grade INT NOT NULL, "
                    + "budget BIGINT NOT NULL, hold VARCHAR(36) NOT NULL, expires BIGINT NOT NULL, state VARCHAR(12) NOT NULL, "
                    + "created BIGINT NOT NULL, artisan VARCHAR(36), artisan_name VARCHAR(32), price BIGINT NOT NULL, due BIGINT NOT NULL, "
                    + "offer BIGINT NOT NULL, ended BIGINT NOT NULL, ended_by VARCHAR(36), kind VARCHAR(12) NOT NULL, ref VARCHAR(64), "
                    + "rounds INT NOT NULL, rounds_done INT NOT NULL, period BIGINT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS life_hunters (order_id BIGINT NOT NULL, hunter VARCHAR(36) NOT NULL, "
                    + "hunter_name VARCHAR(32) NOT NULL, PRIMARY KEY (order_id, hunter));"
                    + "CREATE TABLE IF NOT EXISTS life_bids ({AUTO_ID}, order_id BIGINT NOT NULL, artisan VARCHAR(36) NOT NULL, "
                    + "artisan_name VARCHAR(32) NOT NULL, price BIGINT NOT NULL, days INT NOT NULL, path VARCHAR(64), spec VARCHAR(64), "
                    + "created BIGINT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS life_contract_box ({AUTO_ID}, order_id BIGINT NOT NULL, owner VARCHAR(36) NOT NULL, "
                    + "kind VARCHAR(12) NOT NULL, item TEXT NOT NULL, created BIGINT NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS life_catalog (artisan VARCHAR(36) NOT NULL, artisan_name VARCHAR(32) NOT NULL, "
                    + "profession VARCHAR(16) NOT NULL, product VARCHAR(64) NOT NULL, price BIGINT NOT NULL, days INT NOT NULL, "
                    + "PRIMARY KEY (artisan, profession, product));"
                    + "CREATE TABLE IF NOT EXISTS life_reviews (order_id BIGINT NOT NULL PRIMARY KEY, artisan VARCHAR(36) NOT NULL, "
                    + "client VARCHAR(36) NOT NULL, profession VARCHAR(16) NOT NULL, stars INT NOT NULL, created BIGINT NOT NULL)");

    /** Canal do MessageBus para avisar quem está em outro servidor. */
    public static final String BUS = "life:encomendas";

    private static final String COLUMNS = "id, client, client_name, profession, product, quantity, min_grade, budget, hold, expires, "
            + "state, artisan, artisan_name, price, due, offer, kind, ref, rounds, rounds_done, period";

    /**
     * Uma encomenda.
     *
     * @param budget o orçamento de cada entrega (a custódia guarda o orçamento vezes {@code rounds})
     * @param ref    para o banquete, o número da raid; para a caçada, o andar
     * @param rounds entregas do contrato (1 no pedido comum; na caçada, as vagas)
     * @param period intervalo entre as entregas de um fornecimento recorrente, em milissegundos
     */
    public record Order(long id, UUID client, String clientName, Profession profession, String product, int quantity, int minGrade,
                        long budget, String hold, long expires, State state, UUID artisan, String artisanName, long price, long due,
                        long offer, Kind kind, String ref, int rounds, int roundsDone, long period) {
    }

    /** Uma proposta de um artesão. */
    private record Bid(UUID artisan, String name, long price, int days, String path, String spec) {
    }

    /** O que um item é, para a conferência: o produto e a nota (qualidade, pureza, tamanho). */
    private record Found(String product, int grade) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final Database db;
    private final ProfessionService professions;
    private final Supplier<CookingService> cooking;
    private final Supplier<FishingService> fishing;
    private final Supplier<SmithService> smith;
    private final Supplier<AlchemyService> alchemy;
    private ServiceItems services;

    public OrderService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, ProfessionService professions,
                        Supplier<CookingService> cooking, Supplier<FishingService> fishing, Supplier<SmithService> smith,
                        Supplier<AlchemyService> alchemy) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.db = AincradCore.get().database();
        this.professions = professions;
        this.cooking = cooking;
        this.fishing = fishing;
        this.smith = smith;
        this.alchemy = alchemy;
    }

    /** Liga a parte dos itens em contrato de serviço (travas e devolução). */
    public void services(ServiceItems items) {
        this.services = items;
    }

    // ------------------------------------------------------------------ produtos e notas

    /** O tipo de aprendizado dos produtos de uma profissão (a pesca não tem: é a espécie do peixe). */
    private static String kind(Profession p) {
        return switch (p) {
            case CULINARIA -> CookingService.KIND;
            case FERREIRO -> SmithService.KIND;
            case ALQUIMIA -> AlchemyService.KIND;
            case PESCA -> null;
        };
    }

    private boolean available(Profession p) {
        return switch (p) {
            case CULINARIA -> cooking.get() != null;
            case PESCA -> fishing.get() != null;
            case FERREIRO -> smith.get() != null;
            case ALQUIMIA -> alchemy.get() != null;
        };
    }

    /** O nome de um produto (null: não existe nessa profissão). */
    String productName(Profession p, String id) {
        if (!available(p)) {
            return null;
        }
        if (p == Profession.PESCA) {
            FishingRegistry.Fish f = fishing.get().registry().fish(id);
            return f == null ? null : f.name();
        }
        Learnable l = professions.learnable(kind(p), id);
        return l == null ? null : l.name();
    }

    /** Os ids que podem ser encomendados numa profissão. */
    public List<String> products(Profession p) {
        List<String> out = new ArrayList<>();
        if (!available(p)) {
            return out;
        }
        if (p == Profession.PESCA) {
            out.addAll(fishing.get().registry().fishes().keySet());
        } else {
            professions.learnables().forEach(l -> {
                if (l.kind().equals(kind(p))) {
                    out.add(l.id());
                }
            });
        }
        return out;
    }

    /** O artesão sabe fazer o produto? (qualquer um pode pescar) */
    private boolean canMake(Player p, Profession prof, String product) {
        if (prof == Profession.PESCA) {
            return true;
        }
        Learnable l = professions.learnable(kind(prof), product);
        return l != null && professions.canProduce(p, l);
    }

    /** O que o item é para a profissão: produto e nota (null: não é produto dela). */
    private Found inspect(Profession p, ItemStack it) {
        if (it == null || it.getType().isAir() || !available(p)) {
            return null;
        }
        switch (p) {
            case CULINARIA -> {
                String id = cooking.get().dishId(it);
                Quality q = cooking.get().dishQuality(it);
                return id == null || q == null ? null : new Found(id, q.ordinal());
            }
            case PESCA -> {
                String id = fishing.get().fishId(it);
                return id == null ? null : new Found(id, fishing.get().fishMm(it));
            }
            case FERREIRO -> {
                String id = smith.get().forged().blueprint(it);
                return id == null ? null : new Found(id, smith.get().forged().quality(it));
            }
            default -> {
                String id = alchemy.get().potions().formulaId(it);
                AlchemyMath.Purity purity = alchemy.get().potions().purity(it);
                return id == null || purity == null ? null : new Found(id, purity.ordinal());
            }
        }
    }

    /**
     * A nota mínima escrita pelo cliente: culinária (comum, bom, excelente), pesca (centímetros),
     * ferreiro (qualidade de 0 a 100) ou alquimia (comum, pura, perfeita).
     *
     * @return a nota, ou -1 se o texto não serve
     */
    static int parseGrade(Profession p, String text) {
        String t = text.trim().toLowerCase(Locale.ROOT).replace("%", "").replace(',', '.');
        try {
            switch (p) {
                case CULINARIA -> {
                    Quality q = Quality.parse(t);
                    return q == null || q == Quality.QUEIMADO ? -1 : q.ordinal();
                }
                case ALQUIMIA -> {
                    AlchemyMath.Purity purity = AlchemyMath.Purity.parse(t);
                    return purity == null ? -1 : purity.ordinal();
                }
                case PESCA -> {
                    double cm = Double.parseDouble(t);
                    return cm < 0 ? -1 : (int) Math.round(cm * 10);
                }
                default -> {
                    int q = (int) Math.round(Double.parseDouble(t));
                    return q < 0 || q > 100 ? -1 : q;
                }
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** A nota por extenso: "qualidade 80%", "pureza pura", "40 cm"... */
    private Component gradeText(Profession p, int grade) {
        return switch (p) {
            case CULINARIA -> msg.get("cozinha.qualidade." + Quality.values()[Math.max(0, Math.min(3, grade))].key());
            case ALQUIMIA -> msg.get("alquimia.pureza." + AlchemyMath.Purity.values()[Math.max(0, Math.min(3, grade))].key());
            case PESCA -> msg.get("encomenda.nota_pesca", Messages.vars("cm", FishingService.cm(grade)));
            case FERREIRO -> msg.get("encomenda.nota_ferreiro", Messages.vars("qualidade", grade));
        };
    }

    // ------------------------------------------------------------------ banco

    static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Order order(ResultSet rs) throws SQLException {
        Profession prof = Profession.parse(rs.getString(4));
        State state = State.parse(rs.getString(11));
        return new Order(rs.getLong(1), uuid(rs.getString(2)), rs.getString(3), prof == null ? Profession.FERREIRO : prof,
                rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getLong(8), rs.getString(9), rs.getLong(10),
                state == null ? State.CANCELADO : state, uuid(rs.getString(12)), rs.getString(13), rs.getLong(14), rs.getLong(15), rs.getLong(16),
                Kind.parse(rs.getString(17)), rs.getString(18), Math.max(1, rs.getInt(19)), rs.getInt(20), rs.getLong(21));
    }

    static Order find(Connection c, long id) throws SQLException {
        List<Order> l = Database.query(c, "SELECT " + COLUMNS + " FROM life_orders WHERE id = ?", OrderService::order, id);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Muda o estado só se a encomenda ainda está no estado esperado. @return true se mudou */
    private static boolean move(Connection c, long id, State from, State to, UUID by) throws SQLException {
        return Database.update(c, "UPDATE life_orders SET state = ?, ended = ?, ended_by = ? WHERE id = ? AND state = ?",
                to.name(), to.finished() ? System.currentTimeMillis() : 0, by == null ? null : by.toString(), id, from.name()) > 0;
    }

    /** Uma encomenda encerrada e o que estava na caixa dela ({dono, item}), para devolver. */
    private record Closed(Order order, List<String[]> box) {
    }

    /** Tira tudo da caixa do contrato. Cada item volta para quem o pôs lá. */
    private static List<String[]> emptyBox(Connection c, long id) throws SQLException {
        List<String[]> box = Database.query(c, "SELECT owner, item FROM life_contract_box WHERE order_id = ?",
                rs -> new String[]{rs.getString(1), rs.getString(2)}, id);
        Database.update(c, "DELETE FROM life_contract_box WHERE order_id = ?", id);
        return box;
    }

    /** Manda os itens de uma caixa esvaziada para a caixa de retirada dos donos. */
    private void returnBox(List<String[]> box) {
        for (String[] row : box) {
            UUID owner = uuid(row[0]);
            ItemStack it = unpack(row[1]);
            if (owner != null && it != null) {
                hooks.mail(owner, it, "encomenda");
            }
        }
    }

    /** Roda uma consulta fora da thread principal e entrega o resultado nela. Erro: avisa e não chama. */
    <T> void run(CommandSender who, Database.SqlFunction<T> work, Consumer<T> then) {
        run(who, work, then, () -> { });
    }

    /** Como {@link #run}, com o que desfazer se o banco falhar (ex.: devolver o item que já saiu da mão). */
    <T> void run(CommandSender who, Database.SqlFunction<T> work, Consumer<T> then, Runnable onError) {
        db.async(work).whenComplete((value, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Encomendas: erro no banco", err);
                onError.run();
                if (who != null) {
                    msg.send(who, "geral.erro");
                }
                return;
            }
            then.accept(value);
        }));
    }

    String col(long amount) {
        return hooks.price(amount, Material.GOLD_INGOT, 100);
    }

    private double feePercent() {
        return config.decimal("encomendas.taxa_pct", 5, 0, 100);
    }

    // ------------------------------------------------------------------ avisos

    /** Avisa um jogador, esteja ele neste servidor ou em outro da rede. Os valores não podem ter tabulação. */
    void tell(UUID to, String key, Object... pairs) {
        Player p = to == null ? null : Bukkit.getPlayer(to);
        if (p != null) {
            msg.send(p, key, Messages.vars(pairs));
            return;
        }
        if (to == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(to.toString()).append('\t').append(key);
        for (Object o : pairs) {
            sb.append('\t').append(String.valueOf(o).replace('\t', ' '));
        }
        AincradCore.get().publish(BUS, sb.toString());
    }

    /** Um aviso vindo de outro servidor. */
    public void onBus(String payload) {
        String[] parts = payload.split("\t");
        Player p = parts.length < 2 ? null : Bukkit.getPlayer(uuid(parts[0]) == null ? new UUID(0, 0) : uuid(parts[0]));
        if (p == null || (parts.length - 2) % 2 != 0) {
            return;
        }
        Object[] pairs = new Object[parts.length - 2];
        System.arraycopy(parts, 2, pairs, 0, pairs.length);
        msg.send(p, parts[1], Messages.vars(pairs));
    }

    // ------------------------------------------------------------------ abrir pedido

    /** Abre um pedido no quadro. O orçamento sai do saldo do cliente e fica em custódia. */
    public void open(Player p, Profession prof, String product, int quantity, int minGrade, long budget, int days) {
        int maxDays = config.integer("encomendas.prazo_maximo_dias", 14, 1, 365);
        if (days < 1 || days > maxDays) {
            msg.send(p, "encomenda.valores", Messages.vars("dias", maxDays));
            return;
        }
        create(p, prof, product, quantity, minGrade, budget, System.currentTimeMillis() + days * 86_400_000L, Kind.PEDIDO, null, 1, 0,
                "encomenda.aberta");
    }

    /**
     * Grava um pedido de qualquer tipo. A custódia guarda o orçamento de cada entrega vezes as entregas.
     *
     * @param expires até quando o pedido aceita propostas
     * @param opened  a mensagem de pedido aberto
     */
    private void create(Player p, Profession prof, String product, int quantity, int minGrade, long budget, long expires, Kind kind,
                        String ref, int rounds, long period, String opened) {
        String name = productName(prof, product);
        if (name == null) {
            msg.send(p, "encomenda.produto_desconhecido", Messages.vars("profissao", professions.name(prof)));
            return;
        }
        if (budget <= 0 || quantity < 1 || quantity > 64 || rounds < 1) {
            msg.send(p, "encomenda.valores", Messages.vars("dias", config.integer("encomendas.prazo_maximo_dias", 14, 1, 365)));
            return;
        }
        int limit = config.integer("encomendas.pedidos_por_jogador", 5, 1, 1000);
        String id = p.getUniqueId().toString();
        long total = OrderRules.total(budget, rounds);
        run(p, c -> Database.query(c, "SELECT COUNT(*) FROM life_orders WHERE client = ? AND state IN ('ABERTO', 'AGUARDANDO', 'CONTRATADO', 'OFERTA')",
                rs -> rs.getInt(1), id).get(0), count -> {
            if (count >= limit) {
                msg.send(p, "encomenda.limite", Messages.vars("limite", limit));
                return;
            }
            String hold = p.isOnline() ? hooks.hold(p, total, "life:encomenda") : null;
            if (hold == null) {
                msg.send(p, "encomenda.sem_col", Messages.vars("preco", col(total)));
                return;
            }
            long now = System.currentTimeMillis();
            db.async(c -> {
                Database.update(c, "INSERT INTO life_orders (client, client_name, profession, product, quantity, min_grade, budget, hold, "
                                + "expires, state, created, price, due, offer, ended, kind, ref, rounds, rounds_done, period) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ABERTO', ?, 0, 0, 0, 0, ?, ?, ?, 0, ?)",
                        id, p.getName(), prof.name(), product.toLowerCase(Locale.ROOT), quantity, minGrade, budget, hold, expires, now,
                        kind.name(), ref, rounds, period);
                return Database.query(c, "SELECT id FROM life_orders WHERE hold = ?", rs -> rs.getLong(1), hold).get(0);
            }).whenComplete((orderId, err) -> AincradCore.get().runSync(() -> {
                if (err != null) {
                    // O pedido não foi gravado: o orçamento volta.
                    plugin.getLogger().log(Level.WARNING, "Encomendas: não foi possível gravar o pedido", err);
                    hooks.refund(hold, "life:encomenda_estorno");
                    msg.send(p, "geral.erro");
                    return;
                }
                msg.send(p, opened, Messages.vars("id", orderId, "qtd", quantity, "produto", name, "preco", col(budget), "total", col(total),
                        "vezes", rounds, "dias", Math.max(1, Math.round((expires - now) / 86_400_000.0))));
            }));
        });
    }

    // ------------------------------------------------------------------ quadro e listas

    private List<Component> lore(Order o) {
        List<Component> out = new ArrayList<>();
        out.add(msg.get("encomenda.linha_profissao", Messages.vars("profissao", professions.name(o.profession()), "cliente", o.clientName())));
        if (o.kind() == Kind.SERVICO) {
            out.add(msg.get("encomenda.tipo.servico"));
        } else {
            out.add(msg.get("encomenda.linha_exigencia", Messages.vars("nota", gradeText(o.profession(), o.minGrade()))));
        }
        out.add(msg.get("encomenda.linha_orcamento", Messages.vars("preco", col(o.budget()))));
        if (o.state() == State.AGUARDANDO) {
            out.add(msg.get("encomenda.linha_aguardando", Messages.vars("artesao", o.artisanName() == null ? "—" : o.artisanName(),
                    "tempo", TimeFormat.duration(Math.max(0, o.expires() - System.currentTimeMillis()), 2))));
        } else if (o.state() == State.ABERTO) {
            out.add(msg.get("encomenda.linha_prazo", Messages.vars("tempo", TimeFormat.duration(Math.max(0, o.expires() - System.currentTimeMillis()), 2))));
        } else {
            out.add(msg.get("encomenda.linha_contrato", Messages.vars("artesao", o.artisanName() == null ? "—" : o.artisanName(),
                    "preco", col(o.price()), "tempo", TimeFormat.duration(Math.max(0, o.due() - System.currentTimeMillis()), 2))));
        }
        if (o.kind() == Kind.RECORRENTE) {
            out.add(msg.get("encomenda.tipo.recorrente", Messages.vars("feitas", o.roundsDone(), "vezes", o.rounds(),
                    "intervalo", TimeFormat.duration(o.period(), 1))));
        } else if (o.kind() == Kind.BANQUETE) {
            out.add(msg.get(o.profession() == Profession.ALQUIMIA ? "encomenda.tipo.kit" : "encomenda.tipo.banquete",
                    Messages.vars("raid", o.ref() == null ? "?" : o.ref(), "cada", Math.max(1, o.period()))));
        } else if (o.kind() == Kind.CACADA) {
            out.add(msg.get("encomenda.tipo.cacada", Messages.vars("andar", o.ref() == null ? "?" : o.ref(), "vagas", o.quantity())));
        }
        out.add(msg.get("encomenda.estado." + o.state().name().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static Material icon(Profession p) {
        return switch (p) {
            case CULINARIA -> Material.RABBIT_STEW;
            case PESCA -> Material.COD;
            case FERREIRO -> Material.IRON_SWORD;
            case ALQUIMIA -> Material.POTION;
        };
    }

    private Component title(Order o) {
        if (o.kind() == Kind.SERVICO) {
            return msg.get("encomenda.item_servico", Messages.vars("id", o.id(), "item", o.ref() == null ? "?" : o.ref()));
        }
        String name = productName(o.profession(), o.product());
        return msg.get("encomenda.item", Messages.vars("id", o.id(), "qtd", o.quantity(), "produto", name == null ? o.product() : name));
    }

    /** O quadro: os pedidos abertos da rede. Clicar mostra como propor (ou as propostas, se o pedido é seu). */
    public void board(Player p) {
        long now = System.currentTimeMillis();
        run(p, c -> Database.query(c, "SELECT " + COLUMNS + " FROM life_orders WHERE state = 'ABERTO' AND expires > ? ORDER BY created DESC LIMIT 90",
                OrderService::order, now), list -> {
            if (list.isEmpty()) {
                msg.send(p, "encomenda.quadro_vazio");
                return;
            }
            Menu menu = Menu.of(msg.get("encomenda.quadro_titulo")).rows(6);
            for (Order o : list) {
                List<Component> lore = lore(o);
                boolean mine = p.getUniqueId().equals(o.client());
                boolean hunt = o.kind() == Kind.CACADA;
                boolean can = !mine && !hunt && canMake(p, o.profession(), o.product());
                lore.add(msg.get(hunt ? (mine ? "encomenda.cacada_sua" : "encomenda.cacada_clique")
                        : mine ? "encomenda.clique_propostas" : can ? "encomenda.clique_propor" : "encomenda.nao_sabe_fazer"));
                menu.item(Menu.Button.of(new ItemStack(hunt ? Material.TRIDENT : icon(o.profession())), title(o), pl -> {
                    if (hunt) {
                        if (!mine) {
                            msg.send(pl, "encomenda.cacada_como_entrar", Messages.vars("id", o.id()));
                        }
                    } else if (mine) {
                        proposals(pl, o.id());
                    } else if (can) {
                        msg.send(pl, "encomenda.como_propor", Messages.vars("id", o.id()));
                    }
                }).closing().lore(lore));
            }
            AincradCore.get().menus().open(p, menu);
        });
    }

    /** As encomendas em andamento do jogador, como cliente e como artesão. */
    public void mine(Player p) {
        String id = p.getUniqueId().toString();
        run(p, c -> Database.query(c, "SELECT " + COLUMNS + " FROM life_orders WHERE (client = ? OR artisan = ?) AND "
                + "(state IN ('ABERTO', 'AGUARDANDO', 'CONTRATADO', 'OFERTA') OR ended > ?) ORDER BY created DESC LIMIT 45", OrderService::order,
                id, id, System.currentTimeMillis() - 7 * 86_400_000L), list -> {
            if (list.isEmpty()) {
                msg.send(p, "encomenda.nenhuma");
                return;
            }
            Menu menu = Menu.of(msg.get("encomenda.minhas_titulo")).rows(6);
            for (Order o : list) {
                boolean client = p.getUniqueId().equals(o.client());
                List<Component> lore = lore(o);
                lore.add(msg.get(client ? "encomenda.voce_cliente" : "encomenda.voce_artesao"));
                String hint = switch (o.state()) {
                    case ABERTO -> client ? "encomenda.dica.aberto" : null;
                    case AGUARDANDO -> client ? "encomenda.dica.aguardando_cliente" : "encomenda.dica.aguardando_artesao";
                    case CONTRATADO -> client ? "encomenda.dica.contratado_cliente" : "encomenda.dica.contratado_artesao";
                    case OFERTA -> client ? "encomenda.dica.oferta_cliente" : "encomenda.dica.oferta_artesao";
                    case ENTREGUE -> client ? "encomenda.dica.entregue" : null;
                    default -> null;
                };
                if (hint != null) {
                    lore.add(msg.get(hint, Messages.vars("id", o.id(), "preco", col(o.offer()))));
                }
                menu.item(Menu.Button.of(new ItemStack(icon(o.profession())), title(o), pl -> {
                    if (client && o.state() == State.ABERTO) {
                        proposals(pl, o.id());
                    }
                }).closing().lore(lore));
            }
            AincradCore.get().menus().open(p, menu);
        });
    }

    // ------------------------------------------------------------------ propostas

    /** Um artesão manda (ou troca) a proposta dele: preço e prazo. Só quem sabe fazer o item. */
    public void bid(Player p, long id, long price, int days) {
        run(p, c -> find(c, id), o -> {
            if (o == null || o.state() != State.ABERTO || o.expires() < System.currentTimeMillis()) {
                msg.send(p, "encomenda.nao_aberta");
                return;
            }
            if (p.getUniqueId().equals(o.client())) {
                msg.send(p, "encomenda.propria");
                return;
            }
            if (o.kind() == Kind.CACADA) {
                msg.send(p, "encomenda.cacada_como_entrar", Messages.vars("id", id));
                return;
            }
            if (!canMake(p, o.profession(), o.product())) {
                msg.send(p, "encomenda.nao_sabe_fazer");
                return;
            }
            int maxDays = config.integer("encomendas.prazo_maximo_dias", 14, 1, 365);
            if (price <= 0 || price > o.budget() || days < 1 || days > maxDays) {
                msg.send(p, "encomenda.proposta_invalida", Messages.vars("preco", col(o.budget()), "dias", maxDays));
                return;
            }
            var owned = professions.owned(p, o.profession());
            String artisan = p.getUniqueId().toString();
            run(p, c -> {
                // Uma proposta por artesão: a nova troca a antiga.
                Database.update(c, "DELETE FROM life_bids WHERE order_id = ? AND artisan = ?", id, artisan);
                return Database.update(c, "INSERT INTO life_bids (order_id, artisan, artisan_name, price, days, path, spec, created) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, artisan, p.getName(), price, days, TreeDef.pathOf(owned), TreeDef.specOf(owned),
                        System.currentTimeMillis());
            }, n -> {
                msg.send(p, "encomenda.proposta_enviada", Messages.vars("id", id, "preco", col(price), "dias", days));
                tell(o.client(), "encomenda.aviso_proposta", "id", id, "artesao", p.getName(), "preco", col(price));
            });
        });
    }

    private static Map<UUID, Stats> stats(Connection c, Profession prof) throws SQLException {
        Map<UUID, Stats> out = new HashMap<>();
        for (Object[] r : Database.query(c, "SELECT artisan, MAX(artisan_name), "
                + "SUM(CASE WHEN state = 'ENTREGUE' THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN state = 'CANCELADO' AND ended_by = artisan THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN state = 'ATRASADO' THEN 1 ELSE 0 END) "
                + "FROM life_orders WHERE artisan IS NOT NULL AND profession = ? GROUP BY artisan",
                rs -> new Object[]{rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)}, prof.name())) {
            UUID id = uuid((String) r[0]);
            if (id != null) {
                out.put(id, new Stats(id, (String) r[1], 0, 0, (Integer) r[2], (Integer) r[3], (Integer) r[4]));
            }
        }
        for (Object[] r : Database.query(c, "SELECT artisan, COUNT(*), SUM(stars) FROM life_reviews WHERE profession = ? GROUP BY artisan",
                rs -> new Object[]{rs.getString(1), rs.getInt(2), rs.getInt(3)}, prof.name())) {
            UUID id = uuid((String) r[0]);
            Stats s = id == null ? null : out.get(id);
            if (s != null) {
                out.put(id, new Stats(id, s.name(), (Integer) r[1], (Integer) r[2], s.done(), s.cancelled(), s.late()));
            }
        }
        return out;
    }

    private String stars(Stats s) {
        return s == null || s.reviews() == 0 ? msg.raw("encomenda.sem_avaliacao")
                : String.format(Locale.ROOT, "%.1f", s.average()).replace('.', ',') + " ★ (" + s.reviews() + ")";
    }

    /** As propostas de um pedido do jogador, com a reputação e o ramo de cada artesão. Clicar aceita. */
    public void proposals(Player p, long id) {
        run(p, c -> {
            Order o = find(c, id);
            if (o == null || !p.getUniqueId().equals(o.client()) || o.state() != State.ABERTO) {
                return null;
            }
            List<Bid> bids = Database.query(c, "SELECT artisan, artisan_name, price, days, path, spec FROM life_bids WHERE order_id = ? ORDER BY price ASC",
                    rs -> new Bid(uuid(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getString(5), rs.getString(6)), id);
            return new Object[]{o, bids, stats(c, o.profession())};
        }, data -> {
            if (data == null) {
                msg.send(p, "encomenda.nao_aberta");
                return;
            }
            Order o = (Order) data[0];
            @SuppressWarnings("unchecked")
            List<Bid> bids = (List<Bid>) data[1];
            @SuppressWarnings("unchecked")
            Map<UUID, Stats> stats = (Map<UUID, Stats>) data[2];
            if (bids.isEmpty()) {
                msg.send(p, "encomenda.sem_propostas", Messages.vars("id", id));
                return;
            }
            Menu menu = Menu.of(msg.get("encomenda.propostas_titulo", Messages.vars("id", id))).rows(4);
            for (Bid b : bids) {
                Stats s = stats.get(b.artisan());
                List<Component> lore = new ArrayList<>();
                lore.add(msg.get("encomenda.proposta_preco", Messages.vars("preco", col(b.price()), "dias", b.days())));
                lore.add(msg.get("encomenda.proposta_reputacao", Messages.vars("estrelas", stars(s), "feitos", s == null ? 0 : s.done(),
                        "cancelados", s == null ? 0 : s.cancelled(), "atrasados", s == null ? 0 : s.late())));
                lore.add(msg.get("encomenda.proposta_ramo", Messages.vars("caminho", b.path() == null ? "—" : b.path().replace('_', ' '),
                        "especializacao", b.spec() == null ? "—" : b.spec().replace('_', ' '))));
                lore.add(msg.get("encomenda.clique_aceitar", Messages.vars("troco", col(OrderRules.change(o.budget(), b.price())))));
                menu.item(Menu.Button.of(new ItemStack(Material.PLAYER_HEAD), Messages.parse("<white><nome>", Messages.vars("nome", b.name())),
                        pl -> AincradCore.get().menus().confirm(pl, msg.get("encomenda.confirmar_aceite",
                                Messages.vars("artesao", b.name(), "preco", col(b.price()))), yes -> accept(yes, o, b), no -> { })).closing().lore(lore));
            }
            AincradCore.get().menus().open(p, menu);
        });
    }

    /** O cliente aceita uma proposta: nasce o contrato e a diferença do orçamento volta para ele. */
    private void accept(Player p, Order o, Bid b) {
        // Recorrente: a primeira entrega vence depois de um intervalo. Banquete: até a hora da raid.
        long due = o.kind() == Kind.RECORRENTE ? System.currentTimeMillis() + o.period()
                : o.kind() == Kind.BANQUETE ? o.expires() : System.currentTimeMillis() + b.days() * 86_400_000L;
        run(p, c -> Database.update(c, "UPDATE life_orders SET state = 'CONTRATADO', artisan = ?, artisan_name = ?, price = ?, due = ? "
                + "WHERE id = ? AND state = 'ABERTO' AND client = ?", b.artisan().toString(), b.name(), b.price(), due, o.id(),
                p.getUniqueId().toString()) > 0, ok -> {
            if (!ok) {
                msg.send(p, "encomenda.nao_aberta");
                return;
            }
            long change = OrderRules.change(o.budget(), b.price(), o.rounds());
            hooks.release(o.hold(), o.client(), change, "life:encomenda_troco");
            msg.send(p, "encomenda.contratou", Messages.vars("id", o.id(), "artesao", b.name(), "preco", col(b.price()), "troco", col(change)));
            String name = productName(o.profession(), o.product());
            tell(b.artisan(), "encomenda.aviso_contratado", "id", o.id(), "produto", name == null ? o.product() : name, "dias", b.days());
        });
    }

    // ------------------------------------------------------------------ entrega

    static String pack(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    static ItemStack unpack(String raw) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Fecha o contrato: o artesão recebe o preço menos a taxa, o que sobrar do contrato volta ao cliente e a taxa sai de circulação. */
    private void settle(Order o, long price) {
        long fee = OrderRules.fee(price, feePercent());
        long back = Math.max(0, o.price() - price);
        hooks.release(o.hold(), o.artisan(), price - fee, "life:encomenda_mao_de_obra")
                .thenCompose(ok -> hooks.release(o.hold(), o.client(), back, "life:encomenda_troco"))
                .thenCompose(ok -> hooks.burn(o.hold()));
    }

    /** O artesão entrega o item que está na mão. Só é aceito sozinho se atende às exigências do pedido. */
    public void deliver(Player p, long id) {
        run(p, c -> find(c, id), o -> {
            if (o == null || o.state() != State.CONTRATADO || !p.getUniqueId().equals(o.artisan())) {
                msg.send(p, "encomenda.nao_e_seu_contrato");
                return;
            }
            if (o.kind() == Kind.SERVICO) {
                deliverService(p, o);
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            Found f = inspect(o.profession(), hand);
            Check check = OrderRules.check(o.product(), o.minGrade(), o.quantity(), f == null ? null : f.product(),
                    f == null ? 0 : f.grade(), hand.getAmount());
            if (check != Check.OK) {
                msg.send(p, "encomenda.recusa." + check.name().toLowerCase(Locale.ROOT), Messages.vars("id", id, "qtd", o.quantity(),
                        "nota", gradeText(o.profession(), o.minGrade())));
                return;
            }
            ItemStack item = hand.clone();
            item.setAmount(o.quantity());
            hand.setAmount(hand.getAmount() - o.quantity());
            if (o.kind() == Kind.RECORRENTE) {
                deliverRound(p, o, item);
                return;
            }
            run(p, c -> move(c, id, State.CONTRATADO, State.ENTREGUE, null), ok -> {
                if (!ok) {
                    // Outro servidor mexeu na encomenda no mesmo instante: o item volta.
                    give(p, item);
                    msg.send(p, "encomenda.nao_e_seu_contrato");
                    return;
                }
                if (o.kind() == Kind.BANQUETE) {
                    serveBanquet(o, item);
                } else {
                    hooks.mail(o.client(), item, "encomenda");
                }
                settle(o, o.price());
                long pay = OrderRules.pay(o.price(), feePercent());
                msg.send(p, "encomenda.entregou", Messages.vars("id", id, "preco", col(pay), "taxa", col(o.price() - pay)));
                tell(o.client(), "encomenda.aviso_entregue", "id", id, "artesao", p.getName());
            }, () -> give(p, item));
        });
    }

    static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    /**
     * O item ficou abaixo do pedido: o artesão o oferece por um preço menor. O item fica na caixa
     * do contrato até o cliente aceitar ou recusar.
     */
    public void offer(Player p, long id, long price) {
        run(p, c -> find(c, id), o -> {
            if (o == null || o.state() != State.CONTRATADO || !p.getUniqueId().equals(o.artisan())) {
                msg.send(p, "encomenda.nao_e_seu_contrato");
                return;
            }
            if (o.kind() != Kind.PEDIDO) {
                msg.send(p, "encomenda.oferta_so_pedido");
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            Found f = inspect(o.profession(), hand);
            if (f == null || !f.product().equalsIgnoreCase(o.product()) || hand.getAmount() < o.quantity()) {
                msg.send(p, "encomenda.recusa.produto", Messages.vars("id", id, "qtd", o.quantity()));
                return;
            }
            if (price <= 0 || price >= o.price()) {
                msg.send(p, "encomenda.oferta_invalida", Messages.vars("preco", col(o.price())));
                return;
            }
            ItemStack item = hand.clone();
            item.setAmount(o.quantity());
            hand.setAmount(hand.getAmount() - o.quantity());
            String packed = pack(item);
            run(p, c -> {
                if (Database.update(c, "UPDATE life_orders SET state = 'OFERTA', offer = ? WHERE id = ? AND state = 'CONTRATADO'", price, id) == 0) {
                    return false;
                }
                Database.update(c, "INSERT INTO life_contract_box (order_id, owner, kind, item, created) VALUES (?, ?, 'OFERTA', ?, ?)",
                        id, p.getUniqueId().toString(), packed, System.currentTimeMillis());
                return true;
            }, ok -> {
                if (!ok) {
                    give(p, item);
                    msg.send(p, "encomenda.nao_e_seu_contrato");
                    return;
                }
                msg.send(p, "encomenda.ofereceu", Messages.vars("id", id, "preco", col(price)));
                tell(o.client(), "encomenda.aviso_oferta", "id", id, "artesao", p.getName(), "preco", col(price));
            }, () -> give(p, item));
        });
    }

    /** O cliente responde à oferta com desconto. Aceita: recebe o item e paga o preço menor. Recusa: o item volta ao artesão. */
    public void answerOffer(Player p, long id, boolean accept) {
        run(p, c -> {
            Order o = find(c, id);
            if (o == null || o.state() != State.OFERTA || !p.getUniqueId().equals(o.client())) {
                return null;
            }
            List<String> box = Database.query(c, "SELECT item FROM life_contract_box WHERE order_id = ? AND kind = 'OFERTA'", rs -> rs.getString(1), id);
            boolean moved = accept ? move(c, id, State.OFERTA, State.ENTREGUE, null)
                    : Database.update(c, "UPDATE life_orders SET state = 'CONTRATADO', offer = 0 WHERE id = ? AND state = 'OFERTA'", id) > 0;
            if (!moved) {
                return null;
            }
            Database.update(c, "DELETE FROM life_contract_box WHERE order_id = ? AND kind = 'OFERTA'", id);
            return new Object[]{o, box};
        }, data -> {
            if (data == null) {
                msg.send(p, "encomenda.sem_oferta");
                return;
            }
            Order o = (Order) data[0];
            @SuppressWarnings("unchecked")
            List<String> box = (List<String>) data[1];
            for (String raw : box) {
                ItemStack it = unpack(raw);
                if (it != null) {
                    hooks.mail(accept ? o.client() : o.artisan(), it, "encomenda");
                }
            }
            if (accept) {
                settle(o, o.offer());
                msg.send(p, "encomenda.oferta_aceita", Messages.vars("id", id, "preco", col(o.offer()), "troco", col(o.price() - o.offer())));
                tell(o.artisan(), "encomenda.aviso_oferta_aceita", "id", id, "preco", col(OrderRules.pay(o.offer(), feePercent())));
            } else {
                msg.send(p, "encomenda.oferta_recusada", Messages.vars("id", id));
                tell(o.artisan(), "encomenda.aviso_oferta_recusada", "id", id);
            }
        });
    }

    // ------------------------------------------------------------------ cancelar e prazo

    /** Cancela: o cliente, a qualquer momento; o artesão, depois de contratado. O Col em custódia volta ao cliente. */
    public void cancel(Player p, long id) {
        UUID me = p.getUniqueId();
        run(p, c -> {
            Order o = find(c, id);
            if (o == null) {
                return null;
            }
            boolean client = me.equals(o.client());
            boolean artisan = me.equals(o.artisan());
            boolean ok = (o.state() == State.ABERTO && client && move(c, id, State.ABERTO, State.CANCELADO, me))
                    || (o.state() == State.AGUARDANDO && (client || artisan) && move(c, id, State.AGUARDANDO, State.CANCELADO, me))
                    || (o.state() == State.CONTRATADO && (client || artisan) && move(c, id, State.CONTRATADO, State.CANCELADO, me));
            return ok ? new Closed(o, emptyBox(c, id)) : null;
        }, closed -> {
            if (closed == null) {
                msg.send(p, "encomenda.nao_cancela");
                return;
            }
            Order o = closed.order();
            returnBox(closed.box());
            hooks.refund(o.hold(), "life:encomenda_cancelada");
            msg.send(p, "encomenda.cancelou", Messages.vars("id", id));
            UUID other = me.equals(o.client()) ? o.artisan() : o.client();
            tell(other, "encomenda.aviso_cancelada", "id", id, "jogador", p.getName());
        });
    }

    /** Roda a cada minuto: pedidos sem contrato e contratos que passaram do prazo voltam o Col ao cliente. */
    public void minute() {
        long now = System.currentTimeMillis();
        if (services != null) {
            // Depois de fechar os vencidos (abaixo), relê quais serviços ainda estão em produção.
            Bukkit.getScheduler().runTaskLater(plugin, () -> db.query("SELECT id FROM life_orders WHERE kind = 'SERVICO' AND state = 'CONTRATADO'",
                    rs -> rs.getLong(1)).thenAccept(ids -> AincradCore.get().runSync(() -> {
                if (services != null) {
                    services.refresh(new java.util.HashSet<>(ids));
                }
            })), 60L);
        }
        db.async(c -> {
            List<Closed> out = new ArrayList<>();
            for (Order o : Database.query(c, "SELECT " + COLUMNS + " FROM life_orders WHERE (state IN ('ABERTO', 'AGUARDANDO') AND expires < ?) "
                    + "OR (state = 'CONTRATADO' AND due < ?)", OrderService::order, now, now)) {
                State to = o.state() == State.CONTRATADO ? State.ATRASADO : State.VENCIDO;
                // Só o servidor que conseguir mudar o estado devolve o Col e os materiais.
                if (move(c, o.id(), o.state(), to, null)) {
                    out.add(new Closed(o, emptyBox(c, o.id())));
                }
            }
            return out;
        }).whenComplete((list, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Encomendas: não foi possível conferir os prazos", err);
                return;
            }
            for (Closed closed : list) {
                Order o = closed.order();
                returnBox(closed.box());
                hooks.refund(o.hold(), "life:encomenda_prazo");
                if (o.state() == State.ABERTO) {
                    tell(o.client(), "encomenda.aviso_vencida", "id", o.id());
                } else if (o.state() == State.AGUARDANDO) {
                    tell(o.client(), "encomenda.aviso_sem_resposta", "id", o.id(), "artesao", o.artisanName() == null ? "?" : o.artisanName());
                } else {
                    tell(o.client(), "encomenda.aviso_atrasada_cliente", "id", o.id(), "artesao", o.artisanName() == null ? "?" : o.artisanName());
                    tell(o.artisan(), "encomenda.aviso_atrasada_artesao", "id", o.id());
                }
            }
        }));
    }

    // ------------------------------------------------------------------ reputação

    /** O cliente avalia o artesão de uma encomenda entregue: de 1 a 5 estrelas, uma vez só. */
    public void review(Player p, long id, int stars) {
        if (stars < 1 || stars > 5) {
            msg.send(p, "encomenda.uso");
            return;
        }
        run(p, c -> {
            Order o = find(c, id);
            if (o == null || o.state() != State.ENTREGUE || !p.getUniqueId().equals(o.client()) || o.artisan() == null) {
                return null;
            }
            if (!Database.query(c, "SELECT 1 FROM life_reviews WHERE order_id = ?", rs -> 1, id).isEmpty()) {
                return null;
            }
            Database.update(c, "INSERT INTO life_reviews (order_id, artisan, client, profession, stars, created) VALUES (?, ?, ?, ?, ?, ?)",
                    id, o.artisan().toString(), o.client().toString(), o.profession().name(), stars, System.currentTimeMillis());
            return o;
        }, o -> {
            if (o == null) {
                msg.send(p, "encomenda.nao_avalia");
                return;
            }
            msg.send(p, "encomenda.avaliou", Messages.vars("artesao", o.artisanName(), "estrelas", stars));
            tell(o.artisan(), "encomenda.aviso_avaliado", "id", id, "estrelas", stars);
        });
    }

    /** O ranking dos artesãos de uma profissão: pela média de estrelas e pelo número de contratos. */
    public void ranking(CommandSender to, Profession prof) {
        run(to, c -> stats(c, prof), map -> {
            List<Stats> list = new ArrayList<>(map.values());
            list.removeIf(s -> s.done() == 0);
            list.sort(OrderRules.RANKING);
            msg.send(to, "encomenda.ranking_titulo", Messages.vars("profissao", professions.name(prof)));
            if (list.isEmpty()) {
                msg.sendRaw(to, "encomenda.ranking_vazio");
                return;
            }
            int pos = 0;
            for (Stats s : list) {
                if (pos >= 10) {
                    break;
                }
                msg.sendRaw(to, "encomenda.ranking_linha", Messages.vars("pos", ++pos, "artesao", s.name(), "estrelas", stars(s),
                        "feitos", s.done(), "cancelados", s.cancelled(), "atrasados", s.late()));
            }
        });
    }

    // ------------------------------------------------------------------ comandos

    private static long number(String s) {
        try {
            return Long.parseLong(s.replace(".", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * /encomenda [quadro | minhas | abrir &lt;profissão&gt; &lt;produto&gt; &lt;orçamento&gt; &lt;dias&gt; [qtd] [nota] |
     * propor &lt;id&gt; &lt;preço&gt; &lt;dias&gt; | propostas &lt;id&gt; | entregar &lt;id&gt; | oferecer &lt;id&gt; &lt;preço&gt; |
     * oferta &lt;id&gt; aceitar|recusar | cancelar &lt;id&gt; | avaliar &lt;id&gt; &lt;1-5&gt;]
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
            case "" -> menu(p);
            case "quadro" -> board(p);
            case "minhas" -> mine(p);
            case "abrir" -> {
                Profession prof = args.length > 4 ? Profession.parse(args[1]) : null;
                if (prof == null) {
                    msg.send(p, "encomenda.uso_abrir");
                    return;
                }
                long qty = args.length > 5 ? number(args[5]) : 1;
                int grade = args.length > 6 ? parseGrade(prof, args[6]) : 0;
                if (grade < 0) {
                    msg.send(p, "encomenda.nota_invalida." + prof.key());
                    return;
                }
                open(p, prof, args[2], (int) Math.max(-1, Math.min(1000, qty)), grade, number(args[3]), (int) Math.max(-1, Math.min(1000, number(args[4]))));
            }
            case "recorrente" -> {
                // /encomenda recorrente <profissão> <produto> <orçamento por entrega> <vezes> <dia|semana> [qtd] [nota]
                Profession prof = args.length > 5 ? Profession.parse(args[1]) : null;
                long period = args.length > 5 ? (args[5].equalsIgnoreCase("dia") ? 86_400_000L : args[5].equalsIgnoreCase("semana") ? 604_800_000L : 0) : 0;
                if (prof == null || period == 0) {
                    msg.send(p, "encomenda.uso_recorrente");
                    return;
                }
                int grade = args.length > 7 ? parseGrade(prof, args[7]) : 0;
                if (grade < 0) {
                    msg.send(p, "encomenda.nota_invalida." + prof.key());
                    return;
                }
                openRecurring(p, prof, args[2], (int) Math.max(-1, Math.min(1000, args.length > 6 ? number(args[6]) : 1)), grade,
                        number(args[3]), (int) Math.max(-1, Math.min(1000, number(args[4]))), period);
            }
            case "banquete" -> {
                // /encomenda banquete <raid> <receita> <orçamento> [nota]
                if (args.length < 4 || id < 0) {
                    msg.send(p, "encomenda.uso_banquete");
                    return;
                }
                int grade = args.length > 4 ? parseGrade(Profession.CULINARIA, args[4]) : 0;
                if (grade < 0) {
                    msg.send(p, "encomenda.nota_invalida.culinaria");
                    return;
                }
                openBanquet(p, id, args[2], number(args[3]), grade);
            }
            case "kit" -> {
                // /encomenda kit <raid> <fórmula> <por inscrito> <orçamento> [nota]
                if (args.length < 5 || id < 0) {
                    msg.send(p, "encomenda.uso_kit");
                    return;
                }
                int grade = args.length > 5 ? parseGrade(Profession.ALQUIMIA, args[5]) : 0;
                if (grade < 0) {
                    msg.send(p, "encomenda.nota_invalida.alquimia");
                    return;
                }
                openKit(p, id, args[2], (int) Math.max(-1, Math.min(1000, number(args[3]))), number(args[4]), grade);
            }
            case "cacada" -> {
                // /encomenda cacada abrir <andar> <pagamento por caçador> <vagas> <dias> | entrar <id>
                String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (sub.equals("entrar") && args.length > 2 && number(args[2]) >= 0) {
                    huntJoin(p, number(args[2]));
                } else if (sub.equals("abrir") && args.length > 5) {
                    huntOpen(p, (int) Math.max(-1, Math.min(1000, number(args[2]))), number(args[3]),
                            (int) Math.max(-1, Math.min(1000, number(args[4]))), (int) Math.max(-1, Math.min(1000, number(args[5]))));
                } else {
                    msg.send(p, "encomenda.uso_cacada");
                }
            }
            case "propor" -> {
                if (args.length < 4 || id < 0) {
                    msg.send(p, "encomenda.uso");
                    return;
                }
                bid(p, id, number(args[2]), (int) Math.max(-1, Math.min(1000, number(args[3]))));
            }
            case "propostas", "entregar", "cancelar" -> {
                if (id < 0) {
                    msg.send(p, "encomenda.uso");
                } else if (op.equals("propostas")) {
                    proposals(p, id);
                } else if (op.equals("entregar")) {
                    deliver(p, id);
                } else {
                    AincradCore.get().menus().confirm(p, msg.get("encomenda.confirmar_cancelar", Messages.vars("id", id)), yes -> cancel(yes, id), no -> { });
                }
            }
            case "oferecer" -> {
                if (args.length < 3 || id < 0) {
                    msg.send(p, "encomenda.uso");
                } else {
                    offer(p, id, number(args[2]));
                }
            }
            case "oferta" -> {
                if (args.length < 3 || id < 0 || !(args[2].equalsIgnoreCase("aceitar") || args[2].equalsIgnoreCase("recusar"))) {
                    msg.send(p, "encomenda.uso");
                } else {
                    answerOffer(p, id, args[2].equalsIgnoreCase("aceitar"));
                }
            }
            case "aceitar", "recusar" -> {
                if (id < 0) {
                    msg.send(p, "encomenda.uso");
                } else {
                    answerDirect(p, id, op.equals("aceitar"));
                }
            }
            case "material" -> {
                if (id < 0) {
                    msg.send(p, "encomenda.uso");
                } else {
                    depositMaterial(p, id);
                }
            }
            case "caixa" -> {
                if (id < 0) {
                    msg.send(p, "encomenda.uso");
                } else {
                    withdrawBox(p, id);
                }
            }
            case "avaliar" -> {
                if (args.length < 3 || id < 0) {
                    msg.send(p, "encomenda.uso");
                } else {
                    review(p, id, (int) Math.max(-1, Math.min(9, number(args[2]))));
                }
            }
            default -> msg.send(p, "encomenda.uso");
        }
    }

    /** O menu do quadro (também abre ao clicar na bancada "quadro de encomendas"). */
    public void menu(Player p) {
        if (!hooks.hasEconomy()) {
            msg.send(p, "encomenda.sem_economia");
            return;
        }
        Menu menu = Menu.of(msg.get("encomenda.menu_titulo")).rows(3);
        menu.item(Menu.Button.of(new ItemStack(Material.BOOKSHELF), msg.get("encomenda.menu_quadro"), this::board).closing()
                .lore(msg.list("encomenda.menu_quadro_lore")));
        menu.item(Menu.Button.of(new ItemStack(Material.WRITABLE_BOOK), msg.get("encomenda.menu_abrir"),
                pl -> msg.send(pl, "encomenda.uso_abrir")).closing().lore(msg.list("encomenda.menu_abrir_lore")));
        menu.item(Menu.Button.of(new ItemStack(Material.CHEST), msg.get("encomenda.menu_minhas"), this::mine).closing()
                .lore(msg.list("encomenda.menu_minhas_lore")));
        AincradCore.get().menus().open(p, menu);
    }

    /** /artesaos [profissão] */
    public void rankingCommand(CommandSender sender, String[] args) {
        Profession prof = args.length == 0 ? Profession.FERREIRO : Profession.parse(args[0]);
        if (prof == null) {
            msg.send(sender, "encomenda.uso_artesaos");
            return;
        }
        ranking(sender, prof);
    }

    // ------------------------------------------------------------------ contratos especiais

    /**
     * Fornecimento recorrente: o mesmo pedido, várias entregas, uma a cada dia ou semana. A
     * custódia guarda todas; cada entrega é paga na hora em que acontece.
     */
    public void openRecurring(Player p, Profession prof, String product, int quantity, int minGrade, long budget, int rounds, long period) {
        int max = config.integer("encomendas.recorrente.maximo_de_entregas", 30, 2, 365);
        int maxDays = config.integer("encomendas.prazo_maximo_dias", 14, 1, 365);
        if (rounds < 2 || rounds > max) {
            msg.send(p, "encomenda.recorrente_vezes", Messages.vars("maximo", max));
            return;
        }
        // O pedido fica no quadro pelo prazo máximo; as entregas começam quando uma proposta é aceita.
        create(p, prof, product, quantity, minGrade, budget, System.currentTimeMillis() + maxDays * 86_400_000L, Kind.RECORRENTE, null,
                rounds, period, "encomenda.aberta_recorrente");
    }

    /** Uma entrega de um fornecimento recorrente: paga na hora; a última fecha o contrato. */
    private void deliverRound(Player p, Order o, ItemStack item) {
        boolean last = o.roundsDone() + 1 >= o.rounds();
        long now = System.currentTimeMillis();
        long next = OrderRules.nextDue(o.due(), o.period());
        // Só conta se ninguém entregou esta mesma rodada no mesmo instante.
        run(p, c -> Database.update(c, "UPDATE life_orders SET rounds_done = rounds_done + 1, due = ?, state = ?, ended = ? "
                + "WHERE id = ? AND state = 'CONTRATADO' AND rounds_done = ?", next, last ? State.ENTREGUE.name() : State.CONTRATADO.name(),
                last ? now : 0, o.id(), o.roundsDone()) > 0, ok -> {
            if (!ok) {
                give(p, item);
                msg.send(p, "encomenda.nao_e_seu_contrato");
                return;
            }
            hooks.mail(o.client(), item, "encomenda");
            long fee = OrderRules.fee(o.price(), feePercent());
            hooks.release(o.hold(), o.artisan(), o.price() - fee, "life:encomenda_mao_de_obra")
                    .thenCompose(x -> last ? hooks.burn(o.hold()).thenApply(v -> true) : hooks.burn(o.hold(), fee));
            var vars = Messages.vars("id", o.id(), "preco", col(o.price() - fee), "feitas", o.roundsDone() + 1, "vezes", o.rounds(),
                    "tempo", TimeFormat.duration(Math.max(0, next - now), 2));
            msg.send(p, last ? "encomenda.entregou_ultima" : "encomenda.entregou_rodada", vars);
            tell(o.client(), last ? "encomenda.aviso_entregue" : "encomenda.aviso_rodada", "id", o.id(), "artesao", p.getName(),
                    "feitas", o.roundsDone() + 1, "vezes", o.rounds());
        }, () -> give(p, item));
    }

    /**
     * Banquete de raid: o líder de uma raid marcada no quadro do AincradBosses encomenda um prato
     * para cada inscrito, com prazo até a hora da raid.
     */
    public void openBanquet(Player p, long raidId, String recipe, long budget, int minGrade) {
        Hooks.Raid raid = hooks.raid(raidId);
        long now = System.currentTimeMillis();
        if (raid == null || !p.getUniqueId().equals(raid.leader()) || raid.at() <= now) {
            msg.send(p, "encomenda.banquete_sem_raid");
            return;
        }
        if (raid.members().isEmpty() || raid.members().size() > 64) {
            msg.send(p, "encomenda.banquete_inscritos");
            return;
        }
        // "period" guarda quantas unidades cada inscrito recebe (1 no banquete).
        create(p, Profession.CULINARIA, recipe, raid.members().size(), minGrade, budget, raid.at(), Kind.BANQUETE, String.valueOf(raidId),
                1, 1, "encomenda.aberta_banquete");
    }

    /**
     * Kit de raid: como o banquete, mas de poções — o líder encomenda um conjunto por inscrito
     * (ex.: 5 poções de cura para cada um), com prazo até a hora da raid.
     */
    public void openKit(Player p, long raidId, String formula, int each, long budget, int minGrade) {
        Hooks.Raid raid = hooks.raid(raidId);
        long now = System.currentTimeMillis();
        if (raid == null || !p.getUniqueId().equals(raid.leader()) || raid.at() <= now) {
            msg.send(p, "encomenda.banquete_sem_raid");
            return;
        }
        int total = raid.members().size() * each;
        if (each < 1 || raid.members().isEmpty() || total > 64) {
            msg.send(p, "encomenda.kit_tamanho");
            return;
        }
        create(p, Profession.ALQUIMIA, formula, total, minGrade, budget, raid.at(), Kind.BANQUETE, String.valueOf(raidId), 1, each,
                "encomenda.aberta_kit");
    }

    /** Entrega o banquete: um prato para cada inscrito na raid agora; o que sobrar vai para quem encomendou. */
    private void serveBanquet(Order o, ItemStack dishes) {
        Hooks.Raid raid = null;
        try {
            raid = o.ref() == null ? null : hooks.raid(Long.parseLong(o.ref()));
        } catch (NumberFormatException e) {
            // referência estragada: tudo vai para o cliente
        }
        List<UUID> guests = raid == null ? List.of() : raid.members();
        int each = (int) Math.max(1, o.period());
        int served = OrderRules.banquetServed(dishes.getAmount(), guests.size(), each);
        for (int i = 0; i < served; i++) {
            ItemStack one = dishes.clone();
            one.setAmount(each);
            hooks.mail(guests.get(i), one, "banquete");
            tell(guests.get(i), "encomenda.aviso_banquete", "id", o.id(), "cliente", o.clientName());
        }
        int left = OrderRules.banquetLeftover(dishes.getAmount(), guests.size(), each);
        if (left > 0) {
            ItemStack rest = dishes.clone();
            rest.setAmount(left);
            hooks.mail(o.client(), rest, "encomenda");
        }
    }

    /**
     * Caçada ao Senhor do Lago: o pescador que vai fisgar o lendário de um andar oferece um
     * pagamento a cada aventureiro que ajudar a derrotá-lo. O Col de todas as vagas fica em custódia.
     */
    public void huntOpen(Player p, int floor, long pay, int slots, int days) {
        FishingService f = fishing.get();
        com.aincrad.life.fishing.FishingRegistry.Fish lord = f == null ? null : f.legendaryOf(floor);
        int maxDays = config.integer("encomendas.prazo_maximo_dias", 14, 1, 365);
        if (lord == null) {
            msg.send(p, "encomenda.cacada_sem_lendario");
            return;
        }
        if (slots < 1 || slots > 64 || days < 1 || days > maxDays) {
            msg.send(p, "encomenda.valores", Messages.vars("dias", maxDays));
            return;
        }
        // As "entregas" da caçada são as vagas: a custódia guarda o pagamento de cada caçador.
        create(p, Profession.PESCA, lord.id(), slots, 0, pay, System.currentTimeMillis() + days * 86_400_000L, Kind.CACADA,
                String.valueOf(floor), slots, 0, "encomenda.aberta_cacada");
    }

    /** Um aventureiro entra numa caçada aberta, enquanto houver vaga. */
    public void huntJoin(Player p, long id) {
        String me = p.getUniqueId().toString();
        run(p, c -> {
            Order o = find(c, id);
            if (o == null || o.kind() != Kind.CACADA || o.state() != State.ABERTO || o.expires() < System.currentTimeMillis()
                    || p.getUniqueId().equals(o.client())) {
                return "encomenda.cacada_nao_aberta";
            }
            List<String> in = Database.query(c, "SELECT hunter FROM life_hunters WHERE order_id = ?", rs -> rs.getString(1), id);
            if (in.contains(me)) {
                return "encomenda.cacada_ja_entrou";
            }
            if (in.size() >= o.quantity()) {
                return "encomenda.cacada_cheia";
            }
            Database.update(c, "INSERT INTO life_hunters (order_id, hunter, hunter_name) VALUES (?, ?, ?)", id, me, p.getName());
            tellLater.add(new Object[]{o.client(), id, p.getName(), in.size() + 1, o.quantity()});
            return "encomenda.cacada_entrou";
        }, key -> {
            msg.send(p, key, Messages.vars("id", id));
            for (Object[] t; (t = tellLater.poll()) != null; ) {
                tell((UUID) t[0], "encomenda.aviso_cacador", "id", t[1], "jogador", t[2], "dentro", t[3], "vagas", t[4]);
            }
        });
    }

    /** Avisos montados dentro de uma consulta, enviados depois na thread principal. */
    private final java.util.concurrent.ConcurrentLinkedQueue<Object[]> tellLater = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /**
     * O Senhor do Lago de um andar foi derrotado. Se quem o fisgou tem uma caçada aberta para o
     * andar, ela fecha: recebe cada inscrito que feriu o chefe; o Col das vagas que sobraram volta.
     */
    public void onLegendary(UUID fisher, int floor, Collection<UUID> fought) {
        db.async(c -> {
            List<Order> open = Database.query(c, "SELECT " + COLUMNS + " FROM life_orders WHERE kind = 'CACADA' AND client = ? AND ref = ? "
                    + "AND state = 'ABERTO' ORDER BY id ASC", OrderService::order, fisher.toString(), String.valueOf(floor));
            if (open.isEmpty()) {
                return null;
            }
            Order o = open.get(0);
            List<UUID> enrolled = new ArrayList<>();
            for (String h : Database.query(c, "SELECT hunter FROM life_hunters WHERE order_id = ?", rs -> rs.getString(1), o.id())) {
                UUID u = uuid(h);
                if (u != null) {
                    enrolled.add(u);
                }
            }
            // Sem inscritos, a caçada continua aberta para a próxima vez.
            if (enrolled.isEmpty() || !move(c, o.id(), State.ABERTO, State.ENTREGUE, null)) {
                return null;
            }
            return new Object[]{o, enrolled};
        }).whenComplete((data, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Encomendas: não foi possível fechar a caçada", err);
                return;
            }
            if (data == null) {
                return;
            }
            Order o = (Order) data[0];
            @SuppressWarnings("unchecked")
            List<UUID> paid = OrderRules.paidHunters((List<UUID>) data[1], fought);
            // Um pagamento de cada vez, e só depois o resto volta ao pescador.
            CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
            for (UUID h : paid) {
                chain = chain.thenCompose(x -> hooks.release(o.hold(), h, o.budget(), "life:cacada"));
                tell(h, "encomenda.aviso_cacada_paga", "id", o.id(), "preco", col(o.budget()));
            }
            chain.thenCompose(x -> hooks.refund(o.hold(), "life:cacada_sobra"));
            tell(o.client(), "encomenda.aviso_cacada_fechada", "id", o.id(), "pagos", paid.size(), "vagas", o.quantity(),
                    "preco", col(OrderRules.total(o.budget(), paid.size())));
        }));
    }

    // ------------------------------------------------------------------ catálogo e contrato direto

    /** Uma linha do catálogo de um artesão. */
    private record Entry(UUID artisan, String artisanName, Profession profession, String product, long price, int days) {
    }

    private static Entry entry(ResultSet rs) throws SQLException {
        Profession prof = Profession.parse(rs.getString(3));
        return new Entry(uuid(rs.getString(1)), rs.getString(2), prof == null ? Profession.FERREIRO : prof, rs.getString(4), rs.getLong(5), rs.getInt(6));
    }

    /** O artesão põe (ou troca) um item do catálogo dele: o preço da mão de obra e o prazo padrão. */
    public void catalogSet(Player p, Profession prof, String product, long price, int days) {
        String name = productName(prof, product);
        int maxDays = config.integer("encomendas.prazo_maximo_dias", 14, 1, 365);
        if (name == null || !canMake(p, prof, product)) {
            msg.send(p, "encomenda.nao_sabe_fazer");
            return;
        }
        if (price <= 0 || days < 1 || days > maxDays) {
            msg.send(p, "encomenda.valores", Messages.vars("dias", maxDays));
            return;
        }
        String id = p.getUniqueId().toString();
        String key = product.toLowerCase(Locale.ROOT);
        run(p, c -> {
            Database.update(c, "DELETE FROM life_catalog WHERE artisan = ? AND profession = ? AND product = ?", id, prof.name(), key);
            return Database.update(c, "INSERT INTO life_catalog (artisan, artisan_name, profession, product, price, days) VALUES (?, ?, ?, ?, ?, ?)",
                    id, p.getName(), prof.name(), key, price, days);
        }, n -> msg.send(p, "encomenda.catalogo_posto", Messages.vars("produto", name, "preco", col(price), "dias", days)));
    }

    public void catalogRemove(Player p, Profession prof, String product) {
        run(p, c -> Database.update(c, "DELETE FROM life_catalog WHERE artisan = ? AND profession = ? AND product = ?",
                p.getUniqueId().toString(), prof.name(), product.toLowerCase(Locale.ROOT)),
                n -> msg.send(p, n > 0 ? "encomenda.catalogo_tirado" : "encomenda.catalogo_nao_tem"));
    }

    /**
     * O catálogo de um artesão (pelo nome), para contratar direto. Funciona com ele offline ou em
     * outro servidor: o catálogo está no banco.
     */
    public void catalog(Player p, String artisanName) {
        run(p, c -> Database.query(c, "SELECT artisan, artisan_name, profession, product, price, days FROM life_catalog "
                + "WHERE LOWER(artisan_name) = ? ORDER BY profession, price", OrderService::entry, artisanName.toLowerCase(Locale.ROOT)), list -> {
            if (list.isEmpty()) {
                msg.send(p, "encomenda.catalogo_vazio", Messages.vars("artesao", artisanName));
                return;
            }
            boolean mine = p.getUniqueId().equals(list.get(0).artisan());
            Menu menu = Menu.of(msg.get("encomenda.catalogo_titulo", Messages.vars("artesao", list.get(0).artisanName()))).rows(6);
            for (Entry e : list) {
                String name = productName(e.profession(), e.product());
                if (name == null) {
                    continue;
                }
                List<Component> lore = new ArrayList<>();
                lore.add(msg.get("encomenda.catalogo_linha", Messages.vars("profissao", professions.name(e.profession()),
                        "preco", col(e.price()), "dias", e.days())));
                lore.add(msg.get(mine ? "encomenda.catalogo_seu" : "encomenda.catalogo_clique"));
                menu.item(Menu.Button.of(new ItemStack(icon(e.profession())), Messages.parse("<white><nome>", Messages.vars("nome", name)), pl -> {
                    if (!mine) {
                        AincradCore.get().menus().confirm(pl, msg.get("encomenda.confirmar_contratar", Messages.vars("artesao", e.artisanName(),
                                "produto", name, "preco", col(e.price()), "dias", e.days())), yes -> hire(yes, e), no -> { });
                    }
                }).closing().lore(lore));
            }
            AincradCore.get().menus().open(p, menu);
        });
    }

    /**
     * Contrato direto: o cliente escolheu um item do catálogo. O preço vai para a custódia e o
     * artesão tem um prazo (24 h) para aceitar ou recusar; sem resposta, tudo volta ao cliente.
     */
    private void hire(Player p, Entry e) {
        int limit = config.integer("encomendas.pedidos_por_jogador", 5, 1, 1000);
        String id = p.getUniqueId().toString();
        // O preço do catálogo é relido na hora: vale o que está lá agora.
        run(p, c -> {
            int open = Database.query(c, "SELECT COUNT(*) FROM life_orders WHERE client = ? AND state IN ('ABERTO', 'AGUARDANDO', 'CONTRATADO', 'OFERTA')",
                    rs -> rs.getInt(1), id).get(0);
            List<Entry> now = Database.query(c, "SELECT artisan, artisan_name, profession, product, price, days FROM life_catalog "
                    + "WHERE artisan = ? AND profession = ? AND product = ?", OrderService::entry, e.artisan().toString(), e.profession().name(), e.product());
            return new Object[]{open, now.isEmpty() ? null : now.get(0)};
        }, data -> {
            Entry now = (Entry) data[1];
            if (now == null || now.price() != e.price() || now.days() != e.days()) {
                msg.send(p, "encomenda.catalogo_mudou");
                return;
            }
            if ((Integer) data[0] >= limit) {
                msg.send(p, "encomenda.limite", Messages.vars("limite", limit));
                return;
            }
            String hold = p.isOnline() ? hooks.hold(p, e.price(), "life:contrato_direto") : null;
            if (hold == null) {
                msg.send(p, "encomenda.sem_col", Messages.vars("preco", col(e.price())));
                return;
            }
            long created = System.currentTimeMillis();
            long answerBy = created + config.integer("encomendas.resposta_horas", 24, 1, 720) * 3_600_000L;
            String name = productName(e.profession(), e.product());
            // Enquanto aguarda a resposta, "due" guarda o prazo do catálogo em dias; vira data quando o artesão aceita.
            db.async(c -> {
                Database.update(c, "INSERT INTO life_orders (client, client_name, profession, product, quantity, min_grade, budget, hold, "
                                + "expires, state, created, artisan, artisan_name, price, due, offer, ended, kind, rounds, rounds_done, period) "
                                + "VALUES (?, ?, ?, ?, 1, 0, ?, ?, ?, 'AGUARDANDO', ?, ?, ?, ?, ?, 0, 0, 'PEDIDO', 1, 0, 0)",
                        id, p.getName(), e.profession().name(), e.product(), e.price(), hold, answerBy, created,
                        e.artisan().toString(), e.artisanName(), e.price(), e.days());
                return Database.query(c, "SELECT id FROM life_orders WHERE hold = ?", rs -> rs.getLong(1), hold).get(0);
            }).whenComplete((orderId, err) -> AincradCore.get().runSync(() -> {
                if (err != null) {
                    plugin.getLogger().log(Level.WARNING, "Encomendas: não foi possível gravar o contrato direto", err);
                    hooks.refund(hold, "life:contrato_direto_estorno");
                    msg.send(p, "geral.erro");
                    return;
                }
                msg.send(p, "encomenda.contratou_direto", Messages.vars("id", orderId, "artesao", e.artisanName(), "produto", name,
                        "preco", col(e.price()), "horas", config.integer("encomendas.resposta_horas", 24, 1, 720)));
                tell(e.artisan(), "encomenda.aviso_direto", "id", orderId, "cliente", p.getName(), "produto", name == null ? e.product() : name,
                        "preco", col(e.price()));
            }));
        });
    }

    /** O artesão aceita ou recusa um contrato direto. Recusado, o Col e os materiais voltam ao cliente. */
    public void answerDirect(Player p, long id, boolean accept) {
        long now = System.currentTimeMillis();
        // Um contrato de serviço entrega o item do cliente: precisa de lugar no inventário.
        boolean room = p.getInventory().firstEmpty() >= 0;
        run(p, c -> {
            Order o = find(c, id);
            if (o == null || o.state() != State.AGUARDANDO || !p.getUniqueId().equals(o.artisan()) || o.expires() < now) {
                return null;
            }
            if (accept) {
                if (o.kind() == Kind.SERVICO && (!room || services == null)) {
                    return new Closed(null, List.of());
                }
                // "due" guardava os dias do catálogo (no serviço, "period" guarda o prazo): agora vira a data de entrega.
                long due = o.kind() == Kind.SERVICO ? now + o.period() : now + Math.max(1, o.due()) * 86_400_000L;
                boolean ok = Database.update(c, "UPDATE life_orders SET state = 'CONTRATADO', due = ? WHERE id = ? AND state = 'AGUARDANDO'", due, id) > 0;
                if (!ok) {
                    return null;
                }
                // No serviço, o item continua guardado na caixa (é o que volta ao dono se o prazo vencer); o artesão recebe um igual, preso.
                return new Closed(o, o.kind() != Kind.SERVICO ? List.of() : Database.query(c,
                        "SELECT owner, item FROM life_contract_box WHERE order_id = ? AND kind = 'SERVICO'",
                        rs -> new String[]{rs.getString(1), rs.getString(2)}, id));
            }
            return move(c, id, State.AGUARDANDO, State.CANCELADO, p.getUniqueId()) ? new Closed(o, emptyBox(c, id)) : null;
        }, closed -> {
            if (closed == null) {
                msg.send(p, "encomenda.sem_contrato_direto");
                return;
            }
            Order o = closed.order();
            if (o == null) {
                msg.send(p, "servico.sem_espaco");
                return;
            }
            if (accept && o.kind() == Kind.SERVICO) {
                for (String[] row : closed.box()) {
                    ItemStack it = unpack(row[1]);
                    if (it != null) {
                        give(p, services.bind(it, id, o.clientName()));
                    }
                }
                msg.send(p, "servico.aceitou", Messages.vars("id", id, "cliente", o.clientName(),
                        "tempo", TimeFormat.duration(o.period(), 2)));
                tell(o.client(), "encomenda.aviso_direto_aceito", "id", id, "artesao", p.getName());
            } else if (accept) {
                msg.send(p, "encomenda.aceitou_direto", Messages.vars("id", id, "dias", Math.max(1, o.due())));
                tell(o.client(), "encomenda.aviso_direto_aceito", "id", id, "artesao", p.getName());
            } else {
                returnBox(closed.box());
                hooks.refund(o.hold(), "life:contrato_direto_recusado");
                msg.send(p, "encomenda.recusou_direto", Messages.vars("id", id));
                tell(o.client(), "encomenda.aviso_direto_recusado", "id", id, "artesao", p.getName());
            }
        });
    }

    // ------------------------------------------------------------------ contrato de serviço

    private static String plainName(ItemStack it) {
        String name = it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName())
                : it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.length() > 60 ? name.substring(0, 60) : name;
    }

    /**
     * Contrato de serviço: o cliente manda o item que está na mão para um artesão trabalhar nele
     * (aprimorar, reparar...) por um preço. O item e o Col ficam guardados até o artesão aceitar;
     * aceito, o item vai para o inventário dele, preso: não pode ser guardado, largado nem
     * vendido. Sem resposta, sem entrega no prazo ou com cancelamento, o item volta ao dono como
     * foi entregue, e o Col também.
     *
     * @param hours prazo para o artesão devolver o item pronto, depois de aceitar
     */
    public void service(Player p, Profession prof, String artisanName, long price, int hours) {
        if (services == null) {
            msg.send(p, "encomenda.sem_economia");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        int maxHours = config.integer("encomendas.servico.prazo_maximo_horas", 168, 1, 8760);
        if (hand.getType().isAir() || hand.getAmount() != 1 || services.bound(hand)) {
            msg.send(p, "servico.um_item");
            return;
        }
        if (price <= 0 || hours < 1 || hours > maxHours) {
            msg.send(p, "servico.valores", Messages.vars("horas", maxHours));
            return;
        }
        org.bukkit.OfflinePlayer artisan = Bukkit.getPlayerExact(artisanName) != null ? Bukkit.getPlayerExact(artisanName)
                : Bukkit.getOfflinePlayerIfCached(artisanName);
        if (artisan == null || artisan.getUniqueId().equals(p.getUniqueId())) {
            msg.send(p, "servico.artesao_desconhecido", Messages.vars("jogador", artisanName));
            return;
        }
        String shown = artisan.getName() == null ? artisanName : artisan.getName();
        int limit = config.integer("encomendas.pedidos_por_jogador", 5, 1, 1000);
        String id = p.getUniqueId().toString();
        ItemStack item = hand.clone();
        run(p, c -> Database.query(c, "SELECT COUNT(*) FROM life_orders WHERE client = ? AND state IN ('ABERTO', 'AGUARDANDO', 'CONTRATADO', 'OFERTA')",
                rs -> rs.getInt(1), id).get(0), count -> {
            if (count >= limit) {
                msg.send(p, "encomenda.limite", Messages.vars("limite", limit));
                return;
            }
            // O item precisa ser o mesmo que estava na mão quando o comando foi dado.
            if (!p.isOnline() || !p.getInventory().getItemInMainHand().equals(item)) {
                msg.send(p, "servico.um_item");
                return;
            }
            String hold = hooks.hold(p, price, "life:servico");
            if (hold == null) {
                msg.send(p, "encomenda.sem_col", Messages.vars("preco", col(price)));
                return;
            }
            p.getInventory().setItemInMainHand(null);
            long created = System.currentTimeMillis();
            long answerBy = created + config.integer("encomendas.resposta_horas", 24, 1, 720) * 3_600_000L;
            String packed = pack(item);
            db.async(c -> {
                // "period" guarda o prazo do serviço; vira data de entrega quando o artesão aceita.
                Database.update(c, "INSERT INTO life_orders (client, client_name, profession, product, quantity, min_grade, budget, hold, "
                                + "expires, state, created, artisan, artisan_name, price, due, offer, ended, kind, ref, rounds, rounds_done, period) "
                                + "VALUES (?, ?, ?, 'servico', 1, 0, ?, ?, ?, 'AGUARDANDO', ?, ?, ?, ?, 0, 0, 0, 'SERVICO', ?, 1, 0, ?)",
                        id, p.getName(), prof.name(), price, hold, answerBy, created, artisan.getUniqueId().toString(), shown, price,
                        plainName(item), hours * 3_600_000L);
                long orderId = Database.query(c, "SELECT id FROM life_orders WHERE hold = ?", rs -> rs.getLong(1), hold).get(0);
                Database.update(c, "INSERT INTO life_contract_box (order_id, owner, kind, item, created) VALUES (?, ?, 'SERVICO', ?, ?)",
                        orderId, id, packed, created);
                return orderId;
            }).whenComplete((orderId, err) -> AincradCore.get().runSync(() -> {
                if (err != null) {
                    // Nada foi gravado: o item e o Col voltam na hora.
                    plugin.getLogger().log(Level.WARNING, "Encomendas: não foi possível gravar o contrato de serviço", err);
                    hooks.refund(hold, "life:servico_estorno");
                    give(p, item);
                    msg.send(p, "geral.erro");
                    return;
                }
                msg.send(p, "servico.enviado", Messages.vars("id", orderId, "artesao", shown, "item", plainName(item), "preco", col(price),
                        "horas", hours, "resposta", config.integer("encomendas.resposta_horas", 24, 1, 720)));
                tell(artisan.getUniqueId(), "servico.aviso_pedido", "id", orderId, "cliente", p.getName(), "item", plainName(item),
                        "preco", col(price), "horas", hours, "profissao", professions.name(prof));
            }));
        });
    }

    /** O artesão devolve o item do cliente, pronto: ele vai para a caixa de retirada do dono e o artesão recebe. */
    private void deliverService(Player p, Order o) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (services == null || !OrderRules.serviceItem(o.id(), services.contract(hand))) {
            msg.send(p, "servico.item_errado", Messages.vars("id", o.id()));
            return;
        }
        ItemStack done = services.unbind(hand);
        p.getInventory().setItemInMainHand(null);
        run(p, c -> {
            if (!move(c, o.id(), State.CONTRATADO, State.ENTREGUE, null)) {
                return false;
            }
            // A cópia guardada na caixa não é mais necessária: o item trabalhado é o que vai para o dono.
            Database.update(c, "DELETE FROM life_contract_box WHERE order_id = ? AND kind = 'SERVICO'", o.id());
            return true;
        }, ok -> {
            if (!ok) {
                // O contrato acabou neste instante (prazo ou cancelamento): o dono já recebe a cópia guardada.
                msg.send(p, "encomenda.nao_e_seu_contrato");
                return;
            }
            hooks.mail(o.client(), done, "encomenda");
            settle(o, o.price());
            long pay = OrderRules.pay(o.price(), feePercent());
            msg.send(p, "encomenda.entregou", Messages.vars("id", o.id(), "preco", col(pay), "taxa", col(o.price() - pay)));
            tell(o.client(), "encomenda.aviso_entregue", "id", o.id(), "artesao", p.getName());
        }, () -> give(p, services.bind(done, o.id(), o.clientName())));
    }

    /** /contrato &lt;profissão&gt; &lt;artesão&gt; [preço] [horas]: com um item na mão, manda o item para ele trabalhar; sem item, abre o catálogo dele. */
    public void contractCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        if (!hooks.hasEconomy()) {
            msg.send(p, "encomenda.sem_economia");
            return;
        }
        Profession prof = args.length > 1 ? Profession.parse(args[0]) : null;
        if (prof == null) {
            msg.send(p, "servico.uso");
            return;
        }
        if (p.getInventory().getItemInMainHand().getType().isAir()) {
            // Sem item na mão: é para ele criar algo novo, pelo catálogo dele.
            catalog(p, args[1]);
            return;
        }
        if (args.length < 3) {
            msg.send(p, "servico.uso");
            return;
        }
        int hours = args.length > 3 ? (int) Math.max(-1, Math.min(100_000, number(args[3]))) : config.integer("encomendas.servico.prazo_horas", 48, 1, 8760);
        service(p, prof, args[1], number(args[2]), hours);
    }

    // ------------------------------------------------------------------ caixa do contrato

    /** Guarda um item na caixa do contrato (material do cliente ou de um fornecedor). Roda dentro de uma consulta. */
    static void boxAdd(Connection c, long orderId, UUID owner, ItemStack item) throws SQLException {
        Database.update(c, "INSERT INTO life_contract_box (order_id, owner, kind, item, created) VALUES (?, ?, 'MATERIAL', ?, ?)",
                orderId, owner.toString(), pack(item), System.currentTimeMillis());
    }

    /**
     * O cliente deposita na caixa do contrato um material que já tem (o item da mão). Se o
     * contrato for cancelado, o que não foi usado volta para ele.
     */
    public void depositMaterial(Player p, long id) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg.send(p, "encomenda.material_mao_vazia");
            return;
        }
        ItemStack item = hand.clone();
        p.getInventory().setItemInMainHand(null);
        run(p, c -> {
            Order o = find(c, id);
            if (o == null || !p.getUniqueId().equals(o.client()) || (o.state() != State.AGUARDANDO && o.state() != State.CONTRATADO)) {
                return false;
            }
            boxAdd(c, id, p.getUniqueId(), item);
            return true;
        }, ok -> {
            if (!ok) {
                give(p, item);
                msg.send(p, "encomenda.material_nao_pode");
                return;
            }
            msg.send(p, "encomenda.material_depositado", Messages.vars("id", id, "qtd", item.getAmount()));
        }, () -> give(p, item));
    }

    /** O artesão de um contrato em produção retira os materiais da caixa dele para trabalhar. */
    public void withdrawBox(Player p, long id) {
        run(p, c -> {
            Order o = find(c, id);
            if (o == null || o.state() != State.CONTRATADO || !p.getUniqueId().equals(o.artisan())) {
                return null;
            }
            List<String> items = Database.query(c, "SELECT item FROM life_contract_box WHERE order_id = ? AND kind = 'MATERIAL'", rs -> rs.getString(1), id);
            Database.update(c, "DELETE FROM life_contract_box WHERE order_id = ? AND kind = 'MATERIAL'", id);
            return items;
        }, items -> {
            if (items == null) {
                msg.send(p, "encomenda.nao_e_seu_contrato");
                return;
            }
            int n = 0;
            for (String raw : items) {
                ItemStack it = unpack(raw);
                if (it != null) {
                    give(p, it);
                    n++;
                }
            }
            msg.send(p, n == 0 ? "encomenda.caixa_vazia" : "encomenda.caixa_retirada", Messages.vars("id", id, "qtd", n));
        });
    }

    /** /catalogo [adicionar <profissão> <produto> <preço> <dias> | remover <profissão> <produto>] */
    public void catalogCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        if (!hooks.hasEconomy()) {
            msg.send(p, "encomenda.sem_economia");
            return;
        }
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        Profession prof = args.length > 2 ? Profession.parse(args[1]) : null;
        if (op.isEmpty()) {
            catalog(p, p.getName());
        } else if (op.equals("adicionar") && prof != null && args.length > 4) {
            catalogSet(p, prof, args[2], number(args[3]), (int) Math.max(-1, Math.min(1000, number(args[4]))));
        } else if (op.equals("remover") && prof != null) {
            catalogRemove(p, prof, args[2]);
        } else {
            msg.send(p, "encomenda.uso_catalogo");
        }
    }

    /** /contratar <artesão> */
    public void hireCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
        } else if (!hooks.hasEconomy()) {
            msg.send(p, "encomenda.sem_economia");
        } else if (args.length == 0) {
            msg.send(p, "encomenda.uso_contratar");
        } else {
            catalog(p, args[0]);
        }
    }

    // ------------------------------------------------------------------ admin

    /** Admin: mostra uma encomenda. */
    public void adminView(CommandSender to, long id) {
        run(to, c -> find(c, id), o -> {
            if (o == null) {
                msg.send(to, "encomenda.admin_nao_existe");
                return;
            }
            String name = productName(o.profession(), o.product());
            msg.send(to, "encomenda.admin_ver", Messages.vars("id", o.id(), "estado", o.state().name(), "cliente", o.clientName(),
                    "artesao", o.artisanName() == null ? "—" : o.artisanName(), "qtd", o.quantity(),
                    "produto", name == null ? o.product() : name, "orcamento", col(o.budget()), "preco", col(o.price())));
        });
    }

    /** Admin: cancela uma encomenda em qualquer estado não terminado e devolve tudo (Col ao cliente, cada item da caixa a quem o pôs). */
    public void adminCancel(CommandSender to, long id) {
        run(to, c -> {
            Order o = find(c, id);
            if (o == null || o.state().finished() || !move(c, id, o.state(), State.CANCELADO, null)) {
                return null;
            }
            return new Closed(o, emptyBox(c, id));
        }, closed -> {
            if (closed == null) {
                msg.send(to, "encomenda.admin_nao_existe");
                return;
            }
            Order o = closed.order();
            hooks.refund(o.hold(), "life:encomenda_admin");
            returnBox(closed.box());
            msg.send(to, "encomenda.admin_cancelou", Messages.vars("id", id));
            tell(o.client(), "encomenda.aviso_cancelada", "id", id, "jogador", to.getName());
            tell(o.artisan(), "encomenda.aviso_cancelada", "id", id, "jogador", to.getName());
        });
    }
}
