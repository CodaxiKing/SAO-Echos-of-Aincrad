package com.aincrad.admin;

import com.aincrad.admin.BackupFiles.Stats;
import com.aincrad.admin.model.BackupRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O backup (etapa 30): as regras e a ida e volta em disco, com um SQLite de verdade. */
class BackupTest {

    private static final List<String> NAMES = List.of("2026-11-01_0400", "2026-11-17_0400", "2026-11-18_0400",
            "2026-11-18_1530", "2026-11-18_1530-2", "lixo", "2026-13-40_0400");

    // ------------------------------------------------------------------ regras

    @Test
    void nomeDaPastaTemDiaEHora() {
        assertEquals("2026-11-18_0400", BackupRules.name(LocalDateTime.of(2026, 11, 18, 4, 0, 59)));
        assertTrue(BackupRules.valid("2026-11-18_0400"));
        assertTrue(BackupRules.valid("2026-11-18_0400-2"));
        assertFalse(BackupRules.valid("lixo"));
        assertFalse(BackupRules.valid("2026-13-40_0400"));
        assertFalse(BackupRules.valid("../2026-11-18_0400"));
        assertEquals(LocalDate.of(2026, 11, 18), BackupRules.date("2026-11-18_1530-2"));
        // Dois backups no mesmo minuto não se sobrescrevem.
        assertEquals("2026-11-18_0900", BackupRules.unique("2026-11-18_0900", NAMES));
        assertEquals("2026-11-18_0400-2", BackupRules.unique("2026-11-18_0400", NAMES));
        assertEquals("2026-11-18_1530-3", BackupRules.unique("2026-11-18_1530", NAMES));
    }

    @Test
    void restaurarPeloNomeOuPeloDia() {
        assertEquals("2026-11-17_0400", BackupRules.resolve(NAMES, "2026-11-17_0400"));
        // Só o dia: o mais novo daquele dia.
        assertEquals("2026-11-18_1530-2", BackupRules.resolve(NAMES, "2026-11-18"));
        assertEquals("2026-11-01_0400", BackupRules.resolve(NAMES, "2026-11-01"));
        assertNull(BackupRules.resolve(NAMES, "2026-11-02"));
        assertNull(BackupRules.resolve(NAMES, "2026-11-02_0400"));
        assertNull(BackupRules.resolve(NAMES, "lixo"));
        assertNull(BackupRules.resolve(NAMES, null));
    }

    @Test
    void backupsComMaisDe14DiasSaoApagados() {
        LocalDate today = LocalDate.of(2026, 11, 18);
        assertEquals(List.of("2026-11-01_0400"), BackupRules.expired(NAMES, today, 14));
        // 17 dias atrás ainda vale com 20 dias de guarda; pastas que não são backup nunca são apagadas.
        assertTrue(BackupRules.expired(NAMES, today, 20).isEmpty());
        assertTrue(BackupRules.expired(NAMES, today, 0).isEmpty());
        assertEquals(2, BackupRules.expired(NAMES, today, 1).size());
    }

    @Test
    void oDiarioRodaUmaVezPorDiaDepoisDaHora() {
        LocalTime at = BackupRules.time("04:00");
        assertEquals(LocalTime.of(4, 0), at);
        assertEquals(LocalTime.of(4, 30), BackupRules.time("4:30"));
        assertNull(BackupRules.time("25:00"));
        assertNull(BackupRules.time("cedo"));
        // Antes da hora não roda; depois, só se ainda não há backup de hoje.
        assertFalse(BackupRules.dailyDue(NAMES, LocalDateTime.of(2026, 11, 19, 3, 59), at));
        assertTrue(BackupRules.dailyDue(NAMES, LocalDateTime.of(2026, 11, 19, 4, 0), at));
        assertTrue(BackupRules.dailyDue(NAMES, LocalDateTime.of(2026, 11, 19, 22, 0), at));
        assertFalse(BackupRules.dailyDue(NAMES, LocalDateTime.of(2026, 11, 18, 22, 0), at));
    }

    @Test
    void soConfigEDadosEntram() {
        assertTrue(BackupRules.include("config.yml", 100));
        assertTrue(BackupRules.include("dados.YAML", 100));
        assertTrue(BackupRules.include("guildas.json", 100));
        assertFalse(BackupRules.include("aincrad-pack.zip", 100));
        assertFalse(BackupRules.include("aincrad.db", 100));
        assertFalse(BackupRules.include("enorme.yml", BackupRules.MAX_FILE + 1));
        assertFalse(BackupRules.include(".yml", 100));
        assertTrue(BackupRules.skipDir("backups"));
        assertTrue(BackupRules.skipDir("Pack"));
        assertFalse(BackupRules.skipDir("dungeons"));
        assertEquals("512 B", BackupRules.size(512));
        assertEquals("1,5 KB", BackupRules.size(1536));
        assertEquals("2,0 MB", BackupRules.size(2L * 1024 * 1024));
    }

    // ------------------------------------------------------------------ disco

    private static int floors(Path db) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT unlocked FROM floors")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    @Test
    void restaurarTrazDeVoltaOEstadoDosAndares(@TempDir Path tmp) throws Exception {
        // Uma pasta "plugins" com espaço no caminho, como acontece no Windows.
        Path plugins = Files.createDirectories(tmp.resolve("meu servidor").resolve("plugins"));
        Path floorsDir = Files.createDirectories(plugins.resolve("AincradFloors").resolve("andares"));
        Path core = Files.createDirectories(plugins.resolve("AincradCore"));
        Files.writeString(floorsDir.resolve("1.yml"), "nome: Cidade do Início");
        Files.writeString(plugins.resolve("AincradFloors").resolve("config.yml"), "andares: 100");
        Files.createDirectories(plugins.resolve("AincradEffects").resolve("pack"));
        Files.writeString(plugins.resolve("AincradEffects").resolve("pack").resolve("sounds.json"), "{}");
        Files.writeString(plugins.resolve("AincradEffects").resolve("aincrad-pack.zip"), "zip");
        Path db = core.resolve("aincrad.db");
        Path backup = plugins.resolve("AincradAdmin").resolve("backups").resolve("2026-11-18_0400");

        // O backup: três andares liberados.
        Stats copied = new Stats(0, 0);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE floors (unlocked INT)");
                st.executeUpdate("INSERT INTO floors VALUES (3)");
            }
            for (String p : List.of("AincradFloors", "AincradEffects", "AincradCore")) {
                copied = copied.plus(BackupFiles.copyData(plugins.resolve(p), backup.resolve(p)));
            }
            long size = BackupFiles.snapshot(c, backup.resolve(BackupFiles.DB_DIR).resolve("aincrad.db").toFile());
            assertTrue(size > 0);
            // Depois do backup o jogo segue: mais andares, config mexida.
            try (Statement st = c.createStatement()) {
                st.executeUpdate("UPDATE floors SET unlocked = 7");
            }
        }
        // Só os dois YAML: o .zip e a pasta do pack ficam de fora.
        assertEquals(2, copied.files());
        assertTrue(Files.exists(backup.resolve("AincradFloors").resolve("andares").resolve("1.yml")));
        assertFalse(Files.exists(backup.resolve("AincradEffects")));
        Files.writeString(floorsDir.resolve("1.yml"), "nome: mudado");
        assertEquals(7, floors(db));

        // A restauração (servidor desligado).
        Stats restored = BackupFiles.restore(backup, plugins);
        assertEquals(3, restored.files());
        assertEquals(3, floors(db));
        assertEquals("nome: Cidade do Início", Files.readString(floorsDir.resolve("1.yml")));
        // O que não estava no backup fica como estava.
        assertEquals("zip", Files.readString(plugins.resolve("AincradEffects").resolve("aincrad-pack.zip")));

        BackupFiles.deleteTree(backup);
        assertFalse(Files.exists(backup));
    }
}
