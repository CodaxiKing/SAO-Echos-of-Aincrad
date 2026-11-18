package com.aincrad.life.stall;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.util.TimeFormat;
import com.aincrad.life.fishing.FishingService;
import com.aincrad.life.hook.Hooks;
import com.aincrad.life.model.Profession;
import com.aincrad.life.profession.ProfessionService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Barracas: um bloco, dentro da casa do dono, onde ele põe itens à venda com preço e estoque. Os
 * clientes compram clicando no bloco, mesmo com o dono offline ou em outro servidor, e o Col
 * chega a ele. Serve de barraca do cozinheiro, banca de peixe e botica. O peixe tem frescor: vale
 * o preço cheio por um tempo depois de pescado e uma fração depois.
 */
public final class StallService implements Listener {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS life_stalls ({AUTO_ID}, server VARCHAR(64) NOT NULL, world VARCHAR(64) NOT NULL, x INT NOT NULL, "
                    + "y INT NOT NULL, z INT NOT NULL, owner VARCHAR(36) NOT NULL, owner_name VARCHAR(32) NOT NULL);"
                    + "CREATE TABLE IF NOT EXISTS life_stall_stock ({AUTO_ID}, stall_id BIGINT NOT NULL, item TEXT NOT NULL, amount INT NOT NULL, "
                    + "price BIGINT NOT NULL, fresh_until BIGINT NOT NULL, added BIGINT NOT NULL)");

    /** Uma barraca posta no mundo. */
    public record Stall(long id, String world, int x, int y, int z, UUID owner, String ownerName) {
        boolean at(Block b) {
            return x == b.getX() && y == b.getY() && z == b.getZ() && world.equals(b.getWorld().getName());
        }
    }

    /** Uma linha do estoque: um item (uma unidade), quantas há, o preço cheio e até quando está fresco. */
    private record Line(long id, ItemStack item, int amount, long price, long freshUntil) {
    }

    private final Plugin plugin;
    private final Messages msg;
    private final ConfigFile config;
    private final Hooks hooks;
    private final Database db;
    private final ProfessionService professions;
    private final Supplier<FishingService> fishing;
    private final Map<Long, Stall> stalls = new ConcurrentHashMap<>();

    public StallService(Plugin plugin, Messages msg, ConfigFile config, Hooks hooks, ProfessionService professions,
                        Supplier<FishingService> fishing) {
        this.plugin = plugin;
        this.msg = msg;
        this.config = config;
        this.hooks = hooks;
        this.db = AincradCore.get().database();
        this.professions = professions;
        this.fishing = fishing;
    }

    /** Lê as barracas deste servidor. */
    public void loadBlocking() throws SQLException {
        stalls.clear();
        for (Stall s : db.sync(c -> Database.query(c, "SELECT id, world, x, y, z, owner, owner_name FROM life_stalls WHERE server = ?",
                rs -> new Stall(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), UUID.fromString(rs.getString(6)),
                        rs.getString(7)), AincradCore.get().server()))) {
            stalls.put(s.id(), s);
        }
    }

    public Map<Long, Stall> stalls() {
        return stalls;
    }

    private Stall at(Block b) {
        for (Stall s : stalls.values()) {
            if (s.at(b)) {
                return s;
            }
        }
        return null;
    }

    private <T> void run(CommandSender who, Database.SqlFunction<T> work, Consumer<T> then, Runnable onError) {
        db.async(work).whenComplete((value, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Barracas: erro no banco", err);
                onError.run();
                msg.send(who, "geral.erro");
                return;
            }
            then.accept(value);
        }));
    }

    private String col(long amount) {
        return hooks.price(amount, Material.GOLD_INGOT, 100);
    }

    private static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    private static ItemStack unpack(String raw) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Line line(java.sql.ResultSet rs) throws SQLException {
        return new Line(rs.getLong(1), unpack(rs.getString(2)), rs.getInt(3), rs.getLong(4), rs.getLong(5));
    }

    private static List<Line> stock(java.sql.Connection c, long stall) throws SQLException {
        return Database.query(c, "SELECT id, item, amount, price, fresh_until FROM life_stall_stock WHERE stall_id = ? AND amount > 0 ORDER BY id",
                StallService::line, stall);
    }

    private double staleFactor() {
        return config.decimal("barracas.frescor.preco_depois", 0.5, 0, 1);
    }

    private double taxPercent() {
        return config.decimal("barracas.taxa_pct", 0, 0, 100);
    }

    // ------------------------------------------------------------------ criar e remover

    /** Marca o bloco olhado como barraca do jogador. Precisa estar dentro da casa dele (config → barracas.exigir_casa). */
    public void create(Player p) {
        Block b = p.getTargetBlockExact(5);
        if (b == null || b.getType().isAir() || at(b) != null) {
            msg.send(p, "barraca.olhe");
            return;
        }
        if (config.bool("barracas.exigir_casa", true) && !p.getUniqueId().equals(hooks.houseOwner(b.getLocation()))) {
            msg.send(p, "barraca.fora_de_casa");
            return;
        }
        int limit = config.integer("barracas.por_jogador", 1, 1, 100);
        String me = p.getUniqueId().toString();
        String server = AincradCore.get().server();
        String world = b.getWorld().getName();
        run(p, c -> {
            // O limite vale para a rede inteira.
            if (Database.query(c, "SELECT COUNT(*) FROM life_stalls WHERE owner = ?", rs -> rs.getInt(1), me).get(0) >= limit) {
                return -1L;
            }
            Database.update(c, "INSERT INTO life_stalls (server, world, x, y, z, owner, owner_name) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    server, world, b.getX(), b.getY(), b.getZ(), me, p.getName());
            return Database.query(c, "SELECT MAX(id) FROM life_stalls WHERE owner = ? AND world = ? AND x = ? AND y = ? AND z = ?",
                    rs -> rs.getLong(1), me, world, b.getX(), b.getY(), b.getZ()).get(0);
        }, id -> {
            if (id < 0) {
                msg.send(p, "barraca.limite", Messages.vars("limite", limit));
                return;
            }
            stalls.put(id, new Stall(id, world, b.getX(), b.getY(), b.getZ(), p.getUniqueId(), p.getName()));
            msg.send(p, "barraca.criada");
        }, () -> { });
    }

    /** O dono (ou um admin) tira a barraca olhada. O estoque volta para o dono. */
    public void remove(Player p, boolean admin) {
        Block b = p.getTargetBlockExact(5);
        Stall s = b == null ? null : at(b);
        if (s == null || (!admin && !s.owner().equals(p.getUniqueId()))) {
            msg.send(p, "barraca.nao_e_sua");
            return;
        }
        run(p, c -> {
            List<Line> left = stock(c, s.id());
            Database.update(c, "DELETE FROM life_stall_stock WHERE stall_id = ?", s.id());
            Database.update(c, "DELETE FROM life_stalls WHERE id = ?", s.id());
            return left;
        }, left -> {
            stalls.remove(s.id());
            Player owner = Bukkit.getPlayer(s.owner());
            for (Line l : left) {
                if (l.item() == null) {
                    continue;
                }
                // Devolve em pilhas do tamanho que o item aceita.
                int rest = l.amount();
                while (rest > 0) {
                    ItemStack it = l.item().clone();
                    int n = Math.min(rest, Math.max(1, it.getMaxStackSize()));
                    it.setAmount(n);
                    rest -= n;
                    if (owner != null) {
                        give(owner, it);
                    } else {
                        hooks.mail(s.owner(), it, "barraca");
                    }
                }
            }
            msg.send(p, "barraca.removida");
        }, () -> { });
    }

    // ------------------------------------------------------------------ estoque

    /** O dono põe à venda a pilha que está na mão, olhando para a barraca dele. */
    public void stock(Player p, long price) {
        Block b = p.getTargetBlockExact(5);
        Stall s = b == null ? null : at(b);
        if (s == null || !s.owner().equals(p.getUniqueId())) {
            msg.send(p, "barraca.nao_e_sua");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || price <= 0) {
            msg.send(p, "barraca.uso");
            return;
        }
        // Peixeiro (árvore): a banca guarda o dobro.
        int lines = config.integer("barracas.itens_por_barraca", 27, 1, 45)
                * (professions.modifier(p, Profession.PESCA, "banca_dobro") >= 1 ? 2 : 1);
        ItemStack unit = hand.clone();
        unit.setAmount(1);
        int amount = hand.getAmount();
        String packed = Base64.getEncoder().encodeToString(unit.serializeAsBytes());
        // O frescor do peixe é contado da hora em que foi pescado; os nós do pescador alongam.
        FishingService f = fishing.get();
        long caught = f == null ? 0 : f.fishCaughtAt(unit);
        long fishFresh = StallRules.freshUntil(caught, config.decimal("barracas.frescor.horas", 24, 0, 8760),
                professions.modifier(p, Profession.PESCA, "frescor"));
        // Os pratos também passam do ponto na banca (contado de agora), menos os de quem tem as
        // Conservas na árvore de culinária.
        double dishHours = config.decimal("barracas.frescor.pratos_horas", 48, 0, 8760);
        boolean dish = unit.hasItemMeta() && unit.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "prato"));
        long freshUntil = dish && dishHours > 0 && professions.modifier(p, Profession.CULINARIA, "sem_validade") < 1
                ? System.currentTimeMillis() + Math.round(dishHours * 3_600_000L) : fishFresh;
        p.getInventory().setItemInMainHand(null);
        ItemStack back = hand.clone();
        run(p, c -> {
            // Mesmo item e mesmo preço: soma na linha que já existe.
            List<Long> same = Database.query(c, "SELECT id FROM life_stall_stock WHERE stall_id = ? AND item = ? AND price = ? AND fresh_until = ?",
                    rs -> rs.getLong(1), s.id(), packed, price, freshUntil);
            if (!same.isEmpty()) {
                Database.update(c, "UPDATE life_stall_stock SET amount = amount + ? WHERE id = ?", amount, same.get(0));
                return true;
            }
            if (Database.query(c, "SELECT COUNT(*) FROM life_stall_stock WHERE stall_id = ? AND amount > 0", rs -> rs.getInt(1), s.id()).get(0) >= lines) {
                return false;
            }
            Database.update(c, "INSERT INTO life_stall_stock (stall_id, item, amount, price, fresh_until, added) VALUES (?, ?, ?, ?, ?, ?)",
                    s.id(), packed, amount, price, freshUntil, System.currentTimeMillis());
            return true;
        }, ok -> {
            if (!ok) {
                give(p, back);
                msg.send(p, "barraca.cheia", Messages.vars("limite", lines));
                return;
            }
            msg.send(p, freshUntil > 0 ? "barraca.posto_fresco" : "barraca.posto", Messages.vars("qtd", amount, "preco", col(price),
                    "tempo", TimeFormat.duration(Math.max(0, freshUntil - System.currentTimeMillis()), 2)));
        }, () -> give(p, back));
    }

    // ------------------------------------------------------------------ clique

    /** Clique na barraca: o dono vê o estoque (clicar tira); os outros veem a loja (clicar compra). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Stall s = at(event.getClickedBlock());
        if (s == null) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer(), s);
    }

    private Component name(ItemStack it) {
        return it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? it.getItemMeta().displayName()
                : Component.translatable(it.translationKey());
    }

    private void open(Player p, Stall s) {
        boolean mine = s.owner().equals(p.getUniqueId());
        run(p, c -> stock(c, s.id()), lines -> {
            if (lines.isEmpty()) {
                msg.send(p, mine ? "barraca.vazia_sua" : "barraca.vazia", Messages.vars("dono", s.ownerName()));
                return;
            }
            long now = System.currentTimeMillis();
            Menu menu = Menu.of(msg.get("barraca.titulo", Messages.vars("dono", s.ownerName()))).rows(6);
            for (Line l : lines) {
                if (l.item() == null) {
                    continue;
                }
                long price = StallRules.price(l.price(), l.freshUntil(), now, staleFactor());
                List<Component> lore = new ArrayList<>();
                if (l.item().hasItemMeta() && l.item().getItemMeta().lore() != null) {
                    lore.addAll(l.item().getItemMeta().lore());
                }
                lore.add(msg.get("barraca.linha_preco", Messages.vars("preco", col(price), "estoque", l.amount())));
                if (l.freshUntil() > 0) {
                    lore.add(StallRules.fresh(l.freshUntil(), now)
                            ? msg.get("barraca.linha_fresco", Messages.vars("tempo", TimeFormat.duration(l.freshUntil() - now, 2)))
                            : msg.get("barraca.linha_passado", Messages.vars("cheio", col(l.price()))));
                }
                lore.add(msg.get(mine ? "barraca.clique_tirar" : "barraca.clique_comprar"));
                menu.item(Menu.Button.of(l.item().clone(), name(l.item()), pl -> {
                    if (mine) {
                        take(pl, s, l);
                    } else {
                        buy(pl, s, l, price);
                    }
                }).closing().lore(lore));
            }
            AincradCore.get().menus().open(p, menu);
        }, () -> { });
    }

    /**
     * Compra uma unidade. O Col sai do cliente antes; se a última unidade foi vendida a outro no
     * mesmo instante, volta. O dono recebe mesmo offline ou em outro servidor.
     */
    private void buy(Player p, Stall s, Line l, long shown) {
        long now = System.currentTimeMillis();
        long price = StallRules.price(l.price(), l.freshUntil(), now, staleFactor());
        if (price != shown) {
            // O peixe passou enquanto o menu estava aberto: mostra o preço novo.
            open(p, s);
            return;
        }
        if (!hooks.charge(p, price, Material.GOLD_INGOT, 100, "life:barraca_compra")) {
            msg.send(p, "barraca.sem_col", Messages.vars("preco", col(price)));
            return;
        }
        run(p, c -> Database.update(c, "UPDATE life_stall_stock SET amount = amount - 1 WHERE id = ? AND stall_id = ? AND amount > 0", l.id(), s.id()) > 0,
                ok -> {
                    if (!ok) {
                        hooks.pay(p.getUniqueId(), price, "life:barraca_estorno");
                        msg.send(p, "barraca.acabou");
                        return;
                    }
                    ItemStack it = l.item().clone();
                    it.setAmount(1);
                    give(p, it);
                    long take = StallRules.ownerTake(price, taxPercent());
                    hooks.pay(s.owner(), take, "life:barraca_venda");
                    msg.send(p, "barraca.comprou", Messages.vars("preco", col(price), "dono", s.ownerName()));
                    Player owner = Bukkit.getPlayer(s.owner());
                    if (owner != null) {
                        msg.actionBar(owner, "barraca.vendeu", Messages.vars("preco", col(take), "jogador", p.getName()));
                    }
                }, () -> hooks.pay(p.getUniqueId(), price, "life:barraca_estorno"));
    }

    /** O dono tira uma linha inteira do estoque. */
    private void take(Player p, Stall s, Line l) {
        run(p, c -> {
            List<Integer> now = Database.query(c, "SELECT amount FROM life_stall_stock WHERE id = ? AND stall_id = ?", rs -> rs.getInt(1), l.id(), s.id());
            if (now.isEmpty() || now.get(0) <= 0) {
                return 0;
            }
            // Só tira o que ainda estava lá quando leu.
            return Database.update(c, "DELETE FROM life_stall_stock WHERE id = ? AND amount = ?", l.id(), now.get(0)) > 0 ? now.get(0) : 0;
        }, n -> {
            int rest = n;
            while (rest > 0) {
                ItemStack it = l.item().clone();
                int stack = Math.min(rest, Math.max(1, it.getMaxStackSize()));
                it.setAmount(stack);
                rest -= stack;
                give(p, it);
            }
            msg.send(p, n > 0 ? "barraca.tirou" : "barraca.acabou", Messages.vars("qtd", n));
        }, () -> { });
    }

    /** O bloco da barraca só sai pelo comando de remover. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ comando

    /** /barraca criar | remover | por &lt;preço&gt; */
    public void command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "geral.so_jogador");
            return;
        }
        if (!hooks.hasEconomy()) {
            msg.send(p, "barraca.sem_economia");
            return;
        }
        String op = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (op) {
            case "criar" -> create(p);
            case "remover" -> remove(p, p.hasPermission("aincrad.admin"));
            case "por", "vender" -> {
                long price = -1;
                try {
                    price = args.length > 1 ? Long.parseLong(args[1].replace(".", "")) : -1;
                } catch (NumberFormatException e) {
                    // fica -1
                }
                stock(p, price);
            }
            default -> msg.send(p, "barraca.uso");
        }
    }
}
