package com.aincrad.admin;

import com.aincrad.admin.model.BackupRules;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** A parte de disco do backup: copiar as pastas dos plugins, tirar a cópia do SQLite e devolver tudo. */
public final class BackupFiles {

    /** Pasta do banco dentro de um backup. */
    public static final String DB_DIR = "banco";

    /** Quantos arquivos e bytes foram copiados. */
    public record Stats(int files, long bytes) {

        public Stats plus(Stats o) {
            return new Stats(files + o.files, bytes + o.bytes);
        }
    }

    private BackupFiles() {
    }

    /** Copia os YAML e JSON de uma pasta de plugin, mantendo as subpastas. */
    public static Stats copyData(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return new Stats(0, 0);
        }
        int[] files = {0};
        long[] bytes = {0};
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return !dir.equals(from) && BackupRules.skipDir(dir.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile() && BackupRules.include(file.getFileName().toString(), attrs.size())) {
                    Path dest = to.resolve(from.relativize(file).toString());
                    Files.createDirectories(dest.getParent());
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    files[0]++;
                    bytes[0] += attrs.size();
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return new Stats(files[0], bytes[0]);
    }

    /** Cópia consistente do SQLite em uso (comando "backup to" do driver), sem fechar o banco. */
    public static long snapshot(Connection c, File dest) throws SQLException, IOException {
        Files.createDirectories(dest.getParentFile().toPath());
        Files.deleteIfExists(dest.toPath());
        try (Statement st = c.createStatement()) {
            st.executeUpdate("backup to \"" + dest.getAbsolutePath() + "\"");
        }
        return dest.length();
    }

    /**
     * Devolve um backup às pastas dos plugins: cada pasta do backup vai por cima da pasta de mesmo
     * nome em {@code pluginsDir}, e o que está em {@code banco/} vai para {@code AincradCore/}.
     * Só pode rodar com os plugins ainda desligados (onLoad).
     */
    public static Stats restore(Path backup, Path pluginsDir) throws IOException {
        Stats total = new Stats(0, 0);
        try (var dirs = Files.newDirectoryStream(backup, Files::isDirectory)) {
            for (Path dir : dirs) {
                String name = dir.getFileName().toString();
                if (name.equals(DB_DIR)) {
                    Path core = pluginsDir.resolve("AincradCore");
                    try (var dbs = Files.newDirectoryStream(dir, Files::isRegularFile)) {
                        for (Path db : dbs) {
                            String file = db.getFileName().toString();
                            // Restos de uma sessão anterior do SQLite não valem para o arquivo que volta.
                            Files.deleteIfExists(core.resolve(file + "-wal"));
                            Files.deleteIfExists(core.resolve(file + "-shm"));
                            Files.deleteIfExists(core.resolve(file + "-journal"));
                            Files.createDirectories(core);
                            Files.copy(db, core.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                            total = total.plus(new Stats(1, Files.size(db)));
                        }
                    }
                } else {
                    total = total.plus(copyData(dir, pluginsDir.resolve(name)));
                }
            }
        }
        return total;
    }

    public static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
