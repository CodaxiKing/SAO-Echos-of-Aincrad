package com.aincrad.world;

import com.aincrad.core.AincradCore;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.world.hook.Hooks;
import com.aincrad.world.model.WorldRules;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Notícias do castelo: o plugin ouve o que os outros plugins anunciam, guarda no banco do core
 * (vale para a rede) e monta o jornal da semana. Lido em {@code /jornal}; vendido como livro.
 */
public final class NewsService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS world_news ({AUTO_ID}, at_time BIGINT NOT NULL, kind VARCHAR(24) NOT NULL, "
                    + "body VARCHAR(255) NOT NULL, server VARCHAR(64) NOT NULL)",
            "CREATE TABLE IF NOT EXISTS world_state (k VARCHAR(64) NOT NULL PRIMARY KEY, v VARCHAR(255) NOT NULL)");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.systemDefault());

    /** Uma notícia. */
    public record Item(long at, String kind, String body) {
    }

    private final Plugin plugin;
    private final Database db;
    private final Messages msg;
    private final Hooks hooks;
    private int perPage = 5;
    private int keepWeeks = 52;
    private long price = 20;

    public NewsService(Plugin plugin, Database db, Messages msg, Hooks hooks) {
        this.plugin = plugin;
        this.db = db;
        this.msg = msg;
        this.hooks = hooks;
    }

    public void reload(ConfigFile c) {
        perPage = c.integer("jornal.noticias_por_pagina", 5, 1, 12);
        keepWeeks = c.integer("jornal.guardar_semanas", 52, 0, 5200);
        price = c.integer("jornal.preco", 20, 0, 100_000_000);
    }

    /**
     * Guarda um acontecimento. O texto vem de {@code noticia.<tipo>} no messages.yml, com as
     * variáveis trocadas; tipo sem texto (em branco) não vira notícia.
     */
    public void record(String kind, Map<String, String> vars) {
        if (!msg.has("noticia." + kind)) {
            return;
        }
        String text = msg.raw("noticia." + kind);
        if (text.isBlank()) {
            return;
        }
        for (var e : vars.entrySet()) {
            // O que vem do jogo entra como texto puro: um nome não vira formatação.
            text = text.replace("<" + e.getKey() + ">", e.getValue() == null ? "" : e.getValue().replace("<", "‹"));
        }
        db.update("INSERT INTO world_news (at_time, kind, body, server) VALUES (?, ?, ?, ?)", System.currentTimeMillis(), kind,
                        WorldRules.clip(text, 255), WorldRules.clip(AincradCore.get().server(), 64))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Não foi possível guardar a notícia (" + kind + ")", ex);
                    return 0;
                });
    }

    /** Apaga as edições mais velhas que o prazo de guarda. */
    public void purge() {
        if (keepWeeks > 0) {
            db.update("DELETE FROM world_news WHERE at_time < ?", System.currentTimeMillis() - keepWeeks * 7L * 86_400_000L);
        }
    }

    private CompletableFuture<List<Item>> load(String edition) {
        long[] w = WorldRules.week(edition, ZoneId.systemDefault());
        if (w == null) {
            return CompletableFuture.completedFuture(null);
        }
        return db.query("SELECT at_time, kind, body FROM world_news WHERE at_time >= ? AND at_time < ? ORDER BY at_time",
                rs -> new Item(rs.getLong(1), rs.getString(2), rs.getString(3)), w[0], w[1]);
    }

    private List<Component> pages(String edition, List<Item> items) {
        List<Component> pages = new ArrayList<>();
        List<Component> first = new ArrayList<>(msg.list("jornal.capa", Messages.vars("edicao", edition, "noticias", items.size())));
        if (items.isEmpty()) {
            first.add(msg.get("jornal.sem_noticias"));
        }
        pages.add(Component.join(JoinConfiguration.newlines(), first));
        for (List<Item> chunk : WorldRules.pages(items, perPage)) {
            List<Component> lines = new ArrayList<>();
            for (Item it : chunk) {
                lines.add(msg.get("jornal.linha", Messages.vars("data", DAY.format(Instant.ofEpochMilli(it.at())), "texto", it.body())));
            }
            pages.add(Component.join(JoinConfiguration.separator(Component.newline().append(Component.newline())), lines));
        }
        return pages;
    }

    public String current() {
        return WorldRules.edition(System.currentTimeMillis(), ZoneId.systemDefault());
    }

    /** {@code /jornal [edição]}: abre o jornal da semana (ou de uma semana antiga). */
    public void read(Player p, String edition) {
        String ed = edition == null ? current() : edition.toUpperCase(java.util.Locale.ROOT);
        load(ed).whenComplete((items, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null || items == null) {
                msg.send(p, ex != null ? "jornal.erro" : "jornal.edicao_invalida", Messages.vars("edicao", ed));
                return;
            }
            p.openBook(Book.book(msg.get("jornal.titulo", Messages.vars("edicao", ed)), msg.get("jornal.autor"), pages(ed, items)));
        }));
    }

    /** {@code /jornal lista}: as edições que têm notícias, da mais nova para a mais velha. */
    public void list(Player p) {
        long since = System.currentTimeMillis() - 26 * 7L * 86_400_000L;
        db.query("SELECT at_time FROM world_news WHERE at_time >= ? ORDER BY at_time DESC", rs -> rs.getLong(1), since)
                .whenComplete((times, ex) -> AincradCore.get().runSync(() -> {
                    if (ex != null) {
                        msg.send(p, "jornal.erro");
                        return;
                    }
                    Map<String, Integer> count = new LinkedHashMap<>();
                    for (long t : times) {
                        count.merge(WorldRules.edition(t, ZoneId.systemDefault()), 1, Integer::sum);
                    }
                    if (count.isEmpty()) {
                        msg.send(p, "jornal.nenhuma_edicao");
                        return;
                    }
                    Menu menu = Menu.of(msg.get("jornal.menu")).rows(3);
                    count.forEach((ed, n) -> menu.item(Menu.Button.of(new ItemStack(Material.WRITTEN_BOOK),
                            msg.get("jornal.titulo", Messages.vars("edicao", ed)), pl -> read(pl, ed)).closing()
                            .lore(List.of(msg.get("jornal.menu_noticias", Messages.vars("noticias", n))))));
                    AincradCore.get().menus().open(p, menu);
                }));
    }

    /** {@code /jornal comprar}: o jornal da semana como livro, para guardar. */
    public void buy(Player p) {
        String ed = current();
        load(ed).whenComplete((items, ex) -> AincradCore.get().runSync(() -> {
            if (ex != null || items == null) {
                msg.send(p, "jornal.erro");
                return;
            }
            if (!hooks.charge(p, price, "jornal " + ed)) {
                msg.send(p, "jornal.sem_col", Messages.vars("preco", price));
                return;
            }
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            meta.title(msg.get("jornal.titulo", Messages.vars("edicao", ed)));
            meta.author(msg.get("jornal.autor"));
            meta.addPages(pages(ed, items).toArray(new Component[0]));
            book.setItemMeta(meta);
            p.getInventory().addItem(book).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            msg.send(p, "jornal.comprou", Messages.vars("edicao", ed, "preco", hooks.hasEconomy() ? price : 0));
        }));
    }
}
