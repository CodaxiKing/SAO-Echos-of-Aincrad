package com.aincrad.admin;

import com.aincrad.admin.BackupFiles.Stats;
import com.aincrad.admin.model.BackupRules;
import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Backup dos plugins Aincrad: os YAML e JSON da pasta de cada um e uma cópia do banco SQLite, numa
 * pasta com data em {@code plugins/AincradAdmin/backups/}. Roda uma vez por dia e a pedido.
 * A restauração é marcada aqui e feita ao ligar o servidor, antes de qualquer plugin ler os dados.
 */
public final class BackupService {

    /** Arquivo que marca uma restauração pedida: o nome da pasta do backup. */
    public static final String PENDING = "restaurar.txt";
    public static final String MANIFEST = "backup.yml";

    /** O resultado de um backup. */
    public record Result(String name, int files, long bytes, boolean database) {
    }

    private final Plugin plugin;
    private final File root;
    private final AtomicBoolean running = new AtomicBoolean();
    private boolean daily;
    private LocalTime at = LocalTime.of(4, 0);
    private int keepDays = 14;

    public BackupService(Plugin plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "backups");
    }

    public void settings(boolean daily, LocalTime at, int keepDays) {
        this.daily = daily;
        this.at = at;
        this.keepDays = keepDays;
    }

    public File root() {
        return root;
    }

    /** As pastas de backup, da mais velha para a mais nova. */
    public List<String> names() {
        List<String> out = new ArrayList<>();
        String[] list = root.list();
        if (list != null) {
            for (String n : list) {
                if (BackupRules.valid(n) && new File(root, n).isDirectory()) {
                    out.add(n);
                }
            }
        }
        out.sort(BackupRules::compare);
        return out;
    }

    public YamlConfiguration manifest(String name) {
        return YamlConfiguration.loadConfiguration(new File(new File(root, name), MANIFEST));
    }

    /** A cada minuto: faz o backup do dia se já passou da hora e ele ainda não existe. */
    public void tick() {
        if (daily && !running.get() && BackupRules.dailyDue(names(), LocalDateTime.now(), at)) {
            run("diário").whenComplete((r, ex) -> {
                if (ex == null) {
                    plugin.getLogger().info("Backup diário feito: " + r.name() + " (" + r.files() + " arquivos, "
                            + BackupRules.size(r.bytes()) + (r.database() ? ", com o banco" : ", sem o banco") + ").");
                }
            });
        }
    }

    /** Faz um backup agora, fora da thread principal. Falha se já há um em andamento. */
    public CompletableFuture<Result> run(String reason) {
        CompletableFuture<Result> future = new CompletableFuture<>();
        if (!running.compareAndSet(false, true)) {
            future.completeExceptionally(new IllegalStateException("já há um backup em andamento"));
            return future;
        }
        // As pastas dos plugins são lidas aqui, na thread do servidor.
        List<File> folders = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().startsWith("Aincrad")) {
                folders.add(p.getDataFolder());
            }
        }
        Database db = AincradCore.get().database();
        String server = AincradCore.get().server();
        String dbFile = sqliteFile();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String name = BackupRules.unique(BackupRules.name(LocalDateTime.now()), names());
            Path dir = new File(root, name).toPath();
            try {
                Stats stats = new Stats(0, 0);
                for (File f : folders) {
                    stats = stats.plus(BackupFiles.copyData(f.toPath(), dir.resolve(f.getName())));
                }
                boolean withDb = db.type() == Database.Type.SQLITE;
                if (withDb) {
                    File dest = dir.resolve(BackupFiles.DB_DIR).resolve(dbFile).toFile();
                    long size = db.sync(c -> {
                        try {
                            return BackupFiles.snapshot(c, dest);
                        } catch (IOException e) {
                            throw new SQLException(e);
                        }
                    });
                    stats = stats.plus(new Stats(1, size));
                }
                YamlConfiguration m = new YamlConfiguration();
                m.set("criado", System.currentTimeMillis());
                m.set("motivo", reason);
                m.set("servidor", server);
                m.set("banco", withDb ? "sqlite" : "mysql (não incluído)");
                m.set("arquivos", stats.files());
                m.set("bytes", stats.bytes());
                Files.writeString(dir.resolve(MANIFEST), m.saveToString(), StandardCharsets.UTF_8);
                prune();
                future.complete(new Result(name, stats.files(), stats.bytes(), withDb));
            } catch (IOException | SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "O backup " + name + " falhou", e);
                try {
                    // Uma pasta pela metade não pode parecer um backup válido.
                    BackupFiles.deleteTree(dir);
                } catch (IOException ignored) {
                    // Fica para a próxima limpeza.
                }
                future.completeExceptionally(e);
            } finally {
                running.set(false);
            }
        });
        return future;
    }

    /** O nome do arquivo do SQLite, lido da config do core. */
    private static String sqliteFile() {
        File cfg = new File(AincradCore.get().plugin().getDataFolder(), "config.yml");
        String name = YamlConfiguration.loadConfiguration(cfg).getString("banco.sqlite.arquivo", "aincrad.db");
        return new File(name).getName();
    }

    private void prune() {
        for (String old : BackupRules.expired(names(), LocalDate.now(), keepDays)) {
            try {
                BackupFiles.deleteTree(new File(root, old).toPath());
                plugin.getLogger().info("Backup " + old + " apagado (mais de " + keepDays + " dias).");
            } catch (IOException e) {
                plugin.getLogger().warning("Não foi possível apagar o backup " + old + ": " + e.getMessage());
            }
        }
    }

    /** Marca o backup para ser restaurado na próxima vez que o servidor ligar. */
    public void markRestore(String name) throws IOException {
        Files.createDirectories(plugin.getDataFolder().toPath());
        Files.writeString(new File(plugin.getDataFolder(), PENDING).toPath(), name, StandardCharsets.UTF_8);
    }

    /**
     * Chamado no onLoad, antes de qualquer plugin ligar: se há uma restauração marcada, devolve os
     * arquivos do backup às pastas dos plugins. Não usa o core (ele ainda não ligou).
     */
    public static void applyPending(Plugin plugin) {
        File marker = new File(plugin.getDataFolder(), PENDING);
        if (!marker.isFile()) {
            return;
        }
        try {
            String name = Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim();
            File backup = new File(new File(plugin.getDataFolder(), "backups"), name);
            if (!BackupRules.valid(name) || !backup.isDirectory()) {
                plugin.getLogger().severe("Restauração pedida, mas o backup '" + name + "' não existe. Nada foi mudado.");
                return;
            }
            Stats s = BackupFiles.restore(backup.toPath(), plugin.getDataFolder().getParentFile().toPath());
            plugin.getLogger().warning("Backup " + name + " restaurado: " + s.files() + " arquivos ("
                    + BackupRules.size(s.bytes()) + ") voltaram às pastas dos plugins.");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "A restauração falhou no meio. Confira as pastas dos plugins "
                    + "antes de abrir o servidor; o backup feito antes de restaurar está em backups/.", e);
        } finally {
            if (!marker.delete()) {
                plugin.getLogger().severe("Não foi possível apagar " + marker + ": apague à mão, ou a restauração roda de novo.");
            }
        }
    }
}
