package com.aincrad.core;

import com.aincrad.core.bus.MessageBus;
import com.aincrad.core.db.Database;
import com.aincrad.core.profile.ProfileService;
import com.aincrad.core.region.RegionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Banco de verdade (SQLite em arquivo temporário): migrações, upsert e MessageBus. */
class DatabaseTest {

    @TempDir
    File tmp;

    private Database db;

    @BeforeEach
    void open() {
        db = Database.sqlite(new File(tmp, "teste.db"), Logger.getAnonymousLogger());
    }

    @AfterEach
    void close() {
        db.close();
    }

    @Test
    void migracoesRodamUmaVezEmOrdem() throws SQLException {
        List<String> v1 = List.of("CREATE TABLE t ({AUTO_ID}, nome VARCHAR(20))");
        assertEquals(1, db.migrate("Teste", v1));
        assertEquals(0, db.migrate("Teste", v1));
        List<String> v2 = List.of(v1.get(0), "ALTER TABLE t ADD COLUMN level INT DEFAULT 1; INSERT INTO t (nome) VALUES ('Kirito')");
        assertEquals(1, db.migrate("Teste", v2));
        assertEquals(List.of("Kirito:1"), db.sync(c -> Database.query(c, "SELECT nome, level FROM t",
                rs -> rs.getString(1) + ":" + rs.getInt(2))));
        // Outro dono tem a própria contagem.
        assertEquals(1, db.migrate("Outro", List.of("CREATE TABLE o (x INT)")));
    }

    @Test
    void migracaoComErroNaoMarcaComoAplicada() throws SQLException {
        assertThrows(SQLException.class, () -> db.migrate("Ruim", List.of("CREATE TABLE ok (x INT); ISSO NAO E SQL")));
        int count = db.sync(c -> Database.query(c, "SELECT COUNT(*) FROM aincrad_migrations WHERE owner = 'Ruim'",
                rs -> rs.getInt(1)).get(0));
        assertEquals(0, count);
    }

    @Test
    void upsertInsereEAtualiza() throws Exception {
        db.migrate("AincradCore.profiles", ProfileService.MIGRATIONS);
        String sql = db.upsert("aincrad_profiles", List.of("uuid", "owner", "k"), List.of("v"));
        db.update(sql, "u1", "Floors", "andar", "1").get();
        db.update(sql, "u1", "Floors", "andar", "2").get();
        db.update(sql, "u1", "RPG", "level", "10").get();
        List<String> rows = db.query("SELECT owner, k, v FROM aincrad_profiles ORDER BY owner",
                rs -> rs.getString(1) + "." + rs.getString(2) + "=" + rs.getString(3)).get();
        assertEquals(List.of("Floors.andar=2", "RPG.level=10"), rows);
    }

    @Test
    void messageBusEntregaSoOQueVeioDeOutroServidor() throws Exception {
        db.migrate("AincradCore.bus", MessageBus.MIGRATIONS);
        MessageBus andar1 = new MessageBus(db, "andar-1", Logger.getAnonymousLogger());
        MessageBus andar2 = new MessageBus(db, "andar-2", Logger.getAnonymousLogger());
        andar1.start();
        andar2.start();
        List<String> got1 = new ArrayList<>();
        List<String> got2 = new ArrayList<>();
        andar1.subscribe("floors:unlock", m -> got1.add(m.origin() + ":" + m.payload()));
        andar2.subscribe("floors:unlock", m -> got2.add(m.origin() + ":" + m.payload()));

        andar1.publish("floors:unlock", "2", Runnable::run);
        assertEquals(List.of("andar-1:2"), got1, "quem publica recebe na hora");
        waitWrites();
        andar2.poll(Runnable::run);
        waitPoll();
        andar1.poll(Runnable::run);
        waitPoll();
        assertEquals(List.of("andar-1:2"), got2, "o outro servidor recebe na leitura");
        assertEquals(List.of("andar-1:2"), got1, "quem publicou não recebe de novo");
    }

    @Test
    void tabelasDoCoreCriam() throws SQLException {
        assertEquals(1, db.migrate("AincradCore.regions", RegionManager.MIGRATIONS));
        assertTrue(db.sync(c -> Database.query(c, "SELECT COUNT(*) FROM aincrad_regions", rs -> rs.getInt(1))).get(0) == 0);
    }

    /** As escritas são assíncronas: uma consulta qualquer pela mesma fila espera a anterior. */
    private void waitWrites() throws Exception {
        db.query("SELECT 1", rs -> 1).get();
    }

    private void waitPoll() throws Exception {
        db.query("SELECT 1", rs -> 1).get();
        Thread.sleep(50);
    }
}
